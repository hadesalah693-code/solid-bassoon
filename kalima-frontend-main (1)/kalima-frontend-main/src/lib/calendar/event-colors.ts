import type { CalendarEventDto } from "@/types/calendar";

export type EventColorStyle = {
  bg: string;
  border: string;
  text: string;
  badge: string;
};

const PALETTE: Record<string, EventColorStyle> = {
  hearing: {
    bg: "#fee2e2",
    border: "#dc2626",
    text: "#991b1b",
    badge: "bg-[var(--color-danger-bg)] text-[var(--color-danger)]",
  },
  meeting: {
    bg: "#dbeafe",
    border: "#2563eb",
    text: "#1e40af",
    badge: "bg-[var(--color-info-bg)] text-[var(--color-info)]",
  },
  task: {
    bg: "#fef9c3",
    border: "#ca8a04",
    text: "#854d0e",
    badge: "bg-[var(--color-warning-bg)] text-[var(--color-warning)]",
  },
  reminder: {
    bg: "#f3e8ff",
    border: "#9333ea",
    text: "#6b21a8",
    badge: "bg-purple-100 text-purple-700",
  },
  deadline: {
    bg: "#ffedd5",
    border: "#ea580c",
    text: "#9a3412",
    badge: "bg-orange-100 text-orange-700",
  },
  custom: {
    bg: "#e2e8f0",
    border: "#64748b",
    text: "#334155",
    badge: "bg-[var(--color-surface-muted)] text-[var(--color-muted-foreground)]",
  },
  delegation: {
    bg: "#ecfdf5",
    border: "#059669",
    text: "#065f46",
    badge: "bg-emerald-100 text-emerald-800",
  },
  neutral: {
    bg: "#f1f5f9",
    border: "#94a3b8",
    text: "#475569",
    badge: "bg-[var(--color-surface-muted)] text-[var(--color-muted-foreground)]",
  },
};

export function getEventColor(colorKey: string | null | undefined): EventColorStyle {
  if (!colorKey) return PALETTE.neutral;
  return PALETTE[colorKey] ?? PALETTE.neutral;
}

export function eventDotClass(event: CalendarEventDto): string {
  return getEventColor(event.colorKey).badge;
}
