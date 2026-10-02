"use client";

import { Suspense, useEffect, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { PageHeader } from "@/components/shared/PageHeader";
import { ApiErrorAlert } from "@/components/shared/ApiErrorAlert";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { casesApi } from "@/lib/api/cases";
import { memosApi } from "@/lib/api/memos";
import { ApiRequestError } from "@/lib/api/client";
import type { CaseResponseDto } from "@/types/api";
import type { MemoType } from "@/types/memo";

const TYPE_AR: Record<MemoType, string> = { DEFENSE_MEMO: "مذكرة دفاع", RESPONSE_MEMO: "مذكرة رد", LEGAL_OPINION: "رأي قانوني", GENERAL_MEMO: "مذكرة عامة" };

export default function NewMemoPage() {
  return (
    <Suspense fallback={null}>
      <NewMemoForm />
    </Suspense>
  );
}

function NewMemoForm() {
  const router = useRouter();
  const params = useSearchParams();
  const [cases, setCases] = useState<CaseResponseDto[]>([]);
  const [caseId, setCaseId] = useState(params.get("caseId") ?? "");
  const [title, setTitle] = useState("");
  const [memoType, setMemoType] = useState<MemoType>("GENERAL_MEMO");
  const [content, setContent] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  useEffect(() => { void casesApi.list({ size: 100 }).then((page) => setCases(page.content)).catch((e) => setError(e instanceof ApiRequestError ? e.message : "تعذر تحميل القضايا")); }, []);
  async function submit(event: React.FormEvent) {
    event.preventDefault();
    setSaving(true);
    try { const memo = await memosApi.create(caseId, { title, memoType, content }); router.push(`/memos/${memo.id}`); }
    catch (e) { setError(e instanceof ApiRequestError ? e.message : "تعذر حفظ المذكرة"); }
    finally { setSaving(false); }
  }
  return <div className="mx-auto max-w-4xl space-y-5"><PageHeader title="مذكرة جديدة" description="مسودة مرتبطة بملف قضية." />{error ? <ApiErrorAlert message={error} /> : null}<form onSubmit={submit} className="space-y-4">
    <div className="space-y-2"><Label htmlFor="memo-case">القضية</Label><select id="memo-case" required value={caseId} onChange={(e) => setCaseId(e.target.value)} className="h-10 w-full rounded-xl border border-[var(--color-border)] bg-[var(--color-surface)] px-3 text-sm"><option value="">اختر القضية</option>{cases.filter((c) => !c.archivedAt).map((c) => <option key={c.id} value={c.id}>{c.caseNumber} · {c.title}</option>)}</select></div>
    <div className="grid gap-4 sm:grid-cols-2"><div className="space-y-2"><Label htmlFor="memo-title">العنوان</Label><Input id="memo-title" required maxLength={250} value={title} onChange={(e) => setTitle(e.target.value)} /></div><div className="space-y-2"><Label htmlFor="memo-type">نوع المذكرة</Label><select id="memo-type" value={memoType} onChange={(e) => setMemoType(e.target.value as MemoType)} className="h-10 w-full rounded-xl border border-[var(--color-border)] bg-[var(--color-surface)] px-3 text-sm">{Object.entries(TYPE_AR).map(([v, label]) => <option key={v} value={v}>{label}</option>)}</select></div></div>
    <div className="space-y-2"><Label htmlFor="memo-content">المحتوى</Label><Textarea id="memo-content" required maxLength={100000} rows={18} value={content} onChange={(e) => setContent(e.target.value)} className="font-serif leading-8" /></div>
    <div className="flex justify-end gap-2"><Button type="button" variant="outline" onClick={() => router.back()}>إلغاء</Button><Button disabled={saving || !caseId}>{saving ? "جارٍ الحفظ..." : "حفظ كمسودة"}</Button></div>
  </form></div>;
}