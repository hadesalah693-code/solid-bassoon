"use client";

import { useCallback, useEffect, useMemo, useState, type FormEvent } from "react";

import { AccountingWriteOnly } from "@/components/accounting/AccountingRoleGate";
import { ApiErrorAlert } from "@/components/shared/ApiErrorAlert";
import { EmptyState } from "@/components/shared/EmptyState";
import { PageLoading } from "@/components/shared/PageLoading";
import { SectionCard } from "@/components/shared/SectionCard";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";

import { accountsApi } from "@/lib/api/accounting";
import { cn } from "@/lib/utils/cn";
import { budgetsApi } from "@/lib/api/accounting-extended";
import { notifyApiError, notifySuccess } from "@/lib/feedback";
import { formatDate } from "@/lib/utils/format-date";
import { formatMoney } from "@/lib/utils/format-money";
import type { AccountResponseDto } from "@/types/accounting";
import type { Budget, BudgetPeriod } from "@/types/accounting-extended";

const PERIOD_LABELS: Record<BudgetPeriod, string> = {
  MONTH: "شهري",
  QUARTER: "ربع سنوي",
  YEAR: "سنوي",
  CUSTOM: "مخصص",
};

const MONTHS = [
  "يناير", "فبراير", "مارس", "أبريل", "مايو", "يونيو",
  "يوليو", "أغسطس", "سبتمبر", "أكتوبر", "نوفمبر", "ديسمبر",
];

const selectClassName = cn(
  "flex h-10 w-full cursor-pointer appearance-none rounded-xl border border-[var(--color-border)] bg-[var(--color-surface)] px-3.5 py-2 text-sm text-[var(--color-foreground)]",
  "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--color-gold-400)]",
);

/**
 * Budgets against actuals.
 *
 * <p>Actual spend is read from the ledger for each budget's own date range, so a
 * budget can never claim it is under target while the bank account says otherwise.
 * The month or quarter is part of the form because a monthly budget without one
 * would silently cover a whole year.
 */
