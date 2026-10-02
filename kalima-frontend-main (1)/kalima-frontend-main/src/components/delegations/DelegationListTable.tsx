"use client";

import Link from "next/link";

import { DelegationStarButton } from "@/components/delegations/DelegationStarButton";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import {
  DELEGATION_DISPLAY_STATUS_AR,
  DELEGATION_TYPE_AR,
  countryLabel,
  displayDelegationStatus,
  statusTone,
} from "@/lib/delegations/labels";
import { formatAgentsLabel } from "@/lib/delegations/staff";
import { formatDate } from "@/lib/utils/format-date";
import type { DelegationListItemDto } from "@/types/delegation";

type Props = {
  rows: DelegationListItemDto[];
  onStarChange?: (id: string, starred: boolean) => void;
};

function partyName(p: DelegationListItemDto["client"]) {
  return p?.fullName?.trim() || "—";
}

export function DelegationListTable({ rows, onStarChange }: Props) {
  return (
    <div className="overflow-x-auto rounded-2xl border border-[var(--color-border)]">
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead className="w-10" />
            <TableHead>رقم التوكيل</TableHead>
            <TableHead>الموكل</TableHead>
            <TableHead>الوكيل</TableHead>
            <TableHead>النوع</TableHead>
            <TableHead>البلد</TableHead>
            <TableHead>الحالة</TableHead>
            <TableHead>الانتهاء</TableHead>
            <TableHead className="text-end">إجراء</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {rows.map((row) => {
            const displayStatus = displayDelegationStatus(row.status, row.daysRemaining);
            return (
              <TableRow key={row.id} className="hover:bg-[var(--color-surface-muted)]/40">
                <TableCell>
                  <DelegationStarButton
                    delegationId={row.id}
                    starred={row.starred}
                    onChange={(starred) => onStarChange?.(row.id, starred)}
                  />
                </TableCell>
                <TableCell>
                  <div className="min-w-[8rem]">
                    <p className="font-mono text-xs text-[var(--color-muted-foreground)]">
                      {row.delegationNumber}
                    </p>
                    <p className="truncate text-sm font-medium">{row.title}</p>
                  </div>
                </TableCell>
                <TableCell className="max-w-[10rem] truncate">{partyName(row.client)}</TableCell>
                <TableCell className="max-w-[10rem] truncate">
                  {formatAgentsLabel(row.agents ?? [], row.agent)}
                </TableCell>
                <TableCell>{DELEGATION_TYPE_AR[row.delegationType]}</TableCell>
                <TableCell>{countryLabel(row.issuingCountry)}</TableCell>
                <TableCell>
                  <Badge tone={statusTone(displayStatus)}>
                    {DELEGATION_DISPLAY_STATUS_AR[displayStatus]}
                  </Badge>
                </TableCell>
                <TableCell className="whitespace-nowrap text-sm">
                  {formatDate(row.expiresAt)}
                  {row.daysRemaining != null && row.status === "ACTIVE" ?
                    <span className="mt-0.5 block text-xs text-[var(--color-muted-foreground)]">
                      {row.daysRemaining} يوم
                    </span>
                  : null}
                </TableCell>
                <TableCell className="text-end">
                  <Button asChild size="sm" variant="outline">
                    <Link href={`/delegations/${row.id}`}>التفاصيل</Link>
                  </Button>
                </TableCell>
              </TableRow>
            );
          })}
        </TableBody>
      </Table>
    </div>
  );
}
