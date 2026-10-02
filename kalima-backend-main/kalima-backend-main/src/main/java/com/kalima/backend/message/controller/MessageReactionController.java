package com.kalima.backend.message.controller;

import com.kalima.backend.message.service.MessageReactionService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * {@code POST /api/cases/{caseId}/messages/{messageId}/reactions}
 * <p>Toggles an emoji reaction. If the user has already reacted with the same
 * emoji it is removed; otherwise it is added.
 */
@RestController
@RequestMapping("/api/cases/{caseId}/messages/{messageId}/reactions")
public class MessageReactionController {

    private final MessageReactionService service;

    public MessageReactionController(MessageReactionService service) {
        this.service = service;
    }

    public record ToggleRequest(
            @NotBlank @Size(max = 10) String emoji) {}

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','SENIOR_LAWYER','LAWYER','CLIENT')")
    public Map<String, Object> toggle(
            @PathVariable UUID caseId,
            @PathVariable UUID messageId,
            @RequestBody ToggleRequest body) {
        var result = service.toggle(caseId, messageId, body.emoji());
        return Map.of(
                "reactions",  result.reactions(),
                "myReaction", result.myReaction() != null ? result.myReaction() : "");
    }
}
