"use client";

import { useEffect, useState } from "react";
import { Plus, Search, Trash2 } from "lucide-react";

import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { casesApi } from "@/lib/api/cases";
import { disputesApi } from "@/lib/api/disputes";
import { personsApi } from "@/lib/api/persons";
import { tasksApi } from "@/lib/api/tasks";
import { useDebounce } from "@/hooks/use-debounce";
import { cn } from "@/lib/utils/cn";
import { ocrRelationTypeLabel } from "@/lib/ocr/labels";
import {
  OCR_RELATION_TYPES,
  type OcrRelationInput,
  type OcrRelationType,
} from "@/types/ocr";
import type { CaseResponseDto } from "@/types/api";
import type { DisputeListItemDto } from "@/types/dispute";
import type { ClientPerson } from "@/types/person";
import type { TaskResponseDto } from "@/types/api";

const selectClassName = cn(
  "flex h-10 w-full cursor-pointer appearance-none rounded-xl border border-[var(--color-border)] bg-[var(--color-surface)] px-3.5 py-2 text-sm",
  "focus-visible:ring-2 focus-visible:ring-[var(--color-gold-400)]",
);

type Props = {
  value: OcrRelationInput[];
  onChange: (next: OcrRelationInput[]) => void;
  disabled?: boolean;
};

type Draft = {
  relationType: OcrRelationType;
  relationId: string;
};

