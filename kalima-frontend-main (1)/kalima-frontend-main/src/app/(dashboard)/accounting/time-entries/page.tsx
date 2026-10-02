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

import { timeEntriesApi } from "@/lib/api/accounting-extended";
import { casesApi } from "@/lib/api/cases";
import { notifyApiError, notifySuccess } from "@/lib/feedback";
import { cn } from "@/lib/utils/cn";
import { formatDate } from "@/lib/utils/format-date";
import { formatMoney } from "@/lib/utils/format-money";
import type { CaseResponseDto } from "@/types/api";
import type { TimeEntry } from "@/types/accounting-extended";

const selectClassName = cn(
  "flex h-10 w-full cursor-pointer appearance-none rounded-xl border border-[var(--color-border)] bg-[var(--color-surface)] px-3.5 py-2 text-sm text-[var(--color-foreground)]",
  "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--color-gold-400)]",
);

const today = () => new Date().toISOString().slice(0, 10);

function hours(minutes: number) {
  const h = Math.floor(minutes / 60);
  const m = minutes % 60;
  return h > 0 ? `${h}س ${m}د` : `${m}د`;
}

/**
 * Time entries.
 *
 * <p>An entry that has been invoiced is shown as such and is not editable: the
 * invoice and the ledger entry behind it are one fact, and letting the hours move
 * underneath them would leave two records disagreeing. Voided entries stay in the
 * list for the same reason voided documents do elsewhere in the module.
 */
