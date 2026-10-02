"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { Download, FileUp, MoreVertical, Trash2, Upload } from "lucide-react";

import { ApiErrorAlert } from "@/components/shared/ApiErrorAlert";
import { EmptyState } from "@/components/shared/EmptyState";
import { PageLoading } from "@/components/shared/PageLoading";
import { SectionCard } from "@/components/shared/SectionCard";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { delegationDocumentsApi } from "@/lib/api/delegation-documents";
import { ApiRequestError } from "@/lib/api/client";
import { getStoredUser } from "@/lib/auth/session";
import { notifyApiError, notifyError, notifySuccess } from "@/lib/feedback";
import { DELEGATION_DOCUMENT_TYPE_AR } from "@/lib/delegations/labels";
import { cn } from "@/lib/utils/cn";
import { formatDateTime } from "@/lib/utils/format-date";
import type { DelegationDocumentDto, DelegationDocumentTypeDto } from "@/types/delegation";

type Props = {
  delegationId: string;
  canUpload?: boolean;
  compact?: boolean;
  defaultDocumentType?: DelegationDocumentTypeDto;
};

function formatBytes(size: number) {
  if (size < 1024) return `${size} B`;
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`;
  return `${(size / 1024 / 1024).toFixed(1)} MB`;
}

export function DelegationDocumentsPanel({
  delegationId,
  canUpload = true,
  compact = false,
  defaultDocumentType = "OTHER",
}: Props) {
  const inputRef = useRef<HTMLInputElement | null>(null);
  const [items, setItems] = useState<DelegationDocumentDto[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [file, setFile] = useState<File | null>(null);
  const [description, setDescription] = useState("");
  const [documentType, setDocumentType] = useState<DelegationDocumentTypeDto>(defaultDocumentType);
  const [saving, setSaving] = useState(false);
  const [downloadingId, setDownloadingId] = useState<string | null>(null);
  const [dragOver, setDragOver] = useState(false);
  const [user, setUser] = useState<ReturnType<typeof getStoredUser>>();

  useEffect(() => {
    setUser(getStoredUser());
  }, []);

  useEffect(() => {
    setDocumentType(defaultDocumentType);
  }, [defaultDocumentType]);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setItems(await delegationDocumentsApi.list(delegationId));
    } catch (e) {
      setError(e instanceof ApiRequestError ? e.message : "تعذّر تحميل المرفقات");
    } finally {
      setLoading(false);
    }
  }, [delegationId]);

  useEffect(() => {
    void load();
  }, [load]);

  const canDelete = Boolean(
    user && (user.systemRole === "ADMIN" || user.systemRole === "SENIOR_LAWYER"),
  );

  function pickFile(next: File | null) {
    setFile(next);
  }

  async function handleUpload(selected?: File | null) {
    const toUpload = selected ?? file;
    if (!toUpload) {
      notifyError("اختر ملفاً للرفع");
      return;
    }
    setSaving(true);
    try {
      await delegationDocumentsApi.upload(delegationId, toUpload, description, documentType);
      notifySuccess("تم رفع الملف");
      setFile(null);
      setDescription("");
      setDocumentType(defaultDocumentType);
      if (inputRef.current) inputRef.current.value = "";
      await load();
    } catch (e) {
      notifyApiError(e, "تعذّر رفع الملف");
    } finally {
      setSaving(false);
    }
  }

  async function handleDownload(doc: DelegationDocumentDto) {
    setDownloadingId(doc.id);
    try {
      const blob = await delegationDocumentsApi.download(delegationId, doc.id);
      const url = URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = url;
      a.download = doc.originalFilename;
      a.click();
      URL.revokeObjectURL(url);
    } catch (e) {
      notifyApiError(e, "تعذّر تنزيل الملف");
    } finally {
      setDownloadingId(null);
    }
  }

  async function handleDelete(doc: DelegationDocumentDto) {
    if (!window.confirm(`حذف «${doc.originalFilename}»؟`)) return;
    try {
      await delegationDocumentsApi.remove(delegationId, doc.id);
      notifySuccess("تم حذف الملف");
      await load();
    } catch (e) {
      notifyApiError(e, "تعذّر حذف الملف");
    }
  }

  const uploadZone = canUpload ?
    <div
      className={cn(
        "mb-5 rounded-2xl border-2 border-dashed p-5 transition-colors",
        dragOver ?
          "border-[var(--color-gold-400)] bg-[var(--color-gold-50)]/60"
        : "border-[var(--color-border)] bg-[var(--color-surface-muted)]/25",
      )}
      onDragOver={(e) => {
        e.preventDefault();
        setDragOver(true);
      }}
      onDragLeave={() => setDragOver(false)}
      onDrop={(e) => {
        e.preventDefault();
        setDragOver(false);
        const dropped = e.dataTransfer.files?.[0] ?? null;
        if (dropped) {
          pickFile(dropped);
        }
      }}
    >
      <input
        ref={inputRef}
        type="file"
        className="sr-only"
        id={`delegation-file-${delegationId}`}
        onChange={(e) => pickFile(e.target.files?.[0] ?? null)}
      />
      <div className="flex flex-col items-center text-center">
        <div className="mb-3 flex size-12 items-center justify-center rounded-2xl bg-[var(--color-gold-50)] text-[var(--color-gold-600)]">
          <FileUp className="size-6" aria-hidden />
        </div>
        <p className="text-sm font-medium">اسحب الملف هنا أو اختر من جهازك</p>
        <p className="mt-1 text-xs text-[var(--color-muted-foreground)]">PDF، Word، أو صور — حتى 10 م.ب</p>
        <div className="mt-3 flex flex-wrap justify-center gap-2">
          <Button
            type="button"
            variant="outline"
            size="sm"
            onClick={() => inputRef.current?.click()}
          >
            اختيار ملف
          </Button>
          {file ?
            <span className="self-center text-xs text-[var(--color-muted-foreground)]">{file.name}</span>
          : null}
        </div>
      </div>
      {!compact ?
        <div className="mt-4 space-y-2 border-t border-[var(--color-border)]/60 pt-4">
          <Label htmlFor={`delegation-doc-type-${delegationId}`}>نوع المستند</Label>
          <select
            id={`delegation-doc-type-${delegationId}`}
            className="h-10 w-full cursor-pointer appearance-none rounded-lg border border-[var(--color-border)] bg-[var(--color-surface)] px-3 text-sm"
            value={documentType}
            onChange={(e) => setDocumentType(e.target.value as DelegationDocumentTypeDto)}
          >
            {(Object.entries(DELEGATION_DOCUMENT_TYPE_AR) as [DelegationDocumentTypeDto, string][]).map(
              ([k, v]) => (
                <option key={k} value={k}>{v}</option>
              ),
            )}
          </select>
          <Label htmlFor={`delegation-file-desc-${delegationId}`}>وصف (اختياري)</Label>
          <Textarea
            id={`delegation-file-desc-${delegationId}`}
            rows={2}
            value={description}
            onChange={(e) => setDescription(e.target.value)}
          />
        </div>
      : (
        <div className="mt-4 space-y-2 border-t border-[var(--color-border)]/60 pt-4">
          <Label htmlFor={`delegation-doc-type-${delegationId}`}>نوع المستند</Label>
          <select
            id={`delegation-doc-type-${delegationId}`}
            className="h-10 w-full cursor-pointer appearance-none rounded-lg border border-[var(--color-border)] bg-[var(--color-surface)] px-3 text-sm"
            value={documentType}
            onChange={(e) => setDocumentType(e.target.value as DelegationDocumentTypeDto)}
          >
            {(Object.entries(DELEGATION_DOCUMENT_TYPE_AR) as [DelegationDocumentTypeDto, string][]).map(
              ([k, v]) => (
                <option key={k} value={k}>{v}</option>
              ),
            )}
          </select>
        </div>
      )}
      <Button
        type="button"
        className="mt-4 w-full gap-2 sm:w-auto"
        disabled={!file || saving}
        onClick={() => void handleUpload()}
      >
        <Upload className="size-4" aria-hidden />
        {saving ? "جاري الرفع..." : "رفع الملف"}
      </Button>
    </div>
  : null;

  const fileList = loading ?
    <PageLoading label="جاري تحميل المرفقات..." minHeightClassName="min-h-[8rem]" />
  : items.length === 0 ?
    <EmptyState title="لا توجد مرفقات" description="ارفع نسخة من التوكيل أو المستندات الداعمة." />
  : <ul className="divide-y divide-[var(--color-border)] rounded-xl border border-[var(--color-border)]">
      {items.map((doc) => (
        <li key={doc.id} className="flex flex-wrap items-center justify-between gap-3 px-3 py-3">
          <div className="min-w-0">
            <div className="flex flex-wrap items-center gap-2">
              <p className="truncate text-sm font-medium">{doc.originalFilename}</p>
              <Badge tone="neutral" className="shrink-0 text-xs">
                {DELEGATION_DOCUMENT_TYPE_AR[doc.documentType]}
              </Badge>
            </div>
            <p className="text-xs text-[var(--color-muted-foreground)] ltr-nums">
              {formatBytes(doc.sizeBytes)} · {formatDateTime(doc.createdAt)}
              {doc.uploadedBy?.fullName ? ` · ${doc.uploadedBy.fullName}` : ""}
            </p>
            {doc.description && !compact ?
              <p className="mt-1 text-xs text-[var(--color-muted-foreground)]">{doc.description}</p>
            : null}
          </div>
          <DropdownMenu>
            <DropdownMenuTrigger asChild>
              <Button type="button" variant="ghost" size="icon" className="size-8" aria-label="إجراءات الملف">
                <MoreVertical className="size-4" />
              </Button>
            </DropdownMenuTrigger>
            <DropdownMenuContent align="end">
              <DropdownMenuItem
                disabled={downloadingId === doc.id}
                onClick={() => void handleDownload(doc)}
                className="gap-2"
              >
                <Download className="size-4" />
                {downloadingId === doc.id ? "جاري التنزيل..." : "تنزيل"}
              </DropdownMenuItem>
              {canDelete ?
                <>
                  <DropdownMenuSeparator />
                  <DropdownMenuItem
                    className="gap-2 text-[var(--color-danger)]"
                    onClick={() => void handleDelete(doc)}
                  >
                    <Trash2 className="size-4" />
                    حذف
                  </DropdownMenuItem>
                </>
              : null}
            </DropdownMenuContent>
          </DropdownMenu>
        </li>
      ))}
    </ul>;

  return (
    <SectionCard title="المرفقات" contentClassName="space-y-0">
      {error ? <ApiErrorAlert message={error} className="mb-4" /> : null}
      {uploadZone}
      {fileList}
    </SectionCard>
  );
}
