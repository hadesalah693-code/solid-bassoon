"use client";

import { Suspense, useCallback, useEffect, useMemo, useState } from "react";
import { useSearchParams } from "next/navigation";
import dynamic from "next/dynamic";
import { CalendarPlus, LayoutGrid, List, RefreshCw } from "lucide-react";
import { addDays, startOfDay } from "date-fns";

import { CalendarAgendaList } from "@/components/calendar/CalendarAgendaList";
import { CalendarEventDetail } from "@/components/calendar/CalendarEventDetail";
import { CalendarEventForm } from "@/components/calendar/CalendarEventForm";
import { CalendarFilters } from "@/components/calendar/CalendarFilters";
import { CalendarLegend } from "@/components/calendar/CalendarLegend";
import { ApiErrorAlert } from "@/components/shared/ApiErrorAlert";
import { PageHeader } from "@/components/shared/PageHeader";
import { PageLoading } from "@/components/shared/PageLoading";
import { SectionCard } from "@/components/shared/SectionCard";
import { Button } from "@/components/ui/button";
import { useAuth } from "@/components/auth/auth-provider";
import { calendarApi } from "@/lib/api/calendar";
import { ApiRequestError } from "@/lib/api/client";
import {
  dateToApiDate,
  dateToApiDateTime,
  sortEventsByStart,
} from "@/lib/calendar/calendar-utils";
import { notifyApiError, notifySuccess } from "@/lib/feedback";
import type {
  CalendarEventDto,
  CalendarEventStatus,
  CalendarEventType,
  CalendarViewMode,
} from "@/types/calendar";
import type { SystemRole } from "@/types/staff";

const CalendarShell = dynamic(
  () =>
    import("@/components/calendar/CalendarShell").then((m) => m.CalendarShell),
  {
    ssr: false,
    loading: () => (
      <PageLoading label="جارٍ تحميل التقويم…" minHeightClassName="min-h-[24rem]" />
    ),
  },
);

const VIEW_OPTIONS: { key: CalendarViewMode; label: string; icon: typeof LayoutGrid }[] = [
  { key: "month", label: "شهر", icon: LayoutGrid },
  { key: "week", label: "أسبوع", icon: LayoutGrid },
  { key: "day", label: "يوم", icon: LayoutGrid },
  { key: "agenda", label: "أجندة", icon: List },
];

function canEditCalendar(role: SystemRole | null) {
  return (
    role === "ADMIN" ||
    role === "SENIOR_LAWYER" ||
    role === "LAWYER" ||
    role === "SECRETARY"
  );
}

function parseEventType(raw: string | null): CalendarEventType | "ALL" {
  const allowed: CalendarEventType[] = [
    "HEARING",
    "TASK",
    "MEETING",
    "CALL",
    "REMINDER",
    "DOCUMENT_EXPIRY",
    "DEADLINE",
    "CUSTOM",
  ];
  if (raw && allowed.includes(raw as CalendarEventType)) {
    return raw as CalendarEventType;
  }
  return "ALL";
}

export default function CalendarPage() {
  return (
    <Suspense
      fallback={
        <PageLoading label="جارٍ تحميل التقويم…" minHeightClassName="min-h-[24rem]" />
      }
    >
      <CalendarPageInner />
    </Suspense>
  );
}

