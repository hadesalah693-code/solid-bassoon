"use client";

import { useCallback, useEffect, useState } from "react";

import { Briefcase, Pencil, Plus, RotateCcw, Trash2 } from "lucide-react";

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
import { hrDepartmentsApi, hrPositionsApi } from "@/lib/api/hr";
import type { HrDepartmentDto, HrPositionDto } from "@/types/hr";

const PAGE_SIZE = 20;

type FormState = {
  title: string;
  code: string;
  departmentId: string;
  description: string;
  active: boolean;
};

const EMPTY_FORM: FormState = {
  title: "",
  code: "",
  departmentId: "",
  description: "",
  active: true,
};

export default function HrPositionsPage() {
  return (
    <HrRoleGate>
      <HrPositionsPageInner />
    </HrRoleGate>
  );
}

function HrPositionsPageInner() {
  const [rows, setRows] = useState<HrPositionDto[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [search, setSearch] = useState("");
  const [departmentFilter, setDepartmentFilter] = useState("");
  const [activeOnly, setActiveOnly] = useState(false);
  const debouncedSearch = useDebounce(search, 350);

  const [departments, setDepartments] = useState<HrDepartmentDto[]>([]);
  const [dialogOpen, setDialogOpen] = useState(false);
  const [editing, setEditing] = useState<HrPositionDto | null>(null);
  const [form, setForm] = useState<FormState>(EMPTY_FORM);
  const [submitting, setSubmitting] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);
  const [fetchId, setFetchId] = useState(0);

  const hasFilters = !!search.trim() || !!departmentFilter || activeOnly;

  const refresh = useCallback(() => setFetchId((n) => n + 1), []);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const res = await hrDepartmentsApi.options();
        if (!cancelled) setDepartments(res);
      } catch {
        // The picker degrades to "no department"; not worth surfacing on its own.
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
        const res = await hrPositionsApi.list({
          page,
          size: PAGE_SIZE,
          search: debouncedSearch,
          departmentId: departmentFilter || undefined,
          active: activeOnly ? true : undefined,
        });
        if (!cancelled) {
          setRows(res.content);
          setTotal(res.totalElements);
        }
      } catch (e) {
        if (!cancelled) {
          setError(
            e instanceof ApiRequestError ? e.message : "تعذّر تحميل المسميات الوظيفية",
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
  }, [page, debouncedSearch, departmentFilter, activeOnly, fetchId]);

  function openCreate() {
    setEditing(null);
    setForm(EMPTY_FORM);
    setFormError(null);
    setDialogOpen(true);
  }

  function openEdit(row: HrPositionDto) {
    setEditing(row);
    setForm({
      title: row.title,
      code: row.code ?? "",
      departmentId: row.departmentId ?? "",
      description: row.description ?? "",
      active: row.active,
    });
    setFormError(null);
    setDialogOpen(true);
  }

  async function submit() {
    if (!form.title.trim()) {
      setFormError("المسمى الوظيفي مطلوب");
      return;
    }
    setSubmitting(true);
    setFormError(null);
    try {
      const body = {
        title: form.title.trim(),
        code: form.code.trim() || null,
        departmentId: form.departmentId || null,
        description: form.description.trim() || null,
        active: form.active,
      };
      if (editing) {
        await hrPositionsApi.update(editing.id, body);
      } else {
        await hrPositionsApi.create(body);
      }
      setDialogOpen(false);
      refresh();
    } catch (e) {
      setFormError(
        e instanceof ApiRequestError ? e.message : "تعذّر حفظ المسمى الوظيفي",
      );
    } finally {
      setSubmitting(false);
    }
  }

  async function remove(row: HrPositionDto) {
    if (
      !window.confirm(
        `سيتم حذف المسمى «${row.title}». الموظفون المرتبطون به لن يُحذفوا، لكن بدون مسمى. هل تريد المتابعة؟`,
      )
    ) {
      return;
    }
    try {
      await hrPositionsApi.remove(row.id);
      refresh();
    } catch (e) {
      setError(
        e instanceof ApiRequestError ? e.message : "تعذّر حذف المسمى الوظيفي",
      );
    }
  }

  const departmentOptions = departments.map((d) => ({
    value: d.id,
    label: d.name,
  }));

  return (
    <div>
      <PageHeader
        title="المسميات الوظيفية"
        description="المهام الوظيفية داخل الأقسام، تُربط بالموظفين لحساب تقارير الحضور."
        actions={
          <HrWriteOnly>
            <Button onClick={openCreate}>
              <Plus className="size-4" />
              مسمى جديد
            </Button>
          </HrWriteOnly>
        }
      />

      <SectionCard className="mb-4">
        <div className="flex flex-wrap items-center gap-3">
          <SearchInput
            value={search}
            onChange={setSearch}
            placeholder="ابحث بالمسمى أو الكود..."
            className="min-w-[15rem] flex-1"
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
                setDepartmentFilter("");
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
        <PageLoading label="جاري تحميل المسميات الوظيفية..." />
      ) : rows.length === 0 ? (
        <SectionCard>
          <EmptyState
            icon={Briefcase}
            title={hasFilters ? "لا توجد نتائج" : "لا توجد مسميات وظيفية"}
            description={
              hasFilters
                ? "جرّب تعديل البحث أو إزالة التصفية."
                : "ابدأ بإضافة أول مسمى وظيفي."
            }
            action={
              hasFilters ? (
                <Button
                  variant="outline"
                  onClick={() => {
                    setSearch("");
                    setDepartmentFilter("");
                    setActiveOnly(false);
                  }}
                >
                  مسح التصفية
                </Button>
              ) : (
                <HrWriteOnly>
                  <Button onClick={openCreate}>مسمى جديد</Button>
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
                    <TableHead className="text-right">المسمى</TableHead>
                    <TableHead className="text-right">الكود</TableHead>
                    <TableHead className="text-right">القسم</TableHead>
                    <TableHead className="text-right">الحالة</TableHead>
                    <TableHead className="text-right">إجراءات</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {rows.map((row) => (
                    <TableRow key={row.id}>
                      <TableCell className="font-semibold">{row.title}</TableCell>
                      <TableCell className="ltr-nums text-[var(--color-muted-foreground)]">
                        {row.code ?? "—"}
                      </TableCell>
                      <TableCell className="text-[var(--color-muted-foreground)]">
                        {row.departmentName ?? "—"}
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
                              aria-label={`تعديل ${row.title}`}
                            >
                              <Pencil className="size-3.5" />
                            </Button>
                            <Button
                              variant="ghost"
                              size="sm"
                              onClick={() => remove(row)}
                              aria-label={`حذف ${row.title}`}
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
        icon={<Briefcase className="size-5" />}
        title={editing ? `تعديل المسمى: ${editing.title}` : "مسمى وظيفي جديد"}
        description="اربط المسمى بالقسم المسؤول عن Tasks."
        scrollable
      >
        <form
          id="position-form"
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

          <HrField label="المسمى الوظيفي" htmlFor="pos-title" required>
            <Input
              id="pos-title"
              value={form.title}
              onChange={(e) =>
                setForm((f) => ({ ...f, title: e.target.value }))
              }
              placeholder="مثال: محاسب أول"
              maxLength={150}
            />
          </HrField>

          <div className="grid gap-4 sm:grid-cols-2">
            <HrField
              label="الكود"
              htmlFor="pos-code"
              hint="معرّف اختياري للتقارير"
            >
              <Input
                id="pos-code"
                value={form.code}
                onChange={(e) =>
                  setForm((f) => ({ ...f, code: e.target.value }))
                }
                placeholder="ACC-01"
                maxLength={40}
                className="ltr-nums"
              />
            </HrField>

            <HrField label="القسم" htmlFor="pos-dept">
              <select
                id="pos-dept"
                className="flex h-10 w-full cursor-pointer appearance-none rounded-xl border border-[var(--color-border)] bg-[var(--color-surface)] px-3.5 py-2 text-sm shadow-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--color-gold-400)]"
                value={form.departmentId}
                onChange={(e) =>
                  setForm((f) => ({ ...f, departmentId: e.target.value }))
                }
              >
                <option value="">بدون قسم</option>
                {departmentOptions.map((o) => (
                  <option key={o.value} value={o.value}>
                    {o.label}
                  </option>
                ))}
              </select>
            </HrField>
          </div>

          <HrField label="الوصف" htmlFor="pos-desc">
            <Textarea
              id="pos-desc"
              rows={3}
              value={form.description}
              onChange={(e) =>
                setForm((f) => ({ ...f, description: e.target.value }))
              }
              placeholder="مسؤوليات المسمى"
            />
          </HrField>

          <HrToggle
            id="pos-active"
            checked={form.active}
            onChange={(active) => setForm((f) => ({ ...f, active }))}
            label="المسمى فعّال"
          />

          <div className="mt-2 flex justify-end gap-2 border-t border-[var(--color-border)] pt-4">
            <Button
              type="submit"
              variant="gold"
              disabled={submitting || !form.title.trim()}
            >
              {submitting
                ? "جارٍ الحفظ…"
                : editing
                  ? "حفظ التعديلات"
                  : "إضافة المسمى"}
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