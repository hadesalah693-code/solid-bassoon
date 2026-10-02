package com.kalima.backend.realtime.service;

import com.kalima.backend.message.dto.MessageResponse;
import com.kalima.backend.realtime.dto.TypingEvent;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Pushes a freshly-persisted {@link MessageResponse} onto the per-case STOMP
 * topic so subscribed browsers append it instantly without polling.
 *
 * <p>Authorization happens once at SUBSCRIBE time
 * (see {@link com.kalima.backend.realtime.config.WebSocketConfig}); the
 * broadcast itself is fan-out only.
 */
@Service
public class MessageBroadcaster {

    private final SimpMessagingTemplate template;

    public MessageBroadcaster(SimpMessagingTemplate template) {
        this.template = template;
    }

    public void broadcastToCase(UUID caseId, MessageResponse payload) {
        if (caseId == null || payload == null) {
            return;
        }
        template.convertAndSend("/topic/cases/" + caseId + "/messages", payload);
    }

    /**
     * Notifies all subscribers of this case that a specific user is typing.
     * The frontend filters out events where {@link TypingEvent#personId()} matches
     * the connected user's own ID so nobody sees "you are typing".
     */
    public void broadcastTyping(UUID caseId, TypingEvent event) {
        if (caseId == null || event == null) {
            return;
        }
        template.convertAndSend("/topic/cases/" + caseId + "/typing", event);
    }
}
