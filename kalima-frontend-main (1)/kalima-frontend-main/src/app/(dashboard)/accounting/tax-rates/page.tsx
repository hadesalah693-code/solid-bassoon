"use client";

import { useEffect, useState, type FormEvent } from "react";
import { Percent, Pencil, Plus, Save, Trash2 } from "lucide-react";

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

import { accountsApi, taxRatesApi } from "@/lib/api/accounting";
import { ApiRequestError } from "@/lib/api/client";
import { notifyApiError, notifySuccess } from "@/lib/feedback";
import { cn } from "@/lib/utils/cn";
import type { AccountResponseDto, TaxRateResponseDto } from "@/types/accounting";

const selectClassName = cn(
  "flex h-10 w-full cursor-pointer appearance-none rounded-xl border border-[var(--color-border)] bg-[var(--color-surface)] px-3.5 py-2 text-sm text-[var(--color-foreground)] shadow-sm transition-colors",
  "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--color-gold-400)]",
  "disabled:cursor-not-allowed disabled:opacity-50",
);

const inputLtrClassName = "ltr-nums text-start";

type FormState = {
  nameAr: string;
  rate: string;
  accountId: string;
  active: boolean;
  defaultRate: boolean;
};

const EMPTY: FormState = {
  nameAr: "",
  rate: "15",
  accountId: "",
  active: true,
  defaultRate: false,
};

export default function TaxRatesPage() {
  return (
    <AccountingRoleGate>
      <TaxRatesPageInner />
    </AccountingRoleGate>
  );
}

