"use client";

import { useCallback, useRef, useState } from "react";
import { FileUp, Loader2, ScanLine } from "lucide-react";

import { ApiErrorAlert } from "@/components/shared/ApiErrorAlert";
import { Button } from "@/components/ui/button";
import { delegationIntakeApi } from "@/lib/api/delegation-intake";
import { ocrApi } from "@/lib/api/ocr";
import { ApiRequestError } from "@/lib/api/client";
import type { DelegationIntakeDto } from "@/lib/api/delegation-intake";
import { cn } from "@/lib/utils/cn";
import type { OcrRecordDto } from "@/types/ocr";

type Props = {
  disabled?: boolean;
  onParsed: (intake: DelegationIntakeDto, record: OcrRecordDto) => void;
};

const POLL_MS = 3000;
const MAX_POLLS = 40;

export function DelegationOcrIntakePanel({ disabled, onParsed }: Props) {
  const inputRef = useRef<HTMLInputElement | null>(null);
  const [dragOver, setDragOver] = useState(false);
  const [file, setFile] = useState<File | null>(null);
  const [phase, setPhase] = useState<"idle" | "uploading" | "processing" | "done" | "error">("idle");
  const [error, setError] = useState<string | null>(null);
  const [excerpt, setExcerpt] = useState<string | null>(null);

  const pollUntilReady = useCallback(async (recordId: string): Promise<OcrRecordDto> => {
    for (let i = 0; i < MAX_POLLS; i++) {
      const record = await ocrApi.getById(recordId);
      if (record.status === "COMPLETED") return record;
      if (record.status === "FAILED" || record.status === "CANCELLED") {
        throw new ApiRequestError({
          timestamp: new Date().toISOString(),
          message: record.errorMessage ?? "فشلت عملية OCR",
          status: 409,
          code: "OCR_FAILED",
          path: `/ocr/${recordId}`,
        });
      }
      await new Promise((r) => setTimeout(r, POLL_MS));
    }
    throw new Error("انتهت مهلة انتظار OCR — حاول مرة أخرى");
  }, []);

  async function handleScan() {
    if (!file || disabled) return;
    setPhase("uploading");
    setError(null);
    setExcerpt(null);
    try {
      const uploaded = await delegationIntakeApi.upload(file, file.name.replace(/\.[^.]+$/, ""));
      setPhase("processing");
      const ready = await pollUntilReady(uploaded.id);
      const intake = await delegationIntakeApi.parseFromOcr(uploaded.id);
      setExcerpt(intake.textExcerpt);
      setPhase("done");
      onParsed(intake, ready);
    } catch (e) {
      setPhase("error");
      setError(e instanceof ApiRequestError ? e.message : "تعذّر مسح المستند");
    }
  }

  return (
    <div className="space-y-4">
      <div
        className={cn(
          "rounded-2xl border-2 border-dashed p-6 transition-colors",
          dragOver
            ? "border-[var(--color-gold-400)] bg-[var(--color-gold-50)]/60"
            : "border-[var(--color-border)] bg-[var(--color-surface-muted)]/25",
        )}
        onDragOver={(e) => {
          e.preventDefault();
          if (!disabled && phase === "idle") setDragOver(true);
        }}
        onDragLeave={() => setDragOver(false)}
        onDrop={(e) => {
          e.preventDefault();
          setDragOver(false);
          if (disabled || phase !== "idle") return;
          const dropped = e.dataTransfer.files?.[0] ?? null;
          if (dropped) setFile(dropped);
        }}
      >
        <input
          ref={inputRef}
          type="file"
          accept=".pdf,.png,.jpg,.jpeg"
          className="hidden"
          onChange={(e) => setFile(e.target.files?.[0] ?? null)}
        />
        <div className="flex flex-col items-center gap-3 text-center">
          <div className="flex size-12 items-center justify-center rounded-2xl bg-[var(--color-navy-900)]/10 text-[var(--color-navy-800)]">
            {phase === "uploading" || phase === "processing" ? (
              <Loader2 className="size-6 animate-spin" aria-hidden />
            ) : (
              <FileUp className="size-6" aria-hidden />
            )}
          </div>
          <div>
            <p className="font-medium text-[var(--color-foreground)]">
              {file ? file.name : "اسحب ملف التوكيل هنا أو اختر ملفاً"}
            </p>
            <p className="mt-1 text-sm text-[var(--color-muted-foreground)]">
              PDF أو صورة — يُستخرج النص تلقائياً لتعبئة الحقول
            </p>
          </div>
          {phase === "idle" ? (
            <div className="flex flex-wrap justify-center gap-2">
              <Button
                type="button"
                variant="outline"
                disabled={disabled}
                onClick={() => inputRef.current?.click()}
              >
                اختيار ملف
              </Button>
              <Button
                type="button"
                disabled={!file || disabled}
                onClick={() => void handleScan()}
              >
                <ScanLine className="ms-1 size-4" aria-hidden />
                بدء المسح
              </Button>
            </div>
          ) : null}
          {phase === "processing" ? (
            <p className="text-sm text-[var(--color-muted-foreground)]">جاري استخراج النص من المستند…</p>
          ) : null}
          {phase === "done" ? (
            <p className="text-sm text-[var(--color-success)]">تم استخراج البيانات — راجع الحقول أدناه ثم احفظ</p>
          ) : null}
        </div>
      </div>

      {error ? <ApiErrorAlert message={error} /> : null}

      {excerpt ? (
        <div className="rounded-xl border border-[var(--color-border)] bg-[var(--color-surface)] p-4">
          <p className="mb-2 text-sm font-medium text-[var(--color-foreground)]">معاينة النص المستخرج</p>
          <pre className="max-h-40 overflow-auto whitespace-pre-wrap text-xs leading-relaxed text-[var(--color-muted-foreground)]">
            {excerpt}
          </pre>
        </div>
      ) : null}
    </div>
  );
}
