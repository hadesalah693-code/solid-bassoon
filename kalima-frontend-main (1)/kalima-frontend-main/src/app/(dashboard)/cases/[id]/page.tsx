"use client";

import { Suspense, useEffect, useState } from "react";
import Link from "next/link";
import { useParams, useRouter, useSearchParams } from "next/navigation";
import {
  ArrowRight,
  Briefcase,
  CalendarClock,
  CalendarDays,
  ClipboardList,
  Scale,
} from "lucide-react";
import { isValid, parseISO, startOfDay } from "date-fns";

import { CaseActionsBar } from "@/components/cases/CaseActionsBar";
import { CaseCalendarPanel } from "@/components/calendar/CaseCalendarPanel";
import { CaseEditForm } from "@/components/cases/CaseEditForm";
import { CaseDocumentsPanel } from "@/components/cases/CaseDocumentsPanel";
import { DisputeEventsPanel } from "@/components/cases/DisputeEventsPanel";
import { CaseHearingAddForm } from "@/components/cases/CaseHearingAddForm";
import { CaseHearingRow } from "@/components/cases/CaseHearingRow";
import { CaseHistoryTimeline } from "@/components/cases/CaseHistoryTimeline";
import { CaseInternalNotesPanel } from "@/components/cases/CaseInternalNotesPanel";
import { CaseRelatedCasesPanel } from "@/components/cases/CaseRelatedCasesPanel";
import { CaseMemosPanel } from "@/components/cases/CaseMemosPanel";
import { CaseCirculationPanel } from "@/components/cases/CaseCirculationPanel";
import { CaseMessagesPanel } from "@/components/cases/CaseMessagesPanel";
import { CaseParticipantsPanel } from "@/components/cases/CaseParticipantsPanel";
import { CaseTaskAddForm } from "@/components/cases/CaseTaskAddForm";
import { CaseTaskRow } from "@/components/cases/CaseTaskRow";
import { ApiErrorAlert } from "@/components/shared/ApiErrorAlert";
import { PageHeader } from "@/components/shared/PageHeader";
import { PageLoading } from "@/components/shared/PageLoading";
import { SectionCard } from "@/components/shared/SectionCard";
import { EmptyState } from "@/components/shared/EmptyState";
import { Button } from "@/components/ui/button";
import {
  Table,
  TableBody,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { ApiRequestError } from "@/lib/api/client";
import { casesApi } from "@/lib/api/cases";
import { hearingsApi } from "@/lib/api/hearings";
import { tasksApi } from "@/lib/api/tasks";
import { caseFileFromDto } from "@/types/api";
import type { HearingResponseDto, TaskResponseDto } from "@/types/api";
import { CaseStatusBadge } from "@/components/shared/StatusBadge";
import { formatDate, formatDateTime } from "@/lib/utils/format-date";
import { cn } from "@/lib/utils/cn";
import type { CaseFile } from "@/types/case";

type CaseSection =
  | "overview"
  | "participants"
  | "documents"
  | "hearings"
  | "calendar"
  | "tasks"
  | "disputePath"
  | "history"
  | "messages"
  | "internalNotes"
  | "appeals"
  | "memos"
  | "circulation";

const SECTION_KEYS = new Set<CaseSection>([
  "overview",
  "participants",
  "documents",
  "hearings",
  "calendar",
  "tasks",
  "disputePath",
  "history",
  "messages",
  "internalNotes",
  "appeals",
  "memos",
  "circulation",
]);

const SECTION_NAV: { key: CaseSection; label: string }[] = [
  { key: "overview", label: "نظرة عامة" },
  { key: "participants", label: "المشاركون" },
  { key: "documents", label: "المستندات" },
  { key: "hearings", label: "الجلسات" },
  { key: "calendar", label: "التقويم" },
  { key: "tasks", label: "المهام" },
  { key: "disputePath", label: "مسار النزاع" },
  { key: "history", label: "السجل" },
  { key: "messages", label: "المحادثات" },
  { key: "internalNotes", label: "ملاحظات داخلية" },
  { key: "appeals", label: "الاستئنافات" },
  { key: "memos", label: "المذكرات" },
  { key: "circulation", label: "التداول" },
];

function parseCaseSection(raw: string | null): CaseSection {
  if (raw && SECTION_KEYS.has(raw as CaseSection)) return raw as CaseSection;
  return "overview";
}

function computeHearingSpotlight(hearings: HearingResponseDto[]) {
  const today = startOfDay(new Date());
  const withDates = hearings
    .map((h) => {
      const d = parseISO(h.hearingDate);
      return { h, d, day: startOfDay(d) };
    })
    .filter((x) => isValid(x.d));

  if (withDates.length === 0) {
    return { mode: "empty" as const, hearing: null };
  }

  const futureOrToday = withDates
    .filter((x) => x.day.getTime() >= today.getTime())
    .sort((a, b) => a.d.getTime() - b.d.getTime());
  if (futureOrToday.length > 0) {
    return { mode: "upcoming" as const, hearing: futureOrToday[0]!.h };
  }

  const past = withDates
    .filter((x) => x.day.getTime() < today.getTime())
    .sort((a, b) => b.d.getTime() - a.d.getTime());
  if (past.length > 0) {
    return { mode: "past" as const, hearing: past[0]!.h };
  }

  return { mode: "empty" as const, hearing: null };
}

function CaseDetailInner() {
  const params = useParams();
  const searchParams = useSearchParams();
  const router = useRouter();
  const id = typeof params.id === "string" ? params.id : "";

  const rawSection = searchParams.get("section");
  const activeSection = parseCaseSection(rawSection);

  const [caseData, setCaseData] = useState<CaseFile | null>(null);
  const [hearings, setHearings] = useState<HearingResponseDto[]>([]);
  const [tasks, setTasks] = useState<TaskResponseDto[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [loadVersion, setLoadVersion] = useState(0);

  useEffect(() => {
    if (!id) return;
    if (rawSection !== null && rawSection !== "" && !SECTION_KEYS.has(rawSection as CaseSection)) {
      router.replace(`/cases/${id}?section=overview`, { scroll: false });
    }
  }, [id, rawSection, router]);

  useEffect(() => {
    if (!id) return;
    let cancelled = false;
    (async () => {
      setLoading(true);
      setError(null);
      try {
        const [c, h, t] = await Promise.all([
          casesApi.get(id),
          hearingsApi.listByCase(id, { page: 0, size: 100 }),
          tasksApi.listByCase(id, { page: 0, size: 100 }),
        ]);
        if (cancelled) return;
        setCaseData(caseFileFromDto(c));
        setHearings(h.content);
        setTasks(t.content);
      } catch (e) {
        if (cancelled) return;
        if (e instanceof ApiRequestError) {
          setError(e.details?.message ?? e.message);
        } else {
          setError("تعذر تحميل بيانات القضية");
        }
        setCaseData(null);
        setHearings([]);
        setTasks([]);
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [id, loadVersion]);

  function refresh() {
    setLoadVersion((v) => v + 1);
  }

  if (!id) {
    return null;
  }

  if (loading) {
    return (
      <PageLoading
        label="جارٍ تحميل بيانات القضية…"
        minHeightClassName="min-h-[40vh]"
        className="w-full"
      />
    );
  }

  if (error || !caseData) {
    return (
      <div className="space-y-4">
        <Button variant="ghost" size="sm" asChild>
          <Link href="/cases" className="inline-flex items-center gap-1">
            <ArrowRight className="size-4 rotate-180" />
            العودة للقائمة
          </Link>
        </Button>
        {error ? (
          <ApiErrorAlert
            message={error}
            title="تعذر فتح القضية"
            onRetry={refresh}
          />
        ) : (
          <div
            role="alert"
            className="rounded-xl border border-[var(--color-border)] bg-[var(--color-surface-muted)]/50 px-4 py-3 text-sm text-[var(--color-muted-foreground)]"
          >
            القضية غير موجودة أو لا تملك صلاحية عرضها.
          </div>
        )}
      </div>
    );
  }

  const hearingSpotlight = computeHearingSpotlight(hearings);
  const archived = Boolean(caseData.archivedAt);
  const subtitleParts = [
    `#${caseData.caseNumber}`,
    caseData.courtCaseNumber
      ? `رقم محكمة ${caseData.courtCaseNumber}`
      : null,
  ].filter(Boolean);
  const metaLine = subtitleParts.join(" · ");

  const tabClass = (key: CaseSection) =>
    cn(
      "shrink-0 rounded-full border px-3 py-1.5 text-xs font-semibold shadow-sm transition-colors",
      "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--color-gold-400)]",
      activeSection === key
        ? "border-[var(--color-gold-400)] bg-[var(--color-navy-800)] text-white shadow-[var(--shadow-pop)]"
        : "border-[var(--color-border)] bg-[var(--color-surface)] text-[var(--color-navy-800)] hover:border-[var(--color-gold-300)] hover:text-[var(--color-navy-900)]",
    );

  return (
    <div className="space-y-8">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
        <div>
          <Button variant="ghost" size="sm" asChild className="mb-2 -ms-1">
            <Link
              href="/cases"
              className="inline-flex items-center gap-1 text-[var(--color-muted-foreground)]"
            >
              <ArrowRight className="size-4 rotate-180" />
              كل القضايا
            </Link>
          </Button>
          <PageHeader
            title={caseData.title}
            description={metaLine}
          />
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <CaseStatusBadge value={caseData.status} />
        </div>
      </div>

      <nav
        aria-label="أقسام القضية"
        className="sticky top-0 z-20 -mx-4 flex gap-1.5 overflow-x-auto border-b border-[var(--color-border)] bg-[var(--color-background)]/95 px-4 py-2.5 shadow-[var(--shadow-soft)] backdrop-blur-sm sm:-mx-6 sm:px-6 lg:-mx-8 lg:px-8"
      >
        {SECTION_NAV.map((item) => (
          <Link
            key={item.key}
            href={`/cases/${id}?section=${item.key}`}
            scroll={false}
            className={tabClass(item.key)}
          >
            {item.label}
          </Link>
        ))}
      </nav>

      <div className="min-h-[12rem]">
        {activeSection === "overview" ? (
          <div className="space-y-8">
            <CaseActionsBar caseData={caseData} onChanged={setCaseData} />

            <SectionCard
              title="تعديل بيانات الملف"
              description="تحديث العنوان، بيانات المحكمة/الدائرة، والتواريخ. رقم الملف والموكّل لا يُغيّران من هنا."
              className="p-4 sm:p-5"
            >
              <CaseEditForm caseData={caseData} onSaved={setCaseData} />
            </SectionCard>

            <SectionCard title="نظرة عامة" className="p-4">
              <dl className="grid gap-3 text-sm sm:grid-cols-2">
                <div>
                  <dt className="text-[var(--color-muted-foreground)]">رقم الملف</dt>
                  <dd className="font-medium ltr-nums">{caseData.caseNumber}</dd>
                </div>
                <div>
                  <dt className="text-[var(--color-muted-foreground)]">الموكّل</dt>
                  <dd className="font-medium">{caseData.client.fullName}</dd>
                </div>
                <div>
                  <dt className="text-[var(--color-muted-foreground)]">المحامي</dt>
                  <dd className="font-medium">
                    {caseData.assignedLawyer?.fullName ?? "—"}
                  </dd>
                </div>
                <div>
                  <dt className="text-[var(--color-muted-foreground)]">أنشئ بواسطة</dt>
                  <dd className="font-medium">{caseData.createdBy.fullName}</dd>
                </div>
                {caseData.courtName ? (
                  <div>
                    <dt className="text-[var(--color-muted-foreground)]">المحكمة</dt>
                    <dd>{caseData.courtName}</dd>
                  </div>
                ) : null}
                {caseData.courtCircuit ? (
                  <div>
                    <dt className="text-[var(--color-muted-foreground)]">الدائرة</dt>
                    <dd>{caseData.courtCircuit}</dd>
                  </div>
                ) : null}
                {caseData.procedureStage ? (
                  <div>
                    <dt className="text-[var(--color-muted-foreground)]">مرحلة الإجراء</dt>
                    <dd>{caseData.procedureStage}</dd>
                  </div>
                ) : null}
                {caseData.caseType ? (
                  <div>
                    <dt className="text-[var(--color-muted-foreground)]">النوع</dt>
                    <dd>{caseData.caseType}</dd>
                  </div>
                ) : null}
                <div>
                  <dt className="text-[var(--color-muted-foreground)]">البدء</dt>
                  <dd className="ltr-nums">
                    {caseData.startDate
                      ? formatDate(caseData.startDate)
                      : "—"}
                  </dd>
                </div>
                {caseData.closeDate ? (
                  <div>
                    <dt className="text-[var(--color-muted-foreground)]">الإغلاق</dt>
                    <dd className="ltr-nums">{formatDate(caseData.closeDate)}</dd>
                  </div>
                ) : null}
                <div>
                  <dt className="text-[var(--color-muted-foreground)]">تاريخ الإنشاء</dt>
                  <dd className="ltr-nums">{formatDateTime(caseData.createdAt)}</dd>
                </div>
                <div>
                  <dt className="text-[var(--color-muted-foreground)]">آخر تحديث</dt>
                  <dd className="ltr-nums">{formatDateTime(caseData.updatedAt)}</dd>
                </div>
                {caseData.description ? (
                  <div className="sm:col-span-2">
                    <dt className="text-[var(--color-muted-foreground)]">وصف</dt>
                    <dd className="text-[var(--color-foreground)]">
                      {caseData.description}
                    </dd>
                  </div>
                ) : null}
              </dl>
            </SectionCard>

            <SectionCard
              title="ملخص موعد الجلسة"
              description="يُحسب محليًا من أقرب موعد جلسة (اليوم أو لاحقًا)؛ أو آخر جلسة سابقة إن لم يوجد موعد قادم."
              className="overflow-hidden bg-gradient-to-bl from-[var(--color-navy-50)]/80 to-[var(--color-surface)] p-4 sm:p-5 shadow-[var(--shadow-pop)]"
            >
              {hearingSpotlight.mode === "empty" || !hearingSpotlight.hearing ? (
                <EmptyState
                  title="لا توجد جلسات لعرضها"
                  description="بعد إضافة جلسات ستظهر هنا أقرب موعد أو آخر جلسة ماضية."
                  icon={CalendarClock}
                  className="border-0 bg-transparent px-2 py-6"
                />
              ) : (
                <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
                  <div className="space-y-2">
                    <span
                      className={cn(
                        "inline-flex w-fit rounded-full px-2.5 py-0.5 text-[11px] font-bold",
                        hearingSpotlight.mode === "upcoming"
                          ? "border border-emerald-200 bg-emerald-50 text-emerald-800"
                          : "border border-[var(--color-border)] bg-[var(--color-surface-muted)] text-[var(--color-muted-foreground)]",
                      )}
                    >
                      {hearingSpotlight.mode === "upcoming"
                        ? "أقرب جلسة قادمة"
                        : "آخر جلسة مسجّلة"}
                    </span>
                    <p className="text-lg font-bold text-[var(--color-foreground)] ltr-nums">
                      {formatDate(hearingSpotlight.hearing.hearingDate)}
                    </p>
                    <p className="text-sm text-[var(--color-muted-foreground)]">
                      {hearingSpotlight.hearing.courtName}
                      {hearingSpotlight.hearing.chamber
                        ? ` · ${hearingSpotlight.hearing.chamber}`
                        : ""}
                    </p>
                    {hearingSpotlight.hearing.result ? (
                      <p className="text-sm leading-relaxed text-[var(--color-foreground)]">
                        {hearingSpotlight.hearing.result}
                      </p>
                    ) : null}
                  </div>
                  <Button variant="outline" size="sm" className="shrink-0" asChild>
                    <Link href={`/cases/${id}?section=hearings`} scroll={false}>
                      جميع الجلسات
                    </Link>
                  </Button>
                </div>
              )}
            </SectionCard>
          </div>
        ) : null}

        {activeSection === "participants" ? (
          <CaseParticipantsPanel
            caseId={id}
            canEdit={!archived}
            onChange={refresh}
          />
        ) : null}

        {activeSection === "documents" ? (
          <CaseDocumentsPanel
            caseId={id}
            canUpload={!archived}
            onChange={refresh}
          />
        ) : null}

        {activeSection === "hearings" ? (
          <section>
            <h2 className="mb-3 flex items-center gap-2 text-lg font-semibold">
              <CalendarClock className="size-5 text-[var(--color-navy-700)]" />
              جلسات القضية
            </h2>
            {archived ? null : (
              <div className="mb-4">
                <CaseHearingAddForm
                  caseId={id}
                  defaultCourtName={caseData.courtName}
                  onCreated={refresh}
                />
              </div>
            )}
            {hearings.length === 0 ? (
              <EmptyState
                title="لا جلسات مسجّلة"
                description="سجّل جلسة من النموذج أعلاه أو عبر الـ API."
                icon={Briefcase}
              />
            ) : (
              <div className="flex flex-col gap-3">
                {hearings.map((h) => (
                  <CaseHearingRow
                    key={h.id}
                    caseId={id}
                    hearing={h}
                    canEdit={!archived}
                    onUpdated={(next) =>
                      setHearings((arr) =>
                        arr.map((x) => (x.id === next.id ? next : x)),
                      )
                    }
                  />
                ))}
              </div>
            )}
          </section>
        ) : null}

        {activeSection === "calendar" ? (
          <section>
            <h2 className="mb-3 flex items-center gap-2 text-lg font-semibold">
              <CalendarDays className="size-5 text-[var(--color-navy-700)]" />
              تقويم القضية
            </h2>
            <CaseCalendarPanel caseId={id} archived={archived} />
          </section>
        ) : null}

        {activeSection === "disputePath" ? (
          <section>
            <h2 className="mb-3 flex items-center gap-2 text-lg font-semibold">
              <Scale className="size-5 text-[var(--color-navy-700)]" />
              مسار النزاع
            </h2>
            <DisputeEventsPanel caseId={id} archived={archived} />
          </section>
        ) : null}

        {activeSection === "tasks" ? (
          <section>
            <h2 className="mb-3 flex items-center gap-2 text-lg font-semibold">
              <ClipboardList className="size-5 text-[var(--color-navy-700)]" />
              مهام القضية
            </h2>
            {archived ? null : (
              <div className="mb-4">
                <CaseTaskAddForm caseId={id} onCreated={refresh} />
              </div>
            )}
            {tasks.length === 0 ? (
              <EmptyState
                title="لا مهام"
                description="أضف مهمة من النموذج أعلاه."
                icon={ClipboardList}
              />
            ) : (
              <SectionCard flush>
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>المهمة</TableHead>
                      <TableHead>الأولوية</TableHead>
                      <TableHead>الحالة</TableHead>
                      <TableHead>الموعد</TableHead>
                      <TableHead>المكلَّف</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {tasks.map((t) => (
                      <CaseTaskRow
                        key={t.id}
                        caseId={id}
                        task={t}
                        canEdit={!archived}
                        onUpdated={(next) =>
                          setTasks((arr) =>
                            arr.map((x) => (x.id === next.id ? next : x)),
                          )
                        }
                      />
                    ))}
                  </TableBody>
                </Table>
              </SectionCard>
            )}
          </section>
        ) : null}

        {activeSection === "history" ? (
          <CaseHistoryTimeline caseId={id} reloadKey={loadVersion} />
        ) : null}

        {activeSection === "messages" ? (
          <section>
            <h2 className="mb-3 text-lg font-semibold text-[var(--color-foreground)]">
              محادثات القضية
            </h2>
            <div className="min-h-[22rem] rounded-2xl border border-[var(--color-border)] bg-[var(--color-surface)] p-3 shadow-[var(--shadow-pop)] sm:min-h-[28rem] sm:p-4">
              <CaseMessagesPanel
                caseId={id}
                embedded
                disabled={archived}
              />
            </div>
          </section>
        ) : null}

        {activeSection === "internalNotes" ? (
          <CaseInternalNotesPanel caseId={id} />
        ) : null}

        {activeSection === "appeals" ? (
          <section>
            <h2 className="mb-3 text-lg font-semibold text-[var(--color-foreground)]">
              القضية الأصلية والاستئنافات المرتبطة
            </h2>
            <CaseRelatedCasesPanel caseId={id} parentCaseId={caseData.parentCaseId} />
          </section>
        ) : null}

        {activeSection === "memos" ? (
          <CaseMemosPanel caseId={id} canEdit={!archived} />
        ) : null}

        {activeSection === "circulation" ? (
          <CaseCirculationPanel caseId={id} canCreate={!archived} />
        ) : null}
      </div>
    </div>
  );
}

export default function CaseDetailPage() {
  return (
    <Suspense
      fallback={
        <PageLoading
          label="جارٍ تحميل الصفحة…"
          minHeightClassName="min-h-[40vh]"
          className="w-full"
        />
      }
    >
      <CaseDetailInner />
    </Suspense>
  );
}
