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

import { accountsApi, bankAccountsApi } from "@/lib/api/accounting";
import { payablesApi } from "@/lib/api/accounting-extended";
import { notifyApiError, notifySuccess } from "@/lib/feedback";
import { cn } from "@/lib/utils/cn";
import { formatDate } from "@/lib/utils/format-date";
import { formatMoney } from "@/lib/utils/format-money";
import type { AccountResponseDto, BankAccountResponseDto } from "@/types/accounting";
import type { Vendor, VendorBill, VendorPayment } from "@/types/accounting-extended";

const STATUS_LABELS: Record<VendorBill["status"], string> = {
  OPEN: "مفتوحة",
  PARTIALLY_PAID: "مسددة جزئياً",
  PAID: "مسددة",
  CANCELLED: "ملغاة",
};

const selectClassName = cn(
  "flex h-10 w-full cursor-pointer appearance-none rounded-xl border border-[var(--color-border)] bg-[var(--color-surface)] px-3.5 py-2 text-sm text-[var(--color-foreground)]",
  "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--color-gold-400)]",
);

const today = () => new Date().toISOString().slice(0, 10);

/**
 * Vendor bills.
 *
 * <p>The remaining column and the overdue badge are derived on the server from the
 * amount actually paid, so this screen cannot drift from what the ledger says is
 * owed. Cancelled bills remain listed for the same reason voided notes do.
 *
 * <p>Payments are entered against the bill and reduce the server-side remaining
 * amount; nothing here recomputes what is owed, so a stale figure in the browser can
 * never overwrite the ledger.
 */
