"use client";

import { useEffect, useMemo, useState } from "react";
import {
  Archive,
  ArchiveRestore,
  CalendarClock,
  CheckCircle2,
  Clock,
  Edit,
  FilePlus2,
  History,
  Loader2,
  Search,
  Shuffle,
  UserPlus2,
  Users,
  XCircle,
  type LucideIcon,
} from "lucide-react";

import { ApiErrorAlert } from "@/components/shared/ApiErrorAlert";
import { EmptyState } from "@/components/shared/EmptyState";
import { SectionCard } from "@/components/shared/SectionCard";
import { Input } from "@/components/ui/input";
import { Button } from "@/components/ui/button";
import { caseHistoryApi } from "@/lib/api/case-history";
import { ApiRequestError } from "@/lib/api/client";
import { formatDateTime } from "@/lib/utils/format-date";
import { cn } from "@/lib/utils/cn";
import type { CaseHistoryEntry } from "@/types/case-history";

// -----------------------------------------------------------------------------
// Action catalogue
//
// `action` values come from `AuditAction` in kalima-backend. We keep a small
// presentational map here (label, icon, tone). Unknown actions still render —
// they just fall back to a generic "حدث" row so new backend actions never
// break the UI.
// -----------------------------------------------------------------------------
type Tone = "navy" | "gold" | "success" | "danger" | "muted";

type ActionMeta = {
  label: string;
  icon: LucideIcon;
  tone: Tone;
};

const ACTION_META: Record<string, ActionMeta> = {
  CASE_CREATED: { label: "تم إنشاء القضية", icon: FilePlus2, tone: "navy" },
  CASE_UPDATED: { label: "تم تعديل بيانات القضية", icon: Edit, tone: "navy" },
  CASE_STATUS_CHANGED: {
    label: "تم تغيير حالة القضية",
    icon: Shuffle,
    tone: "gold",
  },
  CASE_ARCHIVED: { label: "تم أرشفة القضية", icon: Archive, tone: "danger" },
  CASE_RESTORED: {
    label: "تم استرجاع القضية",
    icon: ArchiveRestore,
    tone: "success",
  },

  PARTICIPANT_ADDED: { label: "إضافة مشارك", icon: UserPlus2, tone: "navy" },
  PARTICIPANT_REMOVED: { label: "إزالة مشارك", icon: Users, tone: "danger" },

  HEARING_CREATED: { label: "تم إضافة جلسة", icon: CalendarClock, tone: "navy" },
  HEARING_UPDATED: { label: "تم تعديل جلسة", icon: Edit, tone: "navy" },

  TASK_CREATED: { label: "تم إضافة مهمة", icon: FilePlus2, tone: "navy" },
  TASK_UPDATED: { label: "تم تعديل مهمة", icon: Edit, tone: "navy" },
  DOCUMENT_UPLOADED: { label: "تم رفع مستند", icon: FilePlus2, tone: "navy" },
  DOCUMENT_DELETED: { label: "تم حذف مستند", icon: Archive, tone: "danger" },
};

function resolveAction(action: string): ActionMeta {
  return (
    ACTION_META[action] ?? {
      label: action,
      icon: History,
      tone: "muted",
    }
  );
}

// -----------------------------------------------------------------------------
// Metadata helpers — parse the JSON string defensively and derive a short
// human-readable detail line per entry.
// -----------------------------------------------------------------------------
function parseMetadata(raw: string | null | undefined): Record<string, unknown> | null {
  if (!raw) return null;
  try {
    const parsed = JSON.parse(raw);
    return parsed && typeof parsed === "object" && !Array.isArray(parsed)
      ? (parsed as Record<string, unknown>)
      : null;
  } catch {
    return null;
  }
}

const STATUS_AR: Record<string, string> = {
  NEW: "جديد",
  IN_PROGRESS: "قيد المعالجة",
  ON_HOLD: "معلّق",
  CLOSED: "مُغلق",
  ARCHIVED: "مؤرشف",
  TODO: "قيد الانتظار",
  DONE: "منجز",
  CANCELLED: "ملغاة",
};

