package com.kalima.backend.casefile.service;

import com.kalima.backend.audit.AuditAction;
import com.kalima.backend.audit.entity.AuditLog;
import com.kalima.backend.audit.repository.AuditLogRepository;
import com.kalima.backend.audit.repository.AuditLogSpecifications;
import com.kalima.backend.audit.service.AuditLogService;
import com.kalima.backend.auth.support.CurrentUserService;
import com.kalima.backend.casefile.dto.CaseCreateRequest;
import com.kalima.backend.casefile.dto.CaseHistoryEntry;
import com.kalima.backend.casefile.dto.CaseResponse;
import com.kalima.backend.casefile.dto.CaseStatusUpdateRequest;
import com.kalima.backend.casefile.dto.CaseUpdateRequest;
import com.kalima.backend.casefile.dto.PartySummary;
import com.kalima.backend.caseparticipant.service.CaseParticipantService;
import com.kalima.backend.calendar.service.CalendarProjectionService;
import com.kalima.backend.casefile.entity.CaseFile;
import com.kalima.backend.domain.entity.Company;
import com.kalima.backend.domain.entity.DomainEnums;
import com.kalima.backend.domain.entity.Person;
import com.kalima.backend.casefile.repository.CaseFileRepository;
import com.kalima.backend.domain.repository.CompanyRepository;
import com.kalima.backend.domain.repository.PersonRepository;
import com.kalima.backend.domain.tenant.TenantContext;
import com.kalima.backend.disputeevent.service.DisputeWorkflowInitialEventsService;
import com.kalima.backend.notification.service.NotificationService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Business rules for the Cases module.
 *
 * <p>Tenant isolation is enforced by three layers:
 * <ol>
 *   <li>Hibernate tenantFilter (activated by {@code TenantFilterAspect} around
 *       every service call).</li>
 *   <li>Scoped repository lookups ({@code findScopedById}) that go through
 *       JPQL instead of {@code EntityManager.find}, so the filter applies
 *       even for primary-key reads.</li>
 *   <li>{@code @PrePersist} safety net on {@link CaseFile} that rejects any
 *       insert without a {@code company_id}.</li>
 * </ol>
 */
@Service
@Transactional
public class CaseService {

    private static final Set<DomainEnums.SystemRole> LAWYER_ROLES = EnumSet.of(
            DomainEnums.SystemRole.LAWYER,
            DomainEnums.SystemRole.SENIOR_LAWYER);

    private final CaseFileRepository caseFileRepository;
    private final PersonRepository personRepository;
    private final CompanyRepository companyRepository;
    private final CurrentUserService currentUserService;
    private final CaseParticipantService caseParticipantService;
    private final AuditLogService auditLogService;
    private final AuditLogRepository auditLogRepository;
    private final NotificationService notificationService;
    private final DisputeWorkflowInitialEventsService disputeWorkflowInitialEventsService;
    private final CalendarProjectionService calendarProjectionService;
    private final CaseNumberSequenceService caseNumberSequenceService;

    public CaseService(CaseFileRepository caseFileRepository,
                       PersonRepository personRepository,
                       CompanyRepository companyRepository,
                       CurrentUserService currentUserService,
                       CaseParticipantService caseParticipantService,
                       AuditLogService auditLogService,
                       AuditLogRepository auditLogRepository,
                       NotificationService notificationService,
                       DisputeWorkflowInitialEventsService disputeWorkflowInitialEventsService,
                       CalendarProjectionService calendarProjectionService,
                       CaseNumberSequenceService caseNumberSequenceService) {
        this.caseFileRepository = caseFileRepository;
        this.personRepository = personRepository;
        this.companyRepository = companyRepository;
        this.currentUserService = currentUserService;
        this.caseParticipantService = caseParticipantService;
        this.auditLogService = auditLogService;
        this.auditLogRepository = auditLogRepository;
        this.notificationService = notificationService;
        this.disputeWorkflowInitialEventsService = disputeWorkflowInitialEventsService;
        this.calendarProjectionService = calendarProjectionService;
        this.caseNumberSequenceService = caseNumberSequenceService;
    }

