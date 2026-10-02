"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { useParams } from "next/navigation";
import { ArrowRight, Scale } from "lucide-react";

import { ApiErrorAlert } from "@/components/shared/ApiErrorAlert";
import { PageHeader } from "@/components/shared/PageHeader";
import { PageLoading } from "@/components/shared/PageLoading";
import { TemplateCreateForm } from "@/components/templates/TemplateCreateForm";
import { Button } from "@/components/ui/button";
import { ApiRequestError } from "@/lib/api/client";
import { templatesApi } from "@/lib/api/templates";
import type { TemplateResponse } from "@/types/template";

export default function EditTemplatePage() {
  const params = useParams<{ id: string }>();
  const id = typeof params?.id === "string" ? params.id : null;

  const [template, setTemplate] = useState<TemplateResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!id) return;
    let cancelled = false;
    (async () => {
      setLoading(true);
      setError(null);
      try {
        const data = await templatesApi.get(id);
        if (cancelled) return;
        setTemplate(data);
      } catch (e) {
        if (cancelled) return;
        if (e instanceof ApiRequestError) {
          setError(e.details?.message ?? e.message);
        } else {
          setError("تعذر تحميل النموذج");
        }
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [id]);

  if (!id) {
    return <ApiErrorAlert message="معرف النموذج غير صالح" />;
  }

  if (loading) {
    return (
      <PageLoading label="جارٍ تحميل النموذج…" minHeightClassName="min-h-[24rem]" />
    );
  }

  if (error || !template) {
    return (
      <ApiErrorAlert message={error ?? "تعذر تحميل النموذج"} />
    );
  }

  return (
    <div className="space-y-6">
      <PageHeader
        title={`تعديل: ${template.name}`}
        description="عدِّل محتوى النموذج أو بياناته — التغييرات ستنعكس على المستندات المولَّدة مستقبلًا فقط."
        actions={
          <Button variant="ghost" asChild>
            <Link
              href="/templates"
              className="inline-flex items-center gap-2 text-[var(--color-muted-foreground)]"
            >
              <ArrowRight className="size-4 rotate-180" />
              كل النماذج
            </Link>
          </Button>
        }
      />

      <div className="flex items-start gap-3 rounded-xl border border-[var(--color-gold-200)] bg-[var(--color-gold-50)] px-4 py-3">
        <Scale className="mt-0.5 size-4 shrink-0 text-[var(--color-gold-600)]" />
        <p className="text-xs leading-relaxed text-[var(--color-gold-700)]">
          أنت تعدّل نموذجًا موجودًا. التغييرات ستُطبَّق على المستندات الجديدة فقط —
          المستندات المولَّدة مسبقًا لن تتأثر.
        </p>
      </div>

      <TemplateCreateForm
        mode="edit"
        templateId={id}
        defaultValues={{
          name: template.name,
          type: template.type,
          content: template.content,
          isDefault: template.isDefault,
        }}
      />
    </div>
  );
}
