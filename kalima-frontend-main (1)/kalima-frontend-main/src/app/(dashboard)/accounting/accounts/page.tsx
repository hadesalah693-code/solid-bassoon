"use client";

import { useEffect, useMemo, useState, type FormEvent } from "react";
import Link from "next/link";
import {
  Archive,
  BookOpen,
  Lock,
  Pencil,
  Plus,
  RotateCcw,
  Save,
} from "lucide-react";

import { AccountingDialog } from "@/components/accounting/AccountingDialog";
import { AccountingRoleGate, AccountingWriteOnly } from "@/components/accounting/AccountingRoleGate";
import { DisputeModuleHero } from "@/components/disputes/DisputeModuleHero";
import { ApiErrorAlert } from "@/components/shared/ApiErrorAlert";
import { EmptyState } from "@/components/shared/EmptyState";
import { PageLoading } from "@/components/shared/PageLoading";
import { SearchInput } from "@/components/shared/SearchInput";
import { SectionCard } from "@/components/shared/SectionCard";
import { Badge } from "@/components/ui/badge";
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

import { accountsApi } from "@/lib/api/accounting";
import { ApiRequestError } from "@/lib/api/client";
import { ACCOUNT_TYPE_OPTIONS } from "@/lib/accounting/labels";
import { useDebounce } from "@/hooks/use-debounce";
import { notifyApiError, notifySuccess } from "@/lib/feedback";
import { cn } from "@/lib/utils/cn";
import type { AccountResponseDto, AccountTypeDto } from "@/types/accounting";

const selectClassName = cn(
  "flex h-10 w-full cursor-pointer appearance-none rounded-xl border border-[var(--color-border)] bg-[var(--color-surface)] px-3.5 py-2 text-sm text-[var(--color-foreground)] shadow-sm transition-colors",
  "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--color-gold-400)]",
  "disabled:cursor-not-allowed disabled:opacity-50",
);

const inputLtrClassName = "ltr-nums text-start";

type FormState = {
  code: string;
  nameAr: string;
  nameEn: string;
  type: AccountTypeDto;
  parentId: string;
  description: string;
  active: boolean;
};

const EMPTY_FORM: FormState = {
  code: "",
  nameAr: "",
  nameEn: "",
  type: "EXPENSE",
  parentId: "",
  description: "",
  active: true,
};

export default function ChartOfAccountsPage() {
  return (
    <AccountingRoleGate>
      <ChartOfAccountsPageInner />
    </AccountingRoleGate>
  );
}

