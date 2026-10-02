import { Info } from "lucide-react";

const PLACEHOLDERS: { token: string; label: string }[] = [
  { token: "{{company.name}}", label: "اسم المكتب الحالي" },
  { token: "{{client.name}}", label: "اسم الموكِّل (إن وُجد)" },
  { token: "{{case.number}}", label: "رقم القضية الداخلي (إن وُجدت)" },
  { token: "{{today}}", label: "تاريخ اليوم بصيغة yyyy-MM-dd" },
];

/**
 * Compact reference card showing the four supported placeholders. Kept
 * inline (not a separate page) so authors see it next to the textarea.
 *
 * Source of truth for the rendering pipeline:
 *   {@code DocumentGenerationService#render}
 */
export function PlaceholderHelp() {
  return (
    <div className="rounded-xl border border-[var(--color-border)] bg-[var(--color-surface-muted)]/40 p-4">
      <div className="mb-3 flex items-center gap-2 text-sm font-medium text-[var(--color-foreground)]">
        <Info className="size-4 text-[var(--color-gold-600)]" />
        المتغيّرات المدعومة
      </div>
      <p className="mb-3 text-xs text-[var(--color-muted-foreground)]">
        ضع أيًّا من هذه الرموز داخل المحتوى، وسيتم استبدالها تلقائيًا عند توليد
        المستند. الرموز غير المدعومة تبقى كما هي.
      </p>
      <ul className="grid gap-2 sm:grid-cols-2">
        {PLACEHOLDERS.map((p) => (
          <li
            key={p.token}
            className="flex flex-col gap-1 rounded-lg border border-[var(--color-border)] bg-[var(--color-surface)] px-3 py-2"
          >
            <code className="font-mono text-xs text-[var(--color-navy-700)]">
              {p.token}
            </code>
            <span className="text-xs text-[var(--color-muted-foreground)]">
              {p.label}
            </span>
          </li>
        ))}
      </ul>
    </div>
  );
}
