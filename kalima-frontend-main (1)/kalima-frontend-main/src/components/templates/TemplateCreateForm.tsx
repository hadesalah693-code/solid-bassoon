"use client";

import { useRef, useState, type FormEvent } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { ChevronDown, ChevronUp, Info, Save } from "lucide-react";

import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { ApiErrorAlert } from "@/components/shared/ApiErrorAlert";
import { RichEditor, type RichEditorHandle } from "@/components/templates/RichEditor";
import { DocumentPreview } from "@/components/templates/DocumentPreview";
import { templateTypeLabel } from "@/components/templates/TemplateBadges";
import { templatesApi } from "@/lib/api/templates";
import { ApiRequestError } from "@/lib/api/client";
import { notifyApiError, notifySuccess } from "@/lib/feedback";
import { cn } from "@/lib/utils/cn";
import {
  TEMPLATE_TYPES,
  type CreateTemplateRequest,
  type TemplateType,
  type UpdateTemplateRequest,
} from "@/types/template";

// ── Styling helpers ──────────────────────────────────────────────────────────

const selectClassName = cn(
  "flex h-10 w-full cursor-pointer appearance-none rounded-xl border border-[var(--color-border)] bg-[var(--color-surface)] px-3.5 py-2 text-sm text-[var(--color-foreground)] shadow-sm transition-colors",
  "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--color-gold-400)] focus-visible:border-[var(--color-gold-400)]",
  "disabled:cursor-not-allowed disabled:opacity-50",
);

// ── Smart variable chips ─────────────────────────────────────────────────────

const PLACEHOLDER_CHIPS = [
  { label: "اسم المكتب",      token: "{{company.name}}" },
  { label: "اسم العميل",      token: "{{client.name}}" },
  { label: "رقم القضية",      token: "{{case.number}}" },
  { label: "نوع القضية",      token: "{{case.type}}" },
  { label: "تاريخ اليوم",     token: "{{today}}" },
  { label: "اسم المحامي",     token: "{{lawyer.name}}" },
  { label: "اسم المحكمة",     token: "{{court.name}}" },
  { label: "تاريخ بدء القضية", token: "{{case.start_date}}" },
] as const;

// ── Ready HTML sections ──────────────────────────────────────────────────────

const CELL = `border:1px solid #d1d5db;padding:8px 12px;text-align:right`;
const TH = `${CELL};font-weight:600;background:#f9f7f2;color:#5a4a1a`;
const TABLE_WRAPPER = `width:100%;border-collapse:collapse;direction:rtl;margin:12px 0`;

