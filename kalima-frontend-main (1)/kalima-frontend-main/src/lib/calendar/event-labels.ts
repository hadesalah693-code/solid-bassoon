import type {
  CalendarEventStatus,
  CalendarEventType,
  CalendarSourceModule,
  NativeCalendarEventType,
} from "@/types/calendar";

export const CALENDAR_EVENT_TYPE_AR: Record<CalendarEventType, string> = {
  HEARING: "جلسة",
  TASK: "مهمة",
  MEETING: "اجتماع",
  CALL: "مكالمة",
  REMINDER: "تذكير",
  DEADLINE: "موعد نهائي",
  CONTRACT_EXPIRY: "انتهاء عقد",
  INVOICE_DUE: "استحقاق فاتورة",
  DOCUMENT_EXPIRY: "انتهاء مستند",
  CUSTOM: "مخصص",
};

/** User-facing label; delegation expiry gets a dedicated Arabic label. */
export function calendarEventTypeLabel(event: {
  eventType: CalendarEventType;
  sourceModule?: CalendarSourceModule | null;
}): string {
  if (
    event.sourceModule === "DELEGATION"
    && event.eventType === "DOCUMENT_EXPIRY"
  ) {
    return "انتهاء توكيل";
  }
  return CALENDAR_EVENT_TYPE_AR[event.eventType];
}

export const NATIVE_EVENT_TYPES: NativeCalendarEventType[] = [
  "MEETING",
  "CALL",
  "REMINDER",
  "CUSTOM",
];

export const CALENDAR_STATUS_AR: Record<CalendarEventStatus, string> = {
  SCHEDULED: "مجدول",
  COMPLETED: "مكتمل",
  CANCELLED: "ملغى",
};

export const REMINDER_OFFSET_OPTIONS: { minutes: number; label: string }[] = [
  { minutes: 15, label: "15 دقيقة" },
  { minutes: 30, label: "30 دقيقة" },
  { minutes: 60, label: "ساعة" },
  { minutes: 1440, label: "يوم" },
  { minutes: 10080, label: "أسبوع" },
];
