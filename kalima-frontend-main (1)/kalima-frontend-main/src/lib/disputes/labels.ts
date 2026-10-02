import type {
  DisputePartyCapacityDto,
  DisputePartyRoleDto,
  DisputeStageActionTypeDto,
  DisputeStatusDto,
  DisputeTeamRoleDto,
  DisputeTypeDto,
  DisputeWorkflowStepDto,
  LegalPathDto,
  RiskLevelDto,
} from "@/types/dispute";

export const DISPUTE_WORKFLOW_ORDER: DisputeWorkflowStepDto[] = [
  "CREATED",
  "INITIAL_REVIEW",
  "DOCUMENT_COLLECTION",
  "NEGOTIATION",
  "MEDIATION",
  "LEGAL_NOTICE",
  "COURT_FILING",
  "HEARINGS",
  "JUDGMENT",
  "CLOSED",
];

export const DISPUTE_WORKFLOW_STEP_AR: Record<
  DisputeWorkflowStepDto,
  string
> = {
  CREATED: "إنشاء النزاع",
  INITIAL_REVIEW: "تقييم أولي",
  DOCUMENT_COLLECTION: "جمع المستندات",
  NEGOTIATION: "تفاوض",
  MEDIATION: "وساطة",
  LEGAL_NOTICE: "إنذار قانوني",
  COURT_FILING: "رفع دعوى",
  HEARINGS: "جلسات",
  JUDGMENT: "حكم",
  CLOSED: "إغلاق",
};

export const DISPUTE_TYPE_AR: Record<DisputeTypeDto, string> = {
  CIVIL: "مدني",
  COMMERCIAL: "تجاري",
  LABOR: "عمالي",
  ADMINISTRATIVE: "إداري",
  CRIMINAL: "جنائي",
  FAMILY: "أحوال شخصية",
  REAL_ESTATE: "عقاري",
  OTHER: "أخرى",
};

export const DISPUTE_STATUS_AR: Record<DisputeStatusDto, string> = {
  NEW: "جديد",
  UNDER_REVIEW: "قيد المراجعة",
  WAITING_DOCUMENTS: "بانتظار المستندات",
  FRIENDLY_SETTLEMENT: "تسوية ودية",
  NEGOTIATION: "تفاوض",
  MEDIATION: "وساطة",
  READY_TO_CONVERT: "جاهز للتحويل",
  CONVERTED_TO_CASE: "مُحوّل لقضية",
  CLOSED: "مغلق",
  REJECTED: "مرفوض",
};

export const LEGAL_PATH_AR: Record<LegalPathDto, string> = {
  FRIENDLY_SETTLEMENT: "تسوية ودية",
  LEGAL_NOTICE: "إنذار قانوني",
  NEGOTIATION: "تفاوض",
  MEDIATION: "وساطة",
  COURT_CASE: "قضية أمام المحكمة",
  CRIMINAL_REPORT: "بلاغ جنائي",
  ARBITRATION: "تحكيم",
  ADMINISTRATIVE_ACTION: "إجراء إداري",
};

export const RISK_LEVEL_AR: Record<RiskLevelDto, string> = {
  LOW: "منخفض",
  MEDIUM: "متوسط",
  HIGH: "مرتفع",
  CRITICAL: "حرج",
};

/** Case link roles from API (`dispute_case_links.link_role`). */
export const DISPUTE_CASE_LINK_ROLE_AR: Record<string, string> = {
  CONVERTED_LEGACY: "محولة سابقاً",
  CONVERTED: "محولة إلى قضية",
  PRIMARY: "رئيسية",
  RELATED: "مرتبطة",
  LINKED: "مرتبطة",
};

export function disputeCaseLinkRoleAr(role: string): string {
  const key = role.toUpperCase();
  return DISPUTE_CASE_LINK_ROLE_AR[key] ?? DISPUTE_CASE_LINK_ROLE_AR[role] ?? role;
}

/** Roles offered when manually linking a case from the dispute UI (see `LinkDisputeCaseRequest`). */
export type DisputeManualLinkRole = "PRIMARY" | "RELATED" | "CONVERTED";

export const DISPUTE_MANUAL_LINK_ROLE_OPTIONS: {
  value: DisputeManualLinkRole;
}[] = [
  { value: "PRIMARY" },
  { value: "RELATED" },
  { value: "CONVERTED" },
];

/** Free-text stage types stored on `dispute_stages.stage_type` (plus legal-path style values). */
const DISPUTE_STAGE_TYPE_EXTRA_AR: Record<string, string> = {
  COURT_CASE: "قضية أمام المحكمة",
  COURT_FILING: "رفع دعوى",
  HEARINGS: "جلسات",
  JUDGMENT: "حكم",
  EXECUTION: "تنفيذ",
};

