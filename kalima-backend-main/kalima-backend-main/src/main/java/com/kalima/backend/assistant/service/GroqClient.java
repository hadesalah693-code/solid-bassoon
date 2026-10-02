package com.kalima.backend.assistant.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.kalima.backend.assistant.config.GroqProperties;
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
 * Groq OpenAI-compatible chat completions client used as a fallback when
 * Gemini is rate-limited or unavailable.
 */
@Component
public class GroqClient {

    private static final Logger log = LoggerFactory.getLogger(GroqClient.class);

    private final GroqProperties props;
    private final RestTemplate restTemplate;
    private final ObjectMapper mapper = new ObjectMapper();

    public GroqClient(GroqProperties props, RestTemplateBuilder builder) {
        this.props = props;
        Duration timeout = Duration.ofSeconds(Math.max(5, props.getTimeoutSeconds()));
        this.restTemplate = builder
                .connectTimeout(timeout)
                .readTimeout(timeout)
                .build();
    }

    public String generate(String systemPrompt, List<AssistantTurn> turns) {
        if (!props.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Groq fallback is not configured. Set GROQ_API_KEY.");
        }
        if (turns == null || turns.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "At least one user turn is required.");
        }

        ObjectNode body = buildRequestBody(systemPrompt, turns);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(props.getApiKey());

        HttpEntity<String> entity;
        try {
            entity = new HttpEntity<>(mapper.writeValueAsString(body), headers);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to serialize Groq request");
        }

        ResponseEntity<String> response;
        try {
            response = restTemplate.exchange(
                    props.getEndpoint(), HttpMethod.POST, entity, String.class);
        } catch (HttpClientErrorException.TooManyRequests e) {
            log.warn("Groq rate limited: {}", e.getStatusText());
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "تم استهلاك الحصة المجانية لخدمة المساعد، حاول بعد قليل.");
        } catch (HttpClientErrorException.Unauthorized
                 | HttpClientErrorException.Forbidden e) {
            log.error("Groq auth error ({}): check GROQ_API_KEY", e.getStatusCode());
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "إعدادات Groq غير صحيحة. تواصل مع مسؤول النظام.");
        } catch (HttpClientErrorException e) {
            log.warn("Groq 4xx: {} — {}", e.getStatusCode(), abbreviate(e.getResponseBodyAsString()));
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "تعذر الحصول على رد من مزود Groq.");
        } catch (HttpServerErrorException e) {
            log.warn("Groq 5xx: {}", e.getStatusCode());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "خدمة Groq غير متاحة حالياً، حاول لاحقاً.");
        } catch (ResourceAccessException e) {
            log.warn("Groq network/timeout: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT,
                    "انتهت مهلة الاتصال بخدمة Groq.");
        } catch (RuntimeException e) {
            log.error("Groq unexpected client error", e);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "تعذر الوصول إلى خدمة Groq.");
        }

        HttpStatusCode status = response.getStatusCode();
        if (!status.is2xxSuccessful() || response.getBody() == null) {
            log.warn("Groq non-2xx: {}", status);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "تعذر الحصول على رد من مزود Groq.");
        }

        return extractReply(response.getBody());
    }

    /**
     * Streams chat completion deltas. Returns the concatenated full reply.
     */
    public String streamGenerate(String systemPrompt,
                                 List<AssistantTurn> turns,
                                 LlmStreamConsumer consumer) {
        if (!props.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Groq fallback is not configured. Set GROQ_API_KEY.");
        }
        if (turns == null || turns.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "At least one user turn is required.");
        }

        ObjectNode body = buildRequestBody(systemPrompt, turns);
        body.put("stream", true);

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(Math.max(5, props.getTimeoutSeconds())))
                .build();

        HttpRequest request;
        try {
            request = HttpRequest.newBuilder()
                    .uri(URI.create(props.getEndpoint()))
                    .timeout(Duration.ofSeconds(Math.max(5, props.getTimeoutSeconds())))
                    .header("Authorization", "Bearer " + props.getApiKey())
                    .header("Content-Type", "application/json")
                    .header("Accept", "text/event-stream")
                    .POST(HttpRequest.BodyPublishers.ofString(
                            mapper.writeValueAsString(body)))
                    .build();
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to serialize Groq stream request");
        }

        StringBuilder full = new StringBuilder();
        try {
            HttpResponse<java.io.InputStream> response = client.send(
                    request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() == 429) {
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                        "تم استهلاك الحصة المجانية لخدمة المساعد، حاول بعد قليل.");
            }
            if (response.statusCode() == 401 || response.statusCode() == 403) {
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                        "إعدادات Groq غير صحيحة. تواصل مع مسؤول النظام.");
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "تعذر الحصول على رد متدفق من Groq.");
            }

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!line.startsWith("data: ")) {
                        continue;
                    }
                    String data = line.substring(6).trim();
                    if (data.isEmpty() || "[DONE]".equals(data)) {
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
            log.warn("Groq stream failed: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "تعذر الحصول على رد متدفق من Groq.");
        }

        String reply = full.toString().trim();
        if (reply.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "رد فارغ من Groq.");
        }
        return reply;
    }

    private String extractStreamDelta(String jsonLine) {
        try {
            JsonNode root = mapper.readTree(jsonLine);
            JsonNode choices = root.path("choices");
            if (!choices.isArray() || choices.isEmpty()) {
                return null;
            }
            return choices.get(0).path("delta").path("content").asText(null);
        } catch (Exception e) {
            return null;
        }
    }

    public String model() {
        return props.getModel();
    }

    private ObjectNode buildRequestBody(String systemPrompt, List<AssistantTurn> turns) {
        ObjectNode root = mapper.createObjectNode();
        root.put("model", props.getModel());
        root.put("temperature", props.getTemperature());
        root.put("max_tokens", props.getMaxOutputTokens());

        ArrayNode messages = root.putArray("messages");
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            ObjectNode system = messages.addObject();
            system.put("role", "system");
            system.put("content", systemPrompt);
        }
        for (AssistantTurn turn : turns) {
            ObjectNode message = messages.addObject();
            message.put("role", turn.role() == AssistantTurn.Role.ASSISTANT
                    ? "assistant" : "user");
            message.put("content", turn.content() == null ? "" : turn.content());
        }
        return root;
    }

    private String extractReply(String json) {
        try {
            JsonNode root = mapper.readTree(json);
            JsonNode choices = root.path("choices");
            if (!choices.isArray() || choices.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "رد فارغ من Groq.");
            }
            String content = choices.get(0)
                    .path("message")
                    .path("content")
                    .asText("")
                    .trim();
            if (!content.isEmpty()) {
                return content;
            }
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "رد فارغ من Groq.");
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Failed to parse Groq response", e);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "رد غير صالح من Groq.");
        }
    }

    private static String abbreviate(String s) {
        if (s == null) return "";
        return s.length() <= 200 ? s : s.substring(0, 200) + "…";
    }
}
