"use client";

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { useParams } from "next/navigation";
import { ArrowRight, Check, Send, ShieldCheck } from "lucide-react";
import { ApiErrorAlert } from "@/components/shared/ApiErrorAlert";
import { PageHeader } from "@/components/shared/PageHeader";
import { PageLoading } from "@/components/shared/PageLoading";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { memosApi } from "@/lib/api/memos";
import { ApiRequestError } from "@/lib/api/client";
import { getStoredUser } from "@/lib/auth/session";
import { notifyApiError, notifySuccess } from "@/lib/feedback";
import type { MemoResponse, MemoType } from "@/types/memo";

const STATUS_AR: Record<MemoResponse["status"], string> = { DRAFT: "مسودة", UNDER_REVIEW: "قيد المراجعة", APPROVED: "معتمدة", SENT: "مرسلة", ARCHIVED: "مؤرشفة" };
const TYPE_AR: Record<MemoType, string> = { DEFENSE_MEMO: "مذكرة دفاع", RESPONSE_MEMO: "مذكرة رد", LEGAL_OPINION: "رأي قانوني", GENERAL_MEMO: "مذكرة عامة" };

export default function MemoDetailPage() {
  const id = useParams<{ id: string }>().id;
  const [memo, setMemo] = useState<MemoResponse | null>(null);
  const [title, setTitle] = useState("");
  const [memoType, setMemoType] = useState<MemoType>("GENERAL_MEMO");
  const [content, setContent] = useState("");
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const canApprove = ["ADMIN", "SENIOR_LAWYER"].includes(getStoredUser()?.systemRole ?? "");
  const load = useCallback(async () => {
    setLoading(true);
    try { const result = await memosApi.get(id); setMemo(result); setTitle(result.title); setMemoType(result.memoType); setContent(result.content); setError(null); }
    catch (e) { setError(e instanceof ApiRequestError ? e.message : "تعذر تحميل المذكرة"); }
    finally { setLoading(false); }
  }, [id]);
  useEffect(() => { void load(); }, [load]);
  async function act(action: "save" | "submit" | "approve" | "send" | "archive") {
    setBusy(true);
    try {
      const result = action === "save" ? await memosApi.update(id, { title, memoType, content }) : await memosApi[action](id);
      setMemo(result); notifySuccess(action === "save" ? "حُفظت المسودة" : "تم تحديث حالة المذكرة"); await load();
    } catch (e) { notifyApiError(e, "تعذر تنفيذ الإجراء"); }
    finally { setBusy(false); }
  }
  if (loading) return <PageLoading label="جارٍ تحميل المذكرة..." />;
  if (!memo) return <ApiErrorAlert message={error ?? "المذكرة غير موجودة"} onRetry={() => void load()} />;
  const draft = memo.status === "DRAFT";
  return <div className="mx-auto max-w-4xl space-y-5">
    <Link href="/memos" className="inline-flex items-center gap-2 text-sm text-[var(--color-muted-foreground)]"><ArrowRight className="size-4" /> المذكرات</Link>
    <PageHeader title={memo.title} description={`${memo.caseNumber} · ${memo.caseTitle} · ${STATUS_AR[memo.status]}`} actions={<Button variant="outline" asChild><Link href={`/cases/${memo.caseId}?section=memos`}>ملف القضية</Link></Button>} />
    {error ? <ApiErrorAlert message={error} onRetry={() => void load()} /> : null}
    <div className="flex flex-wrap gap-2 text-sm text-[var(--color-muted-foreground)]"><span>{TYPE_AR[memo.memoType]}</span><span>·</span><span>الكاتب: {memo.author.fullName ?? "—"}</span>{memo.reviewer ? <><span>·</span><span>المراجع: {memo.reviewer.fullName ?? "—"}</span></> : null}</div>
    <div className="space-y-4">
      {draft ? <><div className="space-y-2"><Label htmlFor="memo-title">العنوان</Label><Input id="memo-title" value={title} onChange={(e) => setTitle(e.target.value)} maxLength={250} /></div><div className="space-y-2"><Label htmlFor="memo-type">النوع</Label><select id="memo-type" className="h-10 w-full rounded-xl border border-[var(--color-border)] bg-[var(--color-surface)] px-3" value={memoType} onChange={(e) => setMemoType(e.target.value as MemoType)}>{Object.entries(TYPE_AR).map(([v, label]) => <option key={v} value={v}>{label}</option>)}</select></div><div className="space-y-2"><Label htmlFor="memo-content">المحتوى</Label><Textarea id="memo-content" rows={20} maxLength={100000} value={content} onChange={(e) => setContent(e.target.value)} className="font-serif leading-8" /></div></> : <article className="whitespace-pre-wrap rounded-lg border border-[var(--color-border)] bg-[var(--color-surface)] p-6 font-serif leading-8">{memo.content}</article>}
      <div className="flex flex-wrap justify-end gap-2">
        {draft ? <><Button variant="outline" disabled={busy} onClick={() => void act("save")}>حفظ المسودة</Button><Button disabled={busy} onClick={() => void act("submit")}><Send className="size-4" /> إرسال للمراجعة</Button></> : null}
        {memo.status === "UNDER_REVIEW" && canApprove ? <Button disabled={busy} onClick={() => void act("approve")}><ShieldCheck className="size-4" /> اعتماد</Button> : null}
        {memo.status === "APPROVED" ? <Button disabled={busy} onClick={() => void act("send")}><Send className="size-4" /> إرسال</Button> : null}
        {memo.status !== "ARCHIVED" && canApprove ? <Button variant="outline" disabled={busy} onClick={() => void act("archive")}><Check className="size-4" /> أرشفة</Button> : null}
      </div>
    </div>
  </div>;
}