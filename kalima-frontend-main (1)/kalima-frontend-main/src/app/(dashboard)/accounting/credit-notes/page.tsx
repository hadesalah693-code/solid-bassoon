"use client";

import { useCallback, useEffect, useState, type FormEvent } from "react";

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

import { bankAccountsApi } from "@/lib/api/accounting";
import { creditDebitNotesApi, refundsApi } from "@/lib/api/accounting-extended";
import { invoicesApi } from "@/lib/api/invoices";
import { personsApi } from "@/lib/api/persons";
import { notifyApiError, notifyError, notifySuccess } from "@/lib/feedback";
import { cn } from "@/lib/utils/cn";
import { formatDate } from "@/lib/utils/format-date";
import { formatMoney } from "@/lib/utils/format-money";
import type { BankAccountResponseDto } from "@/types/accounting";
import type { CreditDebitNote, NoteType, Refund } from "@/types/accounting-extended";
import type { InvoiceListItemDto } from "@/types/invoice";
import type { ClientPerson } from "@/types/person";

const selectClassName = cn(
  "flex h-10 w-full cursor-pointer appearance-none rounded-xl border border-[var(--color-border)] bg-[var(--color-surface)] px-3.5 py-2 text-sm text-[var(--color-foreground)]",
  "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--color-gold-400)]",
);

const today = () => new Date().toISOString().slice(0, 10);

function personName(p: ClientPerson) {
  if (p.personType === "COMPANY") return p.companyName?.trim() || p.id;
  return p.fullName?.trim() || p.id;
}

/**
 * Credit/debit notes and refunds.
 *
 * <p>Both reverse the client's position, so they sit together on one screen: a note
 * reduces what is owed, a refund returns cash already taken. Voided rows stay
 * visible and struck through rather than disappearing, because an audit needs to see
 * that a document was cancelled rather than infer it from its absence.
 *
 * <p>Amounts are validated by the server: tax is derived from the rate there, and a
 * credit note can never exceed the invoice balance still open. The form therefore
 * sends only what the user typed and lets those rules live in one place.
 */
