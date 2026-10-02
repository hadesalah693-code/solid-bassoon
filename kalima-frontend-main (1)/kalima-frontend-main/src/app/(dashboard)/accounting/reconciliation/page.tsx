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
import { reconciliationApi } from "@/lib/api/accounting-extended";
import { notifyApiError, notifySuccess } from "@/lib/feedback";
import { cn } from "@/lib/utils/cn";
import { formatDate } from "@/lib/utils/format-date";
import { formatMoney } from "@/lib/utils/format-money";
import type { BankAccountResponseDto } from "@/types/accounting";
import type {
  BankReconciliation,
  MatchSuggestion,
  MatchType,
  ReconciliationStatus,
  StatementLine,
} from "@/types/accounting-extended";

const STATUS_LABELS: Record<ReconciliationStatus, string> = {
  IN_PROGRESS: "جارية",
  COMPLETED: "مكتملة",
  CANCELLED: "ملغاة",
};

const MATCH_LABELS: Record<MatchType, string> = {
  RECEIPT: "سند قبض",
  PAYMENT: "دفعة مورد",
  TRANSFER: "تحويل",
  EXPENSE: "مصروف",
};

const selectClassName = cn(
  "flex h-10 w-full cursor-pointer appearance-none rounded-xl border border-[var(--color-border)] bg-[var(--color-surface)] px-3.5 py-2 text-sm text-[var(--color-foreground)]",
  "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--color-gold-400)]",
);

const today = () => new Date().toISOString().slice(0, 10);

/**
 * Bank reconciliations.
 *
 * <p>A reconciliation can only be closed when every line is matched and the
 * reconciled balance agrees with the statement, so the difference is shown on every
 * row rather than only at the end. That way the gap to close is visible while there
 * is still time to explain it.
 *
 * <p>Matching itself is decided by the server: it returns the candidate receipts,
 * payments, transfers and expenses for a line with the same amount and window, and
 * this screen only offers them. Recomputing candidates here would let the browser
 * agree with the ledger on one statement and disagree on the next.
 */
