"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { Download, Eye, FileText, Trash2, Upload } from "lucide-react";

import { ApiErrorAlert } from "@/components/shared/ApiErrorAlert";
import { EmptyState } from "@/components/shared/EmptyState";
import { PageLoading } from "@/components/shared/PageLoading";
import { SectionCard } from "@/components/shared/SectionCard";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { ApiRequestError } from "@/lib/api/client";
import { caseDocumentsApi } from "@/lib/api/case-documents";
import { getStoredUser } from "@/lib/auth/session";
import { notifyApiError, notifyError, notifySuccess } from "@/lib/feedback";
import { cn } from "@/lib/utils/cn";
import { formatDateTime } from "@/lib/utils/format-date";
import type {
  CaseDocumentCategoryDto,
  CaseDocumentResponseDto,
  CaseDocumentVisibilityDto,
} from "@/types/api";

const selectClassName = cn(
  "flex h-10 w-full cursor-pointer appearance-none rounded-xl border border-[var(--color-border)] bg-[var(--color-surface)] px-3.5 py-2 text-sm text-[var(--color-foreground)] shadow-sm",
  "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--color-gold-400)]",
  "disabled:cursor-not-allowed disabled:opacity-50",
);

const CATEGORY_LABELS: Record<CaseDocumentCategoryDto, string> = {
  POWER_OF_ATTORNEY: "توكيل",
  CONTRACT: "عقد",
  COURT_FILING: "مذكرة / صحيفة",
  JUDGMENT: "حكم",
  EVIDENCE: "مستند إثبات",
  RECEIPT: "إيصال",
  OTHER: "أخرى",
};

const CATEGORY_OPTIONS = Object.entries(CATEGORY_LABELS).map(([value, label]) => ({
  value: value as CaseDocumentCategoryDto,
  label,
}));

const VISIBILITY_LABELS: Record<CaseDocumentVisibilityDto, string> = {
  STAFF_ONLY: "داخلي فقط",
  CLIENT_VISIBLE: "ظاهر للعميل",
};

