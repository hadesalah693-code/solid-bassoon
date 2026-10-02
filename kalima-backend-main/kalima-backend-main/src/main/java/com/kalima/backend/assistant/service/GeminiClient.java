package com.kalima.backend.assistant.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.kalima.backend.assistant.config.GeminiProperties;
import com.kalima.backend.assistant.dto.AssistantTurn;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Duration;
import java.util.List;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

/**
 * Thin HTTP client over the Google Gemini {@code :generateContent} endpoint.
 *
 * <p>Holds no business logic — receives the system prompt and the rendered
 * conversation, returns the model's reply string. The API key is held on the
 * server only (read from {@link GeminiProperties}); it never leaves the JVM
 * and never appears in API responses or logs.
 *
 * <p>Errors are mapped to safe HTTP statuses for the caller:
 * <ul>
 *   <li>{@code 503} — feature disabled (no API key configured)</li>
 *   <li>{@code 429} — Gemini rate limit / quota exhausted</li>
 *   <li>{@code 502} — upstream Gemini error or malformed response</li>
 *   <li>{@code 504} — network timeout reaching Gemini</li>
 * </ul>
 */
@Component
public class GeminiClient {

    private static final Logger log = LoggerFactory.getLogger(GeminiClient.class);

    private final GeminiProperties props;
    private final RestTemplate restTemplate;
    private final ObjectMapper mapper = new ObjectMapper();

    public GeminiClient(GeminiProperties props, RestTemplateBuilder builder) {
        this.props = props;
        Duration timeout = Duration.ofSeconds(Math.max(5, props.getTimeoutSeconds()));
        this.restTemplate = builder
                .connectTimeout(timeout)
                .readTimeout(timeout)
                .build();
    }

    /**
     * Sends the conversation to Gemini and returns the model's reply text.
     *
     * @param systemPrompt high-level instructions (role, safety, format)
     * @param turns        ordered conversation turns; the latest is the user
     *                     question being asked now
     * @return the model's Arabic reply (never null/blank — fallback message
     *         is returned if the model produced nothing)
     */
    public String generate(String systemPrompt, List<AssistantTurn> turns) {
        if (!props.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Lawyer Assistant is not configured. Set GEMINI_API_KEY.");
        }
        if (turns == null || turns.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "At least one user turn is required.");
        }

        String url = UriComponentsBuilder
                .fromUriString(stripTrailingSlash(props.getEndpoint()))
                .pathSegment(props.getModel() + ":generateContent")
                .queryParam("key", props.getApiKey())
                .build(true)
                .toUriString();

        ObjectNode body = buildRequestBody(systemPrompt, turns);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity;
        try {
            entity = new HttpEntity<>(mapper.writeValueAsString(body), headers);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to serialize Gemini request");
        }

