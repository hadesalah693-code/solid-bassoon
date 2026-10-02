import type { NotificationDto } from "@/lib/api/notifications";
import type { AppNotification, NotificationKind } from "@/types/notification";

const TYPE_LABEL_AR: Record<NotificationDto["type"], string> = {
  HEARING_ADDED: "جلسة — جديدة",
  HEARING_UPDATED: "جلسة — تحديث",
  CASE_STATUS_CHANGED: "حالة القضية",
  NEW_MESSAGE: "رسالة",
  TASK_ASSIGNED: "مهمة — تعيين",
  TASK_DUE_SOON: "مهمة — غداً",
  TASK_OVERDUE: "مهمة — متأخرة",
  CALENDAR_REMINDER: "تقويم — تذكير",
  CALENDAR_EVENT_ASSIGNED: "تقويم — تعيين",
  DELEGATION_EXPIRING: "توكيل — ينتهي قريباً",
  DELEGATION_CANCELLED: "توكيل — أُلغي",
  DELEGATION_MENTION: "توكيل — إشارة",
  DELEGATION_REMINDER_ADDED: "توكيل — تذكيرات",
  CIRCULATION_REQUEST: "تداول ملف قضية",
};

function typeToKind(type: NotificationDto["type"]): NotificationKind {
  switch (type) {
    case "HEARING_ADDED":
    case "HEARING_UPDATED":
      return "HEARING";
    case "CASE_STATUS_CHANGED":
      return "CASE_UPDATE";
    case "TASK_ASSIGNED":
    case "TASK_DUE_SOON":
    case "TASK_OVERDUE":
      return "TASK";
    case "CALENDAR_REMINDER":
      return "HEARING";
    case "CALENDAR_EVENT_ASSIGNED":
      return "HEARING";
    case "DELEGATION_EXPIRING":
    case "DELEGATION_CANCELLED":
    case "DELEGATION_MENTION":
    case "DELEGATION_REMINDER_ADDED":
    case "CIRCULATION_REQUEST":
      return "SYSTEM";
    case "NEW_MESSAGE":
    default:
      return "SYSTEM";
  }
}

export function notificationToApp(
  n: NotificationDto,
  options?: { casesBasePath?: string },
): AppNotification {
  const base = options?.casesBasePath ?? "/cases";
  return {
    id: n.id,
    kind: typeToKind(n.type),
    typeCode: n.type,
    typeLabel: TYPE_LABEL_AR[n.type],
    title: n.title,
    body: n.body,
    read: n.read,
    createdAt: n.createdAt,
    href: n.type === "CIRCULATION_REQUEST"
      ? n.caseId ? `${base}/${n.caseId}?section=circulation` : "/requests"
      : n.caseId
      ? `${base}/${n.caseId}`
      : n.type === "TASK_ASSIGNED" ||
          n.type === "TASK_DUE_SOON" ||
          n.type === "TASK_OVERDUE"
        ? "/tasks"
        : n.type === "CALENDAR_REMINDER" ||
            n.type === "CALENDAR_EVENT_ASSIGNED"
          ? "/calendar"
          : n.type === "DELEGATION_EXPIRING"
            || n.type === "DELEGATION_CANCELLED"
            || n.type === "DELEGATION_MENTION"
            || n.type === "DELEGATION_REMINDER_ADDED"
            ? "/delegations"
            : undefined,
  };
}
