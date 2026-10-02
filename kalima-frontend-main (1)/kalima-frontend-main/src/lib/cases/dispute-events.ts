import type {
  DisputeEventResponseDto,
  DisputeEventStatusDto,
  DisputeEventTypeDto,
} from "@/types/api";

/** Mirror UI copy used in مسار النزاع — reuse across dashboard and case panels. */
export const DISPUTE_EVENT_TYPE_AR: Record<DisputeEventTypeDto, string> = {
  CASE_OPENED: "فتح الملف",
  WARNING_NOTICE: "إنذار / إخطار",
  CLAIM_FILED: "تقديم الدعوى",
  FIRST_HEARING: "أول جلسة",
  MEMO_EXCHANGE: "تبادل المذكرات",
  JUDGMENT: "حكم",
  APPEAL: "استئناف",
  OTHER: "أخرى",
};

export const DISPUTE_EVENT_STATUS_AR: Record<DisputeEventStatusDto, string> = {
  DONE: "مكتملة",
  CURRENT: "جارية",
  UPCOMING: "قادمة",
  CANCELLED: "ملغاة",
};

/** Matches {@link DisputeEventsPanel} ordering: sortOrder, then event dates, then createdAt. */
export function compareDisputeEvents(
  a: DisputeEventResponseDto,
  b: DisputeEventResponseDto,
): number {
  if (a.sortOrder !== b.sortOrder) {
    return a.sortOrder - b.sortOrder;
  }
  const aT = a.eventDate ? new Date(a.eventDate).getTime() : null;
  const bT = b.eventDate ? new Date(b.eventDate).getTime() : null;
  if (aT !== null && bT !== null) return aT - bT;
  if (aT !== null && bT === null) return -1;
  if (aT === null && bT !== null) return 1;
  return new Date(a.createdAt).getTime() - new Date(b.createdAt).getTime();
}

export function disputeCurrentStage(
  eventsSorted: DisputeEventResponseDto[],
): DisputeEventResponseDto | null {
  const cur = eventsSorted.find((e) => e.status === "CURRENT");
  return cur ?? null;
}