function CalendarPageInner() {
  const searchParams = useSearchParams();
  const { user } = useAuth();
  const role = (user?.systemRole as SystemRole | undefined) ?? null;
  const canEdit = canEditCalendar(role);

  const [viewMode, setViewMode] = useState<CalendarViewMode>("month");
  const [eventType, setEventType] = useState<CalendarEventType | "ALL">(() =>
    parseEventType(searchParams.get("eventType") ?? searchParams.get("type")),
  );
  const [status, setStatus] = useState<CalendarEventStatus | "ALL">("ALL");
  const [search, setSearch] = useState("");
  const [debouncedSearch, setDebouncedSearch] = useState("");
  const caseFilter = searchParams.get("caseId") ?? "";

  useEffect(() => {
    const t = window.setTimeout(() => setDebouncedSearch(search), 300);
    return () => window.clearTimeout(t);
  }, [search]);

  const [rangeFrom, setRangeFrom] = useState<Date>(() =>
    startOfDay(addDays(new Date(), -7)),
  );
  const [rangeTo, setRangeTo] = useState<Date>(() =>
    startOfDay(addDays(new Date(), 45)),
  );

  const [events, setEvents] = useState<CalendarEventDto[]>([]);
  const [agendaDays, setAgendaDays] = useState<
    { date: string; events: CalendarEventDto[] }[]
  >([]);
  const [summary, setSummary] = useState<{
    todayCount: number;
    upcomingWeekCount: number;
    overdueCount: number;
  } | null>(null);

  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [loadKey, setLoadKey] = useState(0);

  const [selected, setSelected] = useState<CalendarEventDto | null>(null);
  const [formOpen, setFormOpen] = useState(false);
  const [formMode, setFormMode] = useState<"create" | "edit">("create");
  const [editTarget, setEditTarget] = useState<CalendarEventDto | null>(null);
  const [deleting, setDeleting] = useState(false);

  const filterQuery = useMemo(
    () => ({
      eventType: eventType === "ALL" ? undefined : eventType,
      status: status === "ALL" ? undefined : status,
      caseId: caseFilter || undefined,
      q: debouncedSearch.trim() || undefined,
    }),
    [eventType, status, caseFilter, debouncedSearch],
  );

  const queryParams = useMemo(
    () => ({
      from: dateToApiDateTime(rangeFrom),
      to: dateToApiDateTime(rangeTo),
      ...filterQuery,
      size: 500,
    }),
    [rangeFrom, rangeTo, filterQuery],
  );

  const loadEvents = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const [page, sum] = await Promise.all([
        calendarApi.listEvents(queryParams),
        calendarApi.summary().catch(() => null),
      ]);
      setEvents(sortEventsByStart(page.content));
      if (sum) {
        setSummary({
          todayCount: sum.todayCount,
          upcomingWeekCount: sum.upcomingWeekCount,
          overdueCount: sum.overdueCount,
        });
      }
    } catch (e) {
      if (e instanceof ApiRequestError) {
        setError(e.details?.message ?? e.message);
      } else {
        setError("تعذر تحميل التقويم");
      }
      setEvents([]);
    } finally {
      setLoading(false);
    }
  }, [queryParams]);

  const loadAgenda = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const [days, sum] = await Promise.all([
        calendarApi.agenda({
          from: dateToApiDate(rangeFrom),
          to: dateToApiDate(addDays(rangeTo, -1)),
          ...filterQuery,
        }),
        calendarApi.summary().catch(() => null),
      ]);
      setAgendaDays(days);
      if (sum) {
        setSummary({
          todayCount: sum.todayCount,
          upcomingWeekCount: sum.upcomingWeekCount,
          overdueCount: sum.overdueCount,
        });
      }
    } catch (e) {
      if (e instanceof ApiRequestError) {
        setError(e.details?.message ?? e.message);
      } else {
        setError("تعذر تحميل الأجندة");
      }
      setAgendaDays([]);
    } finally {
      setLoading(false);
    }
  }, [rangeFrom, rangeTo, filterQuery]);

  useEffect(() => {
    if (viewMode === "agenda") {
      void loadAgenda();
    } else {
      void loadEvents();
    }
  }, [viewMode, loadEvents, loadAgenda, loadKey]);

  function handleDatesChange(from: Date, to: Date) {
    setRangeFrom(from);
    setRangeTo(to);
  }

  function openCreate() {
    setFormMode("create");
    setEditTarget(null);
    setFormOpen(true);
  }

  function openEdit(ev: CalendarEventDto) {
    setFormMode("edit");
    setEditTarget(ev);
    setFormOpen(true);
    setSelected(null);
  }

  async function handleDelete(ev: CalendarEventDto) {
    if (!window.confirm("حذف هذا الموعد؟")) return;
    setDeleting(true);
    try {
      await calendarApi.deleteEvent(ev.id);
      notifySuccess("تم حذف الموعد");
      setSelected(null);
      setLoadKey((k) => k + 1);
    } catch (e) {
      notifyApiError(e, "تعذر الحذف");
    } finally {
      setDeleting(false);
    }
  }

  function handleSaved() {
    setLoadKey((k) => k + 1);
  }

  return (
    <div className="space-y-6">
      <PageHeader
        title="التقويم"
        description="مركز المواعيد — جلسات، مهام، اجتماعات، وانتهاء التوكيلات في مكان واحد."
        actions={
          <div className="flex flex-wrap items-center gap-2">
            <Button
              variant="outline"
              size="sm"
              onClick={() => setLoadKey((k) => k + 1)}
              disabled={loading}
            >
              <RefreshCw className={`size-4 ${loading ? "animate-spin" : ""}`} />
              تحديث
            </Button>
            {canEdit ?
              <Button size="sm" onClick={openCreate}>
                <CalendarPlus className="size-4" />
                موعد جديد
              </Button>
            : null}
          </div>
        }
      />

      {summary ?
        <div className="grid gap-3 sm:grid-cols-3">
          <SectionCard title="اليوم" contentClassName="pt-0">
            <p className="text-2xl font-bold ltr-nums">{summary.todayCount}</p>
          </SectionCard>
          <SectionCard title="الأسبوع القادم" contentClassName="pt-0">
            <p className="text-2xl font-bold ltr-nums">{summary.upcomingWeekCount}</p>
          </SectionCard>
          <SectionCard title="متأخر" contentClassName="pt-0">
            <p className="text-2xl font-bold text-[var(--color-danger)] ltr-nums">
              {summary.overdueCount}
            </p>
          </SectionCard>
        </div>
      : null}

      <SectionCard title="الفلاتر">
        <CalendarFilters
          eventType={eventType}
          status={status}
          search={search}
          onEventTypeChange={setEventType}
          onStatusChange={setStatus}
          onSearchChange={setSearch}
        />
        <div className="mt-4 border-t border-[var(--color-border)] pt-4">
          <CalendarLegend />
        </div>
      </SectionCard>

      <div className="flex flex-wrap gap-2">
        {VIEW_OPTIONS.map((opt) => (
          <Button
            key={opt.key}
            variant={viewMode === opt.key ? "default" : "outline"}
            size="sm"
            onClick={() => setViewMode(opt.key)}
          >
            <opt.icon className="size-4" />
            {opt.label}
          </Button>
        ))}
      </div>

      {error ?
        <ApiErrorAlert
          message={error}
          title="تعذر التحميل"
          onRetry={() => setLoadKey((k) => k + 1)}
        />
      : null}

      {!error && viewMode === "agenda" ?
        <SectionCard title="أجندة المواعيد" flush>
          <CalendarAgendaList
            days={agendaDays}
            loading={loading}
            onSelectEvent={setSelected}
          />
        </SectionCard>
      : null}

      {!error && viewMode !== "agenda" ?
        loading && events.length === 0 ?
          <PageLoading label="جارٍ تحميل المواعيد…" minHeightClassName="min-h-[24rem]" />
        : <CalendarShell
            events={events}
            viewMode={viewMode}
            onDatesChange={handleDatesChange}
            onEventClick={setSelected}
            onDateClick={canEdit ? () => openCreate() : undefined}
          />
      : null}

      <CalendarEventDetail
        event={selected}
        open={Boolean(selected)}
        canEdit={canEdit}
        onClose={() => setSelected(null)}
        onEdit={openEdit}
        onDelete={handleDelete}
        deleting={deleting}
      />

      {canEdit ?
        <CalendarEventForm
          open={formOpen}
          mode={formMode}
          initial={editTarget}
          defaultCaseId={caseFilter || null}
          onClose={() => setFormOpen(false)}
          onSaved={handleSaved}
        />
      : null}
    </div>
  );
}
