"use client";

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { ArrowRight, Loader2, Save } from "lucide-react";

import { useAuth } from "@/components/auth/auth-provider";
import { OcrRelationPicker } from "@/components/ocr/OcrRelationPicker";
import { OcrTextPanel } from "@/components/ocr/OcrTextPanel";
import { ApiErrorAlert } from "@/components/shared/ApiErrorAlert";
import { PageHeader } from "@/components/shared/PageHeader";
import { PageLoading } from "@/components/shared/PageLoading";
import { SectionCard } from "@/components/shared/SectionCard";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { ApiRequestError } from "@/lib/api/client";
import { ocrApi } from "@/lib/api/ocr";
import { ocrDocumentTypeLabel } from "@/lib/ocr/labels";
import { notifyApiError, notifySuccess } from "@/lib/feedback";
import { cn } from "@/lib/utils/cn";
import {
  OCR_DOCUMENT_TYPES,
  type OcrDocumentType,
  type OcrRelationInput,
} from "@/types/ocr";
import type { SystemRole } from "@/types/staff";

const selectClassName = cn(
  "flex h-10 w-full cursor-pointer appearance-none rounded-xl border border-[var(--color-border)] bg-[var(--color-surface)] px-3.5 py-2 text-sm",
  "focus-visible:ring-2 focus-visible:ring-[var(--color-gold-400)]",
);

function canEdit(role: SystemRole | null | undefined) {
  return role === "ADMIN" || role === "SENIOR_LAWYER" || role === "LAWYER";
}

export default function OcrEditPage() {
  const params = useParams<{ id: string }>();
  const id = params.id;
  const router = useRouter();
  const { user } = useAuth();
  const role = user?.systemRole as SystemRole | undefined;
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [title, setTitle] = useState("");
  const [documentType, setDocumentType] = useState<OcrDocumentType>("SCAN");
  const [editedText, setEditedText] = useState("");
  const [relations, setRelations] = useState<OcrRelationInput[]>([]);
  const [filename, setFilename] = useState("ocr.txt");

  const load = useCallback(async () => {
    setError(null);
    try {
      const record = await ocrApi.getById(id);
      setTitle(record.title);
      setDocumentType(record.documentType);
      setEditedText(record.editedText ?? record.extractedText ?? "");
      setRelations(
        record.relations.map((r) => ({
          relationType: r.relationType,
          relationId: r.relationId,
        })),
      );
      setFilename(`${record.title || "ocr"}.txt`);
    } catch (e) {
      setError(
        e instanceof ApiRequestError ? e.message : "تعذّر تحميل السجل",
      );
    } finally {
      setLoading(false);
    }
  }, [id]);

  useEffect(() => {
    void load();
  }, [load]);

  if (!canEdit(role)) {
    return (
      <div className="space-y-4">
        <PageHeader title="تعديل OCR" />
        <ApiErrorAlert message="ليس لديك صلاحية التعديل." />
        <Button asChild variant="outline">
          <Link href={`/ocr/${id}`}>العودة</Link>
        </Button>
      </div>
    );
  }

  if (loading) {
    return <PageLoading label="جارٍ تحميل…" />;
  }

  if (error) {
    return (
      <div className="space-y-4">
        <PageHeader title="تعديل OCR" />
        <ApiErrorAlert message={error} onRetry={() => void load()} />
      </div>
    );
  }

  async function handleSave(e: React.FormEvent) {
    e.preventDefault();
    setSaving(true);
    try {
      await ocrApi.update(id, {
        title: title.trim() || undefined,
        documentType,
        editedText,
        relations,
      });
      notifySuccess("تم حفظ التعديلات");
      router.push(`/ocr/${id}`);
    } catch (e) {
      notifyApiError(e, "تعذّر الحفظ");
    } finally {
      setSaving(false);
    }
  }

  return (
    <form onSubmit={(e) => void handleSave(e)} className="space-y-6">
      <PageHeader
        title="تعديل OCR"
        description="عدّل العنوان والنص والروابط."
        actions={
          <div className="flex flex-wrap gap-2">
            <Button asChild variant="outline" size="sm" type="button">
              <Link href={`/ocr/${id}`}>
                <ArrowRight className="size-4" />
                التفاصيل
              </Link>
            </Button>
            <Button type="submit" size="sm" disabled={saving}>
              {saving ? (
                <Loader2 className="size-4 animate-spin" />
              ) : (
                <Save className="size-4" />
              )}
              حفظ
            </Button>
          </div>
        }
      />

      <div className="grid gap-6 lg:grid-cols-2">
        <SectionCard title="البيانات الأساسية">
          <div className="space-y-4">
            <div className="space-y-1.5">
              <Label htmlFor="edit-title">العنوان</Label>
              <Input
                id="edit-title"
                value={title}
                onChange={(e) => setTitle(e.target.value)}
                maxLength={250}
                required
              />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="edit-type">نوع المستند</Label>
              <select
                id="edit-type"
                className={selectClassName}
                value={documentType}
                onChange={(e) =>
                  setDocumentType(e.target.value as OcrDocumentType)
                }
              >
                {OCR_DOCUMENT_TYPES.map((t) => (
                  <option key={t} value={t}>
                    {ocrDocumentTypeLabel(t)}
                  </option>
                ))}
              </select>
            </div>
          </div>
        </SectionCard>

        <SectionCard title="الروابط">
          <OcrRelationPicker value={relations} onChange={setRelations} />
        </SectionCard>
      </div>

      <SectionCard title="النص (تحرير)">
        <OcrTextPanel
          recordId={id}
          text={editedText}
          filename={filename}
          onChange={setEditedText}
          showDownload
        />
      </SectionCard>
    </form>
  );
}
