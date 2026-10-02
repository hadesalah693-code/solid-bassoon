package com.kalima.backend.message.dto;

import java.util.List;

public record MessagePageResponse(
        List<MessageResponse> items,
        boolean hasMore,
        String nextCursor
) {}
