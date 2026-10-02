"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { CalendarDays, ChevronRight, ChevronLeft, Gavel, Info, List } from "lucide-react";

import { ApiErrorAlert } from "@/components/shared/ApiErrorAlert";
import { PageHeader } from "@/components/shared/PageHeader";
import { PageLoading } from "@/components/shared/PageLoading";
import { SectionCard } from "@/components/shared/SectionCard";
import { EmptyState } from "@/components/shared/EmptyState";
import { HearingListItem } from "@/components/hearings/HearingListItem";
import { Label } from "@/components/ui/label";
import { Button } from "@/components/ui/button";
import { ApiRequestError } from "@/lib/api/client";
import { hearingsApi } from "@/lib/api/hearings";
import { officeHearingToUi } from "@/lib/hearings/map-office-hearing";
import type { Hearing, HearingStatus, HearingType } from "@/types/hearing";

// ─── helpers ────────────────────────────────────────────────────────────────

function toInputDate(d: Date) {
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, "0");
  const day = String(d.getDate()).padStart(2, "0");
  return `${y}-${m}-${day}`;
}

function parseInputDate(s: string): Date {
  const [y, m, d] = s.split("-").map(Number);
  return new Date(y!, (m ?? 1) - 1, d ?? 1);
}

// ─── Calendar helpers ────────────────────────────────────────────────────────

// Days of week header in Arabic (RTL: starts Saturday)
const DAY_NAMES = ["سبت", "أحد", "اثن", "ثلا", "أرب", "خمي", "جمع"];

/** Build a 6×7 grid of Date|null for the given month */
function buildCalendarGrid(year: number, month: number): (Date | null)[] {
  const firstDay = new Date(year, month, 1);
  // Convert JS day (0=Sun) to RTL column (0=Sat)
  // Sat=6 → col 0, Sun=0 → col 1, Mon=1 → col 2 … Fri=5 → col 6
  const jsDay = firstDay.getDay(); // 0=Sun..6=Sat
  const colStart = jsDay === 6 ? 0 : jsDay + 1; // Sat=0..Fri=6

  const daysInMonth = new Date(year, month + 1, 0).getDate();
  const cells: (Date | null)[] = Array(colStart).fill(null);
  for (let d = 1; d <= daysInMonth; d++) {
    cells.push(new Date(year, month, d));
  }
  // pad to full rows
  while (cells.length % 7 !== 0) cells.push(null);
  return cells;
}

function isSameDay(a: Date, b: Date) {
  return (
    a.getFullYear() === b.getFullYear() &&
    a.getMonth() === b.getMonth() &&
    a.getDate() === b.getDate()
  );
}

const MONTH_AR = [
  "يناير", "فبراير", "مارس", "أبريل", "مايو", "يونيو",
  "يوليو", "أغسطس", "سبتمبر", "أكتوبر", "نوفمبر", "ديسمبر",
];

// ─── Main Page ───────────────────────────────────────────────────────────────

