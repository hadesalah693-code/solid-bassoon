package com.kalima.backend.ocr.service.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kalima.backend.domain.entity.DomainEnums;
import com.kalima.backend.ocr.config.AzureDocumentIntelligenceProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Azure Document Intelligence result parsing")
class AzureDocumentIntelligenceProviderTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void parseAnalyzeResult_mapsTextConfidenceAndMixedLanguage() throws Exception {
        String json = """
                {
                  "status": "succeeded",
                  "analyzeResult": {
                    "content": "مرحبا Hello",
                    "languages": [
                      {"locale": "ar", "confidence": 0.91},
                      {"locale": "en", "confidence": 0.88}
                    ],
                    "pages": [{
                      "words": [
                        {"content": "مرحبا", "confidence": 0.95},
                        {"content": "Hello", "confidence": 0.90}
                      ]
                    }]
                  }
                }
                """;

        AzureDocumentIntelligenceProperties props = new AzureDocumentIntelligenceProperties();
        AzureDocumentIntelligenceProvider provider = new AzureDocumentIntelligenceProvider(
                props,
                new RestTemplateBuilder(),
                objectMapper);

        OcrExtractionResult result = provider.parseAnalyzeResult(objectMapper.readTree(json));

        assertThat(result.text()).isEqualTo("مرحبا Hello");
        assertThat(result.language()).isEqualTo("ar-en");
        assertThat(result.provider()).isEqualTo(DomainEnums.OcrProvider.AZURE_DI);
        assertThat(result.confidenceScore()).isEqualByComparingTo(new BigDecimal("0.9250"));
    }
}
