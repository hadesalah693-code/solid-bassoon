package com.kalima.backend.assistant.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for the Groq fallback provider used by the Lawyer Assistant.
 *
 * <p>Values are read from {@code groq.*} properties, which in dev resolve from
 * {@code GROQ_*} environment variables / the local {@code .env} file.
 */
@Configuration
@ConfigurationProperties(prefix = "groq")
public class GroqProperties {

    /** Groq API key. When blank, fallback is disabled. */
    private String apiKey = "";

    /** Groq model id. */
    private String model = "llama-3.3-70b-versatile";

    /** OpenAI-compatible chat completions endpoint. */
    private String endpoint = "https://api.groq.com/openai/v1/chat/completions";

    /** Per-request HTTP timeout. */
    private int timeoutSeconds = 30;

    /** Hard cap on tokens Groq may produce. */
    private int maxOutputTokens = 800;

    /** Sampling temperature; lower = more deterministic. */
    private double temperature = 0.4;

    /** Whisper model for voice transcription (agent speech STT). */
    private String whisperModel = "whisper-large-v3";

    /** OpenAI-compatible audio transcriptions endpoint. */
    private String whisperEndpoint = "https://api.groq.com/openai/v1/audio/transcriptions";

    public boolean isEnabled() {
        return apiKey != null && !apiKey.isBlank();
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public int getMaxOutputTokens() {
        return maxOutputTokens;
    }

    public void setMaxOutputTokens(int maxOutputTokens) {
        this.maxOutputTokens = maxOutputTokens;
    }

    public double getTemperature() {
        return temperature;
    }

    public void setTemperature(double temperature) {
        this.temperature = temperature;
    }

    public String getWhisperModel() {
        return whisperModel;
    }

    public void setWhisperModel(String whisperModel) {
        this.whisperModel = whisperModel;
    }

    public String getWhisperEndpoint() {
        return whisperEndpoint;
    }

    public void setWhisperEndpoint(String whisperEndpoint) {
        this.whisperEndpoint = whisperEndpoint;
    }
}
