import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { SESSION_STORAGE_KEY } from '../../../core/auth/auth.service';
import { ChatbotOffer, ChatbotReply } from '../../../core/models';
import { ChatbotConversation } from '../../../core/services/chatbot.service';
import { ChatbotPage, DEFAULT_SUGGESTIONS, OFFER_SUGGESTIONS, understood } from './chatbot-page';

const URL = '/api/candidate/chatbot/messages';

const JAVA_OFFER: ChatbotOffer = {
  id: 11,
  title: 'Développeur Java Spring Boot',
  companyName: 'Acme',
  location: 'Tunis',
  contractType: 'CDI',
  minExperienceYears: 2,
  educationLevel: 'BAC_PLUS_5',
  requiredSkills: ['Docker', 'Java', 'Spring Boot'],
};

function reply(overrides: Partial<ChatbotReply> = {}): ChatbotReply {
  return {
    intent: 'SEARCH_OFFERS',
    answer: "J'ai trouvé 1 offre publiée qui demande Java.",
    confidence: 1,
    offers: [JAVA_OFFER],
    totalMatches: 1,
    contextOffer: null,
    entities: { skills: ['Java'], experienceYears: null, educationLevel: null, contractType: null, location: null, keywords: [] },
    suggestions: ['Comment postuler à une offre ?'],
    actions: [],
    chatbotVersion: '1.0.0',
    ...overrides,
  };
}

function loginAs(id: number): void {
  localStorage.setItem(
    SESSION_STORAGE_KEY,
    JSON.stringify({
      token: 'header.payload.signature',
      user: { id, email: 'alice@test.com', firstName: 'Alice', lastName: 'Test', role: 'CANDIDAT' },
      expiresAt: Date.now() + 3_600_000,
    }),
  );
}

