"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import {
  ArrowRight,
  Banknote,
  Briefcase,
  Calendar,
  Contact,
  FileSpreadsheet,
  Landmark,
  Receipt,
  User,
} from "lucide-react";

import { ApiErrorAlert } from "@/components/shared/ApiErrorAlert";
import { PageLoading } from "@/components/shared/PageLoading";
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

import { invoicesApi } from "@/lib/api/invoices";
import { bankAccountsApi } from "@/lib/api/accounting";
import { ApiRequestError } from "@/lib/api/client";
import { notifyApiError, notifySuccess } from "@/lib/feedback";
import {
  INVOICE_STATUS_ACTION_AR,
  INVOICE_STATUS_AR,
  INVOICE_STATUS_TONE,
  INVOICE_STATUS_TRANSITIONS,
} from "@/lib/invoices/labels";
import { formatDate, formatDateTime } from "@/lib/utils/format-date";
import { formatMoney, paidPercent } from "@/lib/utils/format-money";
import { cn } from "@/lib/utils/cn";
import {
  INVOICE_PAYMENT_METHODS,
  type InvoiceResponseDto,
  type InvoiceStatusDto,
} from "@/types/invoice";
import type { BankAccountResponseDto } from "@/types/accounting";

const inputLtrClassName = "ltr-nums text-start";