export function OcrRelationPicker({ value, onChange, disabled }: Props) {
  const [draft, setDraft] = useState<Draft>({
    relationType: "CASE",
    relationId: "",
  });
  const [search, setSearch] = useState("");
  const debouncedSearch = useDebounce(search, 300);
  const [cases, setCases] = useState<CaseResponseDto[]>([]);
  const [disputes, setDisputes] = useState<DisputeListItemDto[]>([]);
  const [clients, setClients] = useState<ClientPerson[]>([]);
  const [tasks, setTasks] = useState<TaskResponseDto[]>([]);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (draft.relationType === "DOCUMENT") return;
    let cancelled = false;
    setLoading(true);
    const q = debouncedSearch.trim() || undefined;
    (async () => {
      try {
        if (draft.relationType === "CASE") {
          const page = await casesApi.list({
            search: q,
            page: 0,
            size: 30,
            includeArchived: false,
          });
          if (!cancelled) setCases(page.content.filter((c) => c.status !== "ARCHIVED"));
        } else if (draft.relationType === "DISPUTE") {
          const page = await disputesApi.list({ search: q, page: 0, size: 30 });
          if (!cancelled) setDisputes(page.content);
        } else if (draft.relationType === "CLIENT") {
          const page = await personsApi.list({
            search: q,
            role: "CLIENT",
            page: 0,
            size: 30,
          });
          if (!cancelled) setClients(page.content);
        } else if (draft.relationType === "TASK") {
          const page = await tasksApi.list({ page: 0, size: 30 });
          if (!cancelled) {
            const filtered = q
              ? page.content.filter((t) =>
                  t.title.toLowerCase().includes(q.toLowerCase()),
                )
              : page.content;
            setTasks(filtered);
          }
        }
      } catch {
        if (!cancelled) {
          setCases([]);
          setDisputes([]);
          setClients([]);
          setTasks([]);
        }
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [draft.relationType, debouncedSearch]);

  function addRelation() {
    const id = draft.relationId.trim();
    if (!id) return;
    const exists = value.some(
      (r) => r.relationType === draft.relationType && r.relationId === id,
    );
    if (exists) return;
    onChange([...value, { relationType: draft.relationType, relationId: id }]);
    setDraft((d) => ({ ...d, relationId: "" }));
    setSearch("");
  }

  function removeAt(index: number) {
    onChange(value.filter((_, i) => i !== index));
  }

  function pickId(id: string) {
    setDraft((d) => ({ ...d, relationId: id }));
  }

  function renderResults() {
    if (draft.relationType === "DOCUMENT") {
      return (
        <div className="space-y-1.5">
          <Label htmlFor="ocr-doc-relation-id">معرّف المستند</Label>
          <Input
            id="ocr-doc-relation-id"
            value={draft.relationId}
            onChange={(e) => setDraft((d) => ({ ...d, relationId: e.target.value }))}
            placeholder="UUID للمستند المرتبط"
            disabled={disabled}
            className="font-mono text-xs ltr-nums"
          />
        </div>
      );
    }

    const list =
      draft.relationType === "CASE"
        ? cases.map((c) => ({
            id: c.id,
            title: c.title,
            sub: c.caseNumber,
          }))
        : draft.relationType === "DISPUTE"
          ? disputes.map((d) => ({
              id: d.id,
              title: d.title,
              sub: d.disputeNumber,
            }))
          : draft.relationType === "CLIENT"
            ? clients.map((c) => ({
                id: c.id,
                title: c.fullName ?? "—",
                sub: c.email ?? "",
              }))
            : tasks.map((t) => ({
                id: t.id,
                title: t.title,
                sub: t.status,
              }));

    return (
      <>
        <div className="relative">
          <Search className="pointer-events-none absolute right-3 top-1/2 size-4 -translate-y-1/2 text-[var(--color-muted-foreground)]" />
          <Input
            className="pr-10"
            placeholder="بحث…"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            disabled={disabled}
          />
        </div>
        <div className="max-h-36 overflow-y-auto rounded-lg border border-[var(--color-border)] bg-[var(--color-surface)]">
          {loading ? (
            <p className="p-3 text-center text-xs text-[var(--color-muted-foreground)]">
              جارٍ التحميل…
            </p>
          ) : list.length === 0 ? (
            <p className="p-3 text-center text-xs text-[var(--color-muted-foreground)]">
              لا نتائج
            </p>
          ) : (
            <ul className="divide-y divide-[var(--color-border)]">
              {list.map((item) => (
                <li key={item.id}>
                  <button
                    type="button"
                    disabled={disabled}
                    onClick={() => pickId(item.id)}
                    className={cn(
                      "flex w-full flex-col gap-0.5 px-3 py-2 text-right text-sm transition-colors",
                      draft.relationId === item.id
                        ? "bg-[var(--color-gold-400)]/15"
                        : "hover:bg-[var(--color-surface-muted)]/60",
                    )}
                  >
                    <span className="font-medium">{item.title}</span>
                    {item.sub ? (
                      <span className="text-xs text-[var(--color-muted-foreground)]">
                        {item.sub}
                      </span>
                    ) : null}
                  </button>
                </li>
              ))}
            </ul>
          )}
        </div>
      </>
    );
  }

  return (
    <div className="space-y-3 rounded-xl border border-[var(--color-border)] bg-[var(--color-surface-muted)]/15 p-4">
      <div>
        <Label>الربط بالسجلات</Label>
        <p className="mt-0.5 text-xs text-[var(--color-muted-foreground)]">
          اربط المستند بقضية أو عميل أو نزاع (اختياري).
        </p>
      </div>

      {value.length > 0 ? (
        <ul className="space-y-2">
          {value.map((rel, index) => (
            <li
              key={`${rel.relationType}-${rel.relationId}`}
              className="flex items-center justify-between gap-2 rounded-lg border border-[var(--color-border)] bg-[var(--color-surface)] px-3 py-2 text-sm"
            >
              <span>
                {ocrRelationTypeLabel(rel.relationType)}
                <span className="ms-2 font-mono text-xs text-[var(--color-muted-foreground)] ltr-nums">
                  {rel.relationId.slice(0, 8)}…
                </span>
              </span>
              <Button
                type="button"
                variant="ghost"
                size="sm"
                disabled={disabled}
                onClick={() => removeAt(index)}
                aria-label="إزالة الربط"
              >
                <Trash2 className="size-4" />
              </Button>
            </li>
          ))}
        </ul>
      ) : null}

      <div className="grid gap-3 md:grid-cols-2">
        <div className="space-y-1.5">
          <Label htmlFor="ocr-rel-type">نوع الربط</Label>
          <select
            id="ocr-rel-type"
            className={selectClassName}
            value={draft.relationType}
            disabled={disabled}
            onChange={(e) => {
              const relationType = e.target.value as OcrRelationType;
              setDraft({ relationType, relationId: "" });
              setSearch("");
            }}
          >
            {OCR_RELATION_TYPES.map((t) => (
              <option key={t} value={t}>
                {ocrRelationTypeLabel(t)}
              </option>
            ))}
          </select>
        </div>
        <div className="space-y-2">{renderResults()}</div>
      </div>

      <Button
        type="button"
        variant="outline"
        size="sm"
        disabled={disabled || !draft.relationId.trim()}
        onClick={addRelation}
      >
        <Plus className="size-4" aria-hidden />
        إضافة ربط
      </Button>
    </div>
  );
}
