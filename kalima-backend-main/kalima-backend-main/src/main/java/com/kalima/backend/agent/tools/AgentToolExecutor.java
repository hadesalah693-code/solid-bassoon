package com.kalima.backend.agent.tools;

import com.kalima.backend.assistant.service.CaseContextBuilder;
import com.kalima.backend.assistant.service.DelegationContextBuilder;
import com.kalima.backend.assistant.service.DisputeContextBuilder;
import com.kalima.backend.audit.AuditAction;
import com.kalima.backend.audit.service.AuditLogService;
import com.kalima.backend.casefile.dto.CaseResponse;
import com.kalima.backend.casefile.dto.CaseStatusUpdateRequest;
import com.kalima.backend.casefile.dto.CaseUpdateRequest;
import com.kalima.backend.casefile.entity.CaseFile;
import com.kalima.backend.casefile.repository.CaseFileRepository;
import com.kalima.backend.casefile.service.CaseAccessService;
import com.kalima.backend.casefile.service.CaseService;
import com.kalima.backend.casedocument.repository.CaseDocumentRepository;
import com.kalima.backend.delegation.entity.Delegation;
import com.kalima.backend.delegation.repository.DelegationRepository;
import com.kalima.backend.delegation.repository.DelegationSpecifications;
import com.kalima.backend.dispute.entity.Dispute;
import com.kalima.backend.dispute.repository.DisputeRepository;
import com.kalima.backend.domain.entity.DomainEnums;
import com.kalima.backend.domain.entity.Person;
import com.kalima.backend.domain.repository.PersonRepository;
import com.kalima.backend.hearing.entity.Hearing;
import com.kalima.backend.hearing.repository.HearingRepository;
import com.kalima.backend.notification.service.NotificationService;
import com.kalima.backend.task.dto.TaskCreateRequest;
import com.kalima.backend.task.service.TaskService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Component
public class AgentToolExecutor {

    private static final DateTimeFormatter ISO_DATE = DateTimeFormatter.ISO_LOCAL_DATE;

    private final CaseService caseService;
    private final CaseFileRepository caseFileRepository;
    private final CaseAccessService caseAccessService;
    private final CaseContextBuilder caseContextBuilder;
    private final DelegationContextBuilder delegationContextBuilder;
    private final DisputeContextBuilder disputeContextBuilder;
    private final DelegationRepository delegationRepository;
    private final DisputeRepository disputeRepository;
    private final HearingRepository hearingRepository;
    private final CaseDocumentRepository caseDocumentRepository;
    private final TaskService taskService;
    private final NotificationService notificationService;
    private final PersonRepository personRepository;
    private final AuditLogService auditLogService;

    public AgentToolExecutor(CaseService caseService,
                             CaseFileRepository caseFileRepository,
                             CaseAccessService caseAccessService,
                             CaseContextBuilder caseContextBuilder,
                             DelegationContextBuilder delegationContextBuilder,
                             DisputeContextBuilder disputeContextBuilder,
                             DelegationRepository delegationRepository,
                             DisputeRepository disputeRepository,
                             HearingRepository hearingRepository,
                             CaseDocumentRepository caseDocumentRepository,
                             TaskService taskService,
                             NotificationService notificationService,
                             PersonRepository personRepository,
                             AuditLogService auditLogService) {
        this.caseService = caseService;
        this.caseFileRepository = caseFileRepository;
        this.caseAccessService = caseAccessService;
        this.caseContextBuilder = caseContextBuilder;
        this.delegationContextBuilder = delegationContextBuilder;
        this.disputeContextBuilder = disputeContextBuilder;
        this.delegationRepository = delegationRepository;
        this.disputeRepository = disputeRepository;
        this.hearingRepository = hearingRepository;
        this.caseDocumentRepository = caseDocumentRepository;
        this.taskService = taskService;
        this.notificationService = notificationService;
        this.personRepository = personRepository;
        this.auditLogService = auditLogService;
    }

