import type {
  DisputeStageActionTypeDto,
} from "@/types/dispute";

/** One suggested procedure row for the stage workspace (frontend-only). */
export type StageProcedureSuggestion = {
  title: string;
  actionType: DisputeStageActionTypeDto;
};

const NEGOTIATION: StageProcedureSuggestion[] = [
  { title: "تحديد موعد تفاوض", actionType: "MEETING" },
  { title: "إرسال عرض تسوية", actionType: "FOLLOW_UP" },
  { title: "متابعة رد الطرف الآخر", actionType: "FOLLOW_UP" },
  { title: "توثيق نتيجة التفاوض", actionType: "DOCUMENT" },
];

const LEGAL_NOTICE: StageProcedureSuggestion[] = [
  { title: "صياغة الإنذار القانوني", actionType: "DRAFT" },
  { title: "مراجعة الإنذار", actionType: "REVIEW" },
  { title: "إرسال الإنذار للطرف الآخر", actionType: "SEND_NOTICE" },
  { title: "إرفاق نسخة الإنذار", actionType: "DOCUMENT" },
  { title: "متابعة الرد على الإنذار", actionType: "FOLLOW_UP" },
];

const MEDIATION: StageProcedureSuggestion[] = [
  { title: "تحديد جلسة وساطة", actionType: "MEETING" },
  { title: "تجهيز ملخص النزاع", actionType: "DOCUMENT" },
  { title: "تسجيل مقترح التسوية", actionType: "DOCUMENT" },
  { title: "متابعة قبول التسوية", actionType: "FOLLOW_UP" },
];

const COURT_FILING: StageProcedureSuggestion[] = [
  { title: "تجهيز صحيفة الدعوى", actionType: "DRAFT" },
  { title: "مراجعة المستندات", actionType: "REVIEW" },
  { title: "سداد الرسوم", actionType: "PAYMENT" },
  { title: "تسجيل رقم القضية", actionType: "DOCUMENT" },
  { title: "ربط القضية بالنزاع", actionType: "FOLLOW_UP" },
];

const COURT_CASE: StageProcedureSuggestion[] = [
  { title: "مراجعة ملف القضية", actionType: "REVIEW" },
  { title: "تجهيز مذكرة دفاع", actionType: "DRAFT" },
  { title: "متابعة موعد الجلسة", actionType: "FOLLOW_UP" },
  { title: "تحديث القضايا المرتبطة", actionType: "DOCUMENT" },
];

const HEARINGS: StageProcedureSuggestion[] = [
  { title: "تجهيز الحضور للجلسة", actionType: "COURT_PREPARATION" },
  { title: "مراجعة محضر الجلسة", actionType: "REVIEW" },
  { title: "تسجيل قرار الجلسة", actionType: "DOCUMENT" },
  { title: "متابعة الجلسة القادمة", actionType: "FOLLOW_UP" },
];

const JUDGMENT: StageProcedureSuggestion[] = [
  { title: "مراجعة الحكم", actionType: "REVIEW" },
  { title: "تقييم فرص الطعن", actionType: "REVIEW" },
  { title: "إخطار العميل بالنتيجة", actionType: "CALL" },
  { title: "تحديد خطوة التنفيذ أو الطعن", actionType: "FOLLOW_UP" },
];

const EXECUTION: StageProcedureSuggestion[] = [
  { title: "تجهيز طلب التنفيذ", actionType: "DRAFT" },
  { title: "متابعة إجراءات التنفيذ", actionType: "FOLLOW_UP" },
  { title: "تسجيل نتيجة التنفيذ", actionType: "DOCUMENT" },
];

const FRIENDLY_SETTLEMENT: StageProcedureSuggestion[] = [
  { title: "إعداد مقترح التسوية", actionType: "DRAFT" },
  { title: "التواصل مع الطرف الآخر", actionType: "CALL" },
  { title: "توثيق بنود الاتفاق", actionType: "DOCUMENT" },
  { title: "متابعة توقيع الاتفاق", actionType: "FOLLOW_UP" },
];

const ARBITRATION: StageProcedureSuggestion[] = [
  { title: "مراجعة شرط التحكيم", actionType: "REVIEW" },
  { title: "تجهيز طلب التحكيم", actionType: "DRAFT" },
  { title: "تجهيز ملف المستندات", actionType: "DOCUMENT" },
  { title: "متابعة جلسات التحكيم", actionType: "FOLLOW_UP" },
];

const ADMINISTRATIVE_ACTION: StageProcedureSuggestion[] = [
  { title: "تحديد الجهة الإدارية المختصة", actionType: "REVIEW" },
  { title: "تجهيز الطلب الإداري", actionType: "DRAFT" },
  { title: "إرفاق المستندات المطلوبة", actionType: "DOCUMENT" },
  { title: "متابعة الرد الإداري", actionType: "FOLLOW_UP" },
];

const CRIMINAL_REPORT: StageProcedureSuggestion[] = [
  { title: "تجهيز البلاغ", actionType: "DRAFT" },
  { title: "مراجعة المستندات المؤيدة", actionType: "REVIEW" },
  { title: "تقديم البلاغ للجهة المختصة", actionType: "DOCUMENT" },
  { title: "متابعة رقم البلاغ", actionType: "FOLLOW_UP" },
];

const OTHER: StageProcedureSuggestion[] = [
  { title: "إضافة متابعة عامة", actionType: "FOLLOW_UP" },
  { title: "توثيق ملاحظة", actionType: "DOCUMENT" },
];

const BY_TYPE: Record<string, StageProcedureSuggestion[]> = {
  NEGOTIATION,
  LEGAL_NOTICE,
  MEDIATION,
  COURT_FILING,
  COURT_CASE,
  HEARINGS,
  JUDGMENT,
  EXECUTION,
  FRIENDLY_SETTLEMENT,
  ARBITRATION,
  ADMINISTRATIVE_ACTION,
  CRIMINAL_REPORT,
  OTHER,
};

export function stageProcedureSuggestions(
  stageType: string,
): StageProcedureSuggestion[] {
  const key = stageType.trim().toUpperCase();
  return BY_TYPE[key] ?? OTHER;
}
