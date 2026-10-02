"use client";

import { useEffect, useMemo, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { ArrowRight, Calculator, Save } from "lucide-react";

import { ApiErrorAlert } from "@/components/shared/ApiErrorAlert";
import { SectionCard } from "@/components/shared/SectionCard";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";

import { invoicesApi } from "@/lib/api/invoices";
import { casesApi } from "@/lib/api/cases";
import { personsApi } from "@/lib/api/persons";
import { ApiRequestError } from "@/lib/api/client";
import { notifyApiError, notifySuccess } from "@/lib/feedback";
import { formatMoney } from "@/lib/utils/format-money";
import { cn } from "@/lib/utils/cn";
import type { CaseResponseDto } from "@/types/api";
import type { ClientPerson } from "@/types/person";

const selectClassName = cn(
  "flex h-10 w-full cursor-pointer appearance-none rounded-xl border border-[var(--color-border)] bg-[var(--color-surface)] px-3.5 py-2 text-sm text-[var(--color-foreground)] shadow-[var(--shadow-xs)] transition-[border-color,box-shadow] duration-200",
  "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--color-gold-400)]/30 focus-visible:border-[var(--color-gold-400)]",
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
  if (p.personType === "COMPANY") return p.companyName?.trim() || p.id;
  return p.fullName?.trim() || p.id;
}

/** Mirrors InvoiceService: tax = subtotal * rate / 100; total = subtotal - discount + tax. */
function round2(n: number) {
  return Math.round((n + Number.EPSILON) * 100) / 100;
}

export default function NewInvoicePage() {
  const router = useRouter();

  const [clients, setClients] = useState<ClientPerson[]>([]);
  const [cases, setCases] = useState<CaseResponseDto[]>([]);
  const [loadingRefs, setLoadingRefs] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);

  const [clientId, setClientId] = useState("");
  const [caseId, setCaseId] = useState("");
  const [issueDate, setIssueDate] = useState(today());
  const [dueDate, setDueDate] = useState("");
  const [subtotal, setSubtotal] = useState("");
  const [taxRate, setTaxRate] = useState("0");
  const [discount, setDiscount] = useState("0");
  const [notes, setNotes] = useState("");

  const [submitting, setSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      setLoadingRefs(true);
      setLoadError(null);
      try {
        const [cRes, caseRes] = await Promise.all([
          personsApi.list({ role: "CLIENT", size: 200 }),
          casesApi.list({ size: 200 }),
        ]);
        if (cancelled) return;
        setClients(cRes.content);
        setCases(caseRes.content);
      } catch (e) {
        if (!cancelled) {
          setLoadError(
            e instanceof ApiRequestError
              ? e.message
              : "تعذّر تحميل العملاء والقضايا",
          );
        }
      } finally {
        if (!cancelled) setLoadingRefs(false);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, []);

  const clientCases = useMemo(
    () => (clientId ? cases.filter((c) => c.client?.id === clientId) : []),
    [cases, clientId],
  );

  const totals = useMemo(() => {
    const sub = Number(subtotal) || 0;
    const rate = Number(taxRate) || 0;
    const disc = Number(discount) || 0;
    const tax = round2((sub * rate) / 100);
    return { sub, rate, disc, tax, total: round2(sub - disc + tax) };
  }, [subtotal, taxRate, discount]);

  function handleClientChange(value: string) {
    setClientId(value);
    setCaseId("");
  }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setSubmitError(null);

    if (!clientId) {
      setSubmitError("العميل مطلوب");
      return;
    }
    if (!issueDate) {
      setSubmitError("تاريخ الإصدار مطلوب");
      return;
    }
    if (!Number.isFinite(Number(subtotal)) || Number(subtotal) < 0) {
      setSubmitError("المبلغ الأساسي يجب أن يكون رقماً غير سالب");
      return;
    }
    if (Number(taxRate) < 0 || Number(discount) < 0) {
      setSubmitError("الضريبة والخصم يجب أن يكونا غير سالبين");
      return;
    }

    setSubmitting(true);
    try {
      const created = await invoicesApi.create({
        clientId,
        caseId: caseId || null,
        issueDate,
        dueDate: dueDate || null,
        subtotal: Number(subtotal),
        taxRate: Number(taxRate) || 0,
        discount: Number(discount) || 0,
        notes: notes.trim() || null,
      });
      notifySuccess("تم إنشاء الفاتورة بنجاح");
      router.push(`/invoices/${created.id}`);
    } catch (e) {
      if (e instanceof ApiRequestError) {
        setSubmitError(e.message);
        notifyApiError(e, "تعذّر إنشاء الفاتورة");
      } else {
        setSubmitError("تعذّر إنشاء الفاتورة");
      }
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div dir="rtl" className="space-y-6 pb-10">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="flex items-center gap-3">
          <Button asChild variant="ghost" size="sm">
            <Link href="/invoices">
              <ArrowRight className="size-4" />
              رجوع للفواتير
            </Link>
          </Button>
          <div>
            <h1 className="text-xl font-bold text-[var(--color-foreground)]">
              فاتورة جديدة
            </h1>
            <p className="text-sm text-[var(--color-muted-foreground)]">
              تسجيل فاتورة أتعاب لعميل وإرفاقها بقضية إن وُجدت.
            </p>
          </div>
        </div>
      </div>

      {loadError ? (
        <ApiErrorAlert message={loadError} />
      ) : null}

      <form onSubmit={handleSubmit} className="space-y-6">
        <SectionCard title="بيانات الفاتورة">
          <div className="grid gap-4 md:grid-cols-2">
            <div className="space-y-1.5">
              <Label htmlFor="clientId">العميل *</Label>
              <select
                id="clientId"
                required
                className={selectClassName}
                value={clientId}
                disabled={loadingRefs}
                onChange={(e) => handleClientChange(e.target.value)}
              >
                <option value="">
                  {loadingRefs ? "جارٍ التحميل..." : "اختر العميل"}
                </option>
                {clients.map((c) => (
                  <option key={c.id} value={c.id}>
                    {personName(c)}
                  </option>
                ))}
              </select>
            </div>

            <div className="space-y-1.5">
              <Label htmlFor="caseId">القضية (اختياري)</Label>
              <select
                id="caseId"
                className={selectClassName}
                value={caseId}
                disabled={loadingRefs || !clientId}
                onChange={(e) => setCaseId(e.target.value)}
              >
                <option value="">
                  {clientId ? "بدون قضية" : "اختر العميل أولاً"}
                </option>
                {clientCases.map((c) => (
                  <option key={c.id} value={c.id}>
                    {c.caseNumber} — {c.title}
                  </option>
                ))}
              </select>
            </div>

            <div className="space-y-1.5">
              <Label htmlFor="issueDate">تاريخ الإصدار *</Label>
              <Input
                id="issueDate"
                type="date"
                required
                value={issueDate}
                onChange={(e) => setIssueDate(e.target.value)}
                className={inputLtrClassName}
              />
            </div>

            <div className="space-y-1.5">
              <Label htmlFor="dueDate">تاريخ الاستحقاق</Label>
              <Input
                id="dueDate"
                type="date"
                value={dueDate}
                onChange={(e) => setDueDate(e.target.value)}
                className={inputLtrClassName}
              />
            </div>
          </div>
        </SectionCard>

        <SectionCard title="المبالغ">
          <div className="grid gap-4 md:grid-cols-3">
            <div className="space-y-1.5">
              <Label htmlFor="subtotal">المبلغ قبل الضريبة *</Label>
              <Input
                id="subtotal"
                type="number"
                inputMode="decimal"
                min="0"
                step="0.01"
                required
                placeholder="0.00"
                value={subtotal}
                onChange={(e) => setSubtotal(e.target.value)}
                className={inputLtrClassName}
                dir="ltr"
              />
            </div>

            <div className="space-y-1.5">
              <Label htmlFor="taxRate">نسبة الضريبة ٪</Label>
              <Input
                id="taxRate"
                type="number"
                inputMode="decimal"
                min="0"
                step="0.01"
                placeholder="0"
                value={taxRate}
                onChange={(e) => setTaxRate(e.target.value)}
                className={inputLtrClassName}
                dir="ltr"
              />
            </div>

            <div className="space-y-1.5">
              <Label htmlFor="discount">الخصم</Label>
              <Input
                id="discount"
                type="number"
                inputMode="decimal"
                min="0"
                step="0.01"
                placeholder="0"
                value={discount}
                onChange={(e) => setDiscount(e.target.value)}
                className={inputLtrClassName}
                dir="ltr"
              />
            </div>
          </div>

          <div className="mt-4 rounded-xl border border-[var(--color-border)] bg-[var(--color-surface-muted)] p-4">
            <p className="mb-3 flex items-center gap-1.5 text-sm font-semibold text-[var(--color-foreground)]">
              <Calculator className="size-4" />
              ملخص الحساب
            </p>
            <dl className="grid gap-2 text-sm sm:grid-cols-2">
              <div className="flex items-center justify-between gap-2">
                <dt className="text-[var(--color-muted-foreground)]">
                  المبلغ قبل الضريبة
                </dt>
                <dd className="font-medium ltr-nums">
                  {formatMoney(totals.sub)}
                </dd>
              </div>
              <div className="flex items-center justify-between gap-2">
                <dt className="text-[var(--color-muted-foreground)]">
                  الخصم
                </dt>
                <dd className="font-medium ltr-nums text-[var(--color-danger)]">
                  −{formatMoney(totals.disc)}
                </dd>
              </div>
              <div className="flex items-center justify-between gap-2">
                <dt className="text-[var(--color-muted-foreground)]">
                  قيمة الضريبة ({totals.rate.toLocaleString("ar-EG")}٪)
                </dt>
                <dd className="font-medium ltr-nums">
                  {formatMoney(totals.tax)}
                </dd>
              </div>
              <div className="flex items-center justify-between gap-2 border-t border-[var(--color-border)] pt-2 sm:col-span-2">
                <dt className="font-semibold text-[var(--color-foreground)]">
                  الإجمالي
                </dt>
                <dd className="text-lg font-extrabold text-[var(--color-gold-600)] ltr-nums">
                  {formatMoney(totals.total)}
                </dd>
              </div>
            </dl>
          </div>
        </SectionCard>

        <SectionCard title="ملاحظات">
          <Textarea
            value={notes}
            maxLength={2000}
            onChange={(e) => setNotes(e.target.value)}
            placeholder="أي تفاصيل إضافية عن الفاتورة..."
          />
          <p className="mt-1 text-xs text-[var(--color-muted-foreground)]">
            {notes.length.toLocaleString("ar-EG")} / 2000
          </p>
        </SectionCard>

        {submitError ? (
          <ApiErrorAlert message={submitError} />
        ) : null}

        <div className="flex flex-wrap items-center justify-end gap-2">
          <Button asChild variant="ghost" type="button">
            <Link href="/invoices">إلغاء</Link>
          </Button>
          <Button type="submit" disabled={submitting || loadingRefs}>
            {submitting ? (
              "جارٍ الحفظ..."
            ) : (
              <>
                <Save className="size-4" />
                حفظ الفاتورة
              </>
            )}
          </Button>
        </div>
      </form>
    </div>
  );
}
