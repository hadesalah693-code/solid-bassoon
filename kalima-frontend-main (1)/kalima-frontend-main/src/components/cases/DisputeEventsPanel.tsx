"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { Loader2, Pencil, Scale, Trash2 } from "lucide-react";

import { DisputeEventsTimeline } from "@/components/cases/DisputeEventsTimeline";
import { ApiErrorAlert } from "@/components/shared/ApiErrorAlert";
import { EmptyState } from "@/components/shared/EmptyState";
import { PageLoading } from "@/components/shared/PageLoading";
import { DisputeEventStatusBadge } from "@/components/shared/StatusBadge";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { ApiRequestError } from "@/lib/api/client";
import { disputeEventsApi } from "@/lib/api/disputeEvents";
import { getStoredUser } from "@/lib/auth/session";
import { notifyApiError, notifyError, notifySuccess } from "@/lib/feedback";
import { cn } from "@/lib/utils/cn";
import { localInputToApiDateTime } from "@/lib/utils/datetime-api";
import { formatDate, formatDateTime } from "@/lib/utils/format-date";
import type { SystemRole } from "@/types/staff";
import type {
  DisputeEventPatchRequestDto,
  DisputeEventResponseDto,
  DisputeEventStatusDto,
  DisputeEventTypeDto,
} from "@/types/api";

import {
  compareDisputeEvents,
  DISPUTE_EVENT_STATUS_AR as STATUS_AR,
  DISPUTE_EVENT_TYPE_AR as TYPE_AR,
} from "@/lib/cases/dispute-events";

const DISPUTE_EVENT_WRITE_ROLES: SystemRole[] = [
  "ADMIN",
  "SENIOR_LAWYER",
  "LAWYER",
  "SECRETARY",
];

const TYPE_ORDER: DisputeEventTypeDto[] = [
  "CASE_OPENED",
  "WARNING_NOTICE",
  "CLAIM_FILED",
  "FIRST_HEARING",
  "MEMO_EXCHANGE",
  "JUDGMENT",
  "APPEAL",
  "OTHER",
];

const STATUS_VALUES: DisputeEventStatusDto[] = [
  "DONE",
  "CURRENT",
  "UPCOMING",
  "CANCELLED",
];

const selectClassName = cn(
  "flex h-10 w-full cursor-pointer appearance-none rounded-xl border border-[var(--color-border)] bg-[var(--color-surface)] px-3.5 py-2 text-sm",
  "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--color-gold-400)]",
  "disabled:cursor-not-allowed disabled:opacity-50",
);

function eventDateDisplay(value: string | null | undefined): string {
  if (!value) return "—";
  return value.includes("T") ? formatDateTime(value) : formatDate(value);
}

function toDateTimeLocal(value: string | null | undefined): string {
  if (!value) return "";
  return value.length >= 16 ? value.slice(0, 16) : value;
}

type PanelProps = {
  caseId: string;
  archived: boolean;
};

