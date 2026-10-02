import type { InvoiceStatusDto } from "@/types/invoice";

export const INVOICE_STATUS_AR: Record<InvoiceStatusDto, string> = {
  DRAFT: "مسودة",
  ISSUED: "صادرة",
  PARTIALLY_PAID: "مدفوعة جزئياً",
  PAID: "مدفوعة",
  OVERDUE: "متأخرة",
  CANCELLED: "ملغاة",
};

export const INVOICE_STATUS_TONE: Record<
  InvoiceStatusDto,
  "neutral" | "info" | "warning" | "success" | "danger"
> = {
  DRAFT: "neutral",
  ISSUED: "info",
  PARTIALLY_PAID: "warning",
  PAID: "success",
  OVERDUE: "danger",
  CANCELLED: "neutral",
};

export const INVOICE_STATUS_OPTIONS = (
  Object.entries(INVOICE_STATUS_AR) as [InvoiceStatusDto, string][]
).map(([value, label]) => ({ value, label }));

/** Statuses a user is allowed to move an invoice to from `from`. */
export const INVOICE_STATUS_TRANSITIONS: Record<InvoiceStatusDto, InvoiceStatusDto[]> = {
  DRAFT: ["ISSUED", "CANCELLED"],
  ISSUED: ["PARTIALLY_PAID", "PAID", "OVERDUE", "CANCELLED"],
  PARTIALLY_PAID: ["PAID", "OVERDUE", "CANCELLED"],
  PAID: [],
  OVERDUE: ["PARTIALLY_PAID", "PAID", "CANCELLED"],
  CANCELLED: [],
};

export const INVOICE_STATUS_ACTION_AR: Partial<Record<InvoiceStatusDto, string>> = {
  ISSUED: "إصدار",
  PARTIALLY_PAID: "تسجيل دفعة جزئية",
  PAID: "تعليم كمدفوعة",
  OVERDUE: "تعليم كمتأخرة",
  CANCELLED: "إلغاء",
};
