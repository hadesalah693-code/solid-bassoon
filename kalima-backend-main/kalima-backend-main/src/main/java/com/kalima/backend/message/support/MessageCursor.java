package com.kalima.backend.message.support;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.UUID;

public final class MessageCursor {

    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    private MessageCursor() {
    }

    public record Point(LocalDateTime createdAt, UUID messageId) {}

    public static String encode(LocalDateTime createdAt, UUID messageId) {
        String raw = createdAt.toString() + "|" + messageId;
        return ENCODER.encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    public static Point decode(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid cursor");
        }
        try {
            String raw = new String(DECODER.decode(cursor), StandardCharsets.UTF_8);
            int sep = raw.lastIndexOf('|');
            if (sep <= 0 || sep == raw.length() - 1) {
                throw new IllegalArgumentException("bad format");
            }
            LocalDateTime createdAt = LocalDateTime.parse(raw.substring(0, sep));
            UUID messageId = UUID.fromString(raw.substring(sep + 1));
            return new Point(createdAt, messageId);
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid cursor");
        }
    }
}
