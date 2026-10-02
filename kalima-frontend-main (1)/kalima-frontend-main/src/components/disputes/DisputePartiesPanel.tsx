"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { ChevronDown, ChevronUp, UserPlus } from "lucide-react";

import { ApiErrorAlert } from "@/components/shared/ApiErrorAlert";
import { EmptyState } from "@/components/shared/EmptyState";
import { PageLoading } from "@/components/shared/PageLoading";
import { SectionCard } from "@/components/shared/SectionCard";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
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
import { disputePartiesApi } from "@/lib/api/dispute-parties";
import { personsApi } from "@/lib/api/persons";
import { ApiRequestError } from "@/lib/api/client";
import { getStoredUser } from "@/lib/auth/session";
import { notifyApiError, notifyError, notifySuccess } from "@/lib/feedback";
import { DISPUTE_PARTY_ROLE_AR, DISPUTE_PARTY_ROLE_OPTIONS } from "@/lib/disputes/labels";
import type { ClientPerson } from "@/types/person";
import type { DisputePartyDto, DisputePartyRoleDto } from "@/types/dispute";

const selectClassName = cn(
  "flex h-9 w-full min-w-0 cursor-pointer appearance-none rounded-lg border border-[var(--color-border)] bg-[var(--color-surface)] px-2.5 py-1.5 text-sm text-[var(--color-foreground)] shadow-sm",
  "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--color-gold-400)]",
  "disabled:cursor-not-allowed disabled:opacity-50",
);

function personLabel(p: ClientPerson) {
  if (p.personType === "COMPANY") {
    return p.companyName?.trim() || p.fullName?.trim() || p.id;
  }
  return p.fullName?.trim() || p.id;
}

type Props = {
  disputeId: string;
  canEdit: boolean;
  primaryClientId: string | null;
  onChange?: () => void;
};

