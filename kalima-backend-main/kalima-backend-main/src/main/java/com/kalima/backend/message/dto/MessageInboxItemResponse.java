package com.kalima.backend.message.dto;

import com.kalima.backend.casefile.dto.PartySummary;

import java.util.UUID;

/**
 * One row in the messages inbox: case summary + last message preview + unread count.
 *
 * @param lastMessage {@code null} when no messages have been exchanged yet in this case.
 * @param unreadCount number of messages where the authenticated user is the receiver and
 *                    {@code is_read = false}.
 */
public record MessageInboxItemResponse(
        UUID caseId,
        String caseNumber,
        String caseTitle,
        PartySummary client,
        PartySummary assignedLawyer,
        MessageResponse lastMessage,
        long unreadCount
) {
}