export default function CreditNotesPage() {
  const [notes, setNotes] = useState<CreditDebitNote[]>([]);
  const [refunds, setRefunds] = useState<Refund[]>([]);
  const [invoices, setInvoices] = useState<InvoiceListItemDto[]>([]);
  const [clients, setClients] = useState<ClientPerson[]>([]);
  const [accounts, setAccounts] = useState<BankAccountResponseDto[]>([]);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const [noteType, setNoteType] = useState<NoteType>("CREDIT");
  const [noteInvoice, setNoteInvoice] = useState("");
  const [noteClient, setNoteClient] = useState("");
  const [noteDate, setNoteDate] = useState(today);
  const [noteSubtotal, setNoteSubtotal] = useState("");
  const [noteTaxRate, setNoteTaxRate] = useState("");
  const [noteReason, setNoteReason] = useState("");

  const [refundInvoice, setRefundInvoice] = useState("");
  const [refundDate, setRefundDate] = useState(today);
  const [refundAmount, setRefundAmount] = useState("");
  const [refundBank, setRefundBank] = useState("");
  const [refundReason, setRefundReason] = useState("");

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const [n, r, inv, cl, acc] = await Promise.all([
        creditDebitNotesApi.list(),
        refundsApi.list(),
        invoicesApi.list({ size: 100 }),
        personsApi.list({ role: "CLIENT", size: 200 }),
        bankAccountsApi.list(),
      ]);
      setNotes(n);
      setRefunds(r);
      setInvoices(inv.content);
      setClients(cl.content);
      setAccounts(acc);
    } catch (e) {
      setError(e instanceof Error ? e.message : "تعذر تحميل البيانات");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const submitNote = async (event: FormEvent) => {
    event.preventDefault();
    if (!noteInvoice && !noteClient) {
      notifyError("حدد الفاتورة أو العميل قبل حفظ الإشعار");
      return;
    }
    setSaving("note");
    try {
      const created = await creditDebitNotesApi.create({
        noteType,
        ...(noteInvoice ? { invoiceId: noteInvoice } : {}),
        ...(!noteInvoice && noteClient ? { clientId: noteClient } : {}),
        noteDate,
        subtotal: Number(noteSubtotal),
        ...(noteTaxRate ? { taxRate: Number(noteTaxRate) } : {}),
        ...(noteReason.trim() ? { reason: noteReason.trim() } : {}),
      });
      notifySuccess(`تم إنشاء الإشعار ${created.noteNumber}`);
      setNoteSubtotal("");
      setNoteReason("");
      await load();
    } catch (e) {
      notifyApiError(e, "تعذر إنشاء الإشعار");
    } finally {
      setSaving(null);
    }
  };

  const submitRefund = async (event: FormEvent) => {
    event.preventDefault();
    setSaving("refund");
    try {
      const created = await refundsApi.create({
        ...(refundInvoice ? { invoiceId: refundInvoice } : {}),
        refundDate,
        amount: Number(refundAmount),
        bankAccountId: refundBank,
        reason: refundReason.trim(),
      });
      notifySuccess(`تم تسجيل الاسترداد ${created.refundNumber}`);
      setRefundAmount("");
      setRefundReason("");
      await load();
    } catch (e) {
      notifyApiError(e, "تعذر تسجيل الاسترداد");
    } finally {
      setSaving(null);
    }
  };

  const voidNote = async (row: CreditDebitNote) => {
    if (!window.confirm(`إلغاء الإشعار ${row.noteNumber}؟`)) return;
    setSaving(`note-${row.id}`);
    try {
      await creditDebitNotesApi.voidNote(row.id);
      notifySuccess("تم إلغاء الإشعار");
      await load();
    } catch (e) {
      notifyApiError(e, "تعذر إلغاء الإشعار");
    } finally {
      setSaving(null);
    }
  };

  const voidRefund = async (row: Refund) => {
    if (!window.confirm(`إلغاء سند الاسترداد ${row.refundNumber}؟`)) return;
    setSaving(`refund-${row.id}`);
    try {
      await refundsApi.voidRefund(row.id);
      notifySuccess("تم إلغاء سند الاسترداد");
      await load();
    } catch (e) {
      notifyApiError(e, "تعذر إلغاء سند الاسترداد");
    } finally {
      setSaving(null);
    }
  };

  if (loading) return <PageLoading label="جارٍ التحميل" />;

  return (
    <div className="space-y-6">
      <ApiErrorAlert message={error ?? ""} onRetry={load} />

      <AccountingWriteOnly>
        <SectionCard title="إشعار خصم أو مدينة">
          <form onSubmit={submitNote} className="grid gap-3 md:grid-cols-3 xl:grid-cols-6">
            <div>
              <Label htmlFor="note-type">النوع</Label>
              <select
                id="note-type"
                className={selectClassName}
                value={noteType}
                onChange={(e) => setNoteType(e.target.value as NoteType)}
              >
                <option value="CREDIT">إشعار خصم</option>
                <option value="DEBIT">إشعار مدينة</option>
              </select>
            </div>
            <div>
              <Label htmlFor="note-invoice">الفاتورة</Label>
              <select
                id="note-invoice"
                className={selectClassName}
                value={noteInvoice}
                onChange={(e) => setNoteInvoice(e.target.value)}
              >
                <option value="">بدون فاتورة</option>
                {invoices.map((i) => (
                  <option key={i.id} value={i.id}>
                    {i.invoiceNumber} — {i.clientName ?? "-"}
                  </option>
                ))}
              </select>
            </div>
            <div>
              <Label htmlFor="note-client">العميل</Label>
              <select
                id="note-client"
                className={selectClassName}
                value={noteClient}
                onChange={(e) => setNoteClient(e.target.value)}
                disabled={!!noteInvoice}
              >
                <option value="">من الفاتورة</option>
                {clients.map((c) => (
                  <option key={c.id} value={c.id}>
                    {personName(c)}
                  </option>
                ))}
              </select>
            </div>
            <div>
              <Label htmlFor="note-date">التاريخ</Label>
              <Input
                id="note-date"
                type="date"
                value={noteDate}
                onChange={(e) => setNoteDate(e.target.value)}
                required
              />
            </div>
            <div>
              <Label htmlFor="note-subtotal">المبلغ قبل الضريبة</Label>
              <Input
                id="note-subtotal"
                type="number"
                min="0.01"
                step="0.01"
                value={noteSubtotal}
                onChange={(e) => setNoteSubtotal(e.target.value)}
                required
              />
            </div>
            <div>
              <Label htmlFor="note-tax">نسبة الضريبة %</Label>
              <Input
                id="note-tax"
                type="number"
                min="0"
                max="100"
                step="0.01"
                value={noteTaxRate}
                onChange={(e) => setNoteTaxRate(e.target.value)}
              />
            </div>
            <div className="md:col-span-2 xl:col-span-5">
              <Label htmlFor="note-reason">السبب</Label>
              <Input
                id="note-reason"
                value={noteReason}
                onChange={(e) => setNoteReason(e.target.value)}
              />
            </div>
            <div className="flex items-end">
              <Button type="submit" disabled={saving !== null}>
                {saving === "note" ? "جارٍ الحفظ" : "حفظ الإشعار"}
              </Button>
            </div>
          </form>
        </SectionCard>

        <SectionCard title="تسجيل مبلغ مسترد">
          <form onSubmit={submitRefund} className="grid gap-3 md:grid-cols-3 xl:grid-cols-6">
            <div className="xl:col-span-2">
              <Label htmlFor="refund-invoice">الفاتورة</Label>
              <select
                id="refund-invoice"
                className={selectClassName}
                value={refundInvoice}
                onChange={(e) => setRefundInvoice(e.target.value)}
              >
                <option value="">بدون فاتورة</option>
                {invoices.map((i) => (
                  <option key={i.id} value={i.id}>
                    {i.invoiceNumber} — {i.clientName ?? "-"}
                  </option>
                ))}
              </select>
            </div>
            <div>
              <Label htmlFor="refund-date">التاريخ</Label>
              <Input
                id="refund-date"
                type="date"
                value={refundDate}
                onChange={(e) => setRefundDate(e.target.value)}
                required
              />
            </div>
            <div>
              <Label htmlFor="refund-amount">المبلغ</Label>
              <Input
                id="refund-amount"
                type="number"
                min="0.01"
                step="0.01"
                value={refundAmount}
                onChange={(e) => setRefundAmount(e.target.value)}
                required
              />
            </div>
            <div>
              <Label htmlFor="refund-bank">الحساب البنكي</Label>
              <select
                id="refund-bank"
                className={selectClassName}
                value={refundBank}
                onChange={(e) => setRefundBank(e.target.value)}
                required
              >
                <option value="">اختر الحساب</option>
                {accounts.map((a) => (
                  <option key={a.id} value={a.id}>
                    {a.nameAr}
                  </option>
                ))}
              </select>
            </div>
            <div>
              <Label htmlFor="refund-reason">السبب</Label>
              <Input
                id="refund-reason"
                value={refundReason}
                onChange={(e) => setRefundReason(e.target.value)}
                required
              />
            </div>
            <div className="flex items-end">
              <Button type="submit" disabled={saving !== null}>
                {saving === "refund" ? "جارٍ الحفظ" : "حفظ"}
              </Button>
            </div>
          </form>
        </SectionCard>
      </AccountingWriteOnly>

      <SectionCard title="إشعارات الخصم والمدينة">
        {notes.length === 0 ? (
          <EmptyState title="لا توجد إشعارات" />
        ) : (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>رقم الإشعار</TableHead>
                <TableHead>النوع</TableHead>
                <TableHead>التاريخ</TableHead>
                <TableHead>الفاتورة</TableHead>
                <TableHead>العميل</TableHead>
                <TableHead className="text-end">الإجمالي</TableHead>
                <TableHead />
              </TableRow>
            </TableHeader>
            <TableBody>
              {notes.map((n) => (
                <TableRow key={n.id} className={n.voided ? "opacity-50" : undefined}>
                  <TableCell className="ltr-nums">{n.noteNumber}</TableCell>
                  <TableCell>
                    <Badge tone={n.noteType === "CREDIT" ? "info" : "warning"}>
                      {n.noteType === "CREDIT" ? "خصم" : "مدينة"}
                    </Badge>
                  </TableCell>
                  <TableCell>{formatDate(n.noteDate)}</TableCell>
                  <TableCell className="ltr-nums">{n.invoiceNumber ?? "-"}</TableCell>
                  <TableCell>{n.clientName ?? "-"}</TableCell>
                  <TableCell className="text-end ltr-nums">
                    {formatMoney(n.total)}
                    {n.voided ? " (ملغى)" : ""}
                  </TableCell>
                  <TableCell className="text-end">
                    {!n.voided && (
                      <AccountingWriteOnly>
                        <Button
                          type="button"
                          size="sm"
                          variant="outline"
                          disabled={saving !== null}
                          onClick={() => void voidNote(n)}
                        >
                          {saving === `note-${n.id}` ? "جارٍ" : "إلغاء"}
                        </Button>
                      </AccountingWriteOnly>
                    )}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
      </SectionCard>

      <SectionCard title="المبالغ المستردة">
        {refunds.length === 0 ? (
          <EmptyState title="لا توجد مبالغ مستردة" />
        ) : (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>رقم السند</TableHead>
                <TableHead>التاريخ</TableHead>
                <TableHead>الفاتورة</TableHead>
                <TableHead>العميل</TableHead>
                <TableHead>السبب</TableHead>
                <TableHead className="text-end">المبلغ</TableHead>
                <TableHead />
              </TableRow>
            </TableHeader>
            <TableBody>
              {refunds.map((r) => (
                <TableRow key={r.id} className={r.voided ? "opacity-50" : undefined}>
                  <TableCell className="ltr-nums">{r.refundNumber}</TableCell>
                  <TableCell>{formatDate(r.refundDate)}</TableCell>
                  <TableCell className="ltr-nums">{r.invoiceNumber ?? "-"}</TableCell>
                  <TableCell>{r.clientName ?? "-"}</TableCell>
                  <TableCell>{r.reason ?? "-"}</TableCell>
                  <TableCell className="text-end ltr-nums">
                    {formatMoney(r.amount)}
                    {r.voided ? " (ملغى)" : ""}
                  </TableCell>
                  <TableCell className="text-end">
                    {!r.voided && (
                      <AccountingWriteOnly>
                        <Button
                          type="button"
                          size="sm"
                          variant="outline"
                          disabled={saving !== null}
                          onClick={() => void voidRefund(r)}
                        >
                          {saving === `refund-${r.id}` ? "جارٍ" : "إلغاء"}
                        </Button>
                      </AccountingWriteOnly>
                    )}
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