export default function ReconciliationPage() {
  const [rows, setRows] = useState<BankReconciliation[]>([]);
  const [accounts, setAccounts] = useState<BankAccountResponseDto[]>([]);
  const [accountId, setAccountId] = useState("");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [working, setWorking] = useState<string | null>(null);

  const [openAccount, setOpenAccount] = useState("");
  const [openDate, setOpenDate] = useState(today);
  const [openClosing, setOpenClosing] = useState("");

  const [expanded, setExpanded] = useState<string | null>(null);
  const [lines, setLines] = useState<StatementLine[]>([]);
  const [suggestions, setSuggestions] = useState<Record<string, MatchSuggestion[]>>({});
  const [lineDate, setLineDate] = useState(today);
  const [lineAmount, setLineAmount] = useState("");
  const [lineDescription, setLineDescription] = useState("");

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const [r, a] = await Promise.all([
        reconciliationApi.list(accountId || undefined),
        bankAccountsApi.list(),
      ]);
      setRows(r);
      setAccounts(a);
      if (!accountId && a.length > 0) setOpenAccount(a[0].id);
    } catch (e) {
      setError(e instanceof Error ? e.message : "تعذر تحميل البيانات");
    } finally {
      setLoading(false);
    }
  }, [accountId]);

  useEffect(() => {
    void load();
  }, [load]);

  const loadLines = useCallback(async (id: string) => {
    setWorking(`lines-${id}`);
    try {
      const l = await reconciliationApi.listLines(id);
      setLines(l);
      const found: Record<string, MatchSuggestion[]> = {};
      for (const line of l.filter((x) => !x.matched)) {
        try {
          found[line.id] = await reconciliationApi.suggestions(line.id);
        } catch {
          found[line.id] = [];
        }
      }
      setSuggestions(found);
    } catch (e) {
      notifyApiError(e, "تعذر تحميل سطور الكشف");
    } finally {
      setWorking(null);
    }
  }, []);

  const toggle = async (id: string) => {
    if (expanded === id) {
      setExpanded(null);
      setLines([]);
      setSuggestions({});
      return;
    }
    setExpanded(id);
    await loadLines(id);
  };

  const open = async (event: FormEvent) => {
    event.preventDefault();
    setWorking("open");
    try {
      const created = await reconciliationApi.open({
        bankAccountId: openAccount,
        statementDate: openDate,
        statementClosingBalance: Number(openClosing),
      });
      notifySuccess(`تم فتح التسوية بتاريخ ${formatDate(created.statementDate)}`);
      setOpenClosing("");
      await load();
    } catch (e) {
      notifyApiError(e, "تعذر فتح التسوية");
    } finally {
      setWorking(null);
    }
  };

  const addLine = async (event: FormEvent) => {
    event.preventDefault();
    if (!expanded) return;
    setWorking("line");
    try {
      await reconciliationApi.addLine(expanded, {
        lineDate,
        amount: Number(lineAmount),
        ...(lineDescription.trim() ? { description: lineDescription.trim() } : {}),
      });
      setLineAmount("");
      setLineDescription("");
      await loadLines(expanded);
      await load();
    } catch (e) {
      notifyApiError(e, "تعذر إضافة سطر الكشف");
    } finally {
      setWorking(null);
    }
  };

  const complete = async (id: string) => {
    setWorking(`complete-${id}`);
    try {
      await reconciliationApi.complete(id);
      notifySuccess("تم إغلاق التسوية");
      await load();
    } catch (e) {
      notifyApiError(e, "تعذر إغلاق التسوية");
    } finally {
      setWorking(null);
    }
  };

  const match = async (lineId: string, type: MatchType, matchedId: string) => {
    setWorking(`match-${lineId}`);
    try {
      await reconciliationApi.match(lineId, type, matchedId);
      notifySuccess("تم الربط");
      if (expanded) await loadLines(expanded);
      await load();
    } catch (e) {
      notifyApiError(e, "تعذر ربط السطر");
    } finally {
      setWorking(null);
    }
  };

  const unmatch = async (lineId: string) => {
    setWorking(`match-${lineId}`);
    try {
      await reconciliationApi.unmatch(lineId);
      notifySuccess("تم إلغاء الربط");
      if (expanded) await loadLines(expanded);
      await load();
    } catch (e) {
      notifyApiError(e, "تعذر إلغاء الربط");
    } finally {
      setWorking(null);
    }
  };

  if (loading) return <PageLoading label="جارٍ تحميل التسويات" />;

  return (
    <div className="space-y-6">
      <ApiErrorAlert message={error ?? ""} onRetry={load} />

      <div className="flex flex-wrap gap-2">
        <Button variant={accountId === "" ? "default" : "outline"} onClick={() => setAccountId("")}>
          كل الحسابات
        </Button>
        {accounts.map((a) => (
          <Button
            key={a.id}
            variant={accountId === a.id ? "default" : "outline"}
            onClick={() => setAccountId(a.id)}
          >
            {a.nameAr}
          </Button>
        ))}
      </div>

      <AccountingWriteOnly>
        <SectionCard title="فتح تسوية جديدة">
          <form onSubmit={open} className="grid gap-3 md:grid-cols-3 xl:grid-cols-4">
            <div>
              <Label htmlFor="rec-account">الحساب البنكي</Label>
              <select
                id="rec-account"
                className={selectClassName}
                value={openAccount}
                onChange={(e) => setOpenAccount(e.target.value)}
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
              <Label htmlFor="rec-date">تاريخ الكشف</Label>
              <Input
                id="rec-date"
                type="date"
                value={openDate}
                onChange={(e) => setOpenDate(e.target.value)}
                required
              />
            </div>
            <div>
              <Label htmlFor="rec-closing">رصيد الكشف الختامي</Label>
              <Input
                id="rec-closing"
                type="number"
                step="0.01"
                value={openClosing}
                onChange={(e) => setOpenClosing(e.target.value)}
                required
              />
            </div>
            <div className="flex items-end">
              <Button type="submit" disabled={working !== null}>
                {working === "open" ? "جارٍ" : "فتح التسوية"}
              </Button>
            </div>
          </form>
        </SectionCard>
      </AccountingWriteOnly>

      <SectionCard title="التسويات البنكية">
        {rows.length === 0 ? (
          <EmptyState title="لا توجد تسويات" />
        ) : (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>الحساب</TableHead>
                <TableHead>تاريخ الكشف</TableHead>
                <TableHead className="text-end">رصيد الكشف</TableHead>
                <TableHead className="text-end">الرصيد المطابق</TableHead>
                <TableHead className="text-end">الفرق</TableHead>
                <TableHead>الحالة</TableHead>
                <TableHead />
              </TableRow>
            </TableHeader>
            <TableBody>
              {rows.map((r) => (
                <TableRow key={r.id}>
                  <TableCell>{r.bankAccountName ?? "-"}</TableCell>
                  <TableCell>{formatDate(r.statementDate)}</TableCell>
                  <TableCell className="text-end ltr-nums">
                    {formatMoney(r.statementClosingBalance)}
                  </TableCell>
                  <TableCell className="text-end ltr-nums">
                    {formatMoney(r.reconciledBalance)}
                  </TableCell>
                  <TableCell
                    className={`text-end ltr-nums ${
                      r.difference === 0 ? "" : "text-[var(--color-danger)]"
                    }`}
                  >
                    {formatMoney(r.difference)}
                  </TableCell>
                  <TableCell>
                    <Badge tone={r.status === "COMPLETED" ? "neutral" : "info"}>
                      {STATUS_LABELS[r.status]}
                    </Badge>
                  </TableCell>
                  <TableCell className="text-end">
                    <div className="flex justify-end gap-2">
                      <Button
                        size="sm"
                        variant="outline"
                        disabled={working === `lines-${r.id}`}
                        onClick={() => void toggle(r.id)}
                      >
                        {expanded === r.id ? "إخفاء السطور" : "السطور"}
                      </Button>
                      {r.status === "IN_PROGRESS" && (
                        <AccountingWriteOnly>
                          <Button
                            size="sm"
                            disabled={working === `complete-${r.id}`}
                            onClick={() => void complete(r.id)}
                          >
                            {working === `complete-${r.id}` ? "جارٍ" : "إغلاق"}
                          </Button>
                        </AccountingWriteOnly>
                      )}
                    </div>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
      </SectionCard>

      {expanded ? (
        <>
          <AccountingWriteOnly>
            <SectionCard title="إضافة سطر كشف">
              <form onSubmit={addLine} className="grid gap-3 md:grid-cols-3 xl:grid-cols-5">
                <div>
                  <Label htmlFor="line-date">التاريخ</Label>
                  <Input
                    id="line-date"
                    type="date"
                    value={lineDate}
                    onChange={(e) => setLineDate(e.target.value)}
                    required
                  />
                </div>
                <div>
                  <Label htmlFor="line-description">البيان</Label>
                  <Input
                    id="line-description"
                    value={lineDescription}
                    onChange={(e) => setLineDescription(e.target.value)}
                  />
                </div>
                <div>
                  <Label htmlFor="line-amount">المبلغ (سالب للخصم)</Label>
                  <Input
                    id="line-amount"
                    type="number"
                    step="0.01"
                    value={lineAmount}
                    onChange={(e) => setLineAmount(e.target.value)}
                    required
                  />
                </div>
                <div className="flex items-end">
                  <Button type="submit" disabled={working !== null}>
                    {working === "line" ? "جارٍ" : "إضافة"}
                  </Button>
                </div>
              </form>
            </SectionCard>
          </AccountingWriteOnly>

          <SectionCard title="سطور الكشف">
            {lines.length === 0 ? (
              <EmptyState title="لا توجد سطور في هذه التسوية" />
            ) : (
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>التاريخ</TableHead>
                    <TableHead>البيان</TableHead>
                    <TableHead className="text-end">المبلغ</TableHead>
                    <TableHead>الحالة</TableHead>
                    <TableHead>الربط المقترح</TableHead>
                    <TableHead />
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {lines.map((line) => {
                    const options = suggestions[line.id] ?? [];
                    return (
                      <TableRow key={line.id}>
                        <TableCell>{formatDate(line.lineDate)}</TableCell>
                        <TableCell>{line.description ?? "-"}</TableCell>
                        <TableCell className="text-end ltr-nums">{formatMoney(line.amount)}</TableCell>
                        <TableCell>
                          {line.matched ? (
                            <Badge tone="info">
                              {line.matchedType ? MATCH_LABELS[line.matchedType] : "مربوط"}
                            </Badge>
                          ) : (
                            <Badge tone="warning">غير مربوط</Badge>
                          )}
                        </TableCell>
                        <TableCell>
                          {!line.matched && options.length === 0 ? (
                            <span className="text-xs text-[var(--color-muted-foreground)]">
                              لا يوجد مرشح
                            </span>
                          ) : (
                            <div className="flex flex-wrap gap-2">
                              {options.map((s) => (
                                <Button
                                  key={`${s.type}-${s.id}`}
                                  size="sm"
                                  variant="outline"
                                  disabled={working !== null}
                                  onClick={() => void match(line.id, s.type, s.id)}
                                >
                                  {MATCH_LABELS[s.type]} {s.reference ?? "-"} ·{" "}
                                  {formatMoney(s.amount)}
                                </Button>
                              ))}
                            </div>
                          )}
                        </TableCell>
                        <TableCell className="text-end">
                          {line.matched && (
                            <AccountingWriteOnly>
                              <Button
                                size="sm"
                                variant="outline"
                                disabled={working !== null}
                                onClick={() => void unmatch(line.id)}
                              >
                                إلغاء الربط
                              </Button>
                            </AccountingWriteOnly>
                          )}
                        </TableCell>
                      </TableRow>
                    );
                  })}
                </TableBody>
              </Table>
            )}
          </SectionCard>
        </>
      ) : null}
    </div>
  );
}