"use client";

import { useCallback, useEffect, useState } from "react";

import { AccountingWriteOnly } from "@/components/accounting/AccountingRoleGate";
import { ApiErrorAlert } from "@/components/shared/ApiErrorAlert";
import { EmptyState } from "@/components/shared/EmptyState";
import { PageLoading } from "@/components/shared/PageLoading";
import { SectionCard } from "@/components/shared/SectionCard";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";

import { recurringInvoicesApi } from "@/lib/api/accounting-extended";
import { notifyApiError, notifySuccess } from "@/lib/feedback";
import { formatDate } from "@/lib/utils/format-date";
import { formatMoney } from "@/lib/utils/format-money";
import type { RecurringInvoice, RecurrenceFrequency } from "@/types/accounting-extended";

const FREQUENCY_LABELS: Record<RecurrenceFrequency, string> = {
  WEEKLY: "أسبوعي",
  MONTHLY: "شهري",
  QUARTERLY: "ربع سنوي",
  YEARLY: "سنوي",
};

/**
 * Recurring invoice schedules.
 *
 * <p>Generating issues a real invoice and moves the schedule on in one step, so the
 * two can never disagree. The button is disabled for a run that has already been
 * billed: the backend refuses a second run for the same date as well, so a stale
 * screen cannot produce a duplicate invoice.
 */
export default function RecurringInvoicesPage() {
  const [rows, setRows] = useState<RecurringInvoice[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [working, setWorking] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setRows(await recurringInvoicesApi.list());
    } catch (e) {
      setError(e instanceof Error ? e.message : "تعذر تحميل البيانات");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const generate = async (template: RecurringInvoice) => {
    setWorking(template.id);
    try {
      const result = await recurringInvoicesApi.generate(template.id);
      notifySuccess(`تم إصدار الفاتورة ${result.invoiceNumber}`);
      await load();
    } catch (e) {
      notifyApiError(e, "???? ????? ????????");
    } finally {
      setWorking(null);
    }
  };

  if (loading) return <PageLoading label="جارٍ تحميل القوالب" />;

  return (
    <div className="space-y-6">
      <ApiErrorAlert message={error ?? ""} onRetry={load} />

      <SectionCard title="الفواتير المتكررة">
        {rows.length === 0 ? (
          <EmptyState title="لا توجد قوالب فواتير متكررة" />
        ) : (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>الوصف</TableHead>
                <TableHead>العميل</TableHead>
                <TableHead>الدورية</TableHead>
                <TableHead>التالي</TableHead>
                <TableHead className="text-end">الإجمالي</TableHead>
                <TableHead className="text-end">عدد الإصدارات</TableHead>
                <TableHead>الحالة</TableHead>
                <TableHead />
              </TableRow>
            </TableHeader>
            <TableBody>
              {rows.map((r) => (
                <TableRow key={r.id}>
                  <TableCell>{r.description}</TableCell>
                  <TableCell>{r.clientName ?? "-"}</TableCell>
                  <TableCell>
                    {FREQUENCY_LABELS[r.frequency]}
                    {r.intervalCount > 1 ? ` × ${r.intervalCount}` : ""}
                  </TableCell>
                  <TableCell>{formatDate(r.nextRunDate)}</TableCell>
                  <TableCell className="text-end ltr-nums">{formatMoney(r.total)}</TableCell>
                  <TableCell className="text-end ltr-nums">{r.timesGenerated}</TableCell>
                  <TableCell>
                    <Badge tone={r.active ? "neutral" : "warning"}>
                      {r.active ? "نشط" : "متوقف"}
                    </Badge>
                  </TableCell>
                  <TableCell className="text-end">
                    {r.active && (
                      <AccountingWriteOnly>
                        <Button
                          size="sm"
                          variant="outline"
                          disabled={working === r.id}
                          onClick={() => void generate(r)}
                        >
                          {working === r.id ? "جارٍ" : "إصدار"}
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
