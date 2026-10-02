"use client";

import { useCallback, useEffect, useState } from "react";
import { LockKeyhole, Send } from "lucide-react";
import { SectionCard } from "@/components/shared/SectionCard";
import { ApiErrorAlert } from "@/components/shared/ApiErrorAlert";
import { Button } from "@/components/ui/button";
import { Textarea } from "@/components/ui/textarea";
import { casesApi } from "@/lib/api/cases";
import { ApiRequestError } from "@/lib/api/client";
import { formatDateTime } from "@/lib/utils/format-date";
import type { CaseInternalNoteDto } from "@/types/api";

export function CaseInternalNotesPanel({ caseId }: { caseId: string }) {
  const [notes, setNotes] = useState<CaseInternalNoteDto[]>([]);
  const [content, setContent] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const load = useCallback(async () => {
    try {
      const page = await casesApi.listInternalNotes(caseId);
      setNotes(page.content);
      setError(null);
    } catch (e) {
      setError(e instanceof ApiRequestError ? e.message : "تعذر تحميل الملاحظات");
    }
  }, [caseId]);

  useEffect(() => { void load(); }, [load]);

  async function addNote() {
    const value = content.trim();
    if (!value) return;
    setBusy(true);
    try {
      const note = await casesApi.addInternalNote(caseId, { content: value });
      setNotes((current) => [note, ...current]);
      setContent("");
      setError(null);
    } catch (e) {
      setError(e instanceof ApiRequestError ? e.message : "تعذر حفظ الملاحظة");
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="space-y-4">
      <SectionCard title="ملاحظة داخلية" description="مرئية لفريق المكتب فقط." className="p-4">
        <div className="space-y-3">
          <div className="flex items-center gap-2 text-xs text-[var(--color-muted-foreground)]">
            <LockKeyhole className="size-4" />
            لا تظهر هذه الملاحظات في بوابة العميل.
          </div>
          <Textarea value={content} onChange={(e) => setContent(e.target.value)} maxLength={10000} rows={4} />
          <div className="flex justify-end">
            <Button onClick={addNote} disabled={busy || !content.trim()} className="gap-2">
              <Send className="size-4" /> إضافة الملاحظة
            </Button>
          </div>
        </div>
      </SectionCard>
      {error ? <ApiErrorAlert message={error} onRetry={() => void load()} /> : null}
      <div className="divide-y divide-[var(--color-border)]">
        {notes.map((note) => (
          <article key={note.id} className="py-4 first:pt-0">
            <div className="mb-2 flex flex-wrap items-center justify-between gap-2 text-xs text-[var(--color-muted-foreground)]">
              <span className="font-semibold text-[var(--color-foreground)]">{note.author.fullName}</span>
              <time dir="ltr">{formatDateTime(note.createdAt)}</time>
            </div>
            <p className="whitespace-pre-wrap text-sm leading-7">{note.content}</p>
          </article>
        ))}
      </div>
    </div>
  );
}