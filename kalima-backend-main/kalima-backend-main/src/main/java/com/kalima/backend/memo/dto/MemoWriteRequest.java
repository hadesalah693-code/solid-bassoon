package com.kalima.backend.memo.dto;

import com.kalima.backend.memo.entity.MemoType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record MemoWriteRequest(
        @NotBlank @Size(max = 250) String title,
        @NotNull MemoType memoType,
        @NotBlank @Size(max = 100000) String content
) {
}