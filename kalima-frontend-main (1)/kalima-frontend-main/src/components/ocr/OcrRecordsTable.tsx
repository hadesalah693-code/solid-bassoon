"use client";

import Link from "next/link";

import { OcrStatusBadge } from "@/components/ocr/OcrStatusBadge";
import { SectionCard } from "@/components/shared/SectionCard";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import {
  formatConfidence,
  formatLanguage,
  ocrDocumentTypeLabel,
} from "@/lib/ocr/labels";
import { formatDateTime } from "@/lib/utils/format-date";
import type { OcrRecordListDto } from "@/types/ocr";

type Props = {
  items: OcrRecordListDto[];
  compact?: boolean;
  emptyMessage?: string;
};

export function OcrRecordsTable({
  items,
  compact,
  emptyMessage = "لا توجد سجلات OCR.",
}: Props) {
  if (items.length === 0) {
    return (
      <p className="py-8 text-center text-sm text-[var(--color-muted-foreground)]">
        {emptyMessage}
      </p>
    );
  }

  return (
    <SectionCard flush>
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead>العنوان</TableHead>
            {!compact ? <TableHead>النوع</TableHead> : null}
            <TableHead>الحالة</TableHead>
            {!compact ? <TableHead>اللغة</TableHead> : null}
            {!compact ? <TableHead>الثقة</TableHead> : null}
            <TableHead>رفع بواسطة</TableHead>
            <TableHead className="text-end">التاريخ</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {items.map((row) => (
            <TableRow key={row.id}>
              <TableCell className="max-w-xs">
                <Link
                  href={`/ocr/${row.id}`}
                  className="block truncate font-medium text-[var(--color-navy-700)] hover:underline"
                >
                  {row.title}
                </Link>
                <p className="truncate text-xs text-[var(--color-muted-foreground)]">
                  {row.originalFilename}
                </p>
              </TableCell>
              {!compact ? (
                <TableCell className="text-sm">
                  {ocrDocumentTypeLabel(row.documentType)}
                </TableCell>
              ) : null}
              <TableCell>
                <OcrStatusBadge status={row.status} compact={compact} />
              </TableCell>
              {!compact ? (
                <TableCell className="text-sm">
                  {formatLanguage(row.language)}
                </TableCell>
              ) : null}
              {!compact ? (
                <TableCell className="text-sm ltr-nums">
                  {formatConfidence(row.confidenceScore)}
                </TableCell>
              ) : null}
              <TableCell className="text-sm">
                {row.uploadedBy?.fullName ?? "—"}
              </TableCell>
              <TableCell className="text-end text-xs text-[var(--color-muted-foreground)]">
                {formatDateTime(row.createdAt)}
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </SectionCard>
  );
}
