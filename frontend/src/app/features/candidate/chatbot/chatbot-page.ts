import { Component, effect, ElementRef, inject, input, OnInit, signal, viewChild } from '@angular/core';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../../core/auth/auth.service';
import { errorMessage } from '../../../core/http-errors';
import { ChatbotAction, ChatbotEntities, ChatbotOffer, ChatbotReply, JobOffer } from '../../../core/models';
import { ChatbotConversation, ChatbotService, ChatMessage } from '../../../core/services/chatbot.service';
import { JobOfferService } from '../../../core/services/job-offer.service';
import { LabelPipe } from '../../../shared/label.pipe';

export const MAX_QUESTION_LENGTH = 500;

export const DEFAULT_SUGGESTIONS = [
  'Quelles offres correspondent à Java ?',
  'Comment importer mon CV ?',
  'Comment postuler à une offre ?',
  'Comment fonctionne le score de compatibilité ?',
];

export const OFFER_SUGGESTIONS = [
  'Quelles sont les compétences demandées pour cette offre ?',
  "Combien d'années d'expérience sont nécessaires ?",
  "Quel niveau d'étude est demandé ?",
  'Quel est le salaire ?',
];

export const ACTION_LINKS: Record<ChatbotAction, { label: string; route: string; icon: string }> = {
  OPEN_JOBS: { label: 'Voir les offres', route: '/candidate/jobs', icon: 'bi-search' },
  OPEN_CV: { label: 'Mon CV', route: '/candidate/cv', icon: 'bi-file-earmark-person' },
  OPEN_APPLICATIONS: { label: 'Mes candidatures', route: '/candidate/applications', icon: 'bi-send' },
  OPEN_PROFILE: { label: 'Mon profil', route: '/candidate/profile', icon: 'bi-person' },
};

const EDUCATION_SHORT: Record<string, string> = {
  BAC: 'Bac',
  BAC_PLUS_2: 'Bac+2',
  BAC_PLUS_3: 'Bac+3',
  BAC_PLUS_5: 'Bac+5',
  DOCTORAT: 'Doctorat',
};

/** Ce que l'assistant a compris de la question, affiché sous sa réponse. */
export function understood(entities: ChatbotEntities): string[] {
  const items = [...entities.skills];
  if (entities.contractType) items.push(entities.contractType === 'STAGE' ? 'Stage' : entities.contractType);
  if (entities.location) items.push(entities.location);
  if (entities.experienceYears !== null) {
    items.push(entities.experienceYears === 0 ? 'Débutant' : `${entities.experienceYears} an(s) d'expérience`);
  }
  if (entities.educationLevel) items.push(EDUCATION_SHORT[entities.educationLevel]);
  items.push(...entities.keywords.map((k) => `« ${k} »`));
  return items;
}

function toChatbotOffer(job: JobOffer): ChatbotOffer {
  return {
    id: job.id,
    title: job.title,
    companyName: job.company.name,
    location: job.location,
    contractType: job.contractType,
    minExperienceYears: job.minExperienceYears,
    educationLevel: job.educationLevel,
    requiredSkills: job.requiredSkills,
  };
}

/** Assistant RH du candidat : questions sur les offres publiées et le fonctionnement de la plateforme. */
@Component({
  selector: 'app-chatbot-page',
  imports: [ReactiveFormsModule, RouterLink, LabelPipe],
  templateUrl: './chatbot-page.html',
  styleUrl: './chatbot-page.scss',
})
export class ChatbotPage implements OnInit {
  private readonly chatbot = inject(ChatbotService);
  private readonly jobs = inject(JobOfferService);
  private readonly auth = inject(AuthService);
  protected readonly conversation = inject(ChatbotConversation);

  /** Paramètre ?offre=ID : question sur une offre précise (bouton de la page de l'offre). */
  readonly offre = input<string | undefined>();

  readonly sending = signal(false);
  readonly contextError = signal<string | null>(null);
  readonly question = new FormControl('', {
    nonNullable: true,
    validators: [Validators.maxLength(MAX_QUESTION_LENGTH)],
  });

  protected readonly maxLength = MAX_QUESTION_LENGTH;
  protected readonly actionLinks = ACTION_LINKS;
  protected readonly understood = understood;
  private readonly scroller = viewChild<ElementRef<HTMLElement>>('scroller');
  private readonly input = viewChild<ElementRef<HTMLTextAreaElement>>('questionInput');

  constructor() {
    effect(() => {
      this.conversation.messages();
      this.sending();
      const element = this.scroller()?.nativeElement;
      if (element) {
        queueMicrotask(() => (element.scrollTop = element.scrollHeight));
      }
    });
  }

  ngOnInit(): void {
    this.conversation.claim(this.auth.user()?.id ?? null);
    if (this.conversation.suggestions().length === 0) {
      this.conversation.suggestions.set(DEFAULT_SUGGESTIONS);
    }
    const id = Number(this.offre());
    if (this.offre() && Number.isInteger(id) && id > 0) {
      this.jobs.getPublished(id).subscribe({
        next: (job) => this.selectOffer(toChatbotOffer(job)),
        error: () => this.contextError.set("Cette offre n'est plus disponible : posez une question générale ou choisissez une autre offre."),
      });
    }
  }

  canSend(): boolean {
    return !this.sending() && this.question.value.trim().length > 0 && this.question.valid;
  }

  send(text: string = this.question.value): void {
    const message = text.trim();
    if (!message || message.length > MAX_QUESTION_LENGTH || this.sending()) {
      return;
    }
    this.question.setValue('');
    this.conversation.add({ author: 'candidate', text: message });
    this.ask(message, this.conversation.contextOffer()?.id ?? null);
  }

  retry(failed: ChatMessage): void {
    if (!failed.failedQuestion || this.sending()) {
      return;
    }
    this.conversation.remove(failed.id);
    this.ask(failed.failedQuestion.message, failed.failedQuestion.jobOfferId);
  }

  onSubmit(event: Event): void {
    event.preventDefault();
    this.send();
  }

  onEnter(event: Event): void {
    const keyboard = event as KeyboardEvent;
    if (!keyboard.shiftKey) {
      keyboard.preventDefault();
      this.send();
    }
  }

  selectOffer(offer: ChatbotOffer): void {
    this.contextError.set(null);
    this.conversation.contextOffer.set(offer);
    this.conversation.suggestions.set(OFFER_SUGGESTIONS);
    this.input()?.nativeElement.focus();
  }

  clearContext(): void {
    this.conversation.contextOffer.set(null);
    this.conversation.suggestions.set(DEFAULT_SUGGESTIONS);
  }

  newConversation(): void {
    this.conversation.clear();
    this.conversation.suggestions.set(DEFAULT_SUGGESTIONS);
    this.contextError.set(null);
  }

  private ask(message: string, jobOfferId: number | null): void {
    this.sending.set(true);
    this.chatbot.ask(message, jobOfferId).subscribe({
      next: (reply: ChatbotReply) => {
        this.conversation.add({ author: 'assistant', text: reply.answer, reply });
        this.conversation.contextOffer.set(reply.contextOffer);
        this.conversation.suggestions.set(reply.suggestions.length ? reply.suggestions : DEFAULT_SUGGESTIONS);
        this.sending.set(false);
      },
      error: (err: unknown) => {
        this.conversation.add({
          author: 'assistant',
          text: errorMessage(err, "L'assistant n'a pas pu répondre. Réessayez dans quelques instants."),
          failedQuestion: { message, jobOfferId },
        });
        this.sending.set(false);
      },
    });
  }
}
