"use client";

import { AnimatePresence, motion } from "framer-motion";
import { Search, X } from "lucide-react";
import { useEffect, useState } from "react";

import { ApiErrorAlert } from "@/components/shared/ApiErrorAlert";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { casesApi } from "@/lib/api/cases";
import { disputesApi } from "@/lib/api/disputes";
import { ApiRequestError } from "@/lib/api/client";
import {
  DISPUTE_MANUAL_LINK_ROLE_OPTIONS,
  disputeCaseLinkRoleAr,
  type DisputeManualLinkRole,
} from "@/lib/disputes/labels";
import { cn } from "@/lib/utils/cn";
import type { CaseResponseDto } from "@/types/api";

export type DisputeLinkCaseDialogProps = {
  open: boolean;
  disputeId: string;
  /** Case IDs already linked or reserved (e.g. converted case) — hidden from pick list. */
  excludeCaseIds: string[];
  onClose: () => void;
  onLinked: () => void;
};

export function DisputeLinkCaseDialog({
  open,
  disputeId,
  excludeCaseIds,
  onClose,
  onLinked,
}: DisputeLinkCaseDialogProps) {
  const [search, setSearch] = useState("");
  const [debouncedSearch, setDebouncedSearch] = useState("");
  const [results, setResults] = useState<CaseResponseDto[]>([]);
  const [loadingList, setLoadingList] = useState(false);
  const [selectedCaseId, setSelectedCaseId] = useState<string | null>(null);
  const [linkRole, setLinkRole] = useState<DisputeManualLinkRole>("PRIMARY");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const t = window.setTimeout(() => setDebouncedSearch(search.trim()), 320);
    return () => window.clearTimeout(t);
  }, [search]);

  useEffect(() => {
    if (!open) return;
    setSearch("");
    setDebouncedSearch("");
    setSelectedCaseId(null);
    setLinkRole("PRIMARY");
    setError(null);
    setResults([]);
  }, [open, disputeId]);

  useEffect(() => {
    if (!open) return;
    let cancelled = false;
    const ex = new Set(excludeCaseIds);

    setLoadingList(true);
    casesApi
      .list({
        search: debouncedSearch || undefined,
        page: 0,
        size: 40,
        sort: "updatedAt,desc",
        includeArchived: false,
      })
      .then((page) => {
        if (cancelled) return;
        const filtered = page.content.filter(
          (c) => !ex.has(c.id) && c.status !== "ARCHIVED",
        );
        setResults(filtered);
      })
      .catch(() => {
        if (!cancelled) setResults([]);
      })
      .finally(() => {
        if (!cancelled) setLoadingList(false);
      });

    return () => {
      cancelled = true;
    };
  }, [open, debouncedSearch, excludeCaseIds]);

  async function submit() {
    if (!selectedCaseId) return;
    setSubmitting(true);
    setError(null);
    try {
      await disputesApi.linkCase(disputeId, {
        caseId: selectedCaseId,
        linkRole,
      });
      onLinked();
      onClose();
    } catch (e) {
      let msg =
        e instanceof ApiRequestError
          ? e.details?.message ?? e.message
          : "تعذر ربط القضية";
      if (
        e instanceof ApiRequestError &&
        e.status === 409 &&
        (!msg || msg === "Request failed")
      ) {
        msg = "لا يمكن ربط القضية: الربط موجود مسبقاً أو تعارض مع بيانات أخرى.";
      }
      setError(msg);
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <AnimatePresence>
      {open ? (
        <motion.div
          className="fixed inset-0 z-50 flex items-center justify-center p-4"
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          exit={{ opacity: 0 }}
        >
          <button
            type="button"
            className="absolute inset-0 bg-black/45 backdrop-blur-[2px]"
            aria-label="إغلاق"
            onClick={() => !submitting && onClose()}
          />
          <motion.div
            role="dialog"
            aria-modal="true"
            dir="rtl"
            initial={{ opacity: 0, y: 12, scale: 0.98 }}
            animate={{ opacity: 1, y: 0, scale: 1 }}
            exit={{ opacity: 0, y: 8, scale: 0.98 }}
            className="relative z-10 flex max-h-[min(90vh,560px)] w-full max-w-lg flex-col overflow-hidden rounded-2xl border border-[var(--color-border)] bg-[var(--color-surface)] shadow-xl"
          >
            <div className="flex items-start justify-between gap-3 border-b border-[var(--color-border)] px-5 py-4">
              <div>
                <h2 className="text-base font-semibold text-[var(--color-foreground)]">
                  ربط قضية بالنزاع
                </h2>
                <p className="mt-1 text-xs text-[var(--color-muted-foreground)]">
                  ابحث عن قضية موجودة واختر دور الربط. القضايا المرتبطة مسبقاً لا تظهر في القائمة.
                </p>
              </div>
              <Button
                type="button"
                variant="ghost"
                size="icon"
                className="shrink-0 rounded-full"
                disabled={submitting}
                onClick={onClose}
              >
                <X className="size-4" />
              </Button>
            </div>

            <div className="flex min-h-0 flex-1 flex-col gap-3 overflow-hidden px-5 py-4">
              {error ? <ApiErrorAlert message={error} /> : null}

              <div className="space-y-2">
                <Label htmlFor="caseSearch">بحث عن قضية</Label>
                <div className="relative">
                  <Search className="pointer-events-none absolute right-3 top-1/2 size-4 -translate-y-1/2 text-[var(--color-muted-foreground)]" />
                  <Input
                    id="caseSearch"
                    className="pr-10"
                    placeholder="رقم الملف، العنوان، العميل…"
                    value={search}
                    onChange={(e) => setSearch(e.target.value)}
                    disabled={submitting}
                  />
                </div>
              </div>

              <div className="space-y-2">
                <Label>دور الربط</Label>
                <div className="flex flex-wrap gap-2">
                  {DISPUTE_MANUAL_LINK_ROLE_OPTIONS.map((o) => (
                    <button
                      key={o.value}
                      type="button"
                      disabled={submitting}
                      onClick={() => setLinkRole(o.value)}
                      className={cn(
                        "rounded-xl border px-3 py-2 text-xs font-medium transition-colors",
                        linkRole === o.value
                          ? "border-[var(--color-gold-400)] bg-[var(--color-gold-400)]/12"
                          : "border-[var(--color-border)] bg-[var(--color-surface-muted)]/40 hover:bg-[var(--color-surface-muted)]/70",
                      )}
                    >
                      {disputeCaseLinkRoleAr(o.value)}
                    </button>
                  ))}
                </div>
              </div>

              <div className="min-h-0 flex-1 overflow-y-auto rounded-xl border border-[var(--color-border)] bg-[var(--color-surface-muted)]/20">
                {loadingList ? (
                  <p className="p-4 text-center text-sm text-[var(--color-muted-foreground)]">
                    جارٍ التحميل…
                  </p>
                ) : results.length === 0 ? (
                  <p className="p-4 text-center text-sm text-[var(--color-muted-foreground)]">
                    لا نتائج. جرّب كلمات بحث أخرى.
                  </p>
                ) : (
                  <ul className="divide-y divide-[var(--color-border)]">
                    {results.map((c) => (
                      <li key={c.id}>
                        <button
                          type="button"
                          disabled={submitting}
                          onClick={() => setSelectedCaseId(c.id)}
                          className={cn(
                            "flex w-full flex-col gap-0.5 px-4 py-3 text-right text-sm transition-colors",
                            selectedCaseId === c.id
                              ? "bg-[var(--color-gold-400)]/15"
                              : "hover:bg-[var(--color-surface)]/80",
                          )}
                        >
                          <span className="font-medium text-[var(--color-foreground)]">
                            {c.title}
                          </span>
                          <span className="font-mono text-xs text-[var(--color-muted-foreground)]">
                            {c.caseNumber}
                          </span>
                        </button>
                      </li>
                    ))}
                  </ul>
                )}
              </div>
            </div>

            <div className="flex justify-end gap-2 border-t border-[var(--color-border)] bg-[var(--color-surface-muted)]/30 px-5 py-3">
              <Button
                type="button"
                variant="outline"
                disabled={submitting}
                onClick={onClose}
              >
                إلغاء
              </Button>
              <Button
                type="button"
                variant="gold"
                disabled={submitting || !selectedCaseId}
                onClick={() => void submit()}
              >
                {submitting ? "جارٍ الربط…" : "تأكيد الربط"}
              </Button>
            </div>
          </motion.div>
        </motion.div>
      ) : null}
    </AnimatePresence>
  );
}
