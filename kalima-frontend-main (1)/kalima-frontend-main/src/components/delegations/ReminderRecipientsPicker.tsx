"use client";

import { useMemo, useState } from "react";

import { cn } from "@/lib/utils/cn";
import type { MentionStaff } from "@/components/delegations/StaffMentionTextarea";

type Props = {
  staff: MentionStaff[];
  selectedIds: string[];
  onChange: (ids: string[]) => void;
};

export function ReminderRecipientsPicker({ staff, selectedIds, onChange }: Props) {
  const [mentionQuery, setMentionQuery] = useState("");

  const filtered = useMemo(() => {
    const q = mentionQuery.replace(/^@/, "").trim().toLowerCase();
    if (!q) return staff;
    return staff.filter((s) => s.name.toLowerCase().includes(q));
  }, [mentionQuery, staff]);

  function toggle(id: string) {
    onChange(
      selectedIds.includes(id)
        ? selectedIds.filter((x) => x !== id)
        : [...selectedIds, id],
    );
  }

  function addFromMention(person: MentionStaff) {
    if (!selectedIds.includes(person.id)) {
      onChange([...selectedIds, person.id]);
    }
    setMentionQuery("");
  }

  return (
    <div className="space-y-3">
      <div className="relative">
        <input
          type="text"
          className="h-10 w-full rounded-lg border border-[var(--color-border)] bg-[var(--color-surface)] px-3 text-sm"
          placeholder="ابحث أو اكتب @اسم لإضافة مستلم…"
          value={mentionQuery}
          onChange={(e) => setMentionQuery(e.target.value)}
        />
        {mentionQuery.trim() ? (
          <ul className="absolute z-10 mt-1 max-h-40 w-full overflow-auto rounded-xl border border-[var(--color-border)] bg-[var(--color-surface)] py-1 shadow-[var(--shadow-pop)]">
            {filtered.slice(0, 8).map((s) => (
              <li key={s.id}>
                <button
                  type="button"
                  className="w-full px-3 py-2 text-start text-sm hover:bg-[var(--color-surface-muted)]"
                  onClick={() => addFromMention(s)}
                >
                  @{s.name}
                </button>
              </li>
            ))}
          </ul>
        ) : null}
      </div>
      <div className="grid gap-2 sm:grid-cols-2 lg:grid-cols-3">
        {staff.map((s) => (
          <label
            key={s.id}
            className={cn(
              "flex cursor-pointer items-center gap-2 rounded-lg border px-3 py-2 text-sm",
              selectedIds.includes(s.id)
                ? "border-[var(--color-gold-400)]/60 bg-[var(--color-gold-50)]/50"
                : "border-[var(--color-border)]",
            )}
          >
            <input
              type="checkbox"
              checked={selectedIds.includes(s.id)}
              onChange={() => toggle(s.id)}
            />
            {s.name}
          </label>
        ))}
      </div>
    </div>
  );
}
