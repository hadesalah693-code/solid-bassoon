"use client";

import { Calendar, Clock, Globe, Hash, Stamp } from "lucide-react";

import { OperationalKpiCard } from "@/components/dashboard/OperationalKpiCard";
import {
  DELEGATION_DISPLAY_STATUS_AR,
  DELEGATION_TYPE_AR,
  countryLabel,
  displayDelegationStatus,
  statusTone,
} from "@/lib/delegations/labels";
import { formatDaysRemainingArabic } from "@/lib/delegations/format-duration";
import { formatDate } from "@/lib/utils/format-date";
import type { DelegationResponseDto } from "@/types/delegation";

type DelegationSummaryCardsProps = {
  delegation: DelegationResponseDto;
};

export function DelegationSummaryCards({ delegation }: DelegationSummaryCardsProps) {
  const displayStatus = displayDelegationStatus(delegation.status, delegation.daysRemaining);
  const accent = statusTone(displayStatus);
  const remainingLabel = formatDaysRemainingArabic(delegation.daysRemaining, delegation.status);
  const clientName = delegation.client?.fullName?.trim() || "—";

  return (
    <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-5">
      <OperationalKpiCard
        label="رقم التوكيل"
        value={delegation.delegationNumber}
        hint={clientName !== "—" ? `الموكل: ${clientName}` : undefined}
        icon={<Hash aria-hidden />}
        accent="navy"
        delay={0}
      />
      <OperationalKpiCard
        label="الحالة"
        value={DELEGATION_DISPLAY_STATUS_AR[displayStatus]}
        hint={remainingLabel !== "—" ? `المدة المتبقية: ${remainingLabel}` : undefined}
        icon={<Clock aria-hidden />}
        accent={accent === "neutral" ? "navy" : accent}
        delay={0.05}
      />
      <OperationalKpiCard
        label="التواريخ"
        value={formatDate(delegation.expiresAt)}
        hint={`الإصدار: ${formatDate(delegation.issuedAt)}`}
        icon={<Calendar aria-hidden />}
        accent="navy"
        delay={0.1}
      />
      <OperationalKpiCard
        label="نوع التوكيل"
        value={DELEGATION_TYPE_AR[delegation.delegationType]}
        icon={<Stamp aria-hidden />}
        accent="gold"
        delay={0.15}
      />
      <OperationalKpiCard
        label="بلد الإصدار"
        value={countryLabel(delegation.issuingCountry)}
        icon={<Globe aria-hidden />}
        accent="info"
        delay={0.2}
      />
    </div>
  );
}
