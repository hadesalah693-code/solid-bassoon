import type { TaskResponseDto } from "@/types/api";

export type CalendarEventType =
  | "HEARING"
  | "TASK"
  | "MEETING"
  | "CALL"
  | "REMINDER"
  | "DEADLINE"
  | "CONTRACT_EXPIRY"
  | "INVOICE_DUE"
  | "DOCUMENT_EXPIRY"
  | "CUSTOM";

export type CalendarEventStatus = "SCHEDULED" | "COMPLETED" | "CANCELLED";

export type CalendarSourceModule =
  | "HEARING"
  | "TASK"
  | "DISPUTE_ACTION"
  | "DISPUTE_EVENT"
  | "CONTRACT"
  | "INVOICE"
  | "DOCUMENT"
  | "DELEGATION";

/** Native event types creatable via calendar CRUD. */
export type NativeCalendarEventType = "MEETING" | "CALL" | "REMINDER" | "CUSTOM";

export type CalendarEventDto = {
  id: string;
  title: string;
  description: string | null;
  eventType: CalendarEventType;
  sourceModule: CalendarSourceModule | null;
  sourceId: string | null;
  caseId: string | null;
  caseNumber: string | null;
  caseTitle: string | null;
  clientId: string | null;
  clientName: string | null;
  taskId: string | null;
  disputeId: string | null;
  disputeNumber: string | null;
  startAt: string;
  endAt: string | null;
  allDay: boolean;
  status: CalendarEventStatus;
  priority: TaskResponseDto["priority"] | null;
  assignedToId: string | null;
  assignedToName: string | null;
  createdById: string;
  createdByName: string;
  colorKey: string;
  editable: boolean;
  sourceHref: string;
  reminderOffsetsMinutes: number[];
  createdAt: string;
  updatedAt: string;
};

export type CalendarAgendaDayDto = {
  date: string;
  events: CalendarEventDto[];
};

export type CalendarSummaryDto = {
  todayCount: number;
  upcomingWeekCount: number;
  overdueCount: number;
  hearingsThisWeekCount: number;
  tasksDueThisWeekCount: number;
  meetingsThisWeekCount: number;
};

export type CalendarEventCreateRequest = {
  title: string;
  description?: string;
  eventType: NativeCalendarEventType;
  caseId?: string | null;
  clientId?: string | null;
  disputeId?: string | null;
  startAt: string;
  endAt?: string | null;
  allDay?: boolean;
  priority?: TaskResponseDto["priority"];
  assignedToId?: string | null;
  reminderOffsetsMinutes?: number[];
};

export type CalendarEventPatchRequest = Partial<
  Omit<CalendarEventCreateRequest, "eventType">
> & {
  eventType?: NativeCalendarEventType;
  status?: CalendarEventStatus;
};

export type CalendarViewMode = "month" | "week" | "day" | "agenda";
