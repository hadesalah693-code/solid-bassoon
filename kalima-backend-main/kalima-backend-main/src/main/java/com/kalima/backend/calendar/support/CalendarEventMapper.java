package com.kalima.backend.calendar.support;

import com.kalima.backend.calendar.entity.CalendarEvent;
import com.kalima.backend.domain.entity.DomainEnums;

public final class CalendarEventMapper {

    private CalendarEventMapper() {
    }

    public static String colorKey(DomainEnums.CalendarEventType type) {
        if (type == null) {
            return "neutral";
        }
        return switch (type) {
            case HEARING -> "hearing";
            case MEETING, CALL -> "meeting";
            case TASK -> "task";
            case REMINDER -> "reminder";
            case DEADLINE, CONTRACT_EXPIRY, INVOICE_DUE, DOCUMENT_EXPIRY -> "deadline";
            case MANDATE -> "delegation";
            case CUSTOM -> "custom";
        };
    }

    public static String colorKey(CalendarEvent e) {
        if (e != null && e.getSourceModule() == DomainEnums.CalendarSourceModule.DELEGATION) {
            return "delegation";
        }
        return colorKey(e != null ? e.getEventType() : null);
    }

    public static String sourceHref(CalendarEvent e) {
        if (e.getSourceModule() == DomainEnums.CalendarSourceModule.DELEGATION
                && e.getSourceId() != null) {
            return "/delegations/" + e.getSourceId();
        }
        if (e.getCaseFile() != null) {
            String base = "/cases/" + e.getCaseFile().getId();
            if (e.getEventType() == DomainEnums.CalendarEventType.HEARING
                    || e.getSourceModule() == DomainEnums.CalendarSourceModule.HEARING) {
                return base + "?section=hearings";
            }
            if (e.getEventType() == DomainEnums.CalendarEventType.TASK
                    || e.getTaskItem() != null
                    || e.getSourceModule() == DomainEnums.CalendarSourceModule.TASK) {
                return base + "?section=tasks";
            }
            return base + "?section=calendar";
        }
        if (e.getDispute() != null) {
            return "/disputes/" + e.getDispute().getId();
        }
        if (e.getEventType() == DomainEnums.CalendarEventType.TASK
                || e.getTaskItem() != null) {
            return "/tasks";
        }
        return "/calendar";
    }
}
