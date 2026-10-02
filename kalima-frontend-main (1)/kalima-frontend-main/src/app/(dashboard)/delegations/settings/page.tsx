"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import Link from "next/link";
import { ArrowRight, Plus, Settings2, Star, Trash2, UserCog } from "lucide-react";

import { useAuth } from "@/components/auth/auth-provider";
import { DisputeModuleHero } from "@/components/disputes/DisputeModuleHero";
import { ApiErrorAlert } from "@/components/shared/ApiErrorAlert";
import { EmptyState } from "@/components/shared/EmptyState";
import { PageLoading } from "@/components/shared/PageLoading";
import { SectionCard } from "@/components/shared/SectionCard";
import { Button } from "@/components/ui/button";
import { Label } from "@/components/ui/label";
import {
  delegationAttorneysApi,
  type DelegationOfficeAttorneyDto,
} from "@/lib/api/delegation-attorneys";
import { personsApi } from "@/lib/api/persons";
import { ApiRequestError } from "@/lib/api/client";
import { notifyApiError, notifySuccess } from "@/lib/feedback";
import { cn } from "@/lib/utils/cn";
import type { ClientPerson } from "@/types/person";
import type { SystemRole } from "@/types/staff";

const STAFF_ROLES = ["ADMIN", "SENIOR_LAWYER", "LAWYER", "SECRETARY"] as const;

const selectClassName = cn(
  "h-10 w-full min-w-0 cursor-pointer appearance-none rounded-lg border border-[var(--color-border)] bg-[var(--color-surface)] px-3 py-2 text-sm text-[var(--color-foreground)] shadow-sm",
  "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--color-gold-400)]",
);

function canManage(role: SystemRole | null | undefined) {
  return role === "ADMIN" || role === "SENIOR_LAWYER";
}

function staffLabel(p: { fullName?: string | null; id: string }) {
  return p.fullName?.trim() || p.id;
}

