"use client";

import { useCallback, useEffect, useState } from "react";
import { Users } from "lucide-react";

import { SearchInput } from "@/components/shared/SearchInput";
import { PageLoading } from "@/components/shared/PageLoading";
import { ApiErrorAlert } from "@/components/shared/ApiErrorAlert";
import { SectionCard } from "@/components/shared/SectionCard";
import { Button } from "@/components/ui/button";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { cn } from "@/lib/utils/cn";
import { caseParticipantsApi } from "@/lib/api/case-participants";
import { personsApi } from "@/lib/api/persons";
import { ApiRequestError } from "@/lib/api/client";
import { getStoredUser } from "@/lib/auth/session";
import { notifyApiError, notifyError, notifySuccess } from "@/lib/feedback";
import { useDebounce } from "@/hooks/use-debounce";
import type { ClientPerson } from "@/types/person";
import type {
  CaseParticipantRoleDto,
  ParticipantResponseDto,
} from "@/types/api";

const selectClassName = cn(
  "flex h-10 w-full cursor-pointer appearance-none rounded-xl border border-[var(--color-border)] bg-[var(--color-surface)] px-3.5 py-2 text-sm text-[var(--color-foreground)] shadow-sm",
  "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--color-gold-400)]",
  "disabled:cursor-not-allowed disabled:opacity-50",
);

const ADD_ROLE_OPTIONS: { value: CaseParticipantRoleDto; label: string }[] = [
  { value: "SECONDARY_LAWYER", label: "محامٍ مساعد" },
  { value: "OPPOSING_PARTY", label: "الخصم / الطرف المقابل" },
  { value: "WITNESS", label: "شاهد" },
  { value: "EXPERT", label: "خبير" },
  { value: "JUDGE", label: "قاضٍ" },
  { value: "COURT_OFFICIAL", label: "موظفو المحكمة" },
  { value: "THIRD_PARTY", label: "طرف ثالث" },
  { value: "CLIENT", label: "موكّل (إضافي)" },
  { value: "ASSIGNED_LAWYER", label: "محامٍ مسند (تكرار الدور)" },
];

const ROLE_AR: Record<CaseParticipantRoleDto, string> = {
  CLIENT: "موكّل",
  ASSIGNED_LAWYER: "محامٍ مسند",
  SECONDARY_LAWYER: "محامٍ مساعد",
  OPPOSING_PARTY: "خصم / مقابل",
  WITNESS: "شاهد",
  EXPERT: "خبير",
  JUDGE: "قاضٍ",
  COURT_OFFICIAL: "موظف محكمة",
  THIRD_PARTY: "طرف ثالث",
};

function personLabel(p: ClientPerson) {
  if (p.personType === "COMPANY") {
    return p.companyName?.trim() ?? p.id;
  }
  return p.fullName?.trim() ?? p.id;
}

type Props = {
  caseId: string;
  canEdit: boolean;
  onChange: () => void;
};

