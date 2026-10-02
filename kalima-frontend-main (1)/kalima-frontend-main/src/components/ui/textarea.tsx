import * as React from "react";
import { cn } from "@/lib/utils/cn";

export type TextareaProps = React.TextareaHTMLAttributes<HTMLTextAreaElement>;

export const Textarea = React.forwardRef<HTMLTextAreaElement, TextareaProps>(
  ({ className, rows = 4, ...props }, ref) => (
    <textarea
      ref={ref}
      rows={rows}
      className={cn(
        "flex min-h-[100px] w-full resize-y rounded-xl border border-[var(--color-border)] bg-[var(--color-surface)] px-3.5 py-2.5 text-sm text-[var(--color-foreground)] shadow-[var(--shadow-xs)] transition-[border-color,box-shadow] duration-200",
        "placeholder:text-[var(--color-muted-foreground)]",
        "hover:border-[var(--color-border-strong)]",
        "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--color-gold-400)]/30 focus-visible:border-[var(--color-gold-400)]",
        "disabled:cursor-not-allowed disabled:opacity-50 disabled:bg-[var(--color-surface-muted)]",
        className,
      )}
      {...props}
    />
  ),
);
Textarea.displayName = "Textarea";
