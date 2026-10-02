"use client";

import { useEffect, useMemo, useState, type FormEvent } from "react";
import Link from "next/link";
import { Banknote, Plus, RotateCcw, Save, Trash2 } from "lucide-react";

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

import { bankAccountsApi, receiptsApi } from "@/lib/api/accounting";
import { invoicesApi } from "@/lib/api/invoices";
import { personsApi } from "@/lib/api/persons";
import { ApiRequestError } from "@/lib/api/client";
import { CURRENCY_OPTIONS, PAYMENT_METHOD_OPTIONS } from "@/lib/accounting/labels";
import { useDebounce } from "@/hooks/use-debounce";
import { notifyApiError, notifySuccess } from "@/lib/feedback";
import { formatDate } from "@/lib/utils/format-date";
import { formatMoney } from "@/lib/utils/format-money";
import { cn } from "@/lib/utils/cn";
import type { ClientPerson } from "@/types/person";
import type { BankAccountResponseDto, ReceiptResponseDto } from "@/types/accounting";
import type { InvoiceListItemDto } from "@/types/invoice";

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

function personName(p: ClientPerson) {
  if (p.personType === "COMPANY") return p.companyName?.trim() || p.fullName?.trim() || p.id;
  return p.fullName?.trim() || p.companyName?.trim() || p.id;
}

export default function ReceiptsPage() {
  return (
    <AccountingRoleGate>
      <ReceiptsPageInner />
    </AccountingRoleGate>
  );
}

