"use client";

import { useEffect, useMemo, useState } from "react";
import { CalendarClock, FileText, History, Loader2, Shuffle } from "lucide-react";

import { ApiErrorAlert } from "@/components/shared/ApiErrorAlert";
import { EmptyState } from "@/components/shared/EmptyState";
import { SectionCard } from "@/components/shared/SectionCard";
import { ApiRequestError } from "@/lib/api/client";
import { caseHistoryApi } from "@/lib/api/case-history";
import { formatDateTime } from "@/lib/utils/format-date";
import type { CaseHistoryEntry } from "@/types/case-history";

function parseMetadata(raw: string | null): Record<string, unknown> {
  if (!raw) return {};
  try {
    const parsed = JSON.parse(raw);
    return parsed && typeof parsed === "object" && !Array.isArray(parsed)
      ? (parsed as Record<string, unknown>)
      : {};
  } catch {
    return {};
  }
}

function readString(meta: Record<string, unknown>, key: string) {
  const value = meta[key];
  return typeof value === "string" && value.trim() ? value : null;
}

const STATUS_LABELS: Record<string, string> = {
  NEW: "جديدة",
  IN_PROGRESS: "قيد العمل",
  ON_HOLD: "معلقة",
  CLOSED: "مغلقة",
  ARCHIVED: "مؤرشفة",
};

function eventMeta(entry: CaseHistoryEntry) {
  const meta = parseMetadata(entry.metadata);
  if (entry.action === "CASE_STATUS_CHANGED") {
    const next = readString(meta, "new");
    return {
      icon: Shuffle,
      title: "تغيرت حالة القضية",
      detail: next ? `الحالة الآن: ${STATUS_LABELS[next] ?? next}` : null,
    };
  }
  if (entry.action === "HEARING_CREATED" || entry.action === "HEARING_UPDATED") {
    const date = readString(meta, "hearingDate");
    return {
      icon: CalendarClock,
      title: entry.action === "HEARING_CREATED" ? "تمت إضافة جلسة" : "تم تحديث جلسة",
      detail: date ? `موعد الجلسة: ${formatDateTime(date)}` : null,
    };
  }
  if (entry.action === "DOCUMENT_UPLOADED") {
    const filename = readString(meta, "filename");
    return {
      icon: FileText,
      title: "تمت إضافة مستند",
      detail: filename,
    };
  }
  if (entry.action === "CASE_CREATED") {
    return { icon: History, title: "تم فتح ملف القضية", detail: null };
  }
  return { icon: History, title: "تحديث على القضية", detail: null };
}

type Props = {
  caseId: string;
  reloadKey?: number;
};

export function ClientCaseUpdates({ caseId, reloadKey }: Props) {
  const [items, setItems] = useState<CaseHistoryEntry[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [fetchId, setFetchId] = useState(0);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      setLoading(true);
      setError(null);
      try {
        const res = await caseHistoryApi.list(caseId, { page: 0, size: 20 });
        if (cancelled) return;
        setItems(res.content);
      } catch (e) {
        if (cancelled) return;
        setError(
          e instanceof ApiRequestError
            ? e.details?.message ?? e.message
            : "تعذر تحميل تحديثات القضية",
        );
        setItems([]);
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [caseId, reloadKey, fetchId]);

  const rows = useMemo(
    () => items.map((entry) => ({ entry, meta: eventMeta(entry) })),
    [items],
  );

  return (
    <SectionCard
      title="آخر تحديثات القضية"
      description="الأحداث المسموح بعرضها لك من المكتب، الأحدث أولا."
      action={
        <div className="flex size-10 items-center justify-center rounded-xl bg-[var(--color-gold-50)] text-[var(--color-gold-700)]">
          <History className="size-5" />
        </div>
      }
    >
      {loading ? (
        <div className="flex items-center justify-center py-8 text-sm text-[var(--color-muted-foreground)]">
          <Loader2 className="me-2 size-4 animate-spin" />
          جاري تحميل التحديثات...
        </div>
      ) : error ? (
        <ApiErrorAlert
          title="تعذر تحميل التحديثات"
          message={error}
          onRetry={() => setFetchId((n) => n + 1)}
        />
      ) : rows.length === 0 ? (
        <EmptyState
          icon={History}
          title="لا توجد تحديثات بعد"
          description="ستظهر هنا التغييرات المهمة التي يتيحها المكتب للعميل."
        />
      ) : (
        <div className="space-y-3">
          {rows.map(({ entry, meta }) => {
            const Icon = meta.icon;
            return (
              <div
                key={entry.id}
                className="flex gap-3 rounded-xl border border-[var(--color-border)] bg-[var(--color-surface)] p-4"
              >
                <div className="flex size-9 shrink-0 items-center justify-center rounded-xl bg-[var(--color-navy-50)] text-[var(--color-navy-700)]">
                  <Icon className="size-4" />
                </div>
                <div className="min-w-0">
                  <p className="font-medium text-[var(--color-foreground)]">
                    {meta.title}
                  </p>
                  {meta.detail ? (
                    <p className="mt-1 text-sm text-[var(--color-muted-foreground)]">
                      {meta.detail}
                    </p>
                  ) : null}
                  <p className="mt-1 text-xs text-[var(--color-muted-foreground)] ltr-nums">
                    {formatDateTime(entry.createdAt)}
                  </p>
                </div>
              </div>
            );
          })}
        </div>
      )}
    </SectionCard>
  );
}