export function disputeStageTypeAr(stageType: string): string {
  const u = stageType.toUpperCase();
  if (DISPUTE_STAGE_TYPE_EXTRA_AR[u]) return DISPUTE_STAGE_TYPE_EXTRA_AR[u];
  const asLegal = stageType as keyof typeof LEGAL_PATH_AR;
  if (LEGAL_PATH_AR[asLegal]) return LEGAL_PATH_AR[asLegal];
  return stageType;
}

export const DISPUTE_ADMIN_UPDATE_TYPE_AR: Record<string, string> = {
  INTERNAL_NOTE: "ملاحظة داخلية",
  CONVERTED_TO_CASE: "تم التحويل إلى قضية",
  ACTION_CREATED: "إجراء جديد",
  ACTION_COMPLETED: "اكتمال إجراء",
  ACTION_CANCELLED: "إلغاء إجراء",
  STAGE_CLOSED: "إغلاق مرحلة",
};

export function disputeAdminUpdateTypeAr(updateType: string): string {
  if (DISPUTE_ADMIN_UPDATE_TYPE_AR[updateType]) {
    return DISPUTE_ADMIN_UPDATE_TYPE_AR[updateType];
  }
  const key = updateType.toUpperCase();
  return DISPUTE_ADMIN_UPDATE_TYPE_AR[key] ?? updateType;
}

/** Common API stage status values. */
export function disputeStageStatusAr(status: string): string {
  const u = status.toUpperCase();
  if (u === "ACTIVE") return "نشط";
  if (u === "CLOSED") return "مغلقة";
  if (u === "OPEN") return "مفتوحة";
  return status;
}

export const DISPUTE_ACTION_STATUS_AR: Record<string, string> = {
  OPEN: "مفتوح",
  IN_PROGRESS: "قيد التنفيذ",
  DONE: "مكتمل",
  CANCELLED: "ملغي",
};

export const DISPUTE_ACTION_PRIORITY_AR: Record<string, string> = {
  LOW: "منخفضة",
  MEDIUM: "متوسطة",
  HIGH: "عالية",
  URGENT: "عاجلة",
};

export const DISPUTE_ACTION_TYPE_AR: Record<string, string> = {
  CALL: "اتصال",
  MEETING: "اجتماع",
  DOCUMENT: "مستند",
  DRAFT: "صياغة",
  REVIEW: "مراجعة",
  SEND_NOTICE: "إرسال إنذار",
  FOLLOW_UP: "متابعة",
  COURT_PREPARATION: "تجهيز للمحكمة",
  PAYMENT: "سداد",
  OTHER: "أخرى",
};

export function disputeActionStatusAr(status: string): string {
  const k = status.toUpperCase();
  return DISPUTE_ACTION_STATUS_AR[k] ?? status;
}

export function disputeActionPriorityAr(priority: string): string {
  const k = priority.toUpperCase();
  return DISPUTE_ACTION_PRIORITY_AR[k] ?? priority;
}

export function disputeActionTypeAr(actionType: string): string {
  const k = actionType.toUpperCase();
  return DISPUTE_ACTION_TYPE_AR[k] ?? actionType;
}

/** Canonical codes stored in `dispute_stages.outcome`. */
export const DISPUTE_STAGE_OUTCOME_CODES = [
  "SUCCESS",
  "FAILED",
  "ESCALATED",
  "SETTLED",
  "NO_RESPONSE",
  "NEEDS_FOLLOW_UP",
  "CANCELLED",
] as const;

export type DisputeStageOutcomeCodeDto = (typeof DISPUTE_STAGE_OUTCOME_CODES)[number];

export const DISPUTE_STAGE_OUTCOME_AR: Record<string, string> = {
  SUCCESS: "نجحت",
  FAILED: "فشلت",
  ESCALATED: "تم التصعيد",
  SETTLED: "تمت التسوية",
  NO_RESPONSE: "لم يرد الطرف الآخر",
  NEEDS_FOLLOW_UP: "تحتاج متابعة",
  CANCELLED: "ألغيت",
};

export function disputeStageOutcomeAr(code: string | null | undefined): string {
  if (!code?.trim()) return "—";
  const k = code.toUpperCase();
  return DISPUTE_STAGE_OUTCOME_AR[k] ?? code;
}

