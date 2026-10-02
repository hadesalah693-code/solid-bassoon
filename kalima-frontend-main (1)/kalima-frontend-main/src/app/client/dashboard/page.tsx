"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import {
  Bell,
  Briefcase,
  CalendarClock,
  CheckCircle2,
  Layers,
  Radio,
} from "lucide-react";

import { PageHeader } from "@/components/shared/PageHeader";
import { PageLoading } from "@/components/shared/PageLoading";
import { SectionCard } from "@/components/shared/SectionCard";
import { CaseCard } from "@/components/cases/CaseCard";
import { ApiErrorAlert } from "@/components/shared/ApiErrorAlert";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { StatCard } from "@/components/dashboard/StatCard";
import { NotificationItem } from "@/components/notifications/NotificationItem";
import { getStoredUser } from "@/lib/auth/session";
import { casesApi } from "@/lib/api/cases";
import { notificationsApi } from "@/lib/api/notifications";
import { notificationToApp } from "@/lib/notifications/map-app-notification";
import { caseFileFromDto } from "@/types/api";
import { ApiRequestError } from "@/lib/api/client";
import type { CaseFile } from "@/types/case";
import type { AppNotification } from "@/types/notification";
import { notifyApiError } from "@/lib/feedback";
import { findNextUpcomingHearing, type NextUpcomingHearing } from "@/lib/client-portal/find-next-upcoming-hearing";
import { formatDateTime } from "@/lib/utils/format-date";

const CASES_BASE = "/client/cases";
/** Recent cases + pool for "next hearing" scan (by updatedAt). */
const CASES_LIST_PAGE_SIZE = 25;
const HEARING_MAX_CASES = 15;
const HEARING_CONCURRENCY = 4;
const RECENT_SIZE = 3;
const NOTIF_PREVIEW = 3;

function isCaseEligibleForHearingScan(
  c: { status: string; archivedAt: string | null },
): boolean {
  if (c.archivedAt != null) return false;
  if (c.status === "CLOSED" || c.status === "ARCHIVED") return false;
  return true;
}

