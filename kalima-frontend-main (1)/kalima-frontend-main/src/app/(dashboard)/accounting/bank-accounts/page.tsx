"use client";

import { useEffect, useState, type FormEvent } from "react";
import Link from "next/link";
import { ArrowLeftRight, Landmark, Pencil, Plus, Save } from "lucide-react";

import { AccountingDialog } from "@/components/accounting/AccountingDialog";
import { AccountingRoleGate, AccountingWriteOnly } from "@/components/accounting/AccountingRoleGate";
import { DisputeModuleHero } from "@/components/disputes/DisputeModuleHero";
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
import { ApiRequestError } from "@/lib/api/client";
import { CURRENCY_OPTIONS } from "@/lib/accounting/labels";
import { notifyApiError, notifySuccess } from "@/lib/feedback";
import { formatDate } from "@/lib/utils/format-date";
import { formatMoney } from "@/lib/utils/format-money";
import { cn } from "@/lib/utils/cn";
import type { AccountResponseDto, BankAccountResponseDto, BankTransferResponseDto } from "@/types/accounting";

const selectClassName = cn(
  "flex h-10 w-full cursor-pointer appearance-none rounded-xl border border-[var(--color-border)] bg-[var(--color-surface)] px-3.5 py-2 text-sm text-[var(--color-foreground)] shadow-sm transition-colors",
  "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--color-gold-400)]",
  "disabled:cursor-not-allowed disabled:opacity-50",
);

const inputLtrClassName = "ltr-nums text-start";

type FormState = {
  nameAr: string;
  bankName: string;
  iban: string;
  accountNumber: string;
  currency: string;
  accountId: string;
  openingBalance: string;
};

const EMPTY: FormState = {
  nameAr: "",
  bankName: "",
  iban: "",
  accountNumber: "",
  currency: "SAR",
  accountId: "",
  openingBalance: "0",
};

export default function BankAccountsPage() {
  return (
    <AccountingRoleGate>
      <BankAccountsPageInner />
    </AccountingRoleGate>
  );
}

