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
import { circulationApi } from "@/lib/api/circulation";
import { staffApi } from "@/lib/api/staff";
import { ApiRequestError } from "@/lib/api/client";
import type { CaseResponseDto } from "@/types/api";
import type { Staff } from "@/types/staff";

export default function NewCirculationPage() {
  return (
    <Suspense fallback={null}>
      <NewCirculationForm />
    </Suspense>
  );
}

function NewCirculationForm() {
  const router = useRouter();
  const params = useSearchParams();
  const [cases, setCases] = useState<CaseResponseDto[]>([]);
  const [staff, setStaff] = useState<Staff[]>([]);
  const [caseId, setCaseId] = useState(params.get("caseId") ?? "");
  const [fromPersonId, setFromPersonId] = useState("");
  const [toPersonId, setToPersonId] = useState("");
  const [dueBackAt, setDueBackAt] = useState("");
  const [reason, setReason] = useState("");
  const [notes, setNotes] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  useEffect(() => {
    void Promise.all([
      casesApi.list({ size: 100 }),
      ...(["ADMIN", "SENIOR_LAWYER", "LAWYER", "SECRETARY"] as const).map((role) => staffApi.list({ page: 0, size: 200, role })),
    ]).then(([casePage, ...staffPages]) => {
      setCases(casePage.content.filter((c) => !c.archivedAt));
      const byId = new Map<string, Staff>();
      staffPages.flatMap((p) => p.content).filter((p) => p.isActive).forEach((p) => byId.set(p.id, p));
      setStaff([...byId.values()].sort((a, b) => a.fullName.localeCompare(b.fullName, "ar")));
    }).catch((e) => setError(e instanceof ApiRequestError ? e.message : "تعذر تحميل بيانات الاختيار"));
  }, []);
  async function submit(event: React.FormEvent) {
    event.preventDefault(); setSaving(true);
    try { await circulationApi.create({ caseId, fromPersonId, toPersonId, dueBackAt: dueBackAt || null, reason, notes: notes || null }); router.push("/requests"); }
    catch (e) { setError(e instanceof ApiRequestError ? e.message : "تعذر إنشاء الطلب"); }
    finally { setSaving(false); }
  }
  return <div className="mx-auto max-w-3xl space-y-5"><PageHeader title="طلب تداول ملف" description="تسليم ملف القضية إلى موظف آخر." />{error ? <ApiErrorAlert message={error} /> : null}<form onSubmit={submit} className="space-y-4">
    <div className="space-y-2"><Label htmlFor="circulation-case">القضية</Label><select id="circulation-case" required value={caseId} onChange={(e) => setCaseId(e.target.value)} className="h-10 w-full rounded-xl border border-[var(--color-border)] bg-[var(--color-surface)] px-3"><option value="">اختر القضية</option>{cases.map((c) => <option key={c.id} value={c.id}>{c.caseNumber} · {c.title}</option>)}</select></div>
    <div className="grid gap-4 sm:grid-cols-2"><div className="space-y-2"><Label htmlFor="from-person">من الموظف</Label><select id="from-person" required value={fromPersonId} onChange={(e) => setFromPersonId(e.target.value)} className="h-10 w-full rounded-xl border border-[var(--color-border)] bg-[var(--color-surface)] px-3"><option value="">اختر الموظف</option>{staff.map((p) => <option key={p.id} value={p.id}>{p.fullName}</option>)}</select></div><div className="space-y-2"><Label htmlFor="to-person">إلى الموظف</Label><select id="to-person" required value={toPersonId} onChange={(e) => setToPersonId(e.target.value)} className="h-10 w-full rounded-xl border border-[var(--color-border)] bg-[var(--color-surface)] px-3"><option value="">اختر الموظف</option>{staff.filter((p) => p.id !== fromPersonId).map((p) => <option key={p.id} value={p.id}>{p.fullName}</option>)}</select></div></div>
    <div className="space-y-2"><Label htmlFor="due-back">تاريخ الإعادة المتوقع</Label><Input id="due-back" type="datetime-local" value={dueBackAt} onChange={(e) => setDueBackAt(e.target.value)} /></div>
    <div className="space-y-2"><Label htmlFor="reason">سبب التداول</Label><Input id="reason" required maxLength={500} value={reason} onChange={(e) => setReason(e.target.value)} /></div>
    <div className="space-y-2"><Label htmlFor="notes">ملاحظات</Label><Textarea id="notes" maxLength={10000} rows={4} value={notes} onChange={(e) => setNotes(e.target.value)} /></div>
    <div className="flex justify-end gap-2"><Button type="button" variant="outline" onClick={() => router.back()}>إلغاء</Button><Button disabled={saving}>{saving ? "جارٍ الإرسال..." : "إرسال الطلب"}</Button></div>
  </form></div>;
}