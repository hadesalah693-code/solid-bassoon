import { CalendarDays, MapPin, Gavel, User } from "lucide-react";

import { Card } from "@/components/ui/card";
import { HearingStatusBadge } from "@/components/shared/StatusBadge";
import { formatDate, formatTime } from "@/lib/utils/format-date";
import type { Hearing } from "@/types/hearing";

export function HearingListItem({ data }: { data: Hearing }) {
  return (
    <Card className="flex flex-col gap-4 p-5 md:flex-row md:items-center">
      <div className="flex min-w-[4.5rem] flex-col items-center justify-center gap-0.5 rounded-xl bg-[var(--color-navy-50)] px-3 py-3 text-[var(--color-navy-700)]">
        <CalendarDays className="size-4" />
        <span className="mt-1 text-sm font-semibold ltr-nums">
          {formatDate(data.scheduledAt, "dd MMM")}
        </span>
        <span className="text-xs ltr-nums">{formatTime(data.scheduledAt)}</span>
      </div>

      <div className="min-w-0 flex-1">
        <div className="flex items-center gap-2 text-xs text-[var(--color-muted-foreground)] ltr-nums">
          #{data.caseNumber}
        </div>
        <h3 className="mt-0.5 line-clamp-2 text-base font-semibold text-[var(--color-foreground)]">
          {data.caseTitle}
        </h3>
        <div className="mt-2 flex flex-wrap items-center gap-x-4 gap-y-1 text-xs text-[var(--color-muted-foreground)]">
          {data.court ? (
            <span className="inline-flex items-center gap-1">
              <MapPin className="size-3.5" />
              {data.court}
              {data.room ? ` — ${data.room}` : ""}
            </span>
          ) : null}
          <span className="inline-flex items-center gap-1">
            <User className="size-3.5" />
            {data.client.fullName}
          </span>
          {data.assignedLawyer ? (
            <span className="inline-flex items-center gap-1">
              <Gavel className="size-3.5" />
              {data.assignedLawyer.fullName}
            </span>
          ) : null}
        </div>
        {data.notes ? (
          <p className="mt-2 text-xs text-[var(--color-muted-foreground)]">
            {data.notes}
          </p>
        ) : null}
      </div>

      <div className="shrink-0">
        <HearingStatusBadge value={data.status} />
      </div>
    </Card>
  );
}
