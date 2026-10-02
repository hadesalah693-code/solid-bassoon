"use client";

import { useRef, useState } from "react";
import { FileUp, Loader2, Upload } from "lucide-react";

import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { cn } from "@/lib/utils/cn";
import {
  OCR_DOCUMENT_TYPES,
  type OcrDocumentType,
  type OcrRelationInput,
} from "@/types/ocr";
import { ocrDocumentTypeLabel } from "@/lib/ocr/labels";
import { OcrRelationPicker } from "@/components/ocr/OcrRelationPicker";

const selectClassName = cn(
  "flex h-10 w-full cursor-pointer appearance-none rounded-xl border border-[var(--color-border)] bg-[var(--color-surface)] px-3.5 py-2 text-sm",
  "focus-visible:ring-2 focus-visible:ring-[var(--color-gold-400)]",
);

type Props = {
  disabled?: boolean;
  uploading?: boolean;
  onUpload: (payload: {
    file: File;
    title: string;
    documentType: OcrDocumentType;
    relations: OcrRelationInput[];
  }) => void | Promise<void>;
};

export function OcrDropzoneUploader({ disabled, uploading, onUpload }: Props) {
  const inputRef = useRef<HTMLInputElement | null>(null);
  const [dragOver, setDragOver] = useState(false);
  const [file, setFile] = useState<File | null>(null);
  const [title, setTitle] = useState("");
  const [documentType, setDocumentType] = useState<OcrDocumentType>("SCAN");
  const [relations, setRelations] = useState<OcrRelationInput[]>([]);

  function pickFile(next: File | null) {
    setFile(next);
    if (next && !title.trim()) {
      const base = next.name.replace(/\.[^.]+$/, "");
      setTitle(base);
    }
  }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!file || disabled || uploading) return;
    await onUpload({
      file,
      title: title.trim() || file.name,
      documentType,
      relations,
    });
  }

  return (
    <form onSubmit={(e) => void handleSubmit(e)} className="space-y-5">
      <div
        className={cn(
          "rounded-2xl border-2 border-dashed p-6 transition-colors",
          dragOver
            ? "border-[var(--color-gold-400)] bg-[var(--color-gold-50)]/60"
            : "border-[var(--color-border)] bg-[var(--color-surface-muted)]/25",
        )}
        onDragOver={(e) => {
          e.preventDefault();
          if (!disabled && !uploading) setDragOver(true);
        }}
        onDragLeave={() => setDragOver(false)}
        onDrop={(e) => {
          e.preventDefault();
          setDragOver(false);
          if (disabled || uploading) return;
          const dropped = e.dataTransfer.files?.[0] ?? null;
          if (dropped) pickFile(dropped);
        }}
      >
        <input
          ref={inputRef}
          type="file"
          accept=".pdf,.png,.jpg,.jpeg,application/pdf,image/png,image/jpeg"
          className="sr-only"
          id="ocr-file-input"
          disabled={disabled || uploading}
          onChange={(e) => pickFile(e.target.files?.[0] ?? null)}
        />
        <div className="flex flex-col items-center text-center">
          <div className="mb-3 flex size-12 items-center justify-center rounded-2xl bg-[var(--color-gold-50)] text-[var(--color-gold-600)]">
            <FileUp className="size-6" aria-hidden />
          </div>
          <p className="text-sm font-medium">اسحب الملف هنا أو اختر من جهازك</p>
          <p className="mt-1 text-xs text-[var(--color-muted-foreground)]">
            PDF أو صور (JPG, PNG) — حتى 25 م.ب
          </p>
          <div className="mt-3 flex flex-wrap justify-center gap-2">
            <Button
              type="button"
              variant="outline"
              size="sm"
              disabled={disabled || uploading}
              onClick={() => inputRef.current?.click()}
            >
              اختيار ملف
            </Button>
            {file ? (
              <span className="self-center text-xs text-[var(--color-muted-foreground)]">
                {file.name}
              </span>
            ) : null}
          </div>
        </div>
      </div>

      <div className="grid gap-4 md:grid-cols-2">
        <div className="space-y-1.5">
          <Label htmlFor="ocr-title">العنوان</Label>
          <Input
            id="ocr-title"
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            placeholder="عنوان المستند"
            disabled={disabled || uploading}
            maxLength={250}
          />
        </div>
        <div className="space-y-1.5">
          <Label htmlFor="ocr-doc-type">نوع المستند</Label>
          <select
            id="ocr-doc-type"
            className={selectClassName}
            value={documentType}
            disabled={disabled || uploading}
            onChange={(e) => setDocumentType(e.target.value as OcrDocumentType)}
          >
            {OCR_DOCUMENT_TYPES.map((t) => (
              <option key={t} value={t}>
                {ocrDocumentTypeLabel(t)}
              </option>
            ))}
          </select>
        </div>
      </div>

      <OcrRelationPicker
        value={relations}
        onChange={setRelations}
        disabled={disabled || uploading}
      />

      <div className="flex flex-wrap items-center gap-3">
        <Button type="submit" disabled={!file || disabled || uploading}>
          {uploading ? (
            <>
              <Loader2 className="size-4 animate-spin" aria-hidden />
              جارٍ الرفع…
            </>
          ) : (
            <>
              <Upload className="size-4" aria-hidden />
              رفع وبدء OCR
            </>
          )}
        </Button>
        {uploading ? (
          <p className="text-xs text-[var(--color-muted-foreground)]" role="status">
            يتم رفع الملف وإرساله للمعالجة…
          </p>
        ) : null}
      </div>
    </form>
  );
}
