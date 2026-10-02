package com.kalima.backend.message.dto;

import com.kalima.backend.casefile.dto.PartySummary;
import com.kalima.backend.domain.entity.DomainEnums;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * @param sender      Author of the message. {@code null} when
 *                    {@link #senderType} is
 *                    {@link DomainEnums.MessageSenderType#BOT}.
 * @param senderType  {@code USER} for human-authored messages, {@code BOT} for
 *                    chatbot replies.
 * @param replyTo     Nested preview of the parent message; {@code null} for
 *                    top-level messages.
 * @param reactions   Emoji → count map. Empty map when no reactions.
 * @param myReaction  The authenticated user's own reaction emoji, or {@code null}.
 */
public record MessageResponse(
        UUID id,
        UUID caseId,
        PartySummary sender,
        PartySummary receiver,
        DomainEnums.MessageSenderType senderType,
        String content,
        String attachmentUrl,
        ReplyPreview replyTo,
        boolean read,
        LocalDateTime readAt,
        LocalDateTime createdAt,
        Map<String, Integer> reactions,
        String myReaction
) {
    /**
     * Short preview of a parent message shown inside a reply bubble.
     */
    public record ReplyPreview(UUID id, String content, String senderName) {}
}

