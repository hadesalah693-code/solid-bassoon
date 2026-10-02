package com.kalima.backend.agent.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kalima.backend.assistant.config.GeminiProperties;
import com.kalima.backend.assistant.config.GroqProperties;
import com.kalima.backend.assistant.dto.AssistantTurn;
import com.kalima.backend.assistant.service.GeminiClient;
import com.kalima.backend.assistant.service.GroqClient;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * LLM planning layer for the voice agent. Uses Gemini when configured; otherwise
 * a deterministic Arabic keyword fallback for offline/dev environments.
 */
@Service
public class AgentBrainService {

    private static final Logger log = LoggerFactory.getLogger(AgentBrainService.class);
    private static final DateTimeFormatter ISO_DATE = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final Pattern UUID_PATTERN = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

    private static final String CAPABILITIES_REPLY = """
            أستطيع مساعدتك في:
            • «اعرض القضايا» — قائمة القضايا
            • «اعرض التوكيلات» أو «التوكيلات المنتهية قريباً»
            • «اعرض النزاعات»
            • «مواعيد الجلسات» — جلسات الأسبوع
            • «جهزني للجلسة بكرة» — تحضير للجلسة القادمة
            • «ابحث عن قضية …» — البحث برقم أو اسم
            • إغلاق قضية أو إرسال تنبيه — يتطلب تأكيدك أولاً
            """;

    private static final String SYSTEM_PROMPT = """
            أنت "المساعد الذكي" في نظام كلمة — وكيل صوتي يتحكم في النظام عبر أدوات فقط.
            لا تختلق بيانات. استخدم الأدوات للقراءة والتنفيذ.

            أعد JSON فقط (بدون markdown) بالشكل:
            {
              "done": false,
              "confidence": 0.0-1.0,
              "tool": "tool_name_or_null",
              "params": {},
              "reply": "رد عربي للمستخدم عند done=true",
              "pendingSummary": "ملخص للتأكيد عند الإجراءات الحساسة"
            }

            قواعد:
            - done=true عندما تكتفي بالرد أو تطلب تأكيد إجراء حساس.
            - done=false مع tool عند الحاجة لخطوة أخرى.
            - للإجراءات: close_case, send_notification, modify_case_sensitive_data → done=true مع tool و pendingSummary.
            - للتحضير للجلسة: سلسلة get_hearings ثم get_case_summary / generate_preparation_notes.
            - التاريخ اليوم: %s
            """;

    private final GroqProperties groqProperties;
    private final GeminiProperties geminiProperties;
    private final GroqClient groqClient;
    private final GeminiClient geminiClient;
    private final AgentToolRegistry toolRegistry;
    private final ObjectMapper objectMapper;

    public AgentBrainService(GroqProperties groqProperties,
                             GeminiProperties geminiProperties,
                             GroqClient groqClient,
                             GeminiClient geminiClient,
                             AgentToolRegistry toolRegistry,
                             ObjectMapper objectMapper) {
        this.groqProperties = groqProperties;
        this.geminiProperties = geminiProperties;
        this.groqClient = groqClient;
        this.geminiClient = geminiClient;
        this.toolRegistry = toolRegistry;
        this.objectMapper = objectMapper;
    }

    public AgentStepDecision planNextStep(
            String userText,
            String memorySnippet,
            List<ToolStepRecord> priorSteps) {
        AgentStepDecision routed = deterministicRoute(userText, memorySnippet, priorSteps);
        if (routed != null) {
            return routed;
        }

        String prompt = buildPrompt(memorySnippet, priorSteps);
        List<AssistantTurn> turns = buildTurns(userText, priorSteps);

        if (groqProperties.isEnabled()) {
            try {
                return parseDecision(groqClient.generate(prompt, turns));
            } catch (ResponseStatusException ex) {
                if (!shouldFallbackToAlternateLlm(ex)) {
                    throw ex;
                }
                log.warn("Groq planning failed ({}), trying alternate LLM.", ex.getStatusCode());
            } catch (Exception ex) {
                log.warn("Groq planning failed, trying alternate LLM: {}", ex.getMessage());
            }
        }
        if (geminiProperties.isEnabled()) {
            try {
                return parseDecision(geminiClient.generate(prompt, turns));
            } catch (Exception ex) {
                log.warn("Gemini planning failed, using keyword fallback: {}", ex.getMessage());
            }
        }
        return unknownCommandDecision();
    }

