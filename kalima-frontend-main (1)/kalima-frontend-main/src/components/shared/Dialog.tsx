"use client";

import { AnimatePresence, motion } from "framer-motion";
import { X } from "lucide-react";
import { useEffect, type ReactNode } from "react";

import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils/cn";

/**
 * Generic modal shell. The project has no `ui/dialog` primitive (no
 * @radix-ui/react-dialog), so every dialog is a hand-rolled framer-motion
 * overlay — this centralises that pattern. AccountingDialog and HR screens both
 * build on it.
 */
export type DialogProps = {
  open: boolean;
  onClose: () => void;
  title: string;
  description?: string;
  icon?: ReactNode;
  children: ReactNode;
  footer?: ReactNode;
  size?: "sm" | "md" | "lg" | "xl";
  /** Allow the body to scroll independently of the header and footer. */
  scrollable?: boolean;
};

const sizeClass: Record<NonNullable<DialogProps["size"]>, string> = {
  sm: "max-w-md",
  md: "max-w-lg",
  lg: "max-w-2xl",
  xl: "max-w-4xl",
};

export function Dialog({
  open,
  onClose,
  title,
  description,
  icon,
  children,
  footer,
  size = "md",
  scrollable = false,
}: DialogProps) {
  useEffect(() => {
    if (!open) return;
    function onKey(e: KeyboardEvent) {
      if (e.key === "Escape") onClose();
    }
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [open, onClose]);

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
            aria-label={title}
            initial={{ opacity: 0, scale: 0.96, y: 10 }}
            animate={{ opacity: 1, scale: 1, y: 0 }}
            exit={{ opacity: 0, scale: 0.96, y: 10 }}
            transition={{ duration: 0.2 }}
            className={cn(
              "relative z-10 flex w-full flex-col gap-5 rounded-2xl border border-[var(--color-border)] bg-[var(--color-surface)] p-6 shadow-2xl",
              sizeClass[size],
            )}
          >
            <div className="flex items-start justify-between gap-3">
              <div className="flex min-w-0 items-start gap-3">
                {icon ? (
                  <div className="flex size-10 shrink-0 items-center justify-center rounded-xl bg-[var(--color-gold-50)] text-[var(--color-gold-600)]">
                    {icon}
                  </div>
                ) : null}
                <div className="min-w-0">
                  <h2 className="text-lg font-semibold text-[var(--color-foreground)]">{title}</h2>
                  {description ? (
                    <p className="text-sm text-[var(--color-muted-foreground)]">{description}</p>
                  ) : null}
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

            <div className={cn(scrollable && "max-h-[min(65vh,520px)] overflow-y-auto")}>
              {children}
            </div>

            {footer ? (
              <div className="flex flex-wrap justify-end gap-2 border-t border-[var(--color-border)] pt-4">
                {footer}
              </div>
            ) : null}
          </motion.div>
        </motion.div>
      ) : null}
    </AnimatePresence>
  );
}

export type DialogFooterProps = {
  onClose: () => void;
  submitting?: boolean;
  submitLabel: string;
  disabled?: boolean;
};

export function DialogFooter({
  onClose,
  submitting = false,
  submitLabel,
  disabled = false,
}: DialogFooterProps) {
  return (
    <>
      <Button type="button" variant="ghost" onClick={onClose} disabled={submitting}>
        إلغاء
      </Button>
      <Button type="submit" variant="gold" disabled={submitting || disabled}>
        {submitting ? "جارٍ الحفظ…" : submitLabel}
      </Button>
    </>
  );
}