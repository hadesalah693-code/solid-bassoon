import type {
  MandateDocumentTypeDto,
  MandateEventTypeDto,
  MandatePaymentStatusDto,
  MandatePriorityDto,
  MandateStatusDto,
} from "@/types/mandate";

export const MANDATE_STATUS_AR: Record<MandateStatusDto, string> = {
  PENDING: "قيد الانتظار",
  CONFIRMED: "مؤكدة",
  IN_PROGRESS: "قيد التنفيذ",
  COMPLETED: "مكتملة",
  CANCELLED: "ملغاة",
};

export const MANDATE_STATUS_TONE: Record<
  MandateStatusDto,
  "neutral" | "info" | "navy" | "success" | "danger"
> = {
  PENDING: "neutral",
  CONFIRMED: "info",
  IN_PROGRESS: "navy",
  COMPLETED: "success",
  CANCELLED: "danger",
};

export const MANDATE_PRIORITY_AR: Record<MandatePriorityDto, string> = {
  LOW: "منخفضة",
  MEDIUM: "متوسطة",
  HIGH: "عالية",
  URGENT: "عاجلة",
};

export const MANDATE_PRIORITY_TONE: Record<
  MandatePriorityDto,
  "neutral" | "info" | "warning" | "danger"
> = {
  LOW: "neutral",
  MEDIUM: "info",
  HIGH: "warning",
  URGENT: "danger",
};

export const MANDATE_PRIORITY_OPTIONS = (
  Object.entries(MANDATE_PRIORITY_AR) as [MandatePriorityDto, string][]
).map(([value, label]) => ({ value, label }));

export const MANDATE_PAYMENT_STATUS_AR: Record<MandatePaymentStatusDto, string> = {
  UNPAID: "غير مدفوع",
  PARTIALLY_PAID: "مدفوع جزئياً",
  PAID: "مدفوع بالكامل",
};

export const MANDATE_PAYMENT_STATUS_TONE: Record<
  MandatePaymentStatusDto,
  "neutral" | "warning" | "success"
> = {
  UNPAID: "neutral",
  PARTIALLY_PAID: "warning",
  PAID: "success",
};

export const MANDATE_DOCUMENT_TYPE_AR: Record<MandateDocumentTypeDto, string> = {
  POWER_OF_ATTORNEY: "التوكيل",
  SUBSTITUTION_REQUEST: "طلب الإنابة",
  ID_COPY: "نسخة الهوية",
  CASE_DOCUMENT: "مستند قضية",
  HEARING_MINUTES: "محضر جلسة",
  OTHER: "أخرى",
};

export const MANDATE_DOCUMENT_TYPE_OPTIONS = (
  Object.entries(MANDATE_DOCUMENT_TYPE_AR) as [MandateDocumentTypeDto, string][]
).map(([value, label]) => ({ value, label }));

export const MANDATE_EVENT_TYPE_AR: Record<MandateEventTypeDto, string> = {
  CREATED: "إنشاء الإنابة",
  STATUS_CHANGED: "تغيير الحالة",
  CONFIRMED: "تأكيد الإنابة",
  ATTENDED_SESSION: "حضور الجلسة",
  SESSION_RESULT_RECORDED: "تسجيل نتيجة الجلسة",
  PAYMENT_RECORDED: "تسجيل دفعة",
  CLOSED: "إغلاق الإنابة",
  CANCELLED: "إلغاء الإنابة",
  NOTE_ADDED: "إضافة ملاحظة",
};

export const CURRENCY_OPTIONS = [
  { value: "SAR", label: "ر.س" },
  { value: "QAR", label: "ر.ق" },
  { value: "AED", label: "د.إ" },
  { value: "KWD", label: "د.ك" },
  { value: "EGP", label: "ج.م" },
  { value: "USD", label: "$" },
];