    public CaseResponse create(CaseCreateRequest request) {
        String caseNumber = trimToNull(request.caseNumber());
        if (caseNumber == null) {
            caseNumber = caseNumberSequenceService.nextNumber();
        }
        String title = requireText(request.title(), "title");

        if (caseFileRepository.existsByCaseNumberScoped(caseNumber)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "caseNumber already exists in this company: " + caseNumber);
        }

        Person client = requireClient(request.clientId());
        Person lawyer = resolveLawyer(request.assignedLawyerId());
        Person creator = currentUserService.requireCurrentPerson();

        CaseFile entity = new CaseFile();
        entity.setCompany(currentTenantReference());
        entity.setCaseNumber(caseNumber);
        entity.setCourtCaseNumber(trimToNull(request.courtCaseNumber()));
        entity.setTitle(title);
        entity.setDescription(trimToNull(request.description()));
        entity.setCaseType(trimToNull(request.caseType()));
        entity.setCourtName(trimToNull(request.courtName()));
        entity.setCourtCircuit(trimToNull(request.courtCircuit()));
        entity.setProcedureStage(trimToNull(request.procedureStage()));
        entity.setStatus(request.status() == null
                ? DomainEnums.CaseStatus.NEW
                : request.status());
        if (entity.getStatus() == DomainEnums.CaseStatus.ARCHIVED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Create a case before archiving it");
        }
        entity.setPriority(request.priority() == null
            ? DomainEnums.CasePriority.MEDIUM : request.priority());
        entity.setParentCase(resolveParentCase(request.parentCaseId()));
        entity.setStartDate(request.startDate());
        entity.setCloseDate(entity.getStatus() == DomainEnums.CaseStatus.CLOSED
            ? LocalDate.now() : null);
        entity.setClient(client);
        entity.setAssignedLawyer(lawyer);
        entity.setCreatedBy(creator);
        entity.setDisputePathType(request.disputePathType());

        CaseFile saved = caseFileRepository.save(entity);

        // Seed primary participants so the rich participants list stays in
        // sync with the denormalised shortcuts on CaseFile. Secondary lawyers,
        // witnesses, experts, etc. are added later via the participants API.
        caseParticipantService.addPrimarySeed(
                saved, client, DomainEnums.CaseParticipantRole.CLIENT, creator);
        caseParticipantService.addPrimarySeed(
                saved, lawyer, DomainEnums.CaseParticipantRole.ASSIGNED_LAWYER, creator);

        auditLogService.recordSuccess(
                AuditAction.CASE_CREATED, creator,
                "CaseFile", saved.getId(),
                Map.of(
                        "caseNumber", saved.getCaseNumber(),
                        "title", saved.getTitle(),
                        "status", saved.getStatus().name(),
                        "clientId", saved.getClient().getId().toString(),
                        "assignedLawyerId", lawyer == null ? "" : lawyer.getId().toString(),
                        "disputePathType", saved.getDisputePathType() == null
                                ? ""
                                : saved.getDisputePathType().name()
                ));

