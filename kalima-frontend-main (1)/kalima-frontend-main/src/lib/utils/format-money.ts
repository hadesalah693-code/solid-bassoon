/**
 * Formats a money amount using the Arabic locale. The backend serialises
 * `BigDecimal` as a JSON number, so values arrive as `number`.
 */
export function formatMoney(
  value: number | string | null | undefined,
  currency = "ر.س",
): string {
  const n = typeof value === "string" ? Number(value) : value;
  if (n == null || Number.isNaN(n)) return "—";
  const formatted = new Intl.NumberFormat("ar-EG", {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(n);
  return `${formatted} ${currency}`;
}

/** Percentage progress of a paid amount, clamped to 0..100. */
export function paidPercent(amountPaid: number, total: number): number {
  if (!total || total <= 0) return 0;
  return Math.max(0, Math.min(100, Math.round((amountPaid / total) * 100)));
}
