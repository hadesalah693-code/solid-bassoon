package com.kalima.backend.assistant.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kalima.backend.assistant.config.GeminiProperties;
import com.kalima.backend.assistant.config.GroqProperties;
import com.kalima.backend.assistant.dto.AssistantAskRequest;
import com.kalima.backend.assistant.dto.AssistantAskResponse;
import com.kalima.backend.assistant.dto.AssistantStreamMeta;
import com.kalima.backend.assistant.dto.AssistantTurn;
import com.kalima.backend.auth.support.CurrentUserService;
import com.kalima.backend.domain.entity.DomainEnums;
import com.kalima.backend.domain.entity.Person;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Orchestrates Lawyer Assistant turns: page context, document RAG, LLM call
 * (blocking or SSE streaming).
 */
@Service
@Transactional(readOnly = true)
public class LawyerAssistantService {

    private static final Logger log = LoggerFactory.getLogger(LawyerAssistantService.class);
    private static final int MAX_HISTORY_TURNS = 20;

    private static final String BASE_SYSTEM_PROMPT = """
            أنت "المساعد القانوني" داخل نظام كلمة لإدارة مكاتب المحاماة.
            دورك: مساعدة المحامين بالاقتراحات والتحليل والتلخيص فقط.

            قواعد ملزمة:
            1. لست محامياً ولا تُصدر قرارات قانونية ولا تعطي ضمانات.
            2. كل ما تقوله "اقتراحات داعمة" يقرّر فيها المحامي بنفسه.
            3. لا تختلق نصوص قوانين أو أرقام مواد أو أحكام؛ إن لم تكن متيقّناً
               فاطلب من المحامي مراجعتها بنفسه.
            4. التزم بالعربية الفصحى المهنية الموجزة، باستخدام نقاط مرتّبة
               عند المناسبة.
            5. لا تخرج عن المجال القانوني/المكتبي. لو وُجِّه إليك سؤال خارج
               المجال اعتذر باقتضاب.
            6. لو سُئلت عن بيانات قضية بعينها ولم يُحقن سياقها أدناه فاطلب
               من المحامي اختيار القضية أولاً من القائمة.
            7. لا تطلب من المستخدم إرسال بيانات شخصية حساسة لا يمتلكها النظام.
            8. عند تحليل قضية، نظّم إجابتك في: (الوقائع) (السند القانوني المحتمل)
               (الدفوع المقترحة) (الطلب أو الخطوة التالية).
            9. عند وجود مقتطفات مستندات، استشهد بها دون نسخ نصوص طويلة حرفياً.
            """;

    private final GroqProperties groqProperties;
    private final GeminiProperties geminiProperties;
    private final GeminiClient geminiClient;
    private final GroqClient groqClient;
    private final AssistantContextAssembler contextAssembler;
    private final CaseDocumentRagService documentRagService;
    private final CurrentUserService currentUserService;
    private final ObjectMapper objectMapper;

    public LawyerAssistantService(GroqProperties groqProperties,
                                  GeminiProperties geminiProperties,
                                  GeminiClient geminiClient,
                                  GroqClient groqClient,
                                  AssistantContextAssembler contextAssembler,
                                  CaseDocumentRagService documentRagService,
                                  CurrentUserService currentUserService,
                                  ObjectMapper objectMapper) {
        this.groqProperties = groqProperties;
        this.geminiProperties = geminiProperties;
        this.geminiClient = geminiClient;
        this.groqClient = groqClient;
        this.contextAssembler = contextAssembler;
        this.documentRagService = documentRagService;
        this.currentUserService = currentUserService;
        this.objectMapper = objectMapper;
    }

    public AssistantAskResponse ask(AssistantAskRequest request) {
        Person actor = requireStaffActor();
        PromptBundle bundle = buildPromptBundle(request, actor);
        List<AssistantTurn> turns = mergeHistoryAndQuestion(
                request.history(), request.question());
        ProviderResult result = generateWithFallback(bundle.systemPrompt(), turns);
        return new AssistantAskResponse(
                result.reply(),
                bundle.echoedCaseId(),
                bundle.contextUsed(),
                bundle.documentsUsed(),
                result.model(),
                result.provider());
    }