    AgentToolResult openCase(AgentToolContext ctx, Map<String, Object> params) {
        String name = stringParam(params, "nameOrNumber", "name", "query");
        if (name == null || name.isBlank()) {
            return AgentToolResult.fail("اسم أو رقم القضية مطلوب.");
        }
        Page<CaseResponse> page = caseService.search(
                null, null, null, null, null, null, name.trim(), false,
                PageRequest.of(0, 5, Sort.by(Sort.Direction.DESC, "updatedAt")));
        if (page.isEmpty()) {
            return AgentToolResult.fail("لم أجد قضية تطابق: " + name);
        }
        List<Map<String, Object>> matches = new ArrayList<>();
        for (CaseResponse c : page.getContent()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("caseId", c.id());
            row.put("caseNumber", c.caseNumber());
            row.put("title", c.title());
            row.put("status", c.status());
            matches.add(row);
        }
        StringBuilder sb = new StringBuilder("نتائج البحث (").append(matches.size()).append("):\n");
        for (Map<String, Object> row : matches) {
            sb.append("• #")
                    .append(row.get("caseNumber"))
                    .append(" — ")
                    .append(row.get("title"))
                    .append("\n");
        }
        return AgentToolResult.ok(matches, sb.toString().trim(), page.getContent().get(0).id());
    }

    AgentToolResult closeCase(AgentToolContext ctx, Map<String, Object> params) {
        UUID caseId = uuidParam(params, "caseId");
        if (caseId == null) {
            return AgentToolResult.fail("caseId مطلوب.");
        }
        caseAccessService.requireCaseStatusChange(caseId, ctx.actor());
        CaseResponse updated = caseService.changeStatus(caseId,
                new CaseStatusUpdateRequest(DomainEnums.CaseStatus.CLOSED));
        auditLogService.recordSuccess(AuditAction.AGENT_CASE_CLOSED, ctx.actor(),
                "CaseFile", caseId, Map.of("via", "agent"));
        return AgentToolResult.ok(updated, "تم إغلاق القضية " + updated.caseNumber() + ".", caseId);
    }

    AgentToolResult getCaseSummary(AgentToolContext ctx, Map<String, Object> params) {
        UUID caseId = uuidParam(params, "caseId");
        if (caseId == null) {
            return AgentToolResult.fail("caseId مطلوب.");
        }
        String summary = caseContextBuilder.buildContext(caseId, ctx.actor());
        return AgentToolResult.ok(Map.of("summary", summary), summary, caseId);
    }

    AgentToolResult getHearings(AgentToolContext ctx, Map<String, Object> params) {
        LocalDateTime start;
        LocalDateTime end;
        String dateStr = stringParam(params, "date");
        if (dateStr != null) {
            LocalDate d = LocalDate.parse(dateStr, ISO_DATE);
            start = d.atStartOfDay();
            end = d.plusDays(1).atStartOfDay();
        } else {
            LocalDate from = parseDate(params.get("from"), LocalDate.now());
            LocalDate to = parseDate(params.get("to"), from.plusDays(7));
            start = from.atStartOfDay();
            end = to.plusDays(1).atStartOfDay();
        }
        Page<Hearing> page = hearingRepository.findOfficeHearingsForTimeRange(
                start, end, null, null, null,
                PageRequest.of(0, 20, Sort.by("hearingDate")));
        List<Map<String, Object>> items = new ArrayList<>();
        for (Hearing h : page.getContent()) {
            CaseFile cf = h.getCaseFile();
            try {
                caseAccessService.requireReadableCase(cf, ctx.actor());
            } catch (ResponseStatusException ex) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("hearingId", h.getId());
            row.put("caseId", cf.getId());
            row.put("caseNumber", cf.getCaseNumber());
            row.put("hearingDate", h.getHearingDate());
            row.put("courtName", h.getCourtName());
            items.add(row);
        }
        if (items.isEmpty()) {
            return AgentToolResult.ok(items, "لا توجد جلسات في الفترة المحددة.", null);
        }
        StringBuilder sb = new StringBuilder("الجلسات (").append(items.size()).append("):\n");
        UUID firstCaseId = null;
        for (Map<String, Object> row : items) {
            sb.append("• ")
                    .append(row.get("caseNumber"))
                    .append(" — ")
                    .append(row.get("hearingDate"))
                    .append(" — ")
                    .append(row.get("courtName"))
                    .append("\n");
            if (firstCaseId == null && row.get("caseId") instanceof UUID id) {
                firstCaseId = id;
            }
        }
        return AgentToolResult.ok(items, sb.toString().trim(), firstCaseId);
    }

