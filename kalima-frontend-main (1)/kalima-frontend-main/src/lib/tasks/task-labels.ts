import type { AdminTaskTypeDto, TaskResponseDto } from "@/types/api";

export const TASK_STATUSES: TaskResponseDto["status"][] = [
  "TODO",
  "IN_PROGRESS",
  "DONE",
  "CANCELLED",
];

export const TASK_PRIORITIES: TaskResponseDto["priority"][] = [
  "LOW",
  "MEDIUM",
  "HIGH",
  "URGENT",
];

export const TASK_STATUS_AR: Record<TaskResponseDto["status"], string> = {
  TODO: "قيد الانتظار",
  IN_PROGRESS: "قيد التنفيذ",
  DONE: "منجز",
  CANCELLED: "ملغاة",
};

export const TASK_STATUS_TONE: Record<
  TaskResponseDto["status"],
  "navy" | "info" | "success" | "neutral"
> = {
  TODO: "info",
  IN_PROGRESS: "navy",
  DONE: "success",
  CANCELLED: "neutral",
};

export const TASK_PRIORITY_AR: Record<TaskResponseDto["priority"], string> = {
  LOW: "منخفض",
  MEDIUM: "متوسط",
  HIGH: "عالٍ",
  URGENT: "عاجل",
};

export const ADMIN_TASK_TYPES: AdminTaskTypeDto[] = [
  "NEW_LAWSUIT_REGISTRATION",
  "APPEAL_REGISTRATION",
  "EMAIL_REVIEW",
  "ENFORCEMENT_LAWSUITS",
  "ENFORCEMENT_REQUESTS",
  "COURTS_TAQADI_SYSTEM",
  "PUBLIC_PROSECUTION_SYSTEM",
  "DELEGATIONS",
  "TRANSLATION",
  "MEMO_COORDINATION_REVIEW",
  "HEARING_DATE_FOLLOWUP",
  "EXPERT_REPORT_FOLLOWUP",
  "OPPONENT_APPEAL_FOLLOWUP",
  "OPPONENT_ENFORCEMENT_FOLLOWUP",
  "FAVORABLE_ENFORCEMENT_FOLLOWUP",
  "OPPONENT_MEMO_FOLLOWUP",
  "CRIMINAL_CASE_FOLLOWUP",
  "OPPONENT_CASSATION_FOLLOWUP",
  "OPPONENT_GRIEVANCE_FOLLOWUP",
  "ENFORCEMENT_DISBURSEMENT_FOLLOWUP",
  "LABOR_COMMITTEE_REFERRAL_FOLLOWUP",
  "CIVIL_COURT_REFERRAL_FOLLOWUP",
  "CRIMINAL_COURT_REFERRAL_FOLLOWUP",
  "JUDGMENT_AMENDMENT_ENFORCEMENT_FOLLOWUP",
  "APPEAL_COURT_REFERRAL_FOLLOWUP",
  "PROSECUTION_ENFORCEMENT_JUDGMENT_AMENDMENT",
  "ENFORCEMENT_REVIEW",
  "COURT_REVIEW",
  "EXPERT_DEPOSIT_PAYMENT",
  "STRIKE_RENEWAL",
  "PUBLIC_PROSECUTION_REVIEW",
];

export const ADMIN_TASK_TYPE_AR: Record<AdminTaskTypeDto, string> = {
  NEW_LAWSUIT_REGISTRATION: "تسجيل دعاوى جديدة",
  APPEAL_REGISTRATION: "تسجيل الطعون",
  EMAIL_REVIEW: "مراجعة البريد الإلكتروني",
  ENFORCEMENT_LAWSUITS: "الدعاوى التنفيذية",
  ENFORCEMENT_REQUESTS: "طلبات التنفيذ",
  COURTS_TAQADI_SYSTEM: "نظام المحاكم / تقاضي",
  PUBLIC_PROSECUTION_SYSTEM: "نظام النيابة العامة",
  DELEGATIONS: "التوكيلات",
  TRANSLATION: "الترجمة",
  MEMO_COORDINATION_REVIEW: "تنسيق المذكرات والمراجعة اللغوية",
  HEARING_DATE_FOLLOWUP: "متابعة تحديد الجلسة",
  EXPERT_REPORT_FOLLOWUP: "متابعة تقرير الخبير",
  OPPONENT_APPEAL_FOLLOWUP: "متابعة طعن الخصم",
  OPPONENT_ENFORCEMENT_FOLLOWUP: "متابعة تنفيذ الخصم",
  FAVORABLE_ENFORCEMENT_FOLLOWUP: "متابعة التنفيذ للصالح",
  OPPONENT_MEMO_FOLLOWUP: "متابعة مذكرة الخصم",
  CRIMINAL_CASE_FOLLOWUP: "متابعة الدعوى الجنائية",
  OPPONENT_CASSATION_FOLLOWUP: "متابعة استئناف الخصم",
  OPPONENT_GRIEVANCE_FOLLOWUP: "متابعة التظلم من الخصم",
  ENFORCEMENT_DISBURSEMENT_FOLLOWUP: "متابعة صرف المبالغ بالتنفيذ",
  LABOR_COMMITTEE_REFERRAL_FOLLOWUP: "متابعة الإحالة الي اللجنة العمالية",
  CIVIL_COURT_REFERRAL_FOLLOWUP: "متابعة الإحالة الى المحكمة المدنية",
  CRIMINAL_COURT_REFERRAL_FOLLOWUP: "متابعة الإحالة الي المحكمة الجنائية",
  JUDGMENT_AMENDMENT_ENFORCEMENT_FOLLOWUP:
    "متابعة التعديلات على الحكم بالتنفيذ",
  APPEAL_COURT_REFERRAL_FOLLOWUP: "متابعة إحالة الدعوى لمحكمة الاستئناف",
  PROSECUTION_ENFORCEMENT_JUDGMENT_AMENDMENT:
    "متابعة التعديلات على الحكم في نيابة تنفيذ الاحكام",
  ENFORCEMENT_REVIEW: "مراجعة التنفيذ",
  COURT_REVIEW: "مراجعة المحكمة",
  EXPERT_DEPOSIT_PAYMENT: "سداد امانة خبير",
  STRIKE_RENEWAL: "التجديد من الشطب",
  PUBLIC_PROSECUTION_REVIEW: "مراجعة النيابة العامة",
};
