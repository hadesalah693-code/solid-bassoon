import { Badge, type BadgeProps } from "@/components/ui/badge";
import type { CaseStatus, CasePriority } from "@/types/case";
import type { HearingStatus } from "@/types/hearing";
import type { SystemRole } from "@/types/staff";
import type { DisputeEventStatusDto } from "@/types/api";

const CASE_STATUS: Record<
  CaseStatus,
  { label: string; tone: BadgeProps["tone"] }
> = {
  NEW: { label: "جديدة", tone: "info" },
  IN_PROGRESS: { label: "قيد التنفيذ", tone: "navy" },
  ON_HOLD: { label: "معلّقة", tone: "warning" },
  CLOSED: { label: "مغلقة", tone: "success" },
  ARCHIVED: { label: "مؤرشفة", tone: "neutral" },
};

const CASE_PRIORITY: Record<
  CasePriority,
  { label: string; tone: BadgeProps["tone"] }
> = {
  LOW: { label: "منخفضة", tone: "neutral" },
  MEDIUM: { label: "متوسطة", tone: "info" },
  HIGH: { label: "عالية", tone: "warning" },
  URGENT: { label: "عاجلة", tone: "danger" },
};

const HEARING_STATUS: Record<
  HearingStatus,
  { label: string; tone: BadgeProps["tone"] }
> = {
  SCHEDULED: { label: "مجدولة", tone: "info" },
  HELD: { label: "انعقدت", tone: "success" },
  POSTPONED: { label: "مؤجلة", tone: "warning" },
  CANCELLED: { label: "ملغاة", tone: "danger" },
  ADJOURNED: { label: "مرفوعة", tone: "warning" },
};

const ACTIVE_STATUS = {
  active: { label: "نشط", tone: "success" as const },
  inactive: { label: "موقوف", tone: "neutral" as const },
};

const ROLE_STATUS: Record<
  SystemRole,
  { label: string; tone: BadgeProps["tone"] }
> = {
  ADMIN: { label: "مدير", tone: "navy" },
  SENIOR_LAWYER: { label: "محامي أول", tone: "gold" },
  LAWYER: { label: "محامي", tone: "info" },
  SECRETARY: { label: "سكرتير", tone: "neutral" },
  ACCOUNTANT: { label: "محاسب", tone: "neutral" },
  CLIENT: { label: "عميل", tone: "neutral" },
  CLIENT_USER: { label: "بوابة العميل", tone: "neutral" },
};

/** Dispute path milestone status (`DisputeEventStatus`); labels in Arabic per Kalima UX. */
const DISPUTE_EVENT_STATUS: Record<
  DisputeEventStatusDto,
  { label: string; tone: BadgeProps["tone"] }
> = {
  DONE: { label: "مكتملة", tone: "success" },
  CURRENT: { label: "جارية", tone: "gold" },
  UPCOMING: { label: "قادمة", tone: "info" },
  CANCELLED: { label: "ملغاة", tone: "neutral" },
};

export function CaseStatusBadge({ value }: { value: CaseStatus }) {
  const v = CASE_STATUS[value];
  return <Badge tone={v.tone}>{v.label}</Badge>;
}

export function CasePriorityBadge({ value }: { value: CasePriority }) {
  const v = CASE_PRIORITY[value];
  return <Badge tone={v.tone}>{v.label}</Badge>;
}

export function HearingStatusBadge({ value }: { value: HearingStatus }) {
  const v = HEARING_STATUS[value];
  return <Badge tone={v.tone}>{v.label}</Badge>;
}

export function DisputeEventStatusBadge({
  value,
}: {
  value: DisputeEventStatusDto;
}) {
  const v = DISPUTE_EVENT_STATUS[value];
  return <Badge tone={v.tone}>{v.label}</Badge>;
}

export function ActiveStatusBadge({ active }: { active: boolean }) {
  const v = active ? ACTIVE_STATUS.active : ACTIVE_STATUS.inactive;
  return (
    <Badge tone={v.tone} className="gap-1.5">
      <span
        className={
          active
            ? "size-1.5 rounded-full bg-[var(--color-success)]"
            : "size-1.5 rounded-full bg-slate-400"
        }
      />
      {v.label}
    </Badge>
  );
}

export function RoleBadge({ role }: { role: SystemRole }) {
  const v = ROLE_STATUS[role];
  return <Badge tone={v.tone}>{v.label}</Badge>;
}