export function DisputePartiesPanel({
  disputeId,
  canEdit,
  primaryClientId,
  onChange,
}: Props) {
  const [items, setItems] = useState<DisputePartyDto[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [loadId, setLoadId] = useState(0);

  const [addOpen, setAddOpen] = useState(false);

  const [role, setRole] = useState<DisputePartyRoleDto>("WITNESS");
  const [usePerson, setUsePerson] = useState(true);
  const [personId, setPersonId] = useState("");
  const [displayName, setDisplayName] = useState("");
  const [phone, setPhone] = useState("");
  const [notes, setNotes] = useState("");
  const [sortOrder, setSortOrder] = useState("0");
  const [saving, setSaving] = useState(false);

  const [persons, setPersons] = useState<ClientPerson[]>([]);
  const [clientsOnly, setClientsOnly] = useState<ClientPerson[]>([]);

  const [u, setU] = useState<ReturnType<typeof getStoredUser>>();
  useEffect(() => {
    setU(getStoredUser());
  }, []);

  const canMutate = Boolean(
    u &&
      (u.systemRole === "ADMIN" ||
        u.systemRole === "SENIOR_LAWYER" ||
        u.systemRole === "LAWYER" ||
        u.systemRole === "SECRETARY"),
  );

  const refresh = useCallback(() => {
    setLoadId((n) => n + 1);
  }, []);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      setLoading(true);
      setError(null);
      try {
        const list = await disputePartiesApi.list(disputeId);
        if (cancelled) return;
        setItems(list);
      } catch (e) {
        if (cancelled) return;
        if (e instanceof ApiRequestError) {
          setError(e.details?.message ?? e.message);
        } else {
          setError("تعذر تحميل الأطراف الإضافية");
        }
        setItems([]);
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [disputeId, loadId]);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const [allRes, cRes] = await Promise.all([
          personsApi.list({ page: 0, size: 300 }),
          personsApi.list({ role: "CLIENT", page: 0, size: 400 }),
        ]);
        if (cancelled) return;
        setPersons(allRes.content.filter((p) => p.active && !p.archivedAt));
        setClientsOnly(cRes.content.filter((p) => p.active && !p.archivedAt));
      } catch {
        if (!cancelled) {
          setPersons([]);
          setClientsOnly([]);
        }
      }
    })();
    return () => {
      cancelled = true;
    };
  }, []);

  useEffect(() => {
    if (role === "CO_CLIENT") {
      setUsePerson(true);
    }
  }, [role]);

  const pickerPersons = useMemo(() => {
    if (role === "CO_CLIENT") {
      return clientsOnly.filter((p) => p.id !== primaryClientId);
    }
    return persons;
  }, [role, clientsOnly, persons, primaryClientId]);

  async function onAdd() {
    const so = parseInt(sortOrder, 10);
    const sort = Number.isFinite(so) ? so : 0;
    if (role === "CO_CLIENT" || usePerson) {
      if (!personId.trim()) {
        notifyError("اختر شخصاً من السجل");
        return;
      }
      setSaving(true);
      try {
        await disputePartiesApi.create(disputeId, {
          role,
          personId: personId.trim(),
          displayName: displayName.trim() || null,
          phone: phone.trim() || null,
          notes: notes.trim() || null,
          sortOrder: sort,
        });
        notifySuccess("تمت إضافة الطرف");
        setPersonId("");
        setDisplayName("");
        setPhone("");
        setNotes("");
        setSortOrder("0");
        setAddOpen(false);
        refresh();
        onChange?.();
      } catch (e) {
        notifyApiError(e, "تعذر إضافة الطرف");
      } finally {
        setSaving(false);
      }
      return;
    }

    if (!displayName.trim()) {
      notifyError("أدخل الاسم المعروض للطرف");
      return;
    }
    setSaving(true);
    try {
      await disputePartiesApi.create(disputeId, {
        role,
        displayName: displayName.trim(),
        phone: phone.trim() || null,
        notes: notes.trim() || null,
        sortOrder: sort,
      });
      notifySuccess("تمت إضافة الطرف");
      setDisplayName("");
      setPhone("");
      setNotes("");
      setSortOrder("0");
      setAddOpen(false);
      refresh();
      onChange?.();
    } catch (e) {
      notifyApiError(e, "تعذر إضافة الطرف");
    } finally {
      setSaving(false);
    }
  }

  async function onRemove(row: DisputePartyDto) {
    if (!window.confirm("إزالة هذا الطرف من النزاع؟")) return;
    try {
      await disputePartiesApi.remove(disputeId, row.id);
      notifySuccess("تمت الإزالة");
      refresh();
      onChange?.();
    } catch (e) {
      notifyApiError(e, "تعذر الحذف");
    }
  }

  function rowLabel(row: DisputePartyDto): string {
    if (row.person?.fullName?.trim()) return row.person.fullName.trim();
    if (row.displayName?.trim()) return row.displayName.trim();
    return "—";
  }

  if (error) {
    return (
      <ApiErrorAlert message={error} onRetry={() => refresh()} />
    );
  }

  const showForm = canEdit && canMutate;

  return (
    <SectionCard
      title="أطراف إضافية"
      description="بالإضافة للموكل الأساسي وللخصم الظاهر في رأس النزاع — موكلين إضافيين، أطراف مقابلة مسجلة، أو أسماء خارجية."
      action={
        <div className="flex size-10 items-center justify-center rounded-xl bg-[var(--color-navy-50)] text-[var(--color-navy-700)]">
          <UserPlus className="size-5" />
        </div>
      }
    >
      {loading ? (
        <PageLoading label="جارٍ تحميل الأطراف…" />
      ) : items.length === 0 && !showForm ? (
        <EmptyState
          icon={UserPlus}
          title="لا أطراف إضافية"
          description="أضف أطرافاً عند وجود أكثر من موكل أو طرف مقابل مسجّل."
        />
      ) : (
        <div className="space-y-3">
          {items.length > 0 ? (
            <div className="overflow-x-auto rounded-xl border border-[var(--color-border)]">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead className="text-right">الشخص</TableHead>
                    <TableHead className="text-right">صفته</TableHead>
                    <TableHead className="text-right">المصدر</TableHead>
                    <TableHead className="text-right">جوال</TableHead>
                    <TableHead className="text-right">ملاحظات</TableHead>
                    {showForm ? (
                      <TableHead className="w-[100px] text-center">إجراء</TableHead>
                    ) : null}
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {items.map((row) => (
                    <TableRow key={row.id}>
                      <TableCell className="text-sm">
                        <div className="font-medium">{rowLabel(row)}</div>
                      </TableCell>
                      <TableCell className="text-sm">{DISPUTE_PARTY_ROLE_AR[row.role]}</TableCell>
                      <TableCell className="text-sm text-[var(--color-muted-foreground)]">
                        {row.person?.id ? "من السجل" : "اسم خارج السجل"}
                      </TableCell>
                      <TableCell className="text-sm tabular-nums">{row.phone ?? "—"}</TableCell>
                      <TableCell className="max-w-[14rem] truncate text-sm">
                        {row.notes ?? "—"}
                      </TableCell>
                      {showForm ? (
                        <TableCell className="text-center">
                          <Button
                            type="button"
                            variant="outline"
                            size="sm"
                            onClick={() => void onRemove(row)}
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
          ) : (
            <p className="text-sm text-[var(--color-muted-foreground)]">لا توجد أطراف إضافية بعد.</p>
          )}

          {showForm ? (
            <div className="flex flex-wrap items-center justify-between gap-2">
              <Button
                type="button"
                variant={addOpen ? "outline" : "gold"}
                size="sm"
                className="gap-1.5"
                disabled={saving}
                onClick={() => setAddOpen((v) => !v)}
              >
                {addOpen ? (
                  <>
                    <ChevronUp className="size-4" />
                    إخفاء النموذج
                  </>
                ) : (
                  <>
                    <ChevronDown className="size-4" />
                    إضافة طرف
                  </>
                )}
              </Button>
            </div>
          ) : null}

          {showForm && addOpen ? (
            <div className="rounded-xl border border-[var(--color-border)] bg-[var(--color-surface-muted)]/20 p-3 sm:p-4">
              <div className="grid gap-2 sm:gap-3 sm:grid-cols-2 lg:grid-cols-12 lg:items-end">
                <div className="space-y-1 sm:col-span-2 lg:col-span-4">
                  <Label htmlFor="dp-role" className="text-xs text-[var(--color-muted-foreground)]">
                    صفته
                  </Label>
                  <select
                    id="dp-role"
                    className={selectClassName}
                    value={role}
                    title={
                      role === "CO_CLIENT"
                        ? "عميل مسجّل فقط، دون تكرار الموكل الأساسي"
                        : undefined
                    }
                    onChange={(e) =>
                      setRole(e.target.value as DisputePartyRoleDto)}
                    disabled={saving}
                  >
                    {DISPUTE_PARTY_ROLE_OPTIONS.map((o) => (
                      <option key={o.value} value={o.value}>
                        {o.label}
                      </option>
                    ))}
                  </select>
                </div>

                {role !== "CO_CLIENT" ? (
                  <div className="space-y-1 sm:col-span-2 lg:col-span-4">
                    <Label htmlFor="dp-src" className="text-xs text-[var(--color-muted-foreground)]">
                      المصدر
                    </Label>
                    <select
                      id="dp-src"
                      className={selectClassName}
                      value={usePerson ? "person" : "free"}
                      onChange={(e) => setUsePerson(e.target.value === "person")}
                      disabled={saving}
                    >
                      <option value="person">من السجل</option>
                      <option value="free">اسم خارج السجل</option>
                    </select>
                  </div>
                ) : (
                  <p className="text-[11px] leading-snug text-[var(--color-muted-foreground)] sm:col-span-2 lg:col-span-8 lg:self-end lg:pb-1">
                    موكل إضافي: يختار عميلًا مسجلًا فقط (لا يتكرر الموكل الأساسي).
                  </p>
                )}

                {(role === "CO_CLIENT" || usePerson) && (
                  <div className="space-y-1 sm:col-span-2 lg:col-span-4">
                    <Label htmlFor="dp-person" className="text-xs text-[var(--color-muted-foreground)]">
                      الشخص
                    </Label>
                    <select
                      id="dp-person"
                      className={selectClassName}
                      value={personId}
                      onChange={(e) => setPersonId(e.target.value)}
                      disabled={saving || pickerPersons.length === 0}
                    >
                      <option value="">اختر…</option>
                      {pickerPersons.map((p) => (
                        <option key={p.id} value={p.id}>
                          {personLabel(p)}
                        </option>
                      ))}
                    </select>
                  </div>
                )}

                {role !== "CO_CLIENT" && usePerson && (
                  <div className="space-y-1 sm:col-span-2 lg:col-span-8">
                    <Label className="text-xs text-[var(--color-muted-foreground)]">
                      اسم عرض اختياري
                    </Label>
                    <Input
                      className="h-9"
                      placeholder="يظهر بدل الاسم المعتاد إن رغبت"
                      value={displayName}
                      onChange={(e) => setDisplayName(e.target.value)}
                      disabled={saving}
                    />
                  </div>
                )}

                {role !== "CO_CLIENT" && !usePerson && (
                  <div className="space-y-1 sm:col-span-2 lg:col-span-12">
                    <Label htmlFor="dp-dn" className="text-xs text-[var(--color-muted-foreground)]">
                      الشخص
                    </Label>
                    <Input
                      id="dp-dn"
                      className="h-9"
                      value={displayName}
                      onChange={(e) => setDisplayName(e.target.value)}
                      placeholder="طرف غير مسجل في النظام"
                      disabled={saving}
                    />
                  </div>
                )}

                <div className="space-y-1 sm:col-span-1 lg:col-span-3">
                  <Label htmlFor="dp-phone" className="text-xs text-[var(--color-muted-foreground)]">
                    جوال
                  </Label>
                  <Input
                    id="dp-phone"
                    className="h-9"
                    dir="ltr"
                    value={phone}
                    onChange={(e) => setPhone(e.target.value)}
                    disabled={saving}
                  />
                </div>

                <div className="flex sm:col-span-1 lg:col-span-2 lg:justify-start">
                  <Button
                    type="button"
                    variant="gold"
                    size="sm"
                    className="w-full sm:w-auto lg:mt-5"
                    disabled={saving}
                    onClick={() => void onAdd()}
                  >
                    {saving ? "جارٍ الحفظ…" : "حفظ"}
                  </Button>
                </div>
              </div>

              <details className="mt-2 border-t border-[var(--color-border)]/60 pt-2 text-sm">
                <summary className="cursor-pointer select-none text-xs font-medium text-[var(--color-muted-foreground)]">
                  ملاحظات وترتيب العرض (اختياري)
                </summary>
                <div className="mt-2 grid gap-2 sm:grid-cols-12">
                  <div className="space-y-1 sm:col-span-8">
                    <Label htmlFor="dp-notes" className="text-xs text-[var(--color-muted-foreground)]">
                      ملاحظات
                    </Label>
                    <Textarea
                      id="dp-notes"
                      rows={2}
                      className="min-h-[3.25rem] resize-y text-sm"
                      value={notes}
                      onChange={(e) => setNotes(e.target.value)}
                      disabled={saving}
                    />
                  </div>
                  <div className="space-y-1 sm:col-span-4">
                    <Label htmlFor="dp-sort" className="text-xs text-[var(--color-muted-foreground)]">
                      ترتيب
                    </Label>
                    <Input
                      id="dp-sort"
                      type="number"
                      dir="ltr"
                      className="h-9 text-end"
                      value={sortOrder}
                      onChange={(e) => setSortOrder(e.target.value)}
                      disabled={saving}
                    />
                  </div>
                </div>
              </details>
            </div>
          ) : null}

          {!showForm && !canEdit ? (
            <p className="text-sm text-[var(--color-muted-foreground)]">
              بعد التحويل إلى قضية لا يمكن تعديل أطراف النزاع من هنا.
            </p>
          ) : null}
        </div>
      )}
    </SectionCard>
  );
}
