import type {
  OcrDocumentType,
  OcrProvider,
  OcrRelationType,
  OcrStatus,
} from "@/types/ocr";

export const OCR_STATUS_AR: Record<OcrStatus, string> = {
  PENDING: "بانتظار المعالجة",
  PROCESSING: "قيد المعالجة",
  COMPLETED: "مكتمل",
  FAILED: "فشل",
  CANCELLED: "ملغى",
};

export const OCR_DOCUMENT_TYPE_AR: Record<OcrDocumentType, string> = {
  SCAN: "مسح ضوئي",
  CONTRACT: "عقد",
  COURT_FILING: "صحيفة/ملف محكمة",
  ID: "هوية",
  OTHER: "أخرى",
};

export const OCR_RELATION_TYPE_AR: Record<OcrRelationType, string> = {
  CASE: "قضية",
  DISPUTE: "نزاع",
  CLIENT: "عميل",
  TASK: "مهمة",
  DOCUMENT: "مستند",
  DELEGATION: "توكيل",
};

export const OCR_PROVIDER_AR: Record<OcrProvider, string> = {
  AZURE_DI: "Azure Document Intelligence",
  TIKA: "Apache Tika",
  MANUAL: "يدوي",
};

export function ocrStatusLabel(status: OcrStatus): string {
  return OCR_STATUS_AR[status] ?? status;
}

export function ocrDocumentTypeLabel(type: OcrDocumentType): string {
  return OCR_DOCUMENT_TYPE_AR[type] ?? type;
}

export function ocrRelationTypeLabel(type: OcrRelationType): string {
  return OCR_RELATION_TYPE_AR[type] ?? type;
}

export function ocrProviderLabel(provider: OcrProvider | null): string {
  if (!provider) return "—";
  return OCR_PROVIDER_AR[provider] ?? provider;
}

export function formatConfidence(score: number | null): string {
  if (score == null || Number.isNaN(score)) return "—";
  return `${Math.round(score * 100)}%`;
}

export function formatLanguage(code: string | null): string {
  if (!code) return "—";
  if (code === "ar") return "العربية";
  if (code === "en") return "English";
  if (code === "ar-en") return "عربي / English";
  return code;
}
