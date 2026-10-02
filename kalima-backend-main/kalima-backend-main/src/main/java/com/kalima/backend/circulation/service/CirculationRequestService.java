package com.kalima.backend.circulation.service;

import com.kalima.backend.audit.service.AuditLogService;
import com.kalima.backend.auth.support.CurrentUserService;
import com.kalima.backend.casefile.entity.CaseFile;
import com.kalima.backend.casefile.repository.CaseFileRepository;
import com.kalima.backend.casefile.service.CaseAccessService;
import com.kalima.backend.circulation.dto.CirculationCreateRequest;
import com.kalima.backend.circulation.dto.CirculationEventResponse;
import com.kalima.backend.circulation.dto.CirculationResponse;
import com.kalima.backend.circulation.entity.CirculationEventType;
import com.kalima.backend.circulation.entity.CirculationRequest;
import com.kalima.backend.circulation.entity.CirculationRequestEvent;
import com.kalima.backend.circulation.entity.CirculationStatus;
import com.kalima.backend.circulation.repository.CirculationRequestEventRepository;
import com.kalima.backend.circulation.repository.CirculationRequestRepository;
import com.kalima.backend.domain.entity.Company;
import com.kalima.backend.domain.entity.DomainEnums;
import com.kalima.backend.domain.entity.Person;
import com.kalima.backend.domain.repository.CompanyRepository;
import com.kalima.backend.domain.repository.PersonRepository;
import com.kalima.backend.domain.tenant.TenantContext;
import com.kalima.backend.notification.service.NotificationService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional
public class CirculationRequestService {

    private final CirculationRequestRepository requestRepository;
    private final CirculationRequestEventRepository eventRepository;
    private final CaseFileRepository caseRepository;
    private final CaseAccessService caseAccessService;
    private final PersonRepository personRepository;
    private final CompanyRepository companyRepository;
    private final CurrentUserService currentUserService;
    private final AuditLogService auditLogService;
    private final NotificationService notificationService;

    public CirculationRequestService(
            CirculationRequestRepository requestRepository,
            CirculationRequestEventRepository eventRepository,
            CaseFileRepository caseRepository,
            CaseAccessService caseAccessService,
            PersonRepository personRepository,
            CompanyRepository companyRepository,
            CurrentUserService currentUserService,
            AuditLogService auditLogService,
            NotificationService notificationService) {
        this.requestRepository = requestRepository;
        this.eventRepository = eventRepository;
        this.caseRepository = caseRepository;
        this.caseAccessService = caseAccessService;
        this.personRepository = personRepository;
        this.companyRepository = companyRepository;
        this.currentUserService = currentUserService;
        this.auditLogService = auditLogService;
        this.notificationService = notificationService;
    }

