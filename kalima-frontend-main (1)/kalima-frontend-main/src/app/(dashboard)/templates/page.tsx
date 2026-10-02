"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { FileText, Pencil, Plus, Sparkles } from "lucide-react";

import { ApiErrorAlert } from "@/components/shared/ApiErrorAlert";
import { EmptyState } from "@/components/shared/EmptyState";
import { PageHeader } from "@/components/shared/PageHeader";
import { PageLoading } from "@/components/shared/PageLoading";
import { GenerateDocumentDialog } from "@/components/templates/GenerateDocumentDialog";
import {
  IsDefaultBadge,
  TemplateTypeBadge,
} from "@/components/templates/TemplateBadges";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { ApiRequestError } from "@/lib/api/client";
import { templatesApi } from "@/lib/api/templates";
import { formatDate } from "@/lib/utils/format-date";
import type { TemplateResponse } from "@/types/template";

export default function TemplatesPage() {
  const [items, setItems] = useState<TemplateResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [fetchId, setFetchId] = useState(0);
  const [generating, setGenerating] = useState<TemplateResponse | null>(null);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      setLoading(true);
      setError(null);
      try {
        const list = await templatesApi.list();
        if (cancelled) return;
        setItems(list);
      } catch (e) {
        if (cancelled) return;
        if (e instanceof ApiRequestError) {
          setError(e.details?.message ?? e.message);
        } else {
          setError("تعذر تحميل النماذج");
        }
        setItems([]);
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [fetchId]);

  return (
    <div className="space-y-6">
      <PageHeader
        title="النماذج"
        description="مكتبة النماذج القابلة للتعبئة لإنتاج عروض الأسعار والفواتير والعقود."
        actions={
          <Button variant="gold" asChild>
            <Link
              href="/templates/new"
              className="inline-flex items-center gap-2"
            >
              <Plus className="size-4" />
              إنشاء نموذج جديد
            </Link>
          </Button>
        }
      />

      {error ? (
        <ApiErrorAlert
          message={error}
          onRetry={() => setFetchId((n) => n + 1)}
        />
      ) : null}

      {loading ? (
        <PageLoading
          label="جارٍ تحميل النماذج…"
          minHeightClassName="min-h-[18rem]"
        />
      ) : error ? null : items.length === 0 ? (
        <EmptyState
          title="لا توجد نماذج بعد"
          description="ابدأ بإنشاء أول نموذج (عرض سعر، فاتورة، عقد …) لاستخدامه لاحقًا في توليد المستندات."
          icon={FileText}
          action={
            <Button variant="gold" asChild>
              <Link
                href="/templates/new"
                className="inline-flex items-center gap-2"
              >
                <Plus className="size-4" />
                إنشاء نموذج جديد
              </Link>
            </Button>
          }
        />
      ) : (
        <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
          {items.map((t) => (
            <TemplateCard
              key={t.id}
              template={t}
              onGenerate={() => setGenerating(t)}
            />
          ))}
        </div>
      )}

      <GenerateDocumentDialog
        template={generating}
        onClose={() => setGenerating(null)}
      />
    </div>
  );
}

function TemplateCard({
  template,
  onGenerate,
}: {
  template: TemplateResponse;
  onGenerate: () => void;
}) {
  return (
    <Card className="group flex flex-col p-5">
      <div className="flex items-start justify-between gap-3">
        <div className="flex size-10 shrink-0 items-center justify-center rounded-xl bg-[var(--color-navy-50)] text-[var(--color-navy-700)]">
          <FileText className="size-5" />
        </div>
        <IsDefaultBadge isDefault={template.isDefault} />
      </div>

      <div className="mt-4 min-w-0">
        <h3 className="truncate text-base font-semibold text-[var(--color-foreground)]">
          {template.name}
        </h3>
        <div className="mt-2">
          <TemplateTypeBadge value={template.type} />
        </div>
        <p className="mt-3 line-clamp-3 whitespace-pre-line text-xs text-[var(--color-muted-foreground)]">
          {template.content.trim() || "—"}
        </p>
      </div>

      <div className="mt-auto flex items-center justify-between gap-3 border-t border-[var(--color-border)] pt-4 text-xs text-[var(--color-muted-foreground)]">
        <span>تحديث: {formatDate(template.updatedAt)}</span>
        <div className="flex items-center gap-2">
          <Button size="sm" variant="outline" asChild className="gap-1.5">
            <Link href={`/templates/${template.id}/edit`}>
              <Pencil className="size-3.5" />
              تعديل
            </Link>
          </Button>
          <Button
            type="button"
            size="sm"
            variant="gold"
            onClick={onGenerate}
            className="gap-1.5"
          >
            <Sparkles className="size-3.5" />
            إنشاء مستند
          </Button>
        </div>
      </div>
    </Card>
  );
}
