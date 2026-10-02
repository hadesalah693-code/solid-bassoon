"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { FileText } from "lucide-react";

import { ApiErrorAlert } from "@/components/shared/ApiErrorAlert";
import { EmptyState } from "@/components/shared/EmptyState";
import { PageHeader } from "@/components/shared/PageHeader";
import { PageLoading } from "@/components/shared/PageLoading";
import { SectionCard } from "@/components/shared/SectionCard";
import {
  DocumentStatusBadge,
  documentStatusLabel,
  templateTypeLabel,
} from "@/components/templates/TemplateBadges";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { ApiRequestError } from "@/lib/api/client";
import { documentsApi } from "@/lib/api/documents";
import { formatDate } from "@/lib/utils/format-date";
import { cn } from "@/lib/utils/cn";
import {
  DOCUMENT_STATUSES,
  type DocumentStatus,
  type GeneratedDocumentResponse,
} from "@/types/template";

type StatusFilter = "ALL" | DocumentStatus;

export default function GeneratedDocumentsPage() {
  const [items, setItems] = useState<GeneratedDocumentResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [fetchId, setFetchId] = useState(0);
  const [statusFilter, setStatusFilter] = useState<StatusFilter>("ALL");

  useEffect(() => {
    let cancelled = false;
    (async () => {
      setLoading(true);
      setError(null);
      try {
        const list = await documentsApi.list({
          status: statusFilter === "ALL" ? undefined : statusFilter,
        });
        if (cancelled) return;
        setItems(list);
      } catch (e) {
        if (cancelled) return;
        if (e instanceof ApiRequestError) {
          setError(e.details?.message ?? e.message);
        } else {
          setError("تعذر تحميل المستندات");
        }
        setItems([]);
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [fetchId, statusFilter]);

  return (
    <div className="space-y-6">
      <PageHeader
        title="المستندات المولّدة"
        description="كل المستندات الناتجة عن النماذج القابلة للتعبئة، مع تتبّع حالة المراجعة والاعتماد."
      />

      {error ? (
        <ApiErrorAlert
          message={error}
          onRetry={() => setFetchId((n) => n + 1)}
        />
      ) : null}

      <div className="flex flex-wrap items-center gap-2">
        <FilterPill
          label="الكل"
          active={statusFilter === "ALL"}
          onClick={() => setStatusFilter("ALL")}
        />
        {DOCUMENT_STATUSES.map((s) => (
          <FilterPill
            key={s}
            label={documentStatusLabel(s)}
            active={statusFilter === s}
            onClick={() => setStatusFilter(s)}
          />
        ))}
      </div>

      {loading ? (
        <PageLoading
          label="جارٍ تحميل المستندات…"
          minHeightClassName="min-h-[18rem]"
        />
      ) : error ? null : items.length === 0 ? (
        <EmptyState
          title="لا توجد مستندات"
          description={
            statusFilter === "ALL"
              ? "لم يتم توليد أي مستندات بعد. ابدأ من صفحة النماذج بالضغط على «إنشاء مستند»."
              : "لا توجد مستندات بهذه الحالة. جرّب فلترًا آخر."
          }
          icon={FileText}
        />
      ) : (
        <SectionCard flush>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>العنوان</TableHead>
                <TableHead>النموذج</TableHead>
                <TableHead>الحالة</TableHead>
                <TableHead>الموكِّل</TableHead>
                <TableHead>القضية</TableHead>
                <TableHead className="text-end">تاريخ الإنشاء</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {items.map((d) => (
                <TableRow key={d.id}>
                  <TableCell className="max-w-xs truncate font-medium">
                    <Link
                      href={`/documents/${d.id}`}
                      className="text-[var(--color-navy-700)] hover:underline"
                    >
                      {d.title}
                    </Link>
                  </TableCell>
                  <TableCell className="text-sm">
                    <span className="text-[var(--color-foreground)]">
                      {d.templateName ?? "—"}
                    </span>
                    {d.templateType ? (
                      <span className="ms-2 text-xs text-[var(--color-muted-foreground)]">
                        ({templateTypeLabel(d.templateType)})
                      </span>
                    ) : null}
                  </TableCell>
                  <TableCell>
                    <DocumentStatusBadge value={d.status} />
                  </TableCell>
                  <TableCell className="text-sm">
                    {d.clientName ?? "—"}
                  </TableCell>
                  <TableCell className="text-sm ltr-nums">
                    {d.caseNumber ? `#${d.caseNumber}` : "—"}
                  </TableCell>
                  <TableCell className="text-end text-xs text-[var(--color-muted-foreground)]">
                    {formatDate(d.createdAt)}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </SectionCard>
      )}
    </div>
  );
}

function FilterPill({
  label,
  active,
  onClick,
}: {
  label: string;
  active: boolean;
  onClick: () => void;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      aria-pressed={active}
      className={cn(
        "inline-flex items-center rounded-xl border px-3 py-1.5 text-xs font-medium transition-colors",
        active
          ? "border-[var(--color-gold-400)] bg-[var(--color-gold-50)] text-[var(--color-gold-700)]"
          : "border-[var(--color-border)] bg-[var(--color-surface)] text-[var(--color-muted-foreground)] hover:text-[var(--color-foreground)]",
      )}
    >
      {label}
    </button>
  );
}
