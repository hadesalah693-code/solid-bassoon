"use client";

import { useEffect, useMemo, useState } from "react";
import { FileSpreadsheet, Users } from "lucide-react";

import { AccountingRoleGate } from "@/components/accounting/AccountingRoleGate";
import { DisputeModuleHero } from "@/components/disputes/DisputeModuleHero";
import { ApiErrorAlert } from "@/components/shared/ApiErrorAlert";
import { EmptyState } from "@/components/shared/EmptyState";
import { PageLoading } from "@/components/shared/PageLoading";
import { SearchInput } from "@/components/shared/SearchInput";
import { SectionCard } from "@/components/shared/SectionCard";
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

import { clientAccountsApi, reportsApi } from "@/lib/api/accounting";
import { ApiRequestError } from "@/lib/api/client";
import { notifySuccess } from "@/lib/feedback";
import { useDebounce } from "@/hooks/use-debounce";
import { formatDate } from "@/lib/utils/format-date";
import { formatMoney } from "@/lib/utils/format-money";
import { cn } from "@/lib/utils/cn";
import type { ClientAccountResponseDto, ClientStatementResponseDto } from "@/types/accounting";

const inputLtrClassName = "ltr-nums text-start";

function startOfYear() {
  return `${new Date().getFullYear()}-01-01`;
}

