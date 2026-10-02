package com.kalima.backend.agent.speech;

import com.kalima.backend.assistant.config.AzureSpeechProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.Base64;

/**
 * Azure neural text-to-speech (MP3). Used for natural Arabic voice output.
 */
@Component
public class AzureSpeechTtsClient {

    private static final Logger log = LoggerFactory.getLogger(AzureSpeechTtsClient.class);

    private final AzureSpeechProperties props;
    private final RestTemplate restTemplate;

    public AzureSpeechTtsClient(AzureSpeechProperties props, RestTemplateBuilder builder) {
        this.props = props;
        Duration timeout = Duration.ofSeconds(Math.max(5, props.getTimeoutSeconds()));
        this.restTemplate = builder
                .connectTimeout(timeout)
                .readTimeout(timeout)
                .build();
    }

    public boolean isEnabled() {
        return props.isEnabled();
    }

    public String voiceName() {
        return props.getVoice();
    }

    public byte[] synthesizeMp3(String text) {
        return synthesizeMp3(text, null);
    }

    public byte[] synthesizeMp3(String text, String voiceOverride) {
        if (!props.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Azure Speech is not configured. Set AZURE_SPEECH_KEY and AZURE_SPEECH_REGION.");
        }
        String trimmed = text == null ? "" : text.trim();
        if (trimmed.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Text is required.");
        }

        String voice = AzureArabicVoices.resolve(voiceOverride, props.getVoice());
        String ssml = buildSsml(trimmed, voice);
        String url = "https://" + props.getRegion().trim()
                + ".tts.speech.microsoft.com/cognitiveservices/v1";

        HttpHeaders headers = new HttpHeaders();
        headers.set("Ocp-Apim-Subscription-Key", props.getKey());
        headers.setContentType(MediaType.parseMediaType("application/ssml+xml"));
        headers.set("X-Microsoft-OutputFormat", "audio-24khz-48kbitrate-mono-mp3");
        headers.setAccept(java.util.List.of(MediaType.APPLICATION_OCTET_STREAM));

        HttpEntity<String> entity = new HttpEntity<>(ssml, headers);
        try {
            ResponseEntity<byte[]> response = restTemplate.exchange(
                    url, HttpMethod.POST, entity, byte[].class);
            byte[] body = response.getBody();
            if (body == null || body.length == 0) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "Azure Speech returned empty audio.");
            }
            return body;
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("Azure TTS failed: {}", ex.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Azure Speech synthesis failed.");
        }
    }

    public String synthesizeBase64(String text, String voiceOverride) {
        return Base64.getEncoder().encodeToString(synthesizeMp3(text, voiceOverride));
    }

    private String buildSsml(String text, String voice) {
        String lang = voiceLanguage(voice);
        return """
                <speak version='1.0' xml:lang='%s'>
                  <voice name='%s'>%s</voice>
                </speak>
                """.formatted(lang, voice, escapeXml(text));
    }

    private static String voiceLanguage(String voice) {
        int first = voice.indexOf('-');
        if (first < 0) {
            return "ar-EG";
        }
        int second = voice.indexOf('-', first + 1);
        if (second < 0) {
            return voice;
        }
        return voice.substring(0, second);
    }

    private static String escapeXml(String raw) {
        return raw
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