const READY_SECTIONS = [
  {
    label: "جدول خدمات",
    html:
      `<table style="${TABLE_WRAPPER}"><thead><tr>` +
      `<th style="${TH}">الخدمة</th><th style="${TH}">الوصف</th><th style="${TH}">الرسوم (ر.س)</th>` +
      `</tr></thead><tbody>` +
      `<tr><td style="${CELL}">استشارة قانونية</td><td style="${CELL}">وصف الخدمة</td><td style="${CELL}">0.00</td></tr>` +
      `<tr style="background:#fafafa"><td style="${CELL}">تمثيل قضائي</td><td style="${CELL}">وصف الخدمة</td><td style="${CELL}">0.00</td></tr>` +
      `<tr><td style="${CELL}"><strong>الإجمالي</strong></td><td style="${CELL}"></td><td style="${CELL}"><strong>0.00</strong></td></tr>` +
      `</tbody></table><br>`,
  },
  {
    label: "جدول تكاليف",
    html:
      `<table style="${TABLE_WRAPPER}"><thead><tr>` +
      `<th style="${TH}">البند</th><th style="${TH}">المبلغ (ر.س)</th><th style="${TH}">ملاحظات</th>` +
      `</tr></thead><tbody>` +
      `<tr><td style="${CELL}">رسوم المحكمة</td><td style="${CELL}">0.00</td><td style="${CELL}"></td></tr>` +
      `<tr style="background:#fafafa"><td style="${CELL}">رسوم الخبراء</td><td style="${CELL}">0.00</td><td style="${CELL}"></td></tr>` +
      `<tr><td style="${CELL}">مصاريف أخرى</td><td style="${CELL}">0.00</td><td style="${CELL}"></td></tr>` +
      `<tr style="background:#f9f7f2"><td style="${CELL}"><strong>الإجمالي</strong></td><td style="${CELL}"><strong>0.00</strong></td><td style="${CELL}"></td></tr>` +
      `</tbody></table><br>`,
  },
  {
    label: "شروط دفع",
    html:
      `<p><strong>شروط الدفع:</strong></p>` +
      `<ul style="padding-right:20px;line-height:2">` +
      `<li>يُسدَّد 50% عند توقيع الاتفاقية.</li>` +
      `<li>يُسدَّد الرصيد المتبقي عند انتهاء الخدمة أو صدور الحكم.</li>` +
      `<li>يُقبَل الدفع نقداً أو بتحويل بنكي.</li>` +
      `<li>تُطبَّق رسوم تأخير بواقع 2% شهرياً عند التأخر عن السداد.</li>` +
      `</ul><br>`,
  },
  {
    label: "بنود قانونية",
    html:
      `<p><strong>ملاحظات قانونية:</strong></p>` +
      `<ul style="padding-right:20px;line-height:2">` +
      `<li>يُعدّ هذا المستند سرياً ومخصصاً لطرفَي الاتفاقية فقط.</li>` +
      `<li>يخضع هذا العقد لأحكام نظام المحاماة السعودي وتعديلاته.</li>` +
      `<li>تُحسَم أي نزاعات أمام المحاكم المختصة في المملكة العربية السعودية.</li>` +
      `<li>المكتب غير مسؤول عن التأخيرات الناجمة عن القضاء أو الجهات الحكومية.</li>` +
      `</ul><br>`,
  },
  {
    label: "توقيع وختم",
    html:
      `<br>` +
      `<div style="display:flex;justify-content:space-around;gap:32px;margin-top:32px;direction:rtl">` +
      `<div style="text-align:center">` +
      `<div style="border-top:1px solid #374151;width:160px;padding-top:6px;font-size:12px;color:#374151">توقيع الموكّل</div>` +
      `<div style="font-size:11px;color:#64748b;margin-top:4px">{{client.name}}</div>` +
      `</div>` +
      `<div style="text-align:center">` +
      `<div style="border-top:1px solid #374151;width:160px;padding-top:6px;font-size:12px;color:#374151">توقيع المحامي</div>` +
      `<div style="font-size:11px;color:#64748b;margin-top:4px">{{lawyer.name}}</div>` +
      `</div>` +
      `</div>` +
      `<div style="text-align:center;margin-top:16px;font-size:11px;color:#64748b">[ختم المكتب الرسمي]</div>`,
  },
] as const;

// ── Validation ────────────────────────────────────────────────────────────────

type FormErrors = Partial<Record<"name" | "type" | "content", string>>;

type DefaultValues = {
  name: string;
  type: TemplateType;
  content: string;
  isDefault: boolean;
};

type Props = {
  mode?: "create" | "edit";
  templateId?: string;
  defaultValues?: DefaultValues;
};

// ── Component ─────────────────────────────────────────────────────────────────

