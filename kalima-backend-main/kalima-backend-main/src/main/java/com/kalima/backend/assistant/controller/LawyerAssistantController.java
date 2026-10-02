package com.kalima.backend.assistant.controller;

import com.kalima.backend.assistant.dto.AssistantAskRequest;
import com.kalima.backend.assistant.dto.AssistantAskResponse;
import com.kalima.backend.assistant.service.LawyerAssistantService;
import com.kalima.backend.domain.tenant.TenantContext;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Lawyer Assistant — staff-only AI helper backed by Groq/Gemini.
 */
@RestController
@RequestMapping("/api/lawyer-assistant")
public class LawyerAssistantController {

    private static final long STREAM_TIMEOUT_MS = 120_000L;

    private final LawyerAssistantService service;

    public LawyerAssistantController(LawyerAssistantService service) {
        this.service = service;
    }

    @PostMapping("/ask")
    @PreAuthorize("hasAnyRole('ADMIN','SENIOR_LAWYER','LAWYER','SECRETARY','ACCOUNTANT')")
    public AssistantAskResponse ask(@Valid @RequestBody AssistantAskRequest request) {
        return service.ask(request);
    }

    @PostMapping(value = "/ask/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN','SENIOR_LAWYER','LAWYER','SECRETARY','ACCOUNTANT')")
    public SseEmitter askStream(@Valid @RequestBody AssistantAskRequest request) {
        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MS);
        UUID tenantId = TenantContext.get().orElse(null);
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        CompletableFuture.runAsync(() -> {
            try {
                if (tenantId != null) TenantContext.set(tenantId);
                SecurityContextHolder.getContext().setAuthentication(auth);
                service.streamAsk(request, emitter);
            } finally {
                TenantContext.clear();
                SecurityContextHolder.clearContext();
            }
        });
        return emitter;
    }
}
