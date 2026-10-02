"use client";

import { useMemo, useRef, useState } from "react";

import { Textarea } from "@/components/ui/textarea";
import { cn } from "@/lib/utils/cn";

export type MentionStaff = {
  id: string;
  name: string;
};

type Props = {
  id?: string;
  value: string;
  onChange: (value: string) => void;
  staff: MentionStaff[];
  rows?: number;
  placeholder?: string;
  className?: string;
};

export function StaffMentionTextarea({
  id,
  value,
  onChange,
  staff,
  rows = 3,
  placeholder = "اكتب @ لإشارة موظف…",
  className,
}: Props) {
  const ref = useRef<HTMLTextAreaElement | null>(null);
  const [query, setQuery] = useState<string | null>(null);
  const [anchor, setAnchor] = useState(0);

  const suggestions = useMemo(() => {
    if (query === null) return [];
    const q = query.toLowerCase();
    return staff
      .filter((s) => s.name.toLowerCase().includes(q))
      .slice(0, 8);
  }, [query, staff]);

  function detectMention(next: string, cursor: number) {
    const before = next.slice(0, cursor);
    const match = before.match(/@([^\s@]*)$/);
    if (match) {
      setQuery(match[1]);
      setAnchor(cursor - match[1].length - 1);
    } else {
      setQuery(null);
    }
  }

  function insertMention(person: MentionStaff) {
    const el = ref.current;
    if (!el || query === null) return;
    const cursor = el.selectionStart ?? value.length;
    const before = value.slice(0, anchor);
    const after = value.slice(cursor);
    const mention = `@${person.name} `;
    const next = `${before}${mention}${after}`;
    onChange(next);
    setQuery(null);
    requestAnimationFrame(() => {
      const pos = before.length + mention.length;
      el.focus();
      el.setSelectionRange(pos, pos);
    });
  }

  return (
    <div className={cn("relative", className)}>
      <Textarea
        id={id}
        ref={ref}
        rows={rows}
        value={value}
        placeholder={placeholder}
        onChange={(e) => {
          onChange(e.target.value);
          detectMention(e.target.value, e.target.selectionStart ?? e.target.value.length);
        }}
        onKeyUp={(e) => {
          const el = e.currentTarget;
          detectMention(el.value, el.selectionStart ?? el.value.length);
        }}
        onBlur={() => {
          window.setTimeout(() => setQuery(null), 150);
        }}
      />
      {query !== null && suggestions.length > 0 ? (
        <ul
          className="absolute z-20 mt-1 max-h-48 w-full overflow-auto rounded-xl border border-[var(--color-border)] bg-[var(--color-surface)] py-1 shadow-[var(--shadow-pop)]"
          role="listbox"
        >
          {suggestions.map((s) => (
            <li key={s.id}>
              <button
                type="button"
                className="w-full px-3 py-2 text-start text-sm hover:bg-[var(--color-surface-muted)]"
                onMouseDown={(e) => {
                  e.preventDefault();
                  insertMention(s);
                }}
              >
                @{s.name}
              </button>
            </li>
          ))}
        </ul>
      ) : null}
      <p className="mt-1 text-xs text-[var(--color-muted-foreground)]">
        اكتب <span className="font-mono">@</span> ثم اسم الموظف للإشارة إليه في الملاحظات.
      </p>
    </div>
  );
}
