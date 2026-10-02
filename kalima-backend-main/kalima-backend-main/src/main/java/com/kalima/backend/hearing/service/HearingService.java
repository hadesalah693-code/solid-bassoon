package com.kalima.backend.hearing.service;

import com.kalima.backend.audit.AuditAction;
import com.kalima.backend.audit.service.AuditLogService;
import com.kalima.backend.auth.support.CurrentUserService;
import com.kalima.backend.casefile.dto.PartySummary;
import com.kalima.backend.casefile.entity.CaseFile;
import com.kalima.backend.domain.entity.Company;
import com.kalima.backend.domain.entity.DomainEnums;
import com.kalima.backend.domain.entity.Person;
import com.kalima.backend.casefile.repository.CaseFileRepository;
import com.kalima.backend.domain.repository.CompanyRepository;
import com.kalima.backend.domain.tenant.TenantContext;
import com.kalima.backend.hearing.dto.HearingCreateRequest;
import com.kalima.backend.hearing.entity.Hearing;
import com.kalima.backend.hearing.repository.HearingRepository;
import com.kalima.backend.hearing.dto.HearingOfficeItemResponse;
import com.kalima.backend.hearing.dto.HearingResponse;
import com.kalima.backend.hearing.dto.HearingUpdateRequest;
import com.kalima.backend.hearing.dto.HearingStatusUpdateRequest;
import com.kalima.backend.hearing.entity.HearingStatus;
import com.kalima.backend.hearing.entity.HearingType;
import com.kalima.backend.notification.service.NotificationService;
import com.kalima.backend.calendar.service.CalendarProjectionService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Hearings for a case: tenant-scoped, immutable {@code case_id} after create.
 */
@Service
@Transactional
public class HearingService {

    private final HearingRepository hearingRepository;
    private final CaseFileRepository caseFileRepository;
    private final CompanyRepository companyRepository;
    private final CurrentUserService currentUserService;
    private final AuditLogService auditLogService;
    private final NotificationService notificationService;
    private final CalendarProjectionService calendarProjectionService;

    public HearingService(HearingRepository hearingRepository,
                          CaseFileRepository caseFileRepository,
                          CompanyRepository companyRepository,
                          CurrentUserService currentUserService,
                          AuditLogService auditLogService,
                          NotificationService notificationService,
                          CalendarProjectionService calendarProjectionService) {
        this.hearingRepository = hearingRepository;
        this.caseFileRepository = caseFileRepository;
        this.companyRepository = companyRepository;
        this.currentUserService = currentUserService;
        this.auditLogService = auditLogService;
        this.notificationService = notificationService;
        this.calendarProjectionService = calendarProjectionService;
    }

    public HearingResponse create(UUID caseId, HearingCreateRequest request) {
        CaseFile caseFile = loadCase(caseId);
        ensureCaseMutable(caseFile);
        Person creator = currentUserService.requireCurrentPerson();

        validateHearingWindow(request.hearingDate(), request.nextHearingDate());

        Hearing h = new Hearing();
        h.setCompany(currentTenantReference());
        h.setCaseFile(caseFile);
        HearingStatus status = request.status() == null
            ? HearingStatus.SCHEDULED : request.status();
        if (status != HearingStatus.SCHEDULED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "A new hearing must start in SCHEDULED status");
        }
        validateHearingState(status, request.didAttend());
        h.setHearingDate(request.hearingDate());
        h.setCourtName(requireText(request.courtName(), "courtName"));
        h.setChamber(trimToNull(request.chamber()));
        h.setResult(trimToNull(request.result()));
        h.setNotes(trimToNull(request.notes()));
        h.setNextHearingDate(request.nextHearingDate());
        h.setHearingType(request.hearingType() == null
            ? HearingType.ORDINARY : request.hearingType());
        h.setStatus(status);
        h.setOutcomeCode(trimToNull(request.outcomeCode()));
        h.setDidAttend(request.didAttend());
        h.setPresidingJudge(trimToNull(request.presidingJudge()));
        h.setPreviousHearing(resolvePreviousHearing(
            caseId, request.previousHearingId(), null, request.hearingDate()));
        h.setDurationMinutes(request.durationMinutes());
        h.setCreatedBy(creator);

        Hearing saved = hearingRepository.save(h);
        auditLogService.recordSuccess(
                AuditAction.HEARING_CREATED,
                creator,
                "Hearing", saved.getId(),
                Map.of(
                        "caseId", caseId.toString(),
                        "hearingDate", saved.getHearingDate().toString(),
                        "courtName", saved.getCourtName()
                ));
        notificationService.notifyHearingScheduled(caseFile, saved, creator);
        calendarProjectionService.syncHearing(saved);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
        public Page<HearingResponse> list(
            UUID caseId, LocalDate from, LocalDate to,
            HearingStatus status, HearingType type, UUID lawyerId, Pageable pageable) {
        // Ensures 404 for unknown case in this tenant
        CaseFile caseFile = loadCase(caseId);
        ensureClientCanAccessCase(caseFile);
        validateDateRange(from, to);
        return hearingRepository.findByCaseIdForList(
                caseId, startOf(from), startOfNextDay(to), status, type, lawyerId, pageable)
                .map(this::toResponse);
    }

