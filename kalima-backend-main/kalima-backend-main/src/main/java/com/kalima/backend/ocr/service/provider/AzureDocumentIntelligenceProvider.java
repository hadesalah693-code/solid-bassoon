package com.kalima.backend.ocr.service.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kalima.backend.domain.entity.DomainEnums;
import com.kalima.backend.ocr.config.AzureDocumentIntelligenceProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Azure Document Intelligence {@code prebuilt-read} — Arabic + English OCR.
 */
@Component
public class AzureDocumentIntelligenceProvider implements OcrProvider {

    private static final Logger log = LoggerFactory.getLogger(AzureDocumentIntelligenceProvider.class);
    private static final int MAX_TEXT_CHARS = 500_000;

    private final AzureDocumentIntelligenceProperties props;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public AzureDocumentIntelligenceProvider(AzureDocumentIntelligenceProperties props,
                                             RestTemplateBuilder builder,
                                             ObjectMapper objectMapper) {
        this.props = props;
        Duration timeout = Duration.ofSeconds(Math.max(30, props.getTimeoutSeconds()));
        this.restTemplate = builder
                .connectTimeout(timeout)
                .readTimeout(timeout)
                .build();
        this.objectMapper = objectMapper;
    }

    @Override
    public String providerId() {
        return DomainEnums.OcrProvider.AZURE_DI.name();
    }

    @Override
    public boolean isAvailable() {
        return props.isEnabled();
    }

    @Override
    public OcrExtractionResult extract(Path file, String contentType, String originalFilename)
            throws OcrProviderException {
        if (!isAvailable()) {
            throw new OcrProviderException(
                    "Azure Document Intelligence is not configured.");
        }
        if (!Files.isRegularFile(file)) {
            throw new OcrProviderException("OCR source file not found.");
        }

        byte[] bytes;
        try {
            bytes = Files.readAllBytes(file);
        } catch (Exception ex) {
            throw new OcrProviderException("Could not read file for OCR.", ex);
        }
        if (bytes.length == 0) {
            throw new OcrProviderException("OCR source file is empty.");
        }

        String analyzeUrl = analyzeUrl();
        HttpHeaders headers = new HttpHeaders();
        headers.set("Ocp-Apim-Subscription-Key", props.getKey());
        headers.setContentType(MediaType.parseMediaType(resolveMediaType(contentType, originalFilename)));
        HttpEntity<byte[]> request = new HttpEntity<>(bytes, headers);

        ResponseEntity<Void> analyzeResponse;
        try {
            analyzeResponse = restTemplate.exchange(
                    analyzeUrl, HttpMethod.POST, request, Void.class);
        } catch (HttpClientErrorException.Unauthorized | HttpClientErrorException.Forbidden ex) {
            throw new OcrProviderException("Azure Document Intelligence authentication failed.");
        } catch (HttpClientErrorException ex) {
            throw new OcrProviderException("Azure Document Intelligence rejected the request.");
        } catch (HttpServerErrorException ex) {
            throw new OcrProviderException("Azure Document Intelligence service error.");
        } catch (Exception ex) {
            throw new OcrProviderException("Could not start Azure OCR analysis.", ex);
        }

        String operationUrl = analyzeResponse.getHeaders().getFirst("Operation-Location");
        if (operationUrl == null || operationUrl.isBlank()) {
            throw new OcrProviderException("Azure OCR did not return an operation URL.");
        }

        JsonNode result = pollUntilComplete(operationUrl);
        return parseAnalyzeResult(result);
    }

    private JsonNode pollUntilComplete(String operationUrl) throws OcrProviderException {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Ocp-Apim-Subscription-Key", props.getKey());

        for (int attempt = 0; attempt < props.getMaxPollAttempts(); attempt++) {
            if (attempt > 0) {
                sleep(props.getPollIntervalMs());
            }
            ResponseEntity<String> pollResponse;
            try {
                pollResponse = restTemplate.exchange(
                        operationUrl,
                        HttpMethod.GET,
                        new HttpEntity<>(headers),
                        String.class);
            } catch (Exception ex) {
                throw new OcrProviderException("Azure OCR polling failed.", ex);
            }

            JsonNode root;
            try {
                root = objectMapper.readTree(pollResponse.getBody() == null ? "{}" : pollResponse.getBody());
            } catch (Exception ex) {
                throw new OcrProviderException("Invalid Azure OCR polling response.", ex);
            }

            String status = root.path("status").asText("").toLowerCase(Locale.ROOT);
            if ("succeeded".equals(status)) {
                return root;
            }
            if ("failed".equals(status)) {
                String message = root.path("error").path("message").asText("Azure OCR failed.");
                throw new OcrProviderException(message);
            }
        }
        throw new OcrProviderException("Azure OCR timed out while waiting for results.");
    }

    OcrExtractionResult parseAnalyzeResult(JsonNode operationRoot) throws OcrProviderException {
        JsonNode analyzeResult = operationRoot.path("analyzeResult");
        if (analyzeResult.isMissingNode() || analyzeResult.isNull()) {
            throw new OcrProviderException("Azure OCR returned no analyzeResult.");
        }

        String content = analyzeResult.path("content").asText("").trim();
        if (content.isEmpty()) {
            throw new OcrProviderException("Azure OCR extracted no text from the document.");
        }
        if (content.length() > MAX_TEXT_CHARS) {
            content = content.substring(0, MAX_TEXT_CHARS) + "…";
        }

        BigDecimal confidence = averageWordConfidence(analyzeResult.path("pages"));
        List<String> locales = new ArrayList<>();
        for (JsonNode lang : analyzeResult.path("languages")) {
            String locale = lang.path("locale").asText(null);
            if (locale != null && !locale.isBlank()) {
                locales.add(locale);
            }
        }
        String language = OcrLanguageDetector.fromAzureLocales(locales);
        if (language == null) {
            language = OcrLanguageDetector.fromText(content);
        }

        return new OcrExtractionResult(
                content,
                confidence,
                language,
                DomainEnums.OcrProvider.AZURE_DI);
    }

    private static BigDecimal averageWordConfidence(JsonNode pages) {
        if (!pages.isArray() || pages.isEmpty()) {
            return null;
        }
        double sum = 0;
        int count = 0;
        for (JsonNode page : pages) {
            JsonNode words = page.path("words");
            if (!words.isArray()) {
                continue;
            }
            for (JsonNode word : words) {
                if (word.has("confidence")) {
                    sum += word.path("confidence").asDouble(0);
                    count++;
                }
            }
        }
        if (count == 0) {
            return null;
        }
        return BigDecimal.valueOf(sum / count).setScale(4, RoundingMode.HALF_UP);
    }

    private String analyzeUrl() {
        String base = props.getEndpoint().trim();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/documentintelligence/documentModels/"
                + props.getModel() + ":analyze?api-version=" + props.getApiVersion();
    }

    private static String resolveMediaType(String contentType, String originalFilename) {
        if (contentType != null && !contentType.isBlank()
                && !"application/octet-stream".equalsIgnoreCase(contentType)) {
            return contentType;
        }
        String lower = originalFilename == null ? "" : originalFilename.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".pdf")) {
            return "application/pdf";
        }
        if (lower.endsWith(".png")) {
            return "image/png";
        }
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        return "application/octet-stream";
    }

    private static void sleep(long millis) throws OcrProviderException {
        try {
            Thread.sleep(Math.max(250L, millis));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new OcrProviderException("Azure OCR polling interrupted.", ex);
        }
    }
}
