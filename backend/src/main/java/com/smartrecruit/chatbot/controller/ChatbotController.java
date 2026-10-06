package com.smartrecruit.chatbot.controller;

import com.smartrecruit.chatbot.dto.ChatbotQuestionRequest;
import com.smartrecruit.chatbot.dto.ChatbotReply;
import com.smartrecruit.chatbot.service.ChatbotService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Chatbot RH du candidat : réponses basées sur les offres publiées et le fonctionnement de la plateforme. */
@RestController
@RequestMapping("/api/candidate/chatbot")
@RequiredArgsConstructor
public class ChatbotController {

    private final ChatbotService chatbotService;

    @PostMapping("/messages")
    public ChatbotReply ask(@Valid @RequestBody ChatbotQuestionRequest request) {
        return chatbotService.ask(request);
    }
}
