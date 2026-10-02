"use client";

import { useEffect, useState, type FormEvent } from "react";
import { useRouter } from "next/navigation";
import { AnimatePresence, motion } from "framer-motion";
import { Sparkles, X } from "lucide-react";

import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { ApiErrorAlert } from "@/components/shared/ApiErrorAlert";
import { templateTypeLabel } from "@/components/templates/TemplateBadges";
import { documentsApi } from "@/lib/api/documents";
import { casesApi } from "@/lib/api/cases";
import { personsApi } from "@/lib/api/persons";
import { ApiRequestError } from "@/lib/api/client";
import { notifyApiError, notifySuccess } from "@/lib/feedback";
import { cn } from "@/lib/utils/cn";
import type { ClientPerson } from "@/types/person";
import type { CaseResponseDto } from "@/types/api";
import type { TemplateResponse } from "@/types/template";

const selectClassName = cn(
  "flex h-10 w-full cursor-pointer appearance-none rounded-xl border border-[var(--color-border)] bg-[var(--color-surface)] px-3.5 py-2 text-sm text-[var(--color-foreground)] shadow-sm transition-colors",
  "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--color-gold-400)] focus-visible:border-[var(--color-gold-400)]",
  "disabled:cursor-not-allowed disabled:opacity-50",
);

interface GenerateDocumentDialogProps {
  template: TemplateResponse | null;
  onClose: () => void;
}

function clientLabel(p: ClientPerson) {
  if (p.personType === "COMPANY") {
    return p.companyName?.trim() || p.fullName?.trim() || p.id;
  }
  return p.fullName?.trim() || p.companyName?.trim() || p.id;
}

/**
 * Modal for {@code POST /api/documents/generate}. Loads optional client/case
 * lookup lists lazily on open so the parent list page stays cheap to render.
 *
 * The picker fields are intentionally optional — the backend renders the
 * matching placeholders ({@code {{client.name}}}, {@code {{case.number}}})
 * as empty strings when nothing is chosen, mirroring the service-layer
 * contract.
 */
