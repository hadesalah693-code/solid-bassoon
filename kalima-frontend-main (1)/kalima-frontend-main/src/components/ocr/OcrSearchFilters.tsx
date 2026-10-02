"use client";

import { Filter } from "lucide-react";

import { SearchInput } from "@/components/shared/SearchInput";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { cn } from "@/lib/utils/cn";
import {
  OCR_DOCUMENT_TYPES,
  OCR_STATUSES,
  type OcrDocumentType,
  type OcrSearchQuery,
  type OcrStatus,
} from "@/types/ocr";
import {
  ocrDocumentTypeLabel,
  ocrStatusLabel,
} from "@/lib/ocr/labels";

const selectClassName = cn(
  "flex h-10 w-full cursor-pointer appearance-none rounded-xl border border-[var(--color-border)] bg-[var(--color-surface)] px-3.5 py-2 text-sm",
  "focus-visible:ring-2 focus-visible:ring-[var(--color-gold-400)]",
);

export type OcrFilterState = Omit<
  OcrSearchQuery,
  "page" | "size" | "sort"
>;

type Props = {
  filters: OcrFilterState;
  onChange: (next: OcrFilterState) => void;
  onApply: () => void;
  onReset: () => void;
  showAdvanced?: boolean;
  onToggleAdvanced?: () => void;
};

export function OcrSearchFilters({
  filters,
  onChange,
  onApply,
  onReset,
  showAdvanced,
  onToggleAdvanced,
}: Props) {
  function patch(partial: Partial<OcrFilterState>) {
    onChange({ ...filters, ...partial });
  }

  return (
    <div className="space-y-4">
      <div className="flex flex-col gap-3 lg:flex-row lg:items-end">
        <div className="min-w-0 flex-1">
          <Label htmlFor="ocr-search-q" className="sr-only">
            بحث
          </Label>
          <SearchInput
            id="ocr-search-q"
            value={filters.q ?? ""}
            onChange={(q) => patch({ q })}
            placeholder="بحث في العنوان أو النص المستخرج…"
            onKeyDown={(e) => {
              if (e.key === "Enter") onApply();
            }}
          />
        </div>
        <div className="grid w-full grid-cols-2 gap-2 sm:flex sm:w-auto">
          <select
            className={cn(selectClassName, "sm:min-w-[9rem]")}
            value={filters.status ?? ""}
            onChange={(e) =>
              patch({
                status: (e.target.value || undefined) as OcrStatus | undefined,
              })
            }
            aria-label="فلتر الحالة"
          >
            <option value="">كل الحالات</option>
            {OCR_STATUSES.map((s) => (
              <option key={s} value={s}>
                {ocrStatusLabel(s)}
              </option>
            ))}
          </select>
          <select
            className={cn(selectClassName, "sm:min-w-[9rem]")}
            value={filters.documentType ?? ""}
            onChange={(e) =>
              patch({
                documentType: (e.target.value || undefined) as
                  | OcrDocumentType
                  | undefined,
              })
            }
            aria-label="نوع المستند"
          >
            <option value="">كل الأنواع</option>
            {OCR_DOCUMENT_TYPES.map((t) => (
              <option key={t} value={t}>
                {ocrDocumentTypeLabel(t)}
              </option>
            ))}
          </select>
          {onToggleAdvanced ? (
            <Button type="button" variant="outline" onClick={onToggleAdvanced}>
              <Filter className="size-4" aria-hidden />
              {showAdvanced ? "إخفاء" : "متقدم"}
            </Button>
          ) : null}
          <Button type="button" onClick={onApply}>
            بحث
          </Button>
          <Button type="button" variant="outline" onClick={onReset}>
            إعادة
          </Button>
        </div>
      </div>

      {showAdvanced ? (
        <div className="grid gap-3 rounded-xl border border-[var(--color-border)] bg-[var(--color-surface-muted)]/20 p-4 md:grid-cols-2 lg:grid-cols-3">
          <div className="space-y-1.5">
            <Label htmlFor="ocr-from">من تاريخ</Label>
            <Input
              id="ocr-from"
              type="date"
              value={filters.from ?? ""}
              onChange={(e) => patch({ from: e.target.value || undefined })}
            />
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="ocr-to">إلى تاريخ</Label>
            <Input
              id="ocr-to"
              type="date"
              value={filters.to ?? ""}
              onChange={(e) => patch({ to: e.target.value || undefined })}
            />
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="ocr-uploaded-by">رفع بواسطة (معرّف)</Label>
            <Input
              id="ocr-uploaded-by"
              value={filters.uploadedBy ?? ""}
              onChange={(e) =>
                patch({ uploadedBy: e.target.value.trim() || undefined })
              }
              placeholder="UUID"
              className="font-mono text-xs ltr-nums"
            />
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="ocr-case-id">قضية</Label>
            <Input
              id="ocr-case-id"
              value={filters.caseId ?? ""}
              onChange={(e) =>
                patch({ caseId: e.target.value.trim() || undefined })
              }
              placeholder="معرّف القضية"
              className="font-mono text-xs ltr-nums"
            />
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="ocr-client-id">عميل</Label>
            <Input
              id="ocr-client-id"
              value={filters.clientId ?? ""}
              onChange={(e) =>
                patch({ clientId: e.target.value.trim() || undefined })
              }
              placeholder="معرّف العميل"
              className="font-mono text-xs ltr-nums"
            />
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="ocr-dispute-id">نزاع</Label>
            <Input
              id="ocr-dispute-id"
              value={filters.disputeId ?? ""}
              onChange={(e) =>
                patch({ disputeId: e.target.value.trim() || undefined })
              }
              placeholder="معرّف النزاع"
              className="font-mono text-xs ltr-nums"
            />
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="ocr-task-id">مهمة</Label>
            <Input
              id="ocr-task-id"
              value={filters.taskId ?? ""}
              onChange={(e) =>
                patch({ taskId: e.target.value.trim() || undefined })
              }
              placeholder="معرّف المهمة"
              className="font-mono text-xs ltr-nums"
            />
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="ocr-document-id">مستند</Label>
            <Input
              id="ocr-document-id"
              value={filters.documentId ?? ""}
              onChange={(e) =>
                patch({ documentId: e.target.value.trim() || undefined })
              }
              placeholder="معرّف المستند"
              className="font-mono text-xs ltr-nums"
            />
          </div>
        </div>
      ) : null}
    </div>
  );
}

export const EMPTY_OCR_FILTERS: OcrFilterState = {};
