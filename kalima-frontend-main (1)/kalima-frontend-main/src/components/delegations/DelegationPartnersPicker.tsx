"use client";

import { Label } from "@/components/ui/label";
import { cn } from "@/lib/utils/cn";
import type { ClientPerson } from "@/types/person";

type Props = {
  clients: ClientPerson[];
  excludeClientId?: string;
  selectedIds: string[];
  onChange: (ids: string[]) => void;
  className?: string;
};

function clientLabel(p: ClientPerson) {
  if (p.personType === "COMPANY") return p.companyName?.trim() || p.id;
  return p.fullName?.trim() || p.id;
}

export function DelegationPartnersPicker({
  clients,
  excludeClientId,
  selectedIds,
  onChange,
  className,
}: Props) {
  const options = clients.filter((c) => c.id !== excludeClientId);

  function toggle(id: string) {
    onChange(
      selectedIds.includes(id)
        ? selectedIds.filter((x) => x !== id)
        : [...selectedIds, id],
    );
  }

  return (
    <div className={cn("space-y-2", className)}>
      <Label>الشركاء (اختياري)</Label>
      {options.length === 0 ? (
        <p className="text-xs text-[var(--color-muted-foreground)]">لا يوجد عملاء إضافيون للاختيار.</p>
      ) : (
        <div className="grid gap-2 sm:grid-cols-2 lg:grid-cols-3">
          {options.map((c) => (
            <label
              key={c.id}
              className={cn(
                "flex cursor-pointer items-center gap-2 rounded-lg border px-3 py-2 text-sm",
                selectedIds.includes(c.id)
                  ? "border-[var(--color-gold-400)]/60 bg-[var(--color-gold-50)]/50"
                  : "border-[var(--color-border)]",
              )}
            >
              <input
                type="checkbox"
                checked={selectedIds.includes(c.id)}
                onChange={() => toggle(c.id)}
              />
              {clientLabel(c)}
            </label>
          ))}
        </div>
      )}
    </div>
  );
}
