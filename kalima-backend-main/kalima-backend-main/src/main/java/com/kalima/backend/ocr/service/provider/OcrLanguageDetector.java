package com.kalima.backend.ocr.service.provider;

import java.util.Locale;

final class OcrLanguageDetector {

    private OcrLanguageDetector() {
    }

    static String fromAzureLocales(java.util.List<String> locales) {
        if (locales == null || locales.isEmpty()) {
            return null;
        }
        boolean arabic = false;
        boolean english = false;
        for (String locale : locales) {
            if (locale == null || locale.isBlank()) {
                continue;
            }
            String lower = locale.toLowerCase(Locale.ROOT);
            if (lower.startsWith("ar")) {
                arabic = true;
            } else if (lower.startsWith("en")) {
                english = true;
            }
        }
        if (arabic && english) {
            return "ar-en";
        }
        if (arabic) {
            return "ar";
        }
        if (english) {
            return "en";
        }
        return locales.get(0);
    }

    static String fromText(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        boolean arabic = false;
        boolean latin = false;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (Character.UnicodeBlock.of(ch) == Character.UnicodeBlock.ARABIC) {
                arabic = true;
            } else if (Character.isLetter(ch) && ch <= 127) {
                latin = true;
            }
        }
        if (arabic && latin) {
            return "ar-en";
        }
        if (arabic) {
            return "ar";
        }
        if (latin) {
            return "en";
        }
        return null;
    }
}