export function DisputeEventsPanel({ caseId, archived }: PanelProps) {
  const [items, setItems] = useState<DisputeEventResponseDto[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [reloadKey, setReloadKey] = useState(0);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [editing, setEditing] = useState(false);

  const [role, setRole] = useState<SystemRole | null>(null);

  useEffect(() => {
    const u = getStoredUser();
    setRole(u?.systemRole ?? null);
  }, []);

  const canMutate = Boolean(
    !archived && role && DISPUTE_EVENT_WRITE_ROLES.includes(role),
  );

  const sorted = useMemo(
    () => [...items].sort(compareDisputeEvents),
    [items],
  );

  useEffect(() => {
    if (sorted.length === 0) {
      setSelectedId(null);
      setEditing(false);
      return;
    }
    const exists = Boolean(
      selectedId && sorted.some((e) => e.id === selectedId),
    );
    if (exists) return;
    const current = sorted.find((e) => e.status === "CURRENT");
    const pick = current ?? sorted[0]!;
    setSelectedId(pick.id);
    setEditing(false);
  }, [sorted, selectedId]);

  const nextSortSuggestion = useMemo(() => {
    if (sorted.length === 0) return 0;
    return Math.max(...sorted.map((e) => e.sortOrder)) + 1;
  }, [sorted]);

  const selectedEvent = useMemo(
    () => sorted.find((e) => e.id === selectedId) ?? null,
    [sorted, selectedId],
  );

  function selectNode(id: string) {
    setEditing(false);
    setSelectedId(id);
  }

  const refresh = useCallback(() => {
    setReloadKey((n) => n + 1);
  }, []);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      setLoading(true);
      setError(null);
      try {
        const page = await disputeEventsApi.list(caseId, {
          page: 0,
          size: 200,
        });
        if (cancelled) return;
        setItems(page.content);
      } catch (e) {
        if (cancelled) return;
        if (e instanceof ApiRequestError) {
          setError(e.details?.message ?? e.message);
        } else {
          setError("تعذر تحميل مسار النزاع");
        }
        setItems([]);
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [caseId, reloadKey]);

  if (loading && items.length === 0 && !error) {
    return (
      <PageLoading
        label="جارٍ تحميل مسار النزاع…"
        minHeightClassName="min-h-[12rem]"
        className="w-full py-8"
      />
    );
  }

  return (
    <section className="space-y-5">
      {error ? (
        <ApiErrorAlert
          message={error}
          title="تعذر تحميل مراحل النزاع"
          onRetry={refresh}
        />
      ) : null}

      <p className="text-sm text-[var(--color-muted-foreground)]">
        جدولة زمنية لمراحل المسار الإجرائي؛ اختر مرحلة لعرض التفاصيل وتعديلها
        (حسب الصلاحيات).
      </p>

      {canMutate ? (
        <DisputeEventAddForm
          caseId={caseId}
          defaultSortOrder={nextSortSuggestion}
          onCreated={(row) => {
            setItems((prev) => [...prev, row]);
            setSelectedId(row.id);
            setEditing(false);
            notifySuccess("تمت إضافة المرحلة");
          }}
        />
      ) : null}

      {sorted.length === 0 && !loading ? (
        <EmptyState
          title="لا توجد مراحل في مسار النزاع حتى الآن"
          description={
            canMutate
              ? "يمكنك إضافة مرحلة يدويًا أو اختيار مسار قانوني عند إنشاء القضية لتوليد المراحل تلقائيًا."
              : "لم تُعرّف أي مراحل لهذا الملف بعد؛ يمكن للفريق إضافتها أو توليدها عند إعداد القضية."
          }
          icon={Scale}
        />
      ) : (
        <>
          <DisputeEventsTimeline
            events={sorted}
            selectedId={selectedId}
            onSelect={selectNode}
            labels={{ typeAr: TYPE_AR }}
          />

          {selectedEvent ? (
            editing ? (
              <DisputeEventEditForm
                caseId={caseId}
                event={selectedEvent}
                selectClassName={selectClassName}
                onSaved={(next) => {
                  setItems((arr) =>
                    arr.map((x) => (x.id === next.id ? next : x)),
                  );
                  notifySuccess("تم حفظ التعديلات");
                  setEditing(false);
                }}
                onCancel={() => setEditing(false)}
              />
            ) : (
              <DisputeEventDetailCard
                event={selectedEvent}
                canMutate={canMutate}
                onEdit={() => setEditing(true)}
                onDeleted={(id) => {
                  setItems((arr) => arr.filter((x) => x.id !== id));
                  notifySuccess("تم حذف المرحلة");
                }}
                caseId={caseId}
              />
            )
          ) : null}
        </>
      )}
    </section>
  );
}

function DisputeEventAddForm(props: {
  caseId: string;
  defaultSortOrder: number;
  onCreated: (row: DisputeEventResponseDto) => void;
}) {
  const { caseId, defaultSortOrder, onCreated } = props;
  const [title, setTitle] = useState("");
  const [type, setType] =
    useState<DisputeEventTypeDto>("CASE_OPENED");
  const [status, setStatus] =
    useState<DisputeEventStatusDto>("UPCOMING");
  const [summary, setSummary] = useState("");
  const [eventDate, setEventDate] = useState("");
  const [sortOrderStr, setSortOrderStr] = useState("");
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    setSortOrderStr(String(defaultSortOrder));
  }, [defaultSortOrder]);

  async function submit() {
    const t = title.trim();
    if (!t) {
      notifyError("أدخل عنوان المرحلة");
      return;
    }
    const sortParsed = parseInt(sortOrderStr.trim(), 10);
    if (Number.isNaN(sortParsed)) {
      notifyError("أدخل ترتيبًا رقميًا صالحًا");
      return;
    }
    setSaving(true);
    try {
      const created = await disputeEventsApi.create(caseId, {
        type,
        status,
        title: t,
        summary: summary.trim() || null,
        eventDate: eventDate.trim()
          ? localInputToApiDateTime(eventDate)
          : null,
        sortOrder: sortParsed,
      });
      setTitle("");
      setSummary("");
      setEventDate("");
      setType("CASE_OPENED");
      setStatus("UPCOMING");
      onCreated(created);
    } catch (e) {
      notifyApiError(e, "تعذر إضافة المرحلة");
    } finally {
      setSaving(false);
    }
  }

  return (
    <div className="space-y-3 rounded-xl border border-[var(--color-border)] bg-[var(--color-surface-muted)]/30 p-4">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <p className="text-sm font-semibold text-[var(--color-foreground)]">
          إضافة مرحلة
        </p>
        <span className="text-[11px] text-[var(--color-muted-foreground)]">
          مسار النزاع
        </span>
      </div>
      <div className="grid gap-3 sm:grid-cols-2">
        <div className="space-y-1 sm:col-span-2">
          <Label htmlFor="de-new-title">عنوان المرحلة *</Label>
          <Input
            id="de-new-title"
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            disabled={saving}
            placeholder="مثال: تقديم الدعوى"
          />
        </div>
        <div className="space-y-1">
          <Label>نوع المرحلة</Label>
          <select
            className={selectClassName}
            value={type}
            onChange={(e) =>
              setType(e.target.value as DisputeEventTypeDto)
            }
            disabled={saving}
          >
            {TYPE_ORDER.map((k) => (
              <option key={k} value={k}>
                {TYPE_AR[k]}
              </option>
            ))}
          </select>
        </div>
        <div className="space-y-1">
          <Label>الحالة</Label>
          <select
            className={selectClassName}
            value={status}
            onChange={(e) =>
              setStatus(e.target.value as DisputeEventStatusDto)
            }
            disabled={saving}
          >
            {STATUS_VALUES.map((s) => (
              <option key={s} value={s}>
                {STATUS_AR[s]}
              </option>
            ))}
          </select>
        </div>
        <div className="space-y-1">
          <Label htmlFor="de-new-sort">ترتيب العرض</Label>
          <Input
            id="de-new-sort"
            type="number"
            inputMode="numeric"
            value={sortOrderStr}
            onChange={(e) => setSortOrderStr(e.target.value)}
            disabled={saving}
            className="ltr-nums"
          />
        </div>
        <div className="space-y-1">
          <Label htmlFor="de-new-date">تاريخ / وقت (اختياري)</Label>
          <Input
            id="de-new-date"
            type="datetime-local"
            value={eventDate}
            onChange={(e) => setEventDate(e.target.value)}
            disabled={saving}
          />
        </div>
        <div className="space-y-1 sm:col-span-2">
          <Label htmlFor="de-new-summary">ملخص (اختياري)</Label>
          <Textarea
            id="de-new-summary"
            value={summary}
            onChange={(e) => setSummary(e.target.value)}
            disabled={saving}
            rows={2}
            className="resize-y"
          />
        </div>
      </div>
      <Button
        type="button"
        variant="gold"
        size="sm"
        disabled={saving}
        onClick={() => void submit()}
        className="gap-2"
      >
        {saving ? (
          <>
            <Loader2 className="size-4 shrink-0 animate-spin" />
            جاري الحفظ
          </>
        ) : (
          "إضافة مرحلة"
        )}
      </Button>
    </div>
  );
}

