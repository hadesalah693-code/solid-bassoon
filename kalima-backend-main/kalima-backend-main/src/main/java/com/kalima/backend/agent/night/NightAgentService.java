package com.kalima.backend.agent.night;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kalima.backend.agent.config.AgentProperties;
import com.kalima.backend.agent.night.dto.NightAgentReport;
import com.kalima.backend.agent.night.dto.NightAgentReport.AutoActionRecord;
import com.kalima.backend.agent.night.dto.NightAgentReport.InsightItem;
import com.kalima.backend.agent.safety.AgentSafetyService;
import com.kalima.backend.agent.tools.AgentToolContext;
import com.kalima.backend.agent.tools.AgentToolRegistry;
import com.kalima.backend.agent.tools.AgentToolResult;
import com.kalima.backend.audit.AuditAction;
import com.kalima.backend.audit.service.AuditLogService;
import com.kalima.backend.casefile.entity.CaseFile;
import com.kalima.backend.casefile.repository.CaseFileRepository;
import com.kalima.backend.casedocument.repository.CaseDocumentRepository;
import com.kalima.backend.domain.entity.DomainEnums;
import com.kalima.backend.domain.entity.Person;
import com.kalima.backend.domain.repository.CompanyRepository;
import com.kalima.backend.domain.repository.PersonRepository;
import com.kalima.backend.domain.tenant.TenantContext;
import com.kalima.backend.hearing.entity.Hearing;
import com.kalima.backend.hearing.repository.HearingRepository;
import com.kalima.backend.notification.service.NotificationService;
import com.kalima.backend.task.entity.TaskItem;
import com.kalima.backend.task.repository.TaskItemRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional
public class NightAgentService {

    private static final Logger log = LoggerFactory.getLogger(NightAgentService.class);
    private static final int MAX_CASES_SCAN = 300;
    private static final int LAWYER_OVERLOAD_THRESHOLD = 25;

    private final CaseFileRepository caseFileRepository;
    private final HearingRepository hearingRepository;
    private final CaseDocumentRepository caseDocumentRepository;
    private final TaskItemRepository taskItemRepository;
    private final NotificationService notificationService;
    private final PersonRepository personRepository;
    private final CompanyRepository companyRepository;
    private final NightAgentRunRepository runRepository;
    private final AgentToolRegistry toolRegistry;
    private final AgentSafetyService safetyService;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;

    public NightAgentService(CaseFileRepository caseFileRepository,
                             HearingRepository hearingRepository,
                             CaseDocumentRepository caseDocumentRepository,
                             TaskItemRepository taskItemRepository,
                             NotificationService notificationService,
                             PersonRepository personRepository,
                             CompanyRepository companyRepository,
                             NightAgentRunRepository runRepository,
                             AgentToolRegistry toolRegistry,
                             AgentSafetyService safetyService,
                             AuditLogService auditLogService,
                             ObjectMapper objectMapper) {
        this.caseFileRepository = caseFileRepository;
        this.hearingRepository = hearingRepository;
        this.caseDocumentRepository = caseDocumentRepository;
        this.taskItemRepository = taskItemRepository;
        this.notificationService = notificationService;
        this.personRepository = personRepository;
        this.companyRepository = companyRepository;
        this.runRepository = runRepository;
        this.toolRegistry = toolRegistry;
        this.safetyService = safetyService;
        this.auditLogService = auditLogService;
        this.objectMapper = objectMapper;
    }

