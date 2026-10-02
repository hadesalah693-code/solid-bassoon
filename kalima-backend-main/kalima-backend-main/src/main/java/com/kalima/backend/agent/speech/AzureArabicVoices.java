package com.kalima.backend.agent.speech;

import com.kalima.backend.agent.speech.dto.SpeechVoiceOption;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Curated Azure neural Arabic voices exposed to the client for TTS selection.
 */
public final class AzureArabicVoices {

    private static final List<SpeechVoiceOption> VOICES = List.of(
            new SpeechVoiceOption("ar-EG-SalmaNeural", "سلمى (مصر)", "ar-EG"),
            new SpeechVoiceOption("ar-EG-ShakirNeural", "شاكر (مصر)", "ar-EG"),
            new SpeechVoiceOption("ar-SA-ZariyahNeural", "زارية (السعودية)", "ar-SA"),
            new SpeechVoiceOption("ar-SA-HamedNeural", "حامد (السعودية)", "ar-SA"),
            new SpeechVoiceOption("ar-AE-FatimaNeural", "فاطمة (الإمارات)", "ar-AE"),
            new SpeechVoiceOption("ar-AE-HamdanNeural", "حمدان (الإمارات)", "ar-AE"),
            new SpeechVoiceOption("ar-LB-LaylaNeural", "ليلى (لبنان)", "ar-LB"),
            new SpeechVoiceOption("ar-LB-RamiNeural", "رامي (لبنان)", "ar-LB"),
            new SpeechVoiceOption("ar-MA-MounaNeural", "منى (المغرب)", "ar-MA"),
            new SpeechVoiceOption("ar-MA-JamalNeural", "جمال (المغرب)", "ar-MA"),
            new SpeechVoiceOption("ar-DZ-AminaNeural", "أمينة (الجزائر)", "ar-DZ"),
            new SpeechVoiceOption("ar-DZ-IsmaelNeural", "إسماعيل (الجزائر)", "ar-DZ"),
            new SpeechVoiceOption("ar-KW-NouraNeural", "نورة (الكويت)", "ar-KW"),
            new SpeechVoiceOption("ar-KW-FahedNeural", "فهد (الكويت)", "ar-KW"),
            new SpeechVoiceOption("ar-QA-AmalNeural", "أمل (قطر)", "ar-QA"),
            new SpeechVoiceOption("ar-QA-MoazNeural", "معاذ (قطر)", "ar-QA"),
            new SpeechVoiceOption("ar-BH-LailaNeural", "ليلى (البحرين)", "ar-BH"),
            new SpeechVoiceOption("ar-BH-AliNeural", "علي (البحرين)", "ar-BH"),
            new SpeechVoiceOption("ar-OM-AyshaNeural", "عائشة (عُمان)", "ar-OM"),
            new SpeechVoiceOption("ar-OM-AbdullahNeural", "عبدالله (عُمان)", "ar-OM"),
            new SpeechVoiceOption("ar-JO-SanaNeural", "سناء (الأردن)", "ar-JO"),
            new SpeechVoiceOption("ar-JO-TaimNeural", "تيم (الأردن)", "ar-JO"),
            new SpeechVoiceOption("ar-IQ-RanaNeural", "رنا (العراق)", "ar-IQ"),
            new SpeechVoiceOption("ar-IQ-BasselNeural", "باسل (العراق)", "ar-IQ"),
            new SpeechVoiceOption("ar-TN-ReemNeural", "ريم (تونس)", "ar-TN"),
            new SpeechVoiceOption("ar-TN-HediNeural", "هدي (تونس)", "ar-TN"),
            new SpeechVoiceOption("ar-YE-MaryamNeural", "مريم (اليمن)", "ar-YE"),
            new SpeechVoiceOption("ar-YE-SalehNeural", "صالح (اليمن)", "ar-YE"),
            new SpeechVoiceOption("ar-SY-AmanyNeural", "أماني (سوريا)", "ar-SY"),
            new SpeechVoiceOption("ar-SY-LaithNeural", "ليث (سوريا)", "ar-SY")
    );

    private static final Set<String> ALLOWED_IDS = Set.copyOf(
            VOICES.stream().map(SpeechVoiceOption::id).toList());

    private AzureArabicVoices() {
    }

    public static List<SpeechVoiceOption> all() {
        return VOICES;
    }

    public static boolean isAllowed(String voiceId) {
        return voiceId != null && ALLOWED_IDS.contains(voiceId);
    }

    public static String resolve(String requested, String fallback) {
        if (requested != null && isAllowed(requested)) {
            return requested;
        }
        if (fallback != null && isAllowed(fallback)) {
            return fallback;
        }
        return VOICES.get(0).id();
    }

    public static Optional<SpeechVoiceOption> find(String voiceId) {
        return VOICES.stream().filter(v -> v.id().equals(voiceId)).findFirst();
    }
}
