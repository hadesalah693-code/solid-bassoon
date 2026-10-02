package com.kalima.backend.ocr.dto;

import com.kalima.backend.domain.entity.DomainEnums;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

import java.util.List;

public record OcrRecordUpdateRequest(
        @Size(max = 250, message = "title must be at most 250 characters")
        String title,

        String editedText,

        DomainEnums.OcrDocumentType documentType,

        @Valid
        List<OcrRelationInput> relations
) {
}