    public NightAgentReport runForCurrentTenant() {
        Person actor = resolveNightActor();
        AgentToolContext ctx = new AgentToolContext(actor, TenantContext.getRequired(), true);

        List<InsightItem> highRisk = new ArrayList<>();
        List<InsightItem> warnings = new ArrayList<>();
        List<InsightItem> suggestions = new ArrayList<>();
        List<AutoActionRecord> autoActions = new ArrayList<>();

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime staleCutoff = now.minusDays(30);
        LocalDateTime hearing48hEnd = now.plusHours(48);

        Page<CaseFile> cases = caseFileRepository.searchWithoutText(
                null, null, null, null, null, null, null, true, false, false,
                PageRequest.of(0, MAX_CASES_SCAN, Sort.by(Sort.Direction.DESC, "updatedAt")));

        Map<UUID, Long> casesPerLawyer = new HashMap<>();

        for (CaseFile c : cases) {
            if (c.getStatus() == DomainEnums.CaseStatus.CLOSED
                    || c.getStatus() == DomainEnums.CaseStatus.ARCHIVED) {
                continue;
            }
            UUID caseId = c.getId();

            if (c.getUpdatedAt() != null && c.getUpdatedAt().isBefore(staleCutoff)) {
                warnings.add(item("STALE_CASE",
                        "لا تحديث منذ أكثر من 30 يوماً", c));
                maybeNotifyLawyer(actor, c, autoActions,
                        "قضية بدون تحديث",
                        "القضية " + c.getCaseNumber() + " لم تُحدَّث منذ 30 يوماً.");
            }

            if (c.getAssignedLawyer() == null) {
                highRisk.add(item("NO_LAWYER", "لا يوجد محامٍ معيّن", c));
                maybeAssignReminderTask(ctx, actor, c, autoActions,
                        "تعيين محامٍ للقضية " + c.getCaseNumber());
            } else {
                casesPerLawyer.merge(c.getAssignedLawyer().getId(), 1L, Long::sum);
            }

            int docCount = caseDocumentRepository.findByCaseId(
                    caseId, null, Sort.by("createdAt")).size();
            if (docCount == 0) {
                warnings.add(item("MISSING_DOCUMENTS", "لا توجد مستندات مرفوعة", c));
            }
        }

        Page<Hearing> upcoming48h = hearingRepository.findOfficeHearingsForTimeRange(
                now, hearing48hEnd, null, null, null,
                PageRequest.of(0, 100, Sort.by("hearingDate")));
        for (Hearing h : upcoming48h.getContent()) {
            CaseFile c = h.getCaseFile();
            highRisk.add(item("HEARING_48H",
                    "جلسة خلال 48 ساعة: " + h.getHearingDate(), c));
            if (h.getNotes() == null || h.getNotes().isBlank()) {
                warnings.add(item("HEARING_NO_NOTES", "جلسة قريبة بدون ملاحظات", c));
            }
        }

        analyzeTomorrowHearings(ctx, actor, now, warnings, suggestions, autoActions);
        analyzeWorkload(casesPerLawyer, warnings, suggestions);
        analyzeUnassignedTasks(warnings, suggestions);

        NightAgentReport report = new NightAgentReport(highRisk, warnings, suggestions, autoActions);
        persistRun(report);
        auditLogService.recordSuccess(AuditAction.AGENT_NIGHT_RUN, actor,
                "Company", TenantContext.getRequired(),
                Map.of("autoActions", autoActions.size(),
                        "highRisk", highRisk.size(),
                        "warnings", warnings.size()));
        log.info("Night agent completed for tenant {} — {} auto actions",
                TenantContext.getRequired(), autoActions.size());
        return report;
    }

    @Transactional(readOnly = true)
    public NightAgentReport latestReport() {
        return runRepository.findLatest()
                .map(run -> {
                    try {
                        return objectMapper.readValue(run.getReportJson(), NightAgentReport.class);
                    } catch (Exception ex) {
                        log.warn("Failed to read night agent report", ex);
                        return emptyReport();
                    }
                })
                .orElse(emptyReport());
    }

    private void analyzeTomorrowHearings(
            AgentToolContext ctx,
            Person actor,
            LocalDateTime now,
            List<InsightItem> warnings,
            List<InsightItem> suggestions,
            List<AutoActionRecord> autoActions) {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        LocalDateTime start = tomorrow.atStartOfDay();
        LocalDateTime end = tomorrow.plusDays(1).atStartOfDay();
        Page<Hearing> page = hearingRepository.findOfficeHearingsForTimeRange(
                start, end, null, null, null, PageRequest.of(0, 50, Sort.by("hearingDate")));
        for (Hearing h : page.getContent()) {
            CaseFile c = h.getCaseFile();
            suggestions.add(item("HEARING_TOMORROW",
                    "جلسة غداً — يُنصح بالتحضير", c));
            if (!hasPrepTask(c.getId())) {
                warnings.add(item("NO_PREP_TASK", "جلسة غداً بدون مهمة تحضير", c));
                if (safetyService.isNightAgentAllowed("assign_task")) {
                    Map<String, Object> params = Map.of(
                            "caseId", c.getId().toString(),
                            "title", "تحضير لجلسة " + tomorrow,
                            "description", "أُنشئت تلقائياً بواسطة وكيل الليل");
                    AgentToolResult result = toolRegistry.invoke("assign_task", ctx, params);
                    if (result.success()) {
                        autoActions.add(new AutoActionRecord(
                                "assign_task", result.message(), c.getId()));
                        auditLogService.recordSuccess(AuditAction.AGENT_NIGHT_AUTO_ACTION, actor,
                                "CaseFile", c.getId(), Map.of("tool", "assign_task"));
                    }
                }
            }
            if (safetyService.isNightAgentAllowed("generate_preparation_notes")) {
                toolRegistry.invoke("generate_preparation_notes", ctx,
                        Map.of("caseId", c.getId().toString()));
            }
        }
    }