function TaxRatesPageInner() {
  const [rows, setRows] = useState<TaxRateResponseDto[]>([]);
  const [vatAccounts, setVatAccounts] = useState<AccountResponseDto[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [fetchId, setFetchId] = useState(0);

  const [dialogOpen, setDialogOpen] = useState(false);
  const [editing, setEditing] = useState<TaxRateResponseDto | null>(null);
  const [form, setForm] = useState<FormState>(EMPTY);
  const [submitting, setSubmitting] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);
  const [removing, setRemoving] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      setLoading(true);
      setError(null);
      try {
        const [rates, all] = await Promise.all([taxRatesApi.list(), accountsApi.list()]);
        if (cancelled) return;
        setRows(rates);
        setVatAccounts(all.filter((a) => a.type === "LIABILITY" && a.active));
      } catch (e) {
        if (!cancelled) {
          setError(e instanceof ApiRequestError ? e.message : "تعذّر تحميل نسب الضريبة");
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

  function openCreate() {
    setEditing(null);
    setForm(EMPTY);
    setFormError(null);
    setDialogOpen(true);
  }

  function openEdit(r: TaxRateResponseDto) {
    setEditing(r);
    setForm({
      nameAr: r.nameAr,
      rate: String(r.rate),
      accountId: r.accountId ?? "",
      active: r.active,
      defaultRate: r.defaultRate,
    });
    setFormError(null);
    setDialogOpen(true);
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setFormError(null);

    if (!form.nameAr.trim()) { setFormError("اسم النسبة مطلوب"); return; }
    const rate = Number(form.rate);
    if (!Number.isFinite(rate) || rate < 0 || rate > 100) {
      setFormError("النسبة يجب أن تكون بين 0 و 100");
      return;
    }

    setSubmitting(true);
    try {
      const body = {
        nameAr: form.nameAr.trim(),
        rate,
        accountId: form.accountId || null,
        active: form.active,
        defaultRate: form.defaultRate,
      };
      if (editing) {
        await taxRatesApi.update(editing.id, body);
        notifySuccess("تم تحديث نسبة الضريبة");
      } else {
        await taxRatesApi.create(body);
        notifySuccess("تم إضافة نسبة الضريبة");
      }
      setDialogOpen(false);
      setFetchId((n) => n + 1);
    } catch (err) {
      if (err instanceof ApiRequestError) setFormError(err.message);
      else setFormError("تعذّر حفظ نسبة الضريبة");
      notifyApiError(err, "تعذّر حفظ نسبة الضريبة");
    } finally {
      setSubmitting(false);
    }
  }

  async function handleRemove(r: TaxRateResponseDto) {
    if (!window.confirm(`حذف نسبة «${r.nameAr}»؟`)) return;
    setRemoving(r.id);
    try {
      await taxRatesApi.remove(r.id);
      notifySuccess("تم حذف نسبة الضريبة");
      setFetchId((n) => n + 1);
    } catch (err) {
      notifyApiError(err, "تعذّر حذف نسبة الضريبة");
    } finally {
      setRemoving(null);
    }
  }

  return (
    <div dir="rtl" className="space-y-6 pb-10">
      <DisputeModuleHero
        icon={Percent}
        eyebrow="المالية والمحاسبة"
        title="نسب ضريبة القيمة المضافة"
        description="النسب المتاحة عند تسجيل المصروفات، مع ربط كل نسبة بحساب ضريبة المدخلات."
        badges={
          !loading && !error ? (
            <span className="rounded-lg bg-white/10 px-2.5 py-1 text-xs font-medium text-[var(--color-gold-200)] ring-1 ring-white/15">
              {rows.length.toLocaleString("ar-EG")} نسبة
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
              نسبة جديدة
            </Button>
          </AccountingWriteOnly>
        }
      />

      {error ? (
        <ApiErrorAlert message={error} onRetry={() => setFetchId((n) => n + 1)} />
      ) : null}

      {loading ? (
        <PageLoading label="جاري تحميل نسب الضريبة..." />
      ) : rows.length === 0 ? (
        <SectionCard>
          <EmptyState
            icon={Percent}
            title="لا توجد نسب ضريبة"
            description="أضف نسب ضريبة القيمة المضافة لاستخدامها في المصروفات والفواتير."
            action={<AccountingWriteOnly><Button onClick={openCreate}><Plus className="size-4" />نسبة جديدة</Button></AccountingWriteOnly>}
          />
        </SectionCard>
      ) : (
        <SectionCard flush title="النسب">
          <div className="overflow-x-auto">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead className="text-right">الاسم</TableHead>
                  <TableHead className="text-right">النسبة</TableHead>
                  <TableHead className="text-right">حساب الضريبة</TableHead>
                  <TableHead className="text-right">افتراضية</TableHead>
                  <TableHead className="text-right">الحالة</TableHead>
                  <TableHead className="text-right">إجراءات</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {rows.map((r) => {
                  const acc = vatAccounts.find((a) => a.id === r.accountId);
                  return (
                    <TableRow key={r.id} className="transition-colors hover:bg-[var(--color-navy-50)]/40">
                      <TableCell className="font-medium">{r.nameAr}</TableCell>
                      <TableCell className="whitespace-nowrap font-semibold ltr-nums">{r.rate}%</TableCell>
                      <TableCell className="ltr-nums text-[var(--color-muted-foreground)]">
                        {acc ? `${acc.code} — ${acc.nameAr}` : "—"}
                      </TableCell>
                      <TableCell>
                        {r.defaultRate ? <Badge tone="gold">افتراضية</Badge> : <span className="text-xs text-[var(--color-muted-foreground)]">—</span>}
                      </TableCell>
                      <TableCell>
                        <Badge tone={r.active ? "success" : "neutral"}>{r.active ? "نشطة" : "موقوفة"}</Badge>
                      </TableCell>
                      <TableCell>
                        <AccountingWriteOnly>
                          <div className="flex items-center gap-1.5">
                            <Button variant="ghost" size="sm" onClick={() => openEdit(r)} aria-label={`تعديل ${r.nameAr}`}>
                              <Pencil className="size-3.5" />
                            </Button>
                            <Button
                              variant="ghost"
                              size="sm"
                              disabled={removing === r.id}
                              onClick={() => handleRemove(r)}
                              aria-label={`حذف ${r.nameAr}`}
                              className="text-[var(--color-danger)]"
                            >
                              <Trash2 className="size-3.5" />
                            </Button>
                          </div>
                        </AccountingWriteOnly>
                      </TableCell>
                    </TableRow>
                  );
                })}
              </TableBody>
            </Table>
          </div>
        </SectionCard>
      )}

      <AccountingDialog
        open={dialogOpen}
        onClose={() => setDialogOpen(false)}
        title={editing ? "تعديل نسبة ضريبة" : "نسبة ضريبة جديدة"}
        description="تُطبَّق النسبة على المبلغ الخاضع للضريبة في المصروفات وتُرحَّل إلى حساب الضريبة."
        icon={<Percent className="size-5" />}
      >
        <form id="tax-form" onSubmit={handleSubmit} className="space-y-4" noValidate>
          {formError ? <ApiErrorAlert message={formError} onRetry={() => setFormError(null)} /> : null}

          <div className="space-y-1.5">
            <Label htmlFor="tx-name">اسم النسبة *</Label>
            <Input id="tx-name" value={form.nameAr} onChange={(e) => setForm({ ...form, nameAr: e.target.value })} disabled={submitting} placeholder="مثال: ضريبة القيمة المضافة 15%" />
          </div>

          <div className="grid gap-4 sm:grid-cols-2">
            <div className="space-y-1.5">
              <Label htmlFor="tx-rate">النسبة % *</Label>
              <Input id="tx-rate" type="number" inputMode="decimal" min="0" max="100" step="0.01" value={form.rate} onChange={(e) => setForm({ ...form, rate: e.target.value })} disabled={submitting} className={inputLtrClassName} dir="ltr" />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="tx-account">حساب ضريبة المدخلات</Label>
              <select id="tx-account" className={selectClassName} value={form.accountId} onChange={(e) => setForm({ ...form, accountId: e.target.value })} disabled={submitting}>
                <option value="">— بدون —</option>
                {vatAccounts.map((a) => (
                  <option key={a.id} value={a.id}>{a.code} — {a.nameAr}</option>
                ))}
              </select>
            </div>
          </div>

          <div className="flex flex-wrap gap-4">
            <label className="flex cursor-pointer items-center gap-2 text-sm">
              <input
                type="checkbox"
                checked={form.active}
                onChange={(e) => setForm({ ...form, active: e.target.checked })}
                disabled={submitting}
                className="size-4 accent-[var(--color-gold-400)]"
              />
              نشطة
            </label>
            <label className="flex cursor-pointer items-center gap-2 text-sm">
              <input
                type="checkbox"
                checked={form.defaultRate}
                onChange={(e) => setForm({ ...form, defaultRate: e.target.checked })}
                disabled={submitting}
                className="size-4 accent-[var(--color-gold-400)]"
              />
              النسبة الافتراضية
            </label>
          </div>
        </form>

        <div className="mt-4 flex justify-end gap-2 border-t border-[var(--color-border)] pt-4">
          <Button type="submit" form="tax-form" variant="gold" disabled={submitting}>
            {submitting ? "جارٍ الحفظ…" : (<><Save className="size-4" />{editing ? "حفظ التعديلات" : "إضافة النسبة"}</>)}
          </Button>
          <Button type="button" variant="ghost" onClick={() => setDialogOpen(false)} disabled={submitting}>
            إلغاء
          </Button>
        </div>
      </AccountingDialog>
    </div>
  );
}
