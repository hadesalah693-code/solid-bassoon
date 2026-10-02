"use client";

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import {
  ArrowRight,
  Download,
  Loader2,
  Pencil,
  RefreshCw,
  Trash2,
} from "lucide-react";

import { useAuth } from "@/components/auth/auth-provider";
import { OcrStatusBadge } from "@/components/ocr/OcrStatusBadge";
import { OcrTextPanel } from "@/components/ocr/OcrTextPanel";
import { ApiErrorAlert } from "@/components/shared/ApiErrorAlert";
import { PageHeader } from "@/components/shared/PageHeader";
import { PageLoading } from "@/components/shared/PageLoading";
import { SectionCard } from "@/components/shared/SectionCard";
import { Button } from "@/components/ui/button";
import { ApiRequestError } from "@/lib/api/client";
import { ocrApi } from "@/lib/api/ocr";
import {
  formatConfidence,
  formatLanguage,
  ocrDocumentTypeLabel,
  ocrProviderLabel,
  ocrRelationTypeLabel,
} from "@/lib/ocr/labels";
import { notifyApiError, notifySuccess } from "@/lib/feedback";
import { formatDateTime } from "@/lib/utils/format-date";
import type { OcrRecordDto } from "@/types/ocr";
import type { SystemRole } from "@/types/staff";

function canEdit(role: SystemRole | null | undefined) {
  return role === "ADMIN" || role === "SENIOR_LAWYER" || role === "LAWYER";
}

function canDelete(role: SystemRole | null | undefined) {
  return role === "ADMIN" || role === "SENIOR_LAWYER";
}

function isProcessing(status: OcrRecordDto["status"]) {
  return status === "PENDING" || status === "PROCESSING";
}

