"use client";

import { useState } from "react";
import { Check, Copy, Download, Loader2 } from "lucide-react";

import { Button } from "@/components/ui/button";
import { Textarea } from "@/components/ui/textarea";
import { notifyError, notifySuccess } from "@/lib/feedback";
import { ocrApi } from "@/lib/api/ocr";

type Props = {
  recordId: string;
  text: string;
  filename?: string;
  readOnly?: boolean;
  onChange?: (value: string) => void;
  showDownload?: boolean;
};

export function OcrTextPanel({
  recordId,
  text,
  filename = "ocr-text.txt",
  readOnly,
  onChange,
  showDownload = true,
}: Props) {
  const [copied, setCopied] = useState(false);
  const [downloading, setDownloading] = useState(false);

  async function handleCopy() {
    if (!text.trim()) {
      notifyError("لا يوجد نص للنسخ");
      return;
    }
    try {
      await navigator.clipboard.writeText(text);
      setCopied(true);
      notifySuccess("تم نسخ النص");
      window.setTimeout(() => setCopied(false), 2000);
    } catch {
      notifyError("تعذّر نسخ النص");
    }
  }

  async function handleDownload() {
    setDownloading(true);
    try {
      const blob = await ocrApi.downloadText(recordId);
      const url = URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = url;
      a.download = filename;
      a.click();
      URL.revokeObjectURL(url);
    } catch {
      notifyError("تعذّر تنزيل النص");
    } finally {
      setDownloading(false);
    }
  }

  return (
    <div className="space-y-3">
      <div className="flex flex-wrap gap-2">
        <Button type="button" variant="outline" size="sm" onClick={() => void handleCopy()}>
          {copied ? (
            <Check className="size-4" aria-hidden />
          ) : (
            <Copy className="size-4" aria-hidden />
          )}
          نسخ النص
        </Button>
        {showDownload ? (
          <Button
            type="button"
            variant="outline"
            size="sm"
            disabled={downloading}
            onClick={() => void handleDownload()}
          >
            {downloading ? (
              <Loader2 className="size-4 animate-spin" aria-hidden />
            ) : (
              <Download className="size-4" aria-hidden />
            )}
            تنزيل النص
          </Button>
        ) : null}
      </div>
      <Textarea
        value={text}
        readOnly={readOnly}
        onChange={(e) => onChange?.(e.target.value)}
        rows={16}
        className="min-h-[20rem] font-mono text-sm leading-relaxed"
        dir="auto"
        aria-label="النص المستخرج"
      />
    </div>
  );
}
