package com.kalima.backend.ocr.dto;

import java.util.UUID;

public record OcrActiveUserStat(
        UUID userId,
        String fullName,
        long uploadCount
) {
}
