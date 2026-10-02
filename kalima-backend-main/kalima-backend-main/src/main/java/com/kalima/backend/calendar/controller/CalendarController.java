package com.kalima.backend.calendar.controller;

import com.kalima.backend.calendar.dto.CalendarAgendaDayResponse;
import com.kalima.backend.calendar.dto.CalendarEventCreateRequest;
import com.kalima.backend.calendar.dto.CalendarEventPatchRequest;
import com.kalima.backend.calendar.dto.CalendarEventResponse;
import com.kalima.backend.calendar.dto.CalendarSummaryResponse;
import com.kalima.backend.calendar.service.CalendarEventService;
import com.kalima.backend.calendar.service.CalendarQueryService;
import com.kalima.backend.domain.entity.DomainEnums;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/calendar")
public class CalendarController {

    private final CalendarQueryService queryService;
    private final CalendarEventService eventService;

    public CalendarController(CalendarQueryService queryService,
                              CalendarEventService eventService) {
        this.queryService = queryService;
        this.eventService = eventService;
    }

    @GetMapping("/events")
    @PreAuthorize("hasAnyRole('ADMIN','SENIOR_LAWYER','LAWYER','SECRETARY','ACCOUNTANT')")
    public Page<CalendarEventResponse> listEvents(
            @RequestParam(name = "from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime from,
            @RequestParam(name = "to") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime to,
            @RequestParam(name = "eventType", required = false)
            DomainEnums.CalendarEventType eventType,
            @RequestParam(name = "status", required = false)
            DomainEnums.CalendarEventStatus status,
            @RequestParam(name = "caseId", required = false) UUID caseId,
            @RequestParam(name = "clientId", required = false) UUID clientId,
            @RequestParam(name = "assignedToId", required = false) UUID assignedToId,
            @RequestParam(name = "priority", required = false) DomainEnums.TaskPriority priority,
            @RequestParam(name = "q", required = false) String q,
            @PageableDefault(size = 100) Pageable pageable) {
        return queryService.search(
                from, to, eventType, status, caseId, clientId, assignedToId, priority, q, pageable);
    }

    @GetMapping("/events/{eventId}")
    @PreAuthorize("hasAnyRole('ADMIN','SENIOR_LAWYER','LAWYER','SECRETARY','ACCOUNTANT')")
    public CalendarEventResponse getEvent(@PathVariable UUID eventId) {
        return queryService.getById(eventId);
    }

    @PostMapping("/events")
    @PreAuthorize("hasAnyRole('ADMIN','SENIOR_LAWYER','LAWYER','SECRETARY')")
    public ResponseEntity<CalendarEventResponse> createEvent(
            @Valid @RequestBody CalendarEventCreateRequest request,
            UriComponentsBuilder uriBuilder) {
        CalendarEventResponse created = eventService.create(request);
        URI loc = uriBuilder.path("/api/calendar/events/{id}")
                .buildAndExpand(created.id())
                .toUri();
        return ResponseEntity.created(loc).body(created);
    }

    @PatchMapping("/events/{eventId}")
    @PreAuthorize("hasAnyRole('ADMIN','SENIOR_LAWYER','LAWYER','SECRETARY')")
    public CalendarEventResponse patchEvent(
            @PathVariable UUID eventId,
            @Valid @RequestBody CalendarEventPatchRequest request) {
        return eventService.patch(eventId, request);
    }

    @DeleteMapping("/events/{eventId}")
    @PreAuthorize("hasAnyRole('ADMIN','SENIOR_LAWYER','LAWYER','SECRETARY')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteEvent(@PathVariable UUID eventId) {
        eventService.delete(eventId);
    }

    @GetMapping("/today")
    @PreAuthorize("hasAnyRole('ADMIN','SENIOR_LAWYER','LAWYER','SECRETARY','ACCOUNTANT')")
    public List<CalendarEventResponse> today() {
        return queryService.today();
    }

    @GetMapping("/upcoming")
    @PreAuthorize("hasAnyRole('ADMIN','SENIOR_LAWYER','LAWYER','SECRETARY','ACCOUNTANT')")
    public List<CalendarEventResponse> upcoming(
            @RequestParam(name = "days", defaultValue = "7") int days) {
        return queryService.upcoming(days);
    }

    @GetMapping("/agenda")
    @PreAuthorize("hasAnyRole('ADMIN','SENIOR_LAWYER','LAWYER','SECRETARY','ACCOUNTANT')")
    public List<CalendarAgendaDayResponse> agenda(
            @RequestParam(name = "from", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(name = "to", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(name = "eventType", required = false)
            DomainEnums.CalendarEventType eventType,
            @RequestParam(name = "status", required = false)
            DomainEnums.CalendarEventStatus status,
            @RequestParam(name = "caseId", required = false) UUID caseId,
            @RequestParam(name = "clientId", required = false) UUID clientId,
            @RequestParam(name = "assignedToId", required = false) UUID assignedToId,
            @RequestParam(name = "priority", required = false) DomainEnums.TaskPriority priority,
            @RequestParam(name = "q", required = false) String q) {
        return queryService.agenda(
                from, to, eventType, status, caseId, clientId, assignedToId, priority, q);
    }

    @GetMapping("/overdue")
    @PreAuthorize("hasAnyRole('ADMIN','SENIOR_LAWYER','LAWYER','SECRETARY','ACCOUNTANT')")
    public List<CalendarEventResponse> overdue() {
        return queryService.overdue();
    }

    @GetMapping("/summary")
    @PreAuthorize("hasAnyRole('ADMIN','SENIOR_LAWYER','LAWYER','SECRETARY','ACCOUNTANT')")
    public CalendarSummaryResponse summary() {
        return queryService.summary();
    }

    @GetMapping("/case/{caseId}")
    @PreAuthorize("hasAnyRole('ADMIN','SENIOR_LAWYER','LAWYER','SECRETARY','ACCOUNTANT','CLIENT')")
    public List<CalendarEventResponse> forCase(@PathVariable UUID caseId) {
        return queryService.forCase(caseId);
    }

    @GetMapping("/client/{clientId}")
    @PreAuthorize("hasAnyRole('ADMIN','SENIOR_LAWYER','LAWYER','SECRETARY','ACCOUNTANT')")
    public List<CalendarEventResponse> forClient(@PathVariable UUID clientId) {
        return queryService.forClient(clientId);
    }
}
