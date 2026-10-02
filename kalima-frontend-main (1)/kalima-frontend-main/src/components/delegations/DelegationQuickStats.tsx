"use client";

import { useEffect, useState } from "react";
import { Bell, Briefcase, Clock, Paperclip } from "lucide-react";

import { SectionCard } from "@/components/shared/SectionCard";
import { delegationsApi } from "@/lib/api/delegations";
import { delegationDocumentsApi } from "@/lib/api/delegation-documents";
import {
  DELEGATION_DISPLAY_STATUS_AR,
  displayDelegationStatus,
  statusTone,
} from "@/lib/delegations/labels";
import { formatDaysRemainingArabic } from "@/lib/delegations/format-duration";
import { Badge } from "@/components/ui/badge";
import { cn } from "@/lib/utils/cn";
import type { DelegationResponseDto } from "@/types/delegation";

type Props = {
  delegationId: string;
  delegation: DelegationResponseDto;
};

type StatRowProps = {
  icon: React.ReactNode;
  label: string;
  value: React.ReactNode;
};

function StatRow({ icon, label, value }: StatRowProps) {
  return (
    <div className="flex items-start gap-3 rounded-xl border border-[var(--color-border)]/80 bg-[var(--color-surface-muted)]/40 px-3 py-2.5">
      <span className="mt-0.5 text-[var(--color-gold-600)]">{icon}</span>
      <div className="min-w-0 flex-1">
        <p className="text-xs text-[var(--color-muted-foreground)]">{label}</p>
        <p className="mt-0.5 text-sm font-semibold text-[var(--color-foreground)]">{value}</p>
      </div>
    </div>
  );
}

export function DelegationQuickStats({ delegationId, delegation }: Props) {
  const [casesCount, setCasesCount] = useState<number | null>(null);
  const [docsCount, setDocsCount] = useState<number | null>(null);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const [cases, docs] = await Promise.all([
          delegationsApi.listCases(delegationId),
          delegationDocumentsApi.list(delegationId),
        ]);
        if (!cancelled) {
          setCasesCount(cases.length);
          setDocsCount(docs.length);
        }
      } catch {
        if (!cancelled) {
          setCasesCount(0);
          setDocsCount(0);
        }
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [delegationId]);

  const displayStatus = displayDelegationStatus(delegation.status, delegation.daysRemaining);
  const remaining = formatDaysRemainingArabic(delegation.daysRemaining, delegation.status);
  const remindersCount = delegation.reminderOffsets?.length ?? 0;

  return (
    <SectionCard title="إحصائيات سريعة" contentClassName="space-y-2.5">
      <StatRow
        icon={<Clock className="size-4" aria-hidden />}
        label="المدة المتبقية"
        value={
          remaining === "—" ?
            "—"
          : <span className="ltr-nums">{remaining}</span>
        }
      />
      <StatRow
        icon={<Briefcase className="size-4" aria-hidden />}
        label="القضايا المرتبطة"
        value={
          casesCount == null ?
            "..."
          : <span className="ltr-nums">{casesCount}</span>
        }
      />
      <StatRow
        icon={<Paperclip className="size-4" aria-hidden />}
        label="المرفقات"
        value={
          docsCount == null ?
            "..."
          : <span className="ltr-nums">{docsCount}</span>
        }
      />
      <StatRow
        icon={<Bell className="size-4" aria-hidden />}
        label="تذكيرات الانتهاء"
        value={<span className="ltr-nums">{remindersCount}</span>}
      />
      <div
        className={cn(
          "mt-1 rounded-xl border px-3 py-2.5",
          "border-[var(--color-border)]/80 bg-[var(--color-surface)]",
        )}
      >
        <p className="text-xs text-[var(--color-muted-foreground)]">الحالة الحالية</p>
        <Badge tone={statusTone(displayStatus)} className="mt-1.5">
          {DELEGATION_DISPLAY_STATUS_AR[displayStatus]}
        </Badge>
      </div>
    </SectionCard>
  );
}
