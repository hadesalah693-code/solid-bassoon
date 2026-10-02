export type NotificationKind =
  | "HEARING"
  | "TASK"
  | "INVOICE"
  | "CASE_UPDATE"
  | "SYSTEM";

/** API `NotificationResponse.type` — keep in sync with `NotificationDto` in `lib/api/notifications`. */
export type AppNotificationTypeCode =
  | "HEARING_ADDED"
  | "HEARING_UPDATED"
  | "CASE_STATUS_CHANGED"
  | "NEW_MESSAGE"
  | "TASK_ASSIGNED"
  | "TASK_DUE_SOON"
  | "TASK_OVERDUE"
  | "CALENDAR_REMINDER"
  | "CALENDAR_EVENT_ASSIGNED"
  | "DELEGATION_EXPIRING"
  | "DELEGATION_CANCELLED"
  | "DELEGATION_MENTION"
  | "DELEGATION_REMINDER_ADDED"
  | "CIRCULATION_REQUEST";

export type AppNotification = {
  id: string;
  kind: NotificationKind;
  typeCode?: AppNotificationTypeCode;
  typeLabel?: string;
  title: string;
  body: string;
  createdAt: string;
  read: boolean;
  href?: string;
};
