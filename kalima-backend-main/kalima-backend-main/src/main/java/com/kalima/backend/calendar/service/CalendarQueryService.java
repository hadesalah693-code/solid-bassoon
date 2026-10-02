package com.kalima.backend.calendar.service;

import com.kalima.backend.auth.support.CurrentUserService;
import com.kalima.backend.calendar.dto.CalendarAgendaDayResponse;
import com.kalima.backend.calendar.dto.CalendarEventResponse;
import com.kalima.backend.calendar.dto.CalendarSummaryResponse;
import com.kalima.backend.calendar.entity.CalendarEvent;
import com.kalima.backend.calendar.repository.CalendarEventRepository;
import com.kalima.backend.domain.entity.DomainEnums;
import com.kalima.backend.domain.entity.Person;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class CalendarQueryService {

    /** Upper bound for in-memory RBAC filtering per range query. */
    static final int MAX_EVENTS_PER_RANGE = 2000;

    private final CalendarEventRepository calendarEventRepository;
    private final CalendarAccessService calendarAccessService;
    private final CalendarResponseBuilder responseBuilder;
    private final CurrentUserService currentUserService;

    public CalendarQueryService(
            CalendarEventRepository calendarEventRepository,
            CalendarAccessService calendarAccessService,
            CalendarResponseBuilder responseBuilder,
            CurrentUserService currentUserService) {
        this.calendarEventRepository = calendarEventRepository;
        this.calendarAccessService = calendarAccessService;
        this.responseBuilder = responseBuilder;
        this.currentUserService = currentUserService;
    }

    public Page<CalendarEventResponse> search(
            LocalDateTime from,
            LocalDateTime to,
            DomainEnums.CalendarEventType eventType,
            DomainEnums.CalendarEventStatus status,
            UUID caseId,
            UUID clientId,
            UUID assignedToId,
            DomainEnums.TaskPriority priority,
            String q,
            Pageable pageable) {
        List<CalendarEventResponse> all = listAccessibleBetween(
                from, to, eventType, status, caseId, clientId, assignedToId, priority, q);
        return paginate(all, pageable);
    }

    public List<CalendarEventResponse> today() {
        LocalDate today = LocalDate.now();
        return listAccessibleBetween(
                today.atStartOfDay(),
                today.plusDays(1).atStartOfDay(),
                null, null, null, null, null, null, null);
    }

    public List<CalendarEventResponse> upcoming(int days) {
        LocalDate today = LocalDate.now();
        int span = Math.max(1, Math.min(days, 90));
        return listAccessibleBetween(
                today.atStartOfDay(),
                today.plusDays(span).atStartOfDay(),
                null, null, null, null, null, null, null);
    }

    public List<CalendarAgendaDayResponse> agenda(
            LocalDate from,
            LocalDate to,
            DomainEnums.CalendarEventType eventType,
            DomainEnums.CalendarEventStatus status,
            UUID caseId,
            UUID clientId,
            UUID assignedToId,
            DomainEnums.TaskPriority priority,
            String q) {
        LocalDate start = from != null ? from : LocalDate.now();
        LocalDate end = to != null ? to : start.plusDays(7);
        if (end.isBefore(start)) {
            end = start;
        }
        List<CalendarEventResponse> events = listAccessibleBetween(
                start.atStartOfDay(),
                end.plusDays(1).atStartOfDay(),
                eventType, status, caseId, clientId, assignedToId, priority, q);
        Map<LocalDate, List<CalendarEventResponse>> grouped = events.stream()
                .collect(Collectors.groupingBy(
                        e -> e.startAt().toLocalDate(),
                        LinkedHashMap::new,
                        Collectors.toList()));
        List<CalendarAgendaDayResponse> days = new ArrayList<>();
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            days.add(new CalendarAgendaDayResponse(d, grouped.getOrDefault(d, List.of())));
        }
        return days;
    }

    public List<CalendarEventResponse> forCase(UUID caseId) {
        LocalDate start = LocalDate.now().minusYears(1);
        LocalDate end = LocalDate.now().plusYears(2);
        return listAccessibleBetween(
                start.atStartOfDay(),
                end.plusDays(1).atStartOfDay(),
                null, null, caseId, null, null, null, null);
    }

    public List<CalendarEventResponse> forClient(UUID clientId) {
        LocalDate start = LocalDate.now().minusYears(1);
        LocalDate end = LocalDate.now().plusYears(2);
        return listAccessibleBetween(
                start.atStartOfDay(),
                end.plusDays(1).atStartOfDay(),
                null, null, null, clientId, null, null, null);
    }

    public List<CalendarEventResponse> overdue() {
        Person actor = currentUserService.requireCurrentPerson();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime start = now.minusYears(1);
        Page<CalendarEvent> page = calendarEventRepository.searchInRange(
                start, now,
                null,
                DomainEnums.CalendarEventStatus.SCHEDULED,
                null, null, null, null, null,
                Pageable.ofSize(MAX_EVENTS_PER_RANGE));
        return page.getContent().stream()
                .filter(e -> calendarAccessService.canView(actor, e))
                .filter(e -> e.getStartAt().isBefore(now))
                .filter(e -> e.getEventType() == DomainEnums.CalendarEventType.TASK
                        || e.getEventType() == DomainEnums.CalendarEventType.DEADLINE)
                .map(responseBuilder::toResponse)
                .toList();
    }

    public CalendarSummaryResponse summary() {
        LocalDate today = LocalDate.now();
        LocalDateTime dayStart = today.atStartOfDay();
        LocalDateTime dayEnd = today.plusDays(1).atStartOfDay();
        LocalDateTime weekEnd = today.plusDays(7).atStartOfDay();

        List<CalendarEventResponse> weekScheduled = listAccessibleBetween(
                dayStart, weekEnd,
                null,
                DomainEnums.CalendarEventStatus.SCHEDULED,
                null, null, null, null, null);

        long todayCount = weekScheduled.stream()
                .filter(e -> !e.startAt().isBefore(dayStart) && e.startAt().isBefore(dayEnd))
                .count();
        long upcomingWeek = weekScheduled.size();
        long overdue = overdue().size();
        long hearingsWeek = weekScheduled.stream()
                .filter(e -> e.eventType() == DomainEnums.CalendarEventType.HEARING)
                .count();
        long tasksWeek = weekScheduled.stream()
                .filter(e -> e.eventType() == DomainEnums.CalendarEventType.TASK)
                .count();
        long meetingsWeek = weekScheduled.stream()
                .filter(e -> e.eventType() == DomainEnums.CalendarEventType.MEETING
                        || e.eventType() == DomainEnums.CalendarEventType.CALL)
                .count();

        return new CalendarSummaryResponse(
                todayCount, upcomingWeek, overdue, hearingsWeek, tasksWeek, meetingsWeek);
    }

    public CalendarEventResponse getById(UUID id) {
        Person actor = currentUserService.requireCurrentPerson();
        CalendarEvent event = calendarEventRepository.findScopedById(id)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND, "Event not found"));
        calendarAccessService.requireViewable(event, actor);
        return responseBuilder.toResponse(event);
    }

    private List<CalendarEventResponse> listAccessibleBetween(
            LocalDateTime from,
            LocalDateTime to,
            DomainEnums.CalendarEventType eventType,
            DomainEnums.CalendarEventStatus status,
            UUID caseId,
            UUID clientId,
            UUID assignedToId,
            DomainEnums.TaskPriority priority,
            String q) {
        Person actor = currentUserService.requireCurrentPerson();
        Page<CalendarEvent> page = calendarEventRepository.searchInRange(
                from, to, eventType, status, caseId, clientId, assignedToId, priority, q,
                Pageable.ofSize(MAX_EVENTS_PER_RANGE));
        return page.getContent().stream()
                .filter(e -> calendarAccessService.canView(actor, e))
                .map(responseBuilder::toResponse)
                .toList();
    }

    private static Page<CalendarEventResponse> paginate(
            List<CalendarEventResponse> all,
            Pageable pageable) {
        int start = (int) pageable.getOffset();
        if (start >= all.size()) {
            return new PageImpl<>(List.of(), pageable, all.size());
        }
        int end = Math.min(start + pageable.getPageSize(), all.size());
        return new PageImpl<>(all.subList(start, end), pageable, all.size());
    }
}
