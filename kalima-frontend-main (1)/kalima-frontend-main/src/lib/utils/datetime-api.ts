/**
 * Converts `datetime-local` value ("yyyy-MM-ddTHH:mm") to a string suitable
 * for Jackson {@code LocalDateTime} (seconds optional; we normalize).
 */
export function localInputToApiDateTime(value: string): string {
  const t = value?.trim();
  if (!t) {
    return "";
  }
  return t.length === 16 ? `${t}:00` : t;
}
