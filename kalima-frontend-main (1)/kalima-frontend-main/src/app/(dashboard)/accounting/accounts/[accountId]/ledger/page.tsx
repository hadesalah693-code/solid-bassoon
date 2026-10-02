"use client";

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { ArrowRight, BookOpenCheck } from "lucide-react";
import { useParams } from "next/navigation";
import { AccountingRoleGate } from "@/components/accounting/AccountingRoleGate";
import { DisputeModuleHero } from "@/components/disputes/DisputeModuleHero";
import { ApiErrorAlert } from "@/components/shared/ApiErrorAlert";
import { PageLoading } from "@/components/shared/PageLoading";
import { Pagination } from "@/components/shared/Pagination";
import { SectionCard } from "@/components/shared/SectionCard";
import { Input } from "@/components/ui/input";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { reportsApi } from "@/lib/api/accounting";
import { ApiRequestError } from "@/lib/api/client";
import { formatDate } from "@/lib/utils/format-date";
import { formatMoney } from "@/lib/utils/format-money";
import type { AccountLedgerResponseDto } from "@/types/accounting";

const PAGE_SIZE = 50;
function currentRange() {
  const now = new Date();
  return { from: `${now.getFullYear()}-01-01`, to: `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, "0")}-${String(now.getDate()).padStart(2, "0")}` };
}

function AccountLedgerInner() {
  const accountId = useParams<{ accountId: string }>().accountId;
  const initial = currentRange();
  const [from, setFrom] = useState(initial.from);
  const [to, setTo] = useState(initial.to);
  const [page, setPage] = useState(0);
  const [ledger, setLedger] = useState<AccountLedgerResponseDto | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [retry, setRetry] = useState(0);
  const load = useCallback(async () => {
    setLoading(true);
    try {
      const result = await reportsApi.accountLedger(accountId, { from, to, page, size: PAGE_SIZE });
      setLedger(result); setError(null);
    } catch (e) { setError(e instanceof ApiRequestError ? e.message : "تعذر تحميل دفتر الحساب"); setLedger(null); }
    finally { setLoading(false); }
  }, [accountId, from, to, page]);
  useEffect(() => { void load(); }, [load, retry]);
  return <div dir="rtl" className="space-y-5 pb-10">
    <Link href="/accounting/accounts" className="inline-flex items-center gap-2 text-sm text-[var(--color-muted-foreground)]"><ArrowRight className="size-4" /> دليل الحسابات</Link>
    <DisputeModuleHero icon={BookOpenCheck} eyebrow="المالية والمحاسبة" title={ledger ? `${ledger.accountCode} — ${ledger.accountName}` : "دفتر الحساب"} description="حركة الحساب من دفتر اليومية، مع رصيد افتتاحي وختامي للفترة المحددة." />
    <SectionCard title="الفترة" contentClassName="flex flex-wrap items-end gap-3">
      <label className="space-y-1 text-xs">من تاريخ<Input type="date" value={from} onChange={(e) => { setFrom(e.target.value); setPage(0); }} /></label>
      <label className="space-y-1 text-xs">إلى تاريخ<Input type="date" value={to} onChange={(e) => { setTo(e.target.value); setPage(0); }} /></label>
    </SectionCard>
    {error ? <ApiErrorAlert message={error} onRetry={() => setRetry((v) => v + 1)} /> : null}
    {loading ? <PageLoading label="جارٍ تحميل دفتر الحساب..." /> : ledger ? <>
      <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
        <SectionCard title="الرصيد الافتتاحي"><b className="ltr-nums">{formatMoney(ledger.openingBalance)}</b></SectionCard>
        <SectionCard title="إجمالي المدين"><b className="ltr-nums">{formatMoney(ledger.periodDebit)}</b></SectionCard>
        <SectionCard title="إجمالي الدائن"><b className="ltr-nums">{formatMoney(ledger.periodCredit)}</b></SectionCard>
        <SectionCard title="الرصيد الختامي"><b className="ltr-nums">{formatMoney(ledger.closingBalance)}</b></SectionCard>
      </div>
      <SectionCard flush title="حركات اليومية">
        {ledger.entries.content.length === 0 ? <p className="p-5 text-sm text-[var(--color-muted-foreground)]">لا توجد حركة خلال الفترة.</p> : <div className="overflow-x-auto"><Table>
          <TableHeader><TableRow><TableHead>التاريخ</TableHead><TableHead>رقم القيد</TableHead><TableHead>البيان</TableHead><TableHead>المرجع</TableHead><TableHead>مدين</TableHead><TableHead>دائن</TableHead></TableRow></TableHeader>
          <TableBody>{ledger.entries.content.map((row, index) => <TableRow key={`${row.entryNumber}-${index}`}><TableCell className="whitespace-nowrap">{formatDate(row.date)}</TableCell><TableCell className="ltr-nums">{row.entryNumber}</TableCell><TableCell>{row.description}</TableCell><TableCell className="text-xs text-[var(--color-muted-foreground)]">{row.referenceType ?? "—"}</TableCell><TableCell className="ltr-nums">{formatMoney(row.debit)}</TableCell><TableCell className="ltr-nums">{formatMoney(row.credit)}</TableCell></TableRow>)}</TableBody>
        </Table></div>}
        <div className="border-t border-[var(--color-border)] p-3"><Pagination page={page} pageSize={PAGE_SIZE} total={ledger.entries.totalElements} onPageChange={setPage} /></div>
      </SectionCard>
    </> : null}
  </div>;
}

export default function AccountLedgerPage() {
  return <AccountingRoleGate><AccountLedgerInner /></AccountingRoleGate>;
}
