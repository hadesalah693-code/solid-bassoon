"use client";

import { useCallback, useEffect, useState } from "react";

import { Pencil, Plus, RotateCcw, Trash2, Users } from "lucide-react";

import { HrField, HrSelect } from "@/components/hr/HrField";
import { HrRoleGate, HrWriteOnly } from "@/components/hr/HrRoleGate";
import { HrEmployeeStatusBadge } from "@/components/hr/HrStatusBadge";
import { ApiErrorAlert } from "@/components/shared/ApiErrorAlert";
import { Dialog } from "@/components/shared/Dialog";
import { EmptyState } from "@/components/shared/EmptyState";
import { PageHeader } from "@/components/shared/PageHeader";
import { PageLoading } from "@/components/shared/PageLoading";
import { Pagination } from "@/components/shared/Pagination";
import { SearchInput } from "@/components/shared/SearchInput";
import { SectionCard } from "@/components/shared/SectionCard";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { useDebounce } from "@/hooks/use-debounce";
import { ApiRequestError } from "@/lib/api/client";
import { hrDepartmentsApi, hrEmployeesApi, hrPositionsApi } from "@/lib/api/hr";
import {
  HR_CONTRACT_TYPE_OPTIONS,
  HR_EMPLOYEE_STATUS_OPTIONS,
  HR_GENDER_OPTIONS,
} from "@/lib/hr/labels";
import { formatDate } from "@/lib/utils/format-date";
import type {
  HrDepartmentDto,
  HrContractTypeDto,
  HrEmployeeDto,
  HrEmployeeStatusDto,
  HrGenderDto,
  HrPositionDto,
} from "@/types/hr";

const PAGE_SIZE = 20;

type FormState = {
  employeeNumber: string;
  fullName: string;
  departmentId: string;
  positionId: string;
  deviceUserId: string;
  deviceUserPin: string;
  cardNumber: string;
  nationalId: string;
  gender: string;
  phone: string;
  email: string;
  address: string;
  hireDate: string;
  contractType: string;
  contractEndDate: string;
  basicSalary: string;
  status: string;
  notes: string;
};

const EMPTY_FORM: FormState = {
  employeeNumber: "",
  fullName: "",
  departmentId: "",
  positionId: "",
  deviceUserId: "",
  deviceUserPin: "",
  cardNumber: "",
  nationalId: "",
  gender: "",
  phone: "",
  email: "",
  address: "",
  hireDate: "",
  contractType: "",
  contractEndDate: "",
  basicSalary: "",
  status: "ACTIVE",
  notes: "",
};

export default function HrEmployeesPage() {
  return (
    <HrRoleGate>
      <HrEmployeesPageInner />
    </HrRoleGate>
  );
}

