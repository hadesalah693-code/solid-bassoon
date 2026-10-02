import type { DelegationStatusDto } from "@/types/delegation";

function arUnit(n: number, one: string, two: string, few: string, many: string) {
  if (n === 1) return one;
  if (n === 2) return two;
  if (n >= 3 && n <= 10) return few;
  return many;
}

/** Approximate calendar duration from day count (for display only). */
export function formatDurationFromDays(totalDays: number): string {
  const days = Math.max(0, Math.floor(totalDays));
  const years = Math.floor(days / 365);
  const months = Math.floor((days % 365) / 30);
  const remainder = days % 30;

  const parts: string[] = [];
  if (years > 0) {
    parts.push(`${years} ${arUnit(years, "سنة", "سنتان", "سنوات", "سنة")}`);
  }
  if (months > 0) {
    parts.push(`${months} ${arUnit(months, "شهر", "شهران", "أشهر", "شهر")}`);
  }
  if (remainder > 0 && years === 0) {
    parts.push(`${remainder} ${arUnit(remainder, "يوم", "يومان", "أيام", "يوم")}`);
  }
  if (parts.length === 0) return "أقل من يوم";
  return parts.join(" و ");
}

export function formatDaysRemainingArabic(
  daysRemaining: number | null,
  status: DelegationStatusDto,
): string {
  if (status === "CANCELLED") return "—";
  if (daysRemaining == null) return "—";
  if (daysRemaining < 0) {
    return `منتهي منذ ${formatDurationFromDays(Math.abs(daysRemaining))}`;
  }
  return formatDurationFromDays(daysRemaining);
}

export function formatTotalCountAr(count: number, singular: string, plural: string) {
  return count === 1 ? `1 ${singular}` : `${count.toLocaleString("ar-QA")} ${plural}`;
}
