import type {
  DelegationCancelReasonDto,
  DelegationDisplayStatus,
  DelegationDocumentTypeDto,
  DelegationStatusDto,
  DelegationTypeDto,
} from "@/types/delegation";

export const DELEGATION_TYPE_AR: Record<DelegationTypeDto, string> = {
  GENERAL: "وكالة عامة",
  SPECIAL: "وكالة خاصة",
  JUDICIAL: "وكالة قضائية",
  COMMERCIAL: "وكالة تجارية",
  ADMINISTRATIVE: "وكالة إدارية",
  OFFICIAL: "وكالة رسمية",
  OTHER: "أخرى",
};

export const DELEGATION_STATUS_AR: Record<DelegationStatusDto, string> = {
  ACTIVE: "ساري",
  EXPIRED: "منتهي",
  CANCELLED: "ملغي",
};

export const DELEGATION_DISPLAY_STATUS_AR: Record<DelegationDisplayStatus, string> = {
  ...DELEGATION_STATUS_AR,
  EXPIRING_SOON: "ينتهي قريباً",
};

export const DELEGATION_CANCEL_REASON_AR: Record<DelegationCancelReasonDto, string> = {
  BY_PRINCIPAL: "بطلب الموكل",
  BY_AGENT: "بطلب الوكيل",
  OTHER: "أخرى",
};

export const DELEGATION_DOCUMENT_TYPE_AR: Record<DelegationDocumentTypeDto, string> = {
  ORIGINAL: "أصل التوكيل",
  CANCELLATION: "مستند الإلغاء",
  OTHER: "أخرى",
};

export const ISSUING_COUNTRIES: { code: string; label: string; flag: string }[] = [
  { code: "QA", label: "قطر", flag: "🇶🇦" },
  { code: "SA", label: "السعودية", flag: "🇸🇦" },
  { code: "AE", label: "الإمارات", flag: "🇦🇪" },
  { code: "KW", label: "الكويت", flag: "🇰🇼" },
  { code: "BH", label: "البحرين", flag: "🇧🇭" },
  { code: "OM", label: "عُمان", flag: "🇴🇲" },
  { code: "EG", label: "مصر", flag: "🇪🇬" },
  { code: "JO", label: "الأردن", flag: "🇯🇴" },
];

export function countryLabel(code: string) {
  const c = ISSUING_COUNTRIES.find((x) => x.code === code.toUpperCase());
  return c ? `${c.flag} ${c.label}` : code;
}

export function countryFlag(code: string) {
  return ISSUING_COUNTRIES.find((x) => x.code === code.toUpperCase())?.flag ?? "🌐";
}

export function displayDelegationStatus(
  status: DelegationStatusDto,
  daysRemaining: number | null,
  expiringSoonDays = 30,
): DelegationDisplayStatus {
  if (status === "ACTIVE" && daysRemaining != null && daysRemaining >= 0 && daysRemaining <= expiringSoonDays) {
    return "EXPIRING_SOON";
  }
  return status;
}

export function statusTone(s: DelegationDisplayStatus) {
  switch (s) {
    case "ACTIVE":
      return "success" as const;
    case "EXPIRING_SOON":
      return "gold" as const;
    case "EXPIRED":
      return "neutral" as const;
    case "CANCELLED":
      return "danger" as const;
  }
}

export const REMINDER_OFFSET_OPTIONS = [180, 90, 60, 30] as const;
