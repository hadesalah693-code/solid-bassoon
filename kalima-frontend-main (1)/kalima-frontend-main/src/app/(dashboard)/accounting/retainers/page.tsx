"use client";

import { useCallback, useEffect, useState, type FormEvent } from "react";
import { Plus, Wallet } from "lucide-react";
import { ApiErrorAlert } from "@/components/shared/ApiErrorAlert";
import { AccountingRoleGate, AccountingWriteOnly } from "@/components/accounting/AccountingRoleGate";
import { DisputeModuleHero } from "@/components/disputes/DisputeModuleHero";
import { EmptyState } from "@/components/shared/EmptyState";
import { PageLoading } from "@/components/shared/PageLoading";
import { SectionCard } from "@/components/shared/SectionCard";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { personsApi } from "@/lib/api/persons";
import { invoicesApi } from "@/lib/api/invoices";
import { retainersApi } from "@/lib/api/accounting";
import { ApiRequestError } from "@/lib/api/client";
import { notifyApiError, notifySuccess } from "@/lib/feedback";
import { formatDate } from "@/lib/utils/format-date";
import { formatMoney } from "@/lib/utils/format-money";
import type { RetainerApplicationResponseDto, RetainerResponseDto } from "@/types/accounting";
import type { ClientPerson } from "@/types/person";
import type { InvoiceListItemDto } from "@/types/invoice";

function today() {
  const date = new Date();
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")}`;
}

