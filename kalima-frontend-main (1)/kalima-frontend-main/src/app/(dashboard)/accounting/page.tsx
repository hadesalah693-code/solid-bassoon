"use client";

import { useEffect, useMemo, useState } from "react";
import {
  AlertTriangle,
  ArrowDownLeft,
  ArrowUpRight,
  Calculator,
  CheckCircle2,
  Landmark,
  Percent,
  Scale,
  TrendingDown,
  TrendingUp,
  Wallet,
} from "lucide-react";

import { AccountingRoleGate } from "@/components/accounting/AccountingRoleGate";
import { DisputeModuleHero } from "@/components/disputes/DisputeModuleHero";
import { OperationalKpiCard } from "@/components/dashboard/OperationalKpiCard";
import { ApiErrorAlert } from "@/components/shared/ApiErrorAlert";
import { EmptyState } from "@/components/shared/EmptyState";
import { PageLoading } from "@/components/shared/PageLoading";
import { SectionCard } from "@/components/shared/SectionCard";
import { Badge } from "@/components/ui/badge";
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

import { reportsApi } from "@/lib/api/accounting";
import { ApiRequestError } from "@/lib/api/client";
import { formatDate } from "@/lib/utils/format-date";
import { formatMoney } from "@/lib/utils/format-money";
import { cn } from "@/lib/utils/cn";
import type {
  AccountingReportResponseDto,
  AgingBucketDto,
  BankBalanceDto,
  MoneyDto,
} from "@/types/accounting";

function startOfYear() {
  return `${new Date().getFullYear()}-01-01`;
}

