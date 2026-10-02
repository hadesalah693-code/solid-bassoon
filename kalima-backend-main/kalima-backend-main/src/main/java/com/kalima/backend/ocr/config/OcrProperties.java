package com.kalima.backend.ocr.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import com.kalima.backend.domain.entity.DomainEnums;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Configuration
@ConfigurationProperties(prefix = "kalima.ocr")
public class OcrProperties {

    private boolean enabled = true;

    private String storageDir = "uploads/ocr";

    private long maxFileBytes = 25L * 1024L * 1024L;

    private List<String> allowedContentTypes = List.of(
            "image/jpeg",
            "image/jpg",
            "image/png",
            "application/pdf");

    /** When true, upload triggers async OCR immediately. */
    private boolean autoProcessOnUpload = true;

    /** Preferred engine: AZURE_DI (default) or TIKA. */
    private String provider = "AZURE_DI";

    public boolean isAutoProcessOnUpload() {
        return autoProcessOnUpload;
    }

    public void setAutoProcessOnUpload(boolean autoProcessOnUpload) {
        this.autoProcessOnUpload = autoProcessOnUpload;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public DomainEnums.OcrProvider preferredProviderEnum() {
        if (provider == null || provider.isBlank()) {
            return DomainEnums.OcrProvider.AZURE_DI;
        }
        try {
            return DomainEnums.OcrProvider.valueOf(provider.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return DomainEnums.OcrProvider.AZURE_DI;
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getStorageDir() {
        return storageDir;
    }

    public void setStorageDir(String storageDir) {
        this.storageDir = storageDir;
    }

    public long getMaxFileBytes() {
        return maxFileBytes;
    }

    public void setMaxFileBytes(long maxFileBytes) {
        this.maxFileBytes = maxFileBytes;
    }

    public List<String> getAllowedContentTypes() {
        return allowedContentTypes;
    }

    public void setAllowedContentTypes(List<String> allowedContentTypes) {
        this.allowedContentTypes = allowedContentTypes;
    }

    public Set<String> allowedContentTypeSet() {
        return allowedContentTypes.stream()
                .map(String::trim)
                .map(String::toLowerCase)
                .collect(Collectors.toSet());
    }
}
