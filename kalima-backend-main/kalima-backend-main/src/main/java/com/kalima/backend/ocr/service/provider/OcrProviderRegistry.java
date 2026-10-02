package com.kalima.backend.ocr.service.provider;

import com.kalima.backend.domain.entity.DomainEnums;
import com.kalima.backend.ocr.config.OcrProperties;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.List;

@Component
public class OcrProviderRegistry {

    private final OcrProperties properties;
    private final AzureDocumentIntelligenceProvider azureProvider;
    private final TikaOcrProvider tikaProvider;

    public OcrProviderRegistry(OcrProperties properties,
                               AzureDocumentIntelligenceProvider azureProvider,
                               TikaOcrProvider tikaProvider) {
        this.properties = properties;
        this.azureProvider = azureProvider;
        this.tikaProvider = tikaProvider;
    }

    public OcrExtractionResult extract(Path file, String contentType, String originalFilename)
            throws OcrProviderException {
        List<OcrProvider> chain = orderedProviders();
        OcrProviderException lastFailure = null;
        for (OcrProvider provider : chain) {
            if (!provider.isAvailable()) {
                continue;
            }
            try {
                return provider.extract(file, contentType, originalFilename);
            } catch (OcrProviderException ex) {
                lastFailure = ex;
            }
        }
        if (lastFailure != null) {
            throw lastFailure;
        }
        throw new OcrProviderException("No OCR provider is available.");
    }

    private List<OcrProvider> orderedProviders() {
        DomainEnums.OcrProvider preferred = properties.preferredProviderEnum();
        if (preferred == DomainEnums.OcrProvider.TIKA) {
            return List.of(tikaProvider, azureProvider);
        }
        return List.of(azureProvider, tikaProvider);
    }
}
