"use client";

import { Label } from "@/components/ui/label";
import { cn } from "@/lib/utils/cn";

export type AgentOption = {
  id: string;
  name: string;
};

type Props = {
  attorneys: AgentOption[];
  selectedIds: string[];
  onChange: (ids: string[]) => void;
  className?: string;
};

export function DelegationAgentsPicker({
  attorneys,
  selectedIds,
  onChange,
  className,
}: Props) {
  function toggle(id: string) {
    onChange(
      selectedIds.includes(id)
        ? selectedIds.filter((x) => x !== id)
        : [...selectedIds, id],
    );
  }

  return (
    <div className={cn("space-y-2", className)}>
      <Label>الوكلاء</Label>
      {attorneys.length === 0 ? (
        <p className="text-xs text-[var(--color-muted-foreground)]">
          لم يُضبط وكلاء بالمكتب — أضفهم من إعدادات التوكيلات.
        </p>
      ) : (
        <div className="grid gap-2 sm:grid-cols-2 lg:grid-cols-3">
          {attorneys.map((a) => (
            <label
              key={a.id}
              className={cn(
                "flex cursor-pointer items-center gap-2 rounded-lg border px-3 py-2 text-sm",
                selectedIds.includes(a.id)
                  ? "border-[var(--color-gold-400)]/60 bg-[var(--color-gold-50)]/50"
                  : "border-[var(--color-border)]",
              )}
            >
              <input
                type="checkbox"
                checked={selectedIds.includes(a.id)}
                onChange={() => toggle(a.id)}
              />
              {a.name}
            </label>
          ))}
        </div>
      )}
      {selectedIds.length > 1 ? (
        <p className="text-xs text-[var(--color-muted-foreground)]">
          الوكيل الأول يُستخدم كوكيل رئيسي في التقويم والبحث.
        </p>
      ) : null}
    </div>
  );
}