function statusLabel(v: unknown): string {
  if (typeof v !== "string") return "";
  return STATUS_AR[v] ?? v;
}

function readString(meta: Record<string, unknown> | null, key: string): string | null {
  if (!meta) return null;
  const v = meta[key];
  return typeof v === "string" && v.trim() !== "" ? v : null;
}

function buildDetail(entry: CaseHistoryEntry): string | null {
  const meta = parseMetadata(entry.metadata);

  switch (entry.action) {
    case "CASE_STATUS_CHANGED": {
      const oldV = readString(meta, "old");
      const newV = readString(meta, "new");
      if (oldV && newV) {
        return `${statusLabel(oldV)} ← ${statusLabel(newV)}`;
      }
      return null;
    }
    case "HEARING_CREATED":
    case "HEARING_UPDATED": {
      const d = readString(meta, "hearingDate");
      return d ? `تاريخ الجلسة: ${formatDateTime(d)}` : null;
    }
    case "TASK_CREATED":
    case "TASK_UPDATED": {
      const title = readString(meta, "title");
      const status = readString(meta, "status");
      const parts = [
        title,
        status ? `الحالة: ${statusLabel(status)}` : null,
      ].filter(Boolean);
      return parts.length ? parts.join(" · ") : null;
    }
    case "DOCUMENT_UPLOADED":
    case "DOCUMENT_DELETED": {
      const filename = readString(meta, "filename");
      const category = readString(meta, "category");
      const parts = [
        filename,
        category ? `التصنيف: ${category}` : null,
      ].filter(Boolean);
      return parts.length ? parts.join(" · ") : null;
    }
    case "PARTICIPANT_ADDED":
    case "PARTICIPANT_REMOVED": {
      const role = readString(meta, "role");
      return role ? `الدور: ${role}` : null;
    }
    case "CASE_UPDATED": {
      const oldL = readString(meta, "oldLawyerId");
      const newL = readString(meta, "newLawyerId");
      if ((oldL ?? "") !== (newL ?? "")) {
        return "تم تغيير المحامي المسؤول";
      }
      return null;
    }
    default:
      return null;
  }
}

// -----------------------------------------------------------------------------
// Tone-to-classes. Mirrors the palette tokens used elsewhere in the app so
// dark/light themes and brand colours stay consistent.
// -----------------------------------------------------------------------------
const TONE_CLASSES: Record<Tone, { icon: string; ring: string }> = {
  navy: {
    icon: "bg-[var(--color-navy-50)] text-[var(--color-navy-700)]",
    ring: "ring-[var(--color-navy-100)]",
  },
  gold: {
    icon: "bg-[var(--color-gold-50)] text-[var(--color-gold-700)]",
    ring: "ring-[var(--color-gold-100)]",
  },
  success: {
    icon: "bg-[var(--color-success-bg)] text-[var(--color-success)]",
    ring: "ring-[var(--color-success-bg)]",
  },
  danger: {
    icon: "bg-[var(--color-danger)]/10 text-[var(--color-danger)]",
    ring: "ring-[var(--color-danger)]/20",
  },
  muted: {
    icon: "bg-[var(--color-surface-muted)] text-[var(--color-muted-foreground)]",
    ring: "ring-[var(--color-border)]",
  },
};

// -----------------------------------------------------------------------------

const PAGE_SIZE = 50;

function entrySearchBlob(
  entry: CaseHistoryEntry,
  meta: ActionMeta,
  detail: string | null,
): string {
  const actor = entry.actorName ?? entry.actorEmail ?? "";
  const parts = [
    meta.label,
    entry.action,
    detail ?? "",
    actor,
    formatDateTime(entry.createdAt),
  ];
  return parts.join(" ").toLowerCase();
}

function matchesSearch(
  q: string,
  entry: CaseHistoryEntry,
  meta: ActionMeta,
  detail: string | null,
): boolean {
  const t = q.trim().toLowerCase();
  if (!t) return true;
  return entrySearchBlob(entry, meta, detail).includes(t);
}

