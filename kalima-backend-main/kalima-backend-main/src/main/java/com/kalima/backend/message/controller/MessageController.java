package com.kalima.backend.message.controller;

import com.kalima.backend.message.dto.MessagePageResponse;
import com.kalima.backend.message.dto.MessageResponse;
import com.kalima.backend.message.dto.MessageSendRequest;
import com.kalima.backend.message.service.MessageService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/cases/{caseId}/messages")
public class MessageController {

    private final MessageService service;

    public MessageController(MessageService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','SENIOR_LAWYER','LAWYER','CLIENT')")
    public MessagePageResponse list(
            @PathVariable UUID caseId,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(required = false) String before) {
        return service.list(caseId, limit, before);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','SENIOR_LAWYER','LAWYER','CLIENT')")
    public ResponseEntity<MessageResponse> send(
            @PathVariable UUID caseId,
            @Valid @RequestBody MessageSendRequest request,
            UriComponentsBuilder uriBuilder) {
        MessageResponse created = service.send(caseId, request);
        URI location = uriBuilder
                .path("/api/cases/{caseId}/messages")
                .buildAndExpand(caseId)
                .toUri();
        return ResponseEntity.created(location).body(created);
    }
}
