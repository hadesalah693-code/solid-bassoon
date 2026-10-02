"use client";

import { Fragment, useEffect, useMemo, useState, type FormEvent } from "react";
import {
  BookOpenCheck,
  Check,
  Plus,
  RotateCcw,
  Save,
  Trash2,
  Undo2,
  X,
} from "lucide-react";

import { AccountingDialog } from "@/components/accounting/AccountingDialog";
import { AccountingRoleGate, AccountingWriteOnly } from "@/components/accounting/AccountingRoleGate";
import { DisputeModuleHero } from "@/components/disputes/DisputeModuleHero";
import { ApiErrorAlert } from "@/components/shared/ApiErrorAlert";
import { EmptyState } from "@/components/shared/EmptyState";
import { PageLoading } from "@/components/shared/PageLoading";
import { Pagination } from "@/components/shared/Pagination";
import { SearchInput } from "@/components/shared/SearchInput";
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

import { accountsApi, journalApi } from "@/lib/api/accounting";
import { ApiRequestError } from "@/lib/api/client";
import { JOURNAL_SOURCE_AR, JOURNAL_SOURCE_OPTIONS, JOURNAL_SOURCE_TONE } from "@/lib/accounting/labels";
import { useDebounce } from "@/hooks/use-debounce";
import { notifyApiError, notifySuccess } from "@/lib/feedback";
import { formatDate } from "@/lib/utils/format-date";
import { formatMoney } from "@/lib/utils/format-money";
import { cn } from "@/lib/utils/cn";
import type { AccountResponseDto, JournalSourceDto } from "@/types/accounting";

const PAGE_SIZE = 20;

const selectClassName = cn(
  "flex h-10 w-full cursor-pointer appearance-none rounded-xl border border-[var(--color-border)] bg-[var(--color-surface)] px-3.5 py-2 text-sm text-[var(--color-foreground)] shadow-sm transition-colors",
  "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--color-gold-400)]",
  "disabled:cursor-not-allowed disabled:opacity-50",
);

const inputLtrClassName = "ltr-nums text-start";