type Props = {
  caseId: string;
  /**
   * Optional monotonically increasing value. When it changes, the timeline
   * refetches so it stays in sync with mutations in the parent page (hearing
   * added, task updated, status changed, etc.).
   */
  reloadKey?: number;
};

export function CaseHistoryTimeline({ caseId, reloadKey }: Props) {
  const [entries, setEntries] = useState<CaseHistoryEntry[]>([]);
  const [totalPages, setTotalPages] = useState(0);
  const [loadedThroughPage, setLoadedThroughPage] = useState(-1);
  const [loading, setLoading] = useState(true);
  const [loadingMore, setLoadingMore] = useState(false);
  const [loadMoreError, setLoadMoreError] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [fetchId, setFetchId] = useState(0);
  const [searchQuery, setSearchQuery] = useState("");

  useEffect(() => {
    if (!caseId) return;
    let cancelled = false;
    setSearchQuery("");
    (async () => {
      setLoading(true);
      setError(null);
      setLoadMoreError(null);
      setEntries([]);
      setLoadedThroughPage(-1);
      setTotalPages(0);
      try {
        const res = await caseHistoryApi.list(caseId, { page: 0, size: PAGE_SIZE });
        if (cancelled) return;
        setEntries(res.content);
        setTotalPages(res.totalPages);
        setLoadedThroughPage(0);
      } catch (e) {
        if (cancelled) return;
        if (e instanceof ApiRequestError) {
          setError(e.details?.message ?? e.message);
        } else {
          setError("تعذر تحميل سجل القضية");
        }
        setEntries([]);
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [caseId, reloadKey, fetchId]);

  const rows = useMemo(
    () =>
      entries.map((e) => {
        const meta = resolveAction(e.action);
        const detail = buildDetail(e);
        return { entry: e, meta, detail };
      }),
    [entries],
  );

  const filteredRows = useMemo(
    () =>
      rows.filter((r) =>
        matchesSearch(searchQuery, r.entry, r.meta, r.detail),
      ),
    [rows, searchQuery],
  );

  const hasMore =
    loadedThroughPage >= 0 && totalPages > loadedThroughPage + 1;

  const loadMore = async () => {
    if (!caseId || !hasMore || loadingMore) return;
    const nextPage = loadedThroughPage + 1;
    setLoadingMore(true);
    setLoadMoreError(null);
    try {
      const res = await caseHistoryApi.list(caseId, {
        page: nextPage,
        size: PAGE_SIZE,
      });
      setEntries((prev) => {
        const seen = new Set(prev.map((e) => e.id));
        const merged = [...prev];
        for (const e of res.content) {
          if (!seen.has(e.id)) {
            seen.add(e.id);
            merged.push(e);
          }
        }
        return merged;
      });
      setLoadedThroughPage(nextPage);
      setTotalPages(res.totalPages);
    } catch (e) {
      if (e instanceof ApiRequestError) {
        setLoadMoreError(e.details?.message ?? e.message);
      } else {
        setLoadMoreError("تعذر تحميل المزيد");
      }
    } finally {
      setLoadingMore(false);
    }
  };

  return (
    <SectionCard
      title="السجل"
      description="كل ما حدث لهذه القضية من بداية فتحها — مستخلص من سجل التدقيق (audit logs)، الأحدث أولاً."
      action={
        <div className="flex size-10 items-center justify-center rounded-xl bg-[var(--color-navy-50)] text-[var(--color-navy-700)]">
          <History className="size-5" />
        </div>
      }
    >
      {!loading && !error ? (
        <div className="mb-4 px-1">
          <label className="sr-only" htmlFor="case-history-search">
            بحث في السجل المحمّل
          </label>
          <div className="relative">
            <Search className="pointer-events-none absolute start-3 top-1/2 size-4 -translate-y-1/2 text-[var(--color-muted-foreground)]" />
            <Input
              id="case-history-search"
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              placeholder="بحث في السجل المحمّل…"
              className="ps-10"
              autoComplete="off"
            />
          </div>
        </div>
      ) : null}

      {loading ? (
        <div className="flex items-center justify-center py-10 text-[var(--color-muted-foreground)]">
          <Loader2 className="size-5 animate-spin" />
          <span className="ms-2 text-sm">جارٍ تحميل السجل…</span>
        </div>
      ) : error ? (
        <ApiErrorAlert
          message={error}
          title="تعذر تحميل السجل"
          onRetry={() => setFetchId((n) => n + 1)}
        />
      ) : rows.length === 0 ? (
        <EmptyState
          icon={History}
          title="لا يوجد سجل لهذه القضية بعد"
          description="سيظهر هنا أي تغيير يحدث على القضية أو جلساتها أو مهامها أو مشاركيها."
        />
      ) : filteredRows.length === 0 ? (
        <p className="py-8 text-center text-sm text-[var(--color-muted-foreground)]">
          لا توجد نتائج تطابق البحث في السجل المحمّل.
        </p>
      ) : (
        <ol className="relative space-y-4 ps-6" aria-label="السجل الزمني">
          <span
            aria-hidden
            className="pointer-events-none absolute inset-y-2 start-[0.9rem] w-px bg-[var(--color-border)]"
          />
          {filteredRows.map(({ entry, meta, detail }) => {
            const Icon = meta.icon;
            const tone = TONE_CLASSES[meta.tone];
            const actor = entry.actorName ?? entry.actorEmail ?? "—";
            return (
              <li key={entry.id} className="relative">
                <span
                  aria-hidden
                  className={cn(
                    "absolute -start-6 top-1 flex size-8 items-center justify-center rounded-full ring-4",
                    tone.icon,
                    tone.ring,
                  )}
                >
                  <Icon className="size-4" />
                </span>
                <div className="flex flex-col gap-1 rounded-xl border border-[var(--color-border)] bg-[var(--color-surface)] px-4 py-3 shadow-[var(--shadow-soft)]">
                  <div className="flex flex-wrap items-center gap-x-3 gap-y-1">
                    <span className="text-sm font-semibold text-[var(--color-foreground)]">
                      {meta.label}
                    </span>
                    {entry.success ? null : (
                      <span className="inline-flex items-center gap-1 rounded-full bg-[var(--color-danger)]/10 px-2 py-0.5 text-[10px] font-medium text-[var(--color-danger)]">
                        <XCircle className="size-3" />
                        فشل
                      </span>
                    )}
                    {entry.success && entry.action.endsWith("_CREATED") ? (
                      <span className="inline-flex items-center gap-1 rounded-full bg-[var(--color-success-bg)] px-2 py-0.5 text-[10px] font-medium text-[var(--color-success)]">
                        <CheckCircle2 className="size-3" />
                        جديد
                      </span>
                    ) : null}
                  </div>
                  {detail ? (
                    <p className="text-xs text-[var(--color-muted-foreground)]">
                      {detail}
                    </p>
                  ) : null}
                  <div className="flex flex-wrap items-center gap-x-3 gap-y-1 text-xs text-[var(--color-muted-foreground)]">
                    <span>بواسطة {actor}</span>
                    <span aria-hidden>·</span>
                    <span className="inline-flex items-center gap-1 ltr-nums">
                      <Clock className="size-3.5" />
                      {formatDateTime(entry.createdAt)}
                    </span>
                  </div>
                </div>
              </li>
            );
          })}
        </ol>
      )}

      {!loading && !error && rows.length > 0 && hasMore ? (
        <div className="mt-6 flex flex-col items-stretch gap-2 border-t border-[var(--color-border)] pt-4 sm:items-start">
          <Button
            type="button"
            variant="outline"
            size="sm"
            disabled={loadingMore}
            onClick={() => void loadMore()}
            className="gap-2"
          >
            {loadingMore ? (
              <Loader2 className="size-4 animate-spin" />
            ) : null}
            تحميل المزيد
          </Button>
          {loadMoreError ? (
            <p className="text-xs text-[var(--color-danger)]">{loadMoreError}</p>
          ) : null}
        </div>
      ) : null}
    </SectionCard>
  );
}