function ReceiptsPageInner() {
  const [rows, setRows] = useState<ReceiptResponseDto[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const [search, setSearch] = useState("");
  const debouncedSearch = useDebounce(search, 300);
  const [clientId, setClientId] = useState("");
  const [from, setFrom] = useState("");
  const [to, setTo] = useState("");
  const [fetchId, setFetchId] = useState(0);

  const [banks, setBanks] = useState<BankAccountResponseDto[]>([]);
  const [clients, setClients] = useState<ClientPerson[]>([]);
  const [clientInvoices, setClientInvoices] = useState<InvoiceListItemDto[]>([]);

  const [dialogOpen, setDialogOpen] = useState(false);
  const [receiptDate, setReceiptDate] = useState(today);
  const [bankAccountId, setBankAccountId] = useState("");
  const [amount, setAmount] = useState("");
  const [client, setClient] = useState("");
  const [invoiceId, setInvoiceId] = useState("");
  const [currency, setCurrency] = useState("SAR");
  const [method, setMethod] = useState("CASH");
  const [reference, setReference] = useState("");
  const [deposit, setDeposit] = useState(false);
  const [notes, setNotes] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      setLoading(true);
      setError(null);
      try {
        const res = await receiptsApi.list({
          page,
          size: PAGE_SIZE,
          search: debouncedSearch,
          clientId: clientId || null,
          from: from || null,
          to: to || null,
        });
        if (!cancelled) {
          setRows(res.content);
          setTotal(res.totalElements);
        }
      } catch (e) {
        if (!cancelled) {
          setError(e instanceof ApiRequestError ? e.message : "تعذّر تحميل سندات القبض");
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
  }, [page, debouncedSearch, clientId, from, to, fetchId]);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      const [bankRes, clientRes] = await Promise.allSettled([
        bankAccountsApi.list(),
        personsApi.list({ role: "CLIENT", size: 200 }),
      ]);
      if (cancelled) return;
      if (bankRes.status === "fulfilled") setBanks(bankRes.value.filter((b) => b.active));
      if (clientRes.status === "fulfilled") setClients(clientRes.value.content);
    })();
    return () => {
      cancelled = true;
    };
  }, []);

  useEffect(() => {
    if (!client) {
      setClientInvoices([]);
      setInvoiceId("");
      return;
    }
    let cancelled = false;
    void invoicesApi.list({ clientId: client, size: 100 })
      .then((result) => {
        if (!cancelled) {
          setClientInvoices(result.content.filter((invoice) =>
            invoice.remaining > 0 && invoice.status !== "DRAFT" && invoice.status !== "CANCELLED"));
        }
      })
      .catch(() => { if (!cancelled) setClientInvoices([]); });
    return () => { cancelled = true; };
  }, [client]);

  const pageTotal = useMemo(
    () => rows.reduce((s, r) => s + r.amount, 0),
    [rows],
  );

  const hasFilters = Boolean(debouncedSearch.trim()) || Boolean(clientId) || Boolean(from) || Boolean(to);

  function clearFilters() {
    setSearch("");
    setClientId("");
    setFrom("");
    setTo("");
    setPage(0);
  }

  function openCreate() {
    const preferred = banks.find((b) => b.currency === "SAR") ?? banks[0];
    setReceiptDate(today());
    setBankAccountId(preferred?.id ?? "");
    setAmount("");
    setClient("");
    setInvoiceId("");
    setCurrency(preferred?.currency ?? "SAR");
    setMethod("CASH");
    setReference("");
    setDeposit(false);
    setNotes("");
    setFormError(null);
    setDialogOpen(true);
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setFormError(null);

    if (!receiptDate) { setFormError("تاريخ السند مطلوب"); return; }
    if (!bankAccountId) { setFormError("اختر الحساب البنكي أو الخزينة"); return; }
    if (deposit && !client) { setFormError("حدد العميل لإيداع أموال أمانة باسمه"); return; }
    if (client && !deposit && !invoiceId) { setFormError("خصص المبلغ على فاتورة أو سجله كوديعة عميل"); return; }
    if (!Number.isFinite(Number(amount)) || Number(amount) <= 0) {
      setFormError("المبلغ يجب أن يكون أكبر من صفر");
      return;
    }

    setSubmitting(true);
    try {
      const created = await receiptsApi.create({
        receiptDate,
        bankAccountId,
        amount: Number(amount),
        clientId: client || null,
        invoiceId: invoiceId || null,
        currency,
        paymentMethod: method,
        reference: reference.trim() || null,
        deposit,
        notes: notes.trim() || null,
      });
      notifySuccess("تم تسجيل سند القبض", `${created.receiptNumber} · ${created.journalEntryNumber ?? ""}`);
      setDialogOpen(false);
      setPage(0);
      setFetchId((n) => n + 1);
    } catch (err) {
      if (err instanceof ApiRequestError) setFormError(err.message);
      else setFormError("تعذّر تسجيل السند");
      notifyApiError(err, "تعذّر تسجيل سند القبض");
    } finally {
      setSubmitting(false);
    }
  }

  async function handleDelete(r: ReceiptResponseDto) {
    if (!window.confirm(`حذف السند «${r.receiptNumber}»؟ سيتم عكس القيد المرتبط به.`)) return;
    try {
      await receiptsApi.remove(r.id);
      notifySuccess("تم حذف السند وعكس القيد");
      setFetchId((n) => n + 1);
    } catch (err) {
      notifyApiError(err, "تعذّر حذف السند");
    }
  }

  return (
    <div dir="rtl" className="space-y-6 pb-10">
      <DisputeModuleHero
        icon={Banknote}
        eyebrow="المالية والمحاسبة"
        title="سندات القبض"
        description="تحصيل المبالغ النقدية أو البنكية خارج الفواتير، وترحيلها إلى الخزينة مع خصم الذمم المدينة عند تخصيصها لعميل."
        badges={
          !loading && !error ? (
            <span className="rounded-lg bg-white/10 px-2.5 py-1 text-xs font-medium text-[var(--color-gold-200)] ring-1 ring-white/15">
              {total.toLocaleString("ar-EG")} سند
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
              سند قبض
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
              placeholder="بحث بالرقم أو المرجع..."
            />
          </div>
          <select
            className={selectClassName}
            value={clientId}
            onChange={(e) => { setClientId(e.target.value); setPage(0); }}
            aria-label="العميل"
          >
            <option value="">كل العملاء</option>
            {clients.map((c) => (
              <option key={c.id} value={c.id}>{personName(c)}</option>
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

      <SectionCard flush title="السندات">
        {loading ? (
          <PageLoading label="جاري تحميل سندات القبض..." />
        ) : rows.length === 0 ? (
          <div className="p-6">
            <EmptyState
              icon={Banknote}
              title={hasFilters ? "لا توجد نتائج" : "لا توجد سندات قبض"}
              description={hasFilters ? "جرّب تعديل البحث أو إزالة التصفية." : "سجّل أول سند قبض للتحصيل النقدي."}
              action={hasFilters ? <Button variant="outline" onClick={clearFilters}>مسح التصفية</Button> : null}
            />
          </div>
        ) : (
          <>
            <div className="overflow-x-auto">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead className="text-right">رقم السند</TableHead>
                    <TableHead className="text-right">التاريخ</TableHead>
                    <TableHead className="text-right">العميل</TableHead>
                    <TableHead className="text-right">الفاتورة</TableHead>
                    <TableHead className="text-right">الحساب</TableHead>
                    <TableHead className="text-right">المبلغ</TableHead>
                    <TableHead className="text-right">الطريقة</TableHead>
                    <TableHead className="text-right">المرجع</TableHead>
                    <TableHead className="text-right">القيد</TableHead>
                    <TableHead className="text-right">إجراءات</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {rows.map((row) => (
                    <TableRow key={row.id} className="transition-colors hover:bg-[var(--color-navy-50)]/40">
                      <TableCell className="font-semibold ltr-nums">{row.receiptNumber}</TableCell>
                      <TableCell className="whitespace-nowrap text-[var(--color-muted-foreground)]">
                        {formatDate(row.receiptDate)}
                      </TableCell>
                      <TableCell>{row.clientName ?? "—"}</TableCell>
                      <TableCell className="ltr-nums">{row.invoiceId ? <Link href={`/invoices/${row.invoiceId}`} className="text-[var(--color-gold-700)] hover:underline">مخصصة</Link> : "—"}</TableCell>
                      <TableCell>
                        <span className="block">{row.bankAccountName}</span>
                        <span className="block text-xs text-[var(--color-muted-foreground)]">
                          {row.deposit ? <Badge tone="info">إيداع بنكي</Badge> : "نقدية"}
                        </span>
                      </TableCell>
                      <TableCell className="whitespace-nowrap font-semibold ltr-nums">
                        {formatMoney(row.amount, row.currency)}
                      </TableCell>
                      <TableCell className="text-xs text-[var(--color-muted-foreground)]">
                        {PAYMENT_METHOD_OPTIONS.find((m) => m.value === row.paymentMethod)?.label ?? "—"}
                      </TableCell>
                      <TableCell className="ltr-nums text-xs text-[var(--color-muted-foreground)]">
                        {row.reference ?? "—"}
                      </TableCell>
                      <TableCell className="whitespace-nowrap ltr-nums text-xs text-[var(--color-muted-foreground)]">
                        {row.journalEntryNumber ?? "—"}
                      </TableCell>
                      <TableCell>
                        <AccountingWriteOnly>
                          <Button
                            variant="ghost"
                            size="sm"
                            onClick={() => handleDelete(row)}
                            aria-label={`حذف ${row.receiptNumber}`}
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
            <div className="flex flex-wrap items-center justify-between gap-3 border-t border-[var(--color-border)] p-4">
              <p className="text-sm text-[var(--color-muted-foreground)]">
                إجمالي هذه الصفحة:{" "}
                <span className="font-semibold ltr-nums">{formatMoney(pageTotal)}</span>
              </p>
              <Pagination page={page} pageSize={PAGE_SIZE} total={total} onPageChange={setPage} />
            </div>
          </>
        )}
      </SectionCard>

      <AccountingDialog
        open={dialogOpen}
        onClose={() => setDialogOpen(false)}
        title="سند قبض جديد"
        description="سيُرحَّل قيد: مدين الخزينة، مقابل دائن الذمم المدينة إن حُدد العميل،           أو دائن الإيرادات المتنوعة."
        icon={<Banknote className="size-5" />}
        scrollable
      >
        <form id="receipt-form" onSubmit={handleSubmit} className="space-y-4" noValidate>
          {formError ? <ApiErrorAlert message={formError} onRetry={() => setFormError(null)} /> : null}

          <div className="grid gap-4 md:grid-cols-2">
            <div className="space-y-1.5">
              <Label htmlFor="rc-date">تاريخ السند *</Label>
              <Input id="rc-date" type="date" value={receiptDate} onChange={(e) => setReceiptDate(e.target.value)} disabled={submitting} className={inputLtrClassName} />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="rc-bank">الحساب البنكي / الخزينة *</Label>
              <select id="rc-bank" className={selectClassName} value={bankAccountId} onChange={(e) => setBankAccountId(e.target.value)} disabled={submitting}>
                <option value="">اختر الحساب</option>
                {banks.map((b) => (
                  <option key={b.id} value={b.id}>{b.nameAr}{b.bankName ? ` — ${b.bankName}` : ""}</option>
                ))}
              </select>
            </div>

            <div className="space-y-1.5">
              <Label htmlFor="rc-amount">المبلغ *</Label>
              <Input id="rc-amount" type="number" inputMode="decimal" min="0.01" step="0.01" value={amount} onChange={(e) => setAmount(e.target.value)} disabled={submitting} className={inputLtrClassName} dir="ltr" />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="rc-currency">العملة</Label>
              <select id="rc-currency" className={selectClassName} value={currency} onChange={(e) => setCurrency(e.target.value)} disabled={submitting}>
                {CURRENCY_OPTIONS.map((c) => (
                  <option key={c.value} value={c.value}>{c.label}</option>
                ))}
              </select>
            </div>

            <div className="space-y-1.5">
              <Label htmlFor="rc-client">العميل (يُخصم من الذمم)</Label>
              <select id="rc-client" className={selectClassName} value={client} onChange={(e) => { setClient(e.target.value); setInvoiceId(""); }} disabled={submitting}>
                <option value="">— بدون —</option>
                {clients.map((c) => (
                  <option key={c.id} value={c.id}>{personName(c)}</option>
                ))}
              </select>
            </div>
            {client && !deposit ? (
              <div className="space-y-1.5">
                <Label htmlFor="rc-invoice">تخصيص على فاتورة</Label>
                <select id="rc-invoice" className={selectClassName} value={invoiceId} onChange={(e) => setInvoiceId(e.target.value)} disabled={submitting}>
                  <option value="">اختر الفاتورة</option>
                  {clientInvoices.map((invoice) => (
                    <option key={invoice.id} value={invoice.id}>
                      {invoice.invoiceNumber} · متبقي {formatMoney(invoice.remaining)}
                    </option>
                  ))}
                </select>
              </div>
            ) : null}
            <div className="space-y-1.5">
              <Label htmlFor="rc-method">طريقة الدفع</Label>
              <select id="rc-method" className={selectClassName} value={method} onChange={(e) => setMethod(e.target.value)} disabled={submitting}>
                {PAYMENT_METHOD_OPTIONS.map((o) => (
                  <option key={o.value} value={o.value}>{o.label}</option>
                ))}
              </select>
            </div>

            <div className="space-y-1.5">
              <Label htmlFor="rc-ref">المرجع</Label>
              <Input id="rc-ref" value={reference} onChange={(e) => setReference(e.target.value)} disabled={submitting} maxLength={100} className={inputLtrClassName} dir="ltr" />
            </div>
            <div className="flex items-end">
              <label className="flex h-10 cursor-pointer items-center gap-2 rounded-xl border border-[var(--color-border)] bg-[var(--color-surface)] px-3.5 text-sm">
                <input
                  type="checkbox"
                  checked={deposit}
                  onChange={(e) => {
                    setDeposit(e.target.checked);
                    if (e.target.checked) {
                      setInvoiceId("");
                    }
                  }}
                  disabled={submitting}
                  className="size-4 accent-[var(--color-gold-400)]"
                />
                إيداع بنكي
              </label>
            </div>
          </div>

          <div className="space-y-1.5">
            <Label htmlFor="rc-notes">ملاحظات</Label>
            <Textarea id="rc-notes" rows={2} value={notes} onChange={(e) => setNotes(e.target.value)} disabled={submitting} maxLength={1000} />
          </div>
        </form>

        <div className="mt-4 flex justify-end gap-2 border-t border-[var(--color-border)] pt-4">
          <Button type="submit" form="receipt-form" variant="gold" disabled={submitting}>
            {submitting ? "جارٍ الحفظ…" : (<><Save className="size-4" />تسجيل السند</>)}
          </Button>
          <Button type="button" variant="ghost" onClick={() => setDialogOpen(false)} disabled={submitting}>
            إلغاء
          </Button>
        </div>
      </AccountingDialog>
    </div>
  );
}
