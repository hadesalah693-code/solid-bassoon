package com.kalima.backend.assistant.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for the Google Gemini-backed Lawyer Assistant.
 *
 * <p>Values are loaded from {@code application-*.properties} which themselves
 * fall back to environment variables (or the local {@code .env} file via
 * spring-dotenv). The {@link #apiKey} is intentionally empty by default — when
 * blank, {@link com.kalima.backend.assistant.service.GeminiClient} short-circuits
 * with a {@code 503 SERVICE_UNAVAILABLE} so callers get a clear message instead
 * of leaking a Google 401.
 */
@Configuration
@ConfigurationProperties(prefix = "gemini")
public class GeminiProperties {

    /** Google AI Studio API key. Required to enable the endpoint. */
    private String apiKey = "";

    /** Model id, e.g. {@code gemini-2.0-flash}. */
    private String model = "gemini-2.0-flash";

    /** Base endpoint without trailing slash, model is appended. */
    private String endpoint = "https://generativelanguage.googleapis.com/v1beta/models";

    /** Per-request HTTP timeout. */
    private int timeoutSeconds = 30;

    /** Hard cap on tokens Gemini may produce. */
    private int maxOutputTokens = 800;

    /** Sampling temperature; lower = more deterministic. */
    private double temperature = 0.4;

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
}
