"use client";

import { AlertTriangle, Scale, User, UserCheck, UserX } from "lucide-react";

import {
  OperationalKpiCard,
  type OperationalKpiAccent,
} from "@/components/dashboard/OperationalKpiCard";
import {
  DISPUTE_STATUS_AR,
  RISK_LEVEL_AR,
} from "@/lib/disputes/labels";
import type { DisputeResponseDto } from "@/types/dispute";

function statusAccent(s: DisputeResponseDto["status"]): OperationalKpiAccent {
  switch (s) {
    case "CONVERTED_TO_CASE":
    case "CLOSED":
      return "success";
    case "REJECTED":
      return "danger";
    case "READY_TO_CONVERT":
      return "gold";
    case "NEW":
    case "UNDER_REVIEW":
      return "info";
    default:
      return "navy";
  }
}

function riskAccent(r: DisputeResponseDto["riskLevel"]): OperationalKpiAccent {
  switch (r) {
    case "LOW":
      return "navy";
    case "MEDIUM":
      return "info";
    case "HIGH":
      return "gold";
    case "CRITICAL":
      return "danger";
  }
}

type Props = {
  row: DisputeResponseDto;
};

export function DisputeSummaryCards({ row }: Props) {
  const lawyer = row.assignedLawyer?.fullName ?? "—";
  const client = row.client?.fullName ?? "—";
  const opponent =
    row.opponentName?.trim().length > 22
      ? `${row.opponentName.trim().slice(0, 20)}…`
      : row.opponentName?.trim() || "—";

  return (
    <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-5">
      <OperationalKpiCard
        label="العميل"
        value={client.length > 22 ? `${client.slice(0, 20)}…` : client}
        hint={client.length > 22 ? client : "الموكّل الرئيسي"}
        icon={<User aria-hidden />}
        accent="navy"
        delay={0}
      />
      <OperationalKpiCard
        label="الخصم"
        value={opponent}
        hint={row.opponentName?.trim() || "الطرف المقابل"}
        icon={<UserX aria-hidden />}
        accent="navy"
        delay={0.05}
      />
      <OperationalKpiCard
        label="الحالة"
        value={DISPUTE_STATUS_AR[row.status]}
        hint="وضع الملف الحالي"
        icon={<Scale aria-hidden />}
        accent={statusAccent(row.status)}
        delay={0.1}
      />
      <OperationalKpiCard
        label="الخطورة"
        value={RISK_LEVEL_AR[row.riskLevel]}
        hint="تقييم المخاطر"
        icon={<AlertTriangle aria-hidden />}
        accent={riskAccent(row.riskLevel)}
        delay={0.15}
      />
      <OperationalKpiCard
        label="المحامي المسؤول"
        value={lawyer.length > 22 ? `${lawyer.slice(0, 20)}…` : lawyer}
        hint={lawyer.length > 22 ? lawyer : "المكلف بالمتابعة"}
        icon={<UserCheck aria-hidden />}
        accent="gold"
        delay={0.2}
      />
    </div>
  );
}
