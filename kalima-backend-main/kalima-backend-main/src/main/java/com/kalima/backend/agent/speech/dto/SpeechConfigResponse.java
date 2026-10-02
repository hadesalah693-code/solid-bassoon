package com.kalima.backend.agent.speech.dto;

import java.util.List;

public record SpeechConfigResponse(
        String ttsProvider,
        String sttProvider,
        String voice,
        boolean voiceConversationReady,
        List<SpeechVoiceOption> availableVoices
) {
}