function HrEmployeesPageInner() {
  const [rows, setRows] = useState<HrEmployeeDto[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [search, setSearch] = useState("");
  const [departmentFilter, setDepartmentFilter] = useState("");
  const [statusFilter, setStatusFilter] = useState("");
  const debouncedSearch = useDebounce(search, 350);

  const [departments, setDepartments] = useState<HrDepartmentDto[]>([]);
  const [positions, setPositions] = useState<HrPositionDto[]>([]);
  const [takenPunchIds, setTakenPunchIds] = useState<string[]>([]);

  const [dialogOpen, setDialogOpen] = useState(false);
  const [editing, setEditing] = useState<HrEmployeeDto | null>(null);
  const [form, setForm] = useState<FormState>(EMPTY_FORM);
  const [submitting, setSubmitting] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);
  const [fetchId, setFetchId] = useState(0);

  const hasFilters =
    !!search.trim() || !!departmentFilter || !!statusFilter;

  const refresh = useCallback(() => setFetchId((n) => n + 1), []);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const [depts, poss, punchIds] = await Promise.all([
          hrDepartmentsApi.options(),
          hrPositionsApi.options(),
          hrEmployeesApi.deviceUserIds().catch(() => [] as string[]),
        ]);
        if (!cancelled) {
          setDepartments(depts);
          setPositions(poss);
          setTakenPunchIds(punchIds);
        }
      } catch {
        // Pickers degrade to empty; the list itself reports its own failure.
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [fetchId]);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      setLoading(true);
      setError(null);
      try {
        const res = await hrEmployeesApi.list({
          page,
          size: PAGE_SIZE,
          search: debouncedSearch,
          departmentId: departmentFilter || undefined,
          status: (statusFilter || undefined) as HrEmployeeStatusDto | undefined,
        });
        if (!cancelled) {
          setRows(res.content);
          setTotal(res.totalElements);
        }
      } catch (e) {
        if (!cancelled) {
          setError(
            e instanceof ApiRequestError ? e.message : "تعذّر تحميل الموظفين",
          );
          setRows([]);
          setTotal(0);
        }
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [page, debouncedSearch, departmentFilter, statusFilter, fetchId]);

  function openCreate() {
    setEditing(null);
    setForm(EMPTY_FORM);
    setFormError(null);
    setDialogOpen(true);
  }

  function openEdit(row: HrEmployeeDto) {
    setEditing(row);
    setForm({
      employeeNumber: row.employeeNumber,
      fullName: row.fullName,
      departmentId: row.departmentId ?? "",
      positionId: row.positionId ?? "",
      deviceUserId: row.deviceUserId ?? "",
      deviceUserPin: row.deviceUserPin ?? "",
      cardNumber: row.cardNumber ?? "",
      nationalId: row.nationalId ?? "",
      gender: row.gender ?? "",
      phone: row.phone ?? "",
      email: row.email ?? "",
      address: row.address ?? "",
      hireDate: row.hireDate ?? "",
      contractType: row.contractType ?? "",
      contractEndDate: row.contractEndDate ?? "",
      basicSalary: row.basicSalary != null ? String(row.basicSalary) : "",
      status: row.status,
      notes: row.notes ?? "",
    });
    setFormError(null);
    setDialogOpen(true);
  }

  async function submit() {
    if (!form.employeeNumber.trim() || !form.fullName.trim()) {
      setFormError("الرقم الوظيفي واسم الموظف مطلوبان");
      return;
    }
    setSubmitting(true);
    setFormError(null);
    try {
      const body = {
        employeeNumber: form.employeeNumber.trim(),
        fullName: form.fullName.trim(),
        departmentId: form.departmentId || null,
        positionId: form.positionId || null,
        deviceUserId: form.deviceUserId.trim() || null,
        deviceUserPin: form.deviceUserPin.trim() || null,
        cardNumber: form.cardNumber.trim() || null,
        nationalId: form.nationalId.trim() || null,
        gender: (form.gender || null) as HrGenderDto | null,
        phone: form.phone.trim() || null,
        email: form.email.trim() || null,
        address: form.address.trim() || null,
        hireDate: form.hireDate || null,
        contractType: (form.contractType || null) as HrContractTypeDto | null,
        contractEndDate: form.contractEndDate || null,
        basicSalary: form.basicSalary.trim() ? Number(form.basicSalary) : null,
        status: form.status as HrEmployeeStatusDto,
        notes: form.notes.trim() || null,
      };
      if (editing) {
        await hrEmployeesApi.update(editing.id, body);
      } else {
        await hrEmployeesApi.create(body);
      }
      setDialogOpen(false);
      refresh();
    } catch (e) {
      setFormError(
        e instanceof ApiRequestError ? e.message : "تعذّر حفظ بيانات الموظف",
      );
    } finally {
      setSubmitting(false);
    }
  }

  async function remove(row: HrEmployeeDto) {
    if (
      !window.confirm(
        `سيتم حذف الموظف «${row.fullName}». سجلات حضوره ستبقى محفوظة بدون ربط. هل تريد المتابعة؟`,
      )
    ) {
      return;
    }
    try {
      await hrEmployeesApi.remove(row.id);
      refresh();
    } catch (e) {
      setError(e instanceof ApiRequestError ? e.message : "تعذّر حذف الموظف");
    }
  }

  const departmentOptions = departments.map((d) => ({
    value: d.id,
    label: d.name,
  }));
  const positionOptions = positions.map((p) => ({
    value: p.id,
    label: p.departmentName ? `${p.title} — ${p.departmentName}` : p.title,
  }));

  const punchIdTaken =
    !!form.deviceUserId.trim() &&
    takenPunchIds.some(
      (id) =>
        id.trim().toLowerCase() === form.deviceUserId.trim().toLowerCase() &&
        id.trim().toLowerCase() !==
          (editing?.deviceUserId ?? "").trim().toLowerCase(),
    );

  return (
    <div>
      <PageHeader
        title="الموظفون"
        description="بيانات الموظفين ومعرّف البصمة المستخدم في ربط سجلات الحضور من الجهاز."
        actions={
          <HrWriteOnly>
            <Button onClick={openCreate}>
              <Plus className="size-4" />
              موظف جديد
            </Button>
          </HrWriteOnly>
        }
      />

      <SectionCard className="mb-4">
        <div className="flex flex-wrap items-center gap-3">
          <SearchInput
            value={search}
            onChange={setSearch}
            placeholder="ابحث بالاسم أو الرقم الوظيفي أو معرّف البصمة..."
            className="min-w-[18rem] flex-1"
          />
          <select
            className="h-10 w-full max-w-[14rem] cursor-pointer appearance-none rounded-xl border border-[var(--color-border)] bg-[var(--color-surface)] px-3.5 py-2 text-sm shadow-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--color-gold-400)] sm:w-auto"
            value={departmentFilter}
            onChange={(e) => setDepartmentFilter(e.target.value)}
            aria-label="تصفية حسب القسم"
          >
            <option value="">كل الأقسام</option>
            {departmentOptions.map((o) => (
              <option key={o.value} value={o.value}>
                {o.label}
              </option>
            ))}
          </select>
          <select
            className="h-10 w-full max-w-[12rem] cursor-pointer appearance-none rounded-xl border border-[var(--color-border)] bg-[var(--color-surface)] px-3.5 py-2 text-sm shadow-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--color-gold-400)] sm:w-auto"
            value={statusFilter}
            onChange={(e) => setStatusFilter(e.target.value)}
            aria-label="تصفية حسب الحالة"
          >
            <option value="">كل الحالات</option>
            {HR_EMPLOYEE_STATUS_OPTIONS.map((o) => (
              <option key={o.value} value={o.value}>
                {o.label}
              </option>
            ))}
          </select>
          {hasFilters ? (
            <Button
              variant="ghost"
              onClick={() => {
                setSearch("");
                setDepartmentFilter("");
                setStatusFilter("");
              }}
            >
              <RotateCcw className="size-3.5" />
              مسح التصفية
            </Button>
          ) : null}
        </div>
      </SectionCard>

      {error ? (
        <ApiErrorAlert message={error} onRetry={refresh} />
      ) : null}

      {loading ? (
        <PageLoading label="جاري تحميل الموظفين..." />
      ) : rows.length === 0 ? (
        <SectionCard>
          <EmptyState
            icon={Users}
            title={hasFilters ? "لا توجد نتائج" : "لا يوجد موظفون"}
            description={
              hasFilters
                ? "جرّب تعديل البحث أو إزالة التصفية."
                : "ابدأ بإضافة أول موظف، ثم سجّل معرّف بصمته لربط سجلات الحضور."
            }
            action={
              hasFilters ? (
                <Button
                  variant="outline"
                  onClick={() => {
                    setSearch("");
                    setDepartmentFilter("");
                    setStatusFilter("");
                  }}
                >
                  مسح التصفية
                </Button>
              ) : (
                <HrWriteOnly>
                  <Button onClick={openCreate}>موظف جديد</Button>
                </HrWriteOnly>
              )
            }
          />
        </SectionCard>
      ) : (
        <div className="space-y-4">
          <SectionCard flush>
            <div className="overflow-x-auto">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead className="text-right">الرقم الوظيفي</TableHead>
                    <TableHead className="text-right">الاسم</TableHead>
                    <TableHead className="text-right">القسم</TableHead>
                    <TableHead className="text-right">المسمى</TableHead>
                    <TableHead className="text-right">معرّف البصمة</TableHead>
                    <TableHead className="text-right">تاريخ التعيين</TableHead>
                    <TableHead className="text-right">الحالة</TableHead>
                    <TableHead className="text-right">إجراءات</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {rows.map((row) => (
                    <TableRow key={row.id}>
                      <TableCell className="ltr-nums font-semibold">
                        {row.employeeNumber}
                      </TableCell>
                      <TableCell>{row.fullName}</TableCell>
                      <TableCell className="text-[var(--color-muted-foreground)]">
                        {row.departmentName ?? "—"}
                      </TableCell>
                      <TableCell className="text-[var(--color-muted-foreground)]">
                        {row.positionTitle ?? "—"}
                      </TableCell>
                      <TableCell className="ltr-nums">
                        {row.deviceUserId ? (
                          <span className="rounded-md bg-[var(--color-surface-muted)] px-2 py-0.5 text-xs">
                            {row.deviceUserId}
                          </span>
                        ) : (
                          <span className="text-xs text-[var(--color-danger)]">
                            غير مسجل
                          </span>
                        )}
                      </TableCell>
                      <TableCell className="text-[var(--color-muted-foreground)]">
                        {row.hireDate ? formatDate(row.hireDate) : "—"}
                      </TableCell>
                      <TableCell>
                        <HrEmployeeStatusBadge value={row.status} />
                      </TableCell>
                      <TableCell>
                        <HrWriteOnly>
                          <div className="flex items-center gap-1.5">
                            <Button
                              variant="ghost"
                              size="sm"
                              onClick={() => openEdit(row)}
                              aria-label={`تعديل ${row.fullName}`}
                            >
                              <Pencil className="size-3.5" />
                            </Button>
                            <Button
                              variant="ghost"
                              size="sm"
                              onClick={() => remove(row)}
                              aria-label={`حذف ${row.fullName}`}
                              className="text-[var(--color-danger)]"
                            >
                              <Trash2 className="size-3.5" />
                            </Button>
                          </div>
                        </HrWriteOnly>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </div>
          </SectionCard>

          <Pagination
            page={page}
            pageSize={PAGE_SIZE}
            total={total}
            onPageChange={setPage}
          />
        </div>
      )}

      <Dialog
        open={dialogOpen}
        onClose={() => setDialogOpen(false)}
        icon={<Users className="size-5" />}
        size="lg"
        title={
          editing ? `تعديل الموظف: ${editing.fullName}` : "موظف جديد"
        }
        description="معرّف البصمة هو المفتاح الذي تُربط عليه سجلات الحضور من الجهاز."
        scrollable
      >
        <form
          id="employee-form"
          className="space-y-4"
          noValidate
          onSubmit={(e) => {
            e.preventDefault();
            submit();
          }}
        >
          {formError ? (
            <p className="rounded-lg bg-[var(--color-danger-bg)] px-3 py-2 text-sm text-[var(--color-danger)]">
              {formError}
            </p>
          ) : null}

          <div className="grid gap-4 sm:grid-cols-2">
            <HrField label="الرقم الوظيفي" htmlFor="emp-number" required>
              <Input
                id="emp-number"
                value={form.employeeNumber}
                onChange={(e) =>
                  setForm((f) => ({ ...f, employeeNumber: e.target.value }))
                }
                placeholder="EMP-001"
                maxLength={40}
                className="ltr-nums"
              />
            </HrField>

            <HrField label="اسم الموظف" htmlFor="emp-name" required>
              <Input
                id="emp-name"
                value={form.fullName}
                onChange={(e) =>
                  setForm((f) => ({ ...f, fullName: e.target.value }))
                }
                placeholder="الاسم الكامل"
                maxLength={250}
              />
            </HrField>
          </div>

          <div className="grid gap-4 sm:grid-cols-2">
            <HrField label="القسم" htmlFor="emp-dept">
              <HrSelect
                id="emp-dept"
                value={form.departmentId}
                onChange={(v) => setForm((f) => ({ ...f, departmentId: v }))}
                options={departmentOptions}
                placeholder="بدون قسم"
              />
            </HrField>

            <HrField label="المسمى الوظيفي" htmlFor="emp-pos">
              <HrSelect
                id="emp-pos"
                value={form.positionId}
                onChange={(v) => setForm((f) => ({ ...f, positionId: v }))}
                options={positionOptions}
                placeholder="بدون مسمى"
              />
            </HrField>
          </div>

          <div className="rounded-xl border border-[var(--color-border)] bg-[var(--color-surface-muted)]/40 p-4">
            <p className="mb-3 text-sm font-medium text-[var(--color-foreground)]">
              ربط بصمة الجهاز
            </p>
            <div className="grid gap-4 sm:grid-cols-3">
              <HrField
                label="معرّف البصمة"
                htmlFor="emp-punch"
                error={
                  punchIdTaken
                    ? "هذا المعرّف مستخدم بالفعل لموظف آخر"
                    : null
                }
                hint="نفس المعرّف الموجود على الجهاز (Badgnumber)"
              >
                <Input
                  id="emp-punch"
                  value={form.deviceUserId}
                  onChange={(e) =>
                    setForm((f) => ({ ...f, deviceUserId: e.target.value }))
                  }
                  placeholder="1"
                  maxLength={64}
                  className="ltr-nums"
                />
              </HrField>

              <HrField label="رقم الكارت" htmlFor="emp-card">
                <Input
                  id="emp-card"
                  value={form.cardNumber}
                  onChange={(e) =>
                    setForm((f) => ({ ...f, cardNumber: e.target.value }))
                  }
                  maxLength={64}
                  className="ltr-nums"
                />
              </HrField>

              <HrField label="PIN الجهاز" htmlFor="emp-pin">
                <Input
                  id="emp-pin"
                  value={form.deviceUserPin}
                  onChange={(e) =>
                    setForm((f) => ({ ...f, deviceUserPin: e.target.value }))
                  }
                  maxLength={64}
                  className="ltr-nums"
                />
              </HrField>
            </div>
          </div>

          <div className="grid gap-4 sm:grid-cols-2">
            <HrField label="الرقم القومي" htmlFor="emp-nid">
              <Input
                id="emp-nid"
                value={form.nationalId}
                onChange={(e) =>
                  setForm((f) => ({ ...f, nationalId: e.target.value }))
                }
                maxLength={40}
                className="ltr-nums"
              />
            </HrField>

            <HrField label="الجنس" htmlFor="emp-gender">
              <HrSelect
                id="emp-gender"
                value={form.gender}
                onChange={(v) => setForm((f) => ({ ...f, gender: v }))}
                options={HR_GENDER_OPTIONS}
                placeholder="غير محدد"
              />
            </HrField>
          </div>

          <div className="grid gap-4 sm:grid-cols-2">
            <HrField label="الهاتف" htmlFor="emp-phone">
              <Input
                id="emp-phone"
                value={form.phone}
                onChange={(e) =>
                  setForm((f) => ({ ...f, phone: e.target.value }))
                }
                maxLength={40}
                className="ltr-nums"
              />
            </HrField>

            <HrField label="البريد الإلكتروني" htmlFor="emp-email">
              <Input
                id="emp-email"
                type="email"
                value={form.email}
                onChange={(e) =>
                  setForm((f) => ({ ...f, email: e.target.value }))
                }
                maxLength={150}
                className="ltr-nums"
              />
            </HrField>
          </div>

          <HrField label="العنوان" htmlFor="emp-address">
            <Input
              id="emp-address"
              value={form.address}
              onChange={(e) =>
                setForm((f) => ({ ...f, address: e.target.value }))
              }
              maxLength={250}
            />
          </HrField>

          <div className="grid gap-4 sm:grid-cols-2">
            <HrField label="تاريخ التعيين" htmlFor="emp-hire">
              <Input
                id="emp-hire"
                type="date"
                value={form.hireDate}
                onChange={(e) =>
                  setForm((f) => ({ ...f, hireDate: e.target.value }))
                }
              />
            </HrField>

            <HrField label="نوع العقد" htmlFor="emp-contract">
              <HrSelect
                id="emp-contract"
                value={form.contractType}
                onChange={(v) =>
                  setForm((f) => ({ ...f, contractType: v }))
                }
                options={HR_CONTRACT_TYPE_OPTIONS}
                placeholder="غير محدد"
              />
            </HrField>
          </div>

          <div className="grid gap-4 sm:grid-cols-3">
            <HrField label="نهاية العقد" htmlFor="emp-contract-end">
              <Input
                id="emp-contract-end"
                type="date"
                value={form.contractEndDate}
                onChange={(e) =>
                  setForm((f) => ({ ...f, contractEndDate: e.target.value }))
                }
              />
            </HrField>

            <HrField label="الراتب الأساسي" htmlFor="emp-salary">
              <Input
                id="emp-salary"
                type="number"
                min={0}
                step="0.01"
                value={form.basicSalary}
                onChange={(e) =>
                  setForm((f) => ({ ...f, basicSalary: e.target.value }))
                }
                className="ltr-nums"
              />
            </HrField>

            <HrField label="الحالة" htmlFor="emp-status">
              <HrSelect
                id="emp-status"
                value={form.status}
                onChange={(v) => setForm((f) => ({ ...f, status: v }))}
                options={HR_EMPLOYEE_STATUS_OPTIONS}
              />
            </HrField>
          </div>

          <HrField label="ملاحظات" htmlFor="emp-notes">
            <Textarea
              id="emp-notes"
              rows={2}
              value={form.notes}
              onChange={(e) =>
                setForm((f) => ({ ...f, notes: e.target.value }))
              }
            />
          </HrField>

          <div className="mt-2 flex justify-end gap-2 border-t border-[var(--color-border)] pt-4">
            <Button
              type="submit"
              variant="gold"
              disabled={
                submitting ||
                !form.employeeNumber.trim() ||
                !form.fullName.trim() ||
                punchIdTaken
              }
            >
              {submitting
                ? "جارٍ الحفظ…"
                : editing
                  ? "حفظ التعديلات"
                  : "إضافة الموظف"}
            </Button>
            <Button
              type="button"
              variant="ghost"
              onClick={() => setDialogOpen(false)}
              disabled={submitting}
            >
              إلغاء
            </Button>
          </div>
        </form>
      </Dialog>
    </div>
  );
}