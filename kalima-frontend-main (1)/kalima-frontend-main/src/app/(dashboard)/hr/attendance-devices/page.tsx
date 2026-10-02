"use client";

import { useCallback, useEffect, useState } from "react";

import {
  Fingerprint,
  Pencil,
  Plus,
  RefreshCw,
  RotateCcw,
  Trash2,
  Wifi,
  WifiOff,
} from "lucide-react";

import { HrField, HrSelect, HrToggle } from "@/components/hr/HrField";
import { HrRoleGate, HrWriteOnly } from "@/components/hr/HrRoleGate";
import { HrDeviceStatusBadge } from "@/components/hr/HrStatusBadge";
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
import { hrDevicesApi } from "@/lib/api/hr";
import {
  HR_DEVICE_PROTOCOL_AR,
  HR_DEVICE_PROTOCOL_OPTIONS,
  HR_DEVICE_STATUS_OPTIONS,
} from "@/lib/hr/labels";
import { formatDateTime } from "@/lib/utils/format-date";
import type {
  HrAttendanceDeviceDto,
  HrDeviceProtocolDto,
  HrDeviceStatusDto,
  HrSyncResultDto,
} from "@/types/hr";

const PAGE_SIZE = 20;

type FormState = {
  name: string;
  location: string;
  ipAddress: string;
  port: string;
  protocol: string;
  serialNumber: string;
  status: string;
};

const EMPTY_FORM: FormState = {
  name: "",
  location: "",
  ipAddress: "",
  port: "4370",
  protocol: "ZK_TECO_TCP",
  serialNumber: "",
  status: "ACTIVE",
};

export default function HrAttendanceDevicesPage() {
  return (
    <HrRoleGate>
      <HrAttendanceDevicesPageInner />
    </HrRoleGate>
  );
}

