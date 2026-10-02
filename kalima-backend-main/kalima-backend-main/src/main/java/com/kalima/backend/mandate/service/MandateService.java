package com.kalima.backend.mandate.service;

import com.kalima.backend.audit.AuditAction;
import com.kalima.backend.audit.service.AuditLogService;
import com.kalima.backend.auth.support.CurrentUserService;
import com.kalima.backend.casefile.dto.PartySummary;
import com.kalima.backend.casefile.entity.CaseFile;
import com.kalima.backend.casefile.repository.CaseFileRepository;
import com.kalima.backend.domain.entity.Company;
import com.kalima.backend.domain.entity.DomainEnums;
import com.kalima.backend.domain.entity.Person;
import com.kalima.backend.domain.repository.CompanyRepository;
import com.kalima.backend.domain.repository.PersonRepository;
import com.kalima.backend.domain.tenant.TenantContext;
import com.kalima.backend.hearing.entity.Hearing;
import com.kalima.backend.hearing.repository.HearingRepository;
import com.kalima.backend.mandate.dto.MandateCreateRequest;
import com.kalima.backend.mandate.dto.MandateListItemResponse;
import com.kalima.backend.mandate.dto.MandatePaymentRequest;
import com.kalima.backend.mandate.dto.MandatePaymentResponse;
import com.kalima.backend.mandate.dto.MandateResponse;
import com.kalima.backend.mandate.dto.MandateStatisticsResponse;
import com.kalima.backend.mandate.dto.MandateStatusUpdateRequest;
import com.kalima.backend.mandate.dto.MandateUpdateRequest;
import com.kalima.backend.mandate.entity.Mandate;
import com.kalima.backend.mandate.entity.MandateEvent;
import com.kalima.backend.mandate.entity.MandatePayment;
import com.kalima.backend.mandate.repository.MandateEventRepository;
import com.kalima.backend.mandate.repository.MandateNumberSequenceRepository;
import com.kalima.backend.mandate.repository.MandatePaymentRepository;
import com.kalima.backend.mandate.repository.MandateRepository;
import com.kalima.backend.mandate.repository.MandateSpecifications;
import com.kalima.backend.notification.service.NotificationService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional
public class MandateService {

    private static final Set<DomainEnums.SystemRole> LAWYER_ROLES = Set.of(
            DomainEnums.SystemRole.ADMIN,
            DomainEnums.SystemRole.SENIOR_LAWYER,
            DomainEnums.SystemRole.LAWYER);

    private static final int EXPORT_MAX_ROWS = 5000;

    private final MandateRepository mandateRepository;
    private final MandatePaymentRepository paymentRepository;
    private final MandateEventRepository eventRepository;
    private final MandateNumberSequenceRepository numberSequenceRepository;
    private final CaseFileRepository caseFileRepository;
    private final HearingRepository hearingRepository;
    private final PersonRepository personRepository;
    private final CompanyRepository companyRepository;
    private final CurrentUserService currentUserService;
    private final AuditLogService auditLogService;
    private final NotificationService notificationService;
    private final MandateDocumentService documentService;
    private final MandateCalendarSyncService calendarSyncService;

    public MandateService(MandateRepository mandateRepository,
                          MandatePaymentRepository paymentRepository,
                          MandateEventRepository eventRepository,
                          MandateNumberSequenceRepository numberSequenceRepository,
                          CaseFileRepository caseFileRepository,
                          HearingRepository hearingRepository,
                          PersonRepository personRepository,
                          CompanyRepository companyRepository,
                          CurrentUserService currentUserService,
                          AuditLogService auditLogService,
                          NotificationService notificationService,
                          MandateDocumentService documentService,
                          MandateCalendarSyncService calendarSyncService) {
        this.mandateRepository = mandateRepository;
        this.paymentRepository = paymentRepository;
        this.eventRepository = eventRepository;
        this.numberSequenceRepository = numberSequenceRepository;
        this.caseFileRepository = caseFileRepository;
        this.hearingRepository = hearingRepository;
        this.personRepository = personRepository;
        this.companyRepository = companyRepository;
        this.currentUserService = currentUserService;
        this.auditLogService = auditLogService;
        this.notificationService = notificationService;
        this.documentService = documentService;
        this.calendarSyncService = calendarSyncService;
    }

