package com.kalima.backend.calendar.service;

import com.kalima.backend.calendar.entity.CalendarEvent;
import com.kalima.backend.casefile.entity.CaseFile;
import com.kalima.backend.casefile.service.CaseAccessService;
import com.kalima.backend.domain.entity.DomainEnums;
import com.kalima.backend.domain.entity.Person;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class CalendarAccessService {

    private final CaseAccessService caseAccessService;

    public CalendarAccessService(CaseAccessService caseAccessService) {
        this.caseAccessService = caseAccessService;
    }

    public boolean canView(Person actor, CalendarEvent event) {
        DomainEnums.SystemRole role = actor.getSystemRole();
        if (role == DomainEnums.SystemRole.ADMIN
                || role == DomainEnums.SystemRole.SENIOR_LAWYER) {
            return true;
        }
        if (event.getAssignedTo() != null
                && event.getAssignedTo().getId().equals(actor.getId())) {
            return true;
        }
        if (event.getCreatedBy().getId().equals(actor.getId())) {
            return true;
        }
        CaseFile caseFile = event.getCaseFile();
        if (caseFile != null) {
            try {
                caseAccessService.requireReadableCase(caseFile, actor);
                return true;
            } catch (ResponseStatusException ex) {
                return false;
            }
        }
        if (event.getDispute() != null) {
            return role == DomainEnums.SystemRole.ADMIN
                    || role == DomainEnums.SystemRole.SENIOR_LAWYER;
        }
        if (event.getSourceModule() == DomainEnums.CalendarSourceModule.DELEGATION) {
            return role == DomainEnums.SystemRole.ADMIN
                    || role == DomainEnums.SystemRole.SENIOR_LAWYER
                    || role == DomainEnums.SystemRole.LAWYER
                    || role == DomainEnums.SystemRole.SECRETARY
                    || role == DomainEnums.SystemRole.ACCOUNTANT;
        }
        // Creator/assignee already handled above; deny everyone else.
        return false;
    }

    public CalendarEvent requireViewable(CalendarEvent event, Person actor) {
        if (!canView(actor, event)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found");
        }
        return event;
    }

    public void requireNativeWritable(CalendarEvent event) {
        if (event.isProjection()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Projected events must be edited in their source module.");
        }
    }

    public boolean canWriteNative(Person actor, CalendarEvent event) {
        DomainEnums.SystemRole role = actor.getSystemRole();
        if (role == DomainEnums.SystemRole.ACCOUNTANT
                || role == DomainEnums.SystemRole.CLIENT) {
            return false;
        }
        if (role == DomainEnums.SystemRole.ADMIN
                || role == DomainEnums.SystemRole.SENIOR_LAWYER) {
            return true;
        }
        if (event.getCreatedBy().getId().equals(actor.getId())) {
            return true;
        }
        return event.getAssignedTo() != null
                && event.getAssignedTo().getId().equals(actor.getId());
    }

    public void requireNativeEditable(CalendarEvent event, Person actor) {
        requireNativeWritable(event);
        if (!canWriteNative(actor, event)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Not allowed to edit this calendar event.");
        }
    }
}