function HrAttendanceDevicesPageInner() {
  const [rows, setRows] = useState<HrAttendanceDeviceDto[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [search, setSearch] = useState("");
  const [statusFilter, setStatusFilter] = useState("");
  const debouncedSearch = useDebounce(search, 350);

  const [dialogOpen, setDialogOpen] = useState(false);
  const [editing, setEditing] = useState<HrAttendanceDeviceDto | null>(null);
  const [form, setForm] = useState<FormState>(EMPTY_FORM);
  const [submitting, setSubmitting] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);
  const [fetchId, setFetchId] = useState(0);

  const [busyId, setBusyId] = useState<string | null>(null);
  const [notice, setNotice] = useState<{
    tone: "success" | "danger";
    text: string;
  } | null>(null);

  const hasFilters = !!search.trim() || !!statusFilter;

  const refresh = useCallback(() => setFetchId((n) => n + 1), []);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      setLoading(true);
      setError(null);
      try {
        const res = await hrDevicesApi.list({
          page,
          size: PAGE_SIZE,
          search: debouncedSearch,
          status: (statusFilter || undefined) as
            | HrDeviceStatusDto
            | undefined,
        });
        if (!cancelled) {
          setRows(res.content);
          setTotal(res.totalElements);
        }
      } catch (e) {
        if (!cancelled) {
          setError(
            e instanceof ApiRequestError ? e.message : "تعذّر تحميل الأجهزة",
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
  }, [page, debouncedSearch, statusFilter, fetchId]);

  function openCreate() {
    setEditing(null);
    setForm(EMPTY_FORM);
    setFormError(null);
    setNotice(null);
    setDialogOpen(true);
  }

  function openEdit(row: HrAttendanceDeviceDto) {
    setEditing(row);
    setForm({
      name: row.name,
      location: row.location ?? "",
      ipAddress: row.ipAddress,
      port: String(row.port),
      protocol: row.protocol,
      serialNumber: row.serialNumber ?? "",
      status: row.status,
    });
    setFormError(null);
    setNotice(null);
    setDialogOpen(true);
  }

  async function submit() {
    if (!form.name.trim() || !form.ipAddress.trim()) {
      setFormError("اسم الجهاز وعنوان IP مطلوبان");
      return;
    }
    setSubmitting(true);
    setFormError(null);
    try {
      const body = {
        name: form.name.trim(),
        location: form.location.trim() || null,
        ipAddress: form.ipAddress.trim(),
        port: form.port.trim() ? Number(form.port) : null,
        protocol: form.protocol as HrDeviceProtocolDto,
        serialNumber: form.serialNumber.trim() || null,
        status: form.status as HrAttendanceDeviceDto["status"],
      };
      if (editing) {
        await hrDevicesApi.update(editing.id, body);
      } else {
        await hrDevicesApi.create(body);
      }
      setDialogOpen(false);
      refresh();
    } catch (e) {
      setFormError(
        e instanceof ApiRequestError ? e.message : "تعذّر حفظ بيانات الجهاز",
      );
    } finally {
      setSubmitting(false);
    }
  }

  /** Tests connectivity, including for a device not saved yet. */
  async function testConnection(id?: string) {
    setBusyId(id ?? "__form__");
    setNotice(null);
    try {
      const res = id
        ? await hrDevicesApi.test(id)
        : await hrDevicesApi.testUnsaved(
            form.ipAddress.trim(),
            form.port.trim() ? Number(form.port) : undefined,
          );
      setNotice(
        res.reachable
          ? { tone: "success", text: `${res.message}${res.firmware ? ` — ${res.firmware}` : ""}` }
          : { tone: "danger", text: res.message },
      );
    } catch (e) {
      setNotice({
        tone: "danger",
        text:
          e instanceof ApiRequestError
            ? e.message
            : "تعذّر الاتصال بالجهاز",
      });
    } finally {
      setBusyId(null);
    }
  }

  async function sync(row: HrAttendanceDeviceDto) {
    setBusyId(row.id);
    setNotice(null);
    try {
      const result: HrSyncResultDto = await hrDevicesApi.sync(row.id);
      setNotice({
        tone: result.inserted > 0 ? "success" : "success",
        text: result.message,
      });
      refresh();
    } catch (e) {
      setNotice({
        tone: "danger",
        text:
          e instanceof ApiRequestError ? e.message : "تعذّر سحب سجلات الحضور",
      });
      refresh();
    } finally {
      setBusyId(null);
    }
  }

  async function remove(row: HrAttendanceDeviceDto) {
    if (
      !window.confirm(
        `سيتم حذف الجهاز «${row.name}» مع كل سجلات الحضور المسحوبة منه. هل تريد المتابعة؟`,
      )
    ) {
      return;
    }
    try {
      await hrDevicesApi.remove(row.id);
      refresh();
    } catch (e) {
      setError(e instanceof ApiRequestError ? e.message : "تعذّر حذف الجهاز");
    }
  }

  return (
    <div>
      <PageHeader
        title="أجهزة البصمة"
        description="تسجيل أجهزة ZKTeco على الشبكة، اختبار الاتصال، وسحب سجلات الحضور مباشرة منها."
        actions={
          <HrWriteOnly>
            <Button onClick={openCreate}>
              <Plus className="size-4" />
              جهاز جديد
            </Button>
          </HrWriteOnly>
        }
      />

      <SectionCard className="mb-4">
        <div className="flex flex-wrap items-center gap-3">
          <SearchInput
            value={search}
            onChange={setSearch}
            placeholder="ابحث باسم الجهاز أو IP..."
            className="min-w-[16rem] flex-1"
          />
          <select
            className="h-10 w-full max-w-[12rem] cursor-pointer appearance-none rounded-xl border border-[var(--color-border)] bg-[var(--color-surface)] px-3.5 py-2 text-sm shadow-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--color-gold-400)] sm:w-auto"
            value={statusFilter}
            onChange={(e) => setStatusFilter(e.target.value)}
            aria-label="تصفية حسب الحالة"
          >
            <option value="">كل الحالات</option>
            {HR_DEVICE_STATUS_OPTIONS.map((o) => (
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

      {notice ? (
        <div
          role="status"
          className={`mb-4 flex items-center gap-2 rounded-xl border px-4 py-3 text-sm ${
            notice.tone === "success"
              ? "border-[var(--color-success-border)] bg-[var(--color-success-bg)] text-[var(--color-success)]"
              : "border-[var(--color-danger-border)] bg-[var(--color-danger-bg)] text-[var(--color-danger)]"
          }`}
        >
          {notice.tone === "success" ? (
            <Wifi className="size-4 shrink-0" />
          ) : (
            <WifiOff className="size-4 shrink-0" />
          )}
          <span>{notice.text}</span>
        </div>
      ) : null}

      {loading ? (
        <PageLoading label="جاري تحميل الأجهزة..." />
      ) : rows.length === 0 ? (
        <SectionCard>
          <EmptyState
            icon={Fingerprint}
            title={hasFilters ? "لا توجد نتائج" : "لا توجد أجهزة مسجلة"}
            description={
              hasFilters
                ? "جرّب تعديل البحث أو إزالة التصفية."
                : "أضف جهاز البصمة مع عنوان IP على الشبكة ليتم سحب سجلات الحضور منه."
            }
            action={
              hasFilters ? (
                <Button
                  variant="outline"
                  onClick={() => {
                    setSearch("");
                    setStatusFilter("");
                  }}
                >
                  مسح التصفية
                </Button>
              ) : (
                <HrWriteOnly>
                  <Button onClick={openCreate}>جهاز جديد</Button>
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
                    <TableHead className="text-right">الموقع</TableHead>
                    <TableHead className="text-right">العنوان</TableHead>
                    <TableHead className="text-right">البروتوكول</TableHead>
                    <TableHead className="text-right">آخر مزامنة</TableHead>
                    <TableHead className="text-right">الحالة</TableHead>
                    <TableHead className="text-right">إجراءات</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {rows.map((row) => (
                    <TableRow key={row.id}>
                      <TableCell className="font-semibold">{row.name}</TableCell>
                      <TableCell className="text-[var(--color-muted-foreground)]">
                        {row.location ?? "—"}
                      </TableCell>
                      <TableCell className="ltr-nums">
                        {row.ipAddress}:{row.port}
                      </TableCell>
                      <TableCell className="text-[var(--color-muted-foreground)]">
                        {HR_DEVICE_PROTOCOL_AR[row.protocol]}
                      </TableCell>
                      <TableCell className="text-[var(--color-muted-foreground)]">
                        {row.lastSyncAt ? (
                          <div>
                            <span>{formatDateTime(row.lastSyncAt)}</span>
                            {row.lastSyncMessage ? (
                              <span className="block text-xs">
                                {row.lastSyncMessage}
                              </span>
                            ) : null}
                          </div>
                        ) : (
                          "لم تتم المزامنة"
                        )}
                      </TableCell>
                      <TableCell>
                        <HrDeviceStatusBadge value={row.status} />
                      </TableCell>
                      <TableCell>
                        <HrWriteOnly>
                          <div className="flex items-center gap-1.5">
                            <Button
                              variant="ghost"
                              size="sm"
                              onClick={() => sync(row)}
                              disabled={busyId === row.id}
                              aria-label={`سجلات الحضور من ${row.name}`}
                              title="سحب سجلات الحضور من الجهاز"
                            >
                              <RefreshCw
                                className={`size-3.5 ${busyId === row.id ? "animate-spin" : ""}`}
                              />
                            </Button>
                            <Button
                              variant="ghost"
                              size="sm"
                              onClick={() => testConnection(row.id)}
                              disabled={busyId === row.id}
                              aria-label={`اختبار اتصال ${row.name}`}
                              title="اختبار الاتصال"
                            >
                              <Wifi className="size-3.5" />
                            </Button>
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
        icon={<Fingerprint className="size-5" />}
        title={editing ? `تعديل الجهاز: ${editing.name}` : "جهاز بصمة جديد"}
        description="البورت الافتراضي لأجهزة ZKTeco عبر الشبكة هو 4370."
        scrollable
      >
        <form
          id="device-form"
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

          {notice ? (
            <p
              className={`rounded-lg px-3 py-2 text-sm ${
                notice.tone === "success"
                  ? "bg-[var(--color-success-bg)] text-[var(--color-success)]"
                  : "bg-[var(--color-danger-bg)] text-[var(--color-danger)]"
              }`}
            >
              {notice.text}
            </p>
          ) : null}

          <HrField label="اسم الجهاز" htmlFor="dev-name" required>
            <Input
              id="dev-name"
              value={form.name}
              onChange={(e) =>
                setForm((f) => ({ ...f, name: e.target.value }))
              }
              placeholder="مثال: بوابة الاستقبال"
              maxLength={150}
            />
          </HrField>

          <HrField label="الموقع" htmlFor="dev-location">
            <Input
              id="dev-location"
              value={form.location}
              onChange={(e) =>
                setForm((f) => ({ ...f, location: e.target.value }))
              }
              placeholder="الدور الأول — المدخل"
              maxLength={250}
            />
          </HrField>

          <div className="grid gap-4 sm:grid-cols-2">
            <HrField label="عنوان IP" htmlFor="dev-ip" required>
              <Input
                id="dev-ip"
                value={form.ipAddress}
                onChange={(e) =>
                  setForm((f) => ({ ...f, ipAddress: e.target.value }))
                }
                placeholder="192.168.1.201"
                maxLength={64}
                className="ltr-nums"
              />
            </HrField>

            <HrField label="البورت" htmlFor="dev-port" hint="افتراضي 4370">
              <Input
                id="dev-port"
                type="number"
                min={1}
                max={65535}
                value={form.port}
                onChange={(e) =>
                  setForm((f) => ({ ...f, port: e.target.value }))
                }
                className="ltr-nums"
              />
            </HrField>
          </div>

          <HrWriteOnly>
            <Button
              type="button"
              variant="outline"
              onClick={() => testConnection()}
              disabled={
                busyId === "__form__" || !form.ipAddress.trim()
              }
            >
              <Wifi className="size-4" />
              {busyId === "__form__" ? "جارٍ الاختبار…" : "اختبار الاتصال"}
            </Button>
          </HrWriteOnly>

          <div className="grid gap-4 sm:grid-cols-2">
            <HrField label="البروتوكول" htmlFor="dev-protocol">
              <HrSelect
                id="dev-protocol"
                value={form.protocol}
                onChange={(v) => setForm((f) => ({ ...f, protocol: v }))}
                options={HR_DEVICE_PROTOCOL_OPTIONS}
              />
            </HrField>

            <HrField
              label="السيريال نمبر"
              htmlFor="dev-serial"
              hint="مطلوب لأجهزة الإرسال التلقائي"
            >
              <Input
                id="dev-serial"
                value={form.serialNumber}
                onChange={(e) =>
                  setForm((f) => ({ ...f, serialNumber: e.target.value }))
                }
                maxLength={64}
                className="ltr-nums"
              />
            </HrField>
          </div>

          <HrField label="الحالة" htmlFor="dev-status">
            <HrSelect
              id="dev-status"
              value={form.status}
              onChange={(v) => setForm((f) => ({ ...f, status: v }))}
              options={HR_DEVICE_STATUS_OPTIONS}
            />
          </HrField>

          <HrToggle
            id="dev-pullable"
            checked={form.protocol === "ZK_TECO_TCP"}
            onChange={(checked) =>
              setForm((f) => ({
                ...f,
                protocol: checked ? "ZK_TECO_TCP" : "MANUAL",
              }))
            }
            label="سحب مباشر من الجهاز عبر الشبكة"
          />

          <div className="mt-2 flex justify-end gap-2 border-t border-[var(--color-border)] pt-4">
            <Button
              type="submit"
              variant="gold"
              disabled={
                submitting || !form.name.trim() || !form.ipAddress.trim()
              }
            >
              {submitting
                ? "جارٍ الحفظ…"
                : editing
                  ? "حفظ التعديلات"
                  : "إضافة الجهاز"}
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