    private void analyzeWorkload(
            Map<UUID, Long> casesPerLawyer,
            List<InsightItem> warnings,
            List<InsightItem> suggestions) {
        casesPerLawyer.forEach((lawyerId, count) -> {
            if (count >= LAWYER_OVERLOAD_THRESHOLD) {
                warnings.add(new InsightItem(
                        "LAWYER_OVERLOAD",
                        "محامٍ لديه " + count + " قضية نشطة",
                        null,
                        null));
                suggestions.add(new InsightItem(
                        "REBALANCE_WORKLOAD",
                        "راجع توزيع القضايا على الفريق",
                        null,
                        null));
            }
        });
    }

    private void analyzeUnassignedTasks(
            List<InsightItem> warnings,
            List<InsightItem> suggestions) {
        long pending = taskItemRepository.countPendingOnActiveCases(
                DomainEnums.TaskStatus.TODO,
                DomainEnums.TaskStatus.IN_PROGRESS);
        if (pending > 0) {
            suggestions.add(new InsightItem(
                    "PENDING_TASKS",
                    "يوجد " + pending + " مهمة نشطة على القضايا",
                    null,
                    null));
        }
    }

    private boolean hasPrepTask(UUID caseId) {
        Page<TaskItem> tasks = taskItemRepository.findByCaseIdForList(
                caseId, PageRequest.of(0, 20));
        return tasks.stream().anyMatch(t ->
                t.getTitle() != null && t.getTitle().contains("تحضير"));
    }

    private void maybeNotifyLawyer(
            Person actor,
            CaseFile c,
            List<AutoActionRecord> autoActions,
            String title,
            String body) {
        Person target = c.getAssignedLawyer() != null ? c.getAssignedLawyer() : actor;
        if (!safetyService.isNightAgentAllowed("send_notification")) {
            return;
        }
        notificationService.createNightInsightNotification(target, c, title, body);
        autoActions.add(new AutoActionRecord("send_notification", body, c.getId()));
        auditLogService.recordSuccess(AuditAction.AGENT_NIGHT_AUTO_ACTION, actor,
                "CaseFile", c.getId(), Map.of("tool", "send_notification"));
    }

    private void maybeAssignReminderTask(
            AgentToolContext ctx,
            Person actor,
            CaseFile c,
            List<AutoActionRecord> autoActions,
            String title) {
        if (!safetyService.isNightAgentAllowed("assign_task")) {
            return;
        }
        AgentToolResult result = toolRegistry.invoke("assign_task", ctx, Map.of(
                "caseId", c.getId().toString(),
                "title", title,
                "description", "تذكير من وكيل الليل"));
        if (result.success()) {
            autoActions.add(new AutoActionRecord("assign_task", result.message(), c.getId()));
            auditLogService.recordSuccess(AuditAction.AGENT_NIGHT_AUTO_ACTION, actor,
                    "CaseFile", c.getId(), Map.of("tool", "assign_task"));
        }
    }

    private Person resolveNightActor() {
        return personRepository.findBySystemRoleAndArchivedAtIsNull(
                        DomainEnums.SystemRole.ADMIN, PageRequest.of(0, 1))
                .stream()
                .findFirst()
                .orElseGet(() -> personRepository.findBySystemRoleAndArchivedAtIsNull(
                                DomainEnums.SystemRole.LAWYER, PageRequest.of(0, 1))
                        .stream()
                        .findFirst()
                        .orElseThrow(() -> new IllegalStateException(
                                "No staff user found for night agent")));
    }

    private void persistRun(NightAgentReport report) {
        try {
            NightAgentRun run = new NightAgentRun();
            run.setCompany(companyRepository.getReferenceById(TenantContext.getRequired()));
            run.setReportJson(objectMapper.writeValueAsString(report));
            run.setAutoActions(report.autoActions().size());
            runRepository.save(run);
        } catch (Exception ex) {
            log.error("Failed to persist night agent run", ex);
        }
    }

    private static InsightItem item(String code, String message, CaseFile c) {
        return new InsightItem(code, message, c.getId(), c.getCaseNumber());
    }

    private static NightAgentReport emptyReport() {
        return new NightAgentReport(List.of(), List.of(), List.of(), List.of());
    }
}
