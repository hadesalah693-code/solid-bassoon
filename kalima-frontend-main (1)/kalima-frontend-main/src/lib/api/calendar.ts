import { request } from "./client";
import type { PagedResponse } from "@/types/common";
import type {
  CalendarAgendaDayDto,
  CalendarEventCreateRequest,
  CalendarEventDto,
  CalendarEventPatchRequest,
  CalendarEventStatus,
  CalendarEventType,
  CalendarSummaryDto,
} from "@/types/calendar";
import type { TaskResponseDto } from "@/types/api";

export type CalendarEventsQuery = {
  from: string;
  to: string;
  eventType?: CalendarEventType;
  status?: CalendarEventStatus;
  caseId?: string;
  clientId?: string;
  assignedToId?: string;
  priority?: TaskResponseDto["priority"];
  q?: string;
  page?: number;
  size?: number;
};

export const calendarApi = {
  listEvents: (q: CalendarEventsQuery) =>
    request<PagedResponse<CalendarEventDto>>("/calendar/events", {
      query: {
        from: q.from,
        to: q.to,
        eventType: q.eventType,
        status: q.status,
        caseId: q.caseId,
        clientId: q.clientId,
        assignedToId: q.assignedToId,
        priority: q.priority,
        q: q.q,
        page: q.page ?? 0,
        size: q.size ?? 100,
      },
    }),

  getEvent: (eventId: string) =>
    request<CalendarEventDto>(`/calendar/events/${eventId}`),

  createEvent: (body: CalendarEventCreateRequest) =>
    request<CalendarEventDto>("/calendar/events", { method: "POST", body }),

  patchEvent: (eventId: string, body: CalendarEventPatchRequest) =>
    request<CalendarEventDto>(`/calendar/events/${eventId}`, {
      method: "PATCH",
      body,
    }),

  deleteEvent: (eventId: string) =>
    request<void>(`/calendar/events/${eventId}`, { method: "DELETE" }),

  today: () => request<CalendarEventDto[]>("/calendar/today"),

  upcoming: (days = 7) =>
    request<CalendarEventDto[]>("/calendar/upcoming", { query: { days } }),

  agenda: (q: Omit<CalendarEventsQuery, "from" | "to" | "page" | "size"> & {
    from?: string;
    to?: string;
  } = {}) =>
    request<CalendarAgendaDayDto[]>("/calendar/agenda", {
      query: {
        from: q.from,
        to: q.to,
        eventType: q.eventType,
        status: q.status,
        caseId: q.caseId,
        clientId: q.clientId,
        assignedToId: q.assignedToId,
        priority: q.priority,
        q: q.q,
      },
    }),

  overdue: () => request<CalendarEventDto[]>("/calendar/overdue"),

  summary: () => request<CalendarSummaryDto>("/calendar/summary"),

  forCase: (caseId: string) =>
    request<CalendarEventDto[]>(`/calendar/case/${caseId}`),

  forClient: (clientId: string) =>
    request<CalendarEventDto[]>(`/calendar/client/${clientId}`),
};
