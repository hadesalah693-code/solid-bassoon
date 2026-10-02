"use client";

import { useCallback, useEffect, useState } from "react";

import { Building2, Pencil, Plus, RotateCcw, Trash2 } from "lucide-react";

import { HrField, HrToggle } from "@/components/hr/HrField";
import { HrRoleGate, HrWriteOnly } from "@/components/hr/HrRoleGate";
import { ActiveStatusBadge } from "@/components/shared/StatusBadge";
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
import { hrDepartmentsApi } from "@/lib/api/hr";
import type { HrDepartmentDto } from "@/types/hr";

const PAGE_SIZE = 20;

type FormState = {
  name: string;
  code: string;
  parentId: string;
  description: string;
  active: boolean;
};

const EMPTY_FORM: FormState = {
  name: "",
  code: "",
  parentId: "",
  description: "",
  active: true,
};

export default function HrDepartmentsPage() {
  return (
    <HrRoleGate>
      <HrDepartmentsPageInner />
    </HrRoleGate>
  );
}

function HrDepartmentsPageInner() {
  const [rows, setRows] = useState<HrDepartmentDto[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [search, setSearch] = useState("");
  const [activeOnly, setActiveOnly] = useState(false);
  const debouncedSearch = useDebounce(search, 350);

  const [options, setOptions] = useState<HrDepartmentDto[]>([]);
  const [dialogOpen, setDialogOpen] = useState(false);
  const [editing, setEditing] = useState<HrDepartmentDto | null>(null);
  const [form, setForm] = useState<FormState>(EMPTY_FORM);
  const [submitting, setSubmitting] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);
  const [fetchId, setFetchId] = useState(0);

  const hasFilters = !!search.trim() || activeOnly;

  const refresh = useCallback(() => setFetchId((n) => n + 1), []);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const res = await hrDepartmentsApi.options();
        if (!cancelled) setOptions(res);
      } catch {
        // The picker degrades to "no parent"; not worth surfacing on its own.
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
        const res = await hrDepartmentsApi.list({
          page,
          size: PAGE_SIZE,
          search: debouncedSearch,
          active: activeOnly ? true : undefined,
        });
        if (!cancelled) {
          setRows(res.content);
          setTotal(res.totalElements);
        }
      } catch (e) {
        if (!cancelled) {
          setError(
            e instanceof ApiRequestError ? e.message : "تعذّر تحميل الأقسام",
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
  }, [page, debouncedSearch, activeOnly, fetchId]);

  function openCreate() {
    setEditing(null);
    setForm(EMPTY_FORM);
    setFormError(null);
    setDialogOpen(true);
  }

  function openEdit(row: HrDepartmentDto) {
    setEditing(row);
    setForm({
      name: row.name,
      code: row.code ?? "",
      parentId: row.parentId ?? "",
      description: row.description ?? "",
      active: row.active,
    });
    setFormError(null);
    setDialogOpen(true);
  }

  async function submit() {
    if (!form.name.trim()) {
      setFormError("اسم القسم مطلوب");
      return;
    }
    setSubmitting(true);
    setFormError(null);
    try {
      const body = {
        name: form.name.trim(),
        code: form.code.trim() || null,
        parentId: form.parentId || null,
        description: form.description.trim() || null,
        active: form.active,
      };
      if (editing) {
        await hrDepartmentsApi.update(editing.id, body);
      } else {
        await hrDepartmentsApi.create(body);
      }
      setDialogOpen(false);
      refresh();
    } catch (e) {
      setFormError(
        e instanceof ApiRequestError ? e.message : "تعذّر حفظ القسم",
      );
    } finally {
      setSubmitting(false);
    }
  }

  async function remove(row: HrDepartmentDto) {
    if (
      !window.confirm(
        `سيتم حذف القسم «${row.name}». الموظفون المرتبطون به لن يُحذفوا، لكن بدون قسم. هل تريد المتابعة؟`,
      )
    ) {
      return;
    }
    try {
      await hrDepartmentsApi.remove(row.id);
      refresh();
    } catch (e) {
      setError(e instanceof ApiRequestError ? e.message : "تعذّر حذف القسم");
    }
  }

  return (
    <div>
      <PageHeader
        title="الأقسام"
        description="تنظيم موارد البشرية داخل المكتب، مع إمكانية ربط القسم بقسم رئيسي."
        actions={
          <HrWriteOnly>
            <Button onClick={openCreate}>
              <Plus className="size-4" />
              قسم جديد
            </Button>
          </HrWriteOnly>
        }
      />

      <SectionCard className="mb-4">
        <div className="flex flex-wrap items-center gap-3">
          <SearchInput
            value={search}
            onChange={setSearch}
            placeholder="ابحث باسم القسم أو الكود..."
            className="min-w-[16rem] flex-1"
          />
          <HrToggle
            checked={activeOnly}
            onChange={setActiveOnly}
            label="الفعّالة فقط"
          />
          {hasFilters ? (
            <Button
              variant="ghost"
              onClick={() => {
                setSearch("");
                setActiveOnly(false);
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
        <PageLoading label="جاري تحميل الأقسام..." />
      ) : rows.length === 0 ? (
        <SectionCard>
          <EmptyState
            icon={Building2}
            title={hasFilters ? "لا توجد نتائج" : "لا توجد أقسام"}
            description={
              hasFilters
                ? "جرّب تعديل البحث أو إزالة التصفية."
                : "ابدأ بإضافة أول قسم للموارد البشرية."
            }
            action={
              hasFilters ? (
                <Button
                  variant="outline"
                  onClick={() => {
                    setSearch("");
                    setActiveOnly(false);
                  }}
                >
                  مسح التصفية
                </Button>
              ) : (
                <HrWriteOnly>
                  <Button onClick={openCreate}>قسم جديد</Button>
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
                    <TableHead className="text-right">الاسم</TableHead>
                    <TableHead className="text-right">الكود</TableHead>
                    <TableHead className="text-right">القسم الرئيسي</TableHead>
                    <TableHead className="text-right">المسؤول</TableHead>
                    <TableHead className="text-right">الحالة</TableHead>
                    <TableHead className="text-right">إجراءات</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {rows.map((row) => (
                    <TableRow key={row.id}>
                      <TableCell className="font-semibold">
                        {row.name}
                      </TableCell>
                      <TableCell className="ltr-nums text-[var(--color-muted-foreground)]">
                        {row.code ?? "—"}
                      </TableCell>
                      <TableCell className="text-[var(--color-muted-foreground)]">
                        {row.parentName ?? "—"}
                      </TableCell>
                      <TableCell className="text-[var(--color-muted-foreground)]">
                        {row.manager?.fullName ?? "—"}
                      </TableCell>
                      <TableCell>
                        <ActiveStatusBadge active={row.active} />
                      </TableCell>
                      <TableCell>
                        <HrWriteOnly>
                          <div className="flex items-center gap-1.5">
                            <Button
                              variant="ghost"
                              size="sm"
                              onClick={() => openEdit(row)}
                              aria-label={`تعديل ${row.name}`}
                            >
                              <Pencil className="size-3.5" />
                            </Button>
                            <Button
                              variant="ghost"
                              size="sm"
                              onClick={() => remove(row)}
                              aria-label={`حذف ${row.name}`}
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
        icon={<Building2 className="size-5" />}
        title={editing ? `تعديل القسم: ${editing.name}` : "قسم جديد"}
        description={
          editing
            ? "تعديل الاسم أو التبعية. كود القسم اختياري."
            : "أنشئ قسماً جديداً وحدد القسم الرئيسي إن وُجد."
        }
        scrollable
      >
        <form
          id="dept-form"
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

          <HrField label="اسم القسم" htmlFor="dept-name" required>
            <Input
              id="dept-name"
              value={form.name}
              onChange={(e) => setForm((f) => ({ ...f, name: e.target.value }))}
              placeholder="مثال: إدارة الموارد البشرية"
              maxLength={150}
            />
          </HrField>

          <div className="grid gap-4 sm:grid-cols-2">
            <HrField
              label="الكود"
              htmlFor="dept-code"
              hint="معرّف اختياري للتقارير"
            >
              <Input
                id="dept-code"
                value={form.code}
                onChange={(e) => setForm((f) => ({ ...f, code: e.target.value }))}
                placeholder="HR"
                maxLength={40}
                className="ltr-nums"
              />
            </HrField>

            <HrField label="القسم الرئيسي" htmlFor="dept-parent">
              <select
                id="dept-parent"
                className="flex h-10 w-full cursor-pointer appearance-none rounded-xl border border-[var(--color-border)] bg-[var(--color-surface)] px-3.5 py-2 text-sm shadow-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--color-gold-400)]"
                value={form.parentId}
                onChange={(e) =>
                  setForm((f) => ({ ...f, parentId: e.target.value }))
                }
              >
                <option value="">بدون قسم رئيسي</option>
                {options
                  .filter((o) => o.id !== editing?.id)
                  .map((o) => (
                    <option key={o.id} value={o.id}>
                      {o.name}
                    </option>
                  ))}
              </select>
            </HrField>
          </div>

          <HrField label="الوصف" htmlFor="dept-desc">
            <Textarea
              id="dept-desc"
              rows={3}
              value={form.description}
              onChange={(e) =>
                setForm((f) => ({ ...f, description: e.target.value }))
              }
              placeholder="مهام القسم ونطاق العمل"
            />
          </HrField>

          <HrToggle
            id="dept-active"
            checked={form.active}
            onChange={(active) => setForm((f) => ({ ...f, active }))}
            label="القسم فعّال"
          />

          <div className="mt-2 flex justify-end gap-2 border-t border-[var(--color-border)] pt-4">
            <Button
              type="submit"
              variant="gold"
              disabled={submitting || !form.name.trim()}
            >
              {submitting
                ? "جارٍ الحفظ…"
                : editing
                  ? "حفظ التعديلات"
                  : "إضافة القسم"}
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