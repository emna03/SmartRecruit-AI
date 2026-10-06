import { HttpClient } from '@angular/common/http';
import { inject, Injectable, signal } from '@angular/core';
import { Observable } from 'rxjs';
import { API_URL } from '../api.config';
import { ChatbotOffer, ChatbotQuestion, ChatbotReply } from '../models';

/** Chatbot RH : la question passe par Spring Boot, qui interroge le service IA avec les offres publiées. */
@Injectable({ providedIn: 'root' })
export class ChatbotService {
  private readonly http = inject(HttpClient);

  ask(message: string, jobOfferId: number | null): Observable<ChatbotReply> {
    const body: ChatbotQuestion = { message, jobOfferId };
    return this.http.post<ChatbotReply>(`${API_URL}/candidate/chatbot/messages`, body);
  }
}

export interface ChatMessage {
  id: number;
  author: 'candidate' | 'assistant';
  text: string;
  reply?: ChatbotReply;
  /** Question à renvoyer quand la réponse a échoué. */
  failedQuestion?: { message: string; jobOfferId: number | null };
}

/**
 * Conversation en cours, gardée en mémoire seulement (jamais enregistrée ni stockée dans le navigateur) :
 * elle survit à la navigation vers une offre et disparaît au rechargement ou au changement d'utilisateur.
 */
@Injectable({ providedIn: 'root' })
export class ChatbotConversation {
  readonly messages = signal<ChatMessage[]>([]);
  readonly contextOffer = signal<ChatbotOffer | null>(null);
  readonly suggestions = signal<string[]>([]);
  private ownerId: number | null = null;
  private nextId = 1;

  /** Vide la conversation si elle appartient à un autre utilisateur. */
  claim(userId: number | null): void {
    if (this.ownerId !== userId) {
      this.clear();
      this.ownerId = userId;
    }
  }

  add(message: Omit<ChatMessage, 'id'>): void {
    this.messages.update((list) => [...list, { ...message, id: this.nextId++ }]);
  }

  remove(id: number): void {
    this.messages.update((list) => list.filter((m) => m.id !== id));
  }

  clear(): void {
    this.messages.set([]);
    this.contextOffer.set(null);
    this.suggestions.set([]);
  }
}