    /**
     * Per-tenant list of hearings on a given calendar day (default: today), only
     * for cases that are not archived. Aligns with {@code /api/dashboard/summary}
     * day boundaries ({@code date.atStartOfDay()} to {@code date+1.atStartOfDay()}).
     */
    @Transactional(readOnly = true)
    public Page<HearingOfficeItemResponse> listForOfficeOnDate(
            LocalDate date, LocalDate from, LocalDate to,
            HearingStatus status, HearingType type, UUID lawyerId,
            Pageable pageable) {
        validateDateRange(from, to);
        LocalDate d = date == null ? LocalDate.now() : date;
        LocalDateTime start = (from == null ? d : from).atStartOfDay();
        LocalDateTime end = (to == null ? (from == null ? d : from) : to)
                .plusDays(1).atStartOfDay();
        return hearingRepository
            .findOfficeHearingsForTimeRange(start, end, status, type, lawyerId, pageable)
                .map(this::toOfficeItem);
    }

    @Transactional(readOnly = true)
    public HearingResponse findById(UUID caseId, UUID hearingId) {
        Hearing h = loadHearingInCase(caseId, hearingId);
        ensureClientCanAccessCase(h.getCaseFile());
        return toResponse(h);
    }

    public HearingResponse update(UUID caseId, UUID hearingId, HearingUpdateRequest request) {
        Hearing h = loadHearingInCase(caseId, hearingId);
        ensureCaseMutable(h.getCaseFile());
        Person actor = currentUserService.requireCurrentPerson();

        validateHearingWindow(request.hearingDate(), request.nextHearingDate());

        h.setHearingDate(request.hearingDate());
        h.setCourtName(requireText(request.courtName(), "courtName"));
        h.setChamber(trimToNull(request.chamber()));
        h.setResult(trimToNull(request.result()));
        h.setNotes(trimToNull(request.notes()));
        h.setNextHearingDate(request.nextHearingDate());
        HearingStatus status = request.status() == null ? h.getStatus() : request.status();
        Boolean didAttend = request.didAttend() == null ? h.getDidAttend() : request.didAttend();
        if (!isAllowedTransition(h.getStatus(), status)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Invalid hearing status transition: " + h.getStatus() + " -> " + status);
        }
        validateHearingState(status, didAttend);
        h.setHearingType(request.hearingType() == null ? h.getHearingType() : request.hearingType());
        h.setStatus(status);
        if (request.outcomeCode() != null) {
            h.setOutcomeCode(trimToNull(request.outcomeCode()));
        }
        h.setDidAttend(didAttend);
        if (request.presidingJudge() != null) {
            h.setPresidingJudge(trimToNull(request.presidingJudge()));
        }
        if (request.previousHearingId() != null) {
            h.setPreviousHearing(resolvePreviousHearing(
                    caseId, request.previousHearingId(), hearingId, request.hearingDate()));
        }
        if (request.durationMinutes() != null) {
            h.setDurationMinutes(request.durationMinutes());
        }

        auditLogService.recordSuccess(
                AuditAction.HEARING_UPDATED,
                actor,
                "Hearing", h.getId(),
                Map.of(
                        "caseId", caseId.toString(),
                        "hearingDate", h.getHearingDate().toString()
                ));
        notificationService.notifyHearingUpdated(h.getCaseFile(), h, actor);
        calendarProjectionService.syncHearing(h);
        return toResponse(h);
    }

    public HearingResponse updateStatus(
            UUID caseId, UUID hearingId, HearingStatusUpdateRequest request) {
        Hearing h = loadHearingInCase(caseId, hearingId);
        ensureCaseMutable(h.getCaseFile());
        HearingStatus oldStatus = h.getStatus();
        if (!isAllowedTransition(oldStatus, request.status())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Invalid hearing status transition: " + oldStatus + " -> " + request.status());
        }
        Boolean didAttend = request.didAttend() == null ? h.getDidAttend() : request.didAttend();
        validateHearingState(request.status(), didAttend);
        h.setStatus(request.status());
        h.setDidAttend(didAttend);
        if (request.outcomeCode() != null) {
            h.setOutcomeCode(trimToNull(request.outcomeCode()));
        }
        Person actor = currentUserService.requireCurrentPerson();
        auditLogService.recordSuccess("HEARING_STATUS_CHANGED", actor, "Hearing", h.getId(),
                Map.of("old", oldStatus.name(), "new", h.getStatus().name()));
        calendarProjectionService.syncHearing(h);
        return toResponse(h);
    }

