package com.kalima.backend.calendar.service;

import com.kalima.backend.audit.AuditAction;
import com.kalima.backend.audit.service.AuditLogService;
import com.kalima.backend.auth.support.CurrentUserService;
import com.kalima.backend.calendar.dto.CalendarEventCreateRequest;
import com.kalima.backend.calendar.dto.CalendarEventPatchRequest;
import com.kalima.backend.calendar.dto.CalendarEventResponse;
import com.kalima.backend.calendar.entity.CalendarEvent;
import com.kalima.backend.calendar.entity.CalendarEventReminder;
import com.kalima.backend.calendar.repository.CalendarEventReminderRepository;
import com.kalima.backend.calendar.repository.CalendarEventRepository;
import com.kalima.backend.casefile.entity.CaseFile;
import com.kalima.backend.casefile.repository.CaseFileRepository;
import com.kalima.backend.casefile.service.CaseAccessService;
import com.kalima.backend.dispute.entity.Dispute;
import com.kalima.backend.dispute.repository.DisputeRepository;
import com.kalima.backend.domain.entity.Company;
import com.kalima.backend.domain.entity.DomainEnums;
import com.kalima.backend.domain.entity.Person;
import com.kalima.backend.domain.repository.CompanyRepository;
import com.kalima.backend.domain.repository.PersonRepository;
import com.kalima.backend.domain.tenant.TenantContext;
import com.kalima.backend.notification.service.NotificationService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional
public class CalendarEventService {

    private static final Set<DomainEnums.CalendarEventType> NATIVE_TYPES = Set.of(
            DomainEnums.CalendarEventType.MEETING,
            DomainEnums.CalendarEventType.CALL,
            DomainEnums.CalendarEventType.REMINDER,
            DomainEnums.CalendarEventType.CUSTOM);

    private static final Set<Integer> ALLOWED_OFFSETS = Set.of(15, 30, 60, 1440, 10080);

    private final CalendarEventRepository calendarEventRepository;
    private final CalendarEventReminderRepository reminderRepository;
    private final CaseFileRepository caseFileRepository;
    private final DisputeRepository disputeRepository;
    private final PersonRepository personRepository;
    private final CompanyRepository companyRepository;
    private final CurrentUserService currentUserService;
    private final CaseAccessService caseAccessService;
    private final CalendarAccessService calendarAccessService;
    private final CalendarResponseBuilder responseBuilder;
    private final AuditLogService auditLogService;
    private final NotificationService notificationService;

    public CalendarEventService(
            CalendarEventRepository calendarEventRepository,
            CalendarEventReminderRepository reminderRepository,
            CaseFileRepository caseFileRepository,
            DisputeRepository disputeRepository,
            PersonRepository personRepository,
            CompanyRepository companyRepository,
            CurrentUserService currentUserService,
            CaseAccessService caseAccessService,
            CalendarAccessService calendarAccessService,
            CalendarResponseBuilder responseBuilder,
            AuditLogService auditLogService,
            NotificationService notificationService) {
        this.calendarEventRepository = calendarEventRepository;
        this.reminderRepository = reminderRepository;
        this.caseFileRepository = caseFileRepository;
        this.disputeRepository = disputeRepository;
        this.personRepository = personRepository;
        this.companyRepository = companyRepository;
        this.currentUserService = currentUserService;
        this.caseAccessService = caseAccessService;
        this.calendarAccessService = calendarAccessService;
        this.responseBuilder = responseBuilder;
        this.auditLogService = auditLogService;
        this.notificationService = notificationService;
    }