    AgentToolResult listCases(AgentToolContext ctx, Map<String, Object> params) {
        DomainEnums.CaseStatus status = null;
        String statusStr = stringParam(params, "status");
        if (statusStr != null) {
            try {
                status = DomainEnums.CaseStatus.valueOf(statusStr.trim().toUpperCase());
            } catch (IllegalArgumentException ex) {
                return AgentToolResult.fail("حالة القضية غير صالحة: " + statusStr);
            }
        }
        String search = stringParam(params, "search");
        int limit = intParam(params, "limit", 10);
        Page<CaseResponse> page = caseService.search(
                status, null, null, null, null, null, search, false,
                PageRequest.of(0, Math.min(limit, 50), Sort.by(Sort.Direction.DESC, "updatedAt")));
        if (page.isEmpty()) {
            return AgentToolResult.ok(List.of(), "لا توجد قضايا مطابقة.", null);
        }
        StringBuilder sb = new StringBuilder("القضايا (").append(page.getNumberOfElements()).append("):\n");
        for (CaseResponse c : page.getContent()) {
            sb.append("• #")
                    .append(c.caseNumber())
                    .append(" — ")
                    .append(c.title())
                    .append(" [")
                    .append(c.status())
                    .append("]\n");
        }
        return AgentToolResult.ok(page.getContent(), sb.toString().trim(), page.getContent().get(0).id());
    }

    AgentToolResult createDelegation(AgentToolContext ctx, Map<String, Object> params) {
        UUID caseId = uuidParam(params, "caseId");
        if (caseId == null) {
            return AgentToolResult.fail("caseId مطلوب لإعداد التوكيل.");
        }
        caseAccessService.requireTaskCreate(caseId, ctx.actor());
        String notes = stringParam(params, "notes");
        String title = "متابعة إعداد توكيل";
        String desc = notes == null ? "طلب من المساعد: إعداد/مراجعة توكيل" : notes;
        TaskCreateRequest taskReq = new TaskCreateRequest(
                title, desc, DomainEnums.TaskPriority.HIGH,
                DomainEnums.TaskStatus.TODO,
                LocalDateTime.now().plusDays(3),
                ctx.actor().getId());
        var task = taskService.create(caseId, taskReq);
        auditLogService.recordSuccess(AuditAction.AGENT_DELEGATION_PREP, ctx.actor(),
                "CaseFile", caseId, Map.of("taskId", task.id().toString()));
        return AgentToolResult.ok(task,
                "تم إنشاء مهمة متابعة التوكيل. ارفع مستند التوكيل من قسم المستندات.", caseId);
    }

