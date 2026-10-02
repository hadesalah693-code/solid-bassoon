"use client";

import { useEffect, useState } from "react";
import { Search } from "lucide-react";

import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { casesApi } from "@/lib/api/cases";
import { disputesApi } from "@/lib/api/disputes";
import { useDebounce } from "@/hooks/use-debounce";
import {
  TASK_RELATION_LABEL_AR,
  relationKindFromType,
  type TaskRelationType,
} from "@/lib/tasks/task-relation";
import { cn } from "@/lib/utils/cn";
import type { CaseResponseDto } from "@/types/api";
import type { DisputeListItemDto } from "@/types/dispute";

const selectClassName = cn(
  "flex h-10 w-full cursor-pointer appearance-none rounded-xl border border-[var(--color-border)] bg-[var(--color-surface)] px-3.5 py-2 text-sm",
  "focus-visible:ring-2 focus-visible:ring-[var(--color-gold-400)]",
);

const RELATION_OPTIONS: { value: TaskRelationType; label: string }[] = [
  { value: "none", label: TASK_RELATION_LABEL_AR.standalone },
  { value: "case", label: TASK_RELATION_LABEL_AR.case },
  { value: "dispute", label: TASK_RELATION_LABEL_AR.dispute },
];

type Props = {
  relationType: TaskRelationType;
  onRelationTypeChange: (value: TaskRelationType) => void;
  selectedCaseId: string | null;
  onSelectedCaseIdChange: (id: string | null) => void;
  selectedDisputeId: string | null;
  onSelectedDisputeIdChange: (id: string | null) => void;
  disabled?: boolean;
  /** When set from case/dispute pages — hide picker, show locked label only. */
  lockedRelationType?: TaskRelationType | null;
};

