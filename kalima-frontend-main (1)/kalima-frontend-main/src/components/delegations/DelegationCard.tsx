"use client";

import Link from "next/link";
import { ArrowLeft, Calendar } from "lucide-react";

import { DelegationCardMenu } from "@/components/delegations/DelegationCardMenu";
import { DelegationStarButton } from "@/components/delegations/DelegationStarButton";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils/cn";
import { formatDate } from "@/lib/utils/format-date";
import {
  DELEGATION_DISPLAY_STATUS_AR,
  DELEGATION_TYPE_AR,
  countryFlag,
  countryLabel,
  displayDelegationStatus,
  statusTone,
} from "@/lib/delegations/labels";
import { formatAgentsLabel } from "@/lib/delegations/staff";
import type { DelegationListItemDto } from "@/types/delegation";

type DelegationCardProps = {
  delegation: DelegationListItemDto;
  className?: string;
  onStarChange?: (id: string, starred: boolean) => void;
};

function partyName(p: DelegationListItemDto["client"]) {
  return p?.fullName?.trim() || "—";
}

export function DelegationCard({ delegation, className, onStarChange }: DelegationCardProps) {
  const displayStatus = displayDelegationStatus(delegation.status, delegation.daysRemaining);
  const clientName = partyName(delegation.client);
  const agentName = formatAgentsLabel(delegation.agents ?? [], delegation.agent);

  return (
    <article
      className={cn(
        "group relative flex flex-col overflow-hidden rounded-2xl border bg-[var(--color-surface)] shadow-[var(--shadow-soft)]",
        "border-[var(--color-border)] ring-1 ring-[var(--color-border)]/40 transition-all",
        "hover:border-[var(--color-gold-400)]/40 hover:shadow-[var(--shadow-pop)]",
        delegation.starred && "border-[var(--color-gold-400)]/45 ring-[var(--color-gold-400)]/25",
        className,
      )}
    >
      <div className="border-b border-[var(--color-border)]/60 bg-[var(--color-surface-muted)]/35 px-4 py-2.5">
        <div className="flex items-center justify-between gap-2">
          <Badge tone={statusTone(displayStatus)} className="text-[11px]">
            {DELEGATION_DISPLAY_STATUS_AR[displayStatus]}
          </Badge>
          <div className="flex items-center gap-0.5">
            <DelegationStarButton
              delegationId={delegation.id}
              starred={delegation.starred}
              onChange={(starred) => onStarChange?.(delegation.id, starred)}
            />
            <DelegationCardMenu
              delegationId={delegation.id}
              starred={delegation.starred}
              onStarChange={(starred) => onStarChange?.(delegation.id, starred)}
            />
          </div>
        </div>
      </div>

      <div className="flex flex-1 flex-col p-4 pt-3.5">
        <p className="font-mono text-[11px] font-semibold tracking-wide text-[var(--color-muted-foreground)] ltr-nums">
          {delegation.delegationNumber}
        </p>

        <h3 className="mt-1.5 line-clamp-2 text-lg font-bold leading-snug text-[var(--color-foreground)]">
          {clientName}
        </h3>

        <p className="mt-1 text-sm text-[var(--color-muted-foreground)]">
          الوكيل: <span className="text-[var(--color-foreground)]">{agentName}</span>
        </p>

        <div className="mt-3 flex flex-wrap items-center gap-x-2 gap-y-1 text-xs text-[var(--color-muted-foreground)]">
          <span>{countryLabel(delegation.issuingCountry)}</span>
          <span aria-hidden>·</span>
          <span>{DELEGATION_TYPE_AR[delegation.delegationType]}</span>
        </div>

        <div className="mt-2.5 flex items-center gap-1.5 text-xs text-[var(--color-muted-foreground)]">
          <Calendar className="size-3.5 shrink-0" aria-hidden />
          <span className="ltr-nums">
            {formatDate(delegation.issuedAt)} — {formatDate(delegation.expiresAt)}
          </span>
        </div>

        {delegation.daysRemaining != null && delegation.status === "ACTIVE" ?
          <p className="mt-2 text-xs font-medium text-[var(--color-gold-700)] ltr-nums">
            {delegation.daysRemaining} يوم متبقٍ
          </p>
        : null}

        <div className="mt-4 flex items-center gap-2 pt-1">
          <Button asChild variant="gold" size="sm" className="flex-1 gap-2">
            <Link href={`/delegations/${delegation.id}`}>
              عرض التفاصيل
              <ArrowLeft className="size-3.5" aria-hidden />
            </Link>
          </Button>
          <span className="text-lg leading-none" title={countryLabel(delegation.issuingCountry)} aria-hidden>
            {countryFlag(delegation.issuingCountry)}
          </span>
        </div>
      </div>
    </article>
  );
}
