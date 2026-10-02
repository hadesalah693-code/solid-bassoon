package com.kalima.backend.casefile.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CaseInternalNoteCreateRequest(
        @NotBlank @Size(max = 10000) String content
) {
}