export default function HearingsPage() {
  const [view, setView] = useState<"list" | "calendar">("list");

  // ── List view state ────────────────────────────────────────────────────────
  const [fromDate, setFromDate] = useState(() => toInputDate(new Date()));
  const [toDate, setToDate] = useState(() => toInputDate(new Date()));
  const [status, setStatus] = useState<HearingStatus | "">("");
  const [hearingType, setHearingType] = useState<HearingType | "">("");
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [items, setItems] = useState<Hearing[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [loadKey, setLoadKey] = useState(0);

  useEffect(() => {
    if (view !== "list") return;
    let cancelled = false;
    (async () => {
      setLoading(true);
      setError(null);
      try {
        const res = await hearingsApi.listOffice({
          from: fromDate ? parseInputDate(fromDate) : undefined,
          to: toDate ? parseInputDate(toDate) : undefined,
          status: status || undefined,
          type: hearingType || undefined,
          page,
          size: 20,
        });
        if (cancelled) return;
        setItems(res.content.map(officeHearingToUi));
        setTotalPages(res.totalPages);
      } catch (e) {
        if (cancelled) return;
        if (e instanceof ApiRequestError) {
          setError(e.details?.message ?? e.message);
        } else {
          setError("تعذر تحميل الجلسات");
        }
        setItems([]);
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [fromDate, toDate, status, hearingType, page, loadKey, view]);

  return (
    <div className="space-y-6">
      <PageHeader
        title="الجلسات"
        description="جلسات المكتب — يمكن الاستعراض بالقائمة أو التقويم."
        actions={
          <div className="flex items-center gap-2">
            <Button
              variant={view === "list" ? "gold" : "outline"}
              size="sm"
              onClick={() => setView("list")}
              className="gap-1.5"
            >
              <List className="size-4" />
              عرض القائمة
            </Button>
            <Button
              variant={view === "calendar" ? "gold" : "outline"}
              size="sm"
              onClick={() => setView("calendar")}
              className="gap-1.5"
            >
              <CalendarDays className="size-4" />
              عرض التقويم
            </Button>
            <Button variant="outline" size="sm" asChild>
              <Link href="/calendar?eventType=HEARING">التقويم المركزي</Link>
            </Button>
          </div>
        }
      />

      {view === "list" ? (
        <ListView
          fromDate={fromDate}
          toDate={toDate}
          setFromDate={(value) => { setFromDate(value); setPage(0); }}
          setToDate={(value) => { setToDate(value); setPage(0); }}
          status={status}
          setStatus={(value) => { setStatus(value); setPage(0); }}
          hearingType={hearingType}
          setHearingType={(value) => { setHearingType(value); setPage(0); }}
          page={page}
          totalPages={totalPages}
          setPage={setPage}
          items={items}
          loading={loading}
          error={error}
          onRetry={() => setLoadKey((k) => k + 1)}
        />
      ) : (
        <CalendarView />
      )}

      <div className="flex gap-3 rounded-2xl border border-[var(--color-border)] bg-[var(--color-surface-muted)]/30 p-4 text-sm text-[var(--color-muted-foreground)]">
        <Info className="mt-0.5 size-5 shrink-0 text-[var(--color-info)]" />
        <p>
          تفصيل جلسة واحدة أو تعديلها يبقى عبر{" "}
          <code className="rounded bg-[var(--color-surface)] px-1 ltr-nums" dir="ltr">
            /api/cases/{"{caseId}"}/hearings/…
          </code>
        </p>
      </div>
    </div>
  );
}

// ─── List View ───────────────────────────────────────────────────────────────

type ListViewProps = {
  fromDate: string;
  toDate: string;
  setFromDate: (s: string) => void;
  setToDate: (s: string) => void;
  status: HearingStatus | "";
  setStatus: (s: HearingStatus | "") => void;
  hearingType: HearingType | "";
  setHearingType: (s: HearingType | "") => void;
  page: number;
  totalPages: number;
  setPage: (update: (page: number) => number) => void;
  items: Hearing[];
  loading: boolean;
  error: string | null;
  onRetry: () => void;
};

function ListView({ fromDate, toDate, setFromDate, setToDate, status, setStatus, hearingType, setHearingType, page, totalPages, setPage, items, loading, error, onRetry }: ListViewProps) {
  return (
    <>
      <SectionCard className="p-5">
        <div className="flex flex-wrap items-end gap-4">
          <div className="space-y-1.5">
            <Label htmlFor="hearing-from">من تاريخ</Label>
            <input
              id="hearing-from"
              type="date"
              className="h-10 rounded-xl border border-[var(--color-border)] bg-[var(--color-surface)] px-3 text-sm ltr-nums"
              value={fromDate}
              onChange={(e) => setFromDate(e.target.value)}
            />
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="hearing-to">إلى تاريخ</Label>
            <input id="hearing-to" type="date" className="h-10 rounded-xl border border-[var(--color-border)] bg-[var(--color-surface)] px-3 text-sm ltr-nums" value={toDate} onChange={(e) => setToDate(e.target.value)} />
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="hearing-status">الحالة</Label>
            <select id="hearing-status" className="h-10 rounded-xl border border-[var(--color-border)] bg-[var(--color-surface)] px-3 text-sm" value={status} onChange={(e) => setStatus(e.target.value as HearingStatus | "")}>
              <option value="">كل الحالات</option><option value="SCHEDULED">مجدولة</option><option value="HELD">انعقدت</option><option value="POSTPONED">مؤجلة</option><option value="CANCELLED">ملغاة</option><option value="ADJOURNED">مرفوعة</option>
            </select>
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="hearing-type">النوع</Label>
            <select id="hearing-type" className="h-10 rounded-xl border border-[var(--color-border)] bg-[var(--color-surface)] px-3 text-sm" value={hearingType} onChange={(e) => setHearingType(e.target.value as HearingType | "")}>
              <option value="">كل الأنواع</option><option value="ORDINARY">عادية</option><option value="DEFENSE">دفاع</option><option value="EVIDENCE">إثبات</option><option value="EXPEDITED">مستعجلة</option><option value="ARBITRATION">تحكيم</option><option value="INVESTIGATION">تحقيق</option><option value="APPEAL">استئناف</option><option value="SENTENCING">حكم</option><option value="REVIEW">مراجعة</option>
            </select>
          </div>
          <p className="max-w-md pb-0.5 text-xs text-[var(--color-muted-foreground)]">
            المصدر:{" "}
            <code className="rounded bg-[var(--color-surface-muted)] px-1 ltr-nums" dir="ltr">
              GET /api/hearings?from=…&to=…
            </code>
            — قضايا مؤرشفة تُستبعد تلقائياً.
          </p>
        </div>
      </SectionCard>

      {error ? (
        <ApiErrorAlert message={error} onRetry={onRetry} />
      ) : null}

      {loading ? (
        <PageLoading label="جارٍ تحميل جلسات اليوم…" minHeightClassName="min-h-[14rem]" />
      ) : !error && items.length === 0 ? (
        <EmptyState
          title="لا جلسات في هذا اليوم"
          description="جرّب تغيير التاريخ، أو سجّل جلسة من صفحة القضية."
          icon={Gavel}
        />
      ) : !error ? (
        <ul className="space-y-4">
          {items.map((h) => (
            <li key={h.id}>
              <Link
                href={`/cases/${h.caseId}`}
                className="block rounded-2xl focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--color-gold-400)]"
              >
                <HearingListItem data={h} />
              </Link>
            </li>
          ))}
        </ul>
      ) : null}
      {!error && totalPages > 1 ? <div className="flex items-center justify-between"><Button variant="outline" disabled={page === 0} onClick={() => setPage((current) => current - 1)}>السابق</Button><span className="text-sm">{page + 1} / {totalPages}</span><Button variant="outline" disabled={page + 1 >= totalPages} onClick={() => setPage((current) => current + 1)}>التالي</Button></div> : null}
    </>
  );
}

// ─── Calendar View ───────────────────────────────────────────────────────────

function CalendarView() {
  const today = new Date();
  const [calYear, setCalYear] = useState(today.getFullYear());
  const [calMonth, setCalMonth] = useState(today.getMonth());
  const [selectedDate, setSelectedDate] = useState<Date | null>(null);
  const [dayHearings, setDayHearings] = useState<Hearing[]>([]);
  const [dayLoading, setDayLoading] = useState(false);
  const [dayError, setDayError] = useState<string | null>(null);
  // Map of "yyyy-mm-dd" → whether that day has hearings (true = yes, false = no/checked)
  const [dotCache, setDotCache] = useState<Record<string, boolean>>({});

  const grid = buildCalendarGrid(calYear, calMonth);

  function prevMonth() {
    if (calMonth === 0) {
      setCalYear((y) => y - 1);
      setCalMonth(11);
    } else {
      setCalMonth((m) => m - 1);
    }
    setSelectedDate(null);
    setDayHearings([]);
  }

  function nextMonth() {
    if (calMonth === 11) {
      setCalYear((y) => y + 1);
      setCalMonth(0);
    } else {
      setCalMonth((m) => m + 1);
    }
    setSelectedDate(null);
    setDayHearings([]);
  }

  async function selectDay(date: Date) {
    setSelectedDate(date);
    setDayLoading(true);
    setDayError(null);
    try {
      const res = await hearingsApi.listOffice({ date, page: 0, size: 100 });
      const mapped = res.content.map(officeHearingToUi);
      setDayHearings(mapped);
      const key = toInputDate(date);
      setDotCache((prev) => ({ ...prev, [key]: mapped.length > 0 }));
    } catch (e) {
      if (e instanceof ApiRequestError) {
        setDayError(e.details?.message ?? e.message);
      } else {
        setDayError("تعذر تحميل جلسات اليوم");
      }
      setDayHearings([]);
    } finally {
      setDayLoading(false);
    }
  }

  return (
    <div className="space-y-4">
      {/* Calendar header */}
      <SectionCard className="p-5">
        {/* Month navigation */}
        <div className="mb-4 flex items-center justify-between">
          <Button variant="outline" size="sm" onClick={nextMonth} className="gap-1">
            <ChevronRight className="size-4" />
            التالي
          </Button>
          <h2 className="text-base font-bold text-[var(--color-navy-800)]">
            {MONTH_AR[calMonth]} {calYear}
          </h2>
          <Button variant="outline" size="sm" onClick={prevMonth} className="gap-1">
            السابق
            <ChevronLeft className="size-4" />
          </Button>
        </div>

        {/* Day-of-week header (RTL: Sat→Fri) */}
        <div className="grid grid-cols-7 mb-1">
          {DAY_NAMES.map((d) => (
            <div
              key={d}
              className="py-1.5 text-center text-xs font-semibold text-[var(--color-muted-foreground)]"
            >
              {d}
            </div>
          ))}
        </div>

        {/* Calendar grid */}
        <div className="grid grid-cols-7 gap-px rounded-xl overflow-hidden bg-[var(--color-border)]">
          {grid.map((date, i) => {
            if (!date) {
              return (
                <div
                  key={`empty-${i}`}
                  className="aspect-square bg-[var(--color-surface-muted)]/40"
                />
              );
            }
            const key = toInputDate(date);
            const isToday = isSameDay(date, today);
            const isSelected = selectedDate ? isSameDay(date, selectedDate) : false;
            const hasDot = dotCache[key] === true;

            return (
              <button
                key={key}
                onClick={() => void selectDay(date)}
                className={`relative flex aspect-square flex-col items-center justify-center gap-0.5 transition-colors text-sm font-medium
                  ${isSelected
                    ? "bg-[var(--color-gold-500)] text-[var(--color-navy-900)]"
                    : isToday
                    ? "bg-[var(--color-navy-800)] text-white"
                    : "bg-[var(--color-surface)] hover:bg-[var(--color-surface-muted)] text-[var(--color-foreground)]"
                  }`}
              >
                <span className="ltr-nums">{date.getDate()}</span>
                {hasDot ? (
                  <span
                    className={`size-1.5 rounded-full ${
                      isSelected ? "bg-[var(--color-navy-900)]" : "bg-[var(--color-gold-500)]"
                    }`}
                  />
                ) : (
                  <span className="size-1.5" />
                )}
              </button>
            );
          })}
        </div>
      </SectionCard>

      {/* Selected day hearings */}
      {selectedDate ? (
        <div className="space-y-3">
          <h3 className="flex items-center gap-2 text-base font-semibold text-[var(--color-navy-800)]">
            <Gavel className="size-5 text-[var(--color-gold-500)]" />
            جلسات{" "}
            {selectedDate.toLocaleDateString("ar-EG", {
              weekday: "long",
              year: "numeric",
              month: "long",
              day: "numeric",
            })}
          </h3>

          {dayError ? (
            <ApiErrorAlert message={dayError} />
          ) : dayLoading ? (
            <PageLoading label="جارٍ تحميل…" minHeightClassName="min-h-[8rem]" />
          ) : dayHearings.length === 0 ? (
            <EmptyState
              title="لا جلسات في هذا اليوم"
              description="لا توجد جلسات مسجّلة لهذا اليوم."
              icon={Gavel}
            />
          ) : (
            <ul className="space-y-4">
              {dayHearings.map((h) => (
                <li key={h.id}>
                  <Link
                    href={`/cases/${h.caseId}`}
                    className="block rounded-2xl focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--color-gold-400)]"
                  >
                    <HearingListItem data={h} />
                  </Link>
                </li>
              ))}
            </ul>
          )}
        </div>
      ) : (
        <p className="text-center text-sm text-[var(--color-muted-foreground)]">
          اضغط على أي يوم لعرض جلساته. الأيام التي تحمل نقطة ذهبية تحتوي جلسات.
        </p>
      )}
    </div>
  );
}
