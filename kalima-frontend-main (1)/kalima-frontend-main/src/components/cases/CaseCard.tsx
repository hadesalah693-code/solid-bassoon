import Link from "next/link";
import {
  Building2,
  CalendarClock,
  Briefcase,
  User,
} from "lucide-react";

import { Card } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { CaseStatusBadge } from "@/components/shared/StatusBadge";
import { formatDateTime } from "@/lib/utils/format-date";
import { cn } from "@/lib/utils/cn";
import type { CaseFile, CaseStatus } from "@/types/case";

function cardStatusTopBorder(status: CaseStatus) {
  switch (status) {
    case "NEW":
    case "IN_PROGRESS":
      return "border-t-[3px] border-t-emerald-500";
    case "ON_HOLD":
      return "border-t-[3px] border-t-[var(--color-gold-400)]";
    case "CLOSED":
      return "border-t-[3px] border-t-slate-300";
    case "ARCHIVED":
      return "border-t-[3px] border-t-slate-600";
    default:
      return "border-t-[3px] border-t-[var(--color-gold-400)]";
  }
}

export function CaseCard({
  data,
  casesBasePath = "/cases",
}: {
  data: CaseFile;
  /** Base path for case detail, e.g. `/client/cases` for the client portal. */
  casesBasePath?: string;
}) {
  return (
    <Link href={`${casesBasePath}/${data.id}`} className="group block">
      <Card
        className={cn(
          "flex h-full flex-col gap-3 overflow-hidden p-5 transition-all group-hover:-translate-y-0.5 group-hover:shadow-[var(--shadow-pop)]",
          cardStatusTopBorder(data.status),
        )}
      >
        <div>
          <p className="text-[11px] font-medium text-[var(--color-muted-foreground)] ltr-nums">
            #{data.caseNumber}
          </p>
          <h3 className="mt-1 line-clamp-2 text-base font-bold text-[var(--color-foreground)]">
            {data.title}
          </h3>
        </div>

        <div className="flex flex-wrap items-center gap-1.5">
          <CaseStatusBadge value={data.status} />
          {data.caseType ? (
            <Badge tone="neutral" className="max-w-full truncate font-normal">
              {data.caseType}
            </Badge>
          ) : null}
        </div>

        <div className="mt-auto space-y-2 border-t border-[var(--color-border)] pt-3 text-xs text-[var(--color-muted-foreground)]">
          <div className="flex items-start gap-2">
            <User className="mt-0.5 size-3.5 shrink-0 text-[var(--color-navy-500)]" />
            <span className="min-w-0 leading-snug">{data.client.fullName}</span>
          </div>
          {data.assignedLawyer ? (
            <div className="flex items-start gap-2">
              <Briefcase className="mt-0.5 size-3.5 shrink-0 text-[var(--color-navy-500)]" />
              <span className="min-w-0 leading-snug">
                {data.assignedLawyer.fullName}
              </span>
            </div>
          ) : null}
          {data.courtName ? (
            <div className="flex items-start gap-2">
              <Building2 className="mt-0.5 size-3.5 shrink-0 text-[var(--color-navy-500)]" />
              <span className="min-w-0 leading-snug">{data.courtName}</span>
            </div>
          ) : null}
          {data.nextHearingAt ? (
            <div className="flex items-start gap-2 text-[var(--color-navy-800)]">
              <CalendarClock className="mt-0.5 size-3.5 shrink-0 text-[var(--color-navy-600)]" />
              <span className="ltr-nums leading-snug font-medium">
                جلسة قادمة: {formatDateTime(data.nextHearingAt)}
              </span>
            </div>
          ) : null}
        </div>
      </Card>
    </Link>
  );
}