export default function BudgetsPage() {
  const [budgets, setBudgets] = useState<Budget[]>([]);
  const [expenseAccounts, setExpenseAccounts] = useState<AccountResponseDto[]>([]);
  const [year, setYear] = useState(new Date().getFullYear());
  const [period, setPeriod] = useState<BudgetPeriod>("MONTH");
  const [month, setMonth] = useState(new Date().getMonth() + 1);
  const [quarter, setQuarter] = useState(Math.floor(new Date().getMonth() / 3) + 1);
  const [name, setName] = useState("");
  const [accountId, setAccountId] = useState("");
  const [amount, setAmount] = useState("");
  const [saving, setSaving] = useState(false);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const [b, accounts] = await Promise.all([
        budgetsApi.list(year),
        accountsApi.list("EXPENSE"),
      ]);
      setBudgets(b);
      setExpenseAccounts(accounts);
    } catch (e) {
      setError(e instanceof Error ? e.message : "تعذر تحميل البيانات");
    } finally {
      setLoading(false);
    }
  }, [year]);

  useEffect(() => {
    void load();
  }, [load]);

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    setSaving(true);
    try {
      await budgetsApi.create({
        name: name.trim(),
        fiscalYear: year,
        period,
        ...(period === "MONTH" ? { periodMonth: month } : {}),
        ...(period === "QUARTER" ? { quarter } : {}),
        accountId: accountId || undefined,
        budgetAmount: Number(amount),
      });
      notifySuccess("تم إنشاء الميزانية");
      setName("");
      setAmount("");
      await load();
    } catch (e) {
      notifyApiError(e, "تعذر إنشاء الميزانية");
    } finally {
      setSaving(false);
    }
  };

  const overBudget = useMemo(() => budgets.filter((b) => b.overBudget).length, [budgets]);

  if (loading) return <PageLoading label="جارٍ تحميل الميزانيات" />;

  return (
    <div className="space-y-6">
      <ApiErrorAlert message={error ?? ""} onRetry={load} />

      <div className="flex flex-wrap items-center gap-3">
        <select aria-label="السنة" className={selectClassName} value={year} onChange={(e) => setYear(Number(e.target.value))}>{[year - 2, year - 1, year, year + 1].map((y) => (<option key={y} value={y}>{y}</option>))}</select>
        <span className="text-sm text-[var(--color-muted-foreground)]">
          {budgets.length} ميزانية · {overBudget} تجاوزت المستهدف
        </span>
      </div>

      <AccountingWriteOnly>
        <SectionCard title="ميزانية جديدة">
          <form onSubmit={submit} className="grid gap-4 sm:grid-cols-2 lg:grid-cols-6">
            <div className="grid gap-1.5 lg:col-span-2">
              <Label htmlFor="budget-name">الاسم</Label>
              <Input
                id="budget-name"
                value={name}
                onChange={(e) => setName(e.target.value)}
                required
                maxLength={200}
              />
            </div>
            <div className="grid gap-1.5">
              <Label htmlFor="budget-period">الفترة</Label>
              <select id="budget-period" className={selectClassName} value={period} onChange={(e) => setPeriod(e.target.value as BudgetPeriod)}>{Object.entries(PERIOD_LABELS).map(([key, label]) => (<option key={key} value={key}>{label}</option>))}</select>
            </div>
            {period === "MONTH" && (
              <div className="grid gap-1.5">
                <Label htmlFor="budget-month">الشهر</Label>
                <select id="budget-month" className={selectClassName} value={month} onChange={(e) => setMonth(Number(e.target.value))}>{MONTHS.map((label, index) => (<option key={label} value={index + 1}>{label}</option>))}</select>
              </div>
            )}
            {period === "QUARTER" && (
              <div className="grid gap-1.5">
                <Label htmlFor="budget-quarter">الربع</Label>
                <select id="budget-quarter" className={selectClassName} value={quarter} onChange={(e) => setQuarter(Number(e.target.value))}>{[1, 2, 3, 4].map((q) => (<option key={q} value={q}>الربع {q}</option>))}</select>
              </div>
            )}
            <div className="grid gap-1.5">
              <Label htmlFor="budget-account">حساب المصروفات</Label>
              <select id="budget-account" className={selectClassName} value={accountId} onChange={(e) => setAccountId(e.target.value)}><option value="">كل المصروفات</option>{expenseAccounts.map((a) => (<option key={a.id} value={a.id}>{a.code} - {a.nameAr}</option>))}</select>
            </div>
            <div className="grid gap-1.5">
              <Label htmlFor="budget-amount">المبلغ</Label>
              <Input
                id="budget-amount"
                type="number"
                min="0"
                step="0.01"
                value={amount}
                onChange={(e) => setAmount(e.target.value)}
                required
              />
            </div>
            <div className="flex items-end">
              <Button type="submit" disabled={saving}>
                {saving ? "جارٍ الحفظ" : "حفظ"}
              </Button>
            </div>
          </form>
        </SectionCard>
      </AccountingWriteOnly>

      <SectionCard title="الميزانيات مقابل الفعلي">
        {budgets.length === 0 ? (
          <EmptyState title="لا توجد ميزانيات لهذه السنة" />
        ) : (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>الاسم</TableHead>
                <TableHead>الفترة</TableHead>
                <TableHead>الحساب</TableHead>
                <TableHead className="text-end">المستهدف</TableHead>
                <TableHead className="text-end">الفعلي</TableHead>
                <TableHead className="text-end">الفرق</TableHead>
                <TableHead className="text-end">نسبة الصرف</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {budgets.map((b) => (
                <TableRow key={b.id}>
                  <TableCell>{b.name}</TableCell>
                  <TableCell>
                    {PERIOD_LABELS[b.period]}
                    <span className="block text-xs text-[var(--color-muted-foreground)] ltr-nums">
                      {formatDate(b.periodStart)} - {formatDate(b.periodEnd)}
                    </span>
                  </TableCell>
                  <TableCell className="ltr-nums">{b.accountCode ?? "كل المصروفات"}</TableCell>
                  <TableCell className="text-end ltr-nums">{formatMoney(b.budgetAmount)}</TableCell>
                  <TableCell className="text-end ltr-nums">{formatMoney(b.actualAmount)}</TableCell>
                  <TableCell
                    className={`text-end ltr-nums ${b.variance < 0 ? "text-[var(--color-danger)]" : ""}`}
                  >
                    {formatMoney(b.variance)}
                  </TableCell>
                  <TableCell className="text-end">
                    <Badge tone={b.overBudget ? "danger" : "neutral"}>
                      {b.utilisationPercent}%
                    </Badge>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
      </SectionCard>
    </div>
  );
}