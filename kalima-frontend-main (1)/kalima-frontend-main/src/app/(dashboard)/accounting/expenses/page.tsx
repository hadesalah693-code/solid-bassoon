"use client";

import { useEffect, useMemo, useState, type FormEvent } from "react";
import {
  Calculator,
  Plus,
  Receipt,
  RotateCcw,
  Save,
  Trash2,
} from "lucide-react";

import { AccountingDialog } from "@/components/accounting/AccountingDialog";
import { AccountingRoleGate, AccountingWriteOnly } from "@/components/accounting/AccountingRoleGate";
import { DisputeModuleHero } from "@/components/disputes/DisputeModuleHero";
import { ApiErrorAlert } from "@/components/shared/ApiErrorAlert";
import { EmptyState } from "@/components/shared/EmptyState";
import { PageLoading } from "@/components/shared/PageLoading";
import { Pagination } from "@/components/shared/Pagination";
import { SearchInput } from "@/components/shared/SearchInput";
import { SectionCard } from "@/components/shared/SectionCard";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";

import { accountsApi, bankAccountsApi, expensesApi } from "@/lib/api/accounting";
import { personsApi } from "@/lib/api/persons";
import { ApiRequestError } from "@/lib/api/client";
import {
  EXPENSE_CATEGORY_AR,
  EXPENSE_CATEGORY_OPTIONS,
  PAYMENT_METHOD_OPTIONS,
} from "@/lib/accounting/labels";
import { useDebounce } from "@/hooks/use-debounce";
import { notifyApiError, notifySuccess } from "@/lib/feedback";
import { formatDate } from "@/lib/utils/format-date";
import { formatMoney } from "@/lib/utils/format-money";
import { cn } from "@/lib/utils/cn";
import type { ClientPerson } from "@/types/person";
import type {
  AccountResponseDto,
  BankAccountResponseDto,
  ExpenseCategoryDto,
  ExpenseResponseDto,
} from "@/types/accounting";

const PAGE_SIZE = 20;

const selectClassName = cn(
  "flex h-10 w-full cursor-pointer appearance-none rounded-xl border border-[var(--color-border)] bg-[var(--color-surface)] px-3.5 py-2 text-sm text-[var(--color-foreground)] shadow-sm transition-colors",
  "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--color-gold-400)]",
  "disabled:cursor-not-allowed disabled:opacity-50",
);

const inputLtrClassName = "ltr-nums text-start";

