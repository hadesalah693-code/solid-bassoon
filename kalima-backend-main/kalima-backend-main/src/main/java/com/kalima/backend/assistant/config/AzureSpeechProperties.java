package com.kalima.backend.assistant.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Azure Cognitive Services Speech (neural TTS). Set {@code AZURE_SPEECH_KEY} and
 * {@code AZURE_SPEECH_REGION} in the environment for ChatGPT-quality Arabic voice.
 */
@Configuration
@ConfigurationProperties(prefix = "azure.speech")
public class AzureSpeechProperties {

    private String key = "";
    private String region = "eastus";
    /** Neural voice, e.g. ar-EG-SalmaNeural or ar-SA-HamedNeural */
    private String voice = "ar-EG-SalmaNeural";
    private int timeoutSeconds = 30;

    public boolean isEnabled() {
        return key != null && !key.isBlank() && region != null && !region.isBlank();
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public String getVoice() {
        return voice;
    }

    public void setVoice(String voice) {
        this.voice = voice;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }
}
