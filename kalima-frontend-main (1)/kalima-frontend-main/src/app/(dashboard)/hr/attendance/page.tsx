"use client";

import { Fragment, useCallback, useEffect, useState } from "react";

import {
  CalendarClock,
  CalendarDays,
  ChevronDown,
  Link2,
  Plus,
  UserX,
} from "lucide-react";

import { HrField, HrSelect } from "@/components/hr/HrField";
import { HrRoleGate, HrWriteOnly } from "@/components/hr/HrRoleGate";
import { HrDayStatusBadge } from "@/components/hr/HrStatusBadge";
import { ApiErrorAlert } from "@/components/shared/ApiErrorAlert";
import { Dialog } from "@/components/shared/Dialog";
import { EmptyState } from "@/components/shared/EmptyState";
import { PageHeader } from "@/components/shared/PageHeader";
import { PageLoading } from "@/components/shared/PageLoading";
import { SectionCard } from "@/components/shared/SectionCard";
import { Badge } from "@/components/ui/badge";
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
import { ApiRequestError } from "@/lib/api/client";
import { hrAttendanceApi, hrDepartmentsApi, hrDevicesApi } from "@/lib/api/hr";
import {
  HR_PUNCH_TYPE_OPTIONS,
  HR_VERIFY_MODE_OPTIONS,
  formatWorkedMinutes,
} from "@/lib/hr/labels";
import { formatTime } from "@/lib/utils/format-date";
import type {
  HrAttendanceDayRowDto,
  HrAttendanceDeviceDto,
  HrDepartmentDto,
  HrPunchTypeDto,
  HrVerifyModeDto,
} from "@/types/hr";