function today() {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(
    d.getDate(),
  ).padStart(2, "0")}`;
}

export default function ClientAccountsPage() {
  return (
    <AccountingRoleGate>
      <ClientAccountsPageInner />
    </AccountingRoleGate>
  );
}

function ClientAccountsPageInner() {
  const [rows, setRows] = useState<ClientAccountResponseDto[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [search, setSearch] = useState("");
  const debouncedSearch = useDebounce(search, 300);
  const [fetchId, setFetchId] = useState(0);

  const [selected, setSelected] = useState<ClientAccountResponseDto | null>(null);
  const [from, setFrom] = useState(startOfYear);
  const [to, setTo] = useState(today);
  const [statement, setStatement] = useState<ClientStatementResponseDto | null>(null);
  const [stmtLoading, setStmtLoading] = useState(false);
  const [stmtError, setStmtError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      setLoading(true);
      setError(null);
      try {
        const res = await clientAccountsApi.list(debouncedSearch);
        if (!cancelled) setRows(res);
      } catch (e) {
        if (!cancelled) {
          setError(e instanceof ApiRequestError ? e.message : "تعذّر تحميل حسابات العملاء");
          setRows([]);
        }
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [debouncedSearch, fetchId]);

  useEffect(() => {
    if (!selected) {
      setStatement(null);
      return;
    }
    let cancelled = false;
    (async () => {
      setStmtLoading(true);
      setStmtError(null);
      try {
        const res = await reportsApi.clientStatement(selected.personId, from || null, to || null);
        if (!cancelled) setStatement(res);
      } catch (e) {
        if (!cancelled) {
          setStmtError(e instanceof ApiRequestError ? e.message : "تعذّر تحميل كشف الحساب");
          setStatement(null);
        }
      } finally {
        if (!cancelled) setStmtLoading(false);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [selected, from, to]);

  const totals = useMemo(
    () => ({
      invoiced: rows.reduce((s, r) => s + r.invoiced, 0),
      paid: rows.reduce((s, r) => s + r.paid, 0),
      outstanding: rows.reduce((s, r) => s + r.outstanding, 0),
    }),
    [rows],
  );

  async function handleExport() {
    if (!statement) return;
    const header = [
      "التاريخ",
      "المرجع",
      "البيان",
      "مدين",
      "دائن",
      "الرصيد",
    ];
    const lines = statement.rows.map((r) => [
      r.date,
      r.reference,
      (r.description ?? "").replace(/"/g, '""'),
      r.debit.toFixed(2),
      r.credit.toFixed(2),
      r.balance.toFixed(2),
    ]);
    const csv = [header, ...lines]
      .map((cols) => cols.map((c) => `"${c}"`).join(","))
      .join("\r\n");
    const blob = new Blob([`﻿${csv}`], { type: "text/csv;charset=utf-8;" });
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = `كشف-${statement.clientName}-${statement.from}_${statement.to}.csv`;
    a.click();
    URL.revokeObjectURL(url);
    notifySuccess("تم تنزيل الكشف");
  }

  return (
    <div dir="rtl" className="space-y-6 pb-10">
      <DisputeModuleHero
        icon={Users}
        eyebrow="المالية والمحاسبة"
        title="حسابات العملاء"
        description="أرصدة الذمم المدينة على مستوى العميل. يُنشأ حساب العميل تلقائياً عند إصدار أول فاتورة له."
        badges={
          !loading && !error ? (
            <span className="rounded-lg bg-white/10 px-2.5 py-1 text-xs font-medium text-[var(--color-gold-200)] ring-1 ring-white/15">
              {rows.length.toLocaleString("ar-EG")} حساب
            </span>
          ) : null
        }
      />

      <div className="grid gap-3 sm:grid-cols-3">
        <div className="rounded-xl border border-[var(--color-border)] bg-[var(--color-surface)] p-4">
          <p className="text-xs text-[var(--color-muted-foreground)]">إجمالي المفوتر</p>
          <p className="mt-1 text-lg font-bold ltr-nums">{formatMoney(totals.invoiced)}</p>
        </div>
        <div className="rounded-xl border border-[var(--color-border)] bg-[var(--color-surface)] p-4">
          <p className="text-xs text-[var(--color-muted-foreground)]">إجمالي المحصّل</p>
          <p className="mt-1 text-lg font-bold ltr-nums text-[var(--color-success)]">
            {formatMoney(totals.paid)}
          </p>
        </div>
        <div className="rounded-xl border border-[var(--color-gold-200)] bg-[var(--color-gold-50)] p-4">
          <p className="text-xs text-[var(--color-gold-600)]">إجمالي المتبقي</p>
          <p className="mt-1 text-lg font-bold ltr-nums text-[var(--color-gold-600)]">
            {formatMoney(totals.outstanding)}
          </p>
        </div>
      </div>

      <SectionCard title="البحث" contentClassName="space-y-3">
        <SearchInput value={search} onChange={setSearch} placeholder="بحث باسم العميل أو حسابه..." />
      </SectionCard>

      {error ? (
        <ApiErrorAlert message={error} onRetry={() => setFetchId((n) => n + 1)} />
      ) : null}

      {loading ? (
        <PageLoading label="جاري تحميل حسابات العملاء..." />
      ) : rows.length === 0 ? (
        <SectionCard>
          <EmptyState
            icon={Users}
            title={debouncedSearch.trim() ? "لا توجد نتائج" : "لا توجد حسابات عملاء"}
            description={
              debouncedSearch.trim()
                ? "جرّب تعديل البحث."
                : "سيُنشأ حساب العميل تلقائياً عند إصدار أول فاتورة له."
            }
          />
        </SectionCard>
      ) : (
        <SectionCard flush title="الأرصدة">
          <div className="overflow-x-auto">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead className="text-right">العميل</TableHead>
                  <TableHead className="text-right">الحساب</TableHead>
                  <TableHead className="text-right">الرصيد الافتتاحي</TableHead>
                  <TableHead className="text-right">رصيد الدفتر</TableHead>
                  <TableHead className="text-right">المفوتر</TableHead>
                  <TableHead className="text-right">المحصّل</TableHead>
                  <TableHead className="text-right">المتبقي</TableHead>
                  <TableHead className="text-right">حد الائتمان</TableHead>
                  <TableHead className="text-right">إجراءات</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {rows.map((row) => (
                  <TableRow key={row.id} className="transition-colors hover:bg-[var(--color-navy-50)]/40">
                    <TableCell className="font-medium">{row.clientName}</TableCell>
                    <TableCell className="ltr-nums text-[var(--color-muted-foreground)]">{row.accountCode}</TableCell>
                    <TableCell className="whitespace-nowrap ltr-nums">{formatMoney(row.openingBalance)}</TableCell>
                    <TableCell
                      className={cn(
                        "whitespace-nowrap ltr-nums font-medium",
                        row.balance < 0 ? "text-[var(--color-danger)]" : "",
                      )}
                    >
                      {formatMoney(row.balance)}
                    </TableCell>
                    <TableCell className="whitespace-nowrap ltr-nums">{formatMoney(row.invoiced)}</TableCell>
                    <TableCell className="whitespace-nowrap ltr-nums text-[var(--color-success)]">
                      {formatMoney(row.paid)}
                    </TableCell>
                    <TableCell className="whitespace-nowrap ltr-nums font-semibold">
                      {formatMoney(row.outstanding)}
                    </TableCell>
                    <TableCell className="whitespace-nowrap ltr-nums text-[var(--color-muted-foreground)]">
                      {row.creditLimit != null ? formatMoney(row.creditLimit) : "—"}
                    </TableCell>
                    <TableCell>
                      <Button variant="outline" size="sm" onClick={() => setSelected(row)}>
                        كشف حساب
                      </Button>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </div>
        </SectionCard>
      )}

      {selected ? (
        <SectionCard
          title={`كشف حساب — ${selected.clientName}`}
          description="حركة حساب الذمم المدينة خلال الفترة المحددة"
          action={
            <Button variant="outline" size="sm" onClick={handleExport} disabled={!statement || statement.rows.length === 0}>
              <FileSpreadsheet className="size-3.5" />
              تنزيل CSV
            </Button>
          }
        >
          <div className="mb-4 grid gap-3 sm:grid-cols-3">
            <div className="space-y-1.5">
              <Label htmlFor="ca-from">من تاريخ</Label>
              <Input id="ca-from" type="date" value={from} onChange={(e) => setFrom(e.target.value)} className={inputLtrClassName} />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="ca-to">إلى تاريخ</Label>
              <Input id="ca-to" type="date" value={to} onChange={(e) => setTo(e.target.value)} className={inputLtrClassName} />
            </div>
            <div className="flex items-end">
              <Button variant="ghost" onClick={() => setSelected(null)}>
                إغلاق الكشف
              </Button>
            </div>
          </div>

          {stmtError ? <ApiErrorAlert message={stmtError} /> : null}

          {stmtLoading ? (
            <PageLoading label="جاري تحميل كشف الحساب..." />
          ) : !statement ? null : (
            <>
              <div className="mb-4 grid gap-3 sm:grid-cols-4">
                <div className="rounded-xl border border-[var(--color-border)] bg-[var(--color-surface-muted)] p-3">
                  <p className="text-xs text-[var(--color-muted-foreground)]">رصيد افتتاحي</p>
                  <p className="mt-0.5 font-semibold ltr-nums">{formatMoney(statement.openingBalance)}</p>
                </div>
                <div className="rounded-xl border border-[var(--color-border)] bg-[var(--color-surface-muted)] p-3">
                  <p className="text-xs text-[var(--color-muted-foreground)]">إجمالي المدين</p>
                  <p className="mt-0.5 font-semibold ltr-nums">{formatMoney(statement.totalDebit)}</p>
                </div>
                <div className="rounded-xl border border-[var(--color-border)] bg-[var(--color-surface-muted)] p-3">
                  <p className="text-xs text-[var(--color-muted-foreground)]">إجمالي الدائن</p>
                  <p className="mt-0.5 font-semibold ltr-nums">{formatMoney(statement.totalCredit)}</p>
                </div>
                <div className="rounded-xl border border-[var(--color-gold-200)] bg-[var(--color-gold-50)] p-3">
                  <p className="text-xs text-[var(--color-gold-600)]">الرصيد الختامي</p>
                  <p className="mt-0.5 font-bold ltr-nums text-[var(--color-gold-600)]">
                    {formatMoney(statement.closingBalance)}
                  </p>
                </div>
              </div>

              {statement.rows.length === 0 ? (
                <EmptyState
                  icon={Users}
                  title="لا توجد حركة"
                  description="لا توجد قيود على حساب العميل خلال الفترة المحددة."
                />
              ) : (
                <div className="overflow-x-auto">
                  <Table>
                    <TableHeader>
                      <TableRow>
                        <TableHead className="text-right">التاريخ</TableHead>
                        <TableHead className="text-right">المرجع</TableHead>
                        <TableHead className="text-right">البيان</TableHead>
                        <TableHead className="text-right">مدين</TableHead>
                        <TableHead className="text-right">دائن</TableHead>
                        <TableHead className="text-right">الرصيد</TableHead>
                      </TableRow>
                    </TableHeader>
                    <TableBody>
                      {statement.rows.map((r, i) => (
                        <TableRow key={`${r.reference}-${i}`}>
                          <TableCell className="whitespace-nowrap text-[var(--color-muted-foreground)]">
                            {formatDate(r.date)}
                          </TableCell>
                          <TableCell className="whitespace-nowrap ltr-nums font-semibold">{r.reference}</TableCell>
                          <TableCell className="max-w-[18rem] truncate">{r.description ?? "—"}</TableCell>
                          <TableCell className="whitespace-nowrap ltr-nums">
                            {r.debit > 0 ? formatMoney(r.debit) : "—"}
                          </TableCell>
                          <TableCell className="whitespace-nowrap ltr-nums">
                            {r.credit > 0 ? formatMoney(r.credit) : "—"}
                          </TableCell>
                          <TableCell
                            className={cn(
                              "whitespace-nowrap font-semibold ltr-nums",
                              r.balance < 0 ? "text-[var(--color-danger)]" : "",
                            )}
                          >
                            {formatMoney(r.balance)}
                          </TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                </div>
              )}
            </>
          )}
        </SectionCard>
      ) : null}
    </div>
  );
}