    AgentToolResult sendNotification(AgentToolContext ctx, Map<String, Object> params) {
        UUID userId = uuidParam(params, "userId");
        String message = stringParam(params, "message");
        if (userId == null || message == null || message.isBlank()) {
            return AgentToolResult.fail("userId و message مطلوبان.");
        }
        Person recipient = personRepository.findScopedById(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "User not found"));
        UUID caseId = uuidParam(params, "caseId");
        CaseFile caseFile = null;
        if (caseId != null) {
            caseFile = caseAccessService.requireReadableCase(caseId, ctx.actor());
        }
        notificationService.createAgentNotification(
                recipient, caseFile, "تنبيه من المساعد", message.trim());
        auditLogService.recordSuccess(AuditAction.AGENT_NOTIFICATION_SENT, ctx.actor(),
                "Person", userId, Map.of("caseId", caseId == null ? "" : caseId.toString()));
        return AgentToolResult.ok(Map.of("sent", true), "تم إرسال التنبيه.", caseId);
    }

    AgentToolResult assignTask(AgentToolContext ctx, Map<String, Object> params) {
        UUID caseId = uuidParam(params, "caseId");
        String title = stringParam(params, "title");
        if (caseId == null || title == null || title.isBlank()) {
            return AgentToolResult.fail("caseId و title مطلوبان.");
        }
        caseAccessService.requireTaskCreate(caseId, ctx.actor());
        UUID assignee = uuidParam(params, "assignedToId");
        if (assignee == null && ctx.actor().getSystemRole() != DomainEnums.SystemRole.CLIENT) {
            assignee = ctx.actor().getId();
        }
        LocalDateTime due = null;
        String dueStr = stringParam(params, "dueDate");
        if (dueStr != null) {
            due = LocalDate.parse(dueStr, ISO_DATE).atTime(17, 0);
        }
        TaskCreateRequest req = new TaskCreateRequest(
                title.trim(),
                stringParam(params, "description"),
                DomainEnums.TaskPriority.MEDIUM,
                DomainEnums.TaskStatus.TODO,
                due,
                assignee);
        var task = taskService.create(caseId, req);
        auditLogService.recordSuccess(AuditAction.AGENT_TASK_ASSIGNED, ctx.actor(),
                "TaskItem", task.id(), Map.of("caseId", caseId.toString()));
        return AgentToolResult.ok(task, "تم إنشاء المهمة.", caseId);
    }

    AgentToolResult modifyCaseSensitiveData(AgentToolContext ctx, Map<String, Object> params) {
        UUID caseId = uuidParam(params, "caseId");
        if (caseId == null) {
            return AgentToolResult.fail("caseId مطلوب.");
        }
        caseAccessService.requireCaseUpdate(caseId, ctx.actor());
        CaseResponse current = caseService.findById(caseId);
        CaseUpdateRequest update = new CaseUpdateRequest(
                stringParam(params, "courtCaseNumber") != null
                        ? stringParam(params, "courtCaseNumber") : current.courtCaseNumber(),
                stringParam(params, "title") != null ? stringParam(params, "title") : current.title(),
                stringParam(params, "description") != null ? stringParam(params, "description") : current.description(),
                stringParam(params, "caseType") != null ? stringParam(params, "caseType") : current.caseType(),
                stringParam(params, "courtName") != null ? stringParam(params, "courtName") : current.courtName(),
                stringParam(params, "courtCircuit") != null ? stringParam(params, "courtCircuit") : current.courtCircuit(),
                stringParam(params, "procedureStage") != null ? stringParam(params, "procedureStage") : current.procedureStage(),
                current.startDate(),
                current.closeDate(),
                uuidParam(params, "assignedLawyerId") != null
                        ? uuidParam(params, "assignedLawyerId")
                        : (current.assignedLawyer() != null ? current.assignedLawyer().id() : null),
                stringParam(params, "priority") != null
                        ? DomainEnums.CasePriority.valueOf(stringParam(params, "priority"))
                        : current.priority());
        CaseResponse updated = caseService.update(caseId, update);
        auditLogService.recordSuccess(AuditAction.AGENT_CASE_MODIFIED, ctx.actor(),
                "CaseFile", caseId, Map.of("via", "agent"));
        return AgentToolResult.ok(updated, "تم تحديث بيانات القضية.", caseId);
    }

    AgentToolResult generatePreparationNotes(AgentToolContext ctx, Map<String, Object> params) {
        UUID caseId = uuidParam(params, "caseId");
        if (caseId == null) {
            return AgentToolResult.fail("caseId مطلوب.");
        }
        caseAccessService.requireReadableCase(caseId, ctx.actor());
        String summary = caseContextBuilder.buildContext(caseId, ctx.actor());
        int docCount = caseDocumentRepository.findByCaseId(
                caseId, null, Sort.by(Sort.Direction.DESC, "createdAt")).size();
        LocalDateTime now = LocalDateTime.now();
        Page<Hearing> upcoming = hearingRepository.findOfficeHearingsForTimeRange(
                now, now.plusDays(14),
                null, null, null,
                PageRequest.of(0, 5, Sort.by("hearingDate")));
        List<Map<String, Object>> hearings = new ArrayList<>();
        for (Hearing h : upcoming.getContent()) {
            if (!h.getCaseFile().getId().equals(caseId)) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("date", h.getHearingDate());
            row.put("court", h.getCourtName());
            row.put("chamber", h.getChamber());
            hearings.add(row);
        }
        Map<String, Object> pack = new LinkedHashMap<>();
        pack.put("caseSummary", summary);
        pack.put("documentCount", docCount);
        pack.put("upcomingHearings", hearings);
        pack.put("checklist", List.of(
                "مراجعة مستندات القضية",
                "التأكد من موعد الجلسة والمحكمة",
                "إعداد المذكرات المطلوبة",
                "التنسيق مع العميل"));
        StringBuilder reply = new StringBuilder("حزمة التحضير للجلسة:\n");
        reply.append("• مستندات: ").append(docCount).append("\n");
        if (hearings.isEmpty()) {
            reply.append("• لا توجد جلسات قادمة خلال 14 يوماً.\n");
        } else {
            reply.append("• الجلسات القادمة:\n");
            for (Map<String, Object> h : hearings) {
                reply.append("  - ").append(h.get("date")).append(" — ").append(h.get("court")).append("\n");
            }
        }
        reply.append("• قائمة التحقق: مراجعة المستندات، تأكيد الموعد، إعداد المذكرات.");
        return AgentToolResult.ok(pack, reply.toString().trim(), caseId);
    }

    AgentToolResult listDelegations(AgentToolContext ctx, Map<String, Object> params) {
        String search = stringParam(params, "search");
        int limit = intParam(params, "limit", 10);
        LocalDate today = LocalDate.now();
        Page<Delegation> page = delegationRepository.findAll(
                DelegationSpecifications.filtered(
                        null, today, null, null, null, null, null, search == null ? null : search.toLowerCase(Locale.ROOT)),
                PageRequest.of(0, Math.min(limit, 50), Sort.by(Sort.Direction.DESC, "createdAt")));
        if (page.isEmpty()) {
            return AgentToolResult.ok(List.of(), "لا توجد توكيلات مطابقة.", null);
        }
        StringBuilder sb = new StringBuilder("التوكيلات (").append(page.getNumberOfElements()).append("):\n");
        for (Delegation d : page.getContent()) {
            sb.append("• #")
                    .append(d.getDelegationNumber())
                    .append(" — ")
                    .append(d.getTitle())
                    .append(" [")
                    .append(d.getStatus())
                    .append("] ")
                    .append(d.getExpiresAt() == null ? "بدون تاريخ انتهاء" : "ينتهي " + d.getExpiresAt())
                    .append("\n");
        }
        return AgentToolResult.ok(page.getContent(), sb.toString().trim(), null);
    }

    AgentToolResult getDelegationSummary(AgentToolContext ctx, Map<String, Object> params) {
        UUID delegationId = uuidParam(params, "delegationId");
        if (delegationId == null) {
            return AgentToolResult.fail("delegationId مطلوب.");
        }
        String summary = delegationContextBuilder.buildContext(delegationId, ctx.actor());
        UUID caseHint = delegationContextBuilder.resolveLinkedCaseId(delegationId);
        return AgentToolResult.ok(Map.of("summary", summary), summary, caseHint);
    }

    AgentToolResult listExpiringDelegations(AgentToolContext ctx, Map<String, Object> params) {
        int withinDays = intParam(params, "withinDays", 30);
        LocalDate today = LocalDate.now();
        LocalDate until = today.plusDays(withinDays);
        Page<Delegation> page = delegationRepository.findAll(
                DelegationSpecifications.filtered(
                        DomainEnums.DelegationStatus.ACTIVE, today, null, null, null, null, null, null),
                PageRequest.of(0, 50, Sort.by(Sort.Direction.ASC, "expiresAt")));
        List<Delegation> expiring = page.getContent().stream()
                .filter(d -> d.getExpiresAt() != null
                        && !d.getExpiresAt().isBefore(today)
                        && !d.getExpiresAt().isAfter(until))
                .toList();
        if (expiring.isEmpty()) {
            return AgentToolResult.ok(List.of(),
                    "لا توجد توكيلات سارية تنتهي خلال " + withinDays + " يوم.", null);
        }
        StringBuilder sb = new StringBuilder("توكيلات تنتهي قريباً (")
                .append(expiring.size()).append("):\n");
        for (Delegation d : expiring) {
            sb.append("• #")
                    .append(d.getDelegationNumber())
                    .append(" — ")
                    .append(d.getTitle())
                    .append(" — ينتهي ")
                    .append(d.getExpiresAt())
                    .append("\n");
        }
        return AgentToolResult.ok(expiring, sb.toString().trim(), null);
    }

    AgentToolResult listDisputes(AgentToolContext ctx, Map<String, Object> params) {
        String search = stringParam(params, "search");
        int limit = intParam(params, "limit", 10);
        Page<Dispute> page = disputeRepository.search(
                null, null, null, null,
                search == null ? null : search.toLowerCase(Locale.ROOT),
                PageRequest.of(0, Math.min(limit, 50), Sort.by(Sort.Direction.DESC, "createdAt")));
        if (page.isEmpty()) {
            return AgentToolResult.ok(List.of(), "لا توجد نزاعات مطابقة.", null);
        }
        StringBuilder sb = new StringBuilder("النزاعات (").append(page.getNumberOfElements()).append("):\n");
        UUID firstCase = null;
        for (Dispute d : page.getContent()) {
            sb.append("• #")
                    .append(d.getDisputeNumber())
                    .append(" — ")
                    .append(d.getTitle())
                    .append(" — ")
                    .append(d.getOpponentName())
                    .append("\n");
            if (firstCase == null && d.getConvertedCase() != null) {
                firstCase = d.getConvertedCase().getId();
            }
        }
        return AgentToolResult.ok(page.getContent(), sb.toString().trim(), firstCase);
    }

    AgentToolResult getDisputeSummary(AgentToolContext ctx, Map<String, Object> params) {
        UUID disputeId = uuidParam(params, "disputeId");
        if (disputeId == null) {
            return AgentToolResult.fail("disputeId مطلوب.");
        }
        String summary = disputeContextBuilder.buildContext(disputeId, ctx.actor());
        UUID caseHint = disputeContextBuilder.resolveConvertedCaseId(disputeId);
        return AgentToolResult.ok(Map.of("summary", summary), summary, caseHint);
    }

    private static String stringParam(Map<String, Object> params, String... keys) {
        for (String key : keys) {
            Object v = params.get(key);
            if (v != null && !v.toString().isBlank()) {
                return v.toString().trim();
            }
        }
        return null;
    }

    private static UUID uuidParam(Map<String, Object> params, String key) {
        Object v = params.get(key);
        if (v == null) return null;
        try {
            return UUID.fromString(v.toString().trim());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static int intParam(Map<String, Object> params, String key, int defaultVal) {
        Object v = params.get(key);
        if (v == null) return defaultVal;
        try {
            return Integer.parseInt(v.toString());
        } catch (NumberFormatException ex) {
            return defaultVal;
        }
    }

    private static LocalDate parseDate(Object v, LocalDate defaultVal) {
        if (v == null) return defaultVal;
        try {
            return LocalDate.parse(v.toString().trim(), ISO_DATE);
        } catch (Exception ex) {
            return defaultVal;
        }
    }
}
