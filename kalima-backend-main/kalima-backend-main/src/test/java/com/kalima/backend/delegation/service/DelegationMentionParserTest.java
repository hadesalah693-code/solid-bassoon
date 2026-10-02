package com.kalima.backend.delegation.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DelegationMentionParserTest {

    private static final List<String> KNOWN_STAFF = List.of(
            "Hardening Lawyer",
            "Hardening Admin",
            "ماجد ناصر");

    @Test
    void extractMentionLabels_parsesArabicAndLatinNames() {
        String text = "مرحباً @Hardening Lawyer و @ماجد ناصر" + " للمتابعة";
        List<String> labels = DelegationMentionParser.extractMentionLabels(text, KNOWN_STAFF);
        assertEquals(List.of("Hardening Lawyer", "ماجد ناصر"), labels);
    }

    @Test
    void labelsOnlyInNewText_returns_newly_added_mentions() {
        Set<String> added = DelegationMentionParser.labelsOnlyInNewText(
                "ملاحظة @Hardening Lawyer ",
                "ملاحظة @Hardening Lawyer و @Hardening Admin ",
                KNOWN_STAFF);
        assertEquals(Set.of("Hardening Admin"), added);
    }

    @Test
    void extractMentionLabels_empty_when_no_mentions() {
        assertTrue(DelegationMentionParser.extractMentionLabels("بدون إشارات", KNOWN_STAFF).isEmpty());
        assertTrue(DelegationMentionParser.extractMentionLabels(null, KNOWN_STAFF).isEmpty());
    }
}
