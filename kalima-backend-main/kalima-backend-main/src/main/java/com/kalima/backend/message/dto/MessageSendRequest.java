package com.kalima.backend.message.dto;

import jakarta.validation.constraints.Size;

import java.util.UUID;

public record MessageSendRequest(
        /** Text content. May be blank/null only when attachmentUrl is provided. */
        @Size(max = 4000)
        String content,

        /** Pre-uploaded attachment URL from POST /api/messages/attachments. Optional. */
        @Size(max = 500)
        String attachmentUrl,

        /** ID of the message this is a reply to. Optional. */
        UUID replyToMessageId
) {
    /** Convenience: at least one of content or attachmentUrl must be present. */
    public boolean isValid() {
        boolean hasContent = content != null && !content.isBlank();
        boolean hasAttach  = attachmentUrl != null && !attachmentUrl.isBlank();
        return hasContent || hasAttach;
    }
}