export function TaskRelationSelector({
  relationType,
  onRelationTypeChange,
  selectedCaseId,
  onSelectedCaseIdChange,
  selectedDisputeId,
  onSelectedDisputeIdChange,
  disabled,
  lockedRelationType = null,
}: Props) {
  const [caseSearch, setCaseSearch] = useState("");
  const [disputeSearch, setDisputeSearch] = useState("");
  const debouncedCaseSearch = useDebounce(caseSearch, 300);
  const debouncedDisputeSearch = useDebounce(disputeSearch, 300);
  const [caseResults, setCaseResults] = useState<CaseResponseDto[]>([]);
  const [disputeResults, setDisputeResults] = useState<DisputeListItemDto[]>([]);
  const [loadingCases, setLoadingCases] = useState(false);
  const [loadingDisputes, setLoadingDisputes] = useState(false);

  const effectiveType = lockedRelationType ?? relationType;
  const isLocked = lockedRelationType != null;

  useEffect(() => {
    if (effectiveType !== "case" || isLocked) return;
    let cancelled = false;
    setLoadingCases(true);
    casesApi
      .list({
        search: debouncedCaseSearch.trim() || undefined,
        page: 0,
        size: 40,
        includeArchived: false,
        sort: "updatedAt,desc",
      })
      .then((page) => {
        if (cancelled) return;
        setCaseResults(
          page.content.filter((c) => c.status !== "ARCHIVED"),
        );
      })
      .catch(() => {
        if (!cancelled) setCaseResults([]);
      })
      .finally(() => {
        if (!cancelled) setLoadingCases(false);
      });
    return () => {
      cancelled = true;
    };
  }, [effectiveType, debouncedCaseSearch, isLocked]);

  useEffect(() => {
    if (effectiveType !== "dispute" || isLocked) return;
    let cancelled = false;
    setLoadingDisputes(true);
    disputesApi
      .list({
        search: debouncedDisputeSearch.trim() || undefined,
        page: 0,
        size: 40,
      })
      .then((page) => {
        if (cancelled) return;
        setDisputeResults(page.content);
      })
      .catch(() => {
        if (!cancelled) setDisputeResults([]);
      })
      .finally(() => {
        if (!cancelled) setLoadingDisputes(false);
      });
    return () => {
      cancelled = true;
    };
  }, [effectiveType, debouncedDisputeSearch, isLocked]);

  function handleRelationChange(next: TaskRelationType) {
    if (isLocked) return;
    onRelationTypeChange(next);
    if (next !== "case") onSelectedCaseIdChange(null);
    if (next !== "dispute") onSelectedDisputeIdChange(null);
    if (next === "case") setDisputeSearch("");
    if (next === "dispute") setCaseSearch("");
  }

  return (
    <div className="space-y-3 md:col-span-2">
      <div className="space-y-1.5">
        <Label>نوع الربط</Label>
        {isLocked ? (
          <p className="rounded-xl border border-[var(--color-border)] bg-[var(--color-surface-muted)]/40 px-3.5 py-2.5 text-sm text-[var(--color-foreground)]">
            {TASK_RELATION_LABEL_AR[relationKindFromType(effectiveType)]}
          </p>
        ) : (
          <select
            className={selectClassName}
            value={relationType}
            disabled={disabled}
            onChange={(e) =>
              handleRelationChange(e.target.value as TaskRelationType)
            }
          >
            {RELATION_OPTIONS.map((o) => (
              <option key={o.value} value={o.value}>
                {o.label}
              </option>
            ))}
          </select>
        )}
      </div>

      {!isLocked && relationType === "case" ? (
        <div className="space-y-2 rounded-xl border border-[var(--color-border)] bg-[var(--color-surface-muted)]/20 p-3">
          <Label htmlFor="task-case-search">اختر قضية</Label>
          <div className="relative">
            <Search className="pointer-events-none absolute right-3 top-1/2 size-4 -translate-y-1/2 text-[var(--color-muted-foreground)]" />
            <Input
              id="task-case-search"
              className="pr-10"
              placeholder="رقم الملف، العنوان…"
              value={caseSearch}
              onChange={(e) => setCaseSearch(e.target.value)}
              disabled={disabled}
            />
          </div>
          <div className="max-h-40 overflow-y-auto rounded-lg border border-[var(--color-border)] bg-[var(--color-surface)]">
            {loadingCases ? (
              <p className="p-3 text-center text-xs text-[var(--color-muted-foreground)]">
                جارٍ التحميل…
              </p>
            ) : caseResults.length === 0 ? (
              <p className="p-3 text-center text-xs text-[var(--color-muted-foreground)]">
                لا نتائج
              </p>
            ) : (
              <ul className="divide-y divide-[var(--color-border)]">
                {caseResults.map((c) => (
                  <li key={c.id}>
                    <button
                      type="button"
                      disabled={disabled}
                      onClick={() => onSelectedCaseIdChange(c.id)}
                      className={cn(
                        "flex w-full flex-col gap-0.5 px-3 py-2 text-right text-sm transition-colors",
                        selectedCaseId === c.id
                          ? "bg-[var(--color-gold-400)]/15"
                          : "hover:bg-[var(--color-surface-muted)]/60",
                      )}
                    >
                      <span className="font-medium">{c.title}</span>
                      <span className="font-mono text-xs text-[var(--color-muted-foreground)]">
                        {c.caseNumber}
                      </span>
                    </button>
                  </li>
                ))}
              </ul>
            )}
          </div>
        </div>
      ) : null}

      {!isLocked && relationType === "dispute" ? (
        <div className="space-y-2 rounded-xl border border-[var(--color-border)] bg-[var(--color-surface-muted)]/20 p-3">
          <Label htmlFor="task-dispute-search">اختر نزاعاً</Label>
          <div className="relative">
            <Search className="pointer-events-none absolute right-3 top-1/2 size-4 -translate-y-1/2 text-[var(--color-muted-foreground)]" />
            <Input
              id="task-dispute-search"
              className="pr-10"
              placeholder="رقم النزاع، العنوان، الخصم…"
              value={disputeSearch}
              onChange={(e) => setDisputeSearch(e.target.value)}
              disabled={disabled}
            />
          </div>
          <div className="max-h-40 overflow-y-auto rounded-lg border border-[var(--color-border)] bg-[var(--color-surface)]">
            {loadingDisputes ? (
              <p className="p-3 text-center text-xs text-[var(--color-muted-foreground)]">
                جارٍ التحميل…
              </p>
            ) : disputeResults.length === 0 ? (
              <p className="p-3 text-center text-xs text-[var(--color-muted-foreground)]">
                لا نتائج
              </p>
            ) : (
              <ul className="divide-y divide-[var(--color-border)]">
                {disputeResults.map((d) => (
                  <li key={d.id}>
                    <button
                      type="button"
                      disabled={disabled}
                      onClick={() => onSelectedDisputeIdChange(d.id)}
                      className={cn(
                        "flex w-full flex-col gap-0.5 px-3 py-2 text-right text-sm transition-colors",
                        selectedDisputeId === d.id
                          ? "bg-[var(--color-gold-400)]/15"
                          : "hover:bg-[var(--color-surface-muted)]/60",
                      )}
                    >
                      <span className="font-medium">{d.title}</span>
                      <span className="font-mono text-xs text-[var(--color-muted-foreground)]">
                        {d.disputeNumber}
                      </span>
                    </button>
                  </li>
                ))}
              </ul>
            )}
          </div>
        </div>
      ) : null}
    </div>
  );
}
