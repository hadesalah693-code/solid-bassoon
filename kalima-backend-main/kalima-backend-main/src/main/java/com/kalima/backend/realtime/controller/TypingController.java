package com.kalima.backend.realtime.controller;

import com.kalima.backend.realtime.service.TypingNotificationService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/cases/{caseId}/typing")
public class TypingController {

    private final TypingNotificationService typingNotificationService;

    public TypingController(TypingNotificationService typingNotificationService) {
        this.typingNotificationService = typingNotificationService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('ADMIN','SENIOR_LAWYER','LAWYER','CLIENT')")
    public void notifyTyping(@PathVariable UUID caseId) {
        typingNotificationService.notifyTyping(caseId);
    }
}
