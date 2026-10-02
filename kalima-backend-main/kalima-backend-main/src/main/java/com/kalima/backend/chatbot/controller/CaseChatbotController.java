package com.kalima.backend.chatbot.controller;

import com.kalima.backend.chatbot.dto.ChatbotAskRequest;
import com.kalima.backend.chatbot.dto.ChatbotAskResponse;
import com.kalima.backend.chatbot.service.CaseChatbotService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Per-case chatbot endpoint. Same role matrix as
 * {@link com.kalima.backend.message.controller.MessageController} — the
 * service additionally enforces fine-grained access (CLIENT may only ask
 * about their own case; assigned lawyer / ADMIN / SENIOR_LAWYER for staff).
 */
@RestController
@RequestMapping("/api/cases/{caseId}/chatbot")
public class CaseChatbotController {

    private final CaseChatbotService service;

    public CaseChatbotController(CaseChatbotService service) {
        this.service = service;
    }

    @PostMapping("/ask")
    @PreAuthorize("hasAnyRole('ADMIN','SENIOR_LAWYER','LAWYER','CLIENT')")
    public ChatbotAskResponse ask(
            @PathVariable UUID caseId,
            @Valid @RequestBody ChatbotAskRequest request) {
        return service.ask(caseId, request);
    }
}