        disputeWorkflowInitialEventsService.seedIfApplicable(saved, saved.getDisputePathType());

        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public Page<CaseResponse> search(DomainEnums.CaseStatus status,
                                     DomainEnums.CasePriority priority,
                                     UUID clientId,
                                     UUID assignedLawyerId,
                                     LocalDate fromDate,
                                     LocalDate toDate,
                                     String search,
                                     boolean includeArchived,
                                     Pageable pageable) {
        Person actor = currentUserService.requireCurrentPerson();
        UUID effectiveClientId = clientId;
        boolean clientActor = actor.getSystemRole() == DomainEnums.SystemRole.CLIENT;
        if (clientActor) {
            // Portal: clients may only see their own cases; ignore caller-supplied clientId.
            effectiveClientId = actor.getId();
        }
        boolean actorCanSeeAll = actor.getSystemRole() == DomainEnums.SystemRole.ADMIN
            || actor.getSystemRole() == DomainEnums.SystemRole.SENIOR_LAWYER
            || actor.getSystemRole() == DomainEnums.SystemRole.ACCOUNTANT;
        String trimmed = trimToNull(search);
        if (fromDate != null && toDate != null && toDate.isBefore(fromDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "toDate must not be before fromDate");
        }
        Page<CaseFile> page = trimmed == null
                ? caseFileRepository.searchWithoutText(
                status, priority, fromDate, toDate, effectiveClientId,
                assignedLawyerId, actor.getId(), actorCanSeeAll, clientActor,
                includeArchived, pageable)
                : caseFileRepository.searchWithText(
                status, priority, fromDate, toDate, effectiveClientId,
                assignedLawyerId, actor.getId(), actorCanSeeAll, clientActor,
                trimmed, includeArchived, pageable);
        return page.map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public CaseResponse findById(UUID id) {
        CaseFile entity = loadCase(id);
        ensureClientCanReadCase(entity);
        return toResponse(entity);
    }

    @Transactional(readOnly = true)
    public Page<CaseResponse> relatedCases(UUID id, Pageable pageable) {
        CaseFile original = loadCase(id);
        ensureClientCanReadCase(original);
        return caseFileRepository.findRelatedCases(id, pageable).map(this::toResponse);
    }

    /**
     * Timeline of audit events for a single case. The access gate is the
     * same as {@link #findById}: tenant isolation via {@code loadCase} plus
     * the CLIENT-ownership check via {@link #ensureClientCanReadCase}.
     *
     * <p>The audit read is issued from inside this {@code @Transactional}
     * boundary so the {@code TenantFilterAspect} activates the Hibernate
     * {@code tenantFilter}, which adds {@code company_id = :companyId} to
     * the SQL. Combined with the explicit case-scoped specification, a
     * caller cannot read audit rows from another tenant even if they
     * guessed the UUID.
     */
    @Transactional(readOnly = true)
    public Page<CaseHistoryEntry> history(UUID caseId, Pageable pageable) {
        CaseFile entity = loadCase(caseId);
        Person actor = currentUserService.requireCurrentPerson();
        ensureClientCanReadCase(entity, actor);
        var spec = actor.getSystemRole() == DomainEnums.SystemRole.CLIENT
                ? AuditLogSpecifications.forClientVisibleCase(caseId)
                : AuditLogSpecifications.forCase(caseId);
        return auditLogRepository
                .findAll(spec, pageable)
                .map(this::toHistoryEntry);
    }

    private CaseHistoryEntry toHistoryEntry(AuditLog a) {
        Person actor = a.getActorPerson();
        return new CaseHistoryEntry(
                a.getId(),
                a.getAction(),
                a.getEntityType(),
                a.getEntityId(),
                actor != null ? actor.getId() : null,
                actor != null ? actor.getFullName() : null,
                a.getActorEmail(),
                a.getMetadata(),
                a.isSuccess(),
                a.getCreatedAt()
        );
    }

    public CaseResponse update(UUID id, CaseUpdateRequest request) {
        CaseFile entity = loadCase(id);
        if (entity.getArchivedAt() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Archived cases cannot be edited. Restore the case first.");
        }

        UUID oldLawyerId = entity.getAssignedLawyer() == null
                ? null : entity.getAssignedLawyer().getId();

        entity.setTitle(requireText(request.title(), "title"));
        entity.setCourtCaseNumber(trimToNull(request.courtCaseNumber()));
        entity.setDescription(trimToNull(request.description()));
        entity.setCaseType(trimToNull(request.caseType()));
        entity.setCourtName(trimToNull(request.courtName()));
        entity.setCourtCircuit(trimToNull(request.courtCircuit()));
        entity.setProcedureStage(trimToNull(request.procedureStage()));
        entity.setStartDate(request.startDate());
        entity.setCloseDate(entity.getStatus() == DomainEnums.CaseStatus.CLOSED
            ? (entity.getCloseDate() == null ? LocalDate.now() : entity.getCloseDate())
            : null);
        entity.setAssignedLawyer(resolveLawyer(request.assignedLawyerId()));
        if (request.priority() != null) {
            entity.setPriority(request.priority());
        }

        UUID newLawyerId = entity.getAssignedLawyer() == null
                ? null : entity.getAssignedLawyer().getId();

        auditLogService.recordSuccess(
                AuditAction.CASE_UPDATED,
                currentUserService.requireCurrentPerson(),
                "CaseFile", entity.getId(),
                Map.of(
                        "title", entity.getTitle(),
                        "oldLawyerId", oldLawyerId == null ? "" : oldLawyerId.toString(),
                        "newLawyerId", newLawyerId == null ? "" : newLawyerId.toString()
                ));
        return toResponse(entity);
    }

    public CaseResponse changeStatus(UUID id, CaseStatusUpdateRequest request) {
        if (request.status() == DomainEnums.CaseStatus.ARCHIVED) {
            // Funnel all archive transitions through the archive endpoint so
            // archivedAt stays in sync with the status.
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Use DELETE /api/cases/{id} to archive a case");
        }
        CaseFile entity = loadCase(id);
        if (entity.getArchivedAt() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Archived cases cannot change status. Restore first.");
        }
        DomainEnums.CaseStatus oldStatus = entity.getStatus();
        if (!isAllowedCaseTransition(oldStatus, request.status())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Invalid case status transition: " + oldStatus + " -> " + request.status());
        }
        entity.setStatus(request.status());
        entity.setCloseDate(request.status() == DomainEnums.CaseStatus.CLOSED
            ? (entity.getCloseDate() == null ? LocalDate.now() : entity.getCloseDate())
            : null);
        Person actor = currentUserService.requireCurrentPerson();

        auditLogService.recordSuccess(
                AuditAction.CASE_STATUS_CHANGED,
                actor,
                "CaseFile", entity.getId(),
                Map.of("old", oldStatus.name(), "new", entity.getStatus().name()));
        notificationService.notifyCaseStatusChanged(entity, oldStatus, actor);
        return toResponse(entity);
    }

    public CaseResponse archive(UUID id) {
        CaseFile entity = loadCase(id);
        if (entity.getArchivedAt() != null) {
            return toResponse(entity);
        }
        entity.setArchivedAt(LocalDateTime.now());
        entity.setStatus(DomainEnums.CaseStatus.ARCHIVED);
        calendarProjectionService.removeHearingsForCase(entity.getId());
        auditLogService.recordSuccess(
                AuditAction.CASE_ARCHIVED,
                currentUserService.requireCurrentPerson(),
                "CaseFile", entity.getId(),
                Map.of("caseNumber", entity.getCaseNumber()));
        return toResponse(entity);
    }

    public CaseResponse restore(UUID id) {
        CaseFile entity = loadCase(id);
        if (entity.getArchivedAt() == null) {
            return toResponse(entity);
        }
        entity.setArchivedAt(null);
        // Restored cases return to CLOSED rather than their pre-archive status
        // to avoid silently reviving cases that had been left IN_PROGRESS.
        // Callers can explicitly move them back to IN_PROGRESS afterwards.
        entity.setStatus(DomainEnums.CaseStatus.CLOSED);
        calendarProjectionService.syncHearingsForCase(entity.getId());
        auditLogService.recordSuccess(
                AuditAction.CASE_RESTORED,
                currentUserService.requireCurrentPerson(),
                "CaseFile", entity.getId(),
                Map.of("caseNumber", entity.getCaseNumber()));
        return toResponse(entity);
    }

    private CaseFile loadCase(UUID id) {
        return caseFileRepository.findScopedById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Case not found: " + id));
    }

    private CaseFile resolveParentCase(UUID parentCaseId) {
        if (parentCaseId == null) {
            return null;
        }
        CaseFile parent = loadCase(parentCaseId);
        if (parent.getArchivedAt() != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "An archived case cannot be the parent of a new case");
        }
        return parent;
    }