    public MandateResponse create(MandateCreateRequest request) {
        Person actor = currentUserService.requireCurrentPerson();
        CaseFile caseFile = requireCase(request.caseId());
        Person client = requireClient(caseFile, request.clientId());
        Person original = requireLawyer(request.originalLawyerId());
        Person substituted = requireLawyer(request.substitutedLawyerId());
        if (original.getId().equals(substituted.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "originalLawyerId and substitutedLawyerId must be different");
        }
        Hearing hearing = resolveHearing(request.hearingId(), caseFile);
        Hearing nextHearing = resolveHearing(request.nextHearingId(), caseFile);
        BigDecimal fees = normalize(request.fees());
        BigDecimal paid = normalize(request.amountPaid());
        validatePayment(fees, paid);

        UUID tenantId = TenantContext.getRequired();
        int year = LocalDateTime.now(ZoneId.systemDefault()).getYear();
        int seq = numberSequenceRepository.allocateNextSequence(tenantId, year);
        String number = "INV-" + year + "-" + String.format(Locale.ROOT, "%05d", seq);

        Mandate m = new Mandate();
        m.setCompany(currentTenantReference());
        m.setMandateNumber(number);
        m.setMandateYear(year);
        m.setCaseFile(caseFile);
        m.setHearing(hearing);
        m.setNextHearing(nextHearing);
        m.setClient(client);
        m.setOriginalLawyer(original);
        m.setSubstitutedLawyer(substituted);
        m.setPriority(request.priority());
        m.setStatus(DomainEnums.MandateStatus.PENDING);
        m.setSessionDate(request.sessionDate());
        validateHearingDates(m);
        m.setCourtName(trimToNull(request.courtName()));
        m.setCourtCircuit(trimToNull(request.courtCircuit()));
        m.setCourtroom(trimToNull(request.courtroom()));
        m.setReason(trimToNull(request.reason()));
        m.setNotes(trimToNull(request.notes()));
        m.setInstructions(trimToNull(request.instructions()));
        m.setFees(fees);
        m.setCurrency(normalizeCurrency(request.currency()));
        m.setAmountPaid(paid);
        m.setPaymentStatus(derivePaymentStatus(fees, paid));
        m.setCreatedBy(actor);

        Mandate saved = mandateRepository.save(m);
        calendarSyncService.sync(saved);
        recordEvent(saved, DomainEnums.MandateEventType.CREATED, actor, "تم إنشاء الإنابة");
        auditLogService.recordSuccess(
                AuditAction.MANDATE_CREATED,
                actor,
                "Mandate",
                saved.getId(),
                Map.of("mandateNumber", saved.getMandateNumber()));
        notificationService.notifyMandateCreated(saved, actor);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public Page<MandateListItemResponse> search(
            DomainEnums.MandateStatus status,
            DomainEnums.MandatePriority priority,
            DomainEnums.MandatePaymentStatus paymentStatus,
            UUID caseId,
            UUID originalLawyerId,
            UUID substitutedLawyerId,
            String courtName,
            LocalDateTime sessionFrom,
            LocalDateTime sessionTo,
            String search,
            Pageable pageable) {
        String normalizedSearch = search == null || search.isBlank()
                ? null : search.trim().toLowerCase(Locale.ROOT);
        Pageable paged = PageRequest.of(
                pageable.getPageNumber(),
                pageable.getPageSize(),
                Sort.by(Sort.Order.desc("createdAt")));
        return mandateRepository.findAll(
                        MandateSpecifications.filtered(
                                status, priority, paymentStatus, caseId, originalLawyerId,
                                substitutedLawyerId, courtName, sessionFrom, sessionTo,
                                normalizedSearch),
                        paged)
                .map(this::toListItem);
    }

    @Transactional(readOnly = true)
    public byte[] exportCsv(
            DomainEnums.MandateStatus status,
            DomainEnums.MandatePriority priority,
            DomainEnums.MandatePaymentStatus paymentStatus,
            UUID caseId,
            UUID originalLawyerId,
            UUID substitutedLawyerId,
            String courtName,
            LocalDateTime sessionFrom,
            LocalDateTime sessionTo,
            String search) {
        Page<MandateListItemResponse> page = search(
                status, priority, paymentStatus, caseId, originalLawyerId,
                substitutedLawyerId, courtName, sessionFrom, sessionTo, search,
                PageRequest.of(0, EXPORT_MAX_ROWS));
        StringBuilder sb = new StringBuilder();
        sb.append('\uFEFF');
        sb.append("رقم الإنابة,رقم القضية,عنوان القضية,المحكمة,تاريخ الجلسة,الموكل,المحامي المنيب,المحامي المُناب,الأولوية,الحالة,حالة الدفع,الأتعاب,العملة,تاريخ الإنشاء\n");
        for (MandateListItemResponse row : page.getContent()) {
            sb.append(csv(row.mandateNumber())).append(',');
            sb.append(csv(row.caseNumber())).append(',');
            sb.append(csv(row.caseTitle())).append(',');
            sb.append(csv(row.courtName())).append(',');
            sb.append(csv(row.sessionDate() == null ? "" : row.sessionDate().toString())).append(',');
            sb.append(csv(partyName(row.client()))).append(',');
            sb.append(csv(partyName(row.originalLawyer()))).append(',');
            sb.append(csv(partyName(row.substitutedLawyer()))).append(',');
            sb.append(csv(row.priority().name())).append(',');
            sb.append(csv(row.status().name())).append(',');
            sb.append(csv(row.paymentStatus().name())).append(',');
            sb.append(csv(row.fees() == null ? "" : row.fees().toPlainString())).append(',');
            sb.append(csv(row.currency() == null ? "" : row.currency())).append(',');
            sb.append(csv(row.createdAt() == null ? "" : row.createdAt().toString())).append('\n');
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    @Transactional(readOnly = true)
    public MandateResponse getById(UUID id) {
        return toResponse(loadMandate(id));
    }

    public MandateResponse update(UUID id, MandateUpdateRequest request) {
        Mandate m = loadMandate(id);
        Person actor = currentUserService.requireCurrentPerson();

        if (request.caseId() != null) {
            CaseFile caseFile = requireCase(request.caseId());
            m.setCaseFile(caseFile);
            if (m.getClient() != null
                    && caseFile.getClient() != null
                    && !m.getClient().getId().equals(caseFile.getClient().getId())) {
                // keep explicit client; only auto-sync when it was null or matches
            }
        }
        if (request.hearingId() != null) {
            m.setHearing(resolveHearing(request.hearingId(), m.getCaseFile()));
        }
        if (request.nextHearingId() != null) {
            m.setNextHearing(resolveHearing(request.nextHearingId(), m.getCaseFile()));
        }
        if (request.originalLawyerId() != null) {
            m.setOriginalLawyer(requireLawyer(request.originalLawyerId()));
        }
        if (request.substitutedLawyerId() != null) {
            m.setSubstitutedLawyer(requireLawyer(request.substitutedLawyerId()));
        }
        if (m.getOriginalLawyer() != null && m.getSubstitutedLawyer() != null
                && m.getOriginalLawyer().getId().equals(m.getSubstitutedLawyer().getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "originalLawyerId and substitutedLawyerId must be different");
        }
        if (request.priority() != null) {
            m.setPriority(request.priority());
        }
        if (request.status() != null) {
            applyStatus(m, request.status(), request.cancelledReason(), actor);
        }
        if (request.sessionDate() != null) {
            m.setSessionDate(request.sessionDate());
        }
        if (request.courtName() != null) {
            m.setCourtName(trimToNull(request.courtName()));
        }
        if (request.courtCircuit() != null) {
            m.setCourtCircuit(trimToNull(request.courtCircuit()));
        }
        if (request.courtroom() != null) {
            m.setCourtroom(trimToNull(request.courtroom()));
        }
        if (request.reason() != null) {
            m.setReason(trimToNull(request.reason()));
        }
        if (request.notes() != null) {
            m.setNotes(trimToNull(request.notes()));
        }
        if (request.instructions() != null) {
            m.setInstructions(trimToNull(request.instructions()));
        }
        if (request.fees() != null) {
            m.setFees(normalize(request.fees()));
        }
        if (request.currency() != null) {
            m.setCurrency(normalizeCurrency(request.currency()));
        }
        if (request.amountPaid() != null) {
            m.setAmountPaid(normalize(request.amountPaid()));
        }
        validatePayment(m.getFees(), m.getAmountPaid());
        validateHearingDates(m);
        DomainEnums.MandatePaymentStatus newPaymentStatus = derivePaymentStatus(
                m.getFees(), m.getAmountPaid());
        if (newPaymentStatus != m.getPaymentStatus()) {
            m.setPaymentStatus(newPaymentStatus);
        }

        Mandate saved = mandateRepository.save(m);
        calendarSyncService.sync(saved);
        auditLogService.recordSuccess(
                AuditAction.MANDATE_UPDATED,
                actor,
                "Mandate",
                saved.getId(),
                Map.of("mandateNumber", saved.getMandateNumber()));
        return toResponse(saved);
    }

    public MandateResponse changeStatus(UUID id, MandateStatusUpdateRequest request) {
        Mandate m = loadMandate(id);
        Person actor = currentUserService.requireCurrentPerson();
        applyStatus(m, request.status(), request.cancelledReason(), actor);
        Mandate saved = mandateRepository.save(m);
        calendarSyncService.sync(saved);
        auditLogService.recordSuccess(
                AuditAction.MANDATE_STATUS_CHANGED,
                actor,
                "Mandate",
                saved.getId(),
                Map.of("mandateNumber", saved.getMandateNumber(),
                        "status", saved.getStatus().name()));
        return toResponse(saved);
    }

    public void delete(UUID id) {
        Mandate m = loadMandate(id);
        Person actor = currentUserService.requireCurrentPerson();
        if (m.getStatus() == DomainEnums.MandateStatus.COMPLETED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Cannot delete a completed mandate; archive it instead");
        }
        String number = m.getMandateNumber();
        documentService.deleteAllForMandate(id);
        m.setDeletedAt(LocalDateTime.now());
        calendarSyncService.remove(id);
        auditLogService.recordSuccess(
                AuditAction.MANDATE_DELETED,
                actor,
                "Mandate",
                id,
                Map.of("mandateNumber", number));
    }

    /** Records the outcome of the mandate's session, optionally closing it. */
    public MandateResponse recordSessionResult(
            UUID id,
            com.kalima.backend.mandate.dto.MandateSessionResultRequest request) {
        Mandate m = loadMandate(id);
        Person actor = currentUserService.requireCurrentPerson();
        if (m.getStatus() == DomainEnums.MandateStatus.CANCELLED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Cannot record a session result for a cancelled mandate");
        }
        m.setDidAttend(request.didAttend());
        m.setSessionResult(trimToNull(request.sessionResult()));
        m.setCourtDecision(trimToNull(request.courtDecision()));
        m.setNotes(trimToNull(request.notes()));
        if (request.nextSessionDate() != null) {
            m.setNextSessionDate(request.nextSessionDate());
            m.setNextSessionNotes(trimToNull(request.nextSessionNotes()));
        } else if (request.nextSessionDate() == null) {
            m.setNextSessionDate(null);
            m.setNextSessionNotes(null);
        }
        if (m.getStatus() != DomainEnums.MandateStatus.COMPLETED) {
            m.setStatus(DomainEnums.MandateStatus.COMPLETED);
            m.setClosedAt(LocalDateTime.now());
        }
        Mandate saved = mandateRepository.save(m);
        calendarSyncService.sync(saved);
        recordEvent(saved, DomainEnums.MandateEventType.SESSION_RESULT_RECORDED, actor,
                trimToNull(request.sessionResult()));
        auditLogService.recordSuccess(
                AuditAction.MANDATE_SESSION_RESULT,
                actor,
                "Mandate",
                saved.getId(),
                Map.of("mandateNumber", saved.getMandateNumber(),
                        "didAttend", String.valueOf(request.didAttend())));
        notificationService.notifyMandateSessionResult(saved);
        return toResponse(saved);
    }

    // ----- payments -----

    @Transactional(readOnly = true)
    public List<MandatePaymentResponse> listPayments(UUID mandateId) {
        loadMandate(mandateId);
        return paymentRepository.findByMandate_Id(mandateId,
                        Sort.by(Sort.Direction.DESC, "paymentDate"))
                .stream()
                .map(this::toPaymentResponse)
                .toList();
    }

    public MandatePaymentResponse recordPayment(UUID mandateId, MandatePaymentRequest request) {
        Mandate m = loadMandate(mandateId);
        Person actor = currentUserService.requireCurrentPerson();
        if (m.getStatus() == DomainEnums.MandateStatus.CANCELLED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Cannot record payment on a cancelled mandate");
        }
        MandatePayment p = new MandatePayment();
        p.setCompany(currentTenantReference());
        p.setMandate(m);
        p.setAmount(request.amount());
        p.setPaymentDate(request.paymentDate());
        p.setMethod(trimToNull(request.method()));
        p.setNotes(trimToNull(request.notes()));
        p.setRecordedBy(actor);

        BigDecimal newPaid = m.getAmountPaid().add(request.amount());
        BigDecimal newFees = m.getFees();
        if (newPaid.compareTo(newFees) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "amountPaid cannot exceed total fees");
        }
        m.setAmountPaid(newPaid);
        m.setPaymentStatus(derivePaymentStatus(newFees, newPaid));

        MandatePayment saved = paymentRepository.save(p);
        mandateRepository.save(m);
        recordEvent(m, DomainEnums.MandateEventType.PAYMENT_RECORDED, actor,
                "دفعة " + request.amount() + " " + m.getCurrency());
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("mandateId", mandateId.toString());
        meta.put("amount", request.amount().toPlainString());
        auditLogService.recordSuccess(
                AuditAction.MANDATE_PAYMENT_RECORDED,
                actor,
                "Mandate",
                mandateId,
                meta);
        return toPaymentResponse(saved);
    }

    // ----- statistics & timeline -----

    @Transactional(readOnly = true)
    public MandateStatisticsResponse statistics() {
        LocalDateTime now = LocalDateTime.now(ZoneId.systemDefault());
        LocalDateTime todayStart = now.withHour(0).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime todayEnd = todayStart.plusDays(1);

        List<Mandate> all = mandateRepository.findAll();
        long today = all.stream().filter(m -> m.getSessionDate() != null
                        && !m.getSessionDate().isBefore(todayStart)
                        && m.getSessionDate().isBefore(todayEnd))
                .count();
        long upcoming = all.stream().filter(m -> m.getSessionDate() != null
                        && m.getSessionDate().isAfter(now)
                        && m.getStatus() != DomainEnums.MandateStatus.COMPLETED
                        && m.getStatus() != DomainEnums.MandateStatus.CANCELLED)
                .count();
        long completed = all.stream()
                .filter(m -> m.getStatus() == DomainEnums.MandateStatus.COMPLETED).count();
        long cancelled = all.stream()
                .filter(m -> m.getStatus() == DomainEnums.MandateStatus.CANCELLED).count();

        BigDecimal totalFees = all.stream()
                .map(Mandate::getFees)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalPaid = all.stream()
                .map(Mandate::getAmountPaid)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new MandateStatisticsResponse(
                all.size(), today, upcoming, completed, cancelled,
                totalFees, totalPaid, totalFees.subtract(totalPaid));
    }

    @Transactional(readOnly = true)
    public List<com.kalima.backend.mandate.dto.MandateEventResponse> listTimeline(UUID mandateId) {
        loadMandate(mandateId);
        return eventRepository.findByMandate_Id(mandateId,
                        Sort.by(Sort.Direction.ASC, "createdAt"))
                .stream()
                .map(this::toEventResponse)
                .toList();
    }

    // ----- internal helpers -----

    private void applyStatus(Mandate m, DomainEnums.MandateStatus status,
                             String cancelledReason, Person actor) {
        if (status == m.getStatus()) {
            return;
        }
        if (!isAllowedTransition(m.getStatus(), status)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Invalid mandate status transition: " + m.getStatus() + " -> " + status);
        }
        if (status == DomainEnums.MandateStatus.CANCELLED) {
            if (cancelledReason == null || cancelledReason.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "cancelledReason is required when status is CANCELLED");
            }
            m.setCancelledReason(cancelledReason.trim());
            m.setClosedAt(LocalDateTime.now());
            m.setStatus(DomainEnums.MandateStatus.CANCELLED);
            recordEvent(m, DomainEnums.MandateEventType.CANCELLED, actor, cancelledReason.trim());
            notificationService.notifyMandateCancelled(m, actor);
            return;
        }
        if (status == DomainEnums.MandateStatus.COMPLETED
                && m.getSessionResult() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Cannot complete a mandate without a session result; record it first");
        }
        if (status == DomainEnums.MandateStatus.CONFIRMED) {
            recordEvent(m, DomainEnums.MandateEventType.CONFIRMED, actor, "تم تأكيد الإنابة");
            notificationService.notifyMandateConfirmed(m, actor);
        } else if (status == DomainEnums.MandateStatus.COMPLETED) {
            m.setClosedAt(LocalDateTime.now());
            recordEvent(m, DomainEnums.MandateEventType.CLOSED, actor, "تم إغلاق الإنابة");
        }
        m.setStatus(status);
        m.setCancelledReason(null);
    }

    private static boolean isAllowedTransition(
            DomainEnums.MandateStatus from, DomainEnums.MandateStatus to) {
        if (to == DomainEnums.MandateStatus.CANCELLED) {
            return from == DomainEnums.MandateStatus.PENDING
                    || from == DomainEnums.MandateStatus.CONFIRMED
                    || from == DomainEnums.MandateStatus.IN_PROGRESS;
        }
        return switch (from) {
            case PENDING -> to == DomainEnums.MandateStatus.CONFIRMED;
            case CONFIRMED -> to == DomainEnums.MandateStatus.IN_PROGRESS;
            case IN_PROGRESS -> to == DomainEnums.MandateStatus.COMPLETED;
            case COMPLETED, CANCELLED -> false;
        };
    }

    private static void validateHearingDates(Mandate mandate) {
        if (mandate.getHearing() != null) {
            if (mandate.getSessionDate() == null) {
                mandate.setSessionDate(mandate.getHearing().getHearingDate());
            } else if (!mandate.getSessionDate().equals(mandate.getHearing().getHearingDate())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "sessionDate must match the linked hearing date");
            }
        }
        if (mandate.getNextHearing() != null
                && (!mandate.getNextHearing().getCaseFile().getId()
                        .equals(mandate.getCaseFile().getId())
                || mandate.getSessionDate() != null
                        && !mandate.getNextHearing().getHearingDate().isAfter(mandate.getSessionDate()))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "nextHearingId must be a later hearing on the same case");
        }
    }

    private void recordEvent(Mandate m, DomainEnums.MandateEventType type,
                             Person actor, String note) {
        MandateEvent e = new MandateEvent();
        e.setCompany(currentTenantReference());
        e.setMandate(m);
        e.setEventType(type);
        e.setNote(note);
        e.setRecordedBy(actor);
        eventRepository.save(e);
    }

    private Mandate loadMandate(UUID id) {
        return mandateRepository.findScopedById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "mandate not found"));
    }

    private CaseFile requireCase(UUID caseId) {
        return caseFileRepository.findScopedById(caseId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "case not found: " + caseId));
    }

    private Person requireClient(CaseFile caseFile, UUID clientId) {
        Person client = personRepository.findScopedById(clientId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "client not found: " + clientId));
        if (client.getSystemRole() != DomainEnums.SystemRole.CLIENT) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Referenced person is not a CLIENT: " + clientId);
        }
        if (caseFile.getClient() != null
                && !caseFile.getClient().getId().equals(client.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "clientId must match the case's client");
        }
        return client;
    }

    private Person requireLawyer(UUID lawyerId) {
        Person lawyer = personRepository.findScopedById(lawyerId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "lawyer not found: " + lawyerId));
        if (!LAWYER_ROLES.contains(lawyer.getSystemRole())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Referenced person cannot act as a mandate lawyer: " + lawyerId);
        }
        return lawyer;
    }

    private Hearing resolveHearing(UUID hearingId, CaseFile caseFile) {
        if (hearingId == null) {
            return null;
        }
        Hearing hearing = hearingRepository.findScopedById(hearingId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "hearing not found: " + hearingId));
        if (!hearing.getCaseFile().getId().equals(caseFile.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "hearingId must belong to the case's caseFile");
        }
        return hearing;
    }

    private Company currentTenantReference() {
        return companyRepository.getReferenceById(TenantContext.getRequired());
    }

    private static BigDecimal normalize(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static void validatePayment(BigDecimal fees, BigDecimal paid) {
        if (fees.compareTo(BigDecimal.ZERO) < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "fees cannot be negative");
        }
        if (paid.compareTo(BigDecimal.ZERO) < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "amountPaid cannot be negative");
        }
        if (paid.compareTo(fees) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "amountPaid cannot exceed total fees");
        }
    }

    private static DomainEnums.MandatePaymentStatus derivePaymentStatus(
            BigDecimal fees, BigDecimal paid) {
        if (fees.compareTo(BigDecimal.ZERO) == 0) {
            return DomainEnums.MandatePaymentStatus.PAID;
        }
        if (paid.compareTo(BigDecimal.ZERO) == 0) {
            return DomainEnums.MandatePaymentStatus.UNPAID;
        }
        if (paid.compareTo(fees) >= 0) {
            return DomainEnums.MandatePaymentStatus.PAID;
        }
        return DomainEnums.MandatePaymentStatus.PARTIALLY_PAID;
    }

    private static String normalizeCurrency(String currency) {
        if (currency == null || currency.isBlank()) {
            return "SAR";
        }
        return currency.trim().toUpperCase(Locale.ROOT);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String csv(String value) {
        if (value == null) {
            return "";
        }
        String escaped = value.replace("\"", "\"\"");
        if (escaped.contains(",") || escaped.contains("\"") || escaped.contains("\n")) {
            return "\"" + escaped + "\"";
        }
        return escaped;
    }

    private static String partyName(PartySummary party) {
        return party == null || party.fullName() == null ? "" : party.fullName();
    }

    private MandateListItemResponse toListItem(Mandate m) {
        return new MandateListItemResponse(
                m.getId(),
                m.getMandateNumber(),
                m.getStatus(),
                m.getPriority(),
                m.getPaymentStatus(),
                m.getCaseFile().getCaseNumber(),
                m.getCaseFile().getTitle(),
                toParty(m.getClient()),
                toParty(m.getOriginalLawyer()),
                toParty(m.getSubstitutedLawyer()),
                m.getSessionDate(),
                m.getCourtName(),
                m.getFees(),
                m.getCurrency(),
                m.getCreatedAt());
    }

    private MandateResponse toResponse(Mandate m) {
        return new MandateResponse(
                m.getId(),
                m.getMandateNumber(),
                m.getMandateYear(),
                m.getStatus(),
                m.getPriority(),
                m.getPaymentStatus(),
                m.getCurrency(),
                m.getFees(),
                m.getAmountPaid(),
                m.getFees().subtract(m.getAmountPaid()),
                m.getCaseFile().getId(),
                m.getCaseFile().getCaseNumber(),
                m.getCaseFile().getTitle(),
                m.getCaseFile().getCourtName(),
                m.getHearing() != null ? m.getHearing().getId() : null,
                m.getNextHearing() != null ? m.getNextHearing().getId() : null,
                m.getSessionDate(),
                m.getCourtCircuit(),
                m.getCourtroom(),
                toParty(m.getClient()),
                toParty(m.getOriginalLawyer()),
                toParty(m.getSubstitutedLawyer()),
                m.getReason(),
                m.getNotes(),
                m.getInstructions(),
                m.getCancelledReason(),
                m.getSessionResult(),
                m.getCourtDecision(),
                m.getDidAttend(),
                m.getNextSessionDate(),
                m.getNextSessionNotes(),
                m.getClosedAt(),
                m.getCreatedAt(),
                m.getUpdatedAt());
    }

    private MandatePaymentResponse toPaymentResponse(MandatePayment p) {
        return new MandatePaymentResponse(
                p.getId(),
                p.getAmount(),
                p.getPaymentDate(),
                p.getMethod(),
                p.getNotes(),
                p.getRecordedBy() != null ? p.getRecordedBy().getEmail() : null,
                p.getCreatedAt() != null ? p.getCreatedAt().toLocalDate() : null);
    }

    private com.kalima.backend.mandate.dto.MandateEventResponse toEventResponse(MandateEvent e) {
        return new com.kalima.backend.mandate.dto.MandateEventResponse(
                e.getId(),
                e.getEventType(),
                e.getNote(),
                e.getRecordedBy() != null ? e.getRecordedBy().getEmail() : null,
                e.getCreatedAt());
    }

    private PartySummary toParty(Person person) {
        if (person == null) {
            return null;
        }
        return new PartySummary(person.getId(), displayName(person), person.getSystemRole());
    }

    private static String displayName(Person person) {
        String n = person.getFullName();
        if (n != null && !n.isBlank()) {
            return n.trim();
        }
        if (person.getCompanyName() != null && !person.getCompanyName().isBlank()) {
            return person.getCompanyName().trim();
        }
        return person.getEmail() != null ? person.getEmail() : person.getId().toString();
    }
}