/** `datetime-local` wants `YYYY-MM-DDTHH:mm` in the browser's own timezone. */
function toLocalInputValue(iso: string): string {
  const d = new Date(iso);
  const pad = (n: number) => String(n).padStart(2, "0");
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

function todayValue(): string {
  return toLocalInputValue(new Date().toISOString());
}

type ManualForm = {
  deviceId: string;
  deviceUserId: string;
  punchedAt: string;
  punchType: string;
  verifyMode: string;
};

const EMPTY_MANUAL: ManualForm = {
  deviceId: "",
  deviceUserId: "",
  punchedAt: "",
  punchType: "CHECK_IN",
  verifyMode: "MANUAL",
};

export default function HrAttendancePage() {
  return (
    <HrRoleGate>
      <HrAttendancePageInner />
    </HrRoleGate>
  );
}

function HrAttendancePageInner() {
  const [date, setDate] = useState(todayValue().slice(0, 10));
  const [departmentFilter, setDepartmentFilter] = useState("");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [rows, setRows] = useState<HrAttendanceDayRowDto[]>([]);
  const [summary, setSummary] = useState({
    totalEmployees: 0,
    present: 0,
    absent: 0,
    late: 0,
    earlyLeave: 0,
    unmatchedPunches: 0,
  });
  const [departments, setDepartments] = useState<HrDepartmentDto[]>([]);
  const [devices, setDevices] = useState<HrAttendanceDeviceDto[]>([]);
  const [fetchId, setFetchId] = useState(0);

  const [expanded, setExpanded] = useState<string | null>(null);
  const [notice, setNotice] = useState<{
    tone: "success" | "danger";
    text: string;
  } | null>(null);

  const [manualOpen, setManualOpen] = useState(false);
  const [manual, setManual] = useState<ManualForm>(EMPTY_MANUAL);
  const [submitting, setSubmitting] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);

  const [rematching, setRematching] = useState(false);

  const refresh = useCallback(() => setFetchId((n) => n + 1), []);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const [depts, devs] = await Promise.all([
          hrDepartmentsApi.options(),
          hrDevicesApi.list({ page: 0, size: 100 }).then((r) => r.content),
        ]);
        if (!cancelled) {
          setDepartments(depts);
          setDevices(devs);
        }
      } catch {
        // Filters degrade to "all"; the sheet reports its own failure.
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
        const res = await hrAttendanceApi.daily({
          date,
          departmentId: departmentFilter || undefined,
        });
        if (!cancelled) {
          setRows(res.rows);
          setSummary({
            totalEmployees: res.totalEmployees,
            present: res.present,
            absent: res.absent,
            late: res.late,
            earlyLeave: res.earlyLeave,
            unmatchedPunches: res.unmatchedPunches,
          });
        }
      } catch (e) {
        if (!cancelled) {
          setError(
            e instanceof ApiRequestError ? e.message : "تعذّر تحميل سجل الحضور",
          );
          setRows([]);
        }
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [date, departmentFilter, fetchId]);

  /** Re-runs the punch-ID → employee match for punches pulled before the ID was registered. */
  async function rematch() {
    setRematching(true);
    setNotice(null);
    try {
      const res = await hrAttendanceApi.rematch();
      setNotice({
        tone: "success",
        text:
          res.linked > 0
            ? `تم ربط ${res.linked} حركة حضور بموظف.`
            : "لا توجد حركات غير مرتبطة.",
      });
      refresh();
    } catch (e) {
      setNotice({
        tone: "danger",
        text:
          e instanceof ApiRequestError ? e.message : "تعذّر إعادة الربط",
      });
    } finally {
      setRematching(false);
    }
  }

  async function submitManual() {
    if (!manual.deviceId || !manual.deviceUserId.trim() || !manual.punchedAt) {
      setFormError("الجهاز ومعرّف البصمة ووقت البصمة مطلوبة");
      return;
    }
    setSubmitting(true);
    setFormError(null);
    try {
      const res = await hrAttendanceApi.addManual({
        deviceId: manual.deviceId,
        deviceUserId: manual.deviceUserId.trim(),
        punchedAt: new Date(manual.punchedAt).toISOString(),
        punchType: manual.punchType as HrPunchTypeDto,
        verifyMode: manual.verifyMode as HrVerifyModeDto,
      });
      setManualOpen(false);
      setManual({ ...EMPTY_MANUAL, deviceId: manual.deviceId });
      setNotice({
        tone: "success",
        text: res.employeeName
          ? `تم تسجيل الحركة للموظف ${res.employeeName}.`
          : "تم تسجيل الحركة، لكنها غير مرتبطة بموظف بعد.",
      });
      refresh();
    } catch (e) {
      setFormError(
        e instanceof ApiRequestError ? e.message : "تعذّر تسجيل الحركة",
      );
    } finally {
      setSubmitting(false);
    }
  }

  const kpis = [
    { label: "إجمالي الموظفين", value: summary.totalEmployees },
    { label: "الحاضرون", value: summary.present },
    { label: "الغياب", value: summary.absent },
    { label: "التأخير", value: summary.late },
    { label: "انصراف مبكر", value: summary.earlyLeave },
  ];

  return (
    <div>
      <PageHeader
        title="الحضور والانصراف"
        description="سجل البصمة اليومي محسوب من الحركات المسحوبة من الأجهزة."
        actions={
          <div className="flex items-center gap-2">
            <HrWriteOnly>
              <Button
                variant="outline"
                onClick={rematch}
                disabled={rematching}
                title="إعادة ربط الحركات التي لا يوجد لها موظف"
              >
                <Link2 className="size-4" />
                {rematching ? "جارٍ الربط…" : "إعادة الربط"}
              </Button>
              <Button onClick={() => { setManual({ ...EMPTY_MANUAL }); setFormError(null); setManualOpen(true); }}>
                <Plus className="size-4" />
                حركة يدوية
              </Button>
            </HrWriteOnly>
          </div>
        }
      />

      <SectionCard className="mb-4">
        <div className="flex flex-wrap items-end gap-3">
          <HrField label="التاريخ" htmlFor="att-date" className="w-[11rem]">
            <Input
              id="att-date"
              type="date"
              value={date}
              onChange={(e) => setDate(e.target.value)}
            />
          </HrField>

          <HrField label="القسم" htmlFor="att-dept" className="w-[14rem]">
            <HrSelect
              id="att-dept"
              value={departmentFilter}
              onChange={setDepartmentFilter}
              options={departments.map((d) => ({ value: d.id, label: d.name }))}
              placeholder="كل الأقسام"
            />
          </HrField>

          {date !== todayValue().slice(0, 10) ? (
            <Button
              variant="ghost"
              onClick={() => setDate(todayValue().slice(0, 10))}
            >
              اليوم
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
          className={`mb-4 rounded-xl border px-4 py-3 text-sm ${
            notice.tone === "success"
              ? "border-[var(--color-success-border)] bg-[var(--color-success-bg)] text-[var(--color-success)]"
              : "border-[var(--color-danger-border)] bg-[var(--color-danger-bg)] text-[var(--color-danger)]"
          }`}
        >
          {notice.text}
        </div>
      ) : null}

      <div className="mb-4 grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-5">
        {kpis.map((kpi) => (
          <div
            key={kpi.label}
            className="rounded-2xl border border-[var(--color-border)] bg-[var(--color-surface)] px-4 py-3"
          >
            <p className="text-xs text-[var(--color-muted-foreground)]">
              {kpi.label}
            </p>
            <p className="mt-1 text-2xl font-bold tabular-nums ltr-nums text-[var(--color-foreground)]">
              {kpi.value.toLocaleString("ar-EG")}
            </p>
          </div>
        ))}
      </div>

      {summary.unmatchedPunches > 0 ? (
        <SectionCard className="mb-4">
          <div className="flex flex-wrap items-center gap-3 text-sm">
            <UserX className="size-4 shrink-0 text-[var(--color-warning)]" />
            <span className="text-[var(--color-foreground)]">
              يوجد{" "}
              <span className="font-bold tabular-nums ltr-nums">
                {summary.unmatchedPunches}
              </span>{" "}
              حركة بصمة غير مرتبطة بأي موظف.
            </span>
            <span className="text-[var(--color-muted-foreground)]">
              أضف معرّف البصمة المطابق في شاشة الموظفين، أو استخدم «إعادة
              الربط».
            </span>
          </div>
        </SectionCard>
      ) : null}

      {loading ? (
        <PageLoading label="جاري تحميل سجل الحضور..." />
      ) : rows.length === 0 ? (
        <SectionCard>
          <EmptyState
            icon={CalendarDays}
            title="لا توجد حركات في هذا اليوم"
            description="سجّل الحركات بسحبها من جهاز البصمة، أو أضف حركة يدوية."
            action={
              <HrWriteOnly>
                <Button onClick={() => setManualOpen(true)}>حركة يدوية</Button>
              </HrWriteOnly>
            }
          />
        </SectionCard>
      ) : (
        <SectionCard flush>
          <div className="overflow-x-auto">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead className="w-8" />
                  <TableHead className="text-right">الموظف</TableHead>
                  <TableHead className="text-right">القسم</TableHead>
                  <TableHead className="text-right">أول بصمة</TableHead>
                  <TableHead className="text-right">آخر بصمة</TableHead>
                  <TableHead className="text-right">ساعات العمل</TableHead>
                  <TableHead className="text-right">الحالة</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {rows.map((row) => {
                  const isOpen = expanded === row.employeeId;
                  return (
                    <Fragment key={row.employeeId}>
                      <TableRow
                        className="cursor-pointer"
                        onClick={() =>
                          setExpanded(isOpen ? null : row.employeeId)
                        }
                      >
                        <TableCell>
                          <ChevronDown
                            className={`size-4 text-[var(--color-muted-foreground)] transition-transform ${
                              isOpen ? "rotate-180" : ""
                            }`}
                          />
                        </TableCell>
                        <TableCell>
                          <span className="font-semibold">
                            {row.employeeName}
                          </span>
                          <span className="block text-xs text-[var(--color-muted-foreground)] ltr-nums">
                            {row.employeeNumber}
                          </span>
                        </TableCell>
                        <TableCell className="text-[var(--color-muted-foreground)]">
                          {row.departmentName ?? "—"}
                        </TableCell>
                        <TableCell className="ltr-nums tabular-nums">
                          {formatTime(row.firstPunch)}
                        </TableCell>
                        <TableCell className="ltr-nums tabular-nums">
                          {formatTime(row.lastPunch)}
                        </TableCell>
                        <TableCell className="tabular-nums">
                          {formatWorkedMinutes(row.workedMinutes)}
                        </TableCell>
                        <TableCell>
                          <HrDayStatusBadge value={row.status} />
                        </TableCell>
                      </TableRow>

                      {isOpen ? (
                        <TableRow>
                          <TableCell colSpan={7} className="bg-[var(--color-surface-muted)]/40">
                            <div className="flex flex-wrap gap-2 py-1">
                              {row.punches.map((p) => (
                                <Badge
                                  key={p.id}
                                  tone="neutral"
                                  className="gap-1.5"
                                >
                                  <span className="ltr-nums tabular-nums">
                                    {formatTime(p.punchedAt)}
                                  </span>
                                  <span>
                                    {
                                      HR_PUNCH_TYPE_OPTIONS.find(
                                        (o) => o.value === p.punchType,
                                      )?.label
                                    }
                                  </span>
                                  <span className="text-[var(--color-muted-foreground)]">
                                    {p.deviceName ?? "—"}
                                  </span>
                                </Badge>
                              ))}
                            </div>
                          </TableCell>
                        </TableRow>
                      ) : null}
                    </Fragment>
                  );
                })}
              </TableBody>
            </Table>
          </div>
        </SectionCard>
      )}

      <Dialog
        open={manualOpen}
        onClose={() => setManualOpen(false)}
        icon={<CalendarClock className="size-5" />}
        title="تسجيل حركة يدوية"
        description="للأجهزة التي لا يمكن الوصول إليها عبر الشبكة، أو لتصحيح حركة ناقصة."
      >
        <form
          id="manual-punch-form"
          className="space-y-4"
          noValidate
          onSubmit={(e) => {
            e.preventDefault();
            submitManual();
          }}
        >
          {formError ? (
            <p className="rounded-lg bg-[var(--color-danger-bg)] px-3 py-2 text-sm text-[var(--color-danger)]">
              {formError}
            </p>
          ) : null}

          <HrField label="الجهاز" htmlFor="mp-device" required>
            <HrSelect
              id="mp-device"
              value={manual.deviceId}
              onChange={(v) => setManual((f) => ({ ...f, deviceId: v }))}
              options={devices.map((d) => ({
                value: d.id,
                label: `${d.name} (${d.ipAddress})`,
              }))}
              placeholder="اختر الجهاز"
            />
          </HrField>

          <HrField
            label="معرّف البصمة"
            htmlFor="mp-punch"
            required
            hint="نفس المعرّف المسجل في شاشة الموظفين"
          >
            <Input
              id="mp-punch"
              value={manual.deviceUserId}
              onChange={(e) =>
                setManual((f) => ({ ...f, deviceUserId: e.target.value }))
              }
              maxLength={64}
              className="ltr-nums"
            />
          </HrField>

          <HrField label="وقت البصمة" htmlFor="mp-time" required>
            <Input
              id="mp-time"
              type="datetime-local"
              value={manual.punchedAt}
              onChange={(e) =>
                setManual((f) => ({ ...f, punchedAt: e.target.value }))
              }
            />
          </HrField>

          <div className="grid gap-4 sm:grid-cols-2">
            <HrField label="نوع الحركة" htmlFor="mp-type">
              <HrSelect
                id="mp-type"
                value={manual.punchType}
                onChange={(v) => setManual((f) => ({ ...f, punchType: v }))}
                options={HR_PUNCH_TYPE_OPTIONS}
              />
            </HrField>

            <HrField label="طريقة التحقق" htmlFor="mp-verify">
              <HrSelect
                id="mp-verify"
                value={manual.verifyMode}
                onChange={(v) => setManual((f) => ({ ...f, verifyMode: v }))}
                options={HR_VERIFY_MODE_OPTIONS}
              />
            </HrField>
          </div>

          <div className="flex justify-end gap-2 border-t border-[var(--color-border)] pt-4">
            <Button
              type="submit"
              variant="gold"
              disabled={
                submitting ||
                !manual.deviceId ||
                !manual.deviceUserId.trim() ||
                !manual.punchedAt
              }
            >
              {submitting ? "جارٍ الحفظ…" : "تسجيل الحركة"}
            </Button>
            <Button
              type="button"
              variant="ghost"
              onClick={() => setManualOpen(false)}
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