/** Options for close-stage form (value = API code). */
export const DISPUTE_STAGE_OUTCOME_OPTIONS: { value: DisputeStageOutcomeCodeDto; label: string }[] =
  DISPUTE_STAGE_OUTCOME_CODES.map((value) => ({
    value,
    label: DISPUTE_STAGE_OUTCOME_AR[value],
  }));

export const ALL_DISPUTE_ACTION_TYPES: DisputeStageActionTypeDto[] = [
  "CALL",
  "MEETING",
  "DOCUMENT",
  "DRAFT",
  "REVIEW",
  "SEND_NOTICE",
  "FOLLOW_UP",
  "COURT_PREPARATION",
  "PAYMENT",
  "OTHER",
];

export const DISPUTE_PARTY_ROLE_AR: Record<DisputePartyRoleDto, string> = {
  CO_CLIENT: "موكّل إضافي",
  OPPOSED_PARTY: "طرف مقابل (مسجّل)",
  CO_OPPONENT: "خصم إضافي",
  REPRESENTATIVE: "ممثّل / وكيل",
  WITNESS: "شاهد",
  EXPERT: "خبير",
  THIRD_PARTY: "طرف ثالث",
  OTHER: "أخرى",
};

export const DISPUTE_PARTY_ROLE_OPTIONS: { value: DisputePartyRoleDto; label: string }[] =
  (
    [
      "CO_CLIENT",
      "OPPOSED_PARTY",
      "CO_OPPONENT",
      "REPRESENTATIVE",
      "WITNESS",
      "EXPERT",
      "THIRD_PARTY",
      "OTHER",
    ] as const satisfies readonly DisputePartyRoleDto[]
  ).map((value) => ({
    value,
    label: DISPUTE_PARTY_ROLE_AR[value],
  }));

export const DISPUTE_PARTY_CAPACITY_AR: Record<DisputePartyCapacityDto, string> = {
  PLAINTIFF: "مدعي",
  DEFENDANT: "مدعى عليه",
  CLAIMANT: "مُطالب",
  RESPONDENT: "مُطالب ضده",
  LESSOR: "مؤجّر",
  LESSEE: "مستأجر",
  EMPLOYER: "صاحب عمل",
  EMPLOYEE: "موظف",
  OTHER: "أخرى",
};

export const DISPUTE_PARTY_CAPACITY_OPTIONS: {
  value: DisputePartyCapacityDto;
  label: string;
}[] = (
  [
    "PLAINTIFF",
    "DEFENDANT",
    "CLAIMANT",
    "RESPONDENT",
    "LESSOR",
    "LESSEE",
    "EMPLOYER",
    "EMPLOYEE",
    "OTHER",
  ] as const satisfies readonly DisputePartyCapacityDto[]
).map((value) => ({
  value,
  label: DISPUTE_PARTY_CAPACITY_AR[value],
}));

export const DISPUTE_TEAM_ROLE_AR: Record<DisputeTeamRoleDto, string> = {
  ASSIGNED_LAWYER: "المحامي المسؤول",
  LEGAL_CONSULTANT: "المستشار القانوني",
  ADMINISTRATIVE_OFFICER: "المسؤول الإداري",
  COORDINATION_REVIEW: "التنسيق والتدقيق",
  TRANSLATION_OFFICER: "مسؤول الترجمة",
  INITIAL_REVIEW: "المراجعة المبدئية",
  FINAL_REVIEW: "المراجعة النهائية",
};

export const DISPUTE_TEAM_ROLE_ORDER: DisputeTeamRoleDto[] = [
  "ASSIGNED_LAWYER",
  "LEGAL_CONSULTANT",
  "ADMINISTRATIVE_OFFICER",
  "COORDINATION_REVIEW",
  "TRANSLATION_OFFICER",
  "INITIAL_REVIEW",
  "FINAL_REVIEW",
];

/** Lightweight UI presets when adding an action from a given dispute stage type. */
export function suggestedDisputeActionTypes(
  stageType: string,
): DisputeStageActionTypeDto[] {
  const u = stageType.toUpperCase();
  switch (u) {
    case "LEGAL_NOTICE":
      return ["DOCUMENT", "DRAFT", "SEND_NOTICE", "CALL", "FOLLOW_UP"];
    case "COURT_FILING":
    case "COURT_CASE":
      return ["COURT_PREPARATION", "DOCUMENT", "REVIEW", "MEETING"];
    case "MEDIATION":
      return ["MEETING", "CALL", "FOLLOW_UP", "DOCUMENT"];
    case "NEGOTIATION":
      return ["MEETING", "CALL", "FOLLOW_UP", "DOCUMENT"];
    default:
      return ["CALL", "FOLLOW_UP", "DOCUMENT", "OTHER"];
  }
}