    public CirculationResponse create(CirculationCreateRequest input) {
        Person actor = requireStaffActor();
        CaseFile caseFile = caseRepository.findScopedById(input.caseId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Case not found"));
        caseAccessService.requireReadableCase(caseFile, actor);
        if (caseFile.getArchivedAt() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cannot circulate an archived case");
        }
        Person from = requireStaffPerson(input.fromPersonId());
        Person to = requireStaffPerson(input.toPersonId());
        if (from.getId().equals(to.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "fromPersonId and toPersonId must differ");
        }
        LocalDateTime now = LocalDateTime.now();
        if (input.dueBackAt() != null && !input.dueBackAt().isAfter(now)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "dueBackAt must be in the future");
        }
        CirculationRequest request = new CirculationRequest();
        request.setCompany(companyRepository.getReferenceById(TenantContext.getRequired()));
        request.setCaseFile(caseFile);
        request.setFromPerson(from);
        request.setToPerson(to);
        request.setRequestedBy(actor);
        request.setRequestedAt(now);
        request.setDueBackAt(input.dueBackAt());
        request.setStatus(CirculationStatus.PENDING);
        request.setReason(input.reason().trim());
        request.setNotes(trim(input.notes()));
        CirculationRequest saved = requestRepository.save(request);
        recordEvent(saved, CirculationEventType.REQUESTED, actor, saved.getNotes());
        audit("CIRCULATION_REQUESTED", actor, saved);
        notificationService.notifyCirculationUpdate(saved, to, "طلب جديد");
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public Page<CirculationResponse> inbox(CirculationStatus status, Pageable pageable) {
        Person actor = requireStaffActor();
        return requestRepository.inbox(TenantContext.getRequired(), actor.getId(), status, pageable)
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<CirculationResponse> outbox(CirculationStatus status, Pageable pageable) {
        Person actor = requireStaffActor();
        return requestRepository.outbox(TenantContext.getRequired(), actor.getId(), status, pageable)
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<CirculationResponse> forCase(UUID caseId, Pageable pageable) {
        caseAccessService.requireReadableCase(caseId, requireStaffActor());
        return requestRepository.findByCaseId(caseId, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public CirculationResponse get(UUID id) {
        CirculationRequest request = load(id);
        caseAccessService.requireReadableCase(request.getCaseFile(), requireStaffActor());
        return toResponse(request);
    }

    public CirculationResponse approve(UUID id, String notes) {
        CirculationRequest request = load(id);
        Person actor = requireApprover();
        requireStatus(request, CirculationStatus.PENDING);
        request.setApprovedBy(actor);
        request.setStatus(CirculationStatus.APPROVED);
        transition(request, CirculationEventType.APPROVED, actor, notes, "تمت الموافقة");
        return toResponse(request);
    }

    public CirculationResponse reject(UUID id, String notes) {
        CirculationRequest request = load(id);
        Person actor = requireApprover();
        requireStatus(request, CirculationStatus.PENDING);
        request.setStatus(CirculationStatus.REJECTED);
        transition(request, CirculationEventType.REJECTED, actor, notes, "تم رفض الطلب");
        return toResponse(request);
    }

    public CirculationResponse handover(UUID id, String notes) {
        CirculationRequest request = load(id);
        Person actor = requireStaffActor();
        requireStatus(request, CirculationStatus.APPROVED);
        if (!isPrivileged(actor) && !request.getFromPerson().getId().equals(actor.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the sender can hand over the file");
        }
        request.setStatus(CirculationStatus.IN_HAND);
        request.setHandedAt(LocalDateTime.now());
        transition(request, CirculationEventType.HANDED_OVER, actor, notes, "تم تسليم الملف");
        return toResponse(request);
    }

    public CirculationResponse returnFile(UUID id, String notes) {
        CirculationRequest request = load(id);
        Person actor = requireStaffActor();
        requireStatus(request, CirculationStatus.IN_HAND);
        if (!isPrivileged(actor) && !request.getToPerson().getId().equals(actor.getId())
                && !request.getFromPerson().getId().equals(actor.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not allowed to return this file");
        }
        request.setStatus(CirculationStatus.RETURNED);
        request.setReturnedAt(LocalDateTime.now());
        transition(request, CirculationEventType.RETURNED, actor, notes, "تمت إعادة الملف");
        return toResponse(request);
    }

    public CirculationResponse cancel(UUID id, String notes) {
        CirculationRequest request = load(id);
        Person actor = requireStaffActor();
        requireStatus(request, CirculationStatus.PENDING);
        if (!isPrivileged(actor) && !request.getRequestedBy().getId().equals(actor.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the requester can cancel");
        }
        request.setStatus(CirculationStatus.CANCELLED);
        transition(request, CirculationEventType.CANCELLED, actor, notes, "تم إلغاء الطلب");
        return toResponse(request);
    }

    private void transition(CirculationRequest request, CirculationEventType type,
                            Person actor, String notes, String notification) {
        String cleanNotes = trim(notes);
        recordEvent(request, type, actor, cleanNotes);
        audit("CIRCULATION_" + type.name(), actor, request);
        Person other = actor.getId().equals(request.getToPerson().getId())
                ? request.getFromPerson() : request.getToPerson();
        notificationService.notifyCirculationUpdate(request, other, notification);
    }

    private void recordEvent(CirculationRequest request, CirculationEventType type, Person actor, String notes) {
        CirculationRequestEvent event = new CirculationRequestEvent();
        event.setCompany(request.getCompany());
        event.setCirculationRequest(request);
        event.setEventType(type);
        event.setActor(actor);
        event.setNotes(notes);
        eventRepository.save(event);
    }

    private CirculationRequest load(UUID id) {
        return requestRepository.findScoped(id, TenantContext.getRequired())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Circulation request not found"));
    }

    private Person requireStaffActor() {
        Person actor = currentUserService.requireCurrentPerson();
        if (!isStaff(actor)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Staff access required");
        return actor;
    }

    private Person requireStaffPerson(UUID id) {
        Person person = personRepository.findScopedById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Person not found"));
        if (!isStaff(person)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Circulation is limited to staff");
        return person;
    }

    private Person requireApprover() {
        Person actor = requireStaffActor();
        if (!isPrivileged(actor)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Approval requires admin or senior lawyer");
        return actor;
    }

    private static boolean isStaff(Person p) {
        return p.getSystemRole() != DomainEnums.SystemRole.CLIENT
                && p.getSystemRole() != DomainEnums.SystemRole.CLIENT_USER;
    }

    private static boolean isPrivileged(Person p) {
        return p.getSystemRole() == DomainEnums.SystemRole.ADMIN
                || p.getSystemRole() == DomainEnums.SystemRole.SENIOR_LAWYER;
    }

    private static void requireStatus(CirculationRequest request, CirculationStatus expected) {
        if (request.getStatus() != expected) throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Request must be " + expected + " to perform this action");
    }

    private void audit(String action, Person actor, CirculationRequest request) {
        auditLogService.recordSuccess(action, actor, "CirculationRequest", request.getId(),
                Map.of("caseId", request.getCaseFile().getId().toString(), "status", request.getStatus().name()));
    }

    private CirculationResponse toResponse(CirculationRequest r) {
        return new CirculationResponse(r.getId(), r.getCaseFile().getId(), r.getCaseFile().getCaseNumber(),
                r.getCaseFile().getTitle(), party(r.getFromPerson()), party(r.getToPerson()),
                party(r.getRequestedBy()), party(r.getApprovedBy()), r.getRequestedAt(), r.getDueBackAt(),
                r.getHandedAt(), r.getReturnedAt(), r.getStatus(), r.getReason(), r.getNotes(),
                eventRepository.findByRequest(r.getId(), TenantContext.getRequired()).stream().map(e -> new CirculationEventResponse(
                        e.getId(), e.getEventType(), e.getActor().getId(), e.getActor().getFullName(),
                        e.getOccurredAt(), e.getNotes())).toList());
    }

    private static com.kalima.backend.casefile.dto.PartySummary party(Person person) {
        return person == null ? null : new com.kalima.backend.casefile.dto.PartySummary(
                person.getId(), person.getFullName(), person.getSystemRole());
    }

    private static String trim(String value) {
        if (value == null) return null;
        String clean = value.trim();
        return clean.isEmpty() ? null : clean;
    }
}