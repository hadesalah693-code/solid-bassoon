"use client";

import { useEffect, useState } from "react";
import {
  BarChart3,
  Briefcase,
  Gavel,
  ListChecks,
  Receipt,
} from "lucide-react";

import { ApiErrorAlert } from "@/components/shared/ApiErrorAlert";
import { PageHeader } from "@/components/shared/PageHeader";
import { PageLoading } from "@/components/shared/PageLoading";
import { SectionCard } from "@/components/shared/SectionCard";
import { StatCard } from "@/components/dashboard/StatCard";
import { EmptyState } from "@/components/shared/EmptyState";
import { dashboardApi } from "@/lib/api/dashboard";
import { ApiRequestError } from "@/lib/api/client";

export default function ReportsPage() {
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [retryToken, setRetryToken] = useState(0);
  const [summary, setSummary] = useState<{
    active: number;
    hearingsToday: number;
    pendingTasks: number;
    overdue: number | null;
  } | null>(null);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      setLoading(true);
      setError(null);
      try {
        const s = await dashboardApi.summary();
        if (cancelled) return;
        setSummary({
          active: s.activeCaseCount,
          hearingsToday: s.hearingsTodayCount,
          pendingTasks: s.pendingTaskCount,
          overdue: s.overdueInvoiceCount,
        });
      } catch (e) {
        if (cancelled) return;
        if (e instanceof ApiRequestError) {
          setError(
            e.status === 403
              ? "لا تملك صلاحية عرض التقارير (GET /api/dashboard/summary)."
              : (e.details?.message ?? e.message),
          );
        } else {
          setError("تعذر تحميل التقارير");
        }
        setSummary(null);
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [retryToken]);

  return (
    <div className="space-y-8">
      <PageHeader
        title="التقارير والتحليلات"
        description="ملخص من الخادم فقط — GET /api/dashboard/summary"
      />

      {error ? (
        <ApiErrorAlert
          message={error}
          title="تعذر تحميل البيانات"
          onRetry={() => setRetryToken((n) => n + 1)}
        />
      ) : null}

      {loading ? (
        <PageLoading
          label="جارٍ تحميل التقارير…"
          minHeightClassName="min-h-[14rem]"
        />
      ) : null}

      {!loading && !error && summary ? (
        <>
          <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
            <StatCard
              label="القضايا النشطة (تقدير الخادم)"
              value={summary.active}
              icon={<Briefcase className="size-4" />}
              accent="navy"
              delay={0}
            />
            <StatCard
              label="جلسات اليوم (تقدير الخادم)"
              value={summary.hearingsToday}
              icon={<Gavel className="size-4" />}
              accent="gold"
              delay={0.05}
            />
            <StatCard
              label="مهام معلّقة (تقدير الخادم)"
              value={summary.pendingTasks}
              icon={<ListChecks className="size-4" />}
              accent="success"
              delay={0.1}
            />
            <StatCard
              label="فواتير متأخرة"
              value={summary.overdue ?? "—"}
              icon={<Receipt className="size-4" />}
              accent={
                summary.overdue != null && summary.overdue > 0
                  ? "danger"
                  : "navy"
              }
              delay={0.15}
            />
          </div>

          {summary.overdue === null ? (
            <SectionCard className="border-[var(--color-border)] bg-[var(--color-surface-muted)]/30 p-6">
              <EmptyState
                icon={Receipt}
                title="بيانات الفواتير غير متاحة من الخادم حالياً"
                description="لم يُرجع الخادم عدد الفواتير المتأخرة — قد تكون وحدة الفوترة غير مفعّلة. لا تُعرض أرقام وهمية."
                className="border-0 bg-transparent py-6"
              />
            </SectionCard>
          ) : null}

          <SectionCard className="border-[var(--color-border)] bg-[var(--color-surface)] p-5 shadow-[var(--shadow-soft)]">
            <div className="flex flex-wrap items-start gap-4">
              <div className="flex size-12 items-center justify-center rounded-2xl bg-[var(--color-navy-50)] text-[var(--color-navy-700)]">
                <BarChart3 className="size-6" />
              </div>
              <div className="min-w-0 text-sm leading-relaxed text-[var(--color-muted-foreground)]">
                <p className="font-semibold text-[var(--color-foreground)]">
                  نطاق التقارير الحالي
                </p>
                <p className="mt-1">
                  جميع الأرقام أعلاها تأتي من نقطة واحدة:{" "}
                  <span className="rounded bg-[var(--color-surface-muted)] px-1.5 py-0.5 text-xs ltr-nums">
                    GET /api/dashboard/summary
                  </span>
                  . لا توجد استدعاءات إضافية.
                </p>
              </div>
            </div>
          </SectionCard>
        </>
      ) : null}
    </div>
  );
}
