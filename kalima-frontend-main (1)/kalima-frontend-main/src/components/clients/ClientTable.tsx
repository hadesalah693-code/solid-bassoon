"use client";

import Link from "next/link";
import { Mail, Phone, Pencil } from "lucide-react";

import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { ActiveStatusBadge } from "@/components/shared/StatusBadge";
import type { ClientPerson } from "@/types/person";

function displayName(p: ClientPerson) {
  if (p.personType === "COMPANY" && p.companyName) return p.companyName;
  if (p.fullName) return p.fullName;
  return "—";
}

export function ClientTable({ persons }: { persons: ClientPerson[] }) {
  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead>العميل</TableHead>
          <TableHead>النوع</TableHead>
          <TableHead>التواصل</TableHead>
          <TableHead>الحالة</TableHead>
          <TableHead className="text-end">إجراءات</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {persons.map((p) => (
          <TableRow key={p.id}>
            <TableCell>
              <p className="font-semibold text-[var(--color-foreground)]">
                {displayName(p)}
              </p>
              {p.personType === "INDIVIDUAL" && p.nationalId ? (
                <p className="text-xs text-[var(--color-muted-foreground)] ltr-nums">
                  {p.nationalId}
                </p>
              ) : null}
            </TableCell>
            <TableCell>
              <Badge tone={p.personType === "INDIVIDUAL" ? "info" : "navy"}>
                {p.personType === "INDIVIDUAL" ? "فرد" : "شركة"}
              </Badge>
            </TableCell>
            <TableCell>
              <div className="flex flex-col gap-0.5 text-xs text-[var(--color-muted-foreground)]">
                <span className="inline-flex items-center gap-1.5 ltr-nums">
                  <Mail className="size-3.5" /> {p.email ?? "—"}
                </span>
                {p.phone ? (
                  <span className="inline-flex items-center gap-1.5 ltr-nums">
                    <Phone className="size-3.5" /> {p.phone}
                  </span>
                ) : null}
              </div>
            </TableCell>
            <TableCell>
              <ActiveStatusBadge active={p.active} />
            </TableCell>
            <TableCell className="text-end">
              <Button variant="ghost" size="sm" asChild>
                <Link href={`/clients/${p.id}`} className="inline-flex items-center gap-1.5">
                  <Pencil className="size-4" />
                  تعديل
                </Link>
              </Button>
            </TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );
}