    public CalendarEventResponse create(CalendarEventCreateRequest request) {
        Person actor = currentUserService.requireCurrentPerson();
        ensureCanCreate(actor);
        if (!NATIVE_TYPES.contains(request.eventType())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Native calendar events must use MEETING, CALL, REMINDER, or CUSTOM.");
        }

        CalendarEvent event = new CalendarEvent();
        event.setCompany(currentTenantReference());
        event.setCreatedBy(actor);
        event.setEventType(request.eventType());
        event.setSourceModule(null);
        event.setSourceId(null);
        applyNativeFields(event, request.title(), request.description(), request.caseId(),
                request.clientId(), request.disputeId(), request.startAt(), request.endAt(),
                request.allDay(), request.priority(), request.assignedToId(), actor);
        event.setStatus(DomainEnums.CalendarEventStatus.SCHEDULED);

        CalendarEvent saved = calendarEventRepository.save(event);
        replaceReminders(saved, request.reminderOffsetsMinutes());
        notifyAssigneeIfNeeded(saved, actor, true, null);

        auditLogService.recordSuccess(
                AuditAction.CALENDAR_EVENT_CREATED,
                actor,
                "CalendarEvent", saved.getId(),
                Map.of("eventType", saved.getEventType().name()));
        return responseBuilder.toResponse(saved);
    }

    public CalendarEventResponse patch(UUID id, CalendarEventPatchRequest request) {
        Person actor = currentUserService.requireCurrentPerson();
        CalendarEvent event = load(id);
        calendarAccessService.requireNativeEditable(event, actor);
        UUID previousAssigneeId = event.getAssignedTo() != null
                ? event.getAssignedTo().getId() : null;

        if (request.title() != null) {
            event.setTitle(requireText(request.title()));
        }
        if (request.description() != null) {
            event.setDescription(trimToNull(request.description()));
        }
        if (request.eventType() != null) {
            if (!NATIVE_TYPES.contains(request.eventType())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Invalid native event type.");
            }
            event.setEventType(request.eventType());
        }
        if (request.caseId() != null || request.clientId() != null || request.disputeId() != null
                || request.startAt() != null || request.endAt() != null
                || request.allDay() != null || request.priority() != null
                || request.assignedToId() != null) {
            applyNativeFields(
                    event,
                    event.getTitle(),
                    event.getDescription(),
                    request.caseId() != null ? request.caseId()
                            : (event.getCaseFile() != null ? event.getCaseFile().getId() : null),
                    request.clientId() != null ? request.clientId()
                            : (event.getClient() != null ? event.getClient().getId() : null),
                    request.disputeId() != null ? request.disputeId()
                            : (event.getDispute() != null ? event.getDispute().getId() : null),
                    request.startAt() != null ? request.startAt() : event.getStartAt(),
                    request.endAt() != null ? request.endAt() : event.getEndAt(),
                    request.allDay() != null ? request.allDay() : event.isAllDay(),
                    request.priority() != null ? request.priority() : event.getPriority(),
                    request.assignedToId() != null ? request.assignedToId()
                            : (event.getAssignedTo() != null ? event.getAssignedTo().getId() : null),
                    actor);
        }
        if (request.status() != null) {
            event.setStatus(request.status());
        }
        if (request.reminderOffsetsMinutes() != null) {
            replaceReminders(event, request.reminderOffsetsMinutes());
        }

        notifyAssigneeIfNeeded(event, actor, false, previousAssigneeId);
        auditLogService.recordSuccess(
                AuditAction.CALENDAR_EVENT_UPDATED,
                actor,
                "CalendarEvent", event.getId(),
                Map.of("status", event.getStatus().name()));
        return responseBuilder.toResponse(event);
    }

    public void delete(UUID id) {
        Person actor = currentUserService.requireCurrentPerson();
        CalendarEvent event = load(id);
        calendarAccessService.requireNativeEditable(event, actor);
        reminderRepository.deleteByCalendarEvent_Id(id);
        calendarEventRepository.delete(event);
        auditLogService.recordSuccess(
                AuditAction.CALENDAR_EVENT_DELETED,
                actor,
                "CalendarEvent", id,
                Map.of("title", event.getTitle()));
    }

