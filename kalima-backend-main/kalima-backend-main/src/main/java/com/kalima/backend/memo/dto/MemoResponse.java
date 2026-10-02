package com.kalima.backend.memo.dto;

import com.kalima.backend.casefile.dto.PartySummary;
import com.kalima.backend.memo.entity.MemoStatus;
import com.kalima.backend.memo.entity.MemoType;

import java.time.LocalDateTime;
import java.util.UUID;

public record MemoResponse(
        UUID id,
        UUID caseId,
        String caseNumber,
        String caseTitle,
        String title,
        MemoType memoType,
        MemoStatus status,
        PartySummary author,
        PartySummary reviewer,
        String content,
        LocalDateTime submittedAt,
        LocalDateTime approvedAt,
        LocalDateTime sentAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}