package com.kalima.backend.task.service;

import com.kalima.backend.audit.AuditAction;
import com.kalima.backend.audit.service.AuditLogService;
import com.kalima.backend.auth.support.CurrentUserService;
import com.kalima.backend.casefile.dto.PartySummary;
import com.kalima.backend.casefile.entity.CaseFile;
import com.kalima.backend.casefile.repository.CaseFileRepository;
import com.kalima.backend.dispute.entity.Dispute;
import com.kalima.backend.dispute.repository.DisputeRepository;
import com.kalima.backend.domain.entity.Company;
import com.kalima.backend.domain.entity.DomainEnums;
import com.kalima.backend.domain.entity.Person;
import com.kalima.backend.domain.repository.CompanyRepository;
import com.kalima.backend.domain.repository.PersonRepository;
import com.kalima.backend.domain.tenant.TenantContext;
import com.kalima.backend.notification.service.NotificationService;
import com.kalima.backend.calendar.service.CalendarProjectionService;
import com.kalima.backend.task.dto.AdminTaskCreateRequest;
import com.kalima.backend.task.dto.AdminTaskUpdateRequest;
import com.kalima.backend.task.dto.OpenTaskCountResponse;
import com.kalima.backend.task.dto.TaskCreateRequest;
import com.kalima.backend.task.dto.TaskResponse;
import com.kalima.backend.task.dto.TaskUpdateRequest;
import com.kalima.backend.task.entity.TaskItem;
import com.kalima.backend.task.repository.TaskItemRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Case-, dispute-, and tenant-wide tasks; tenant isolation matches hearings.
 */
@Service
@Transactional
public class TaskService {

    private final TaskItemRepository taskItemRepository;
    private final CaseFileRepository caseFileRepository;
    private final DisputeRepository disputeRepository;
    private final CompanyRepository companyRepository;
    private final PersonRepository personRepository;
    private final CurrentUserService currentUserService;
    private final AuditLogService auditLogService;
    private final NotificationService notificationService;
    private final CalendarProjectionService calendarProjectionService;

    public TaskService(TaskItemRepository taskItemRepository,
                       CaseFileRepository caseFileRepository,
                       DisputeRepository disputeRepository,
                       CompanyRepository companyRepository,
                       PersonRepository personRepository,
                       CurrentUserService currentUserService,
                       AuditLogService auditLogService,
                       NotificationService notificationService,
                       CalendarProjectionService calendarProjectionService) {
        this.taskItemRepository = taskItemRepository;
        this.caseFileRepository = caseFileRepository;
        this.disputeRepository = disputeRepository;
        this.companyRepository = companyRepository;
        this.personRepository = personRepository;
        this.currentUserService = currentUserService;
        this.auditLogService = auditLogService;
        this.notificationService = notificationService;
        this.calendarProjectionService = calendarProjectionService;
    }

    public TaskResponse create(UUID caseId, TaskCreateRequest request) {
        CaseFile caseFile = loadCase(caseId);
        ensureCaseMutable(caseFile);
        return saveNewTask(
                caseFile,
                null,
                request.title(),
                request.description(),
                request.priority(),
                request.status(),
                request.dueDate(),
                request.assignedToId(),
                null,
                caseId.toString());
    }

    public TaskResponse createAdmin(AdminTaskCreateRequest request) {
        CaseFile caseFile = resolveOptionalCase(request.caseId());
        Dispute dispute = resolveOptionalDispute(request.disputeId());
        if (caseFile != null) {
            ensureCaseMutable(caseFile);
        }
        return saveNewTask(
                caseFile,
                dispute,
                request.title(),
                request.description(),
                request.priority(),
                request.status(),
                request.dueDate(),
                request.assignedToId(),
                request.adminTaskType(),
                linkAuditKey(caseFile, dispute));
    }

