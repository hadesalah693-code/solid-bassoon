"use client";

/** Minimal placeholder while auth bootstrap or redirect is in progress. */
export function RouteGuardShell({ label = "جارٍ التحقق…" }: { label?: string }) {
  return (
    <div className="flex min-h-screen items-center justify-center bg-[var(--color-background)]">
      <p className="text-sm text-[var(--color-muted-foreground)]">{label}</p>
    </div>
  );
}