export default function VendorBillsPage() {
  const [bills, setBills] = useState<VendorBill[]>([]);
  const [vendors, setVendors] = useState<Vendor[]>([]);
  const [payments, setPayments] = useState<VendorPayment[]>([]);
  const [accounts, setAccounts] = useState<BankAccountResponseDto[]>([]);
  const [expenseAccounts, setExpenseAccounts] = useState<AccountResponseDto[]>([]);
  const [statusFilter, setStatusFilter] = useState<VendorBill["status"] | "ALL">("ALL");
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const [billVendor, setBillVendor] = useState("");
  const [billDate, setBillDate] = useState(today);
  const [billDue, setBillDue] = useState("");
  const [billSubtotal, setBillSubtotal] = useState("");
  const [billTaxRate, setBillTaxRate] = useState("");
  const [billAccount, setBillAccount] = useState("");
  const [billReference, setBillReference] = useState("");

  const [payBillId, setPayBillId] = useState("");
  const [payAmount, setPayAmount] = useState("");
  const [payDate, setPayDate] = useState(today);
  const [payBank, setPayBank] = useState("");
  const [payMethod, setPayMethod] = useState("BANK_TRANSFER");
  const [payReference, setPayReference] = useState("");

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const [b, v, p, acc, exp] = await Promise.all([
        payablesApi.listBills(),
        payablesApi.listVendors({ activeOnly: false }),
        payablesApi.listPayments(),
        bankAccountsApi.list(),
        accountsApi.list("EXPENSE"),
      ]);
      setBills(b);
      setVendors(v);
      setPayments(p);
      setAccounts(acc);
      setExpenseAccounts(exp);
    } catch (e) {
      setError(e instanceof Error ? e.message : "تعذر تحميل البيانات");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const openBills = useMemo(
    () => bills.filter((b) => b.remaining > 0 && b.status !== "CANCELLED"),
    [bills],
  );

  const visible = useMemo(
    () => (statusFilter === "ALL" ? bills : bills.filter((b) => b.status === statusFilter)),
    [bills, statusFilter],
  );

  const submitBill = async (event: FormEvent) => {
    event.preventDefault();
    setSaving("bill");
    try {
      const created = await payablesApi.createBill({
        vendorId: billVendor,
        billDate,
        ...(billDue ? { dueDate: billDue } : {}),
        subtotal: Number(billSubtotal),
        ...(billTaxRate ? { taxRate: Number(billTaxRate) } : {}),
        expenseAccountId: billAccount,
        ...(billReference.trim() ? { reference: billReference.trim() } : {}),
      });
      notifySuccess(`تم تسجيل الفاتورة ${created.billNumber}`);
      setBillSubtotal("");
      setBillReference("");
      await load();
    } catch (e) {
      notifyApiError(e, "تعذر تسجيل فاتورة المورد");
    } finally {
      setSaving(null);
    }
  };

  const submitPayment = async (event: FormEvent) => {
    event.preventDefault();
    setSaving("payment");
    try {
      const created = await payablesApi.payBill({
        billId: payBillId,
        paymentDate: payDate,
        amount: Number(payAmount),
        bankAccountId: payBank,
        ...(payMethod ? { method: payMethod } : {}),
        ...(payReference.trim() ? { reference: payReference.trim() } : {}),
      });
      notifySuccess(`تم تسجيل دفعة ${formatMoney(created.amount)}`);
      setPayAmount("");
      setPayReference("");
      await load();
    } catch (e) {
      notifyApiError(e, "تعذر تسجيل دفعة المورد");
    } finally {
      setSaving(null);
    }
  };

  const payRow = async (bill: VendorBill) => {
    setPayBillId(bill.id);
    setPayAmount(String(bill.remaining));
  };

  const cancelBill = async (bill: VendorBill) => {
    if (!window.confirm(`إلغاء الفاتورة ${bill.billNumber}؟`)) return;
    setSaving(`bill-${bill.id}`);
    try {
      await payablesApi.cancelBill(bill.id);
      notifySuccess("تم إلغاء الفاتورة");
      await load();
    } catch (e) {
      notifyApiError(e, "تعذر إلغاء الفاتورة");
    } finally {
      setSaving(null);
    }
  };

  const voidPayment = async (row: VendorPayment) => {
    if (!window.confirm(`إلغاء الدفعة على الفاتورة ${row.billNumber ?? "-"}؟`)) return;
    setSaving(`payment-${row.id}`);
    try {
      await payablesApi.voidPayment(row.id);
      notifySuccess("تم إلغاء الدفعة");
      await load();
    } catch (e) {
      notifyApiError(e, "تعذر إلغاء الدفعة");
    } finally {
      setSaving(null);
    }
  };

  if (loading) return <PageLoading label="جارٍ التحميل" />;

  return (
    <div className="space-y-6">
      <ApiErrorAlert message={error ?? ""} onRetry={load} />

      <AccountingWriteOnly>
        <SectionCard title="فاتورة مورد جديدة">
          <form onSubmit={submitBill} className="grid gap-3 md:grid-cols-3 xl:grid-cols-6">
            <div>
              <Label htmlFor="bill-vendor">المورد</Label>
              <select
                id="bill-vendor"
                className={selectClassName}
                value={billVendor}
                onChange={(e) => setBillVendor(e.target.value)}
                required
              >
                <option value="">اختر المورد</option>
                {vendors.filter((v) => v.active).map((v) => (
                  <option key={v.id} value={v.id}>
                    {v.name}
                  </option>
                ))}
              </select>
            </div>
            <div>
              <Label htmlFor="bill-date">التاريخ</Label>
              <Input
                id="bill-date"
                type="date"
                value={billDate}
                onChange={(e) => setBillDate(e.target.value)}
                required
              />
            </div>
            <div>
              <Label htmlFor="bill-due">الاستحقاق</Label>
              <Input
                id="bill-due"
                type="date"
                value={billDue}
                onChange={(e) => setBillDue(e.target.value)}
              />
            </div>
            <div>
              <Label htmlFor="bill-subtotal">المبلغ قبل الضريبة</Label>
              <Input
                id="bill-subtotal"
                type="number"
                min="0.01"
                step="0.01"
                value={billSubtotal}
                onChange={(e) => setBillSubtotal(e.target.value)}
                required
              />
            </div>
            <div>
              <Label htmlFor="bill-tax">نسبة الضريبة %</Label>
              <Input
                id="bill-tax"
                type="number"
                min="0"
                max="100"
                step="0.01"
                value={billTaxRate}
                onChange={(e) => setBillTaxRate(e.target.value)}
              />
            </div>
            <div>
              <Label htmlFor="bill-account">حساب المصروف</Label>
              <select
                id="bill-account"
                className={selectClassName}
                value={billAccount}
                onChange={(e) => setBillAccount(e.target.value)}
                required
              >
                <option value="">اختر الحساب</option>
                {expenseAccounts.map((a) => (
                  <option key={a.id} value={a.id}>
                    {a.code} — {a.nameAr}
                  </option>
                ))}
              </select>
            </div>
            <div className="xl:col-span-3">
              <Label htmlFor="bill-reference">المرجع</Label>
              <Input
                id="bill-reference"
                value={billReference}
                onChange={(e) => setBillReference(e.target.value)}
              />
            </div>
            <div className="flex items-end">
              <Button type="submit" disabled={saving !== null}>
                {saving === "bill" ? "جارٍ الحفظ" : "حفظ الفاتورة"}
              </Button>
            </div>
          </form>
        </SectionCard>

        <SectionCard title="دفع فاتورة مورد">
          {openBills.length === 0 ? (
            <EmptyState title="لا توجد فواتير مستحقة" />
          ) : (
            <form onSubmit={submitPayment} className="grid gap-3 md:grid-cols-3 xl:grid-cols-6">
              <div className="xl:col-span-2">
                <Label htmlFor="pay-bill">الفاتورة</Label>
                <select
                  id="pay-bill"
                  className={selectClassName}
                  value={payBillId}
                  onChange={(e) => setPayBillId(e.target.value)}
                  required
                >
                  <option value="">اختر الفاتورة</option>
                  {openBills.map((b) => (
                    <option key={b.id} value={b.id}>
                      {b.billNumber} — {b.vendorName ?? "-"} — متبقي {formatMoney(b.remaining)}
                    </option>
                  ))}
                </select>
              </div>
              <div>
                <Label htmlFor="pay-amount">المبلغ</Label>
                <Input
                  id="pay-amount"
                  type="number"
                  min="0.01"
                  step="0.01"
                  value={payAmount}
                  onChange={(e) => setPayAmount(e.target.value)}
                  required
                />
              </div>
              <div>
                <Label htmlFor="pay-date">تاريخ الدفع</Label>
                <Input
                  id="pay-date"
                  type="date"
                  value={payDate}
                  onChange={(e) => setPayDate(e.target.value)}
                  required
                />
              </div>
              <div>
                <Label htmlFor="pay-bank">من حساب</Label>
                <select
                  id="pay-bank"
                  className={selectClassName}
                  value={payBank}
                  onChange={(e) => setPayBank(e.target.value)}
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
                <Label htmlFor="pay-method">طريقة الدفع</Label>
                <select
                  id="pay-method"
                  className={selectClassName}
                  value={payMethod}
                  onChange={(e) => setPayMethod(e.target.value)}
                >
                  <option value="BANK_TRANSFER">تحويل بنكي</option>
                  <option value="CASH">نقداً</option>
                  <option value="CHEQUE">شيك</option>
                  <option value="CARD">بطاقة</option>
                </select>
              </div>
              <div className="xl:col-span-3">
                <Label htmlFor="pay-reference">المرجع</Label>
                <Input
                  id="pay-reference"
                  value={payReference}
                  onChange={(e) => setPayReference(e.target.value)}
                />
              </div>
              <div className="flex items-end">
                <Button type="submit" disabled={saving !== null}>
                  {saving === "payment" ? "جارٍ الحفظ" : "تسجيل الدفعة"}
                </Button>
              </div>
            </form>
          )}
        </SectionCard>
      </AccountingWriteOnly>

      <div className="flex flex-wrap gap-2">
        {(["ALL", "OPEN", "PARTIALLY_PAID", "PAID", "CANCELLED"] as const).map((s) => (
          <button
            key={s}
            type="button"
            onClick={() => setStatusFilter(s)}
            className={`rounded-xl px-3 py-1.5 text-sm transition-colors ${
              statusFilter === s
                ? "bg-[var(--color-gold-500)] text-white"
                : "bg-[var(--color-surface)] text-[var(--color-muted-foreground)]"
            }`}
          >
            {s === "ALL" ? "الكل" : STATUS_LABELS[s]}
          </button>
        ))}
      </div>

      <SectionCard
        title="فواتير الموردين"
        description={`المستحق: ${formatMoney(visible.reduce((s, b) => s + b.remaining, 0))} · متأخرة: ${
          visible.filter((b) => b.overdue).length
        }`}
      >
        {visible.length === 0 ? (
          <EmptyState title="لا توجد فواتير" />
        ) : (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>رقم الفاتورة</TableHead>
                <TableHead>المورد</TableHead>
                <TableHead>التاريخ</TableHead>
                <TableHead>الاستحقاق</TableHead>
                <TableHead>الحالة</TableHead>
                <TableHead className="text-end">الإجمالي</TableHead>
                <TableHead className="text-end">المدفوع</TableHead>
                <TableHead className="text-end">المتبقي</TableHead>
                <TableHead />
              </TableRow>
            </TableHeader>
            <TableBody>
              {visible.map((b) => (
                <TableRow key={b.id} className={b.voided ? "opacity-50" : undefined}>
                  <TableCell className="ltr-nums">{b.billNumber}</TableCell>
                  <TableCell>{b.vendorName ?? "-"}</TableCell>
                  <TableCell>{formatDate(b.billDate)}</TableCell>
                  <TableCell>
                    <span className={b.overdue ? "text-destructive" : undefined}>
                      {b.dueDate ? formatDate(b.dueDate) : "-"}
                    </span>
                  </TableCell>
                  <TableCell>
                    <Badge tone={b.overdue ? "danger" : "neutral"}>{STATUS_LABELS[b.status]}</Badge>
                  </TableCell>
                  <TableCell className="text-end ltr-nums">{formatMoney(b.total)}</TableCell>
                  <TableCell className="text-end ltr-nums">{formatMoney(b.amountPaid)}</TableCell>
                  <TableCell className="text-end ltr-nums font-medium">{formatMoney(b.remaining)}</TableCell>
                  <TableCell className="text-end">
                    {b.status !== "CANCELLED" && b.status !== "PAID" && (
                      <AccountingWriteOnly>
                        <div className="flex justify-end gap-2">
                          <Button
                            type="button"
                            size="sm"
                            disabled={saving !== null}
                            onClick={() => void payRow(b)}
                          >
                            دفع
                          </Button>
                          <Button
                            type="button"
                            size="sm"
                            variant="outline"
                            disabled={saving !== null}
                            onClick={() => void cancelBill(b)}
                          >
                            {saving === `bill-${b.id}` ? "جارٍ" : "إلغاء"}
                          </Button>
                        </div>
                      </AccountingWriteOnly>
                    )}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
      </SectionCard>

      <SectionCard title="دفعات الموردين">
        {payments.length === 0 ? (
          <EmptyState title="لا توجد دفعات" />
        ) : (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>الفاتورة</TableHead>
                <TableHead>المورد</TableHead>
                <TableHead>التاريخ</TableHead>
                <TableHead>الحساب</TableHead>
                <TableHead>المرجع</TableHead>
                <TableHead className="text-end">المبلغ</TableHead>
                <TableHead />
              </TableRow>
            </TableHeader>
            <TableBody>
              {payments.map((p) => (
                <TableRow key={p.id} className={p.voided ? "opacity-50" : undefined}>
                  <TableCell className="ltr-nums">{p.billNumber ?? "-"}</TableCell>
                  <TableCell>{p.vendorName ?? "-"}</TableCell>
                  <TableCell>{formatDate(p.paymentDate)}</TableCell>
                  <TableCell>{p.bankAccountName ?? "-"}</TableCell>
                  <TableCell className="ltr-nums">{p.reference ?? "-"}</TableCell>
                  <TableCell className="text-end ltr-nums">
                    {formatMoney(p.amount)}
                    {p.voided ? " (ملغاة)" : ""}
                  </TableCell>
                  <TableCell className="text-end">
                    {!p.voided && (
                      <AccountingWriteOnly>
                        <Button
                          type="button"
                          size="sm"
                          variant="outline"
                          disabled={saving !== null}
                          onClick={() => void voidPayment(p)}
                        >
                          {saving === `payment-${p.id}` ? "جارٍ" : "إلغاء"}
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

      <SectionCard title="الموردون">
        {vendors.length === 0 ? (
          <EmptyState title="لا يوجد موردون" />
        ) : (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>الاسم</TableHead>
                <TableHead>البريد</TableHead>
                <TableHead>الهاتف</TableHead>
                <TableHead>الرقم الضريبي</TableHead>
                <TableHead>المستحق</TableHead>
                <TableHead>الحالة</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {vendors.map((v) => (
                <TableRow key={v.id}>
                  <TableCell>{v.name}</TableCell>
                  <TableCell className="ltr-nums">{v.email ?? "-"}</TableCell>
                  <TableCell className="ltr-nums">{v.phone ?? "-"}</TableCell>
                  <TableCell className="ltr-nums">{v.taxNumber ?? "-"}</TableCell>
                  <TableCell className="ltr-nums">{formatMoney(v.outstandingPayable)}</TableCell>
                  <TableCell>
                    <Badge tone={v.active ? "neutral" : "warning"}>
                      {v.active ? "نشط" : "متوقف"}
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