    public void streamAsk(AssistantAskRequest request, SseEmitter emitter) {
        try {
            Person actor = requireStaffActor();
            PromptBundle bundle = buildPromptBundle(request, actor);
            List<AssistantTurn> turns = mergeHistoryAndQuestion(
                    request.history(), request.question());

            AssistantStreamMeta meta = new AssistantStreamMeta(
                    bundle.echoedCaseId(),
                    bundle.contextUsed(),
                    bundle.documentsUsed(),
                    "",
                    "");
            sendSse(emitter, "meta", meta);

            ProviderResult result = streamWithFallback(
                    bundle.systemPrompt(),
                    turns,
                    delta -> sendSseQuiet(emitter, "delta",
                            java.util.Map.of("text", delta)));

            AssistantStreamMeta done = new AssistantStreamMeta(
                    bundle.echoedCaseId(),
                    bundle.contextUsed(),
                    bundle.documentsUsed(),
                    result.provider(),
                    result.model());
            sendSse(emitter, "done", done);
            emitter.complete();
        } catch (ResponseStatusException e) {
            completeStreamError(emitter, e);
        } catch (Exception e) {
            log.warn("Assistant stream failed", e);
            completeStreamError(emitter, new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY, "تعذر إكمال الرد المتدفق."));
        }
    }

    private PromptBundle buildPromptBundle(AssistantAskRequest request, Person actor) {
        String systemPrompt = BASE_SYSTEM_PROMPT;
        boolean contextUsed = false;
        boolean documentsUsed = false;
        UUID echoedCaseId = request.caseId();

        AssistantContextAssembler.AssembledContext assembled = contextAssembler.assemble(
                request.caseId(),
                request.delegationId(),
                request.disputeId(),
                actor);
        if (assembled.contextUsed()) {
            systemPrompt = systemPrompt + assembled.promptBlock();
            contextUsed = true;
            if (echoedCaseId == null) {
                echoedCaseId = assembled.effectiveCaseId();
            }
        }

        UUID ragCaseId = echoedCaseId != null ? echoedCaseId : assembled.effectiveCaseId();
        if (ragCaseId != null) {
            CaseDocumentRagService.RagResult rag = documentRagService.retrieve(
                    ragCaseId, request.question(), actor);
            if (rag.documentsUsed()) {
                systemPrompt = systemPrompt + rag.promptBlock();
                documentsUsed = true;
            }
        }

        return new PromptBundle(systemPrompt, contextUsed, documentsUsed, echoedCaseId);
    }

    private Person requireStaffActor() {
        Person actor = currentUserService.requireCurrentPerson();
        if (actor.getSystemRole() == DomainEnums.SystemRole.CLIENT) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Lawyer Assistant is not available to clients.");
        }
        return actor;
    }

    private ProviderResult generateWithFallback(String systemPrompt, List<AssistantTurn> turns) {
        if (groqProperties.isEnabled()) {
            try {
                String reply = groqClient.generate(systemPrompt, turns);
                return new ProviderResult(reply, groqClient.model(), "GROQ");
            } catch (ResponseStatusException e) {
                if (!shouldFallbackToAlternateProvider(e)) {
                    throw e;
                }
                log.warn("Groq unavailable ({}). Falling back to Gemini.", e.getStatusCode());
            }
        }
        if (geminiProperties.isEnabled()) {
            String reply = geminiClient.generate(systemPrompt, turns);
            return new ProviderResult(reply, geminiProperties.getModel(), "GEMINI");
        }
        throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "المساعد غير مُعد. أضف GROQ_API_KEY أو GEMINI_API_KEY.");
    }

    private ProviderResult streamWithFallback(String systemPrompt,
                                              List<AssistantTurn> turns,
                                              LlmStreamConsumer consumer) {
        if (groqProperties.isEnabled()) {
            try {
                String reply = groqClient.streamGenerate(systemPrompt, turns, consumer);
                return new ProviderResult(reply, groqClient.model(), "GROQ");
            } catch (ResponseStatusException e) {
                if (!shouldFallbackToAlternateProvider(e)) {
                    throw e;
                }
                log.warn("Groq stream unavailable ({}). Falling back to Gemini.", e.getStatusCode());
            }
        }
        if (geminiProperties.isEnabled()) {
            String reply = geminiClient.streamGenerate(systemPrompt, turns, consumer);
            return new ProviderResult(reply, geminiProperties.getModel(), "GEMINI");
        }
        throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "المساعد غير مُعد. أضف GROQ_API_KEY أو GEMINI_API_KEY.");
    }

    private void sendSse(SseEmitter emitter, String eventName, Object payload)
            throws IOException {
        emitter.send(SseEmitter.event()
                .name(eventName)
                .data(objectMapper.writeValueAsString(payload)));
    }

    private void sendSseQuiet(SseEmitter emitter, String eventName, Object payload) {
        try {
            sendSse(emitter, eventName, payload);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "تعذر إرسال الرد المتدفق.");
        }
    }

    private void completeStreamError(SseEmitter emitter, ResponseStatusException e) {
        try {
            emitter.send(SseEmitter.event()
                    .name("error")
                    .data(objectMapper.writeValueAsString(
                            java.util.Map.of("message", e.getReason()))));
        } catch (IOException ignored) {
            /* ignore */
        }
        emitter.completeWithError(e);
    }

    private static boolean shouldFallbackToAlternateProvider(ResponseStatusException e) {
        HttpStatus status = HttpStatus.resolve(e.getStatusCode().value());
        if (status == null) {
            return true;
        }
        return status == HttpStatus.TOO_MANY_REQUESTS
                || status == HttpStatus.SERVICE_UNAVAILABLE
                || status == HttpStatus.BAD_GATEWAY
                || status == HttpStatus.GATEWAY_TIMEOUT;
    }

    private static List<AssistantTurn> mergeHistoryAndQuestion(
            List<AssistantTurn> history, String question) {
        List<AssistantTurn> trimmed = new ArrayList<>();
        if (history != null) {
            int start = Math.max(0, history.size() - MAX_HISTORY_TURNS);
            for (int i = start; i < history.size(); i++) {
                AssistantTurn t = history.get(i);
                if (t == null || t.content() == null || t.content().isBlank()) {
                    continue;
                }
                trimmed.add(t);
            }
        }
        trimmed.add(new AssistantTurn(AssistantTurn.Role.USER, question));
        return trimmed;
    }

    private record PromptBundle(
            String systemPrompt,
            boolean contextUsed,
            boolean documentsUsed,
            UUID echoedCaseId
    ) {
    }

    private record ProviderResult(String reply, String model, String provider) {
    }
}