export function TemplateCreateForm({
  mode = "create",
  templateId,
  defaultValues,
}: Props) {
  const router = useRouter();
  const richEditorRef = useRef<RichEditorHandle>(null);

  const [name, setName] = useState(defaultValues?.name ?? "");
  const [type, setType] = useState<TemplateType>(
    defaultValues?.type ?? "QUOTATION",
  );
  const [content, setContent] = useState(defaultValues?.content ?? "");
  const [isDefault, setIsDefault] = useState(
    defaultValues?.isDefault ?? false,
  );
  const [errors, setErrors] = useState<FormErrors>({});
  const [submitting, setSubmitting] = useState(false);
  const [serverError, setServerError] = useState<string | null>(null);
  const [sectionsOpen, setSectionsOpen] = useState(false);

  // ── Word/char count ────────────────────────────────────────────────────────

  const textContent = content.replace(/<[^>]*>/g, "");
  const charCount = textContent.length;
  const wordCount = textContent.trim()
    ? textContent.trim().split(/\s+/).length
    : 0;

  // ── Validation ─────────────────────────────────────────────────────────────

  function validate(): FormErrors {
    const next: FormErrors = {};
    if (!name.trim()) next.name = "اسم النموذج مطلوب";
    if (name.trim().length > 200) next.name = "الاسم يجب ألا يتجاوز 200 حرفًا";
    if (!type) next.type = "النوع مطلوب";
    if (!textContent.trim()) next.content = "محتوى النموذج مطلوب";
    return next;
  }

  // ── Submit ─────────────────────────────────────────────────────────────────

  async function onSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    setServerError(null);
    const v = validate();
    setErrors(v);
    if (Object.keys(v).length > 0) return;

    const body: CreateTemplateRequest & UpdateTemplateRequest = {
      name: name.trim(),
      type,
      content,
      isDefault,
    };

    setSubmitting(true);
    try {
      if (mode === "edit" && templateId) {
        const updated = await templatesApi.update(templateId, body);
        notifySuccess("تمّ تحديث النموذج", updated.name);
      } else {
        const created = await templatesApi.create(body);
        notifySuccess("تمّ حفظ النموذج", created.name);
      }
      router.push("/templates");
    } catch (err) {
      if (err instanceof ApiRequestError) {
        setServerError(err.details?.message ?? err.message);
      }
      notifyApiError(
        err,
        mode === "edit" ? "تعذر تحديث النموذج" : "تعذر حفظ النموذج",
      );
    } finally {
      setSubmitting(false);
    }
  }

  // ── Render ─────────────────────────────────────────────────────────────────

  return (
    <form onSubmit={onSubmit} noValidate className="space-y-6">
      {serverError ? (
        <ApiErrorAlert
          message={serverError}
          onRetry={() => setServerError(null)}
        />
      ) : null}

      {/* Split: editor (right in RTL) | preview (left in RTL) */}
      <div className="grid grid-cols-1 gap-6 xl:grid-cols-[3fr_2fr]">

        {/* ── Right panel: editor ─────────────────────────────────────────── */}
        <div className="space-y-5">

          {/* Meta fields */}
          <div className="space-y-4 rounded-xl border border-[var(--color-border)] bg-[var(--color-surface)] p-5 shadow-sm">
            <div className="flex items-center gap-2 border-b border-[var(--color-border)] pb-3">
              <span className="text-sm font-semibold text-[var(--color-foreground)]">
                بيانات النموذج
              </span>
            </div>

            {/* Name */}
            <div className="space-y-1.5">
              <Label htmlFor="tpl-name">اسم النموذج *</Label>
              <Input
                id="tpl-name"
                value={name}
                onChange={(e) => setName(e.target.value)}
                disabled={submitting}
                placeholder="مثال: عرض سعر — استشارات شركات"
                autoComplete="off"
              />
              {errors.name ? (
                <p className="text-xs text-[var(--color-danger)]">
                  {errors.name}
                </p>
              ) : null}
            </div>

            <div className="grid grid-cols-2 gap-4">
              {/* Type */}
              <div className="space-y-1.5">
                <Label htmlFor="tpl-type">نوع النموذج *</Label>
                <select
                  id="tpl-type"
                  value={type}
                  onChange={(e) => setType(e.target.value as TemplateType)}
                  disabled={submitting}
                  className={selectClassName}
                >
                  {TEMPLATE_TYPES.map((t) => (
                    <option key={t} value={t}>
                      {templateTypeLabel(t)}
                    </option>
                  ))}
                </select>
                {errors.type ? (
                  <p className="text-xs text-[var(--color-danger)]">
                    {errors.type}
                  </p>
                ) : null}
              </div>

              {/* isDefault */}
              <div className="space-y-1.5">
                <Label className="select-none opacity-0">.</Label>
                <label
                  htmlFor="tpl-default"
                  className="flex h-10 cursor-pointer items-center gap-2.5 rounded-xl border border-[var(--color-border)] bg-[var(--color-surface-muted)]/40 px-3.5 text-sm"
                >
                  <input
                    id="tpl-default"
                    type="checkbox"
                    className="size-4 rounded border-[var(--color-border)] accent-[var(--color-gold-500)]"
                    checked={isDefault}
                    onChange={(e) => setIsDefault(e.target.checked)}
                    disabled={submitting}
                  />
                  <span className="text-[var(--color-foreground)]">
                    افتراضي لنوعه
                  </span>
                </label>
              </div>
            </div>
          </div>

          {/* ── Rich editor ────────────────────────────────────────────────── */}
          <div className="space-y-1">
            <div className="flex items-center justify-between px-0.5">
              <Label htmlFor="tpl-content" className="text-sm font-semibold">
                محتوى النموذج *
              </Label>
              <span className="text-[10px] text-[var(--color-muted-foreground)]">
                {wordCount} كلمة · {charCount} حرف
              </span>
            </div>

            <RichEditor
              ref={richEditorRef}
              key={templateId ?? "new"}
              defaultValue={defaultValues?.content ?? ""}
              onChange={setContent}
              disabled={submitting}
              placeholder="ابدأ بكتابة محتوى النموذج القانوني…"
            />

            {errors.content ? (
              <p className="text-xs text-[var(--color-danger)]">
                {errors.content}
              </p>
            ) : null}
          </div>

          {/* ── Smart placeholders panel ──────────────────────────────────── */}
          <div className="rounded-xl border border-[var(--color-border)] bg-[var(--color-surface)] p-4 shadow-sm">
            <div className="mb-3 flex items-center gap-2">
              <Info className="size-3.5 text-[var(--color-gold-600)]" />
              <span className="text-xs font-semibold text-[var(--color-foreground)]">
                المتغيّرات الذكية
              </span>
              <span className="text-[10px] text-[var(--color-muted-foreground)]">
                — انقر لإدراج في مكان المؤشر
              </span>
            </div>
            <div className="flex flex-wrap gap-2">
              {PLACEHOLDER_CHIPS.map((chip) => (
                <button
                  key={chip.token}
                  type="button"
                  disabled={submitting}
                  onClick={() =>
                    richEditorRef.current?.insertAtCursor(chip.token)
                  }
                  className={cn(
                    "flex items-center gap-1.5 rounded-lg border border-[var(--color-gold-200)] bg-[var(--color-gold-50)] px-3 py-1.5 text-xs font-medium text-[var(--color-gold-700)] transition-colors",
                    "hover:border-[var(--color-gold-300)] hover:bg-[var(--color-gold-100)]",
                    "disabled:pointer-events-none disabled:opacity-50",
                  )}
                >
                  <span className="font-mono text-[10px] opacity-70">
                    {chip.token}
                  </span>
                  <span>{chip.label}</span>
                </button>
              ))}
            </div>
          </div>

          {/* ── Ready HTML sections ───────────────────────────────────────── */}
          <div className="rounded-xl border border-[var(--color-border)] bg-[var(--color-surface)] shadow-sm">
            <button
              type="button"
              onClick={() => setSectionsOpen((o) => !o)}
              className="flex w-full items-center justify-between px-4 py-3 text-right"
            >
              <div className="flex items-center gap-2">
                <span className="text-xs font-semibold text-[var(--color-foreground)]">
                  أقسام جاهزة
                </span>
                <span className="text-[10px] text-[var(--color-muted-foreground)]">
                  — جداول وبنود احترافية جاهزة للإدراج
                </span>
              </div>
              {sectionsOpen ? (
                <ChevronUp className="size-4 text-[var(--color-muted-foreground)]" />
              ) : (
                <ChevronDown className="size-4 text-[var(--color-muted-foreground)]" />
              )}
            </button>

            {sectionsOpen ? (
              <div className="flex flex-wrap gap-2 border-t border-[var(--color-border)] px-4 py-3">
                {READY_SECTIONS.map((section) => (
                  <button
                    key={section.label}
                    type="button"
                    disabled={submitting}
                    onClick={() =>
                      richEditorRef.current?.insertAtCursor(section.html)
                    }
                    className={cn(
                      "rounded-lg border border-[var(--color-border)] bg-[var(--color-surface-muted)]/60 px-3 py-1.5 text-xs font-medium text-[var(--color-foreground)] transition-colors",
                      "hover:border-[var(--color-navy-200)] hover:bg-[var(--color-navy-50)] hover:text-[var(--color-navy-700)]",
                      "disabled:pointer-events-none disabled:opacity-50",
                    )}
                  >
                    + {section.label}
                  </button>
                ))}
              </div>
            ) : null}
          </div>
        </div>

        {/* ── Left panel: live preview ────────────────────────────────────── */}
        <DocumentPreview content={content} />
      </div>

      {/* ── Sticky save bar ────────────────────────────────────────────────── */}
      <div className="sticky bottom-4 z-10 flex flex-wrap items-center gap-3 rounded-xl border border-[var(--color-border)] bg-[var(--color-surface)]/95 p-4 shadow-[var(--shadow-pop)] backdrop-blur-sm">
        <Button
          type="submit"
          variant="gold"
          disabled={submitting}
          className="gap-2"
        >
          <Save className="size-4" />
          {mode === "edit"
            ? submitting
              ? "جارٍ التحديث…"
              : "تحديث النموذج"
            : submitting
              ? "جارٍ الحفظ…"
              : "حفظ النموذج"}
        </Button>
        <Button type="button" variant="ghost" asChild>
          <Link href="/templates">إلغاء</Link>
        </Button>
        {charCount > 0 ? (
          <span className="mr-auto text-xs text-[var(--color-muted-foreground)]">
            المعاينة تُحدَّث تلقائيًا — القيم المُظلَّلة بالأصفر تجريبية.
          </span>
        ) : null}
      </div>
    </form>
  );
}
