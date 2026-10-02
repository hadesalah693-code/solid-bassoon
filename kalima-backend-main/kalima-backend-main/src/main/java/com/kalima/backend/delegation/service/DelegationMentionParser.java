package com.kalima.backend.delegation.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Detects {@code @name} tokens in delegation notes by matching against known staff
 * full names (same format as {@code StaffMentionTextarea} inserts: {@code @name }).
 */
public final class DelegationMentionParser {

    private DelegationMentionParser() {
    }

    public static List<String> extractMentionLabels(String text, Collection<String> knownStaffNames) {
        if (text == null || text.isBlank() || knownStaffNames == null || knownStaffNames.isEmpty()) {
            return List.of();
        }
        List<String> sorted = knownStaffNames.stream()
                .filter(name -> name != null && !name.isBlank())
                .map(String::trim)
                .sorted(Comparator.comparingInt(String::length).reversed())
                .toList();
        Set<String> found = new LinkedHashSet<>();
        for (String name : sorted) {
            if (containsMention(text, name)) {
                found.add(name);
            }
        }
        return List.copyOf(found);
    }

    public static Set<String> labelsOnlyInNewText(
            String previousText,
            String newText,
            Collection<String> knownStaffNames) {
        Set<String> previous = new LinkedHashSet<>(extractMentionLabels(previousText, knownStaffNames));
        Set<String> added = new LinkedHashSet<>();
        for (String label : extractMentionLabels(newText, knownStaffNames)) {
            if (!previous.contains(label)) {
                added.add(label);
            }
        }
        return added;
    }

    static boolean containsMention(String text, String name) {
        String token = "@" + name.trim();
        int from = 0;
        while (from < text.length()) {
            int idx = text.indexOf(token, from);
            if (idx < 0) {
                return false;
            }
            int end = idx + token.length();
            if (end >= text.length() || Character.isWhitespace(text.charAt(end))) {
                return true;
            }
            from = idx + 1;
        }
        return false;
    }
}