function DisputeEventDetailCard(props: {
  caseId: string;
  event: DisputeEventResponseDto;
  canMutate: boolean;
  onEdit: () => void;
  onDeleted: (id: string) => void;
}) {
  const { caseId, event, canMutate, onEdit, onDeleted } = props;
  const [deleting, setDeleting] = useState(false);

  async function remove() {
    if (
      !window.confirm(
        `حذف المرحلة «${event.title}» من مسار النزاع؟ لا يمكن التراجع.`,
      )
    ) {
      return;
    }
    setDeleting(true);
    try {
      await disputeEventsApi.remove(caseId, event.id);
      onDeleted(event.id);
    } catch (e) {
      notifyApiError(e, "تعذر حذف المرحلة");
    } finally {
      setDeleting(false);
    }
  }

  return (
    <Card
      className={cn(
        "border-[var(--color-border)] p-4 shadow-[var(--shadow-soft)]",
        event.status === "CURRENT" &&
          "border-[var(--color-gold-400)]/50 bg-[var(--color-navy-50)]/40 ring-1 ring-[var(--color-gold-400)]/20",
      )}
    >
      <div className="mb-3 flex flex-wrap items-start justify-between gap-3 border-b border-[var(--color-border)]/80 pb-3">
        <div>
          <p className="text-[11px] font-semibold uppercase tracking-wide text-[var(--color-muted-foreground)]">
            تفاصيل المرحلة المحددة
          </p>
          <h4 className="mt-1 text-lg font-bold text-[var(--color-foreground)]">
            {event.title}
          </h4>
        </div>
        {canMutate ? (
          <div className="flex flex-wrap gap-2">
            <Button type="button" variant="outline" size="sm" onClick={onEdit}>
              <Pencil className="size-4" />
              تعديل
            </Button>
            <Button
              type="button"
              variant="ghost"
              size="sm"
              className="text-[var(--color-danger)] hover:text-[var(--color-danger)]"
              onClick={() => void remove()}
              disabled={deleting}
            >
              {deleting ? (
                <Loader2 className="size-4 animate-spin" />
              ) : (
                <Trash2 className="size-4" />
              )}
              حذف
            </Button>
          </div>
        ) : null}
      </div>

      <dl className="grid gap-3 text-sm sm:grid-cols-2">
        <div>
          <dt className="text-[var(--color-muted-foreground)]">نوع المرحلة</dt>
          <dd className="font-medium">{TYPE_AR[event.type]}</dd>
        </div>
        <div>
          <dt className="text-[var(--color-muted-foreground)]">الحالة</dt>
          <dd className="flex flex-wrap items-center gap-2">
            <DisputeEventStatusBadge value={event.status} />
            <span className="text-xs text-[var(--color-muted-foreground)]">
              {STATUS_AR[event.status]}
            </span>
          </dd>
        </div>
        <div>
          <dt className="text-[var(--color-muted-foreground)]">التاريخ</dt>
          <dd className="ltr-nums font-mono text-[var(--color-foreground)]">
            {eventDateDisplay(event.eventDate)}
          </dd>
        </div>
        <div>
          <dt className="text-[var(--color-muted-foreground)]">ترتيب العرض</dt>
          <dd className="ltr-nums">{event.sortOrder}</dd>
        </div>
        <div className="sm:col-span-2">
          <dt className="text-[var(--color-muted-foreground)]">تاريخ الإنشاء</dt>
          <dd className="ltr-nums font-mono text-xs text-[var(--color-foreground)]">
            {formatDateTime(event.createdAt)}
          </dd>
        </div>
        {event.summary ? (
          <div className="sm:col-span-2">
            <dt className="text-[var(--color-muted-foreground)]">ملخص</dt>
            <dd className="mt-1 leading-relaxed text-[var(--color-foreground)]">
              {event.summary}
            </dd>
          </div>
        ) : null}
      </dl>
    </Card>
  );
}