    public void delete(UUID caseId, UUID hearingId) {
        Hearing h = loadHearingInCase(caseId, hearingId);
        ensureCaseMutable(h.getCaseFile());
        if (hearingRepository.existsByPreviousHearingId(hearingId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Cannot delete a hearing referenced as another hearing's predecessor");
        }
        Person actor = currentUserService.requireCurrentPerson();
        calendarProjectionService.removeProjection(
                com.kalima.backend.domain.entity.DomainEnums.CalendarSourceModule.HEARING, hearingId);
        hearingRepository.delete(h);
        auditLogService.recordSuccess("HEARING_DELETED", actor, "Hearing", hearingId,
                Map.of("caseId", caseId.toString()));
    }

    // ----- helpers ----------------------------------------------------------

    private Hearing loadHearingInCase(UUID caseId, UUID hearingId) {
        Hearing h = hearingRepository.findScopedById(hearingId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Hearing not found: " + hearingId));
        if (!h.getCaseFile().getId().equals(caseId)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Hearing not found for case " + caseId);
        }
        return h;
    }

    private CaseFile loadCase(UUID caseId) {
        return caseFileRepository.findScopedById(caseId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Case not found: " + caseId));
    }

    /**
     * Clients may only read hearings for cases where they are the primary client.
     */
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
                    "Cannot add or edit hearings for an archived case.");
        }
    }

    private void validateHearingWindow(
            java.time.LocalDateTime hearingDate,
            java.time.LocalDateTime nextHearingDate) {
        if (nextHearingDate == null) {
            return;
        }
        if (nextHearingDate.isBefore(hearingDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "nextHearingDate must not be before hearingDate");
        }
    }

    private Hearing resolvePreviousHearing(
            UUID caseId, UUID previousId, UUID currentId, LocalDateTime hearingDate) {
        if (previousId == null) {
            return null;
        }
        if (previousId.equals(currentId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A hearing cannot refer to itself as its previous hearing");
        }
        Hearing previous = hearingRepository.findScopedById(previousId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "previousHearingId is not available in this tenant"));
        if (!previous.getCaseFile().getId().equals(caseId)
                || !previous.getHearingDate().isBefore(hearingDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "previousHearingId must refer to an earlier hearing on the same case");
        }
        return previous;
    }

    private static void validateHearingState(HearingStatus status, Boolean didAttend) {
        if (Boolean.TRUE.equals(didAttend) && status != HearingStatus.HELD) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "didAttend can only be true for a held hearing");
        }
    }

    private static boolean isAllowedTransition(HearingStatus from, HearingStatus to) {
        if (from == to) {
            return true;
        }
        return switch (from) {
            case SCHEDULED -> to != HearingStatus.SCHEDULED;
            case POSTPONED, ADJOURNED -> to == HearingStatus.SCHEDULED
                    || to == HearingStatus.CANCELLED || to == HearingStatus.HELD;
            case HELD, CANCELLED -> false;
        };
    }

    private static void validateDateRange(LocalDate from, LocalDate to) {
        if (from != null && to != null && to.isBefore(from)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "to must not be before from");
        }
    }

    private static LocalDateTime startOf(LocalDate date) {
        return date == null ? null : date.atStartOfDay();
    }

    private static LocalDateTime startOfNextDay(LocalDate date) {
        return date == null ? null : date.plusDays(1).atStartOfDay();
    }

    private Company currentTenantReference() {
        return companyRepository.getReferenceById(TenantContext.getRequired());
    }

    private HearingResponse toResponse(Hearing h) {
        Person createdBy = h.getCreatedBy();
        PartySummary party = toParty(createdBy);
        return new HearingResponse(
                h.getId(),
                h.getCaseFile().getId(),
                h.getHearingDate(),
                h.getCourtName(),
                h.getChamber(),
                h.getResult(),
                h.getNotes(),
                h.getNextHearingDate(),
                h.getHearingType(),
                h.getStatus(),
                h.getOutcomeCode(),
                h.getDidAttend(),
                h.getPresidingJudge(),
                h.getPreviousHearing() == null ? null : h.getPreviousHearing().getId(),
                h.getDurationMinutes(),
                party,
                h.getCreatedAt(),
                h.getUpdatedAt()
        );
    }

    private HearingOfficeItemResponse toOfficeItem(Hearing h) {
        CaseFile c = h.getCaseFile();
        return new HearingOfficeItemResponse(
                h.getId(),
                c.getId(),
                c.getCaseNumber(),
                c.getTitle(),
                h.getHearingDate(),
                h.getCourtName(),
                h.getChamber(),
                h.getResult(),
                h.getNotes(),
                h.getHearingType(),
                h.getStatus(),
                h.getOutcomeCode(),
                h.getDidAttend(),
                h.getPresidingJudge(),
                h.getPreviousHearing() == null ? null : h.getPreviousHearing().getId(),
                h.getDurationMinutes(),
                toParty(c.getClient()),
                toParty(c.getAssignedLawyer()),
                toParty(h.getCreatedBy()),
                h.getCreatedAt()
        );
    }

    private static PartySummary toParty(Person person) {
        if (person == null) {
            return null;
        }
        return new PartySummary(
                person.getId(), person.getFullName(), person.getSystemRole());
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