        ResponseEntity<String> response;
        try {
            response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
        } catch (HttpClientErrorException.TooManyRequests e) {
            log.warn("Gemini rate limited: {}", e.getStatusText());
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "تم استهلاك الحصة المجانية لخدمة المساعد، حاول بعد قليل.");
        } catch (HttpClientErrorException.Unauthorized
                 | HttpClientErrorException.Forbidden e) {
            log.error("Gemini auth error ({}): check GEMINI_API_KEY", e.getStatusCode());
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "إعدادات المساعد غير صحيحة. تواصل مع مسؤول النظام.");
        } catch (HttpClientErrorException e) {
            log.warn("Gemini 4xx: {} — {}", e.getStatusCode(), abbreviate(e.getResponseBodyAsString()));
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "تعذر الحصول على رد من المساعد.");
        } catch (HttpServerErrorException e) {
            log.warn("Gemini 5xx: {}", e.getStatusCode());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "خدمة المساعد غير متاحة حالياً، حاول لاحقاً.");
        } catch (ResourceAccessException e) {
            log.warn("Gemini network/timeout: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT,
                    "انتهت مهلة الاتصال بخدمة المساعد.");
        } catch (RuntimeException e) {
            log.error("Gemini unexpected client error", e);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "تعذر الوصول إلى خدمة المساعد.");
        }

        HttpStatusCode status = response.getStatusCode();
        if (!status.is2xxSuccessful() || response.getBody() == null) {
            log.warn("Gemini non-2xx: {}", status);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "تعذر الحصول على رد من المساعد.");
        }

        return extractReply(response.getBody());
    }

    /**
     * Streams Gemini content deltas via {@code alt=sse}. Returns full reply.
     */
    public String streamGenerate(String systemPrompt,
                                 List<AssistantTurn> turns,
                                 LlmStreamConsumer consumer) {
        if (!props.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Lawyer Assistant is not configured. Set GEMINI_API_KEY.");
        }
        if (turns == null || turns.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "At least one user turn is required.");
        }

        String url = UriComponentsBuilder
                .fromUriString(stripTrailingSlash(props.getEndpoint()))
                .pathSegment(props.getModel() + ":streamGenerateContent")
                .queryParam("key", props.getApiKey())
                .queryParam("alt", "sse")
                .build(true)
                .toUriString();

        ObjectNode body = buildRequestBody(systemPrompt, turns);

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(Math.max(5, props.getTimeoutSeconds())))
                .build();

        HttpRequest request;
        try {
            request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(Math.max(5, props.getTimeoutSeconds())))
                    .header("Content-Type", "application/json")
                    .header("Accept", "text/event-stream")
                    .POST(HttpRequest.BodyPublishers.ofString(
                            mapper.writeValueAsString(body)))
                    .build();
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to serialize Gemini stream request");
        }

        StringBuilder full = new StringBuilder();
        try {
            HttpResponse<java.io.InputStream> response = client.send(
                    request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() == 429) {
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                        "تم استهلاك الحصة المجانية لخدمة المساعد، حاول بعد قليل.");
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "تعذر الحصول على رد متدفق من المساعد.");
            }

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!line.startsWith("data: ")) {
                        continue;
                    }
                    String data = line.substring(6).trim();
                    if (data.isEmpty()) {
                        continue;
                    }
                    String delta = extractStreamDelta(data);
                    if (delta == null || delta.isEmpty()) {
                        continue;
                    }
                    full.append(delta);
                    if (consumer != null) {
                        consumer.accept(delta);
                    }
                }
            }
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Gemini stream failed: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "تعذر الحصول على رد متدفق من المساعد.");
        }

        String reply = full.toString().trim();
        if (reply.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "رد فارغ من المساعد.");
        }
        return reply;
    }

    private String extractStreamDelta(String jsonLine) {
        try {
            JsonNode root = mapper.readTree(jsonLine);
            JsonNode parts = root.path("candidates").path(0).path("content").path("parts");
            if (!parts.isArray() || parts.isEmpty()) {
                return null;
            }
            return parts.get(0).path("text").asText(null);
        } catch (Exception e) {
            return null;
        }
    }

    private ObjectNode buildRequestBody(String systemPrompt, List<AssistantTurn> turns) {
        ObjectNode root = mapper.createObjectNode();

        if (systemPrompt != null && !systemPrompt.isBlank()) {
            ObjectNode systemInstruction = root.putObject("system_instruction");
            ArrayNode parts = systemInstruction.putArray("parts");
            parts.addObject().put("text", systemPrompt);
        }

        ArrayNode contents = root.putArray("contents");
        for (AssistantTurn turn : turns) {
            ObjectNode item = contents.addObject();
            // Gemini uses "user" / "model" instead of "user" / "assistant".
            item.put("role", turn.role() == AssistantTurn.Role.ASSISTANT
                    ? "model" : "user");
            ArrayNode itemParts = item.putArray("parts");
            itemParts.addObject().put("text",
                    turn.content() == null ? "" : turn.content());
        }

        ObjectNode generation = root.putObject("generationConfig");
        generation.put("temperature", props.getTemperature());
        generation.put("maxOutputTokens", props.getMaxOutputTokens());

        // Be permissive about safety — legal content can trip the default
        // medium thresholds easily. We still rely on Gemini's hard-coded
        // illegality blocks; only relaxing the default soft filters.
        ArrayNode safety = root.putArray("safetySettings");
        for (String cat : List.of(
                "HARM_CATEGORY_HARASSMENT",
                "HARM_CATEGORY_HATE_SPEECH",
                "HARM_CATEGORY_SEXUALLY_EXPLICIT",
                "HARM_CATEGORY_DANGEROUS_CONTENT")) {
            ObjectNode s = safety.addObject();
            s.put("category", cat);
            s.put("threshold", "BLOCK_ONLY_HIGH");
        }

        return root;
    }

    private String extractReply(String json) {
        try {
            JsonNode root = mapper.readTree(json);
            JsonNode candidates = root.path("candidates");
            if (!candidates.isArray() || candidates.isEmpty()) {
                JsonNode block = root.path("promptFeedback").path("blockReason");
                if (!block.isMissingNode() && !block.isNull()) {
                    log.warn("Gemini blocked prompt: {}", block.asText());
                    return "تعذر الرد على هذا السؤال نظراً لقيود السلامة. حاول إعادة صياغة السؤال.";
                }
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "رد فارغ من المساعد.");
            }
            JsonNode parts = candidates.get(0).path("content").path("parts");
            if (parts.isArray() && !parts.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                for (JsonNode p : parts) {
                    String text = p.path("text").asText("");
                    if (!text.isEmpty()) {
                        if (sb.length() > 0) sb.append("\n");
                        sb.append(text);
                    }
                }
                String out = sb.toString().trim();
                if (!out.isEmpty()) return out;
            }
            String finishReason = candidates.get(0).path("finishReason").asText("");
            if ("SAFETY".equalsIgnoreCase(finishReason)
                    || "RECITATION".equalsIgnoreCase(finishReason)) {
                return "تعذر الرد على هذا السؤال نظراً لقيود السلامة. حاول إعادة صياغة السؤال.";
            }
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "رد فارغ من المساعد.");
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Failed to parse Gemini response", e);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "رد غير صالح من المساعد.");
        }
    }

    private static String stripTrailingSlash(String s) {
        if (s == null) return "";
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }

    private static String abbreviate(String s) {
        if (s == null) return "";
        return s.length() <= 200 ? s : s.substring(0, 200) + "…";
    }
}