function today() {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(
    d.getDate(),
  ).padStart(2, "0")}`;
}

function round2(n: number) {
  return Math.round((n + Number.EPSILON) * 100) / 100;
}

function personName(p: ClientPerson) {
  if (p.personType === "COMPANY") return p.companyName?.trim() || p.fullName?.trim() || p.id;
  return p.fullName?.trim() || p.companyName?.trim() || p.id;
}

export default function ExpensesPage() {
  return (
    <AccountingRoleGate>
      <ExpensesPageInner />
    </AccountingRoleGate>
  );
}

function ExpensesPageInner() {
  const [rows, setRows] = useState<ExpenseResponseDto[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const [search, setSearch] = useState("");
  const debouncedSearch = useDebounce(search, 300);
  const [category, setCategory] = useState<ExpenseCategoryDto | "">("");
  const [from, setFrom] = useState("");
  const [to, setTo] = useState("");
  const [fetchId, setFetchId] = useState(0);

  const [expenseAccounts, setExpenseAccounts] = useState<AccountResponseDto[]>([]);
  const [banks, setBanks] = useState<BankAccountResponseDto[]>([]);
  const [clients, setClients] = useState<ClientPerson[]>([]);

  const [dialogOpen, setDialogOpen] = useState(false);
  const [expenseDate, setExpenseDate] = useState(today);
  const [cat, setCat] = useState<ExpenseCategoryDto>("OTHER");
  const [desc, setDesc] = useState("");
  const [payee, setPayee] = useState("");
  const [accountId, setAccountId] = useState("");
  const [bankAccountId, setBankAccountId] = useState("");
  const [clientId, setClientId] = useState("");
  const [subtotal, setSubtotal] = useState("");
  const [taxRate, setTaxRate] = useState("0");
  const [paymentMethod, setPaymentMethod] = useState("CASH");
  const [reference, setReference] = useState("");
  const [notes, setNotes] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      setLoading(true);
      setError(null);
      try {
        const res = await expensesApi.list({
          page,
          size: PAGE_SIZE,
          search: debouncedSearch,
          category: category || undefined,
          from: from || null,
          to: to || null,
        });
        if (!cancelled) {
          setRows(res.content);
          setTotal(res.totalElements);
        }
      } catch (e) {
        if (!cancelled) {
          setError(e instanceof ApiRequestError ? e.message : "تعذّر تحميل المصروفات");
          setRows([]);
          setTotal(0);
        }
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [page, debouncedSearch, category, from, to, fetchId]);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      const [accRes, bankRes, clientRes] = await Promise.allSettled([
        accountsApi.list("EXPENSE"),
        bankAccountsApi.list(),
        personsApi.list({ role: "CLIENT", size: 200 }),
      ]);
      if (cancelled) return;
      if (accRes.status === "fulfilled") setExpenseAccounts(accRes.value.filter((a) => a.active));
      if (bankRes.status === "fulfilled") setBanks(bankRes.value.filter((b) => b.active));
        if (clientRes.status === "fulfilled") setClients(clientRes.value.content);
    })();
    return () => {
      cancelled = true;
    };
  }, []);

  const totals = useMemo(() => {
    const sub = Number(subtotal) || 0;
    const rate = Number(taxRate) || 0;
    const tax = round2((sub * rate) / 100);
    return { sub, rate, tax, total: round2(sub + tax) };
  }, [subtotal, taxRate]);

  const hasFilters = Boolean(debouncedSearch.trim()) || Boolean(category) || Boolean(from) || Boolean(to);

  function clearFilters() {
    setSearch("");
    setCategory("");
    setFrom("");
    setTo("");
    setPage(0);
  }

  function openCreate() {
    setExpenseDate(today());
    setCat("OTHER");
    setDesc("");
    setPayee("");
    setAccountId(expenseAccounts[0]?.id ?? "");
    setBankAccountId(banks.find((b) => b.currency === "SAR")?.id ?? banks[0]?.id ?? "");
    setClientId("");
    setSubtotal("");
    setTaxRate("0");
    setPaymentMethod("CASH");
    setReference("");
    setNotes("");
    setFormError(null);
    setDialogOpen(true);
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setFormError(null);

    if (!expenseDate) { setFormError("تاريخ المصروف مطلوب"); return; }
    if (!desc.trim()) { setFormError("وصف المصروف مطلوب"); return; }
    if (!Number.isFinite(Number(subtotal)) || Number(subtotal) <= 0) {
      setFormError("المبلغ يجب أن يكون أكبر من صفر");
      return;
    }
    if (Number(taxRate) < 0) { setFormError("نسبة الضريبة لا يمكن أن تكون سالبة"); return; }

    setSubmitting(true);
    try {
      const created = await expensesApi.create({
        expenseDate,
        category: cat,
        description: desc.trim(),
        payee: payee.trim() || null,
        accountId: accountId || null,
        bankAccountId: bankAccountId || null,
        clientId: clientId || null,
        subtotal: round2(Number(subtotal)),
        taxRate: Number(taxRate) || 0,
        paymentMethod,
        reference: reference.trim() || null,
        recurring: false,
        notes: notes.trim() || null,
      });
      notifySuccess("تم تسجيل المصروف", `${created.expenseNumber} · ${created.journalEntryNumber ?? ""}`);
      setDialogOpen(false);
      setPage(0);
      setFetchId((n) => n + 1);
    } catch (err) {
      if (err instanceof ApiRequestError) setFormError(err.message);
      else setFormError("تعذّر تسجيل المصروف");
      notifyApiError(err, "تعذّر تسجيل المصروف");
    } finally {
      setSubmitting(false);
    }
  }

  async function handleDelete(e: ExpenseResponseDto) {
    if (
      !window.confirm(
        `حذف المصروف «${e.expenseNumber}»؟ سيتم عكس القيد المحاسبي المرتبط به.`,
      )
    ) {
      return;
    }
    try {
      await expensesApi.remove(e.id);
      notifySuccess("تم حذف المصروف وعكس القيد");
      setFetchId((n) => n + 1);
    } catch (err) {
      notifyApiError(err, "تعذّر حذف المصروف");
    }
  }

  return (
    <div dir="rtl" className="space-y-6 pb-10">
      <DisputeModuleHero
        icon={Receipt}
        eyebrow="المالية والمحاسبة"
        title="المصروفات"
        description="تسجيل مصروفات المكتب مع احتساب ضريبة المدخلات وترحيل قيد مزدوج إلى دفتر اليومية."
        badges={
          !loading && !error ? (
            <span className="rounded-lg bg-white/10 px-2.5 py-1 text-xs font-medium text-[var(--color-gold-200)] ring-1 ring-white/15">
              {total.toLocaleString("ar-EG")} مصروف
            </span>
          ) : null
        }
        actions={
          <AccountingWriteOnly>
            <Button
              className="border-white/25 bg-[var(--color-gold-400)] text-[var(--color-navy-950)] hover:bg-[var(--color-gold-300)]"
              onClick={openCreate}
            >
              <Plus className="size-4" />
              مصروف جديد
            </Button>
          </AccountingWriteOnly>
        }
      />

      <SectionCard title="البحث والتصفية" contentClassName="space-y-3">
        <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-4">
          <div className="xl:col-span-2">
            <SearchInput
              value={search}
              onChange={(v) => { setSearch(v); setPage(0); }}
              placeholder="بحث بالرقم أو الوصف أو الجهة..."
            />
          </div>
          <select
            className={selectClassName}
            value={category}
            onChange={(e) => { setCategory(e.target.value as ExpenseCategoryDto | ""); setPage(0); }}
            aria-label="التصنيف"
          >
            <option value="">كل التصنيفات</option>
            {EXPENSE_CATEGORY_OPTIONS.map((o) => (
              <option key={o.value} value={o.value}>{o.label}</option>
            ))}
          </select>
          <div className="grid grid-cols-2 gap-2">
            <Input type="date" value={from} onChange={(e) => { setFrom(e.target.value); setPage(0); }} className={inputLtrClassName} aria-label="من تاريخ" />
            <Input type="date" value={to} onChange={(e) => { setTo(e.target.value); setPage(0); }} className={inputLtrClassName} aria-label="إلى تاريخ" />
          </div>
        </div>
        {hasFilters ? (
          <div className="flex items-center justify-end border-t border-[var(--color-border)] pt-3">
            <Button type="button" variant="ghost" size="sm" className="h-8 gap-1.5 text-[var(--color-muted-foreground)]" onClick={clearFilters}>
              <RotateCcw className="size-3.5" />مسح التصفية
            </Button>
          </div>
        ) : null}
      </SectionCard>

      {error ? (
        <ApiErrorAlert message={error} onRetry={() => setFetchId((n) => n + 1)} />
      ) : null}

      <SectionCard flush title="المصروفات">
        {loading ? (
          <PageLoading label="جاري تحميل المصروفات..." />
        ) : rows.length === 0 ? (
          <div className="p-6">
            <EmptyState
              icon={Receipt}
              title={hasFilters ? "لا توجد نتائج" : "لا توجد مصروفات"}
              description={hasFilters ? "جرّب تعديل البحث أو إزالة التصفية." : "سجّل أول مصروف للمكتب."}
              action={hasFilters ? <Button variant="outline" onClick={clearFilters}>مسح التصفية</Button> : null}
            />
          </div>
        ) : (
          <>
            <div className="overflow-x-auto">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead className="text-right">الرقم</TableHead>
                    <TableHead className="text-right">التاريخ</TableHead>
                    <TableHead className="text-right">التصنيف</TableHead>
                    <TableHead className="text-right">الوصف</TableHead>
                    <TableHead className="text-right">الحساب</TableHead>
                    <TableHead className="text-right">قبل الضريبة</TableHead>
                    <TableHead className="text-right">الضريبة</TableHead>
                    <TableHead className="text-right">الإجمالي</TableHead>
                    <TableHead className="text-right">القيد</TableHead>
                    <TableHead className="text-right">إجراءات</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {rows.map((row) => (
                    <TableRow key={row.id} className="transition-colors hover:bg-[var(--color-navy-50)]/40">
                      <TableCell className="font-semibold ltr-nums">{row.expenseNumber}</TableCell>
                      <TableCell className="whitespace-nowrap text-[var(--color-muted-foreground)]">
                        {formatDate(row.expenseDate)}
                      </TableCell>
                      <TableCell>
                        <Badge tone="neutral">{EXPENSE_CATEGORY_AR[row.category]}</Badge>
                      </TableCell>
                      <TableCell className="max-w-[16rem] truncate">
                        <span className="block">{row.description}</span>
                        {row.payee ? (
                          <span className="block text-xs text-[var(--color-muted-foreground)]">{row.payee}</span>
                        ) : null}
                      </TableCell>
                      <TableCell>
                        <span className="block font-medium ltr-nums">{row.accountCode}</span>
                        <span className="block text-xs text-[var(--color-muted-foreground)]">{row.accountName}</span>
                      </TableCell>
                      <TableCell className="whitespace-nowrap ltr-nums">{formatMoney(row.subtotal)}</TableCell>
                      <TableCell className="whitespace-nowrap ltr-nums">
                        {row.taxAmount > 0 ? formatMoney(row.taxAmount) : "—"}
                      </TableCell>
                      <TableCell className="whitespace-nowrap font-semibold ltr-nums">{formatMoney(row.total)}</TableCell>
                      <TableCell className="whitespace-nowrap ltr-nums text-xs text-[var(--color-muted-foreground)]">
                        {row.journalEntryNumber ?? "—"}
                      </TableCell>
                      <TableCell>
                        <AccountingWriteOnly>
                          <Button
                            variant="ghost"
                            size="sm"
                            onClick={() => handleDelete(row)}
                            aria-label={`حذف ${row.expenseNumber}`}
                            className="text-[var(--color-danger)]"
                          >
                            <Trash2 className="size-3.5" />
                          </Button>
                        </AccountingWriteOnly>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </div>
            <div className="border-t border-[var(--color-border)] p-4">
              <Pagination page={page} pageSize={PAGE_SIZE} total={total} onPageChange={setPage} />
            </div>
          </>
        )}
      </SectionCard>

      <AccountingDialog
        open={dialogOpen}
        onClose={() => setDialogOpen(false)}
        title="مصروف جديد"
        description="سيُرحَّل قيد مزدوج: مدين المصروف ومدين ضريبة المدخلات، مقابل دائن الخزينة."
        icon={<Receipt className="size-5" />}
        size="lg"
        scrollable
      >
        <form id="expense-form" onSubmit={handleSubmit} className="space-y-4" noValidate>
          {formError ? <ApiErrorAlert message={formError} onRetry={() => setFormError(null)} /> : null}

          <div className="grid gap-4 md:grid-cols-2">
            <div className="space-y-1.5">
              <Label htmlFor="ex-date">تاريخ المصروف *</Label>
              <Input id="ex-date" type="date" value={expenseDate} onChange={(e) => setExpenseDate(e.target.value)} disabled={submitting} className={inputLtrClassName} />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="ex-cat">التصنيف *</Label>
              <select id="ex-cat" className={selectClassName} value={cat} onChange={(e) => setCat(e.target.value as ExpenseCategoryDto)} disabled={submitting}>
                {EXPENSE_CATEGORY_OPTIONS.map((o) => (
                  <option key={o.value} value={o.value}>{o.label}</option>
                ))}
              </select>
            </div>

            <div className="space-y-1.5 md:col-span-2">
              <Label htmlFor="ex-desc">الوصف *</Label>
              <Input id="ex-desc" value={desc} onChange={(e) => setDesc(e.target.value)} disabled={submitting} placeholder="مثال: إيجار المكتب لشهر سبتمبر" maxLength={500} />
            </div>

            <div className="space-y-1.5">
              <Label htmlFor="ex-payee">الجهة المستفيدة</Label>
              <Input id="ex-payee" value={payee} onChange={(e) => setPayee(e.target.value)} disabled={submitting} maxLength={200} />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="ex-ref">المرجع / رقم الفاتورة</Label>
              <Input id="ex-ref" value={reference} onChange={(e) => setReference(e.target.value)} disabled={submitting} maxLength={100} className={inputLtrClassName} dir="ltr" />
            </div>

            <div className="space-y-1.5">
              <Label htmlFor="ex-account">حساب المصروف</Label>
              <select id="ex-account" className={selectClassName} value={accountId} onChange={(e) => setAccountId(e.target.value)} disabled={submitting}>
                <option value="">— افتراضي حسب التصنيف —</option>
                {expenseAccounts.map((a) => (
                  <option key={a.id} value={a.id}>{a.code} — {a.nameAr}</option>
                ))}
              </select>
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="ex-bank">الخزينة / الحساب البنكي</Label>
              <select id="ex-bank" className={selectClassName} value={bankAccountId} onChange={(e) => setBankAccountId(e.target.value)} disabled={submitting}>
                <option value="">— النقدية —</option>
                {banks.map((b) => (
                  <option key={b.id} value={b.id}>{b.nameAr}{b.bankName ? ` — ${b.bankName}` : ""}</option>
                ))}
              </select>
            </div>

            <div className="space-y-1.5">
              <Label htmlFor="ex-client">العميل (اختياري)</Label>
              <select id="ex-client" className={selectClassName} value={clientId} onChange={(e) => setClientId(e.target.value)} disabled={submitting}>
                <option value="">— بدون —</option>
                {clients.map((c) => (
                  <option key={c.id} value={c.id}>{personName(c)}</option>
                ))}
              </select>
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="ex-method">طريقة الدفع</Label>
              <select id="ex-method" className={selectClassName} value={paymentMethod} onChange={(e) => setPaymentMethod(e.target.value)} disabled={submitting}>
                {PAYMENT_METHOD_OPTIONS.map((o) => (
                  <option key={o.value} value={o.value}>{o.label}</option>
                ))}
              </select>
            </div>
          </div>

          <div className="rounded-xl border border-[var(--color-border)] bg-[var(--color-surface-muted)] p-4">
            <p className="mb-3 flex items-center gap-1.5 text-sm font-semibold">
              <Calculator className="size-4" />
              حساب المبلغ
            </p>
            <div className="grid gap-3 sm:grid-cols-3">
              <div className="space-y-1.5">
                <Label htmlFor="ex-sub">المبلغ قبل الضريبة *</Label>
                <Input id="ex-sub" type="number" inputMode="decimal" min="0" step="0.01" value={subtotal} onChange={(e) => setSubtotal(e.target.value)} disabled={submitting} className={inputLtrClassName} dir="ltr" />
              </div>
              <div className="space-y-1.5">
                <Label htmlFor="ex-tax">نسبة الضريبة ٪</Label>
                <Input id="ex-tax" type="number" inputMode="decimal" min="0" step="0.01" value={taxRate} onChange={(e) => setTaxRate(e.target.value)} disabled={submitting} className={inputLtrClassName} dir="ltr" />
              </div>
              <div className="space-y-1.5">
                <Label>الإجمالي</Label>
                <div className="flex h-10 items-center rounded-xl border border-[var(--color-gold-200)] bg-[var(--color-gold-50)] px-3.5 text-sm font-bold text-[var(--color-gold-600)] ltr-nums">
                  {formatMoney(totals.total)}
                </div>
                <p className="text-xs text-[var(--color-muted-foreground)]">
                  ضريبة {formatMoney(totals.tax)}
                </p>
              </div>
            </div>
          </div>

          <div className="space-y-1.5">
            <Label htmlFor="ex-notes">ملاحظات</Label>
            <Textarea id="ex-notes" rows={2} value={notes} onChange={(e) => setNotes(e.target.value)} disabled={submitting} maxLength={1000} />
          </div>
        </form>

        <div className="mt-4 flex justify-end gap-2 border-t border-[var(--color-border)] pt-4">
          <Button type="submit" form="expense-form" variant="gold" disabled={submitting}>
            {submitting ? "جارٍ الحفظ…" : (<><Save className="size-4" />تسجيل المصروف</>)}
          </Button>
          <Button type="button" variant="ghost" onClick={() => setDialogOpen(false)} disabled={submitting}>
            إلغاء
          </Button>
        </div>
      </AccountingDialog>
    </div>
  );
}