export function CaseParticipantsPanel({ caseId, canEdit, onChange }: Props) {
  const [items, setItems] = useState<ParticipantResponseDto[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [loadId, setLoadId] = useState(0);
  const [search, setSearch] = useState("");
  const debounced = useDebounce(search, 300);
  const [candidates, setCandidates] = useState<ClientPerson[]>([]);
  const [personId, setPersonId] = useState("");
  const [role, setRole] = useState<CaseParticipantRoleDto>("SECONDARY_LAWYER");
  const [notes, setNotes] = useState("");
  const [saving, setSaving] = useState(false);
  const [u, setU] = useState<ReturnType<typeof getStoredUser>>();

  useEffect(() => {
    setU(getStoredUser());
  }, []);

  const canRemove = Boolean(
    u && (u.systemRole === "ADMIN" || u.systemRole === "SENIOR_LAWYER"),
  );
  // POST /api/cases/{caseId}/participants allows ADMIN, SENIOR_LAWYER, LAWYER, SECRETARY.
  const canAdd = Boolean(
    u &&
      (u.systemRole === "ADMIN" ||
        u.systemRole === "SENIOR_LAWYER" ||
        u.systemRole === "LAWYER" ||
        u.systemRole === "SECRETARY"),
  );
  const showAddForm = canEdit && canAdd;

  const refresh = useCallback(() => {
    setLoadId((n) => n + 1);
  }, []);

  useEffect(() => {
    if (!showAddForm) {
      return;
    }
    let cancelled = false;
    (async () => {
      try {
        const res = await personsApi.list({
          page: 0,
          size: 50,
          search: debounced.trim() || undefined,
        });
        if (cancelled) return;
        setCandidates(res.content.filter((p) => p.active));
      } catch {
        if (!cancelled) setCandidates([]);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [debounced, showAddForm]);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      setLoading(true);
      setError(null);
      try {
        const list = await caseParticipantsApi.list(caseId);
        if (cancelled) return;
        setItems(list);
      } catch (e) {
        if (cancelled) return;
        if (e instanceof ApiRequestError) {
          setError(e.details?.message ?? e.message);
        } else {
          setError("تعذر تحميل المشاركين");
        }
        setItems([]);
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [caseId, loadId]);

  async function onAdd() {
    if (!personId) {
      notifyError("اختر شخصاً من القائمة");
      return;
    }
    setSaving(true);
    try {
      await caseParticipantsApi.add(caseId, {
        personId,
        role,
        notes: notes.trim() || null,
      });
      setNotes("");
      setPersonId("");
      notifySuccess("تمت إضافة المشارك");
      refresh();
      onChange();
    } catch (e) {
      notifyApiError(e, "تعذر إضافة المشارك");
    } finally {
      setSaving(false);
    }
  }

  async function onRemove(p: ParticipantResponseDto) {
    if (!canRemove) return;
    if (!window.confirm("إزالة هذا المشارك من الملف؟")) return;
    try {
      await caseParticipantsApi.remove(caseId, p.id);
      notifySuccess("تمت إزالة المشارك");
      refresh();
      onChange();
    } catch (e) {
      notifyApiError(e, "تعذر الإزالة");
    }
  }

  if (error) {
    return (
      <ApiErrorAlert
        message={error}
        onRetry={() => {
          setError(null);
          refresh();
        }}
      />
    );
  }

  return (
    <SectionCard
      title="مشاركو القضية"
      description="الأشخاص المرتبطون بالملف (مستقل عن الموكّل والمحامي الافتراضيين في رأس الملف). إزالة المشارك: مدير / مستشار فقط."
      action={
        <div className="flex size-10 items-center justify-center rounded-xl bg-[var(--color-gold-50)] text-[var(--color-gold-600)]">
          <Users className="size-5" />
        </div>
      }
    >
      {loading ? (
        <PageLoading
          label="جارٍ تحميل المشاركين…"
          minHeightClassName="min-h-[4rem]"
        />
      ) : null}

      {!loading && items.length > 0 ? (
        <div className="mb-6 overflow-x-auto">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>الاسم</TableHead>
                <TableHead>الدور</TableHead>
                <TableHead>ملاحظات</TableHead>
                {canEdit && canRemove ? (
                  <TableHead className="w-24">إجراء</TableHead>
                ) : null}
              </TableRow>
            </TableHeader>
            <TableBody>
              {items.map((p) => (
                <TableRow key={p.id}>
                  <TableCell className="font-medium">
                    {p.person.fullName ?? p.person.id}
                  </TableCell>
                  <TableCell className="text-sm">
                    {ROLE_AR[p.role] ?? p.role}
                  </TableCell>
                  <TableCell className="text-sm text-[var(--color-muted-foreground)]">
                    {p.notes ?? "—"}
                  </TableCell>
                  {canEdit && canRemove ? (
                    <TableCell>
                      <Button
                        type="button"
                        variant="ghost"
                        size="sm"
                        className="text-[var(--color-danger)]"
                        onClick={() => void onRemove(p)}
                      >
                        إزالة
                      </Button>
                    </TableCell>
                  ) : null}
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </div>
      ) : null}

      {showAddForm ? (
        <div className="space-y-3 rounded-xl border border-[var(--color-border)] bg-[var(--color-surface-muted)]/30 p-4">
          <p className="text-sm font-medium">إضافة مشارك</p>
          <SearchInput
            value={search}
            onChange={setSearch}
            placeholder="ابحث عن اسم في المكتب…"
            className="max-w-md"
          />
          <div className="grid gap-3 sm:grid-cols-2">
            <div className="space-y-1">
              <Label>الشخص *</Label>
              <select
                className={selectClassName}
                value={personId}
                onChange={(e) => setPersonId(e.target.value)}
                disabled={saving}
              >
                <option value="">— اختر —</option>
                {candidates.map((c) => (
                  <option key={c.id} value={c.id}>
                    {personLabel(c)}
                  </option>
                ))}
              </select>
            </div>
            <div className="space-y-1">
              <Label>الدور *</Label>
              <select
                className={selectClassName}
                value={role}
                onChange={(e) =>
                  setRole(e.target.value as CaseParticipantRoleDto)
                }
                disabled={saving}
              >
                {ADD_ROLE_OPTIONS.map((o) => (
                  <option key={o.value} value={o.value}>
                    {o.label}
                  </option>
                ))}
              </select>
            </div>
          </div>
          <div className="space-y-1">
            <Label>ملاحظات (اختياري)</Label>
            <Textarea
              value={notes}
              onChange={(e) => setNotes(e.target.value)}
              rows={2}
              className="resize-y"
              disabled={saving}
            />
          </div>
          <Button
            type="button"
            variant="gold"
            size="sm"
            disabled={saving || !personId}
            onClick={() => void onAdd()}
          >
            {saving ? "…" : "إضافة"}
          </Button>
        </div>
      ) : canEdit ? (
        <p className="text-sm text-[var(--color-muted-foreground)]">
          لا تملك صلاحية تعديل المشاركين.
        </p>
      ) : null}
    </SectionCard>
  );
}
