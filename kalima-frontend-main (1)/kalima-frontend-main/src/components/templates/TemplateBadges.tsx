import { Badge, type BadgeProps } from "@/components/ui/badge";
import type { DocumentStatus, TemplateType } from "@/types/template";

const TEMPLATE_TYPE_LABEL: Record<
  TemplateType,
  { label: string; tone: BadgeProps["tone"] }
> = {
  QUOTATION: { label: "عرض سعر", tone: "info" },
  INVOICE: { label: "فاتورة", tone: "navy" },
  RECEIPT: { label: "إيصال قبض", tone: "neutral" },
  LEGAL_SERVICE_CONTRACT: { label: "عقد خدمات قانونية", tone: "gold" },
  RETAINER_AGREEMENT: { label: "اتفاقية أتعاب", tone: "gold" },
  POWER_OF_ATTORNEY: { label: "وكالة", tone: "navy" },
};

const DOCUMENT_STATUS_LABEL: Record<
  DocumentStatus,
  { label: string; tone: BadgeProps["tone"] }
> = {
  DRAFT: { label: "مسودة", tone: "neutral" },
  UNDER_REVIEW: { label: "قيد المراجعة", tone: "warning" },
  APPROVED: { label: "معتمد", tone: "success" },
  SENT: { label: "مُرسل", tone: "info" },
};

export function templateTypeLabel(type: TemplateType): string {
  return TEMPLATE_TYPE_LABEL[type]?.label ?? type;
}

export function documentStatusLabel(status: DocumentStatus): string {
  return DOCUMENT_STATUS_LABEL[status]?.label ?? status;
}

export function TemplateTypeBadge({ value }: { value: TemplateType }) {
  const v = TEMPLATE_TYPE_LABEL[value] ?? { label: value, tone: "neutral" };
  return <Badge tone={v.tone}>{v.label}</Badge>;
}

export function DocumentStatusBadge({ value }: { value: DocumentStatus }) {
  const v = DOCUMENT_STATUS_LABEL[value] ?? { label: value, tone: "neutral" };
  return <Badge tone={v.tone}>{v.label}</Badge>;
}

export function IsDefaultBadge({ isDefault }: { isDefault: boolean }) {
  return isDefault ? (
    <Badge tone="gold">افتراضي</Badge>
  ) : (
    <Badge tone="neutral">مخصّص</Badge>
  );
}
