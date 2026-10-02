package com.kalima.backend.realtime.dto;

import java.util.UUID;

/**
 * Broadcast payload sent to {@code /topic/cases/{caseId}/typing} whenever a
 * user is typing in that case's chat. Subscribers filter out their own events
 * by comparing {@link #personId} against the connected user's ID.
 */
public record TypingEvent(UUID personId, String personName) {
}
