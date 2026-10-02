package com.kalima.backend.accounting.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Account edit payload.
 *
 * <p>Deliberately separate from {@link AccountCreateRequest}: the create contract
 * requires {@code code} and {@code type}, so reusing it for the update endpoint meant
 * the edit form — which only sends names, parent and active flag — could never pass
 * validation and every account edit returned 400.
 *
 * <p>{@code code} and {@code type} are intentionally absent: an account that already
 * carries movements must not be re-coded or re-typed, because that would silently
 * restate published reports.
 */
public record AccountUpdateRequest(
        @NotBlank @Size(max = 150) String nameAr,
        @Size(max = 150) String nameEn,
        UUID parentId,
        @Size(max = 2000) String description,
        Boolean active
) {
}
