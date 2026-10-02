package com.kalima.backend.disputeevent.service;

import com.kalima.backend.audit.AuditAction;
import com.kalima.backend.audit.service.AuditLogService;
import com.kalima.backend.auth.support.CurrentUserService;
import com.kalima.backend.casefile.entity.CaseFile;
import com.kalima.backend.casefile.repository.CaseFileRepository;
import com.kalima.backend.domain.entity.Company;
import com.kalima.backend.domain.entity.DomainEnums;
import com.kalima.backend.domain.entity.Person;
import com.kalima.backend.domain.repository.CompanyRepository;
import com.kalima.backend.domain.tenant.TenantContext;
import com.kalima.backend.disputeevent.dto.DisputeEventCreateRequest;
import com.kalima.backend.disputeevent.dto.DisputeEventPatchRequest;
import com.kalima.backend.disputeevent.dto.DisputeEventResponse;
import com.kalima.backend.disputeevent.entity.DisputeEvent;
import com.kalima.backend.disputeevent.repository.DisputeEventRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.UUID;

/**
 * Case-scoped dispute-path events; tenant isolation matches
 * {@link com.kalima.backend.hearing.service.HearingService}.
 */
@Service
@Transactional
public class DisputeEventService {

    private final DisputeEventRepository disputeEventRepository;
    private final CaseFileRepository caseFileRepository;
    private final CompanyRepository companyRepository;
    private final CurrentUserService currentUserService;
    private final AuditLogService auditLogService;

    public DisputeEventService(DisputeEventRepository disputeEventRepository,
                               CaseFileRepository caseFileRepository,
                               CompanyRepository companyRepository,
                               CurrentUserService currentUserService,
                               AuditLogService auditLogService) {
        this.disputeEventRepository = disputeEventRepository;
        this.caseFileRepository = caseFileRepository;
        this.companyRepository = companyRepository;
        this.currentUserService = currentUserService;
        this.auditLogService = auditLogService;
    }

    public DisputeEventResponse create(UUID caseId, DisputeEventCreateRequest request) {
        CaseFile caseFile = loadCase(caseId);
        ensureCaseMutable(caseFile);
        Person actor = currentUserService.requireCurrentPerson();

        DisputeEvent e = new DisputeEvent();
        e.setCompany(currentTenantReference());
        e.setCaseFile(caseFile);
        e.setType(request.type());
        e.setStatus(request.status());
        e.setTitle(requireText(request.title(), "title"));
        e.setSummary(trimToNull(request.summary()));
        e.setEventDate(request.eventDate());
        e.setSortOrder(request.sortOrder() != null ? request.sortOrder() : 0);

        DisputeEvent saved = disputeEventRepository.save(e);
        auditLogService.recordSuccess(
                AuditAction.DISPUTE_EVENT_CREATED,
                actor,
                "DisputeEvent", saved.getId(),
                Map.of(
                        "caseId", caseId.toString(),
                        "type", saved.getType().name(),
                        "status", saved.getStatus().name()
                ));
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public Page<DisputeEventResponse> list(UUID caseId, Pageable pageable) {
        CaseFile caseFile = loadCase(caseId);
        ensureClientCanAccessCase(caseFile);
        return disputeEventRepository
                .findByCaseIdForList(caseId, pageable)
                .map(this::toResponse);
    }

    public DisputeEventResponse update(UUID caseId, UUID eventId, DisputeEventPatchRequest request) {
        DisputeEvent e = loadEventInCase(caseId, eventId);
        ensureCaseMutable(e.getCaseFile());
        Person actor = currentUserService.requireCurrentPerson();

        e.setType(request.type());
        e.setStatus(request.status());
        e.setTitle(requireText(request.title(), "title"));
        e.setSummary(trimToNull(request.summary()));
        e.setEventDate(request.eventDate());
        e.setSortOrder(request.sortOrder());

        auditLogService.recordSuccess(
                AuditAction.DISPUTE_EVENT_UPDATED,
                actor,
                "DisputeEvent", e.getId(),
                Map.of(
                        "caseId", caseId.toString(),
                        "type", e.getType().name(),
                        "status", e.getStatus().name()
                ));
        return toResponse(e);
    }

    public void delete(UUID caseId, UUID eventId) {
        DisputeEvent e = loadEventInCase(caseId, eventId);
        ensureCaseMutable(e.getCaseFile());
        Person actor = currentUserService.requireCurrentPerson();
        UUID id = e.getId();
        auditLogService.recordSuccess(
                AuditAction.DISPUTE_EVENT_DELETED,
                actor,
                "DisputeEvent", id,
                Map.of("caseId", caseId.toString()));
        disputeEventRepository.delete(e);
    }

    private DisputeEvent loadEventInCase(UUID caseId, UUID eventId) {
        DisputeEvent e = disputeEventRepository.findScopedById(eventId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Dispute event not found: " + eventId));
        if (!e.getCaseFile().getId().equals(caseId)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Dispute event not found for case " + caseId);
        }
        return e;
    }

    private CaseFile loadCase(UUID caseId) {
        return caseFileRepository.findScopedById(caseId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Case not found: " + caseId));
    }

    private void ensureClientCanAccessCase(CaseFile caseFile) {
        Person actor = currentUserService.requireCurrentPerson();
        if (actor.getSystemRole() == DomainEnums.SystemRole.CLIENT
                && !caseFile.getClient().getId().equals(actor.getId())) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Case not found: " + caseFile.getId());
        }
    }

    private void ensureCaseMutable(CaseFile c) {
        if (c.getArchivedAt() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Cannot add or edit dispute events for an archived case.");
        }
    }

    private Company currentTenantReference() {
        return companyRepository.getReferenceById(TenantContext.getRequired());
    }

    private DisputeEventResponse toResponse(DisputeEvent e) {
        return new DisputeEventResponse(
                e.getId(),
                e.getCaseFile().getId(),
                e.getType(),
                e.getStatus(),
                e.getTitle(),
                e.getSummary(),
                e.getEventDate(),
                e.getSortOrder(),
                e.getCreatedAt(),
                e.getUpdatedAt()
        );
    }

    private static String requireText(String value, String field) {
        String t = trimToNull(value);
        if (t == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " is required");
        }
        return t;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
