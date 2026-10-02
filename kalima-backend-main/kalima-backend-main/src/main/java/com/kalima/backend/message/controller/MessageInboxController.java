package com.kalima.backend.message.controller;

import com.kalima.backend.message.dto.MessageInboxItemResponse;
import com.kalima.backend.message.service.MessageInboxService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Optimised inbox endpoint: returns all accessible conversations with last-message
 * preview and unread count in a single response — replacing the N+1 pattern where
 * the frontend fetched all messages per case individually.
 */
@RestController
@RequestMapping("/api/messages/inbox")
public class MessageInboxController {

    private final MessageInboxService service;

    public MessageInboxController(MessageInboxService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','SENIOR_LAWYER','LAWYER','CLIENT')")
    public List<MessageInboxItemResponse> inbox() {
        return service.inbox();
    }
}