    private CalendarEvent load(UUID id) {
        CalendarEvent event = calendarEventRepository.findScopedById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Event not found"));
        calendarAccessService.requireViewable(event, currentUserService.requireCurrentPerson());
        return event;
    }

    private void applyNativeFields(
            CalendarEvent event,
            String title,
            String description,
            UUID caseId,
            UUID clientId,
            UUID disputeId,
            java.time.LocalDateTime startAt,
            java.time.LocalDateTime endAt,
            Boolean allDay,
            DomainEnums.TaskPriority priority,
            UUID assignedToId,
            Person actor) {
        event.setTitle(requireText(title));
        event.setDescription(trimToNull(description));
        event.setStartAt(startAt);
        event.setEndAt(endAt);
        event.setAllDay(Boolean.TRUE.equals(allDay));
        event.setPriority(priority);

        CaseFile caseFile = resolveCase(caseId, actor);
        Dispute dispute = resolveDispute(disputeId);
        event.setCaseFile(caseFile);
        event.setDispute(dispute);
        event.setTaskItem(null);
        event.setClient(resolveClient(clientId, caseFile));
        event.setAssignedTo(resolveAssignee(assignedToId));
    }

    private CaseFile resolveCase(UUID caseId, Person actor) {
        if (caseId == null) {
            return null;
        }
        return caseAccessService.requireReadableCase(caseId, actor);
    }

    private Dispute resolveDispute(UUID disputeId) {
        if (disputeId == null) {
            return null;
        }
        return disputeRepository.findScopedById(disputeId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Dispute not found: " + disputeId));
    }

    private Person resolveClient(UUID clientId, CaseFile caseFile) {
        if (clientId != null) {
            Person p = personRepository.findScopedById(clientId)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.BAD_REQUEST, "Client not found: " + clientId));
            if (p.getSystemRole() != DomainEnums.SystemRole.CLIENT) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "clientId must reference a CLIENT person.");
            }
            return p;
        }
        return caseFile != null ? caseFile.getClient() : null;
    }

    private Person resolveAssignee(UUID assignedToId) {
        if (assignedToId == null) {
            return null;
        }
        Person p = personRepository.findScopedById(assignedToId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Assignee not found: " + assignedToId));
        if (p.getSystemRole() == DomainEnums.SystemRole.CLIENT) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Cannot assign calendar event to a client.");
        }
        return p;
    }

    private void replaceReminders(CalendarEvent event, List<Integer> offsets) {
        reminderRepository.deleteByCalendarEvent_Id(event.getId());
        if (offsets == null) {
            return;
        }
        for (Integer offset : offsets) {
            if (offset == null || !ALLOWED_OFFSETS.contains(offset)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Invalid reminder offset: " + offset);
            }
            CalendarEventReminder r = new CalendarEventReminder();
            r.setCompany(event.getCompany());
            r.setCalendarEvent(event);
            r.setOffsetMinutes(offset);
            reminderRepository.save(r);
        }
    }

    private void notifyAssigneeIfNeeded(
            CalendarEvent event,
            Person actor,
            boolean created,
            UUID previousAssigneeId) {
        Person assignee = event.getAssignedTo();
        if (assignee == null || assignee.getId().equals(actor.getId())) {
            return;
        }
        if (created || previousAssigneeId == null
                || !previousAssigneeId.equals(assignee.getId())) {
            notificationService.notifyCalendarEventAssigned(event, assignee, actor);
        }
    }

    private void ensureCanCreate(Person actor) {
        DomainEnums.SystemRole role = actor.getSystemRole();
        if (role == DomainEnums.SystemRole.ACCOUNTANT
                || role == DomainEnums.SystemRole.CLIENT) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Not allowed to create calendar events.");
        }
    }

    private Company currentTenantReference() {
        return companyRepository.getReferenceById(TenantContext.getRequired());
    }

    private static String requireText(String value) {
        String t = trimToNull(value);
        if (t == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "title is required");
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
