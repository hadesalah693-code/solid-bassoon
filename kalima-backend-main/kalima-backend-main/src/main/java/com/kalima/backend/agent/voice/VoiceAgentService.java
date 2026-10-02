package com.kalima.backend.agent.voice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kalima.backend.agent.config.AgentProperties;
import com.kalima.backend.agent.memory.AgentCommandLog;
import com.kalima.backend.agent.memory.AgentCommandLogRepository;
import com.kalima.backend.agent.memory.AgentMemoryService;
import com.kalima.backend.agent.memory.AgentPendingAction;
import com.kalima.backend.agent.memory.AgentPendingActionService;
import com.kalima.backend.agent.safety.AgentSafetyService;
import com.kalima.backend.agent.tools.AgentBrainService;
import com.kalima.backend.agent.tools.AgentStepDecision;
import com.kalima.backend.agent.tools.AgentToolContext;
import com.kalima.backend.agent.tools.AgentToolRegistry;
import com.kalima.backend.agent.tools.AgentToolResult;
import com.kalima.backend.agent.voice.dto.VoiceAgentRequest;
import com.kalima.backend.agent.voice.dto.VoiceAgentResponse;
import com.kalima.backend.assistant.service.AssistantContextAssembler;
import com.kalima.backend.audit.AuditAction;
import com.kalima.backend.audit.service.AuditLogService;
import com.kalima.backend.auth.support.CurrentUserService;
import com.kalima.backend.domain.entity.DomainEnums;
import com.kalima.backend.domain.entity.Person;
import com.kalima.backend.domain.repository.CompanyRepository;
import com.kalima.backend.domain.tenant.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class VoiceAgentService {

    private final CurrentUserService currentUserService;
    private final AgentBrainService brainService;
    private final AgentToolRegistry toolRegistry;
    private final AgentSafetyService safetyService;
    private final AgentMemoryService memoryService;
    private final AgentPendingActionService pendingActionService;
    private final AgentCommandLogRepository commandLogRepository;
    private final CompanyRepository companyRepository;
    private final AgentProperties properties;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;
    private final AssistantContextAssembler contextAssembler;

    public VoiceAgentService(CurrentUserService currentUserService,
                             AgentBrainService brainService,
                             AgentToolRegistry toolRegistry,
                             AgentSafetyService safetyService,
                             AgentMemoryService memoryService,
                             AgentPendingActionService pendingActionService,
                             AgentCommandLogRepository commandLogRepository,
                             CompanyRepository companyRepository,
                             AgentProperties properties,
                             AuditLogService auditLogService,
                             ObjectMapper objectMapper,
                             AssistantContextAssembler contextAssembler) {
        this.currentUserService = currentUserService;
        this.brainService = brainService;
        this.toolRegistry = toolRegistry;
        this.safetyService = safetyService;
        this.memoryService = memoryService;
        this.pendingActionService = pendingActionService;
        this.commandLogRepository = commandLogRepository;
        this.companyRepository = companyRepository;
        this.properties = properties;
        this.auditLogService = auditLogService;
        this.objectMapper = objectMapper;
        this.contextAssembler = contextAssembler;
    }

    public VoiceAgentResponse handle(VoiceAgentRequest request) {
        Person actor = currentUserService.requireCurrentPerson();
        if (actor.getSystemRole() == DomainEnums.SystemRole.CLIENT) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Voice agent is not available to clients.");
        }

        if (request.pendingActionId() != null) {
            if (Boolean.TRUE.equals(request.confirm())) {
                return executePending(actor, request.text(), request.pendingActionId());
            }
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "يتطلب هذا الإجراء تأكيداً صريحاً. استخدم زر «تأكيد التنفيذ».");
        }

        return runAgentLoop(actor, request.text(), request);
    }

    private VoiceAgentResponse executePending(Person actor, String inputText, UUID pendingId) {
        AgentToolResult result = pendingActionService.executeConfirmed(actor, pendingId);
        auditLogService.recordSuccess(AuditAction.AGENT_PENDING_CONFIRMED, actor,
                "AgentPendingAction", pendingId, null);
        UUID caseId = result.caseIdHint();
        if (caseId != null) {
            memoryService.rememberLastActiveCase(actor, caseId);
        }
        VoiceAgentResponse response = new VoiceAgentResponse(
                result.message(),
                false,
                null,
                null,
                List.of("confirmed_action"),
                caseId);
        logCommand(actor, inputText, response);
        return response;
    }

    private VoiceAgentResponse runAgentLoop(Person actor, String userText, VoiceAgentRequest request) {
        String memory = memoryService.buildContextSnippet(actor.getId());
        String pageSnippet = contextAssembler.buildPageSnippet(
                request.caseId(),
                request.delegationId(),
                request.disputeId(),
                actor);
        if (pageSnippet != null && !pageSnippet.isBlank()) {
            memory = memory + pageSnippet;
        }
        List<AgentBrainService.ToolStepRecord> prior = new ArrayList<>();
        List<String> toolsUsed = new ArrayList<>();
        UUID activeCaseId = request.caseId() != null
                ? request.caseId()
                : memoryService.getLastActiveCaseId(actor.getId()).orElse(null);
        if (activeCaseId == null && request.delegationId() != null) {
            activeCaseId = contextAssembler.assemble(
                    null, request.delegationId(), null, actor).effectiveCaseId();
        }
        if (activeCaseId == null && request.disputeId() != null) {
            activeCaseId = contextAssembler.assemble(
                    null, null, request.disputeId(), actor).effectiveCaseId();
        }
        AgentToolContext ctx = new AgentToolContext(actor, TenantContext.getRequired(), false);

        for (int step = 0; step < properties.getMaxToolSteps(); step++) {
            AgentStepDecision decision = brainService.planNextStep(userText, memory, prior);

            if (decision.done()) {
                if (decision.tool() != null && decision.pendingSummary() != null) {
                    VoiceAgentResponse queued = tryQueueConfirmation(
                            actor, userText, decision, toolsUsed, activeCaseId);
                    if (queued != null) {
                        return queued;
                    }
                }
                String reply = resolveFinalReply(decision, prior);
                VoiceAgentResponse response = new VoiceAgentResponse(
                        reply,
                        false,
                        null,
                        null,
                        toolsUsed,
                        activeCaseId);
                logCommand(actor, userText, response);
                auditLogService.recordSuccess(AuditAction.AGENT_VOICE_COMMAND, actor,
                        "Person", actor.getId(),
                        java.util.Map.of("tools", String.join(",", toolsUsed)));
                return response;
            }

            String tool = decision.tool();
            if (tool == null) {
                break;
            }
            if (safetyService.isToolBlocked(tool)) {
                return failResponse(actor, userText, toolsUsed, activeCaseId,
                        "هذا الإجراء غير مسموح.");
            }
            if (safetyService.requiresConfirmation(tool)) {
                VoiceAgentResponse queued = tryQueueConfirmation(
                        actor, userText, decision, toolsUsed, activeCaseId);
                if (queued != null) {
                    return queued;
                }
                return failResponse(actor, userText, toolsUsed, activeCaseId,
                        "تعذر طلب التأكيد لهذا الإجراء.");
            }

            AgentToolResult result = toolRegistry.invoke(tool, ctx, decision.params());
            toolsUsed.add(tool);
            if (result.caseIdHint() != null) {
                activeCaseId = result.caseIdHint();
                memoryService.rememberLastActiveCase(actor, activeCaseId);
            }
            prior.add(new AgentBrainService.ToolStepRecord(
                    tool, result.success(), result.message(), result.caseIdHint()));
            if (!result.success()) {
                VoiceAgentResponse response = new VoiceAgentResponse(
                        result.message(),
                        false,
                        null,
                        null,
                        toolsUsed,
                        activeCaseId);
                logCommand(actor, userText, response);
                return response;
            }
        }

        return failResponse(actor, userText, toolsUsed, activeCaseId,
                "تعذر إكمال الطلب ضمن عدد الخطوات المسموح.");
    }

    /** Prefer real tool output when the planner returns a generic placeholder. */
    private static String resolveFinalReply(
            AgentStepDecision decision,
            List<AgentBrainService.ToolStepRecord> prior) {
        if (prior == null || prior.isEmpty()) {
            return decision.reply();
        }
        AgentBrainService.ToolStepRecord last = prior.get(prior.size() - 1);
        if (!last.success()) {
            return decision.reply();
        }
        String reply = decision.reply();
        if (reply == null || reply.isBlank() || "تم.".equals(reply.trim())) {
            return last.resultMessage();
        }
        if (reply.contains("بدون ذكاء اصطناعي")
                || reply.contains("لم أفهم الأمر")
                || reply.contains("تعذر فهم خطة")) {
            return last.resultMessage();
        }
        return reply;
    }

    /**
     * Validates sensitive-action queue rules. Returns null when the planner should
     * fall through to a normal text reply instead of queuing confirmation.
     */
    private VoiceAgentResponse tryQueueConfirmation(
            Person actor,
            String userText,
            AgentStepDecision decision,
            List<String> toolsUsed,
            UUID activeCaseId) {
        String tool = decision.tool();
        if (tool == null || decision.pendingSummary() == null || decision.pendingSummary().isBlank()) {
            return null;
        }
        if (safetyService.isToolBlocked(tool)) {
            return failResponse(actor, userText, toolsUsed, activeCaseId,
                    "هذا الإجراء غير مسموح.");
        }
        if (!safetyService.isRegisteredTool(tool, toolRegistry.toolNames())) {
            return failResponse(actor, userText, toolsUsed, activeCaseId,
                    "الأداة المطلوبة غير معروفة.");
        }
        if (!safetyService.requiresConfirmation(tool)) {
            return null;
        }
        if (!safetyService.meetsConfidenceThreshold(decision.confidence())) {
            return failResponse(actor, userText, toolsUsed, activeCaseId,
                    "ثقتي في هذا الإجراء غير كافية. يرجى التأكيد يدوياً من الواجهة.");
        }
        return queueConfirmation(actor, userText, decision, toolsUsed, activeCaseId);
    }

    private VoiceAgentResponse queueConfirmation(
            Person actor,
            String userText,
            AgentStepDecision decision,
            List<String> toolsUsed,
            UUID activeCaseId) {
        AgentPendingAction pending = pendingActionService.create(
                actor,
                decision.tool(),
                decision.params(),
                decision.pendingSummary(),
                decision.confidence());
        VoiceAgentResponse response = new VoiceAgentResponse(
                "يتطلب تأكيدك: " + decision.pendingSummary()
                        + " — اضغط «تأكيد التنفيذ» للموافقة.",
                true,
                pending.getId(),
                decision.pendingSummary(),
                toolsUsed,
                activeCaseId);
        logCommand(actor, userText, response);
        return response;
    }

    private VoiceAgentResponse failResponse(
            Person actor,
            String userText,
            List<String> toolsUsed,
            UUID activeCaseId,
            String message) {
        VoiceAgentResponse response = new VoiceAgentResponse(
                message, false, null, null, toolsUsed, activeCaseId);
        logCommand(actor, userText, response);
        return response;
    }

    private void logCommand(Person actor, String input, VoiceAgentResponse response) {
        AgentCommandLog row = new AgentCommandLog();
        row.setCompany(companyRepository.getReferenceById(TenantContext.getRequired()));
        row.setPerson(actor);
        row.setInputText(input == null ? "" : input.trim());
        row.setReplyText(response.reply());
        try {
            row.setToolsUsed(objectMapper.writeValueAsString(response.toolsUsed()));
        } catch (Exception ex) {
            row.setToolsUsed(String.valueOf(response.toolsUsed()));
        }
        commandLogRepository.save(row);
    }
}