    /**
     * Handles common Arabic voice commands deterministically so Groq cannot pick
     * the wrong tool (e.g. get_case_summary without caseId).
     */
    private AgentStepDecision deterministicRoute(
            String userText,
            String memorySnippet,
            List<ToolStepRecord> priorSteps) {
        String text = normalizeArabicCommand(userText);
        String lower = text.toLowerCase();

        if (priorSteps != null && !priorSteps.isEmpty()) {
            return continueAfterTool(lower, priorSteps);
        }

        if (wantsCapabilities(lower)) {
            return AgentStepDecision.finished(CAPABILITIES_REPLY.trim(), 0.95);
        }
        if (wantsListCases(lower)) {
            return AgentStepDecision.invokeTool("list_cases", Map.of("limit", 20), 0.95);
        }
        if (wantsListDelegations(lower)) {
            if (containsAny(lower, "منته", "ينته", "قريب", "expir")) {
                return AgentStepDecision.invokeTool("list_expiring_delegations",
                        Map.of("withinDays", 30), 0.92);
            }
            return AgentStepDecision.invokeTool("list_delegations", Map.of("limit", 20), 0.92);
        }
        if (wantsListDisputes(lower)) {
            return AgentStepDecision.invokeTool("list_disputes", Map.of("limit", 20), 0.92);
        }
        if (wantsHearingPrep(lower)) {
            LocalDate target = parseRelativeHearingDate(lower);
            return AgentStepDecision.invokeTool("get_hearings",
                    Map.of("date", target.format(ISO_DATE)), 0.9);
        }
        if (wantsHearingsSchedule(lower)) {
            return AgentStepDecision.invokeTool("get_hearings",
                    Map.of("from", LocalDate.now().format(ISO_DATE),
                            "to", LocalDate.now().plusDays(7).format(ISO_DATE)),
                    0.88);
        }
        UUID fromMemory = parseUuid(memorySnippet);
        UUID fromText = parseUuid(text);
        UUID caseId = fromText != null ? fromText : fromMemory;
        UUID delegationId = parseDelegationId(memorySnippet);
        UUID disputeId = parseDisputeId(memorySnippet);
        if (delegationId != null && containsAny(lower, "ملخص", "summary", "توكيل")) {
            return AgentStepDecision.invokeTool("get_delegation_summary",
                    Map.of("delegationId", delegationId.toString()), 0.9);
        }
        if (disputeId != null && containsAny(lower, "ملخص", "summary", "نزاع")) {
            return AgentStepDecision.invokeTool("get_dispute_summary",
                    Map.of("disputeId", disputeId.toString()), 0.9);
        }
        if (caseId != null && containsAny(lower, "ملخص", "summary")) {
            return AgentStepDecision.invokeTool("get_case_summary",
                    Map.of("caseId", caseId.toString()), 0.9);
        }
        if (caseId != null && wantsCloseCase(lower)) {
            return AgentStepDecision.needsConfirmation(
                    "close_case",
                    Map.of("caseId", caseId.toString()),
                    "إغلاق القضية",
                    0.92);
        }
        if (containsAny(lower, "ابحث", "افتح") && containsAny(lower, "قض")) {
            String query = text.replaceAll("(?i)(ابحث|افتح|عن|قضية|قضايا|اعرض)", "").trim();
            if (!query.isBlank()) {
                return AgentStepDecision.invokeTool("open_case",
                        Map.of("nameOrNumber", query), 0.85);
            }
        }
        return null;
    }