function BankAccountsPageInner() {
  const [rows, setRows] = useState<BankAccountResponseDto[]>([]);
  const [assetAccounts, setAssetAccounts] = useState<AccountResponseDto[]>([]);
  const [transfers, setTransfers] = useState<BankTransferResponseDto[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [fetchId, setFetchId] = useState(0);

  const [dialogOpen, setDialogOpen] = useState(false);
  const [editing, setEditing] = useState<BankAccountResponseDto | null>(null);
  const [form, setForm] = useState<FormState>(EMPTY);
  const [submitting, setSubmitting] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);
  const [toggling, setToggling] = useState<string | null>(null);
  const [transferFrom, setTransferFrom] = useState("");
  const [transferTo, setTransferTo] = useState("");
  const [transferAmount, setTransferAmount] = useState("");
  const [transferDate, setTransferDate] = useState(() => new Date().toISOString().slice(0, 10));
  const [transferReference, setTransferReference] = useState("");
  const [transferNotes, setTransferNotes] = useState("");
  const [transferError, setTransferError] = useState<string | null>(null);
  const [transferBusy, setTransferBusy] = useState(false);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      setLoading(true);
      setError(null);
      try {
        const [banks, accs, transferRows] = await Promise.all([
          bankAccountsApi.list(),
          accountsApi.list("ASSET"),
          bankAccountsApi.transfers(),
        ]);
        if (cancelled) return;
        setRows(banks);
        setTransfers(transferRows);
        setTransferFrom((current) => current || banks.find((b) => b.active)?.id || "");
        setTransferTo((current) => current || banks.filter((b) => b.active)[1]?.id || "");
        setAssetAccounts(accs.filter((a) => a.active));
      } catch (e) {
        if (!cancelled) {
          setError(e instanceof ApiRequestError ? e.message : "تعذّر تحميل الحسابات البنكية");
          setRows([]);
        }
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [fetchId]);

  async function handleTransfer() {
    setTransferError(null);
    if (!transferFrom || !transferTo || transferFrom === transferTo) {
      setTransferError("اختر حسابين مختلفين للتحويل");
      return;
    }
    if (!Number.isFinite(Number(transferAmount)) || Number(transferAmount) <= 0) {
      setTransferError("أدخل مبلغًا أكبر من صفر");
      return;
    }
    setTransferBusy(true);
    try {
      const result = await bankAccountsApi.transfer({
        sourceBankAccountId: transferFrom,
        destinationBankAccountId: transferTo,
        amount: Number(transferAmount),
        transferDate,
        reference: transferReference.trim() || null,
        notes: transferNotes.trim() || null,
      });
      setTransfers((current) => [result, ...current]);
      setTransferAmount("");
      setTransferReference("");
      setTransferNotes("");
      setFetchId((current) => current + 1);
      notifySuccess("تم التحويل بين الحسابين", result.journalEntryNumber ?? undefined);
    } catch (e) {
      setTransferError(e instanceof ApiRequestError ? e.message : "تعذر تنفيذ التحويل");
      notifyApiError(e, "تعذر تنفيذ التحويل");
    } finally {
      setTransferBusy(false);
    }
  }

  function openCreate() {
    setEditing(null);
    setForm(EMPTY);
    setFormError(null);
    setDialogOpen(true);
  }

  function openEdit(b: BankAccountResponseDto) {
    setEditing(b);
    setForm({
      nameAr: b.nameAr,
      bankName: b.bankName ?? "",
      iban: b.iban ?? "",
      accountNumber: b.accountNumber ?? "",
      currency: b.currency,
      accountId: b.accountId,
      openingBalance: String(b.openingBalance ?? 0),
    });
    setFormError(null);
    setDialogOpen(true);
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setFormError(null);

    if (!form.nameAr.trim()) {
      setFormError("اسم الحساب مطلوب");
      return;
    }
    if (!editing && !form.accountId) {
      setFormError("اختر حساب الأصول المقابل");
      return;
    }
    if (Number(form.openingBalance) < 0) {
      setFormError("الرصيد الافتتاحي لا يمكن أن يكون سالباً");
      return;
    }

    setSubmitting(true);
    try {
      const body = {
        nameAr: form.nameAr.trim(),
        accountId: form.accountId || null,
        bankName: form.bankName.trim() || null,
        iban: form.iban.trim() || null,
        accountNumber: form.accountNumber.trim() || null,
        currency: form.currency,
        openingBalance: Number(form.openingBalance) || 0,
      };
      if (editing) {
        await bankAccountsApi.update(editing.id, body);
        notifySuccess("تم تحديث الحساب البنكي");
      } else {
        await bankAccountsApi.create(body);
        notifySuccess("تم إضافة الحساب البنكي");
      }
      setDialogOpen(false);
      setFetchId((n) => n + 1);
    } catch (err) {
      if (err instanceof ApiRequestError) setFormError(err.message);
      else setFormError("تعذّر حفظ الحساب البنكي");
      notifyApiError(err, "تعذّر حفظ الحساب البنكي");
    } finally {
      setSubmitting(false);
    }
  }

  async function handleToggle(b: BankAccountResponseDto) {
    setToggling(b.id);
    try {
      await bankAccountsApi.setActive(b.id, !b.active);
      notifySuccess(b.active ? "تم إيقاف الحساب" : "تم تفعيل الحساب");
      setFetchId((n) => n + 1);
    } catch (err) {
      notifyApiError(err, "تعذّر تغيير حالة الحساب");
    } finally {
      setToggling(null);
    }
  }

  return (
    <div dir="rtl" className="space-y-6 pb-10">
      <DisputeModuleHero
        icon={Landmark}
        eyebrow="المالية والمحاسبة"
        title="الحسابات البنكية والخزائن"
        description="كل حساب نقدي مرتبط بحساب أصول في دليل الحسابات. حركة الفترة محسوبة من القيود المرحلة فقط."
        badges={
          !loading && !error ? (
            <span className="rounded-lg bg-white/10 px-2.5 py-1 text-xs font-medium text-[var(--color-gold-200)] ring-1 ring-white/15">
              {rows.length.toLocaleString("ar-EG")} حساب
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
              حساب جديد
            </Button>
          </AccountingWriteOnly>
        }
      />

      {error ? (
        <ApiErrorAlert message={error} onRetry={() => setFetchId((n) => n + 1)} />
      ) : null}

      <AccountingWriteOnly>
        <SectionCard title="تحويل بين الحسابات" description="يُرحّل التحويل كمدين للحساب المستلم ودائن للحساب المصدر، دون إيراد أو مصروف.">
          <div className="grid gap-3 md:grid-cols-3">
            <label className="space-y-1.5 text-sm">من
              <select value={transferFrom} onChange={(e) => setTransferFrom(e.target.value)} className={selectClassName}>
                <option value="">اختر الحساب المصدر</option>{rows.filter((b) => b.active).map((b) => <option key={b.id} value={b.id}>{b.nameAr} · {b.currency}</option>)}
              </select>
            </label>
            <label className="space-y-1.5 text-sm">إلى
              <select value={transferTo} onChange={(e) => setTransferTo(e.target.value)} className={selectClassName}>
                <option value="">اختر الحساب المستلم</option>{rows.filter((b) => b.active && b.id !== transferFrom).map((b) => <option key={b.id} value={b.id}>{b.nameAr} · {b.currency}</option>)}
              </select>
            </label>
            <label className="space-y-1.5 text-sm">المبلغ
              <Input type="number" min="0.01" step="0.01" value={transferAmount} onChange={(e) => setTransferAmount(e.target.value)} className={inputLtrClassName} />
            </label>
            <label className="space-y-1.5 text-sm">التاريخ
              <Input type="date" value={transferDate} onChange={(e) => setTransferDate(e.target.value)} className={inputLtrClassName} />
            </label>
            <label className="space-y-1.5 text-sm">المرجع
              <Input value={transferReference} onChange={(e) => setTransferReference(e.target.value)} maxLength={100} />
            </label>
            <label className="space-y-1.5 text-sm">ملاحظات
              <Input value={transferNotes} onChange={(e) => setTransferNotes(e.target.value)} maxLength={1000} />
            </label>
            <div className="flex items-end"><Button onClick={() => void handleTransfer()} disabled={transferBusy} className="gap-2"><ArrowLeftRight className="size-4" />تنفيذ التحويل</Button></div>
          </div>
          {transferError ? <p role="alert" className="mt-3 text-sm text-[var(--color-danger)]">{transferError}</p> : null}
        </SectionCard>
      </AccountingWriteOnly>

      {transfers.length > 0 ? <SectionCard title="آخر التحويلات" flush>
        <ul className="divide-y divide-[var(--color-border)]">
          {transfers.slice(0, 10).map((transfer) => <li key={transfer.id} className="flex flex-wrap items-center justify-between gap-3 px-4 py-3 text-sm">
            <span>{transfer.sourceBankAccountName} ← {transfer.destinationBankAccountName}<span className="ms-2 text-xs text-[var(--color-muted-foreground)]">{formatDate(transfer.transferDate)}</span></span>
            <span className="font-semibold ltr-nums">{formatMoney(transfer.amount, transfer.currency)}{transfer.journalEntryNumber ? <span className="ms-2 text-xs text-[var(--color-muted-foreground)]">{transfer.journalEntryNumber}</span> : null}</span>
          </li>)}
        </ul>
      </SectionCard> : null}

      {loading ? (
        <PageLoading label="جاري تحميل الحسابات البنكية..." />
      ) : rows.length === 0 ? (
        <SectionCard>
          <EmptyState
            icon={Landmark}
            title="لا توجد حسابات بنكية"
            description="أضف حساباً نقدياً أو بنكياً لربط المصروفات والمقبوضات به."
            action={<AccountingWriteOnly><Button onClick={openCreate}><Plus className="size-4" />حساب جديد</Button></AccountingWriteOnly>}
          />
        </SectionCard>
      ) : (
        <SectionCard flush title="الحسابات">
          <div className="overflow-x-auto">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead className="text-right">الاسم</TableHead>
                  <TableHead className="text-right">البنك</TableHead>
                  <TableHead className="text-right">الحساب المحاسبي</TableHead>
                  <TableHead className="text-right">العملة</TableHead>
                  <TableHead className="text-right">الرصيد الافتتاحي</TableHead>
                  <TableHead className="text-right">حركة الفترة</TableHead>
                  <TableHead className="text-right">الرصيد الحالي</TableHead>
                  <TableHead className="text-right">الحالة</TableHead>
                  <TableHead className="text-right">الكشف</TableHead>
                  <TableHead className="text-right">إجراءات</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {rows.map((b) => (
                  <TableRow key={b.id} className="transition-colors hover:bg-[var(--color-navy-50)]/40">
                    <TableCell className="font-medium">{b.nameAr}</TableCell>
                    <TableCell className="text-[var(--color-muted-foreground)]">{b.bankName ?? "—"}</TableCell>
                    <TableCell>
                      <span className="block font-semibold ltr-nums">{b.accountCode}</span>
                      {b.accountNumber ? (
                        <span className="block text-xs ltr-nums text-[var(--color-muted-foreground)]">
                          {b.accountNumber}
                        </span>
                      ) : null}
                    </TableCell>
                    <TableCell className="ltr-nums">{b.currency}</TableCell>
                    <TableCell className="whitespace-nowrap ltr-nums">{formatMoney(b.openingBalance)}</TableCell>
                    <TableCell
                      className={cn(
                        "whitespace-nowrap ltr-nums font-medium",
                        b.movement < 0 ? "text-[var(--color-danger)]" : "text-[var(--color-success)]",
                      )}
                    >
                      {formatMoney(b.movement)}
                    </TableCell>
                    <TableCell className="whitespace-nowrap font-semibold ltr-nums">
                      {formatMoney(b.balance, b.currency)}
                    </TableCell>
                    <TableCell>
                      <Badge tone={b.active ? "success" : "neutral"}>
                        {b.active ? "نشط" : "موقوف"}
                      </Badge>
                    </TableCell>
                    <TableCell><Button asChild variant="outline" size="sm"><Link href={`/accounting/accounts/${b.accountId}/ledger`}>كشف الحركة</Link></Button></TableCell>
                    <TableCell>
                      <AccountingWriteOnly>
                        <div className="flex items-center gap-1.5">
                          <Button
                            variant="ghost"
                            size="sm"
                            onClick={() => openEdit(b)}
                            aria-label={`تعديل ${b.nameAr}`}
                          >
                            <Pencil className="size-3.5" />
                          </Button>
                          <Button
                            variant="ghost"
                            size="sm"
                            disabled={toggling === b.id}
                            onClick={() => handleToggle(b)}
                            className="text-xs"
                          >
                            {b.active ? "إيقاف" : "تفعيل"}
                          </Button>
                        </div>
                      </AccountingWriteOnly>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </div>
        </SectionCard>
      )}

      <AccountingDialog
        open={dialogOpen}
        onClose={() => setDialogOpen(false)}
        title={editing ? "تعديل حساب بنكي" : "حساب بنكي جديد"}
        description="يُربط كل حساب نقدي بحساب أصول في دليل الحسابات لتتبّع الأرصدة."
        icon={<Landmark className="size-5" />}
        size="lg"
      >
        <form id="bank-form" onSubmit={handleSubmit} className="space-y-4" noValidate>
          {formError ? <ApiErrorAlert message={formError} onRetry={() => setFormError(null)} /> : null}

          <div className="grid gap-4 md:grid-cols-2">
            <div className="space-y-1.5">
              <Label htmlFor="ba-name">اسم الحساب *</Label>
              <Input id="ba-name" value={form.nameAr} onChange={(e) => setForm({ ...form, nameAr: e.target.value })} disabled={submitting} placeholder="مثال: الصندوق النقدي" />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="ba-bank">اسم البنك</Label>
              <Input id="ba-bank" value={form.bankName} onChange={(e) => setForm({ ...form, bankName: e.target.value })} disabled={submitting} placeholder="مثال: البنك الأهلي" />
            </div>

            <div className="space-y-1.5">
              <Label htmlFor="ba-account-id">حساب الأصول المقابل *</Label>
              <select
                id="ba-account-id"
                className={selectClassName}
                value={form.accountId}
                onChange={(e) => setForm({ ...form, accountId: e.target.value })}
                disabled={submitting}
              >
                <option value="">اختر الحساب</option>
                {assetAccounts.map((a) => (
                  <option key={a.id} value={a.id}>{a.code} — {a.nameAr}</option>
                ))}
              </select>
              <p className="text-xs text-[var(--color-muted-foreground)]">
                يجب أن يكون الحساب من نوع أصول، وغير مرتبط بحساب بنكي آخر.
              </p>
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="ba-currency">العملة</Label>
              <select id="ba-currency" className={selectClassName} value={form.currency} onChange={(e) => setForm({ ...form, currency: e.target.value })} disabled={submitting}>
                {CURRENCY_OPTIONS.map((c) => (
                  <option key={c.value} value={c.value}>{c.label}</option>
                ))}
              </select>
            </div>

            <div className="space-y-1.5">
              <Label htmlFor="ba-number">رقم الحساب</Label>
              <Input id="ba-number" value={form.accountNumber} onChange={(e) => setForm({ ...form, accountNumber: e.target.value })} disabled={submitting} className={inputLtrClassName} dir="ltr" maxLength={50} />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="ba-iban">الآيبان</Label>
              <Input id="ba-iban" value={form.iban} onChange={(e) => setForm({ ...form, iban: e.target.value })} disabled={submitting} className={inputLtrClassName} dir="ltr" maxLength={50} placeholder="SA00 0000 0000 0000 0000 0000" />
            </div>

            <div className="space-y-1.5 md:col-span-2">
              <Label htmlFor="ba-opening">الرصيد الافتتاحي</Label>
              <Input id="ba-opening" type="number" inputMode="decimal" min="0" step="0.01" value={form.openingBalance} onChange={(e) => setForm({ ...form, openingBalance: e.target.value })} disabled={submitting} className={inputLtrClassName} dir="ltr" />
            </div>
          </div>
        </form>

        <div className="mt-4 flex justify-end gap-2 border-t border-[var(--color-border)] pt-4">
          <Button type="submit" form="bank-form" variant="gold" disabled={submitting}>
            {submitting ? "جارٍ الحفظ…" : (<><Save className="size-4" />{editing ? "حفظ التعديلات" : "إضافة الحساب"}</>)}
          </Button>
          <Button type="button" variant="ghost" onClick={() => setDialogOpen(false)} disabled={submitting}>
            إلغاء
          </Button>
        </div>
      </AccountingDialog>
    </div>
  );
}