function formatBytes(size: number) {
  if (size < 1024) return `${size} B`;
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`;
  return `${(size / 1024 / 1024).toFixed(1)} MB`;
}

type Props = {
  caseId: string;
  canUpload: boolean;
  onChange?: () => void;
};

export function CaseDocumentsPanel({ caseId, canUpload, onChange }: Props) {
  const inputRef = useRef<HTMLInputElement | null>(null);
  const [items, setItems] = useState<CaseDocumentResponseDto[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [reloadKey, setReloadKey] = useState(0);
  const [file, setFile] = useState<File | null>(null);
  const [category, setCategory] = useState<CaseDocumentCategoryDto>("OTHER");
  const [visibility, setVisibility] =
    useState<CaseDocumentVisibilityDto>("STAFF_ONLY");
  const [description, setDescription] = useState("");
  const [saving, setSaving] = useState(false);
  const [downloadingId, setDownloadingId] = useState<string | null>(null);
  const [viewingId, setViewingId] = useState<string | null>(null);
  const [user, setUser] = useState<ReturnType<typeof getStoredUser>>();

  useEffect(() => {
    setUser(getStoredUser());
  }, []);

  const canDelete = Boolean(
    user && (user.systemRole === "ADMIN" || user.systemRole === "SENIOR_LAWYER"),
  );

  const refresh = useCallback(() => {
    setReloadKey((n) => n + 1);
  }, []);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      setLoading(true);
      setError(null);
      try {
        const docs = await caseDocumentsApi.list(caseId);
        if (cancelled) return;
        setItems(docs);
      } catch (e) {
        if (cancelled) return;
        if (e instanceof ApiRequestError) {
          setError(e.details?.message ?? e.message);
        } else {
          setError("تعذر تحميل مستندات القضية");
        }
        setItems([]);
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [caseId, reloadKey]);

  async function upload() {
    if (!file) {
      notifyError("اختر ملفا أولا");
      return;
    }
    setSaving(true);
    try {
      await caseDocumentsApi.upload(caseId, {
        file,
        category,
        visibility,
        description,
      });
      setFile(null);
      setDescription("");
      if (inputRef.current) inputRef.current.value = "";
      notifySuccess("تم رفع المستند");
      refresh();
      onChange?.();
    } catch (e) {
      notifyApiError(e, "تعذر رفع المستند");
    } finally {
      setSaving(false);
    }
  }

  async function download(doc: CaseDocumentResponseDto) {
    setDownloadingId(doc.id);
    try {
      const blob = await caseDocumentsApi.download(caseId, doc.id);
      const url = URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = url;
      a.download = doc.originalFilename;
      document.body.appendChild(a);
      a.click();
      a.remove();
      URL.revokeObjectURL(url);
    } catch (e) {
      notifyApiError(e, "تعذر تحميل المستند");
    } finally {
      setDownloadingId(null);
    }
  }

  async function viewFile(doc: CaseDocumentResponseDto) {
    setViewingId(doc.id);
    try {
      const blob = await caseDocumentsApi.download(caseId, doc.id);
      const url = URL.createObjectURL(blob);
      window.open(url, "_blank");
    } catch (e) {
      notifyApiError(e, "تعذر عرض المستند");
    } finally {
      setViewingId(null);
    }
  }

  async function remove(doc: CaseDocumentResponseDto) {
    if (!canDelete) return;
    if (!window.confirm("حذف هذا المستند من القضية؟")) return;
    try {
      await caseDocumentsApi.remove(caseId, doc.id);
      notifySuccess("تم حذف المستند");
      refresh();
      onChange?.();
    } catch (e) {
      notifyApiError(e, "تعذر حذف المستند");
    }
  }

  if (error) {
    return (
      <ApiErrorAlert
        message={error}
        title="تعذر تحميل المستندات"
        onRetry={() => {
          setError(null);
          refresh();
        }}
      />
    );
  }

  return (
    <SectionCard
      title="مستندات القضية"
      description="ملفات مرتبطة بالقضية مع تصنيف ورؤية منفصلة للعميل."
      action={
        <div className="flex size-10 items-center justify-center rounded-xl bg-[var(--color-navy-50)] text-[var(--color-navy-700)]">
          <FileText className="size-5" />
        </div>
      }
    >
      {canUpload ? (
        <div className="mb-5 space-y-3 rounded-xl border border-[var(--color-border)] bg-[var(--color-surface-muted)]/30 p-4">
          <div className="grid gap-3 lg:grid-cols-[1.4fr_0.8fr_0.8fr]">
            <div className="space-y-1">
              <Label>الملف *</Label>
              <Input
                ref={inputRef}
                type="file"
                disabled={saving}
                onChange={(e) => setFile(e.target.files?.[0] ?? null)}
              />
            </div>
            <div className="space-y-1">
              <Label>التصنيف</Label>
              <select
                className={selectClassName}
                value={category}
                disabled={saving}
                onChange={(e) =>
                  setCategory(e.target.value as CaseDocumentCategoryDto)
                }
              >
                {CATEGORY_OPTIONS.map((o) => (
                  <option key={o.value} value={o.value}>
                    {o.label}
                  </option>
                ))}
              </select>
            </div>
            <div className="space-y-1">
              <Label>الرؤية</Label>
              <select
                className={selectClassName}
                value={visibility}
                disabled={saving}
                onChange={(e) =>
                  setVisibility(e.target.value as CaseDocumentVisibilityDto)
                }
              >
                <option value="STAFF_ONLY">داخلي فقط</option>
                <option value="CLIENT_VISIBLE">ظاهر للعميل</option>
              </select>
            </div>
          </div>
          <div className="space-y-1">
            <Label>وصف مختصر</Label>
            <Textarea
              rows={2}
              value={description}
              disabled={saving}
              onChange={(e) => setDescription(e.target.value)}
            />
          </div>
          <Button
            type="button"
            variant="gold"
            size="sm"
            disabled={saving || !file}
            onClick={() => void upload()}
          >
            <Upload className="size-4" />
            {saving ? "جاري الرفع..." : "رفع مستند"}
          </Button>
        </div>
      ) : null}

      {loading ? (
        <PageLoading
          label="جاري تحميل المستندات..."
          minHeightClassName="min-h-[4rem]"
        />
      ) : items.length === 0 ? (
        <EmptyState
          icon={FileText}
          title="لا توجد مستندات"
          description="ستظهر هنا الملفات المرتبطة بهذه القضية."
        />
      ) : (
        <div className="divide-y divide-[var(--color-border)] rounded-xl border border-[var(--color-border)]">
          {items.map((doc) => (
            <div
              key={doc.id}
              className="flex flex-col gap-3 p-4 sm:flex-row sm:items-center sm:justify-between"
            >
              <div className="min-w-0">
                <div className="flex flex-wrap items-center gap-2">
                  <p className="truncate font-medium text-[var(--color-foreground)]">
                    {doc.originalFilename}
                  </p>
                  <span className="rounded-full bg-[var(--color-navy-50)] px-2 py-0.5 text-[10px] font-medium text-[var(--color-navy-700)]">
                    {CATEGORY_LABELS[doc.category] ?? doc.category}
                  </span>
                  <span className="rounded-full bg-[var(--color-surface-muted)] px-2 py-0.5 text-[10px] font-medium text-[var(--color-muted-foreground)]">
                    {VISIBILITY_LABELS[doc.visibility] ?? doc.visibility}
                  </span>
                </div>
                <p className="mt-1 text-xs text-[var(--color-muted-foreground)] ltr-nums">
                  {formatBytes(doc.sizeBytes)} · {formatDateTime(doc.createdAt)}
                </p>
                {doc.description ? (
                  <p className="mt-1 text-sm text-[var(--color-muted-foreground)]">
                    {doc.description}
                  </p>
                ) : null}
              </div>
              <div className="flex shrink-0 items-center gap-2">
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  disabled={viewingId === doc.id}
                  onClick={() => void viewFile(doc)}
                  title="عرض الملف"
                >
                  <Eye className="size-4" />
                  عرض
                </Button>
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  disabled={downloadingId === doc.id}
                  onClick={() => void download(doc)}
                >
                  <Download className="size-4" />
                  تحميل
                </Button>
                {canUpload && canDelete ? (
                  <Button
                    type="button"
                    variant="ghost"
                    size="icon"
                    className="text-[var(--color-danger)]"
                    onClick={() => void remove(doc)}
                    title="حذف"
                    aria-label="حذف"
                  >
                    <Trash2 className="size-4" />
                  </Button>
                ) : null}
              </div>
            </div>
          ))}
        </div>
      )}
    </SectionCard>
  );
}