export function GenerateDocumentDialog({
  template,
  onClose,
}: GenerateDocumentDialogProps) {
  const router = useRouter();
  const open = template !== null;

  const [title, setTitle] = useState("");
  const [clientId, setClientId] = useState("");
  const [caseId, setCaseId] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const [clients, setClients] = useState<ClientPerson[]>([]);
  const [cases, setCases] = useState<CaseResponseDto[]>([]);
  const [loadingOptions, setLoadingOptions] = useState(false);
  const [optionsError, setOptionsError] = useState<string | null>(null);

  useEffect(() => {
    if (!open) return;
    setTitle(template.name);
    setClientId("");
    setCaseId("");
    setError(null);
  }, [open, template]);

  useEffect(() => {
    if (!open) return;
    let cancelled = false;
    (async () => {
      setLoadingOptions(true);
      setOptionsError(null);
      try {
        const [clientsRes, casesRes] = await Promise.all([
          personsApi.list({ page: 0, size: 200, role: "CLIENT" }),
          casesApi.list({ page: 0, size: 200 }),
        ]);
        if (cancelled) return;
        setClients(clientsRes.content.filter((c) => c.active));
        setCases(casesRes.content);
      } catch (e) {
        if (cancelled) return;
        if (e instanceof ApiRequestError) {
          setOptionsError(e.details?.message ?? e.message);
        } else {
          setOptionsError("تعذر تحميل العملاء والقضايا");
        }
        setClients([]);
        setCases([]);
      } finally {
        if (!cancelled) setLoadingOptions(false);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [open]);

  useEffect(() => {
    if (!open) return;
    function onKey(e: KeyboardEvent) {
      if (e.key === "Escape") onClose();
    }
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [open, onClose]);

  async function onSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    if (!template) return;
    setError(null);
    setSubmitting(true);
    try {
      const created = await documentsApi.generate({
        templateId: template.id,
        title: title.trim() || undefined,
        clientId: clientId || undefined,
        caseId: caseId || undefined,
      });
      notifySuccess("تمّ توليد المستند", created.title);
      onClose();
      router.push(`/documents/${created.id}`);
    } catch (err) {
      if (err instanceof ApiRequestError) {
        setError(err.details?.message ?? err.message);
      }
      notifyApiError(err, "تعذر توليد المستند");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <AnimatePresence>
      {open ? (
        <motion.div
          className="fixed inset-0 z-50 flex items-center justify-center px-4 py-6"
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          exit={{ opacity: 0 }}
        >
          <motion.button
            type="button"
            aria-label="إغلاق"
            className="absolute inset-0 bg-black/50 backdrop-blur-[1px]"
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            onClick={onClose}
          />
          <motion.div
            role="dialog"
            aria-modal="true"
            aria-label={`توليد مستند من نموذج ${template.name}`}
            initial={{ opacity: 0, scale: 0.96, y: 10 }}
            animate={{ opacity: 1, scale: 1, y: 0 }}
            exit={{ opacity: 0, scale: 0.96, y: 10 }}
            transition={{ duration: 0.2 }}
            className="relative z-10 flex w-full max-w-lg flex-col gap-5 rounded-2xl border border-[var(--color-border)] bg-[var(--color-surface)] p-6 shadow-2xl"
          >
            <div className="flex items-start justify-between gap-3">
              <div className="flex items-start gap-3 min-w-0">
                <div className="flex size-10 shrink-0 items-center justify-center rounded-xl bg-[var(--color-gold-50)] text-[var(--color-gold-600)]">
                  <Sparkles className="size-5" />
                </div>
                <div className="min-w-0">
                  <h2 className="text-lg font-semibold text-[var(--color-foreground)]">
                    توليد مستند
                  </h2>
                  <p className="text-sm text-[var(--color-muted-foreground)]">
                    من نموذج «{template.name}» — {templateTypeLabel(template.type)}
                  </p>
                </div>
              </div>
              <button
                type="button"
                onClick={onClose}
                aria-label="إغلاق"
                className="rounded-lg p-1.5 text-[var(--color-muted-foreground)] hover:bg-[var(--color-surface-muted)]"
              >
                <X className="size-4" />
              </button>
            </div>

            {error ? (
              <ApiErrorAlert message={error} onRetry={() => setError(null)} />
            ) : null}

            {optionsError ? (
              <p className="text-xs text-[var(--color-warning)]">
                {optionsError} — يمكنك المتابعة بدون اختيار موكِّل أو قضية.
              </p>
            ) : null}

            <form onSubmit={onSubmit} className="space-y-4" noValidate>
              <div className="space-y-2">
                <Label htmlFor="doc-title">عنوان المستند</Label>
                <Input
                  id="doc-title"
                  value={title}
                  onChange={(e) => setTitle(e.target.value)}
                  placeholder="إذا تركته فارغًا سنستخدم اسم النموذج"
                  disabled={submitting}
                  autoComplete="off"
                />
              </div>

              <div className="space-y-2">
                <Label htmlFor="doc-client">الموكِّل (اختياري)</Label>
                <select
                  id="doc-client"
                  className={selectClassName}
                  value={clientId}
                  onChange={(e) => setClientId(e.target.value)}
                  disabled={submitting || loadingOptions}
                >
                  <option value="">— بدون —</option>
                  {clients.map((c) => (
                    <option key={c.id} value={c.id}>
                      {clientLabel(c)}
                    </option>
                  ))}
                </select>
              </div>

              <div className="space-y-2">
                <Label htmlFor="doc-case">القضية (اختيارية)</Label>
                <select
                  id="doc-case"
                  className={selectClassName}
                  value={caseId}
                  onChange={(e) => setCaseId(e.target.value)}
                  disabled={submitting || loadingOptions}
                >
                  <option value="">— بدون —</option>
                  {cases.map((c) => (
                    <option key={c.id} value={c.id}>
                      #{c.caseNumber} — {c.title}
                    </option>
                  ))}
                </select>
              </div>

              <div className="flex justify-end gap-2 border-t border-[var(--color-border)] pt-4">
                <Button
                  type="button"
                  variant="ghost"
                  onClick={onClose}
                  disabled={submitting}
                >
                  إلغاء
                </Button>
                <Button type="submit" variant="gold" disabled={submitting}>
                  {submitting ? "جارٍ التوليد…" : "توليد المستند"}
                </Button>
              </div>
            </form>
          </motion.div>
        </motion.div>
      ) : null}
    </AnimatePresence>
  );
}