function RetainersPageInner() {
  const [rows, setRows] = useState<RetainerResponseDto[]>([]);
  const [clients, setClients] = useState<ClientPerson[]>([]);
  const [applications, setApplications] = useState<RetainerApplicationResponseDto[]>([]);
  const [invoices, setInvoices] = useState<InvoiceListItemDto[]>([]);
  const [selected, setSelected] = useState<RetainerResponseDto | null>(null);
  const [clientId, setClientId] = useState("");
  const [amount, setAmount] = useState("");
  const [currency, setCurrency] = useState("SAR");
  const [receivedDate, setReceivedDate] = useState(today);
  const [reference, setReference] = useState("");
  const [notes, setNotes] = useState("");
  const [invoiceId, setInvoiceId] = useState("");
  const [applyAmount, setApplyAmount] = useState("");
  const [applyDate, setApplyDate] = useState(today);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const [retainerRows, clientRows] = await Promise.all([
        retainersApi.list(),
        personsApi.list({ role: "CLIENT", size: 200 }),
      ]);
      setRows(retainerRows);
      setClients(clientRows.content);
      setError(null);
    } catch (e) {
      setError(e instanceof ApiRequestError ? e.message : "تعذر تحميل السلف");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { void load(); }, [load]);

  useEffect(() => {
    if (!selected) {
      setApplications([]);
      setInvoices([]);
      return;
    }
    let cancelled = false;
    void Promise.all([
      retainersApi.applications(selected.id),
      invoicesApi.list({ clientId: selected.clientId, size: 100 }),
    ]).then(([history, invoicePage]) => {
      if (cancelled) return;
      setApplications(history);
      setInvoices(invoicePage.content.filter((invoice) => invoice.remaining > 0
        && invoice.status !== "DRAFT" && invoice.status !== "CANCELLED"));
    }).catch((e) => {
      if (!cancelled) setError(e instanceof ApiRequestError ? e.message : "تعذر تحميل سجل تطبيق السلفة");
    });
    return () => { cancelled = true; };
  }, [selected]);

  async function createRetainer(event: FormEvent) {
    event.preventDefault();
    setBusy(true);
    try {
      await retainersApi.create({ clientId, amount: Number(amount), currency, receivedDate, reference: reference || null, notes: notes || null });
      notifySuccess("تم تسجيل سلفة العميل");
      setClientId(""); setAmount(""); setReference(""); setNotes("");
      await load();
    } catch (e) { notifyApiError(e, "تعذر تسجيل السلفة"); }
    finally { setBusy(false); }
  }

  async function applyRetainer(event: FormEvent) {
    event.preventDefault();
    if (!selected || !invoiceId) return;
    setBusy(true);
    try {
      await retainersApi.apply(selected.id, {
        retainerId: selected.id,
        appliedAmount: Number(applyAmount),
        appliedDate: applyDate,
        targetType: "INVOICE",
        targetId: invoiceId,
      });
      notifySuccess("تم تخصيص السلفة على الفاتورة");
      setInvoiceId(""); setApplyAmount("");
      await load();
      const latest = (await retainersApi.list()).find((item) => item.id === selected.id);
      if (latest) setSelected(latest);
      setApplications(await retainersApi.applications(selected.id));
    } catch (e) { notifyApiError(e, "تعذر تطبيق السلفة"); }
    finally { setBusy(false); }
  }

  return (
    <div dir="rtl" className="space-y-6 pb-10">
      <DisputeModuleHero icon={Wallet} eyebrow="المالية والمحاسبة" title="سلف العملاء" description="الأموال المستلمة مقدمًا تبقى التزامًا على المكتب حتى تخصيصها على فاتورة العميل." />
      {error ? <ApiErrorAlert message={error} onRetry={() => void load()} /> : null}
      <AccountingWriteOnly>
        <SectionCard title="تسجيل سلفة" description="يُرحّل الاستلام إلى الخزينة مقابل التزام أموال العميل.">
          <form onSubmit={createRetainer} className="grid gap-3 md:grid-cols-3">
            <div className="space-y-1.5"><Label htmlFor="retainer-client">العميل</Label><select id="retainer-client" required value={clientId} onChange={(e) => setClientId(e.target.value)} className="h-10 rounded-xl border border-[var(--color-border)] bg-[var(--color-surface)] px-3"><option value="">اختر العميل</option>{clients.map((client) => <option key={client.id} value={client.id}>{client.fullName}</option>)}</select></div>
            <div className="space-y-1.5"><Label htmlFor="retainer-amount">المبلغ</Label><Input id="retainer-amount" required type="number" min="0.01" step="0.01" value={amount} onChange={(e) => setAmount(e.target.value)} /></div>
            <div className="space-y-1.5"><Label htmlFor="retainer-date">تاريخ الاستلام</Label><Input id="retainer-date" required type="date" value={receivedDate} onChange={(e) => setReceivedDate(e.target.value)} /></div>
            <div className="space-y-1.5"><Label htmlFor="retainer-ref">المرجع</Label><Input id="retainer-ref" value={reference} onChange={(e) => setReference(e.target.value)} maxLength={100} /></div>
            <div className="space-y-1.5"><Label htmlFor="retainer-currency">العملة</Label><Input id="retainer-currency" value={currency} onChange={(e) => setCurrency(e.target.value.toUpperCase())} maxLength={3} /></div>
            <div className="flex items-end"><Button disabled={busy || !clientId}><Plus className="size-4" />تسجيل السلفة</Button></div>
            <div className="space-y-1.5 md:col-span-3"><Label htmlFor="retainer-notes">ملاحظات</Label><Textarea id="retainer-notes" rows={2} value={notes} onChange={(e) => setNotes(e.target.value)} maxLength={4000} /></div>
          </form>
        </SectionCard>
      </AccountingWriteOnly>

      <SectionCard flush title="السلف المسجلة">
        {loading ? <PageLoading label="جارٍ تحميل السلف..." /> : rows.length === 0 ? <EmptyState icon={Wallet} title="لا توجد سلف" description="تُعرض هنا أرصدة العملاء المستلمة مقدمًا." /> : (
          <div className="divide-y divide-[var(--color-border)]">
            {rows.map((retainer) => <button key={retainer.id} type="button" onClick={() => setSelected(retainer)} className={`flex w-full flex-wrap items-center justify-between gap-3 px-4 py-3 text-start hover:bg-[var(--color-surface-muted)] ${selected?.id === retainer.id ? "bg-[var(--color-gold-50)]" : ""}`}>
              <span><span className="block font-semibold">{retainer.clientNameAr ?? retainer.clientId}</span><span className="text-xs text-[var(--color-muted-foreground)]">{formatDate(retainer.receivedDate)} · {retainer.status}</span></span>
              <span className="text-end text-sm"><span className="block font-semibold ltr-nums">{formatMoney(retainer.remainingAmount, retainer.currency)}</span><span className="text-xs text-[var(--color-muted-foreground)]">متبقٍ من {formatMoney(retainer.amount, retainer.currency)}</span></span>
            </button>)}
          </div>
        )}
      </SectionCard>

      {selected ? <div className="grid gap-5 lg:grid-cols-2">
        <AccountingWriteOnly><SectionCard title="تطبيق على فاتورة" description="يقلل التزام السلفة والذمم المدينة؛ لا يعيد إثبات الإيراد.">
          <form onSubmit={applyRetainer} className="space-y-3">
            <div className="space-y-1.5"><Label htmlFor="retainer-invoice">الفاتورة</Label><select id="retainer-invoice" required value={invoiceId} onChange={(e) => setInvoiceId(e.target.value)} className="h-10 w-full rounded-xl border border-[var(--color-border)] bg-[var(--color-surface)] px-3"><option value="">اختر الفاتورة</option>{invoices.map((invoice) => <option key={invoice.id} value={invoice.id}>{invoice.invoiceNumber} · متبقي {formatMoney(invoice.remaining)}</option>)}</select></div>
            <div className="grid gap-3 sm:grid-cols-2"><div className="space-y-1.5"><Label htmlFor="apply-amount">المبلغ</Label><Input id="apply-amount" required type="number" min="0.01" step="0.01" max={selected.remainingAmount} value={applyAmount} onChange={(e) => setApplyAmount(e.target.value)} /></div><div className="space-y-1.5"><Label htmlFor="apply-date">تاريخ التطبيق</Label><Input id="apply-date" required type="date" value={applyDate} onChange={(e) => setApplyDate(e.target.value)} /></div></div>
            <Button disabled={busy || !invoiceId || Number(applyAmount) > selected.remainingAmount}>تطبيق السلفة</Button>
          </form>
        </SectionCard></AccountingWriteOnly>
        <SectionCard title="سجل التطبيقات">
          {applications.length === 0 ? <p className="text-sm text-[var(--color-muted-foreground)]">لا توجد تطبيقات مسجلة.</p> : <ul className="divide-y divide-[var(--color-border)]">{applications.map((application) => <li key={application.id} className="flex justify-between gap-3 py-3 text-sm"><span>{application.targetReference ?? application.targetId}<span className="block text-xs text-[var(--color-muted-foreground)]">{formatDate(application.appliedDate)}</span></span><span className="font-semibold ltr-nums">{formatMoney(application.appliedAmount, selected.currency)}</span></li>)}</ul>}
        </SectionCard>
      </div> : null}
    </div>
  );
}

export default function RetainersPage() {
  return <AccountingRoleGate><RetainersPageInner /></AccountingRoleGate>;
}
