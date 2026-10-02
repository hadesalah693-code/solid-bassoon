import { format, formatDistanceToNow, parseISO } from "date-fns";
import { ar } from "date-fns/locale";

export function formatDate(value: string | Date, pattern = "dd MMM yyyy") {
  const d = typeof value === "string" ? parseISO(value) : value;
  return format(d, pattern, { locale: ar });
}

export function formatDateTime(value: string | Date) {
  const d = typeof value === "string" ? parseISO(value) : value;
  return format(d, "dd MMM yyyy · HH:mm", { locale: ar });
}

export function formatRelative(value: string | Date) {
  const d = typeof value === "string" ? parseISO(value) : value;
  return formatDistanceToNow(d, { addSuffix: true, locale: ar });
}

export function formatTime(value: string | Date) {
  const d = typeof value === "string" ? parseISO(value) : value;
  return format(d, "HH:mm", { locale: ar });
}