export default function ClientDashboardPage() {
  const router = useRouter();
  const [greeting, setGreeting] = useState("مرحباً");
  const [stats, setStats] = useState<{
    total: number;
    active: number;
    closed: number;
  } | null>(null);
  const [recentCases, setRecentCases] = useState<CaseFile[]>([]);
  const [nextHearing, setNextHearing] = useState<NextUpcomingHearing | null>(
    null,
  );
  const [hearingLoadNote, setHearingLoadNote] = useState<string | null>(null);
  const [notifs, setNotifs] = useState<AppNotification[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [version, setVersion] = useState(0);

  useEffect(() => {
    const u = getStoredUser();
    if (u?.fullName?.trim()) {
      setGreeting(`مرحباً، ${u.fullName.trim()}`);
    }
  }, []);

  useEffect(() => {
    const u = getStoredUser();
    if (!u?.id) return;
    let cancelled = false;
    (async () => {
      setLoading(true);
      setError(null);
      setHearingLoadNote(null);
      try {
        const [[allMeta, closedMeta, archivedMeta], casesPage, nPage] =
          await Promise.all([
            Promise.all([
              casesApi.list({
                clientId: u.id,
                page: 0,
                size: 1,
                includeArchived: true,
              }),
              casesApi.list({
                clientId: u.id,
                page: 0,
                size: 1,
                status: "CLOSED",
                includeArchived: true,
              }),
              casesApi.list({
                clientId: u.id,
                page: 0,
                size: 1,
                status: "ARCHIVED",
                includeArchived: true,
              }),
            ]),
            casesApi.list({
              clientId: u.id,
              page: 0,
              size: CASES_LIST_PAGE_SIZE,
              includeArchived: true,
              sort: "updatedAt,desc",
            }),
            notificationsApi.list({ page: 0, size: NOTIF_PREVIEW }),
          ]);
        if (cancelled) return;
        const total = allMeta.totalElements;
        const closed = closedMeta.totalElements;
        const archived = archivedMeta.totalElements;
        const active = Math.max(0, total - closed - archived);
        setStats({ total, active, closed });
        setRecentCases(
          casesPage.content
            .slice(0, RECENT_SIZE)
            .map((row) => caseFileFromDto(row)),
        );
        setNotifs(
          nPage.content.map((n) =>
            notificationToApp(n, { casesBasePath: CASES_BASE }),
          ),
        );
        const hearingRefs = casesPage.content
          .filter((c) => isCaseEligibleForHearingScan(c))
          .map((c) => ({
            id: c.id,
            title: c.title,
            caseNumber: c.caseNumber,
          }));
        try {
          if (hearingRefs.length === 0) {
            if (!cancelled) setNextHearing(null);
          } else {
            const next = await findNextUpcomingHearing(hearingRefs, {
              maxCases: HEARING_MAX_CASES,
              concurrency: HEARING_CONCURRENCY,
            });
            if (cancelled) return;
            setNextHearing(next);
          }
        } catch {
          if (cancelled) return;
          setNextHearing(null);
          setHearingLoadNote("تعذر تحميل جدول أقرب جلسة.");
        }
      } catch (e) {
        if (cancelled) return;
        if (e instanceof ApiRequestError) {
          setError(e.details?.message ?? e.message);
        } else {
          setError("تعذر تحميل البيانات");
        }
        setStats(null);
        setRecentCases([]);
        setNotifs([]);
        setNextHearing(null);
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [version]);

  async function markRead(id: string) {
    try {
      await notificationsApi.markRead(id);
      setNotifs((prev) => prev.map((n) => (n.id === id ? { ...n, read: true } : n)));
    } catch (e) {
      notifyApiError(e, "تعذر تحديث حالة القراءة");
    }
  }

  function openNotification(n: AppNotification) {
    if (n.href) router.push(n.href);
  }

  if (loading) {
    return (
      <PageLoading
        label="جارٍ تحميل لوحة الموكّل…"
        minHeightClassName="min-h-[40vh]"
        className="w-full"
      />
    );
  }

  if (error) {
    return (
      <div className="space-y-6">
        <PageHeader
          title={greeting}
          description="تعذر مزامنة بيانات لوحتك."
        />
        <ApiErrorAlert
          message={error}
          title="تعذر التحميل"
          onRetry={() => setVersion((v) => v + 1)}
        />
      </div>
    );
  }

  return (
    <div className="space-y-8">
      <PageHeader
        title={greeting}
        description="نظرة موحّدة على قضاياك، أقرب جلسة، وآخر التنبيهات."
        actions={
          <div className="flex flex-wrap gap-2">
            <Button variant="gold" size="md" asChild>
              <Link href="/client/cases" className="inline-flex items-center gap-1">
                <Briefcase className="size-4" />
                قضاياي
              </Link>
            </Button>
            <Button variant="outline" size="md" asChild>
              <Link
                href="/client/notifications"
                className="inline-flex items-center gap-1"
              >
                <Bell className="size-4" />
                الإشعارات
              </Link>
            </Button>
          </div>
        }
      />

      {stats ? (
        <section className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
          <StatCard
            label="إجمالي القضايا"
            value={stats.total}
            icon={<Layers />}
            accent="navy"
            delay={0}
          />
          <StatCard
            label="قضايا نشطة"
            value={stats.active}
            icon={<Radio />}
            accent="gold"
            delay={0.05}
          />
          <StatCard
            label="مغلقة"
            value={stats.closed}
            icon={<CheckCircle2 />}
            accent="success"
            delay={0.1}
          />
        </section>
      ) : null}
      <p className="text-xs text-[var(--color-muted-foreground)]">
        «نشطة» = كل ما عدا «مغلقة» و«مؤرشفة» ضمن نطاق حسابك. الأرقام من الخادم
        (عدّ متعدد لـ GET /api/cases).
      </p>

      <section>
        <div className="mb-4 flex flex-wrap items-end justify-between gap-3">
          <div>
            <h2 className="text-lg font-semibold">أقرب جلسة قادمة</h2>
            <p className="text-sm text-[var(--color-muted-foreground)]">
              نبحث فقط في الملفات غير المغلقة/المؤرشفة، حتى {HEARING_MAX_CASES}{" "}
              ملفاً (من آخر {CASES_LIST_PAGE_SIZE} بتحديث حديث)
            </p>
          </div>
        </div>
        {hearingLoadNote ? (
          <p className="text-sm text-amber-800/90 dark:text-amber-200/90">
            {hearingLoadNote}
          </p>
        ) : null}
        {nextHearing ? (
          <Link
            href={`${CASES_BASE}/${nextHearing.caseId}`}
            className="block rounded-xl outline-none transition focus-visible:ring-2 focus-visible:ring-[var(--color-gold-500)] focus-visible:ring-offset-2 focus-visible:ring-offset-[var(--color-background)]"
          >
            <Card className="h-full border-[var(--color-border)] bg-gradient-to-br from-[var(--color-surface)] to-[var(--color-navy-50)]/40 p-5 shadow-[var(--shadow-sm)] transition hover:border-[var(--color-gold-500)]/50 hover:shadow-md">
              <div className="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
                <div className="min-w-0 space-y-2">
                  <div className="flex items-center gap-2 text-sm font-semibold text-[var(--color-navy-800)]">
                    <CalendarClock className="size-5 shrink-0 text-[var(--color-gold-500)]" />
                    <span>جلسة قادمة</span>
                  </div>
                  <p className="text-2xl font-bold tracking-tight text-[var(--color-foreground)] ltr-nums">
                    {formatDateTime(nextHearing.hearing.hearingDate)}
                  </p>
                  <p className="text-sm font-medium text-[var(--color-foreground)]">
                    {nextHearing.hearing.courtName}
                    {nextHearing.hearing.chamber
                      ? ` · ${nextHearing.hearing.chamber}`
                      : null}
                  </p>
                  <p className="text-xs text-[var(--color-muted-foreground)]">
                    الملف:{" "}
                    <span className="font-medium text-[var(--color-foreground)] ltr-nums">
                      #{nextHearing.caseNumber}
                    </span>{" "}
                    — {nextHearing.caseTitle}
                  </p>
                </div>
                <span
                  className="inline-flex shrink-0 items-center self-start rounded-md bg-[var(--color-gold-500)] px-4 py-2 text-sm font-medium text-white"
                >
                  تفاصيل الملف
                </span>
              </div>
            </Card>
          </Link>
        ) : !hearingLoadNote ? (
          <Card className="p-6 text-center text-sm text-[var(--color-muted-foreground)]">
            لا توجد جلسة قادمة في النطاق الممسوح، أو لا توجد جلسات بتاريخ لاحق.
          </Card>
        ) : null}
      </section>

      <section>
        <div className="mb-3 flex items-center justify-between">
          <h2 className="text-lg font-semibold">أحدث القضايا</h2>
          <Button variant="link" className="text-sm" asChild>
            <Link href="/client/cases">عرض الكل</Link>
          </Button>
        </div>
        {recentCases.length === 0 ? (
          <Card className="p-8 text-center text-sm text-[var(--color-muted-foreground)]">
            لا توجد قضايا مرتبطة بحسابك حالياً.
          </Card>
        ) : (
          <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
            {recentCases.map((c) => (
              <CaseCard key={c.id} data={c} casesBasePath={CASES_BASE} />
            ))}
          </div>
        )}
      </section>

      <section id="client-notifications" className="scroll-mt-24">
        <div className="mb-3 flex flex-wrap items-center justify-between gap-2">
          <div className="flex items-center gap-2">
            <Bell className="size-5 text-[var(--color-navy-700)]" />
            <h2 className="text-lg font-semibold">آخر الإشعارات</h2>
          </div>
          <Button variant="link" className="h-auto p-0 text-sm" asChild>
            <Link href="/client/notifications">عرض الكل</Link>
          </Button>
        </div>
        <SectionCard className="p-0">
          {notifs.length === 0 ? (
            <p className="p-5 text-sm text-[var(--color-muted-foreground)]">
              لا إشعارات لعرضها.
            </p>
          ) : (
            <ul className="flex flex-col gap-3 p-4">
              {notifs.map((n) => (
                <NotificationItem
                  key={n.id}
                  data={n}
                  onMarkAsRead={markRead}
                  onRowClick={openNotification}
                />
              ))}
            </ul>
          )}
        </SectionCard>
        <p className="mt-2 text-xs text-[var(--color-muted-foreground)]">
          آخر {NOTIF_PREVIEW} إشعارات. الجلسات التفصيلية من صفحة الملف.
        </p>
      </section>
    </div>
  );
}
