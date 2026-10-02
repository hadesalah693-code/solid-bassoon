package com.kalima.backend.delegation.settings.dto;

import com.kalima.backend.casefile.dto.PartySummary;

/**
 * Result of matching a free-text name (typically extracted via OCR from a
 * scanned power-of-attorney document) against the tenant's configured office
 * attorneys.
 */
public record DelegationAttorneyMatchResponse(
        boolean matched,
        PartySummary person,
        MatchType matchType
) {
    public enum MatchType {
        EXACT,
        PARTIAL,
        NONE
    }

    public static DelegationAttorneyMatchResponse none() {
        return new DelegationAttorneyMatchResponse(false, null, MatchType.NONE);
    }
}