export default function DelegationSettingsPage() {
  const { user } = useAuth();
  const role = user?.systemRole as SystemRole | undefined;
  const manage = canManage(role);

  const [attorneys, setAttorneys] = useState<DelegationOfficeAttorneyDto[]>([]);
  const [staff, setStaff] = useState<ClientPerson[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [selectedPersonId, setSelectedPersonId] = useState("");
  const [adding, setAdding] = useState(false);
  const [busyId, setBusyId] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const [attorneyRows, ...staffPages] = await Promise.all([
        delegationAttorneysApi.list(false),
        ...STAFF_ROLES.map((r) => personsApi.list({ role: r, size: 200 })),
      ]);
      const mergedStaff = staffPages.flatMap((p) => p.content);
      const seen = new Set<string>();
      setAttorneys(attorneyRows);
      setStaff(mergedStaff.filter((p) => !seen.has(p.id) && seen.add(p.id)));
    } catch (e) {
      setError(e instanceof ApiRequestError ? e.message : "تعذّر تحميل إعدادات التوكيلات");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const registeredIds = useMemo(
    () => new Set(attorneys.map((a) => a.person.id)),
    [attorneys],
  );

  const addableStaff = useMemo(
    () => staff.filter((s) => !registeredIds.has(s.id)),
    [staff, registeredIds],
  );

  async function handleAdd() {
    if (!selectedPersonId) return;
    setAdding(true);
    try {
      const created = await delegationAttorneysApi.create({ personId: selectedPersonId });
      setAttorneys((prev) => [...prev, created]);
      setSelectedPersonId("");
      notifySuccess("تمت إضافة الوكيل");
    } catch (e) {
      notifyApiError(e, "تعذّر إضافة الوكيل");
    } finally {
      setAdding(false);
    }
  }

  async function handleToggleActive(row: DelegationOfficeAttorneyDto) {
    setBusyId(row.id);
    try {
      const updated = await delegationAttorneysApi.update(row.id, { active: !row.active });
      setAttorneys((prev) => prev.map((a) => (a.id === row.id ? updated : a)));
    } catch (e) {
      notifyApiError(e, "تعذّر تحديث حالة الوكيل");
    } finally {
      setBusyId(null);
    }
  }

  async function handleSetDefault(row: DelegationOfficeAttorneyDto) {
    if (row.isDefault) return;
    setBusyId(row.id);
    try {
      const updated = await delegationAttorneysApi.update(row.id, { isDefault: true });
      setAttorneys((prev) =>
        prev.map((a) => {
          if (a.id === row.id) return updated;
          if (a.isDefault) return { ...a, isDefault: false };
          return a;
        }),
      );
      notifySuccess("تم تعيين الوكيل الافتراضي");
    } catch (e) {
      notifyApiError(e, "تعذّر تعيين الوكيل الافتراضي");
    } finally {
      setBusyId(null);
    }
  }

  async function handleRemove(row: DelegationOfficeAttorneyDto) {
    const name = staffLabel(row.person);
    if (!window.confirm(`إزالة «${name}» من وكلاء المكتب؟`)) return;
    setBusyId(row.id);
    try {
      await delegationAttorneysApi.remove(row.id);
      setAttorneys((prev) => prev.filter((a) => a.id !== row.id));
      notifySuccess("تمت إزالة الوكيل");
    } catch (e) {
      notifyApiError(e, "تعذّر إزالة الوكيل");
    } finally {
      setBusyId(null);
    }
  }

  if (loading) return <PageLoading label="جاري تحميل الإعدادات..." />;

  return (
    <div className="space-y-6">
      <DisputeModuleHero
        eyebrow="إعدادات التوكيلات"
        title="وكلاء المكتب"
        description="تحديد الموظفين الذين يظهرون كوكلاء في نماذج التوكيل — مع وكيل افتراضي للتعبئة التلقائية عند وجود وكيل واحد نشط."
        icon={Settings2}
        actions={
          <Button
            asChild
            variant="outline"
            className="gap-2 border-white/25 bg-white/5 text-white hover:bg-white/10"
          >
            <Link href="/delegations">
              <ArrowRight className="size-4" aria-hidden />
              العودة للتوكيلات
            </Link>
          </Button>
        }
      />

      {error ? <ApiErrorAlert message={error} onRetry={() => void load()} /> : null}

      {manage ? (
        <SectionCard title="إضافة وكيل" contentClassName="space-y-4">
          <p className="text-sm text-[var(--color-muted-foreground)]">
            يمكن إضافة موظفي المكتب (محامون، كبار المحامين، سكرتارية، مدير) كوكلاء للتوكيلات.
          </p>
          <div className="flex flex-col gap-3 sm:flex-row sm:items-end">
            <div className="min-w-0 flex-1 space-y-2">
              <Label htmlFor="newAttorney">الموظف</Label>
              <select
                id="newAttorney"
                className={selectClassName}
                value={selectedPersonId}
                onChange={(e) => setSelectedPersonId(e.target.value)}
              >
                <option value="">— اختر موظفًا —</option>
                {addableStaff.map((s) => (
                  <option key={s.id} value={s.id}>
                    {staffLabel(s)}
                  </option>
                ))}
              </select>
            </div>
            <Button
              type="button"
              className="gap-2 shrink-0 bg-[var(--color-gold-400)] text-[var(--color-navy-900)] hover:bg-[var(--color-gold-300)]"
              disabled={!selectedPersonId || adding}
              onClick={() => void handleAdd()}
            >
              <Plus className="size-4" aria-hidden />
              {adding ? "جاري الإضافة..." : "إضافة"}
            </Button>
          </div>
          {addableStaff.length === 0 ? (
            <p className="text-xs text-[var(--color-muted-foreground)]">
              جميع الموظفين المؤهلين مُسجَّلون بالفعل كوكلاء.
            </p>
          ) : null}
        </SectionCard>
      ) : (
        <p className="rounded-xl border border-[var(--color-border)] bg-[var(--color-surface)] px-4 py-3 text-sm text-[var(--color-muted-foreground)]">
          عرض فقط — تعديل وكلاء المكتب متاح لمدير النظام وكبار المحامين.
        </p>
      )}

      <SectionCard title={`الوكلاء المسجَّلون (${attorneys.length})`} contentClassName="p-0">
        {attorneys.length === 0 ? (
          <EmptyState
            icon={UserCog}
            title="لا يوجد وكلاء بالمكتب"
            description="أضف موظفين ليظهروا في قائمة الوكيل عند إنشاء توكيل جديد."
          />
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full min-w-[36rem] text-sm">
              <thead>
                <tr className="border-b border-[var(--color-border)] bg-[var(--color-surface-muted)]/60 text-[var(--color-muted-foreground)]">
                  <th className="px-4 py-3 text-start font-medium">الاسم</th>
                  <th className="px-4 py-3 text-center font-medium">نشط</th>
                  <th className="px-4 py-3 text-center font-medium">افتراضي</th>
                  {manage ? <th className="px-4 py-3 text-end font-medium">إجراءات</th> : null}
                </tr>
              </thead>
              <tbody>
                {attorneys.map((row) => {
                  const busy = busyId === row.id;
                  return (
                    <tr
                      key={row.id}
                      className="border-b border-[var(--color-border)] last:border-0 hover:bg-[var(--color-surface-muted)]/40"
                    >
                      <td className="px-4 py-3 font-medium text-[var(--color-foreground)]">
                        {staffLabel(row.person)}
                      </td>
                      <td className="px-4 py-3 text-center">
                        {manage ? (
                          <input
                            type="checkbox"
                            className="size-4 cursor-pointer accent-[var(--color-gold-500)]"
                            checked={row.active}
                            disabled={busy}
                            onChange={() => void handleToggleActive(row)}
                            aria-label={`نشط — ${staffLabel(row.person)}`}
                          />
                        ) : (
                          <span className={row.active ? "text-emerald-600" : "text-[var(--color-muted-foreground)]"}>
                            {row.active ? "نعم" : "لا"}
                          </span>
                        )}
                      </td>
                      <td className="px-4 py-3 text-center">
                        {manage ? (
                          <button
                            type="button"
                            className={cn(
                              "inline-flex items-center justify-center rounded-md p-1.5 transition-colors",
                              row.isDefault
                                ? "text-[var(--color-gold-500)]"
                                : "text-[var(--color-muted-foreground)] hover:text-[var(--color-gold-400)]",
                            )}
                            disabled={busy || row.isDefault}
                            onClick={() => void handleSetDefault(row)}
                            aria-label={`افتراضي — ${staffLabel(row.person)}`}
                            title={row.isDefault ? "الوكيل الافتراضي" : "تعيين كافتراضي"}
                          >
                            <Star className={cn("size-4", row.isDefault && "fill-current")} />
                          </button>
                        ) : row.isDefault ? (
                          <Star className="mx-auto size-4 fill-[var(--color-gold-500)] text-[var(--color-gold-500)]" />
                        ) : (
                          <span className="text-[var(--color-muted-foreground)]">—</span>
                        )}
                      </td>
                      {manage ? (
                        <td className="px-4 py-3 text-end">
                          <Button
                            type="button"
                            variant="ghost"
                            size="sm"
                            className="gap-1 text-[var(--color-danger)] hover:bg-[var(--color-danger)]/10 hover:text-[var(--color-danger)]"
                            disabled={busy}
                            onClick={() => void handleRemove(row)}
                          >
                            <Trash2 className="size-4" aria-hidden />
                            إزالة
                          </Button>
                        </td>
                      ) : null}
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </SectionCard>
    </div>
  );
}
