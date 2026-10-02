package com.kalima.backend.chat.controller;

import com.kalima.backend.chat.dto.ChatScopeCase;
import com.kalima.backend.chat.dto.ChatScopeClient;
import com.kalima.backend.chat.service.ChatScopeService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Chat picker scope: lists of clients and cases the authenticated user
 * is allowed to chat with / in. Authoritative — frontend cannot bypass
 * this filter, and follow-up message/chatbot endpoints re-check per-case
 * access.
 */
@RestController
@RequestMapping("/api/chat/scope")
public class ChatScopeController {

    private final ChatScopeService service;

    public ChatScopeController(ChatScopeService service) {
        this.service = service;
    }

    @GetMapping("/clients")
    @PreAuthorize("hasAnyRole('ADMIN','SENIOR_LAWYER','LAWYER','CLIENT')")
    public List<ChatScopeClient> clients() {
        return service.listClients();
    }

    @GetMapping("/cases")
    @PreAuthorize("hasAnyRole('ADMIN','SENIOR_LAWYER','LAWYER','CLIENT')")
    public List<ChatScopeCase> cases(@RequestParam("clientId") UUID clientId) {
        return service.listCasesForClient(clientId);
    }
}