function today() {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(
    d.getDate(),
  ).padStart(2, "0")}`;
}

function round2(n: number) {
  return Math.round((n + Number.EPSILON) * 100) / 100;
}

type LineDraft = {
  key: number;
  accountId: string;
  debit: string;
  credit: string;
  description: string;
};

function emptyLine(key: number): LineDraft {
  return { key, accountId: "", debit: "", credit: "", description: "" };
}

export default function JournalPage() {
  return (
    <AccountingRoleGate>
      <JournalPageInner />
    </AccountingRoleGate>
  );
}

function JournalPageInner() {
  const [rows, setRows] = useState<Awaited<ReturnType<typeof journalApi.list>>["content"]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const [search, setSearch] = useState("");
  const debouncedSearch = useDebounce(search, 300);
  const [source, setSource] = useState<JournalSourceDto | "">("");
  const [from, setFrom] = useState("");
  const [to, setTo] = useState("");
  const [fetchId, setFetchId] = useState(0);

  const [accounts, setAccounts] = useState<AccountResponseDto[]>([]);
  const [expanded, setExpanded] = useState<string | null>(null);

  const [dialogOpen, setDialogOpen] = useState(false);
  const [entryDate, setEntryDate] = useState(today);
  const [description, setDescription] = useState("");
  const [lines, setLines] = useState<LineDraft[]>([emptyLine(1), emptyLine(2)]);
  const [submitting, setSubmitting] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      setLoading(true);
      setError(null);
      try {
        const res = await journalApi.list({
          page,
          size: PAGE_SIZE,
          search: debouncedSearch,
          source: source || undefined,
          from: from || null,
          to: to || null,
        });
        if (!cancelled) {
          setRows(res.content);
          setTotal(res.totalElements);
        }
      } catch (e) {
        if (!cancelled) {
          setError(e instanceof ApiRequestError ? e.message : "تعذّر تحميل القيود");
          setRows([]);
          setTotal(0);
        }
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [page, debouncedSearch, source, from, to, fetchId]);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const res = await accountsApi.list();
        if (!cancelled) setAccounts(res.filter((a) => a.active));
      } catch {
        /* the dialog degrades to an empty account list */
      }
    })();
    return () => {
      cancelled = true;
    };
  }, []);

  const totals = useMemo(() => {
    const debit = lines.reduce((s, l) => s + (Number(l.debit) || 0), 0);
    const credit = lines.reduce((s, l) => s + (Number(l.credit) || 0), 0);
    return { debit: round2(debit), credit: round2(credit), diff: round2(debit - credit) };
  }, [lines]);

  const balanced = totals.diff === 0 && totals.debit > 0;
  const hasFilters = Boolean(debouncedSearch.trim()) || Boolean(source) || Boolean(from) || Boolean(to);

  function clearFilters() {
    setSearch("");
    setSource("");
    setFrom("");
    setTo("");
    setPage(0);
  }

  function openCreate() {
    setEntryDate(today());
    setDescription("");
    setLines([emptyLine(1), emptyLine(2)]);
    setFormError(null);
    setDialogOpen(true);
  }

  function updateLine(key: number, patch: Partial<LineDraft>) {
    setLines((prev) =>
      prev.map((l) => {
        if (l.key !== key) return l;
        // A line is debit-or-credit; entering one clears the other.
        if (patch.debit !== undefined && patch.debit !== "") {
          return { ...l, ...patch, credit: "" };
        }
        if (patch.credit !== undefined && patch.credit !== "") {
          return { ...l, ...patch, debit: "" };
        }
        return { ...l, ...patch };
      }),
    );
  }

  function addLine() {
    setLines((prev) => [...prev, emptyLine(prev.length + 1)]);
  }

  function removeLine(key: number) {
    setLines((prev) => (prev.length <= 2 ? prev : prev.filter((l) => l.key !== key)));
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setFormError(null);

    if (!entryDate) {
      setFormError("تاريخ القيد مطلوب");
      return;
    }
    if (!description.trim()) {
      setFormError("بيان القيد مطلوب");
      return;
    }
    const filled = lines.filter((l) => l.accountId && ((Number(l.debit) || 0) > 0 || (Number(l.credit) || 0) > 0));
    if (filled.length < 2) {
      setFormError("القيد يحتاج طرفين على الأقل بحساب ومبلغ");
      return;
    }
    if (totals.debit !== totals.credit) {
      setFormError(`القيد غير متوازن: الفرق ${formatMoney(totals.diff)}`);
      return;
    }

    setSubmitting(true);
    try {
      const created = await journalApi.create({
        entryDate,
        description: description.trim(),
        lines: filled.map((l) => ({
          accountId: l.accountId,
          debit: round2(Number(l.debit) || 0),
          credit: round2(Number(l.credit) || 0),
          description: l.description.trim() || null,
        })),
      });
      notifySuccess("تم ترحيل القيد", created.entryNumber);
      setDialogOpen(false);
      setPage(0);
      setFetchId((n) => n + 1);
    } catch (err) {
      if (err instanceof ApiRequestError) setFormError(err.message);
      else setFormError("تعذّر ترحيل القيد");
      notifyApiError(err, "تعذّر ترحيل القيد");
    } finally {
      setSubmitting(false);
    }
  }

  async function handleReverse(id: string, entryNumber: string) {
    if (
      !window.confirm(
        `عكس القيد «${entryNumber}»؟ سيُنشأ قيد عكسي مطابق ويؤثر على الأرصدة، ولا يمكن عكس القيد العكسي.`,
      )
    ) {
      return;
    }
    try {
      const rev = await journalApi.reverse(id);
      notifySuccess("تم عكس القيد", rev.entryNumber);
      setFetchId((n) => n + 1);
    } catch (err) {
      notifyApiError(err, "تعذّر عكس القيد");
    }
  }

  return (
    <div dir="rtl" className="space-y-6 pb-10">
      <DisputeModuleHero
        icon={BookOpenCheck}
        eyebrow="المالية والمحاسبة"
        title="دفتر اليومية"
        description="كل القيود المرحلة في النظام. الفواتير والمصروفات والمقبوضات تُرحّل تلقائياً ويمكن عكس أي قيد بقيد مطابق."
        badges={
          !loading && !error ? (
            <span className="rounded-lg bg-white/10 px-2.5 py-1 text-xs font-medium text-[var(--color-gold-200)] ring-1 ring-white/15">
              {total.toLocaleString("ar-EG")} قيد
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
              قيد جديد
            </Button>
          </AccountingWriteOnly>
        }
      />

      <SectionCard title="البحث والتصفية" contentClassName="space-y-3">
        <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-5">
          <div className="xl:col-span-2">
            <SearchInput
              value={search}
              onChange={(v) => { setSearch(v); setPage(0); }}
              placeholder="بحث بالرقم أو البيان..."
            />
          </div>
          <select
            className={selectClassName}
            value={source}
            onChange={(e) => { setSource(e.target.value as JournalSourceDto | ""); setPage(0); }}
            aria-label="مصدر القيد"
          >
            <option value="">كل المصادر</option>
            {JOURNAL_SOURCE_OPTIONS.map((o) => (
              <option key={o.value} value={o.value}>{o.label}</option>
            ))}
          </select>
          <Input type="date" value={from} onChange={(e) => { setFrom(e.target.value); setPage(0); }} className={inputLtrClassName} aria-label="من تاريخ" />
          <Input type="date" value={to} onChange={(e) => { setTo(e.target.value); setPage(0); }} className={inputLtrClassName} aria-label="إلى تاريخ" />
        </div>
        {hasFilters ? (
          <div className="flex items-center justify-end border-t border-[var(--color-border)] pt-3">
            <Button type="button" variant="ghost" size="sm" className="h-8 gap-1.5 text-[var(--color-muted-foreground)]" onClick={clearFilters}>
              <RotateCcw className="size-3.5" />مسح التصفية
            </Button>
          </div>
        ) : null}
      </SectionCard>

      {error ? (
        <ApiErrorAlert message={error} onRetry={() => setFetchId((n) => n + 1)} />
      ) : null}

      <SectionCard flush title="القيود">
        {loading ? (
          <PageLoading label="جاري تحميل القيود..." />
        ) : rows.length === 0 ? (
          <div className="p-6">
            <EmptyState
              icon={BookOpenCheck}
              title={hasFilters ? "لا توجد نتائج" : "لا توجد قيود"}
              description={hasFilters ? "جرّب تعديل البحث أو إزالة التصفية." : "سجّل أول قيد يدوي أو أصدر فاتورة."}
              action={hasFilters ? <Button variant="outline" onClick={clearFilters}>مسح التصفية</Button> : null}
            />
          </div>
        ) : (
          <>
            <div className="overflow-x-auto">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead className="text-right">رقم القيد</TableHead>
                    <TableHead className="text-right">التاريخ</TableHead>
                    <TableHead className="text-right">البيان</TableHead>
                    <TableHead className="text-right">المصدر</TableHead>
                    <TableHead className="text-right">مدين</TableHead>
                    <TableHead className="text-right">دائن</TableHead>
                    <TableHead className="text-right">الحالة</TableHead>
                    <TableHead className="text-right">إجراءات</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {rows.map((row) => {
                    const isOpen = expanded === row.id;
                    return (
                      <Fragment key={row.id}>
                        <TableRow
                          className="cursor-pointer transition-colors hover:bg-[var(--color-navy-50)]/40"
                          onClick={() => setExpanded(isOpen ? null : row.id)}
                        >
                          <TableCell className="font-semibold ltr-nums">{row.entryNumber}</TableCell>
                          <TableCell className="whitespace-nowrap text-[var(--color-muted-foreground)]">
                            {formatDate(row.entryDate)}
                          </TableCell>
                          <TableCell className="max-w-[18rem] truncate">{row.description}</TableCell>
                          <TableCell>
                            <Badge tone={JOURNAL_SOURCE_TONE[row.source]}>
                              {JOURNAL_SOURCE_AR[row.source]}
                            </Badge>
                          </TableCell>
                          <TableCell className="whitespace-nowrap ltr-nums">{formatMoney(row.totalDebit)}</TableCell>
                          <TableCell className="whitespace-nowrap ltr-nums">{formatMoney(row.totalCredit)}</TableCell>
                          <TableCell>
                            <Badge tone={row.posted ? "success" : "warning"}>
                              {row.posted ? "مرحّل" : "مسودة"}
                            </Badge>
                          </TableCell>
                          <TableCell onClick={(e) => e.stopPropagation()}>
                            <AccountingWriteOnly>
                              <Button
                                variant="ghost"
                                size="sm"
                                onClick={() => handleReverse(row.id, row.entryNumber)}
                                aria-label={`عكس ${row.entryNumber}`}
                              >
                                <Undo2 className="size-3.5" />
                                عكس
                              </Button>
                            </AccountingWriteOnly>
                          </TableCell>
                        </TableRow>
                        {isOpen ? (
                          <TableRow>
                            <TableCell colSpan={8} className="bg-[var(--color-surface-muted)]/40">
                              <Table>
                                <TableHeader>
                                  <TableRow>
                                    <TableHead className="text-right">#</TableHead>
                                    <TableHead className="text-right">الحساب</TableHead>
                                    <TableHead className="text-right">البيان</TableHead>
                                    <TableHead className="text-right">مدين</TableHead>
                                    <TableHead className="text-right">دائن</TableHead>
                                  </TableRow>
                                </TableHeader>
                                <TableBody>
                                  {row.lines.map((line) => (
                                    <TableRow key={line.id}>
                                      <TableCell className="ltr-nums text-[var(--color-muted-foreground)]">{line.lineNo}</TableCell>
                                      <TableCell>
                                        <span className="font-semibold ltr-nums">{line.accountCode}</span>{" "}
                                        <span className="text-[var(--color-muted-foreground)]">{line.accountName}</span>
                                        {line.clientName ? (
                                          <Badge tone="navy" className="ms-2">{line.clientName}</Badge>
                                        ) : null}
                                      </TableCell>
                                      <TableCell className="text-[var(--color-muted-foreground)]">
                                        {line.description ?? "—"}
                                      </TableCell>
                                      <TableCell className="ltr-nums">
                                        {line.debit > 0 ? formatMoney(line.debit) : "—"}
                                      </TableCell>
                                      <TableCell className="ltr-nums">
                                        {line.credit > 0 ? formatMoney(line.credit) : "—"}
                                      </TableCell>
                                    </TableRow>
                                  ))}
                                </TableBody>
                              </Table>
                              {row.createdBy ? (
                                <p className="mt-2 text-xs text-[var(--color-muted-foreground)]">
                                  بواسطة {row.createdBy}
                                </p>
                              ) : null}
                            </TableCell>
                          </TableRow>
                        ) : null}
                      </Fragment>
                    );
                  })}
                </TableBody>
              </Table>
            </div>
            <div className="border-t border-[var(--color-border)] p-4">
              <Pagination
                page={page}
                pageSize={PAGE_SIZE}
                total={total}
                onPageChange={setPage}
              />
            </div>
          </>
        )}
      </SectionCard>

      <AccountingDialog
        open={dialogOpen}
        onClose={() => setDialogOpen(false)}
        title="قيد يومية جديد"
        description="يجب أن يتساوى مجموع المدين مع مجموع الدائن قبل الترحيل."
        icon={<BookOpenCheck className="size-5" />}
        size="xl"
        scrollable
      >
        <form id="journal-entry-form" onSubmit={handleSubmit} className="space-y-4" noValidate>
          {formError ? <ApiErrorAlert message={formError} onRetry={() => setFormError(null)} /> : null}

          <div className="grid gap-4 md:grid-cols-3">
            <div className="space-y-1.5">
              <Label htmlFor="je-date">تاريخ القيد *</Label>
              <Input id="je-date" type="date" value={entryDate} onChange={(e) => setEntryDate(e.target.value)} disabled={submitting} className={inputLtrClassName} />
            </div>
            <div className="space-y-1.5 md:col-span-2">
              <Label htmlFor="je-desc">بيان القيد *</Label>
              <Input id="je-desc" value={description} onChange={(e) => setDescription(e.target.value)} disabled={submitting} placeholder="مثال: إثبات مصروف إيجار الشهر" />
            </div>
          </div>

          <div className="space-y-2">
            <div className="flex items-center justify-between">
              <Label>أطراف القيد</Label>
              <Button type="button" variant="outline" size="sm" onClick={addLine} disabled={submitting}>
                <Plus className="size-3.5" />إضافة سطر
              </Button>
            </div>

            <div className="space-y-2">
              {lines.map((line, idx) => (
                <div key={line.key} className="grid items-end gap-2 rounded-xl border border-[var(--color-border)] bg-[var(--color-surface-muted)]/40 p-3 sm:grid-cols-12">
                  <div className="space-y-1 sm:col-span-5">
                    {idx === 0 ? <Label className="text-xs">الحساب</Label> : null}
                    <select
                      className={selectClassName}
                      value={line.accountId}
                      onChange={(e) => updateLine(line.key, { accountId: e.target.value })}
                      disabled={submitting}
                      aria-label={`الحساب ${idx + 1}`}
                    >
                      <option value="">اختر الحساب</option>
                      {accounts.map((a) => (
                        <option key={a.id} value={a.id}>{a.code} — {a.nameAr}</option>
                      ))}
                    </select>
                  </div>
                  <div className="space-y-1 sm:col-span-3">
                    {idx === 0 ? <Label className="text-xs">مدين</Label> : null}
                    <Input
                      type="number" inputMode="decimal" min="0" step="0.01" placeholder="0.00"
                      value={line.debit}
                      onChange={(e) => updateLine(line.key, { debit: e.target.value })}
                      disabled={submitting}
                      className={inputLtrClassName}
                      dir="ltr"
                      aria-label={`مدين ${idx + 1}`}
                    />
                  </div>
                  <div className="space-y-1 sm:col-span-3">
                    {idx === 0 ? <Label className="text-xs">دائن</Label> : null}
                    <Input
                      type="number" inputMode="decimal" min="0" step="0.01" placeholder="0.00"
                      value={line.credit}
                      onChange={(e) => updateLine(line.key, { credit: e.target.value })}
                      disabled={submitting}
                      className={inputLtrClassName}
                      dir="ltr"
                      aria-label={`دائن ${idx + 1}`}
                    />
                  </div>
                  <div className="flex justify-end sm:col-span-1">
                    <Button
                      type="button"
                      variant="ghost"
                      size="icon"
                      onClick={() => removeLine(line.key)}
                      disabled={submitting || lines.length <= 2}
                      aria-label={`حذف السطر ${idx + 1}`}
                      className="text-[var(--color-danger)]"
                    >
                      <Trash2 className="size-4" />
                    </Button>
                  </div>
                </div>
              ))}
            </div>
          </div>

          <div
            className={cn(
              "flex flex-wrap items-center justify-between gap-3 rounded-xl border p-4",
              balanced
                ? "border-[var(--color-success-border)] bg-[var(--color-success-bg)]"
                : "border-[var(--color-warning-border)] bg-[var(--color-warning-bg)]",
            )}
          >
            <div className="flex items-center gap-2 text-sm">
              {balanced ? (
                <Check className="size-4 text-[var(--color-success)]" />
              ) : (
                <X className="size-4 text-[var(--color-warning)]" />
              )}
              <span className="font-semibold">
                {balanced ? "القيد متوازن" : `الفرق ${formatMoney(totals.diff)}`}
              </span>
            </div>
            <div className="flex items-center gap-4 text-sm">
              <span className="text-[var(--color-muted-foreground)]">
                مجموع المدين <span className="font-semibold ltr-nums">{formatMoney(totals.debit)}</span>
              </span>
              <span className="text-[var(--color-muted-foreground)]">
                مجموع الدائن <span className="font-semibold ltr-nums">{formatMoney(totals.credit)}</span>
              </span>
            </div>
          </div>
        </form>

        <div className="mt-4 flex justify-end gap-2 border-t border-[var(--color-border)] pt-4">
          <Button type="submit" variant="gold" form="journal-entry-form" disabled={submitting || !balanced}>
            {submitting ? "جارٍ الترحيل…" : (<><Save className="size-4" />ترحيل القيد</>)}
          </Button>
          <Button type="button" variant="ghost" onClick={() => setDialogOpen(false)} disabled={submitting}>
            إلغاء
          </Button>
        </div>
      </AccountingDialog>
    </div>
  );
}
