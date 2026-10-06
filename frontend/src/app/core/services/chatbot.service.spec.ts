import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ChatbotConversation, ChatbotService } from './chatbot.service';

describe('ChatbotService', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('posts the question and the offer under discussion to the candidate endpoint', () => {
    TestBed.inject(ChatbotService).ask('Quel niveau est demandé ?', 7).subscribe();

    const request = http.expectOne('/api/candidate/chatbot/messages');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ message: 'Quel niveau est demandé ?', jobOfferId: 7 });
    request.flush({});
  });
});

describe('ChatbotConversation', () => {
  it('keeps messages for the same user and clears them for another one', () => {
    const conversation = TestBed.inject(ChatbotConversation);
    conversation.claim(1);
    conversation.add({ author: 'candidate', text: 'Bonjour' });

    conversation.claim(1);
    expect(conversation.messages()).toHaveLength(1);

    conversation.claim(2);
    expect(conversation.messages()).toHaveLength(0);
    expect(conversation.contextOffer()).toBeNull();
  });

  it('gives each message a unique id and removes a message by id', () => {
    const conversation = TestBed.inject(ChatbotConversation);
    conversation.add({ author: 'candidate', text: 'A' });
    conversation.add({ author: 'candidate', text: 'B' });
    const [first, second] = conversation.messages();

    expect(first.id).not.toBe(second.id);
    conversation.remove(first.id);
    expect(conversation.messages().map((m) => m.text)).toEqual(['B']);
  });
});