    private AgentStepDecision continueAfterTool(String lower, List<ToolStepRecord> priorSteps) {
        ToolStepRecord last = priorSteps.get(priorSteps.size() - 1);
        if (!last.success()) {
            return AgentStepDecision.finished(last.resultMessage(), 0.5);
        }
        if (priorSteps.size() >= 2) {
            ToolStepRecord prev = priorSteps.get(priorSteps.size() - 2);
            if (prev.tool().equals(last.tool()) && prev.success()) {
                return AgentStepDecision.finished(last.resultMessage(), 0.9);
            }
        }
        return switch (last.tool()) {
            case "get_hearings" -> {
                if (wantsHearingPrep(lower) && last.caseIdHint() != null) {
                    yield AgentStepDecision.invokeTool("generate_preparation_notes",
                            Map.of("caseId", last.caseIdHint().toString()), 0.9);
                }
                yield AgentStepDecision.finished(last.resultMessage(), 0.92);
            }
            case "open_case" -> {
                if (last.caseIdHint() != null && containsAny(lower, "ملخص", "summary")) {
                    yield AgentStepDecision.invokeTool("get_case_summary",
                            Map.of("caseId", last.caseIdHint().toString()), 0.88);
                }
                yield AgentStepDecision.finished(last.resultMessage(), 0.92);
            }
            case "generate_preparation_notes", "list_cases", "get_case_summary",
                    "list_delegations", "list_expiring_delegations", "get_delegation_summary",
                    "list_disputes", "get_dispute_summary" ->
                    AgentStepDecision.finished(last.resultMessage(), 0.92);
            default -> AgentStepDecision.finished(last.resultMessage(), 0.92);
        };
    }

    private static AgentStepDecision unknownCommandDecision() {
        return AgentStepDecision.finished(
                "لم أفهم الأمر. جرّب: «اعرض القضايا»، «مواعيد الجلسات»، أو «جهزني للجلسة بكرة».",
                0.7);
    }

    private static boolean shouldFallbackToAlternateLlm(ResponseStatusException ex) {
        HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
        if (status == null) {
            return true;
        }
        return status == HttpStatus.TOO_MANY_REQUESTS
                || status == HttpStatus.SERVICE_UNAVAILABLE
                || status == HttpStatus.BAD_GATEWAY
                || status == HttpStatus.GATEWAY_TIMEOUT;
    }

    private String buildPrompt(String memorySnippet, List<ToolStepRecord> priorSteps) {
        StringBuilder sb = new StringBuilder(SYSTEM_PROMPT.formatted(LocalDate.now()));
        sb.append("\n\n").append(toolRegistry.toolCatalogForPrompt());
        if (memorySnippet != null && !memorySnippet.isBlank()) {
            sb.append("\n\n=== ذاكرة المستخدم ===\n").append(memorySnippet);
        }
        if (priorSteps != null && !priorSteps.isEmpty()) {
            sb.append("\n\n=== نتائج الأدوات السابقة ===\n");
            for (ToolStepRecord step : priorSteps) {
                sb.append("- ").append(step.tool()).append(": ")
                        .append(step.resultMessage()).append("\n");
            }
        }
        return sb.toString();
    }

    private List<AssistantTurn> buildTurns(String userText, List<ToolStepRecord> priorSteps) {
        List<AssistantTurn> turns = new ArrayList<>();
        if (priorSteps != null) {
            for (ToolStepRecord step : priorSteps) {
                turns.add(new AssistantTurn(AssistantTurn.Role.USER,
                        "نفّذ: " + step.tool()));
                turns.add(new AssistantTurn(AssistantTurn.Role.ASSISTANT,
                        step.resultMessage()));
            }
        }
        turns.add(new AssistantTurn(AssistantTurn.Role.USER, userText));
        return turns;
    }

    AgentStepDecision parseDecision(String rawJson) {
        try {
            String json = extractJson(rawJson);
            JsonNode node = objectMapper.readTree(json);
            boolean done = node.path("done").asBoolean(false);
            double confidence = node.path("confidence").asDouble(0.75);
            String tool = textOrNull(node, "tool");
            String reply = textOrNull(node, "reply");
            String pendingSummary = textOrNull(node, "pendingSummary");
            Map<String, Object> params = readParams(node.path("params"));
            if (done) {
                if (tool != null && pendingSummary != null) {
                    return AgentStepDecision.needsConfirmation(tool, params, pendingSummary, confidence);
                }
                return AgentStepDecision.finished(
                        reply != null ? reply : "تم.",
                        confidence);
            }
            if (tool == null) {
                return AgentStepDecision.finished(
                        reply != null ? reply : "لم أفهم الطلب. حاول مرة أخرى.",
                        confidence);
            }
            return AgentStepDecision.invokeTool(tool, params, confidence);
        } catch (Exception ex) {
            log.warn("Failed to parse agent decision JSON", ex);
            return AgentStepDecision.finished(
                    "تعذر فهم خطة التنفيذ. أعد صياغة طلبك.",
                    0.5);
        }
    }

