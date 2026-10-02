package com.kalima.backend.agent.speech;

import com.kalima.backend.assistant.config.GroqProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Duration;

/**
 * Groq Whisper transcription for clearer Arabic STT than browser speech recognition.
 */
@Component
public class GroqWhisperClient {

    private static final Logger log = LoggerFactory.getLogger(GroqWhisperClient.class);

    private final GroqProperties props;
    private final RestTemplate restTemplate;
    private final ObjectMapper mapper = new ObjectMapper();

    public GroqWhisperClient(GroqProperties props, RestTemplateBuilder builder) {
        this.props = props;
        Duration timeout = Duration.ofSeconds(Math.max(10, props.getTimeoutSeconds()));
        this.restTemplate = builder
                .connectTimeout(timeout)
                .readTimeout(timeout)
                .build();
    }

    public boolean isEnabled() {
        return props.isEnabled();
    }

    public String transcribe(byte[] audioBytes, String filename, String contentType) {
        if (!props.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Groq Whisper is not configured. Set GROQ_API_KEY.");
        }
        if (audioBytes == null || audioBytes.length == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Audio file is empty.");
        }

        String safeName = filename == null || filename.isBlank() ? "speech.webm" : filename;
        String mime = contentType == null || contentType.isBlank() ? "audio/webm" : contentType;

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(props.getApiKey());

        ByteArrayResource fileResource = new ByteArrayResource(audioBytes) {
            @Override
            public String getFilename() {
                return safeName;
            }
        };

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", fileResource);
        body.add("model", props.getWhisperModel());
        body.add("language", "ar");
        body.add("response_format", "json");

        HttpEntity<MultiValueMap<String, Object>> entity = new HttpEntity<>(body, headers);
        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    props.getWhisperEndpoint(), HttpMethod.POST, entity, String.class);
            JsonNode node = mapper.readTree(response.getBody());
            String text = node.path("text").asText("").trim();
            if (text.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Could not transcribe speech.");
            }
            return text;
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("Groq Whisper failed: {}", ex.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Speech transcription failed.");
        }
    }
}