    private static boolean isAllowedCaseTransition(
            DomainEnums.CaseStatus from, DomainEnums.CaseStatus to) {
        if (from == to) {
            return true;
        }
        return switch (from) {
            case NEW -> to == DomainEnums.CaseStatus.IN_PROGRESS;
            case IN_PROGRESS -> to == DomainEnums.CaseStatus.ON_HOLD
                    || to == DomainEnums.CaseStatus.CLOSED;
            case ON_HOLD -> to == DomainEnums.CaseStatus.IN_PROGRESS
                    || to == DomainEnums.CaseStatus.CLOSED;
            case CLOSED -> to == DomainEnums.CaseStatus.IN_PROGRESS;
            case ARCHIVED -> false;
        };
    }

    /**
     * For {@link DomainEnums.SystemRole#CLIENT}, only the primary client of the
     * matter may read the case. Others already pass staff/accountant role checks
     * in the controller.
     */
    private void ensureClientCanReadCase(CaseFile entity) {
        ensureClientCanReadCase(entity, currentUserService.requireCurrentPerson());
    }

    private void ensureClientCanReadCase(CaseFile entity, Person actor) {
        if (actor.getSystemRole() == DomainEnums.SystemRole.CLIENT
                && !entity.getClient().getId().equals(actor.getId())) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Case not found: " + entity.getId());
        }
    }

    private Person requireClient(UUID clientId) {
        if (clientId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "clientId is required");
        }
        Person client = personRepository.findScopedById(clientId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "client not found in this company: " + clientId));
        if (client.getSystemRole() != DomainEnums.SystemRole.CLIENT) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Referenced person is not a CLIENT: " + clientId);
        }
        if (!client.isActive()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Client is inactive: " + clientId);
        }
        return client;
    }

    private Person resolveLawyer(UUID lawyerId) {
        if (lawyerId == null) {
            return null;
        }
        Person lawyer = personRepository.findScopedById(lawyerId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "assignedLawyer not found in this company: " + lawyerId));
        if (!LAWYER_ROLES.contains(lawyer.getSystemRole())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "assignedLawyer must have role LAWYER or SENIOR_LAWYER: "
                            + lawyerId);
        }
        if (!lawyer.isActive()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "assignedLawyer is inactive: " + lawyerId);
        }
        return lawyer;
    }

    private Company currentTenantReference() {
        UUID tenantId = TenantContext.getRequired();
        return companyRepository.getReferenceById(tenantId);
    }

    private CaseResponse toResponse(CaseFile entity) {
        return new CaseResponse(
                entity.getId(),
                entity.getCaseNumber(),
                entity.getCourtCaseNumber(),
                entity.getTitle(),
                entity.getDescription(),
                entity.getStatus(),
                entity.getPriority(),
                entity.getParentCase() == null ? null : entity.getParentCase().getId(),
                entity.getCaseType(),
                entity.getCourtName(),
                entity.getCourtCircuit(),
                entity.getProcedureStage(),
                entity.getDisputePathType(),
                entity.getStartDate(),
                entity.getCloseDate(),
                entity.getArchivedAt(),
                toParty(entity.getClient()),
                toParty(entity.getAssignedLawyer()),
                toParty(entity.getCreatedBy()),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private PartySummary toParty(Person person) {
        if (person == null) {
            return null;
        }
        return new PartySummary(
                person.getId(),
                person.getFullName(),
                person.getSystemRole());
    }

    private static String requireText(String value, String field) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    field + " is required");
        }
        return trimmed;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