function today() {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(
    d.getDate(),
  ).padStart(2, "0")}`;
}

function Row({ label, value }: { label: string; value: React.ReactNode }) {
  return (
    <div className="flex items-center justify-between gap-3 border-b border-[var(--color-border)] py-2 last:border-0">
      <dt className="text-sm text-[var(--color-muted-foreground)]">{label}</dt>
      <dd className="text-sm font-medium text-[var(--color-foreground)]">
        {value}
      </dd>
    </div>
  );
}

export default function InvoiceDetailPage() {
  const params = useParams<{ id: string }>();
  const router = useRouter();
  const id = params.id;

  const [invoice, setInvoice] = useState<InvoiceResponseDto | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const [payAmount, setPayAmount] = useState("");
  const [payDate, setPayDate] = useState(today());
  const [payMethod, setPayMethod] = useState<string>("");
  const [payBankId, setPayBankId] = useState("");
  const [payReference, setPayReference] = useState("");
  const [payNotes, setPayNotes] = useState("");
  const [payError, setPayError] = useState<string | null>(null);
  const [paying, setPaying] = useState(false);
  const [banks, setBanks] = useState<BankAccountResponseDto[]>([]);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const res = await bankAccountsApi.list();
        if (cancelled) return;
        const active = res.filter((b) => b.active);
        setBanks(active);
        setPayBankId((prev) => prev || active.find((b) => b.currency === "SAR")?.id || active[0]?.id || "");
      } catch {
        // The payment form falls back to the backend default cash account.
      }
    })();
    return () => {
      cancelled = true;
    };
  }, []);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setInvoice(await invoicesApi.get(id));
    } catch (e) {
      setError(
        e instanceof ApiRequestError ? e.message : "تعذّر تحميل الفاتورة",
      );
    } finally {
      setLoading(false);
    }
  }, [id]);

  useEffect(() => {
    void load();
  }, [load]);

  const transitions = useMemo(
    () => (invoice ? INVOICE_STATUS_TRANSITIONS[invoice.status] : []),
    [invoice],
  );

  const canPay =
    !!invoice &&
    invoice.remaining > 0 &&
    invoice.status !== "CANCELLED" &&
    invoice.status !== "DRAFT";

  const pct = invoice ? paidPercent(invoice.amountPaid, invoice.total) : 0;

  async function handleStatusChange(next: InvoiceStatusDto) {
    if (!invoice) return;
    setBusy(true);
    try {
      const updated = await invoicesApi.updateStatus(invoice.id, next);
      setInvoice(updated);
      notifySuccess("تم تحديث حالة الفاتورة");
    } catch (e) {
      notifyApiError(e, "تعذّر تحديث الحالة");
    } finally {
      setBusy(false);
    }
  }

  async function handlePayment(e: React.FormEvent) {
    e.preventDefault();
    if (!invoice) return;
    setPayError(null);

    const amount = Number(payAmount);
    if (!Number.isFinite(amount) || amount <= 0) {
      setPayError("قيمة الدفعة يجب أن تكون أكبر من صفر");
      return;
    }
    if (amount > invoice.remaining) {
      setPayError(
        `الدفعة تتجاوز المتبقي (${formatMoney(invoice.remaining)})`,
      );
      return;
    }
    if (!payDate) {
      setPayError("تاريخ الدفعة مطلوب");
      return;
    }

    setPaying(true);
    try {
      const updated = await invoicesApi.recordPayment(invoice.id, {
        amount,
        paymentDate: payDate,
        method: payMethod || null,
        bankAccountId: payBankId || null,
        reference: payReference.trim() || null,
        notes: payNotes.trim() || null,
      });
      setInvoice(updated);
      setPayAmount("");
      setPayReference("");
      setPayNotes("");
      notifySuccess("تم تسجيل الدفعة");
    } catch (e) {
      if (e instanceof ApiRequestError) {
        setPayError(e.message);
        notifyApiError(e, "تعذّر تسجيل الدفعة");
      } else {
        setPayError("تعذّر تسجيل الدفعة");
      }
    } finally {
      setPaying(false);
    }
  }

  if (loading) return <PageLoading label="جاري تحميل الفاتورة..." />;

  if (error || !invoice) {
    return (
      <div dir="rtl" className="space-y-4">
        <ApiErrorAlert message={error ?? "الفاتورة غير موجودة"} onRetry={load} />
        <Button asChild variant="ghost">
          <Link href="/invoices">
            <ArrowRight className="size-4" />
            رجوع للفواتير
          </Link>
        </Button>
      </div>
    );
  }

  return (
    <div dir="rtl" className="space-y-6 pb-10">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="flex items-center gap-3">
          <Button asChild variant="ghost" size="sm">
            <Link href="/invoices">
              <ArrowRight className="size-4" />
              رجوع
            </Link>
          </Button>
          <div>
            <div className="flex items-center gap-2">
              <h1 className="text-xl font-bold text-[var(--color-foreground)] ltr-nums">
                {invoice.invoiceNumber}
              </h1>
              <Badge tone={INVOICE_STATUS_TONE[invoice.status]}>
                {INVOICE_STATUS_AR[invoice.status]}
              </Badge>
            </div>
            <p className="text-sm text-[var(--color-muted-foreground)]">
              {invoice.clientName ?? "—"}
              {invoice.caseNumber ? ` • قضية ${invoice.caseNumber}` : ""}
            </p>
          </div>
        </div>

        {transitions.length > 0 ? (
          <div className="flex flex-wrap items-center gap-2">
            {transitions.map((next) => (
              <Button
                key={next}
                variant={next === "CANCELLED" ? "outline" : "default"}
                size="sm"
                disabled={busy}
                onClick={() => void handleStatusChange(next)}
              >
                {INVOICE_STATUS_ACTION_AR[next] ?? INVOICE_STATUS_AR[next]}
              </Button>
            ))}
          </div>
        ) : null}
      </div>

      <div className="grid gap-4 lg:grid-cols-3">
        <div className="space-y-4 lg:col-span-2">
          <SectionCard title="المبالغ">
            <div className="space-y-3">
              <div className="flex items-center justify-between gap-2">
                <span className="text-sm text-[var(--color-muted-foreground)]">
                  نسبة السداد
                </span>
                <span className="text-sm font-semibold ltr-nums">
                  {pct.toLocaleString("ar-EG")}٪
                </span>
              </div>
              <div className="h-2 w-full overflow-hidden rounded-full bg-[var(--color-surface-muted)]">
                <div
                  className="h-full rounded-full bg-[var(--color-success)] transition-[width] duration-300"
                  style={{ width: `${pct}%` }}
                />
              </div>
              <dl className="mt-4">
                <Row
                  label="المبلغ قبل الضريبة"
                  value={<span className="ltr-nums">{formatMoney(invoice.subtotal)}</span>}
                />
                <Row
                  label="الخصم"
                  value={
                    <span className="ltr-nums text-[var(--color-danger)]">
                      −{formatMoney(invoice.discount)}
                    </span>
                  }
                />
                <Row
                  label={`الضريبة (${(invoice.taxRate ?? 0).toLocaleString("ar-EG")}٪)`}
                  value={<span className="ltr-nums">{formatMoney(invoice.taxAmount)}</span>}
                />
                <Row
                  label="الإجمالي"
                  value={
                    <span className="text-base font-extrabold text-[var(--color-gold-600)] ltr-nums">
                      {formatMoney(invoice.total)}
                    </span>
                  }
                />
                <Row
                  label="المدفوع"
                  value={
                    <span className="ltr-nums text-[var(--color-success)]">
                      {formatMoney(invoice.amountPaid)}
                    </span>
                  }
                />
                <Row
                  label="المتبقي"
                  value={
                    <span className="text-base font-extrabold text-[var(--color-danger)] ltr-nums">
                      {formatMoney(invoice.remaining)}
                    </span>
                  }
                />
              </dl>
            </div>
          </SectionCard>

          {invoice.notes ? (
            <SectionCard title="ملاحظات">
              <p className="whitespace-pre-wrap text-sm leading-relaxed text-[var(--color-foreground)]">
                {invoice.notes}
              </p>
            </SectionCard>
          ) : null}

          {invoice.payments.length > 0 ? (
            <SectionCard flush title="الدفعات">
              <div className="overflow-x-auto">
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead className="text-right">التاريخ</TableHead>
                      <TableHead className="text-right">المبلغ</TableHead>
                      <TableHead className="text-right">الطريقة</TableHead>
                      <TableHead className="text-right">الحساب</TableHead>
                      <TableHead className="text-right">المرجع</TableHead>
                      <TableHead className="text-right">القيد</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {invoice.payments.map((p) => (
                      <TableRow key={p.id}>
                        <TableCell className="whitespace-nowrap text-[var(--color-muted-foreground)]">
                          {formatDate(p.paymentDate)}
                        </TableCell>
                        <TableCell className="whitespace-nowrap font-semibold ltr-nums">
                          {formatMoney(p.amount)}
                        </TableCell>
                        <TableCell className="text-xs text-[var(--color-muted-foreground)]">
                          {INVOICE_PAYMENT_METHODS.find((m) => m.value === p.method)?.label ?? "—"}
                        </TableCell>
                        <TableCell className="text-xs text-[var(--color-muted-foreground)]">
                          {p.bankAccountName ?? "—"}
                        </TableCell>
                        <TableCell className="ltr-nums text-xs text-[var(--color-muted-foreground)]">
                          {p.reference ?? "—"}
                        </TableCell>
                        <TableCell className="whitespace-nowrap ltr-nums text-xs text-[var(--color-muted-foreground)]">
                          {p.journalEntryNumber ?? "—"}
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </div>
            </SectionCard>
          ) : null}

          {canPay ? (
            <SectionCard title="تسجيل دفعة">
              <form onSubmit={handlePayment} className="space-y-4">
                <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
                  <div className="space-y-1.5">
                    <Label htmlFor="payAmount">قيمة الدفعة *</Label>
                    <Input
                      id="payAmount"
                      type="number"
                      inputMode="decimal"
                      min="0.01"
                      step="0.01"
                      required
                      dir="ltr"
                      placeholder="0.00"
                      value={payAmount}
                      onChange={(e) => setPayAmount(e.target.value)}
                      className={inputLtrClassName}
                    />
                  </div>
                  <div className="space-y-1.5">
                    <Label htmlFor="payDate">تاريخ الدفعة *</Label>
                    <Input
                      id="payDate"
                      type="date"
                      required
                      value={payDate}
                      onChange={(e) => setPayDate(e.target.value)}
                      className={inputLtrClassName}
                    />
                  </div>
                  <div className="space-y-1.5">
                    <Label htmlFor="payMethod">طريقة الدفع</Label>
                    <select
                      id="payMethod"
                      className={cn(
                        "flex h-10 w-full cursor-pointer appearance-none rounded-xl border border-[var(--color-border)] bg-[var(--color-surface)] px-3.5 py-2 text-sm text-[var(--color-foreground)]",
                        "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--color-gold-400)]/30",
                      )}
                      value={payMethod}
                      onChange={(e) => setPayMethod(e.target.value)}
                    >
                      <option value="">غير محددة</option>
                      {INVOICE_PAYMENT_METHODS.map((m) => (
                        <option key={m.value} value={m.value}>
                          {m.label}
                        </option>
                      ))}
                    </select>
                  </div>
                  <div className="space-y-1.5">
                    <Label htmlFor="payBank">الحساب المحصَّل إليه</Label>
                    <select
                      id="payBank"
                      className={cn(
                        "flex h-10 w-full cursor-pointer appearance-none rounded-xl border border-[var(--color-border)] bg-[var(--color-surface)] px-3.5 py-2 text-sm text-[var(--color-foreground)]",
                        "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--color-gold-400)]/30",
                      )}
                      value={payBankId}
                      onChange={(e) => setPayBankId(e.target.value)}
                    >
                      <option value="">الافتراضي (الصندوق)</option>
                      {banks.map((b) => (
                        <option key={b.id} value={b.id}>
                          {b.nameAr} ({b.accountCode})
                        </option>
                      ))}
                    </select>
                  </div>
                  <div className="space-y-1.5">
                    <Label htmlFor="payReference">المرجع</Label>
                    <Input
                      id="payReference"
                      maxLength={100}
                      dir="ltr"
                      placeholder="رقم الشيك أو التحويل"
                      value={payReference}
                      onChange={(e) => setPayReference(e.target.value)}
                      className={inputLtrClassName}
                    />
                  </div>
                </div>

                <div className="space-y-1.5">
                  <Label htmlFor="payNotes">ملاحظات الدفعة</Label>
                  <Textarea
                    id="payNotes"
                    rows={3}
                    maxLength={500}
                    value={payNotes}
                    onChange={(e) => setPayNotes(e.target.value)}
                    placeholder="رقم الشيك، رقم التحويل، أو أي تفاصيل..."
                  />
                </div>

                {payError ? <ApiErrorAlert message={payError} /> : null}

                <div className="flex justify-end">
                  <Button type="submit" disabled={paying}>
                    {paying ? (
                      "جارٍ التسجيل..."
                    ) : (
                      <>
                        <Banknote className="size-4" />
                        تسجيل الدفعة
                      </>
                    )}
                  </Button>
                </div>
              </form>
            </SectionCard>
          ) : null}
        </div>

        <div className="space-y-4">
          <SectionCard title="البيانات">
            <dl>
              <Row
                label="العميل"
                value={
                  <span className="flex items-center gap-1.5">
                    <Contact className="size-3.5 text-[var(--color-muted-foreground)]" />
                    {invoice.clientName ?? "—"}
                  </span>
                }
              />
              <Row
                label="القضية"
                value={
                  <span className="flex items-center gap-1.5 ltr-nums">
                    <Briefcase className="size-3.5 text-[var(--color-muted-foreground)]" />
                    {invoice.caseNumber ?? "—"}
                  </span>
                }
              />
              <Row
                label="تاريخ الإصدار"
                value={
                  <span className="flex items-center gap-1.5">
                    <Calendar className="size-3.5 text-[var(--color-muted-foreground)]" />
                    {invoice.issueDate ? formatDate(invoice.issueDate) : "—"}
                  </span>
                }
              />
              <Row
                label="تاريخ الاستحقاق"
                value={
                  <span className="flex items-center gap-1.5">
                    <Calendar className="size-3.5 text-[var(--color-muted-foreground)]" />
                    {invoice.dueDate ? formatDate(invoice.dueDate) : "—"}
                  </span>
                }
              />
              <Row
                label="سجّلها"
                value={
                  <span className="flex items-center gap-1.5">
                    <User className="size-3.5 text-[var(--color-muted-foreground)]" />
                    {invoice.recordedBy ?? "—"}
                  </span>
                }
              />
              <Row
                label="تاريخ الإنشاء"
                value={
                  <span className="flex items-center gap-1.5 ltr-nums">
                    <Receipt className="size-3.5 text-[var(--color-muted-foreground)]" />
                    {invoice.createdAt ? formatDateTime(invoice.createdAt) : "—"}
                  </span>
                }
              />
              <Row
                label="قيد الإيراد"
                value={
                  invoice.journalEntryNumber ? (
                    <span className="flex items-center gap-1.5 ltr-nums">
                      <FileSpreadsheet className="size-3.5 text-[var(--color-gold-500)]" />
                      {invoice.journalEntryNumber}
                    </span>
                  ) : (
                    <span className="text-[var(--color-muted-foreground)]">
                      {invoice.status === "DRAFT" ? "لم يُرحّل بعد" : "—"}
                    </span>
                  )
                }
              />
            </dl>
            {invoice.journalEntryNumber ? (
              <Button asChild variant="ghost" size="sm" className="mt-3 w-full">
                <Link href="/accounting/journal">
                  <Landmark className="size-3.5" />
                  عرض في دفتر اليومية
                </Link>
              </Button>
            ) : null}
          </SectionCard>

          <div className="flex justify-end">
            <Button
              variant="ghost"
              size="sm"
              onClick={() => router.refresh()}
              className="text-[var(--color-muted-foreground)]"
            >
              تحديث البيانات
            </Button>
          </div>
        </div>
      </div>
    </div>
  );
}