function ChartOfAccountsPageInner() {
  const [rows, setRows] = useState<AccountResponseDto[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [search, setSearch] = useState("");
  const debouncedSearch = useDebounce(search, 300);
  const [type, setType] = useState<AccountTypeDto | "">("");
  const [showArchived, setShowArchived] = useState(false);
  const [fetchId, setFetchId] = useState(0);

  const [dialogOpen, setDialogOpen] = useState(false);
  const [editing, setEditing] = useState<AccountResponseDto | null>(null);
  const [form, setForm] = useState<FormState>(EMPTY_FORM);
  const [submitting, setSubmitting] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      setLoading(true);
      setError(null);
      try {
        const res = await accountsApi.list();
        if (!cancelled) setRows(res);
      } catch (e) {
        if (!cancelled) {
          setError(e instanceof ApiRequestError ? e.message : "تعذّر تحميل دليل الحسابات");
          setRows([]);
        }
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [fetchId]);

  const visible = useMemo(() => {
    const term = debouncedSearch.trim().toLowerCase();
    return rows
      .filter((a) => (showArchived ? true : a.active))
      .filter((a) => (type ? a.type === type : true))
      .filter((a) =>
        term
          ? a.code.toLowerCase().includes(term) ||
            a.nameAr.toLowerCase().includes(term) ||
            (a.nameEn ?? "").toLowerCase().includes(term)
          : true,
      );
  }, [rows, debouncedSearch, type, showArchived]);

  const grouped = useMemo(() => {
    const map = new Map<AccountTypeDto, AccountResponseDto[]>();
    for (const a of visible) {
      const list = map.get(a.type) ?? [];
      list.push(a);
      map.set(a.type, list);
    }
    return map;
  }, [visible]);

  const systemCount = useMemo(() => visible.filter((a) => a.system).length, [visible]);
  const hasFilters = Boolean(debouncedSearch.trim()) || Boolean(type) || showArchived;

  function openCreate() {
    setEditing(null);
    setForm(EMPTY_FORM);
    setFormError(null);
    setDialogOpen(true);
  }

  function openEdit(a: AccountResponseDto) {
    setEditing(a);
    setForm({
      code: a.code,
      nameAr: a.nameAr,
      nameEn: a.nameEn ?? "",
      type: a.type,
      parentId: a.parentId ?? "",
      description: a.description ?? "",
      active: a.active,
    });
    setFormError(null);
    setDialogOpen(true);
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setFormError(null);

    if (!form.nameAr.trim()) {
      setFormError("اسم الحساب بالعربية مطلوب");
      return;
    }
    if (!editing && !form.code.trim()) {
      setFormError("رمز الحساب مطلوب");
      return;
    }
    if (form.parentId && form.parentId === editing?.id) {
      setFormError("لا يمكن أن يكون الحساب تابعاً لنفسه");
      return;
    }

    setSubmitting(true);
    try {
      if (editing) {
        await accountsApi.update(editing.id, {
          nameAr: form.nameAr.trim(),
          nameEn: form.nameEn.trim() || null,
          parentId: form.parentId || null,
          description: form.description.trim() || null,
          active: form.active,
        });
        notifySuccess("تم تحديث الحساب");
      } else {
        await accountsApi.create({
          code: form.code.trim(),
          nameAr: form.nameAr.trim(),
          nameEn: form.nameEn.trim() || null,
          type: form.type,
          parentId: form.parentId || null,
          description: form.description.trim() || null,
        });
        notifySuccess("تم إضافة الحساب");
      }
      setDialogOpen(false);
      setFetchId((n) => n + 1);
    } catch (err) {
      if (err instanceof ApiRequestError) setFormError(err.message);
      else setFormError("تعذّر حفظ الحساب");
      notifyApiError(err, "تعذّر حفظ الحساب");
    } finally {
      setSubmitting(false);
    }
  }

  async function handleArchive(a: AccountResponseDto) {
    if (
      !window.confirm(
        `أرشفة الحساب «${a.code} — ${a.nameAr}»؟ لن يمكن الترحيل عليه بعد الآن.`,
      )
    ) {
      return;
    }
    try {
      await accountsApi.archive(a.id);
      notifySuccess("تمت أرشفة الحساب");
      setFetchId((n) => n + 1);
    } catch (err) {
      notifyApiError(err, "تعذّرت أرشفة الحساب");
    }
  }

  return (
    <div dir="rtl" className="space-y-6 pb-10">
      <DisputeModuleHero
        icon={BookOpen}
        eyebrow="المالية والمحاسبة"
        title="دليل الحسابات"
        description="شجرة الحسابات المحاسبية. الحسابات النظامية لا يمكن تعديلها أو أرشفتها للحفاظ على سلامة الترحيل التلقائي."
        badges={
          !loading && !error ? (
            <span className="rounded-lg bg-white/10 px-2.5 py-1 text-xs font-medium text-[var(--color-gold-200)] ring-1 ring-white/15">
              {visible.length.toLocaleString("ar-EG")} حساب
            </span>
          ) : null
        }
        actions={
          <AccountingWriteOnly>
            <Button
              className="border-white/25 bg-[var(--color-gold-400)] text-[var(--color-navy-950)] hover:bg-[var(--color-gold-300)]"
              onClick={openCreate}
            >
              <Plus className="size-4" />
              حساب جديد
            </Button>
          </AccountingWriteOnly>
        }
      />

      <SectionCard title="البحث والتصفية" contentClassName="space-y-3">
        <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-4">
          <div className="xl:col-span-2">
            <SearchInput
              value={search}
              onChange={setSearch}
              placeholder="بحث بالرمز أو الاسم..."
            />
          </div>
          <select
            className={selectClassName}
            value={type}
            onChange={(e) => setType(e.target.value as AccountTypeDto | "")}
            aria-label="نوع الحساب"
          >
            <option value="">كل الأنواع</option>
            {ACCOUNT_TYPE_OPTIONS.map((o) => (
              <option key={o.value} value={o.value}>{o.label}</option>
            ))}
          </select>
          <label className="flex h-10 cursor-pointer items-center gap-2 rounded-xl border border-[var(--color-border)] bg-[var(--color-surface)] px-3.5 text-sm">
            <input
              type="checkbox"
              checked={showArchived}
              onChange={(e) => setShowArchived(e.target.checked)}
              className="size-4 accent-[var(--color-gold-400)]"
            />
            إظهار المؤرشفة
          </label>
        </div>
        {hasFilters ? (
          <div className="flex items-center justify-end border-t border-[var(--color-border)] pt-3">
            <Button
              type="button"
              variant="ghost"
              size="sm"
              className="h-8 gap-1.5 text-[var(--color-muted-foreground)]"
              onClick={() => { setSearch(""); setType(""); setShowArchived(false); }}
            >
              <RotateCcw className="size-3.5" />
              مسح التصفية
            </Button>
          </div>
        ) : null}
      </SectionCard>

      {error ? (
        <ApiErrorAlert message={error} onRetry={() => setFetchId((n) => n + 1)} />
      ) : null}

      {loading ? (
        <PageLoading label="جاري تحميل دليل الحسابات..." />
      ) : visible.length === 0 ? (
        <SectionCard>
          <EmptyState
            icon={BookOpen}
            title={hasFilters ? "لا توجد نتائج" : "لا توجد حسابات"}
            description={
              hasFilters
                ? "جرّب تعديل البحث أو إزالة التصفية."
                : "ابدأ بإضافة أول حساب محاسبي."
            }
            action={
              hasFilters ? (
                <Button variant="outline" onClick={() => { setSearch(""); setType(""); setShowArchived(false); }}>
                  مسح التصفية
                </Button>
              ) : (
                  <AccountingWriteOnly>
                  <Button onClick={openCreate}><Plus className="size-4" />حساب جديد</Button>
                  </AccountingWriteOnly>
              )
            }
          />
        </SectionCard>
      ) : (
        <div className="space-y-4">
          {ACCOUNT_TYPE_OPTIONS.filter((o) => grouped.has(o.value)).map((o) => (
            <SectionCard key={o.value} flush title={o.label}>
              <div className="overflow-x-auto">
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead className="text-right">الرمز</TableHead>
                      <TableHead className="text-right">الاسم</TableHead>
                      <TableHead className="text-right">الاسم بالإنجليزية</TableHead>
                      <TableHead className="text-right">الحساب الأب</TableHead>
                      <TableHead className="text-right">الحالة</TableHead>
                      <TableHead className="text-right">دفتر الحساب</TableHead>
                      <TableHead className="text-right">إجراءات</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {grouped.get(o.value)?.map((a) => (
                      <TableRow key={a.id}>
                        <TableCell className="font-semibold ltr-nums">{a.code}</TableCell>
                        <TableCell>{a.nameAr}</TableCell>
                        <TableCell className="text-[var(--color-muted-foreground)]">
                          {a.nameEn ?? "—"}
                        </TableCell>
                        <TableCell className="ltr-nums text-[var(--color-muted-foreground)]">
                          {a.parentCode ?? "—"}
                        </TableCell>
                        <TableCell>
                          <div className="flex flex-wrap gap-1.5">
                            {a.system ? (
                              <Badge tone="navy"><Lock className="size-3" />نظامي</Badge>
                            ) : null}
                            <Badge tone={a.active ? "success" : "neutral"}>
                              {a.active ? "نشط" : "مؤرشف"}
                            </Badge>
                          </div>
                        </TableCell>
                        <TableCell><Button asChild size="sm" variant="outline"><Link href={`/accounting/accounts/${a.id}/ledger`}>عرض الدفتر</Link></Button></TableCell>
                        <TableCell>
                          <AccountingWriteOnly>
                            <div className="flex items-center gap-1.5">
                              <Button
                                variant="ghost"
                                size="sm"
                                onClick={() => openEdit(a)}
                                aria-label={`تعديل ${a.nameAr}`}
                              >
                                <Pencil className="size-3.5" />
                              </Button>
                              {!a.system ? (
                                <Button
                                  variant="ghost"
                                  size="sm"
                                  onClick={() => handleArchive(a)}
                                  aria-label={`أرشفة ${a.nameAr}`}
                                  className="text-[var(--color-danger)]"
                                >
                                  <Archive className="size-3.5" />
                                </Button>
                              ) : null}
                            </div>
                          </AccountingWriteOnly>
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </div>
            </SectionCard>
          ))}
          {systemCount > 0 ? (
            <p className="text-xs text-[var(--color-muted-foreground)]">
              {systemCount.toLocaleString("ar-EG")} حساب نظامي محمي من التعديل والأرشفة.
            </p>
          ) : null}
        </div>
      )}

      <AccountingDialog
        open={dialogOpen}
        onClose={() => setDialogOpen(false)}
        title={editing ? "تعديل حساب" : "حساب جديد"}
        description={
          editing
            ? "تعديل الاسم والتبعية. رمز الحساب ثابت بعد الإنشاء."
            : "أضف حساباً إلى دليل الحسابات ليظهر في قيودك."
        }
        icon={<BookOpen className="size-5" />}
      >
        <form id="account-form" onSubmit={handleSubmit} className="space-y-4" noValidate>
          {formError ? <ApiErrorAlert message={formError} onRetry={() => setFormError(null)} /> : null}

          <div className="grid gap-4 md:grid-cols-2">
            <div className="space-y-1.5">
              <Label htmlFor="acc-code">رمز الحساب *</Label>
              <Input
                id="acc-code"
                value={form.code}
                onChange={(e) => setForm({ ...form, code: e.target.value })}
                disabled={Boolean(editing) || submitting}
                placeholder="مثال: 5300"
                className={inputLtrClassName}
                dir="ltr"
              />
              {editing ? (
                <p className="text-xs text-[var(--color-muted-foreground)]">
                  الرمز غير قابل للتعديل بعد الإنشاء.
                </p>
              ) : null}
            </div>

            <div className="space-y-1.5">
              <Label htmlFor="acc-type">نوع الحساب *</Label>
              <select
                id="acc-type"
                className={selectClassName}
                value={form.type}
                disabled={Boolean(editing) || submitting}
                onChange={(e) => setForm({ ...form, type: e.target.value as AccountTypeDto })}
              >
                {ACCOUNT_TYPE_OPTIONS.map((o) => (
                  <option key={o.value} value={o.value}>{o.label}</option>
                ))}
              </select>
            </div>

            <div className="space-y-1.5">
              <Label htmlFor="acc-name-ar">الاسم بالعربية *</Label>
              <Input
                id="acc-name-ar"
                value={form.nameAr}
                onChange={(e) => setForm({ ...form, nameAr: e.target.value })}
                disabled={submitting}
                placeholder="مثال: مصروفات إيجار"
              />
            </div>

            <div className="space-y-1.5">
              <Label htmlFor="acc-name-en">الاسم بالإنجليزية</Label>
              <Input
                id="acc-name-en"
                value={form.nameEn}
                onChange={(e) => setForm({ ...form, nameEn: e.target.value })}
                disabled={submitting}
                placeholder="Rent expense"
                dir="ltr"
              />
            </div>

            <div className="space-y-1.5 md:col-span-2">
              <Label htmlFor="acc-parent">الحساب الأب</Label>
              <select
                id="acc-parent"
                className={selectClassName}
                value={form.parentId}
                onChange={(e) => setForm({ ...form, parentId: e.target.value })}
                disabled={submitting}
              >
                <option value="">— حساب رئيسي —</option>
                {rows
                  .filter((a) => a.id !== editing?.id && a.active)
                  .map((a) => (
                    <option key={a.id} value={a.id}>
                      {a.code} — {a.nameAr}
                    </option>
                  ))}
              </select>
            </div>

            <div className="space-y-1.5 md:col-span-2">
              <Label htmlFor="acc-desc">الوصف</Label>
              <Textarea
                id="acc-desc"
                rows={3}
                value={form.description}
                onChange={(e) => setForm({ ...form, description: e.target.value })}
                disabled={submitting}
                placeholder="ملاحظة توضيحية عن طبيعة الحساب..."
              />
            </div>
          </div>

          {editing && !editing.system ? (
            <label className="flex cursor-pointer items-center gap-2 rounded-xl border border-[var(--color-border)] bg-[var(--color-surface-muted)] p-3 text-sm">
              <input
                type="checkbox"
                checked={form.active}
                onChange={(e) => setForm({ ...form, active: e.target.checked })}
                disabled={submitting}
                className="size-4 accent-[var(--color-gold-400)]"
              />
              الحساب نشط ويمكن الترحيل عليه
            </label>
          ) : null}
        </form>

        <div className="mt-4 flex justify-end gap-2 border-t border-[var(--color-border)] pt-4">
          <Button
            type="submit"
            form="account-form"
            variant="gold"
            disabled={submitting || editing?.system === true}
          >
            {submitting ? (
              "جارٍ الحفظ…"
            ) : (
              <>
                <Save className="size-4" />
                {editing ? "حفظ التعديلات" : "إضافة الحساب"}
              </>
            )}
          </Button>
          <Button type="button" variant="ghost" onClick={() => setDialogOpen(false)} disabled={submitting}>
            إلغاء
          </Button>
        </div>
      </AccountingDialog>
    </div>
  );
}
