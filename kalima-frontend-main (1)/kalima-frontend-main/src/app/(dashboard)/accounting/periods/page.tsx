"use client";

import { useEffect, useState, type FormEvent } from "react";
import { CalendarRange, Lock, LockOpen, Plus, Save } from "lucide-react";

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

import { periodsApi } from "@/lib/api/accounting";
import { ApiRequestError } from "@/lib/api/client";
import { PERIOD_STATUS_AR, PERIOD_STATUS_TONE } from "@/lib/accounting/labels";
import { notifyApiError, notifySuccess } from "@/lib/feedback";
import { formatDate } from "@/lib/utils/format-date";
import { cn } from "@/lib/utils/cn";
import type { FinancialPeriodResponseDto } from "@/types/accounting";

const inputLtrClassName = "ltr-nums text-start";

function currentYear() {
  return new Date().getFullYear();
}

export default function PeriodsPage() {
  return (
    <AccountingRoleGate>
      <PeriodsPageInner />
    </AccountingRoleGate>
  );
}

function PeriodsPageInner() {
  const [rows, setRows] = useState<FinancialPeriodResponseDto[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [fetchId, setFetchId] = useState(0);
  const [busyId, setBusyId] = useState<string | null>(null);

  const [dialogOpen, setDialogOpen] = useState(false);
  const [name, setName] = useState("");
  const [startDate, setStartDate] = useState(`${currentYear()}-01-01`);
  const [endDate, setEndDate] = useState(`${currentYear()}-12-31`);
  const [submitting, setSubmitting] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      setLoading(true);
      setError(null);
      try {
        const res = await periodsApi.list();
        if (!cancelled) setRows(res);
      } catch (e) {
        if (!cancelled) {
          setError(e instanceof ApiRequestError ? e.message : "تعذّر تحميل الفترات المالية");
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
    const year = currentYear();
    setName(`${year} — السنة المالية`);
    setStartDate(`${year}-01-01`);
    setEndDate(`${year}-12-31`);
    setFormError(null);
    setDialogOpen(true);
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setFormError(null);

    if (!name.trim()) { setFormError("اسم الفترة مطلوب"); return; }
    if (!startDate || !endDate) { setFormError("حدد تاريخ البداية والنهاية"); return; }
    if (startDate > endDate) { setFormError("تاريخ البداية يجب أن يكون قبل تاريخ النهاية"); return; }

    setSubmitting(true);
    try {
      await periodsApi.create({ name: name.trim(), startDate, endDate });
      notifySuccess("تم إنشاء الفترة المالية");
      setDialogOpen(false);
      setFetchId((n) => n + 1);
    } catch (err) {
      if (err instanceof ApiRequestError) setFormError(err.message);
      else setFormError("تعذّر إنشاء الفترة");
      notifyApiError(err, "تعذّر إنشاء الفترة المالية");
    } finally {
      setSubmitting(false);
    }
  }

  async function handleClose(p: FinancialPeriodResponseDto) {
    if (!window.confirm(`إغلاق الفترة «${p.name}»؟ لن يُسمح بأي ترحيل جديد داخلها.`)) return;
    setBusyId(p.id);
    try {
      await periodsApi.close(p.id);
      notifySuccess("تم إغلاق الفترة");
      setFetchId((n) => n + 1);
    } catch (err) {
      notifyApiError(err, "تعذّر إغلاق الفترة");
    } finally {
      setBusyId(null);
    }
  }

  async function handleReopen(p: FinancialPeriodResponseDto) {
    if (!window.confirm(`إعادة فتح الفترة «${p.name}»؟`)) return;
    setBusyId(p.id);
    try {
      await periodsApi.reopen(p.id);
      notifySuccess("تم إعادة فتح الفترة");
      setFetchId((n) => n + 1);
    } catch (err) {
      notifyApiError(err, "تعذّر إعادة فتح الفترة");
    } finally {
      setBusyId(null);
    }
  }

  return (
    <div dir="rtl" className="space-y-6 pb-10">
      <DisputeModuleHero
        icon={CalendarRange}
        eyebrow="المالية والمحاسبة"
        title="الفترات المالية"
        description="إغلاق الفترة يمنع أي قيد جديد داخلها ويحمي دفتر اليومية من التعديل بعد المراجعة."
        badges={
          !loading && !error ? (
            <span className="rounded-lg bg-white/10 px-2.5 py-1 text-xs font-medium text-[var(--color-gold-200)] ring-1 ring-white/15">
              {rows.length.toLocaleString("ar-EG")} فترة
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
              فترة جديدة
            </Button>
          </AccountingWriteOnly>
        }
      />

      {error ? (
        <ApiErrorAlert message={error} onRetry={() => setFetchId((n) => n + 1)} />
      ) : null}

      {loading ? (
        <PageLoading label="جاري تحميل الفترات المالية..." />
      ) : rows.length === 0 ? (
        <SectionCard>
          <EmptyState
            icon={CalendarRange}
            title="لا توجد فترات مالية"
            description="أنشئ فترة مالية لتحديد نطاق الترحيل المحاسبي."
            action={<AccountingWriteOnly><Button onClick={openCreate}><Plus className="size-4" />فترة جديدة</Button></AccountingWriteOnly>}
          />
        </SectionCard>
      ) : (
        <SectionCard flush title="الفترات">
          <div className="overflow-x-auto">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead className="text-right">الفترة</TableHead>
                  <TableHead className="text-right">من</TableHead>
                  <TableHead className="text-right">إلى</TableHead>
                  <TableHead className="text-right">عدد القيود</TableHead>
                  <TableHead className="text-right">الحالة</TableHead>
                  <TableHead className="text-right">أُغلق بواسطة</TableHead>
                  <TableHead className="text-right">إجراءات</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {rows.map((p) => (
                  <TableRow key={p.id} className="transition-colors hover:bg-[var(--color-navy-50)]/40">
                    <TableCell className="font-medium">{p.name}</TableCell>
                    <TableCell className="whitespace-nowrap text-[var(--color-muted-foreground)]">
                      {formatDate(p.startDate)}
                    </TableCell>
                    <TableCell className="whitespace-nowrap text-[var(--color-muted-foreground)]">
                      {formatDate(p.endDate)}
                    </TableCell>
                    <TableCell className="ltr-nums">{p.entryCount}</TableCell>
                    <TableCell>
                      <Badge tone={PERIOD_STATUS_TONE[p.status]}>
                        {PERIOD_STATUS_AR[p.status]}
                      </Badge>
                    </TableCell>
                    <TableCell className="text-xs text-[var(--color-muted-foreground)]">
                      {p.closedBy ? (
                        <>
                          <span className="block">{p.closedBy}</span>
                          {p.closedAt ? <span className="block">{formatDate(p.closedAt)}</span> : null}
                        </>
                      ) : (
                        "—"
                      )}
                    </TableCell>
                    <TableCell>
                      <AccountingWriteOnly>
                        {p.status === "OPEN" ? (
                          <Button
                            variant="outline"
                            size="sm"
                            disabled={busyId === p.id}
                            onClick={() => handleClose(p)}
                          >
                            <Lock className="size-3.5" />
                            إغلاق
                          </Button>
                        ) : p.status === "CLOSED" ? (
                          <Button
                            variant="ghost"
                            size="sm"
                            disabled={busyId === p.id}
                            onClick={() => handleReopen(p)}
                            className={cn("text-[var(--color-gold-600)]")}
                          >
                            <LockOpen className="size-3.5" />
                            إعادة فتح
                          </Button>
                        ) : (
                          <span className="text-xs text-[var(--color-muted-foreground)]">مقفلة نهائياً</span>
                        )}
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
        title="فترة مالية جديدة"
        description="لا يمكن تداخل فترتين، وكل قيد يقع داخل نطاق الفترة المغلقة يُرفض."
        icon={<CalendarRange className="size-5" />}
      >
        <form id="period-form" onSubmit={handleSubmit} className="space-y-4" noValidate>
          {formError ? <ApiErrorAlert message={formError} onRetry={() => setFormError(null)} /> : null}

          <div className="space-y-1.5">
            <Label htmlFor="fp-name">اسم الفترة *</Label>
            <Input id="fp-name" value={name} onChange={(e) => setName(e.target.value)} disabled={submitting} placeholder="مثال: 2026 — السنة المالية" />
          </div>

          <div className="grid gap-4 sm:grid-cols-2">
            <div className="space-y-1.5">
              <Label htmlFor="fp-start">من تاريخ *</Label>
              <Input id="fp-start" type="date" value={startDate} onChange={(e) => setStartDate(e.target.value)} disabled={submitting} className={inputLtrClassName} />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="fp-end">إلى تاريخ *</Label>
              <Input id="fp-end" type="date" value={endDate} onChange={(e) => setEndDate(e.target.value)} disabled={submitting} className={inputLtrClassName} />
            </div>
          </div>
        </form>

        <div className="mt-4 flex justify-end gap-2 border-t border-[var(--color-border)] pt-4">
          <Button type="submit" form="period-form" variant="gold" disabled={submitting}>
            {submitting ? "جارٍ الحفظ…" : (<><Save className="size-4" />إنشاء الفترة</>)}
          </Button>
          <Button type="button" variant="ghost" onClick={() => setDialogOpen(false)} disabled={submitting}>
            إلغاء
          </Button>
        </div>
      </AccountingDialog>
    </div>
  );
}