function DisputeEventEditForm(props: {
  caseId: string;
  event: DisputeEventResponseDto;
  selectClassName: string;
  onSaved: (next: DisputeEventResponseDto) => void;
  onCancel: () => void;
}) {
  const { caseId, event, selectClassName, onSaved, onCancel } = props;

  const [saving, setSaving] = useState(false);

  const [title, setTitle] = useState(event.title);
  const [type, setType] = useState(event.type);
  const [status, setStatus] = useState(event.status);
  const [summary, setSummary] = useState(event.summary ?? "");
  const [eventDate, setEventDate] = useState(
    toDateTimeLocal(event.eventDate),
  );
  const [sortOrderStr, setSortOrderStr] = useState(String(event.sortOrder));

  useEffect(() => {
    setTitle(event.title);
    setType(event.type);
    setStatus(event.status);
    setSummary(event.summary ?? "");
    setEventDate(toDateTimeLocal(event.eventDate));
    setSortOrderStr(String(event.sortOrder));
  }, [
    event.id,
    event.updatedAt,
    event.title,
    event.type,
    event.status,
    event.summary,
    event.eventDate,
    event.sortOrder,
  ]);

  async function submit() {
    const t = title.trim();
    if (!t) {
      notifyError("أدخل عنوان المرحلة");
      return;
    }
    const sortParsed = parseInt(sortOrderStr.trim(), 10);
    if (Number.isNaN(sortParsed)) {
      notifyError("أدخل ترتيبًا رقميًا صالحًا");
      return;
    }
    const body: DisputeEventPatchRequestDto = {
      type,
      status,
      title: t,
      summary: summary.trim() || null,
      eventDate: eventDate.trim()
        ? localInputToApiDateTime(eventDate)
        : null,
      sortOrder: sortParsed,
    };
    setSaving(true);
    try {
      const updated = await disputeEventsApi.patch(
        caseId,
        event.id,
        body,
      );
      onSaved(updated);
    } catch (e) {
      notifyApiError(e, "تعذر حفظ التعديلات");
    } finally {
      setSaving(false);
    }
  }

  return (
    <Card className="border-[var(--color-border)] p-4 shadow-[var(--shadow-soft)]">
      <p className="mb-4 text-sm font-semibold text-[var(--color-foreground)]">
        تعديل المرحلة
      </p>
      <div className="grid gap-3 sm:grid-cols-2">
        <div className="space-y-1 sm:col-span-2">
          <Label htmlFor={`de-t-${event.id}`}>العنوان *</Label>
          <Input
            id={`de-t-${event.id}`}
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            disabled={saving}
          />
        </div>
        <div className="space-y-1">
          <Label>النوع</Label>
          <select
            className={selectClassName}
            value={type}
            onChange={(e) =>
              setType(e.target.value as DisputeEventTypeDto)
            }
            disabled={saving}
          >
            {TYPE_ORDER.map((k) => (
              <option key={k} value={k}>
                {TYPE_AR[k]}
              </option>
            ))}
          </select>
        </div>
        <div className="space-y-1">
          <Label>الحالة</Label>
          <select
            className={selectClassName}
            value={status}
            onChange={(e) =>
              setStatus(e.target.value as DisputeEventStatusDto)
            }
            disabled={saving}
          >
            {STATUS_VALUES.map((s) => (
              <option key={s} value={s}>
                {STATUS_AR[s]}
              </option>
            ))}
          </select>
        </div>
        <div className="space-y-1">
          <Label htmlFor={`de-so-${event.id}`}>ترتيب العرض</Label>
          <Input
            id={`de-so-${event.id}`}
            type="number"
            inputMode="numeric"
            value={sortOrderStr}
            onChange={(e) => setSortOrderStr(e.target.value)}
            disabled={saving}
            className="ltr-nums"
          />
        </div>
        <div className="space-y-1">
          <Label htmlFor={`de-d-${event.id}`}>التاريخ / الوقت</Label>
          <Input
            id={`de-d-${event.id}`}
            type="datetime-local"
            value={eventDate}
            onChange={(e) => setEventDate(e.target.value)}
            disabled={saving}
          />
        </div>
        <div className="space-y-1 sm:col-span-2">
          <Label htmlFor={`de-s-${event.id}`}>ملخص</Label>
          <Textarea
            id={`de-s-${event.id}`}
            value={summary}
            onChange={(e) => setSummary(e.target.value)}
            disabled={saving}
            rows={2}
            className="resize-y"
          />
        </div>
      </div>
      <div className="mt-4 flex flex-wrap justify-end gap-2">
        <Button
          type="button"
          variant="outline"
          size="sm"
          onClick={() => {
            setTitle(event.title);
            setType(event.type);
            setStatus(event.status);
            setSummary(event.summary ?? "");
            setEventDate(toDateTimeLocal(event.eventDate));
            setSortOrderStr(String(event.sortOrder));
            onCancel();
          }}
          disabled={saving}
        >
          إلغاء
        </Button>
        <Button
          type="button"
          variant="gold"
          size="sm"
          onClick={() => void submit()}
          disabled={saving}
        >
          {saving ? <Loader2 className="size-4 animate-spin" /> : "حفظ"}
        </Button>
      </div>
    </Card>
  );
}
