"use client";

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { useParams } from "next/navigation";
import { ArrowRight, FileText, Printer } from "lucide-react";

import { ApiErrorAlert } from "@/components/shared/ApiErrorAlert";
import { PageHeader } from "@/components/shared/PageHeader";
import { PageLoading } from "@/components/shared/PageLoading";
import { SectionCard } from "@/components/shared/SectionCard";
import { DocumentPreview } from "@/components/templates/DocumentPreview";
import {
  DocumentStatusBadge,
  documentStatusLabel,
  templateTypeLabel,
} from "@/components/templates/TemplateBadges";
import { Button } from "@/components/ui/button";
import { ApiRequestError } from "@/lib/api/client";
import { documentsApi } from "@/lib/api/documents";
import { notifyApiError, notifySuccess } from "@/lib/feedback";
import { formatDateTime } from "@/lib/utils/format-date";
import { cn } from "@/lib/utils/cn";
import {
  DOCUMENT_STATUSES,
  type DocumentStatus,
  type GeneratedDocumentResponse,
} from "@/types/template";

export default function GeneratedDocumentPage() {
  const params = useParams<{ id: string }>();
  const id = typeof params?.id === "string" ? params.id : null;

  const [doc, setDoc] = useState<GeneratedDocumentResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [fetchId, setFetchId] = useState(0);
  const [updatingStatus, setUpdatingStatus] = useState<DocumentStatus | null>(
    null,
  );

  useEffect(() => {
    if (!id) return;
    let cancelled = false;
    (async () => {
      setLoading(true);
      setError(null);
      try {
        const data = await documentsApi.get(id);
        if (cancelled) return;
        setDoc(data);
      } catch (e) {
        if (cancelled) return;
        if (e instanceof ApiRequestError) {
          setError(e.details?.message ?? e.message);
        } else {
          setError("تعذر تحميل المستند");
        }
        setDoc(null);
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [id, fetchId]);

  const changeStatus = useCallback(
    async (next: DocumentStatus) => {
      if (!id || !doc || doc.status === next || updatingStatus) return;
      setUpdatingStatus(next);
      try {
        const updated = await documentsApi.updateStatus(id, { status: next });
        setDoc(updated);
        notifySuccess("تم تحديث الحالة", documentStatusLabel(updated.status));
      } catch (e) {
        notifyApiError(e, "تعذر تحديث حالة المستند");
      } finally {
        setUpdatingStatus(null);
      }
    },
    [id, doc, updatingStatus],
  );

  if (!id) {
    return <ApiErrorAlert message="معرف المستند غير صالح" />;
  }

  if (loading) {
    return (
      <PageLoading
        label="جارٍ تحميل المستند…"
        minHeightClassName="min-h-[24rem]"
      />
    );
  }

  if (error || !doc) {
    return (
      <ApiErrorAlert
        message={error ?? "تعذر تحميل المستند"}
        onRetry={() => setFetchId((n) => n + 1)}
      />
    );
  }

  return (
    <div className="space-y-6">
      {/* PageHeader — hidden during print */}
      <div className="no-print">
        <PageHeader
          title={doc.title}
          description={
            doc.templateName
              ? `من نموذج «${doc.templateName}»${
                  doc.templateType
                    ? ` — ${templateTypeLabel(doc.templateType)}`
                    : ""
                }`
              : "مستند مولَّد"
          }
          actions={
            <div className="flex items-center gap-2">
              <Button
                type="button"
                variant="outline"
                onClick={() => window.print()}
                className="gap-2"
              >
                <Printer className="size-4" />
                طباعة / PDF
              </Button>
              <Button variant="ghost" asChild>
                <Link
                  href="/documents"
                  className="inline-flex items-center gap-2 text-[var(--color-muted-foreground)]"
                >
                  <ArrowRight className="size-4 rotate-180" />
                  كل المستندات
                </Link>
              </Button>
            </div>
          }
        />
      </div>

      <div className="grid gap-6 lg:grid-cols-[1fr_18rem]">
        {/* Document preview — visible both on screen and in print */}
        <div className="print-area">
          <SectionCard
            title="معاينة المستند"
            action={
              <div className="flex size-10 items-center justify-center rounded-xl bg-[var(--color-navy-50)] text-[var(--color-navy-700)] no-print">
                <FileText className="size-5" />
              </div>
            }
          >
            <DocumentPreview
              content={doc.renderedContent || ""}
              isRendered
            />
          </SectionCard>
        </div>

        {/* Status + details sidebar — hidden during print */}
        <div className="space-y-4 no-print">
          <SectionCard title="الحالة">
            <div className="space-y-3">
              <div className="flex items-center justify-between">
                <span className="text-xs text-[var(--color-muted-foreground)]">
                  الحالة الحالية
                </span>
                <DocumentStatusBadge value={doc.status} />
              </div>
              <div className="grid grid-cols-2 gap-2">
                {DOCUMENT_STATUSES.map((s) => {
                  const isCurrent = doc.status === s;
                  const isLoading = updatingStatus === s;
                  return (
                    <button
                      key={s}
                      type="button"
                      onClick={() => changeStatus(s)}
                      disabled={isCurrent || updatingStatus !== null}
                      className={cn(
                        "rounded-xl border px-3 py-2 text-xs font-medium transition-colors",
                        isCurrent
                          ? "border-[var(--color-gold-400)] bg-[var(--color-gold-50)] text-[var(--color-gold-700)] cursor-default"
                          : "border-[var(--color-border)] bg-[var(--color-surface)] text-[var(--color-foreground)] hover:bg-[var(--color-surface-muted)]",
                        updatingStatus !== null &&
                          !isCurrent &&
                          "opacity-60 cursor-not-allowed",
                      )}
                    >
                      {isLoading ? "…" : documentStatusLabel(s)}
                    </button>
                  );
                })}
              </div>
            </div>
          </SectionCard>

          <SectionCard title="التفاصيل">
            <dl className="grid grid-cols-1 gap-3 text-sm">
              <DetailRow label="الموكِّل" value={doc.clientName ?? "—"} />
              <DetailRow
                label="القضية"
                value={
                  doc.caseId && doc.caseNumber ? (
                    <Link
                      href={`/cases/${doc.caseId}`}
                      className="ltr-nums text-[var(--color-navy-700)] hover:underline"
                    >
                      #{doc.caseNumber}
                    </Link>
                  ) : (
                    "—"
                  )
                }
              />
              <DetailRow
                label="النموذج"
                value={
                  doc.templateName
                    ? doc.templateType
                      ? `${doc.templateName} (${templateTypeLabel(doc.templateType)})`
                      : doc.templateName
                    : "—"
                }
              />
              <DetailRow label="أُنشئ في" value={formatDateTime(doc.createdAt)} />
              <DetailRow
                label="آخر تحديث"
                value={formatDateTime(doc.updatedAt)}
              />
            </dl>
          </SectionCard>
        </div>
      </div>
    </div>
  );
}

function DetailRow({
  label,
  value,
}: {
  label: string;
  value: React.ReactNode;
}) {
  return (
    <div className="flex items-start justify-between gap-3 border-b border-[var(--color-border)] pb-2 last:border-0 last:pb-0">
      <dt className="text-xs text-[var(--color-muted-foreground)]">{label}</dt>
      <dd className="text-end text-sm text-[var(--color-foreground)]">
        {value}
      </dd>
    </div>
  );
}
