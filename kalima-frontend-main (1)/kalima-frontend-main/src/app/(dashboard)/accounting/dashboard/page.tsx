"use client";

import { useCallback, useEffect, useMemo, useState } from "react";

import { ApiErrorAlert } from "@/components/shared/ApiErrorAlert";
import { PageLoading } from "@/components/shared/PageLoading";
import { SectionCard } from "@/components/shared/SectionCard";

import { accountingReportsApi } from "@/lib/api/accounting-extended";
import { formatDate } from "@/lib/utils/format-date";
import { formatMoney } from "@/lib/utils/format-money";
import type { AccountingDashboard, PortfolioSummary } from "@/types/accounting-extended";

const PERIODS = [
  { key: "month", label: "هذا الشهر", from: () => startOfMonth() },
  { key: "quarter", label: "هذا الربع", from: () => startOfQuarter() },
  { key: "year", label: "هذه السنة", from: () => `${new Date().getFullYear()}-01-01` },
] as const;

type PeriodKey = (typeof PERIODS)[number]["key"];

function isoToday() {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(
    d.getDate(),
  ).padStart(2, "0")}`;
}

function startOfMonth() {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-01`;
}

function startOfQuarter() {
  const d = new Date();
  const firstMonth = Math.floor(d.getMonth() / 3) * 3 + 1;
  return `${d.getFullYear()}-${String(firstMonth).padStart(2, "0")}-01`;
}

function Stat({ label, value, tone }: { label: string; value: string; tone?: "danger" | "info" }) {
  return (
    <div className="rounded-2xl border border-[var(--color-border)] bg-[var(--color-surface)] p-4">
      <p className="text-xs text-[var(--color-muted-foreground)]">{label}</p>
      <p
        className={`mt-1 text-xl font-semibold ltr-nums ${
          tone === "danger" ? "text-[var(--color-danger)]" : tone === "info" ? "text-[var(--color-info)]" : ""
        }`}
      >
        {value}
      </p>
    </div>
  );
}

/**
 * Management dashboard.
 *
 * <p>Everything here is read from the ledger on request, so the page has no state of
 * its own to fall out of date: if a figure looks wrong, the accounts say the same
 * thing. Period presets cover the three spans a partner actually asks about.
 */
export default function AccountingDashboardPage() {
  const [period, setPeriod] = useState<PeriodKey>("month");
  const [dashboard, setDashboard] = useState<AccountingDashboard | null>(null);
  const [summary, setSummary] = useState<PortfolioSummary | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const range = useMemo(() => {
    const preset = PERIODS.find((p) => p.key === period);
    return { from: preset?.from() ?? startOfMonth(), to: isoToday() };
  }, [period]);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const [d, s] = await Promise.all([
        accountingReportsApi.dashboard(range),
        accountingReportsApi.profitabilitySummary(range),
      ]);
      setDashboard(d);
      setSummary(s);
    } catch (e) {
      setError(e instanceof Error ? e.message : "تعذر تحميل البيانات");
    } finally {
      setLoading(false);
    }
  }, [range]);

  useEffect(() => {
    void load();
  }, [load]);

  if (loading) return <PageLoading label="جارٍ تحميل المؤشرات" />;

  return (
    <div className="space-y-6">
      <ApiErrorAlert message={error ?? ""} onRetry={load} />

      <div className="flex flex-wrap gap-2">
        {PERIODS.map((p) => (
          <button
            key={p.key}
            type="button"
            onClick={() => setPeriod(p.key)}
            className={`rounded-xl px-3 py-1.5 text-sm transition-colors ${
              period === p.key
                ? "bg-[var(--color-gold-500)] text-white"
                : "bg-[var(--color-surface)] text-[var(--color-muted-foreground)]"
            }`}
          >
            {p.label}
          </button>
        ))}
        {dashboard && (
          <span className="ms-auto self-center text-xs text-[var(--color-muted-foreground)] ltr-nums">
            {formatDate(dashboard.from)} — {formatDate(dashboard.to)}
          </span>
        )}
      </div>

      {dashboard && (
        <>
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
            <Stat label="النقدية" value={formatMoney(dashboard.cash)} />
            <Stat label="الذمم المدينة" value={formatMoney(dashboard.receivables)} />
            <Stat label="الذمم الدائنة" value={formatMoney(dashboard.payables)} tone="danger" />
            <Stat label="أرصدة العملاء" value={formatMoney(dashboard.clientLiabilities)} tone="info" />
          </div>

          <div className="grid gap-4 sm:grid-cols-3">
            <Stat label="الإيرادات" value={formatMoney(dashboard.revenue)} />
            <Stat label="المصروفات" value={formatMoney(dashboard.expenses)} />
            <Stat label="صافي الربح" value={formatMoney(dashboard.netProfit)} />
          </div>

          <div className="grid gap-4 sm:grid-cols-3">
            <Stat label="ذمم متأخرة" value={formatMoney(dashboard.arOverdue)} tone="danger" />
            <Stat label="ذمم الموردين" value={formatMoney(dashboard.outstandingPayable)} />
            <Stat label="تسويات جارية" value={String(dashboard.reconciliationsInProgress)} />
          </div>

          {dashboard.overdueBills > 0 && (
            <p className="text-sm text-[var(--color-danger)]">
              {dashboard.overdueBills} فاتورة مورد متأخرة عن الاستحقاق.
            </p>
          )}
        </>
      )}

      {summary && (
        <>
          <SectionCard title="ملخص المحفظة">
            <div className="grid gap-4 sm:grid-cols-4">
              <Stat label="إجمالي الإيراد" value={formatMoney(summary.revenue)} />
              <Stat label="إجمالي التكلفة" value={formatMoney(summary.cost)} />
              <Stat label="صافي الربح" value={formatMoney(summary.profit)} />
              <Stat label="الهامش" value={`${summary.marginPercent}%`} />
            </div>
            <p className="mt-4 text-sm text-[var(--color-muted-foreground)]">
              قضايا مربحة: {summary.profitableCases} / {summary.breakevenCases} / {summary.losingCases}
            </p>
          </SectionCard>
        </>
      )}
    </div>
  );
}