    private TaskResponse saveNewTask(
            CaseFile caseFile,
            Dispute dispute,
            String title,
            String description,
            DomainEnums.TaskPriority priority,
            DomainEnums.TaskStatus status,
            java.time.LocalDateTime dueDate,
            UUID assignedToId,
            DomainEnums.AdminTaskType adminTaskType,
            String linkKey) {
        Person creator = currentUserService.requireCurrentPerson();
        Person assignee = resolveAssignee(assignedToId);

        TaskItem t = new TaskItem();
        t.setCompany(currentTenantReference());
        t.setCaseFile(caseFile);
        t.setDispute(dispute);
        t.setTitle(requireText(title, "title"));
        t.setDescription(trimToNull(description));
        t.setPriority(priority);
        t.setStatus(status);
        t.setAdminTaskType(adminTaskType);
        t.setDueDate(dueDate);
        t.setAssignedTo(assignee);
        t.setCreatedBy(creator);
        if (status == DomainEnums.TaskStatus.DONE) {
            t.setCompletedAt(LocalDateTime.now());
        }

        TaskItem saved = taskItemRepository.save(t);
        auditLogService.recordSuccess(
                AuditAction.TASK_CREATED,
                creator,
                "TaskItem", saved.getId(),
                Map.of(
                        "link", linkKey,
                        "title", saved.getTitle(),
                        "status", saved.getStatus().name()
                ));
        notificationService.notifyTaskAssigned(saved, creator);
        calendarProjectionService.syncTask(saved);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public Page<TaskResponse> list(UUID caseId, Pageable pageable) {
        loadCase(caseId);
        return taskItemRepository.findByCaseIdForList(caseId, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<TaskResponse> listByDispute(UUID disputeId, Pageable pageable) {
        loadDispute(disputeId);
        return taskItemRepository.findByDisputeIdForList(disputeId, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<TaskResponse> listAll(
            DomainEnums.TaskStatus status,
            UUID assignedToId,
            UUID caseId,
            UUID disputeId,
            Boolean unassignedOnly,
            DomainEnums.AdminTaskType adminTaskType,
            Pageable pageable) {
        if (caseId != null) {
            loadCase(caseId);
        }
        if (disputeId != null) {
            loadDispute(disputeId);
        }
        return taskItemRepository.search(
                        status, assignedToId, caseId, disputeId, unassignedOnly, adminTaskType, pageable)
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public OpenTaskCountResponse myOpenCount() {
        UUID me = currentUserService.requireCurrentPerson().getId();
        long count = taskItemRepository.countOpenForAssignee(
                me,
                DomainEnums.TaskStatus.TODO,
                DomainEnums.TaskStatus.IN_PROGRESS);
        return new OpenTaskCountResponse(count);
    }

    @Transactional(readOnly = true)
    public TaskResponse findById(UUID caseId, UUID taskId) {
        TaskItem t = loadTaskInCase(caseId, taskId);
        return toResponse(t);
    }

    @Transactional(readOnly = true)
    public TaskResponse findById(UUID taskId) {
        TaskItem t = loadTask(taskId);
        return toResponse(t);
    }

    public TaskResponse update(UUID caseId, UUID taskId, TaskUpdateRequest request) {
        TaskItem t = loadTaskInCase(caseId, taskId);
        ensureCaseMutable(t.getCaseFile());
        return applyUpdate(t, request.title(), request.description(), request.priority(),
                request.status(), request.dueDate(), request.assignedToId(),
                t.getCaseFile(), t.getDispute(), caseId.toString());
    }

    public TaskResponse updateAdmin(UUID taskId, AdminTaskUpdateRequest request) {
        TaskItem t = loadTask(taskId);
        CaseFile caseFile = resolveOptionalCase(request.caseId());
        Dispute dispute = resolveOptionalDispute(request.disputeId());
        if (caseFile != null) {
            ensureCaseMutable(caseFile);
        } else if (t.getCaseFile() != null) {
            ensureCaseMutable(t.getCaseFile());
        }
        t.setCaseFile(caseFile);
        t.setDispute(dispute);
        t.setAdminTaskType(request.adminTaskType());
        return applyUpdate(t, request.title(), request.description(), request.priority(),
                request.status(), request.dueDate(), request.assignedToId(),
                caseFile, dispute, linkAuditKey(caseFile, dispute));
    }

    public void deleteInCase(UUID caseId, UUID taskId) {
        TaskItem t = loadTaskInCase(caseId, taskId);
        ensureCaseMutable(t.getCaseFile());
        deleteTask(t, caseId.toString());
    }

    public void delete(UUID taskId) {
        TaskItem t = loadTask(taskId);
        if (t.getCaseFile() != null) {
            ensureCaseMutable(t.getCaseFile());
        }
        deleteTask(t, linkAuditKey(t.getCaseFile(), t.getDispute()));
    }

    private void deleteTask(TaskItem t, String linkKey) {
        Person actor = currentUserService.requireCurrentPerson();
        UUID id = t.getId();
        calendarProjectionService.removeProjection(
                com.kalima.backend.domain.entity.DomainEnums.CalendarSourceModule.TASK, id);
        taskItemRepository.delete(t);
        auditLogService.recordSuccess(
                AuditAction.TASK_DELETED,
                actor,
                "TaskItem", id,
                Map.of("link", linkKey, "title", t.getTitle()));
    }

    private TaskResponse applyUpdate(
            TaskItem t,
            String title,
            String description,
            DomainEnums.TaskPriority priority,
            DomainEnums.TaskStatus status,
            java.time.LocalDateTime dueDate,
            UUID assignedToId,
            CaseFile caseFile,
            Dispute dispute,
            String linkKey) {
        Person actor = currentUserService.requireCurrentPerson();
        Person assignee = resolveAssignee(assignedToId);
        UUID previousAssigneeId = t.getAssignedTo() != null ? t.getAssignedTo().getId() : null;
        DomainEnums.TaskStatus previousStatus = t.getStatus();
        LocalDateTime previousDueDate = t.getDueDate();

        t.setTitle(requireText(title, "title"));
        t.setDescription(trimToNull(description));
        t.setPriority(priority);
        t.setStatus(status);
        t.setDueDate(dueDate);
        t.setAssignedTo(assignee);
        applyCompletionState(t, status, previousStatus);
        if (!java.util.Objects.equals(dueDate, previousDueDate)) {
            t.setDueSoonNotifiedAt(null);
            t.setOverdueNotifiedAt(null);
        }

        auditLogService.recordSuccess(
                AuditAction.TASK_UPDATED,
                actor,
                "TaskItem", t.getId(),
                Map.of("link", linkKey, "status", t.getStatus().name()));
        if (assignee != null
                && !assignee.getId().equals(actor.getId())
                && (previousAssigneeId == null || !previousAssigneeId.equals(assignee.getId()))) {
            notificationService.notifyTaskAssigned(t, actor);
        }
        calendarProjectionService.syncTask(t);
        return toResponse(t);
    }

    private TaskItem loadTask(UUID taskId) {
        return taskItemRepository.findScopedById(taskId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Task not found: " + taskId));
    }

    private TaskItem loadTaskInCase(UUID caseId, UUID taskId) {
        TaskItem t = loadTask(taskId);
        if (t.getCaseFile() == null || !t.getCaseFile().getId().equals(caseId)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Task not found for case " + caseId);
        }
        return t;
    }

    private CaseFile loadCase(UUID caseId) {
        return caseFileRepository.findScopedById(caseId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Case not found: " + caseId));
    }

    private Dispute loadDispute(UUID disputeId) {
        return disputeRepository.findScopedById(disputeId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Dispute not found: " + disputeId));
    }

    private CaseFile resolveOptionalCase(UUID caseId) {
        if (caseId == null) {
            return null;
        }
        return loadCase(caseId);
    }

    private Dispute resolveOptionalDispute(UUID disputeId) {
        if (disputeId == null) {
            return null;
        }
        return loadDispute(disputeId);
    }

    private void ensureCaseMutable(CaseFile c) {
        if (c == null) {
            return;
        }
        if (c.getArchivedAt() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Cannot add or edit tasks for an archived case.");
        }
    }

    private Person resolveAssignee(UUID assignedToId) {
        if (assignedToId == null) {
            return null;
        }
        Person p = personRepository.findScopedById(assignedToId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "assignedTo not found in this company: " + assignedToId));
        if (!p.isActive()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "assignedTo is inactive: " + assignedToId);
        }
        if (p.getArchivedAt() != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Cannot assign task to an archived person: " + assignedToId);
        }
        return p;
    }

    private Company currentTenantReference() {
        return companyRepository.getReferenceById(TenantContext.getRequired());
    }

    private static String linkAuditKey(CaseFile caseFile, Dispute dispute) {
        if (caseFile != null && dispute != null) {
            return "case:" + caseFile.getId() + ",dispute:" + dispute.getId();
        }
        if (caseFile != null) {
            return "case:" + caseFile.getId();
        }
        if (dispute != null) {
            return "dispute:" + dispute.getId();
        }
        return "standalone";
    }

    private TaskResponse toResponse(TaskItem t) {
        Person createdBy = t.getCreatedBy();
        PartySummary createdParty = new PartySummary(
                createdBy.getId(), createdBy.getFullName(), createdBy.getSystemRole());
        Person at = t.getAssignedTo();
        PartySummary assigneeParty = at == null
                ? null
                : new PartySummary(at.getId(), at.getFullName(), at.getSystemRole());
        CaseFile cf = t.getCaseFile();
        Dispute d = t.getDispute();
        return new TaskResponse(
                t.getId(),
                cf != null ? cf.getId() : null,
                d != null ? d.getId() : null,
                cf != null ? cf.getCaseNumber() : null,
                cf != null ? cf.getTitle() : null,
                d != null ? d.getDisputeNumber() : null,
                d != null ? d.getTitle() : null,
                t.getTitle(),
                t.getDescription(),
                t.getPriority(),
                t.getStatus(),
                t.getDueDate(),
                assigneeParty,
                createdParty,
                t.getAdminTaskType(),
                t.getCompletedAt(),
                t.getCreatedAt(),
                t.getUpdatedAt()
        );
    }

    private static void applyCompletionState(
            TaskItem t,
            DomainEnums.TaskStatus newStatus,
            DomainEnums.TaskStatus previousStatus) {
        if (newStatus == DomainEnums.TaskStatus.DONE
                && previousStatus != DomainEnums.TaskStatus.DONE) {
            t.setCompletedAt(LocalDateTime.now());
        } else if (newStatus != DomainEnums.TaskStatus.DONE) {
            t.setCompletedAt(null);
        }
    }

    private static String requireText(String value, String field) {
        String x = trimToNull(value);
        if (x == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " is required");
        }
        return x;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
