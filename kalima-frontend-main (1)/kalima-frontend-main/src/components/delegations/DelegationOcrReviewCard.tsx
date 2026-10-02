"use client";

import { CheckCircle2 } from "lucide-react";

import { SectionCard } from "@/components/shared/SectionCard";
import { Button } from "@/components/ui/button";
import {
  DELEGATION_TYPE_AR,
  countryLabel,
} from "@/lib/delegations/labels";
import { buildDelegationTitle } from "@/lib/delegations/build-title";
import type { DelegationIntakeDto } from "@/lib/api/delegation-intake";
import type { DelegationTypeDto } from "@/types/delegation";

type Props = {
  intake: DelegationIntakeDto;
  clientName: string;
  confirmed: boolean;
  onConfirm: () => void;
};

function row(label: string, value: string | null | undefined) {
  return (
    <div className="flex flex-wrap justify-between gap-2 border-b border-[var(--color-border)]/60 py-2 last:border-0">
      <span className="text-sm text-[var(--color-muted-foreground)]">{label}</span>
      <span className="text-sm font-medium text-[var(--color-foreground)]">{value?.trim() || "—"}</span>
    </div>
  );
}

export function DelegationOcrReviewCard({ intake, clientName, confirmed, onConfirm }: Props) {
  const title = buildDelegationTitle(
    intake.suggestedClient?.fullName ?? clientName ?? intake.suggestedClientName ?? "—",
    intake.notaryNumber,
  );

  return (
    <SectionCard
      title="تأكيد البيانات المستخرجة"
      contentClassName="space-y-4"
    >
      <p className="text-sm text-[var(--color-muted-foreground)]">
        راجع البيانات التالية من المستند قبل المتابعة وتعديل الحقول إن لزم.
      </p>
      <div className="rounded-xl border border-[var(--color-border)] bg-[var(--color-surface-muted)]/30 px-4">
        {row("عنوان التوكيل (متوقع)", title)}
        {row("الموكل", intake.suggestedClient?.fullName ?? intake.suggestedClientName)}
        {row("الوكيل", intake.agentMatch.person?.fullName ?? intake.suggestedAgentName)}
        {row("رقم التوثيق", intake.notaryNumber)}
        {row("تاريخ الإصدار", intake.issuedAt)}
        {row("تاريخ الانتهاء", intake.expiresAt)}
        {row(
          "نوع التوكيل",
          intake.delegationType ? DELEGATION_TYPE_AR[intake.delegationType as DelegationTypeDto] : null,
        )}
        {row("بلد الإصدار", intake.issuingCountry ? countryLabel(intake.issuingCountry) : null)}
      </div>
      {confirmed ? (
        <p className="flex items-center gap-2 text-sm text-[var(--color-success)]">
          <CheckCircle2 className="size-4" aria-hidden />
          تم التأكيد — يمكنك تعديل الحقول أدناه ثم الحفظ
        </p>
      ) : (
        <Button type="button" onClick={onConfirm}>
          تأكيد البيانات والمتابعة
        </Button>
      )}
    </SectionCard>
  );
}
