import type { LaborComplaintPartyCapacityDto, LaborComplaintStatusDto } from "@/types/labor-complaint";

export const LABOR_COMPLAINT_STATUS_AR: Record<LaborComplaintStatusDto, string> = {
  ACTIVE: "متداولة",
  SAVED: "حفظ",
  REFERRAL_TO_COMMITTEE: "إحالة للجنة فض النزاعات",
};

export const LABOR_COMPLAINT_STATUS_TONE: Record<
  LaborComplaintStatusDto,
  "warning" | "info" | "neutral" | "success"
> = {
  ACTIVE: "warning",
  SAVED: "success",
  REFERRAL_TO_COMMITTEE: "info",
};

export const LABOR_COMPLAINT_WORKFLOW_STEPS: {
  status: LaborComplaintStatusDto;
  label: string;
  caption?: string;
}[] = [
  { status: "ACTIVE", label: "متداولة", caption: "متابعة في إدارة حماية الأجور" },
  { status: "SAVED", label: "حفظ", caption: "تم فض النزاع في إدارة العمل" },
  {
    status: "REFERRAL_TO_COMMITTEE",
    label: "إحالة للجنة",
    caption: "فتح دعوى أمام لجنة فض النزاعات العمالية",
  },
];

export const LABOR_COMPLAINT_PARTY_CAPACITY_AR: Record<LaborComplaintPartyCapacityDto, string> = {
  PLAINTIFF: "مدعي",
  DEFENDANT: "مدعى عليه",
  EMPLOYEE: "عامل / موظف",
  EMPLOYER: "صاحب عمل",
  OTHER: "أخرى",
};

export const LABOR_COMPLAINT_PARTY_CAPACITY_OPTIONS = (
  Object.entries(LABOR_COMPLAINT_PARTY_CAPACITY_AR) as [LaborComplaintPartyCapacityDto, string][]
).map(([value, label]) => ({ value, label }));

export const DISPUTE_TEAM_ROLE_AR: Record<string, string> = {
  ASSIGNED_LAWYER: "المحامي المسؤول",
  LEGAL_CONSULTANT: "المستشار القانوني",
  ADMINISTRATIVE_OFFICER: "المسؤول الإداري",
  COORDINATION_REVIEW: "مراجعة التنسيق",
  TRANSLATION_OFFICER: "مترجم",
  INITIAL_REVIEW: "مراجعة أولية",
  FINAL_REVIEW: "مراجعة نهائية",
};
