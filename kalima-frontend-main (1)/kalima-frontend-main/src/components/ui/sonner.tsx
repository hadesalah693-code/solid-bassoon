"use client";

import { Toaster as Sonner, type ToasterProps } from "sonner";

export function Toaster(props: ToasterProps) {
  return (
    <Sonner
      className="toaster group"
      dir="rtl"
      position="top-center"
      richColors
      closeButton
      toastOptions={{
        duration: 4000,
        classNames: {
          toast:
            "items-start gap-3 border border-[var(--color-border)] bg-[var(--color-surface)] text-[var(--color-foreground)] shadow-[var(--shadow-medium)] rounded-xl",
          title: "text-start text-sm font-bold",
          description: "text-start text-xs text-[var(--color-muted-foreground)]",
          success: "border-[var(--color-success-border)]",
          error: "border-[var(--color-danger-border)]",
          warning: "border-[var(--color-warning-border)]",
          info: "border-[var(--color-info-border)]",
        },
      }}
      {...props}
    />
  );
}