describe('ChatbotPage', () => {
  let http: HttpTestingController;
  let fixture: ComponentFixture<ChatbotPage>;
  let el: HTMLElement;

  async function open(offre?: string, before?: () => void): Promise<void> {
    loginAs(1);
    TestBed.configureTestingModule({
      imports: [ChatbotPage],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    http = TestBed.inject(HttpTestingController);
    before?.();
    fixture = TestBed.createComponent(ChatbotPage);
    if (offre) {
      fixture.componentRef.setInput('offre', offre);
    }
    fixture.detectChanges();
    await fixture.whenStable();
    el = fixture.nativeElement as HTMLElement;
  }

  async function render(): Promise<void> {
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  }

  function all(id: string): HTMLElement[] {
    return Array.from(el.querySelectorAll<HTMLElement>(`[data-testid=${id}]`));
  }

  function text(id: string): string {
    return el.querySelector(`[data-testid=${id}]`)?.textContent?.replace(/\s+/g, ' ').trim() ?? '';
  }

  async function type(value: string): Promise<void> {
    const input = el.querySelector<HTMLTextAreaElement>('[data-testid=chat-input]')!;
    input.value = value;
    input.dispatchEvent(new Event('input'));
    await render();
  }

  async function submit(): Promise<void> {
    expect(el.querySelector<HTMLButtonElement>('[data-testid=chat-send]')!.disabled).toBe(false);
    el.querySelector('form')!.dispatchEvent(new Event('submit'));
    await render();
  }

  afterEach(() => {
    http.verify();
    localStorage.clear();
  });

  it('shows the welcome message, the default suggestions and a disabled send button', async () => {
    await open();

    expect(text('chat-messages')).toContain('Je peux rechercher des offres publiées');
    expect(text('chat-disclaimer')).toContain('aucune décision de recrutement');
    expect(text('chat-suggestions')).toContain(DEFAULT_SUGGESTIONS[0]);
    expect(el.querySelector<HTMLButtonElement>('[data-testid=chat-send]')!.disabled).toBe(true);
  });

  it('never sends an empty or blank question', async () => {
    await open();
    await type('   ');

    expect(el.querySelector<HTMLButtonElement>('[data-testid=chat-send]')!.disabled).toBe(true);
    fixture.componentInstance.send();
    http.expectNone(URL);
  });

  it('sends the question, shows the loading state, then the answer with real offers', async () => {
    await open();
    await type('  Quelles offres correspondent à Java ?  ');
    await submit();

    const request = http.expectOne(URL);
    expect(request.request.body).toEqual({ message: 'Quelles offres correspondent à Java ?', jobOfferId: null });
    expect(text('chat-user-message')).toBe('Quelles offres correspondent à Java ?');
    expect(all('chat-typing')).toHaveLength(1);
    expect(el.querySelector<HTMLTextAreaElement>('[data-testid=chat-input]')!.value).toBe('');

    request.flush(reply());
    await render();

    expect(all('chat-typing')).toHaveLength(0);
    expect(text('chat-answer')).toBe("J'ai trouvé 1 offre publiée qui demande Java.");
    expect(text('chat-understood')).toBe('Compris : Java');
    expect(text('chat-offer-title')).toBe('Développeur Java Spring Boot');
    expect(el.querySelector('[data-testid=chat-offer-link]')!.getAttribute('href')).toBe('/candidate/jobs/11');
    expect(text('chat-suggestions')).toBe('Comment postuler à une offre ?');
  });

  it('asks the next question about an offer selected in an answer', async () => {
    await open();
    await type('Java');
    await submit();
    http.expectOne(URL).flush(reply());
    await render();

    el.querySelector<HTMLButtonElement>('[data-testid=chat-offer-select]')!.click();
    await render();
    expect(text('chat-context')).toContain('Développeur Java Spring Boot');
    expect(text('chat-suggestions')).toContain(OFFER_SUGGESTIONS[0]);

    await type("Combien d'années d'expérience sont nécessaires ?");
    await submit();
    const request = http.expectOne(URL);
    expect(request.request.body.jobOfferId).toBe(11);
    request.flush(reply({
      intent: 'OFFER_EXPERIENCE',
      answer: "L'offre « Développeur Java Spring Boot » (Acme) demande au moins 2 ans d'expérience.",
      contextOffer: JAVA_OFFER,
    }));
    await render();

    expect(all('chat-answer').at(-1)!.textContent).toContain('au moins 2 ans');
    el.querySelector<HTMLButtonElement>('[data-testid=chat-context-clear]')!.click();
    await render();
    expect(all('chat-context')).toHaveLength(0);
  });

  it('starts with the offer passed by the offer page', async () => {
    await open('5');
    http.expectOne('/api/jobs/5').flush({
      id: 5, title: 'Data Scientist', description: 'x', location: 'Tunis', contractType: 'STAGE', minExperienceYears: 0,
      educationLevel: null, salaryMin: null, salaryMax: null, status: 'PUBLIEE', requiredSkills: ['Python'],
      company: { id: 1, name: 'DataLab', sector: null, website: null }, createdAt: '', updatedAt: '',
    });
    await render();

    expect(text('chat-context')).toContain('Data Scientist');
    await type('Quel niveau est demandé ?');
    await submit();
    expect(http.expectOne(URL).request.body.jobOfferId).toBe(5);
  });

  it('warns when the offer passed by the offer page is no longer available', async () => {
    await open('99');
    http.expectOne('/api/jobs/99').flush({ message: 'Offre introuvable' }, { status: 404, statusText: 'Not Found' });
    await render();

    expect(text('chat-context-error')).toContain("n'est plus disponible");
    expect(all('chat-context')).toHaveLength(0);
  });

  it('displays the AI service error and retries the same question', async () => {
    await open();
    await type('Comment postuler ?');
    await submit();
    http.expectOne(URL).flush(
      { message: 'Le service IA est momentanément indisponible. Réessayez plus tard.' },
      { status: 503, statusText: 'Service Unavailable' },
    );
    await render();

    expect(text('chat-error')).toContain('momentanément indisponible');
    el.querySelector<HTMLButtonElement>('[data-testid=chat-retry]')!.click();
    await render();
    const retry = http.expectOne(URL);
    expect(retry.request.body.message).toBe('Comment postuler ?');
    retry.flush(reply({ intent: 'FAQ_APPLY', answer: 'Pour postuler : ...', offers: [], totalMatches: 0, actions: ['OPEN_JOBS', 'OPEN_APPLICATIONS'] }));
    await render();

    expect(all('chat-error')).toHaveLength(0);
    expect(all('chat-user-message')).toHaveLength(1);
    expect(all('chat-action').map((a) => a.getAttribute('href'))).toEqual(['/candidate/jobs', '/candidate/applications']);
  });

  it('sends with Enter but not with Shift+Enter', async () => {
    await open();
    await type('Bonjour');
    const input = el.querySelector<HTMLTextAreaElement>('[data-testid=chat-input]')!;

    input.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', shiftKey: true }));
    http.expectNone(URL);
    input.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter' }));
    http.expectOne(URL).flush(reply({ intent: 'GREETING', answer: 'Bonjour !', offers: [], totalMatches: 0 }));
  });

  it('clicking a suggestion asks it directly', async () => {
    await open();
    (el.querySelector('[data-testid=chat-suggestions] button') as HTMLButtonElement).click();
    await render();

    expect(http.expectOne(URL).request.body.message).toBe(DEFAULT_SUGGESTIONS[0]);
  });

  it('does not show the conversation of another user', async () => {
    await open(undefined, () => {
      const conversation = TestBed.inject(ChatbotConversation);
      conversation.claim(42);
      conversation.add({ author: 'candidate', text: 'Question privée' });
    });

    expect(all('chat-user-message')).toHaveLength(0);
  });
});

describe('understood', () => {
  it('lists what the assistant extracted from the question', () => {
    expect(understood({ skills: ['Java'], experienceYears: 0, educationLevel: 'BAC_PLUS_3', contractType: 'STAGE', location: 'Tunis', keywords: [] }))
      .toEqual(['Java', 'Stage', 'Tunis', 'Débutant', 'Bac+3']);
    expect(understood({ skills: [], experienceYears: 2, educationLevel: null, contractType: 'CDI', location: null, keywords: ['data'] }))
      .toEqual(['CDI', "2 an(s) d'expérience", '« data »']);
  });
});