export default function TimeEntriesPage() {
  const [entries, setEntries] = useState<TimeEntry[]>([]);
  const [onlyUnbilled, setOnlyUnbilled] = useState(false);
  const [loading, setLoading] = useState(true);
  const [billingCaseId, setBillingCaseId] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState<string | null>(null);

  const [entryCase, setEntryCase] = useState("");
  const [entryDate, setEntryDate] = useState(today);
  const [entryMinutes, setEntryMinutes] = useState("");
  const [entryDescription, setEntryDescription] = useState("");
  const [entryBillable, setEntryBillable] = useState(true);
  const [entryRate, setEntryRate] = useState("");
  const [cases, setCases] = useState<CaseResponseDto[]>([]);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const [e, c] = await Promise.all([
        timeEntriesApi.list(),
        casesApi.list({ size: 200 }),
      ]);
      setEntries(e);
      setCases(c.content);
    } catch (err) {
      setError(err instanceof Error ? err.message : "تعذر تحميل البيانات");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const visible = useMemo(
    () => (onlyUnbilled ? entries.filter((e) => !e.invoiceId && !e.voided) : entries),
    [entries, onlyUnbilled],
  );

  const totals = useMemo(
    () => ({
      minutes: visible.filter((e) => !e.voided).reduce((s, e) => s + e.durationMinutes, 0),
      value: visible.filter((e) => !e.voided).reduce((s, e) => s + e.amount, 0),
    }),
    [visible],
  );

  /**
   * One row per case that still holds unbilled billable work. Billing works from
   * the whole case balance on the server, never from a partial selection made in
   * the browser, so a lawyer cannot bill some hours and leave the rest stranded.
   */
  const billableCases = useMemo(() => {
    const byCase = new Map<string, { caseId: string; caseNumber: string; entries: number; minutes: number; value: number }>();
    for (const e of entries) {
      if (!e.caseId || e.invoiceId || e.voided || !e.billable) continue;
      const row = byCase.get(e.caseId) ?? {
        caseId: e.caseId,
        caseNumber: e.caseNumber ?? "-",
        entries: 0,
        minutes: 0,
        value: 0,
      };
      row.entries += 1;
      row.minutes += e.durationMinutes;
      row.value += e.amount;
      byCase.set(e.caseId, row);
    }
    return [...byCase.values()].sort((a, b) => b.value - a.value);
  }, [entries]);

  const billCase = useCallback(
    async (caseId: string, caseNumber: string) => {
      if (!window.confirm(`إصدار فاتورة لكل العمل غير المفوتر على القضية ${caseNumber}؟`)) return;
      setBillingCaseId(caseId);
      setError(null);
      try {
        const result = await timeEntriesApi.billCase(caseId, {});
        notifySuccess(`تم إصدار الفاتورة ${result.invoiceNumber} بمبلغ ${formatMoney(result.total)}`);
        await load();
      } catch (e) {
        setError(e instanceof Error ? e.message : "تعذر إصدار الفاتورة");
      } finally {
        setBillingCaseId(null);
      }
    },
    [load],
  );

  const submitEntry = async (event: FormEvent) => {
    event.preventDefault();
    setSaving("entry");
    try {
      await timeEntriesApi.create({
        caseId: entryCase,
        workDate: entryDate,
        durationMinutes: Number(entryMinutes),
        ...(entryDescription.trim() ? { description: entryDescription.trim() } : {}),
        billable: entryBillable,
        ...(entryRate ? { hourlyRate: Number(entryRate) } : {}),
      });
      notifySuccess("تم تسجيل قيد الوقت");
      setEntryMinutes("");
      setEntryDescription("");
      await load();
    } catch (e) {
      notifyApiError(e, "تعذر تسجيل قيد الوقت");
    } finally {
      setSaving(null);
    }
  };

  const voidEntry = async (row: TimeEntry) => {
    if (!window.confirm(`إلغاء القيد ${row.entryNumber}؟`)) return;
    setSaving(`entry-${row.id}`);
    try {
      await timeEntriesApi.voidEntry(row.id);
      notifySuccess("تم إلغاء قيد الوقت");
      await load();
    } catch (e) {
      notifyApiError(e, "تعذر إلغاء قيد الوقت");
    } finally {
      setSaving(null);
    }
  };

  if (loading) return <PageLoading label="جارٍ تحميل قيود الوقت" />;

  return (
    <div className="space-y-6">
      <ApiErrorAlert message={error ?? ""} onRetry={load} />

      <AccountingWriteOnly>
        <SectionCard title="تسجيل قيد وقت">
          <form onSubmit={submitEntry} className="grid gap-3 md:grid-cols-3 xl:grid-cols-6">
            <div className="xl:col-span-2">
              <Label htmlFor="entry-case">القضية</Label>
              <select
                id="entry-case"
                className={selectClassName}
                value={entryCase}
                onChange={(e) => setEntryCase(e.target.value)}
                required
              >
                <option value="">اختر القضية</option>
                {cases.map((c) => (
                  <option key={c.id} value={c.id}>
                    {c.caseNumber} — {c.title}
                  </option>
                ))}
              </select>
            </div>
            <div>
              <Label htmlFor="entry-date">التاريخ</Label>
              <Input
                id="entry-date"
                type="date"
                value={entryDate}
                onChange={(e) => setEntryDate(e.target.value)}
                required
              />
            </div>
            <div>
              <Label htmlFor="entry-minutes">المدة بالدقائق</Label>
              <Input
                id="entry-minutes"
                type="number"
                min="1"
                step="1"
                value={entryMinutes}
                onChange={(e) => setEntryMinutes(e.target.value)}
                required
              />
            </div>
            <div className="flex items-end gap-3 pb-1">
              <label className="flex items-center gap-2 text-sm">
                <input
                  type="checkbox"
                  checked={entryBillable}
                  onChange={(e) => setEntryBillable(e.target.checked)}
                />
                قابل للفوترة
              </label>
            </div>
            <div>
              <Label htmlFor="entry-rate">سعر الساعة</Label>
              <Input
                id="entry-rate"
                type="number"
                min="0"
                step="0.01"
                value={entryRate}
                onChange={(e) => setEntryRate(e.target.value)}
              />
            </div>
            <div className="xl:col-span-3">
              <Label htmlFor="entry-description">البيان</Label>
              <Input
                id="entry-description"
                value={entryDescription}
                onChange={(e) => setEntryDescription(e.target.value)}
              />
            </div>
            <div className="flex items-end">
              <Button type="submit" disabled={saving !== null}>
                {saving === "entry" ? "جارٍ الحفظ" : "حفظ"}
              </Button>
            </div>
          </form>
        </SectionCard>
      </AccountingWriteOnly>

      <div className="flex flex-wrap items-center gap-3">
        <button
          type="button"
          onClick={() => setOnlyUnbilled((v) => !v)}
          className={`rounded-xl px-3 py-1.5 text-sm transition-colors ${
            onlyUnbilled
              ? "bg-[var(--color-gold-500)] text-white"
              : "bg-[var(--color-surface)] text-[var(--color-muted-foreground)]"
          }`}
        >
          غير المفوتر فقط
        </button>
        <span className="text-sm text-[var(--color-muted-foreground)]">
          الإجمالي: {hours(totals.minutes)} · {formatMoney(totals.value)}
        </span>
      </div>

      <SectionCard title="قيود الوقت">
        {visible.length === 0 ? (
          <EmptyState title="لا توجد قيود وقت" />
        ) : (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>الرقم</TableHead>
                <TableHead>التاريخ</TableHead>
                <TableHead>القضية</TableHead>
                <TableHead>المحامي</TableHead>
                <TableHead className="text-end">المدة</TableHead>
                <TableHead className="text-end">القيمة</TableHead>
                <TableHead>الحالة</TableHead>
                <TableHead />
              </TableRow>
            </TableHeader>
            <TableBody>
              {visible.map((e) => (
                <TableRow key={e.id} className={e.voided ? "opacity-50" : undefined}>
                  <TableCell className="ltr-nums">{e.entryNumber}</TableCell>
                  <TableCell>{formatDate(e.workDate)}</TableCell>
                  <TableCell className="ltr-nums">{e.caseNumber ?? "-"}</TableCell>
                  <TableCell>{e.lawyerName ?? "-"}</TableCell>
                  <TableCell className="text-end ltr-nums">{hours(e.durationMinutes)}</TableCell>
                  <TableCell className="text-end ltr-nums">{formatMoney(e.amount)}</TableCell>
                  <TableCell>
                    {e.voided ? (
                      <Badge tone="warning">ملغى</Badge>
                    ) : e.invoiceId ? (
                      <Badge tone="info">مفوتر</Badge>
                    ) : (
                      <Badge tone="neutral">غير مفوتر</Badge>
                    )}
                  </TableCell>
                  <TableCell className="text-end">
                    {!e.voided && !e.invoiceId && (
                      <AccountingWriteOnly>
                        <Button
                          type="button"
                          size="sm"
                          variant="outline"
                          disabled={saving !== null}
                          onClick={() => void voidEntry(e)}
                        >
                          {saving === `entry-${e.id}` ? "جارٍ" : "إلغاء"}
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

      <AccountingWriteOnly>
        <SectionCard title="فوترة عمل القضايا">
          {billableCases.length === 0 ? (
            <EmptyState title="لا يوجد عمل غير مفوتر قابل للفوترة" />
          ) : (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>القضية</TableHead>
                  <TableHead className="text-end">القيود</TableHead>
                  <TableHead className="text-end">المدة</TableHead>
                  <TableHead className="text-end">القيمة غير المفوترة</TableHead>
                  <TableHead />
                </TableRow>
              </TableHeader>
              <TableBody>
                {billableCases.map((c) => (
                  <TableRow key={c.caseId}>
                    <TableCell className="ltr-nums">{c.caseNumber}</TableCell>
                    <TableCell className="text-end ltr-nums">{c.entries}</TableCell>
                    <TableCell className="text-end ltr-nums">{hours(c.minutes)}</TableCell>
                    <TableCell className="text-end ltr-nums">{formatMoney(c.value)}</TableCell>
                    <TableCell className="text-end">
                      <Button
                        type="button"
                        size="sm"
                        disabled={billingCaseId !== null}
                        onClick={() => void billCase(c.caseId, c.caseNumber)}
                      >
                        {billingCaseId === c.caseId ? "جارٍ الإصدار" : "إصدار فاتورة"}
                      </Button>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}
        </SectionCard>
      </AccountingWriteOnly>
    </div>
  );
}