    private static String normalizeArabicCommand(String userText) {
        if (userText == null) {
            return "";
        }
        return userText
                .replace("«", "")
                .replace("»", "")
                .replace("\"", "")
                .replace("'", "")
                .trim();
    }

    private static boolean wantsCapabilities(String lower) {
        return containsAny(lower,
                "اى اللى", "إيه اللي", "ايه اللي", "ماذا تستطيع", "ماذا يمكن",
                "تقدر تعمل", "بتقدر", "تقدر ت", "help", "القدرات", "وش تقدر");
    }

    private static boolean wantsListCases(String lower) {
        if (containsAny(lower, "قضايا", "cases", "القضايا")) {
            return containsAny(lower, "اعرض", "ورين", "ورني", "كل", "جميع", "قائمة", "list", "show")
                    || lower.contains("قضايا");
        }
        return containsAny(lower, "اعرض القض", "قائمة القض");
    }

    private static boolean wantsListDelegations(String lower) {
        if (!containsAny(lower, "توكيل", "توكيلات", "delegation")) {
            return false;
        }
        return containsAny(lower, "اعرض", "قائمة", "list", "ورين", "ورني", "منته", "ينته", "قريب", "كل");
    }

    private static boolean wantsListDisputes(String lower) {
        return containsAny(lower, "نزاع", "نزاعات", "dispute");
    }

    private static boolean wantsHearingPrep(String lower) {
        return containsAny(lower, "جهز", "تحضير")
                || (containsAny(lower, "جلس", "جلسه", "جلسة") && containsAny(lower, "جهز", "تحضير", "بكر", "غدا"));
    }

    private static boolean wantsHearingsSchedule(String lower) {
        return containsAny(lower, "جلسات", "مواعيد", "موعد") && !wantsHearingPrep(lower);
    }

    private static boolean wantsCloseCase(String lower) {
        return containsAny(lower, "اغلق", "اقفل", "إغلاق", "اغلاق", "قفل", "close");
    }

    private static LocalDate parseRelativeHearingDate(String lower) {
        if (containsAny(lower, "بكر", "غدا", "غداً", "tomorrow")) {
            return LocalDate.now().plusDays(1);
        }
        if (containsAny(lower, "النهارده", "اليوم", "today")) {
            return LocalDate.now();
        }
        return LocalDate.now().plusDays(1);
    }

    private static boolean containsAny(String text, String... needles) {
        for (String n : needles) {
            if (text.contains(n)) return true;
        }
        return false;
    }

    private static UUID parseUuid(String text) {
        if (text == null) return null;
        Matcher m = UUID_PATTERN.matcher(text);
        return m.find() ? UUID.fromString(m.group()) : null;
    }

    private static UUID parseDelegationId(String memorySnippet) {
        if (memorySnippet == null || !memorySnippet.contains("معرّف التوكيل")) {
            return null;
        }
        int idx = memorySnippet.indexOf("معرّف التوكيل");
        return parseUuid(memorySnippet.substring(idx));
    }

    private static UUID parseDisputeId(String memorySnippet) {
        if (memorySnippet == null || !memorySnippet.contains("معرّف النزاع")) {
            return null;
        }
        int idx = memorySnippet.indexOf("معرّف النزاع");
        return parseUuid(memorySnippet.substring(idx));
    }

    private Map<String, Object> readParams(JsonNode paramsNode) {
        if (paramsNode == null || paramsNode.isMissingNode() || paramsNode.isNull()) {
            return Map.of();
        }
        return objectMapper.convertValue(paramsNode, Map.class);
    }

    private static String textOrNull(JsonNode node, String field) {
        JsonNode v = node.path(field);
        if (v.isMissingNode() || v.isNull()) return null;
        String s = v.asText(null);
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static String extractJson(String raw) {
        String trimmed = raw.trim();
        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return trimmed.substring(start, end + 1);
        }
        return trimmed;
    }

    public record ToolStepRecord(
            String tool,
            boolean success,
            String resultMessage,
            UUID caseIdHint
    ) {
    }
}