function today() {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(
    d.getDate(),
  ).padStart(2, "0")}`;
}

const moneyClass = "whitespace-nowrap font-medium ltr-nums";
const negClass = "text-[var(--color-danger)]";
const posClass = "text-[var(--color-success)]";

function AmountCell({ value, muted = false }: { value: MoneyDto; muted?: boolean }) {
  const cls = cn(
    moneyClass,
    value < 0 ? negClass : value > 0 && !muted ? posClass : "",
  );
  return <span className={cls}>{formatMoney(value)}</span>;
}

/** Proportion bar used for expense/revenue mix and AR aging. */
function ShareBar({ value, total }: { value: MoneyDto; total: MoneyDto }) {
  const share = total > 0 ? Math.min(100, Math.max(0, (Math.abs(value) / Math.abs(total)) * 100)) : 0;
  return (
    <div className="h-1.5 w-full overflow-hidden rounded-full bg-[var(--color-surface-muted)]">
      <div className="h-full rounded-full bg-[var(--color-gold-400)]" style={{ width: `${share}%` }} />
    </div>
  );
}

function AgingTable({ buckets, total }: { buckets: AgingBucketDto[]; total: MoneyDto }) {
  return (
    <div className="space-y-3">
      {buckets.map((b) => (
        <div key={b.label} className="space-y-1.5">
          <div className="flex items-center justify-between gap-2 text-sm">
            <span className="text-[var(--color-foreground-secondary)]">
              {b.label}
              <span className="ms-1.5 text-xs text-[var(--color-muted-foreground)]">
                ({b.count.toLocaleString("ar-EG")})
              </span>
            </span>
            <AmountCell value={b.amount} />
          </div>
          <ShareBar value={b.amount} total={total} />
        </div>
      ))}
    </div>
  );
}

function BankTable({ banks }: { banks: BankBalanceDto[] }) {
  if (banks.length === 0) {
    return (
      <p className="py-4 text-center text-sm text-[var(--color-muted-foreground)]">
        لا توجد حسابات خزينة مسجّلة.
      </p>
    );
  }
  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead className="text-right">الحساب</TableHead>
          <TableHead className="text-right">البنك</TableHead>
          <TableHead className="text-right">رصيد افتتاحي</TableHead>
          <TableHead className="text-right">حركة الفترة</TableHead>
          <TableHead className="text-right">الرصيد</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {banks.map((b) => (
          <TableRow key={b.id}>
            <TableCell className="font-medium">{b.nameAr}</TableCell>
            <TableCell className="text-[var(--color-muted-foreground)]">{b.bankName ?? "—"}</TableCell>
            <TableCell><AmountCell value={b.openingBalance} muted /></TableCell>
            <TableCell><AmountCell value={b.movement} /></TableCell>
            <TableCell><AmountCell value={b.balance} /></TableCell>
          </TableRow>
        ))}
      </TableBody>
      <TableBody>
        <TableRow className="bg-[var(--color-surface-muted)]/60">
          <TableCell className="font-bold" colSpan={3}>الإجمالي</TableCell>
          <TableCell />
          <TableCell><AmountCell value={banks.reduce((s, b) => s + b.balance, 0)} /></TableCell>
        </TableRow>
      </TableBody>
    </Table>
  );
}
export default function AccountingOverviewPage() {
  return (
    <AccountingRoleGate>
      <AccountingOverviewPageInner />
    </AccountingRoleGate>
  );
}

function AccountingOverviewPageInner() {
  const [from, setFrom] = useState(startOfYear);
  const [to, setTo] = useState(today);
  const [report, setReport] = useState<AccountingReportResponseDto | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [fetchId, setFetchId] = useState(0);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      setLoading(true);
      setError(null);
      try {
        const res = await reportsApi.overview(from || null, to || null);
        if (!cancelled) setReport(res);
      } catch (e) {
        if (!cancelled) {
          setError(
            e instanceof ApiRequestError ? e.message : "تعذّر تحميل التقرير المحاسبي",
          );
          setReport(null);
        }
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [from, to, fetchId]);

  const rangeInvalid = useMemo(() => Boolean(from && to && from > to), [from, to]);

  return (
    <div dir="rtl" className="space-y-6 pb-10">
      <DisputeModuleHero
        icon={Calculator}
        eyebrow="المالية والمحاسبة"
        title="لوحة المحاسبة"
        description="قائمة الدخل، المركز المالي، ميزان المراجعة، الخزينة، أعمار الذمم المدينة، وضريبة القيمة المضافة — مُولّدة مباشرة من دفتر اليومية المزدوج."
        badges={
          report ? (
            <>
              <span className="rounded-lg bg-white/10 px-2.5 py-1 text-xs font-medium text-[var(--color-gold-200)] ring-1 ring-white/15">
                {formatDate(report.from)} — {formatDate(report.to)}
              </span>
              {report.balanced ? (
                <span className="inline-flex items-center gap-1 rounded-lg bg-emerald-500/15 px-2.5 py-1 text-xs font-medium text-emerald-200 ring-1 ring-emerald-400/30">
                  <CheckCircle2 className="size-3.5" />ميزان مُتوازن
                </span>
              ) : (
                <span className="inline-flex items-center gap-1 rounded-lg bg-red-500/15 px-2.5 py-1 text-xs font-medium text-red-200 ring-1 ring-red-400/30">
                  <AlertTriangle className="size-3.5" />الميزان غير متوازن
                </span>
              )}
            </>
          ) : null
        }
      />

      <SectionCard title="الفترة المالية" contentClassName="space-y-3">
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
          <div className="space-y-1.5">
            <Label htmlFor="acc-from">من تاريخ</Label>
            <Input
              id="acc-from"
              type="date"
              value={from}
              onChange={(e) => setFrom(e.target.value)}
              className="ltr-nums text-start"
            />
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="acc-to">إلى تاريخ</Label>
            <Input
              id="acc-to"
              type="date"
              value={to}
              onChange={(e) => setTo(e.target.value)}
              className="ltr-nums text-start"
            />
          </div>
          <div className="flex items-end">
            <button
              type="button"
              onClick={() => { setFrom(startOfYear()); setTo(today()); }}
              className="h-10 text-sm font-medium text-[var(--color-gold-600)] hover:underline"
            >
              إعادة التعيين
            </button>
          </div>
        </div>
        {rangeInvalid ? (
          <p className="text-xs text-[var(--color-danger)]">
            تاريخ البداية يجب أن يكون قبل تاريخ النهاية.
          </p>
        ) : null}
      </SectionCard>

      {error ? (
        <ApiErrorAlert message={error} onRetry={() => setFetchId((n) => n + 1)} />
      ) : null}

      {loading ? (
        <PageLoading label="جاري إعداد التقارير المحاسبية..." />
      ) : !report ? null : (
        <>
          <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-3">
            <OperationalKpiCard
              label="الإيرادات"
              value={formatMoney(report.revenue)}
              hint="إجمالي الإيراد المحقق"
              icon={<TrendingUp aria-hidden />}
              accent="success"
            />
            <OperationalKpiCard
              label="المصروفات"
              value={formatMoney(report.operatingExpenses)}
              hint="إجمالي المصروف التشغيلي"
              icon={<TrendingDown aria-hidden />}
              accent="danger"
            />
            <OperationalKpiCard
              label="صافي الربح"
              value={formatMoney(report.netProfit)}
              hint={report.netProfit >= 0 ? "ربح صافي" : "خسارة صافية"}
              icon={report.netProfit >= 0 ? <ArrowUpRight aria-hidden /> : <ArrowDownLeft aria-hidden />}
              accent={report.netProfit >= 0 ? "success" : "danger"}
            />
            <OperationalKpiCard
              label="النقد في الخزينة"
              value={formatMoney(report.cashOnHand)}
              hint={`وارد ${formatMoney(report.cashIn)} · منصرف ${formatMoney(report.cashOut)}`}
              icon={<Wallet aria-hidden />}
              accent="navy"
            />
            <OperationalKpiCard
              label="الذمم المدينة"
              value={formatMoney(report.totalReceivable)}
              hint={
                report.overdueInvoiceCount > 0
                  ? `${report.overdueInvoiceCount.toLocaleString("ar-EG")} فاتورة متأخرة`
                  : "لا توجد فواتير متأخرة"
              }
              icon={<Landmark aria-hidden />}
              accent={report.overdueInvoiceCount > 0 ? "gold" : "navy"}
            />
            <OperationalKpiCard
              label="ضريبة القيمة المضافة"
              value={formatMoney(report.netVatPayable)}
              hint={`مخرجات ${formatMoney(report.outputVat)} · مدخلات ${formatMoney(report.inputVat)}`}
              icon={<Percent aria-hidden />}
              accent={report.netVatPayable > 0 ? "gold" : "info"}
            />
          </div>

          <div className="grid gap-6 xl:grid-cols-2">
            <SectionCard
              title="قائمة الدخل"
              description="الإيراد مقابل المصروف خلال الفترة المحددة"
              action={
                <Badge tone={report.netProfit >= 0 ? "success" : "danger"}>
                  {report.netProfit >= 0 ? "ربح" : "خسارة"}
                </Badge>
              }
            >
              <dl className="space-y-2 text-sm">
                <div className="flex items-center justify-between gap-2">
                  <dt className="text-[var(--color-muted-foreground)]">إجمالي الإيراد</dt>
                  <dd className={cn(moneyClass, "text-[var(--color-success)]")}>
                    {formatMoney(report.revenue)}
                  </dd>
                </div>
                <div className="flex items-center justify-between gap-2">
                  <dt className="text-[var(--color-muted-foreground)]">تكلفة الإيراد</dt>
                  <dd className={moneyClass}>{formatMoney(report.cogs)}</dd>
                </div>
                <div className="flex items-center justify-between gap-2 border-t border-[var(--color-border)] pt-2">
                  <dt className="font-semibold">مجمل الربح</dt>
                  <dd className="font-semibold ltr-nums">{formatMoney(report.grossProfit)}</dd>
                </div>
                <div className="flex items-center justify-between gap-2">
                  <dt className="text-[var(--color-muted-foreground)]">المصروفات التشغيلية</dt>
                  <dd className={cn(moneyClass, "text-[var(--color-danger)]")}>
                    −{formatMoney(report.operatingExpenses)}
                  </dd>
                </div>
                <div className="flex items-center justify-between gap-2 border-t border-[var(--color-border)] pt-2">
                  <dt className="font-bold">صافي الربح / (الخسارة)</dt>
                  <dd className={cn("text-lg font-extrabold ltr-nums", report.netProfit < 0 ? negClass : "text-[var(--color-gold-600)]")}>
                    {formatMoney(report.netProfit)}
                  </dd>
                </div>
              </dl>

              {report.revenueBreakdown.length > 0 ? (
                <div className="mt-5 space-y-3 border-t border-[var(--color-border)] pt-4">
                  <p className="text-xs font-bold uppercase tracking-wider text-[var(--color-muted-foreground)]">
                    تفصيل الإيرادات
                  </p>
                  {report.revenueBreakdown.map((row) => (
                    <div key={row.accountId} className="space-y-1.5">
                      <div className="flex items-center justify-between gap-2 text-sm">
                        <span>
                          <span className="font-semibold ltr-nums">{row.code}</span>{" "}
                          <span className="text-[var(--color-muted-foreground)]">{row.nameAr}</span>
                        </span>
                        <span className="font-medium ltr-nums">{formatMoney(row.credit)}</span>
                      </div>
                      <ShareBar value={row.credit} total={report.revenue} />
                    </div>
                  ))}
                </div>
              ) : null}

              {report.expenseBreakdown.length > 0 ? (
                <div className="mt-5 space-y-3 border-t border-[var(--color-border)] pt-4">
                  <p className="text-xs font-bold uppercase tracking-wider text-[var(--color-muted-foreground)]">
                    تفصيل المصروفات
                  </p>
                  {report.expenseBreakdown.map((row) => (
                    <div key={row.accountId} className="space-y-1.5">
                      <div className="flex items-center justify-between gap-2 text-sm">
                        <span>
                          <span className="font-semibold ltr-nums">{row.code}</span>{" "}
                          <span className="text-[var(--color-muted-foreground)]">{row.nameAr}</span>
                        </span>
                        <span className="font-medium ltr-nums">{formatMoney(row.debit)}</span>
                      </div>
                      <ShareBar value={row.debit} total={report.operatingExpenses} />
                    </div>
                  ))}
                </div>
              ) : null}
            </SectionCard>

            <SectionCard
              title="المركز المالي"
              description="الأصول مقابل الالتزامات وحقوق الملكية"
              action={
                report.balanced ? (
                  <Badge tone="success">متوازن</Badge>
                ) : (
                  <Badge tone="danger">غير متوازن</Badge>
                )
              }
            >
              <dl className="space-y-2 text-sm">
                <div className="flex items-center justify-between gap-2">
                  <dt className="text-[var(--color-muted-foreground)]">إجمالي الأصول</dt>
                  <dd className={cn(moneyClass, "text-[var(--color-navy-700)]")}>
                    {formatMoney(report.totalAssets)}
                  </dd>
                </div>
                <div className="flex items-center justify-between gap-2">
                  <dt className="text-[var(--color-muted-foreground)]">إجمالي الالتزامات</dt>
                  <dd className={moneyClass}>{formatMoney(report.totalLiabilities)}</dd>
                </div>
                <div className="flex items-center justify-between gap-2">
                  <dt className="text-[var(--color-muted-foreground)]">حقوق الملكية</dt>
                  <dd className={moneyClass}>{formatMoney(report.totalEquity)}</dd>
                </div>
                <div className="flex items-center justify-between gap-2 border-t border-[var(--color-border)] pt-2">
                  <dt className="font-semibold">الالتزامات + حقوق الملكية</dt>
                  <dd className="font-semibold ltr-nums">{formatMoney(report.totalLiabilitiesAndEquity)}</dd>
                </div>
              </dl>

              <div className="mt-5 grid gap-4 border-t border-[var(--color-border)] pt-4 sm:grid-cols-3">
                <div>
                  <p className="mb-2 text-xs font-bold uppercase tracking-wider text-[var(--color-muted-foreground)]">
                    الأصول
                  </p>
                  <div className="space-y-1 text-sm">
                    {report.assets.map((a) => (
                      <div key={a.accountId} className="flex items-center justify-between gap-2">
                        <span className="truncate text-[var(--color-muted-foreground)]">{a.nameAr}</span>
                        <span className="ltr-nums">{formatMoney(a.balance)}</span>
                      </div>
                    ))}
                  </div>
                </div>
                <div>
                  <p className="mb-2 text-xs font-bold uppercase tracking-wider text-[var(--color-muted-foreground)]">
                    الالتزامات
                  </p>
                  <div className="space-y-1 text-sm">
                    {report.liabilities.map((a) => (
                      <div key={a.accountId} className="flex items-center justify-between gap-2">
                        <span className="truncate text-[var(--color-muted-foreground)]">{a.nameAr}</span>
                        <span className="ltr-nums">{formatMoney(a.balance)}</span>
                      </div>
                    ))}
                  </div>
                </div>
                <div>
                  <p className="mb-2 text-xs font-bold uppercase tracking-wider text-[var(--color-muted-foreground)]">
                    حقوق الملكية
                  </p>
                  <div className="space-y-1 text-sm">
                    {report.equity.map((a) => (
                      <div key={a.accountId} className="flex items-center justify-between gap-2">
                        <span className="truncate text-[var(--color-muted-foreground)]">{a.nameAr}</span>
                        <span className="ltr-nums">{formatMoney(a.balance)}</span>
                      </div>
                    ))}
                  </div>
                </div>
              </div>
            </SectionCard>
          </div>

          <SectionCard
            flush
            title="ميزان المراجعة"
            description="كل الحسابات ذات الحركة خلال الفترة"
            action={
              report.trialBalanced ? (
                <Badge tone="success">
                  مدين {formatMoney(report.trialTotalDebit)} = دائن {formatMoney(report.trialTotalCredit)}
                </Badge>
              ) : (
                <Badge tone="danger">الطرفين غير متساويين</Badge>
              )
            }
          >
            {report.trialBalance.length === 0 ? (
              <div className="p-6">
                <EmptyState
                  icon={Scale}
                  title="لا توجد حركات في هذه الفترة"
                  description="سجّل قيوداً أو مصروفات أو فواتير لترى أثرها هنا."
                />
              </div>
            ) : (
              <div className="overflow-x-auto">
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead className="text-right">الكود</TableHead>
                      <TableHead className="text-right">الحساب</TableHead>
                      <TableHead className="text-right">النوع</TableHead>
                      <TableHead className="text-right">مدين</TableHead>
                      <TableHead className="text-right">دائن</TableHead>
                      <TableHead className="text-right">الرصيد</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {report.trialBalance.map((row) => (
                      <TableRow key={row.accountId}>
                        <TableCell className="font-semibold ltr-nums">{row.code}</TableCell>
                        <TableCell>{row.nameAr}</TableCell>
                        <TableCell>
                          <Badge tone="neutral">{row.type}</Badge>
                        </TableCell>
                        <TableCell><AmountCell value={row.debit} /></TableCell>
                        <TableCell><AmountCell value={row.credit} muted /></TableCell>
                        <TableCell><AmountCell value={row.balance} /></TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </div>
            )}
          </SectionCard>

          <div className="grid gap-6 xl:grid-cols-2">
            <SectionCard flush title="حركة الخزائن" description="الأرصدة والحركة خلال الفترة">
              <BankTable banks={report.bankBalances} />
            </SectionCard>

            <SectionCard
              title="أعمار الذمم المدينة"
              description={`إجمالي ${formatMoney(report.totalReceivable)}`}
            >
              <AgingTable buckets={report.aging} total={report.totalReceivable} />
            </SectionCard>
          </div>

          <SectionCard
            title="ملخص ضريبة القيمة المضافة"
            description="وفق فواتير الإيراد ومصروفات الشراء خلال الفترة"
          >
            <div className="grid gap-3 sm:grid-cols-3">
              <div className="rounded-xl border border-[var(--color-border)] bg-[var(--color-surface-muted)] p-4">
                <p className="text-xs text-[var(--color-muted-foreground)]">ضريبة المخرجات</p>
                <p className="mt-1 text-lg font-bold ltr-nums text-[var(--color-success)]">
                  {formatMoney(report.outputVat)}
                </p>
              </div>
              <div className="rounded-xl border border-[var(--color-border)] bg-[var(--color-surface-muted)] p-4">
                <p className="text-xs text-[var(--color-muted-foreground)]">ضريبة المدخلات</p>
                <p className="mt-1 text-lg font-bold ltr-nums text-[var(--color-danger)]">
                  {formatMoney(report.inputVat)}
                </p>
              </div>
              <div className="rounded-xl border border-[var(--color-gold-200)] bg-[var(--color-gold-50)] p-4">
                <p className="text-xs text-[var(--color-gold-600)]">
                  {report.netVatPayable >= 0 ? "صافي المستحق" : "رصيد ضريبي مسترد"}
                </p>
                <p className="mt-1 text-lg font-bold ltr-nums text-[var(--color-gold-600)]">
                  {formatMoney(report.netVatPayable)}
                </p>
              </div>
            </div>
            <p className="mt-3 text-xs text-[var(--color-muted-foreground)]">
              تُرحَّل فواتير الإيراد تلقائياً بقيد تحقق الإيراد، وتُقيَّد المدفوعات بقيد سداد مقابل
              حساب الذمم المدينة. الفارق بين المخرجات والمدخلات هو صافي الضريبة المستحقة للهيئة
              الزكاة والضريبة والجمارك.
            </p>
          </SectionCard>
        </>
      )}
    </div>
  );
}
