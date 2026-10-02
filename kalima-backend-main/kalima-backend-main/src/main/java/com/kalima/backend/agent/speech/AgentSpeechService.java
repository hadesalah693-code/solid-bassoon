package com.kalima.backend.agent.speech;

import com.kalima.backend.agent.speech.dto.SpeechConfigResponse;
import com.kalima.backend.agent.speech.dto.SpeechVoiceOption;
import com.kalima.backend.agent.speech.dto.SttResponse;
import com.kalima.backend.agent.speech.dto.TtsResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AgentSpeechService {

    private final AzureSpeechTtsClient azureTts;
    private final GroqWhisperClient groqWhisper;

    public AgentSpeechService(AzureSpeechTtsClient azureTts, GroqWhisperClient groqWhisper) {
        this.azureTts = azureTts;
        this.groqWhisper = groqWhisper;
    }

    public SpeechConfigResponse config() {
        String tts = azureTts.isEnabled() ? "AZURE" : "BROWSER";
        String stt = groqWhisper.isEnabled() ? "GROQ_WHISPER" : "BROWSER";
        String voice = azureTts.isEnabled()
                ? AzureArabicVoices.resolve(azureTts.voiceName(), azureTts.voiceName())
                : null;
        boolean ready = azureTts.isEnabled() || groqWhisper.isEnabled();
        var voices = azureTts.isEnabled() ? AzureArabicVoices.all() : java.util.List.<SpeechVoiceOption>of();
        return new SpeechConfigResponse(tts, stt, voice, ready, voices);
    }

    public TtsResponse synthesize(String text, String voice) {
        if (!azureTts.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Neural TTS requires AZURE_SPEECH_KEY. Browser TTS is used as fallback.");
        }
        String resolved = AzureArabicVoices.resolve(voice, azureTts.voiceName());
        return new TtsResponse(
                azureTts.synthesizeBase64(text, resolved),
                "audio/mpeg",
                "AZURE",
                resolved);
    }

    public SttResponse transcribe(MultipartFile file) {
        if (!groqWhisper.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Cloud STT requires GROQ_API_KEY.");
        }
        try {
            String text = groqWhisper.transcribe(
                    file.getBytes(),
                    file.getOriginalFilename(),
                    file.getContentType());
            return new SttResponse(text, "GROQ_WHISPER");
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Invalid audio upload.");
        }
    }
}
