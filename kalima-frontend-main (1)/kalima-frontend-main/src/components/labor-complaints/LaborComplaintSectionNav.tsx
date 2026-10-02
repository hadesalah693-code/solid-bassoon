"use client";

import { useEffect, useState } from "react";

import { cn } from "@/lib/utils/cn";

const SECTIONS = [
  { id: "lc-details", label: "التفاصيل" },
  { id: "lc-content", label: "المحتوى" },
  { id: "lc-parties", label: "الأطراف" },
  { id: "lc-sessions", label: "الجلسات" },
  { id: "lc-documents", label: "المرفقات" },
] as const;

export function LaborComplaintSectionNav() {
  const [active, setActive] = useState<string>(SECTIONS[0].id);

  useEffect(() => {
    const observers: IntersectionObserver[] = [];

    for (const { id } of SECTIONS) {
      const el = document.getElementById(id);
      if (!el) continue;

      const observer = new IntersectionObserver(
        (entries) => {
          for (const entry of entries) {
            if (entry.isIntersecting) setActive(id);
          }
        },
        { rootMargin: "-18% 0px -55% 0px", threshold: 0 },
      );

      observer.observe(el);
      observers.push(observer);
    }

    return () => {
      for (const observer of observers) observer.disconnect();
    };
  }, []);

  return (
    <nav
      aria-label="أقسام الشكوى"
      className="sticky top-0 z-20 overflow-x-auto border-b border-[var(--color-border)] bg-[var(--color-background)]/95 backdrop-blur-sm"
    >
      <div className="flex gap-1 py-1">
        {SECTIONS.map(({ id, label }) => {
          const isActive = active === id;
          return (
            <a
              key={id}
              href={`#${id}`}
              className={cn(
                "shrink-0 rounded-lg px-4 py-2 text-sm font-medium transition-colors",
                "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--color-gold-400)]",
                isActive
                  ? "bg-[var(--color-surface)] text-[var(--color-foreground)] shadow-sm ring-1 ring-[var(--color-border)]"
                  : "text-[var(--color-muted-foreground)] hover:text-[var(--color-foreground)]",
              )}
              aria-current={isActive ? "true" : undefined}
            >
              {label}
            </a>
          );
        })}
      </div>
    </nav>
  );
}
