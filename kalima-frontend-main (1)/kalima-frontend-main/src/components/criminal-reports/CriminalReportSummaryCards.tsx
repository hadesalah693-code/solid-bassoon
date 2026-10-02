"use client";

import { Calendar, Scale, ShieldAlert, User, UserCheck, UserX } from "lucide-react";

import { formatDate } from "@/lib/utils/format-date";
import { CRIMINAL_REPORT_STATUS_AR } from "@/lib/criminal-reports/labels";
import { cn } from "@/lib/utils/cn";
import type { CriminalReportResponseDto } from "@/types/criminal-report";

function displayValue(value: string | null | undefined) {
  const text = value?.trim();
  return text && text.length > 0 ? text : "—";
}

function MetaItem({
  label,
  value,
  hint,
  icon: Icon,
}: {
  label: string;
  value: string;
  hint?: string;
  icon: React.ComponentType<{ className?: string }>;
}) {
  return (
    <div className="min-w-0 rounded-xl border border-[var(--color-border)]/80 bg-[var(--color-surface-muted)]/25 p-4">
      <div className="mb-2 flex items-center gap-2">
        <span className="flex size-8 items-center justify-center rounded-lg bg-[var(--color-navy-50)] text-[var(--color-navy-700)] ring-1 ring-[var(--color-navy-200)]/50">
          <Icon className="size-4" aria-hidden />
        </span>
        <p className="text-[11px] font-semibold uppercase tracking-wide text-[var(--color-muted-foreground)]">
          {label}
        </p>
      </div>
      <p className="truncate text-base font-semibold text-[var(--color-foreground)]" title={value}>
        {value}
      </p>
      {hint ? (
        <p className="mt-1 truncate text-xs text-[var(--color-muted-foreground)]" title={hint}>
          {hint}
        </p>
      ) : null}
    </div>
  );
}

type Props = {
  report: CriminalReportResponseDto;
};

export function CriminalReportSummaryCards({ report }: Props) {
  const client =
    report.clients?.map((c) => c.client.fullName).filter(Boolean).join("، ") ||
    report.client?.fullName ||
    "—";
  const opponent =
    report.opponents?.map((o) => o.name).filter(Boolean).join("، ") ||
    report.opponentName ||
    "—";
  const lawyer = report.assignedLawyer?.fullName ?? "—";

  return (
    <section
      aria-label="ملخص البلاغ"
      className={cn(
        "rounded-2xl border border-[var(--color-border-strong)]/35 bg-[var(--color-surface)] p-4 shadow-[var(--shadow-soft)] ring-1 ring-[var(--color-border)]/50 sm:p-5",
      )}
    >
      <div className="mb-4 flex flex-wrap items-center justify-between gap-2">
        <h2 className="text-sm font-semibold text-[var(--color-foreground)]">نظرة سريعة على الملف</h2>
        <span className="rounded-full bg-[var(--color-gold-50)] px-3 py-1 text-xs font-semibold text-[var(--color-gold-800)] ring-1 ring-[var(--color-gold-200)]/80">
          {CRIMINAL_REPORT_STATUS_AR[report.status]}
        </span>
      </div>
      <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
        <MetaItem label="الموكل" value={displayValue(client)} icon={User} />
        <MetaItem label="الخصم" value={displayValue(opponent)} icon={UserX} />
        <MetaItem
          label="النيابة"
          value={displayValue(report.prosecutionOffice)}
          hint={displayValue(report.securityCenter)}
          icon={ShieldAlert}
        />
        <MetaItem
          label="المحامي المكلف"
          value={displayValue(lawyer)}
          hint={displayValue(report.charge)}
          icon={UserCheck}
        />
        <MetaItem
          label="تاريخ التسجيل"
          value={formatDate(report.registrationDate)}
          hint={report.incidentDate ? `الواقعة: ${formatDate(report.incidentDate)}` : undefined}
          icon={Calendar}
        />
        <MetaItem label="الحالة الإجرائية" value={CRIMINAL_REPORT_STATUS_AR[report.status]} icon={Scale} />
      </div>
    </section>
  );
}