export default function OcrDetailPage() {
  const params = useParams<{ id: string }>();
  const id = params.id;
  const router = useRouter();
  const { user } = useAuth();
  const role = user?.systemRole as SystemRole | undefined;
  const [record, setRecord] = useState<OcrRecordDto | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState<string | null>(null);

  const load = useCallback(async () => {
    setError(null);
    try {
      const data = await ocrApi.getById(id);
      setRecord(data);
      return data;
    } catch (e) {
      setError(
        e instanceof ApiRequestError ? e.message : "تعذّر تحميل سجل OCR",
      );
      setRecord(null);
      return null;
    } finally {
      setLoading(false);
    }
  }, [id]);

  useEffect(() => {
    void load();
  }, [load]);

  useEffect(() => {
    if (!record || !isProcessing(record.status)) return;
    const timer = window.setInterval(() => {
      void load();
    }, 4000);
    return () => window.clearInterval(timer);
  }, [record, load]);

  async function handleDownloadOriginal() {
    if (!record) return;
    setBusy("download");
    try {
      const blob = await ocrApi.downloadOriginal(record.id);
      const url = URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = url;
      a.download = record.originalFilename;
      a.click();
      URL.revokeObjectURL(url);
    } catch (e) {
      notifyApiError(e, "تعذّر تنزيل الملف");
    } finally {
      setBusy(null);
    }
  }

  async function handleProcess(rerun: boolean) {
    if (!record) return;
    setBusy(rerun ? "rerun" : "process");
    try {
      const next = await ocrApi.process(record.id, rerun);
      setRecord(next);
      notifySuccess(rerun ? "تم إعادة تشغيل OCR" : "بدأت المعالجة");
    } catch (e) {
      notifyApiError(e, "تعذّر تشغيل OCR");
    } finally {
      setBusy(null);
    }
  }

  async function handleDelete() {
    if (!record || !window.confirm(`حذف «${record.title}»؟`)) return;
    setBusy("delete");
    try {
      await ocrApi.remove(record.id);
      notifySuccess("تم الحذف");
      router.push("/ocr");
    } catch (e) {
      notifyApiError(e, "تعذّر الحذف");
    } finally {
      setBusy(null);
    }
  }

  if (loading) {
    return <PageLoading label="جارٍ تحميل التفاصيل…" />;
  }

  if (error || !record) {
    return (
      <div className="space-y-4">
        <PageHeader title="تفاصيل OCR" />
        <ApiErrorAlert message={error ?? "غير موجود"} onRetry={() => void load()} />
        <Button asChild variant="outline">
          <Link href="/ocr">العودة</Link>
        </Button>
      </div>
    );
  }

  const displayText =
    record.editedText?.trim() ||
    record.extractedText?.trim() ||
    "";

  return (
    <div className="space-y-6">
      <PageHeader
        title={record.title}
        description={record.originalFilename}
        actions={
          <div className="flex flex-wrap gap-2">
            <Button asChild variant="outline" size="sm">
              <Link href="/ocr">
                <ArrowRight className="size-4" aria-hidden />
                لوحة OCR
              </Link>
            </Button>
            <Button
              type="button"
              variant="outline"
              size="sm"
              disabled={busy != null}
              onClick={() => void handleDownloadOriginal()}
            >
              {busy === "download" ? (
                <Loader2 className="size-4 animate-spin" />
              ) : (
                <Download className="size-4" />
              )}
              تنزيل الأصل
            </Button>
            {canEdit(role) ? (
              <>
                <Button asChild variant="outline" size="sm">
                  <Link href={`/ocr/${record.id}/edit`}>
                    <Pencil className="size-4" />
                    تعديل
                  </Link>
                </Button>
                <Button
                  type="button"
                  size="sm"
                  disabled={busy != null || isProcessing(record.status)}
                  onClick={() =>
                    void handleProcess(
                      record.status === "COMPLETED" ||
                        record.status === "FAILED",
                    )
                  }
                >
                  {busy === "process" || busy === "rerun" ? (
                    <Loader2 className="size-4 animate-spin" />
                  ) : (
                    <RefreshCw className="size-4" />
                  )}
                  {record.status === "COMPLETED" ? "إعادة OCR" : "تشغيل OCR"}
                </Button>
              </>
            ) : null}
            {canDelete(role) ? (
              <Button
                type="button"
                variant="destructive"
                size="sm"
                disabled={busy != null}
                onClick={() => void handleDelete()}
              >
                <Trash2 className="size-4" />
                حذف
              </Button>
            ) : null}
          </div>
        }
      />

      <div className="flex flex-wrap items-center gap-3">
        <OcrStatusBadge status={record.status} />
        {isProcessing(record.status) ? (
          <span className="text-xs text-[var(--color-muted-foreground)]" role="status">
            يتم تحديث الحالة تلقائياً…
          </span>
        ) : null}
      </div>

      {record.errorMessage && record.status === "FAILED" ? (
        <ApiErrorAlert message={record.errorMessage} />
      ) : null}

      <div className="grid gap-6 lg:grid-cols-3">
        <SectionCard title="البيانات" className="lg:col-span-1">
          <dl className="space-y-3 text-sm">
            <MetaRow label="النوع" value={ocrDocumentTypeLabel(record.documentType)} />
            <MetaRow label="اللغة" value={formatLanguage(record.language)} />
            <MetaRow label="الثقة" value={formatConfidence(record.confidenceScore)} />
            <MetaRow label="المزوّد" value={ocrProviderLabel(record.provider)} />
            <MetaRow label="رفع بواسطة" value={record.uploadedBy?.fullName ?? "—"} />
            <MetaRow label="تاريخ الرفع" value={formatDateTime(record.createdAt)} />
            {record.processingCompletedAt ? (
              <MetaRow
                label="اكتملت المعالجة"
                value={formatDateTime(record.processingCompletedAt)}
              />
            ) : null}
          </dl>
          {record.relations?.length ? (
            <div className="mt-4 border-t border-[var(--color-border)] pt-4">
              <p className="mb-2 text-xs font-semibold text-[var(--color-muted-foreground)]">
                الروابط
              </p>
              <ul className="space-y-1 text-sm">
                {record.relations.map((rel) => (
                  <li key={rel.id}>
                    {ocrRelationTypeLabel(rel.relationType)}
                    <span className="ms-2 font-mono text-xs text-[var(--color-muted-foreground)] ltr-nums">
                      {rel.relationId.slice(0, 8)}…
                    </span>
                  </li>
                ))}
              </ul>
            </div>
          ) : null}
        </SectionCard>

        <SectionCard title="النص المستخرج" className="lg:col-span-2">
          {displayText ? (
            <OcrTextPanel
              recordId={record.id}
              text={displayText}
              filename={`${record.title || "ocr"}.txt`}
              readOnly
            />
          ) : (
            <p className="py-8 text-center text-sm text-[var(--color-muted-foreground)]">
              {isProcessing(record.status)
                ? "جارٍ استخراج النص…"
                : "لا يوجد نص مستخرج بعد."}
            </p>
          )}
        </SectionCard>
      </div>
    </div>
  );
}

function MetaRow({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex items-start justify-between gap-3">
      <dt className="text-[var(--color-muted-foreground)]">{label}</dt>
      <dd className="text-end font-medium">{value}</dd>
    </div>
  );
}
