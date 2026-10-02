"use client";

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { useParams } from "next/navigation";
import { ArrowRight, Plus, Save, ShieldAlert, Trash2, Workflow } from "lucide-react";

import { CriminalReportSectionNav } from "@/components/criminal-reports/CriminalReportSectionNav";
import { CriminalReportSummaryCards } from "@/components/criminal-reports/CriminalReportSummaryCards";
import { CriminalReportStatusPanel } from "@/components/criminal-reports/CriminalReportStatusPanel";
import type { CriminalReportStatusFormValues } from "@/components/criminal-reports/CriminalReportStatusPanel";
import { CriminalReportWorkflowStepper } from "@/components/criminal-reports/CriminalReportWorkflowStepper";
import { DisputeModuleHero } from "@/components/disputes/DisputeModuleHero";
import { ApiErrorAlert } from "@/components/shared/ApiErrorAlert";
import { PageLoading } from "@/components/shared/PageLoading";
import { SectionCard } from "@/components/shared/SectionCard";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { criminalReportsApi } from "@/lib/api/criminal-reports";
import { personsApi } from "@/lib/api/persons";
import { ApiRequestError } from "@/lib/api/client";
import { notifyApiError, notifySuccess } from "@/lib/feedback";
import {
  CRIMINAL_REPORT_LOCATION_TYPE_OPTIONS,
  CRIMINAL_REPORT_PARTY_CAPACITY_AR,
  CRIMINAL_REPORT_PARTY_CAPACITY_OPTIONS,
  CRIMINAL_REPORT_STATUS_AR,
  PROSECUTION_OFFICES,
  SECURITY_CENTERS,
} from "@/lib/criminal-reports/labels";
import { STAFF_ROLES, clientDisplayName } from "@/lib/delegations/staff";
import { cn } from "@/lib/utils/cn";
import type {
  CriminalReportInvestigationSessionDto,
  CriminalReportLocationDto,
  CriminalReportPartyCapacityDto,
  CriminalReportResponseDto,
} from "@/types/criminal-report";
import type { ClientPerson } from "@/types/person";

const selectClassName = cn(
  "h-10 w-full cursor-pointer appearance-none rounded-xl border border-[var(--color-border)] bg-[var(--color-surface)] px-3 text-sm",
  "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--color-gold-400)]",
);

type ClientRow = { clientId: string; capacity: CriminalReportPartyCapacityDto | "" };
type OpponentRow = { name: string; capacity: CriminalReportPartyCapacityDto | "" };
type LocationRow = { locationType: string; locationDescription: string };

export default function CriminalReportDetailPage() {
  const params = useParams();
  const id = params.id as string;

  const [report, setReport] = useState<CriminalReportResponseDto | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [statusPanelOpen, setStatusPanelOpen] = useState(false);
  const [statusSubmitting, setStatusSubmitting] = useState(false);

  const [registrationDate, setRegistrationDate] = useState("");
  const [reportNumber, setReportNumber] = useState("");
  const [incidentDate, setIncidentDate] = useState("");
  const [prosecutionOffice, setProsecutionOffice] = useState("");
  const [securityCenter, setSecurityCenter] = useState("");
  const [charge, setCharge] = useState("");
  const [subject, setSubject] = useState("");
  const [summary, setSummary] = useState("");
  const [legalRequest, setLegalRequest] = useState("");
  const [clientRows, setClientRows] = useState<ClientRow[]>([{ clientId: "", capacity: "" }]);
  const [opponentRows, setOpponentRows] = useState<OpponentRow[]>([{ name: "", capacity: "" }]);
  const [locationRows, setLocationRows] = useState<LocationRow[]>([{ locationType: "", locationDescription: "" }]);
  const [clients, setClients] = useState<ClientPerson[]>([]);
  const [lawyers, setLawyers] = useState<ClientPerson[]>([]);
  const [assignedLawyerId, setAssignedLawyerId] = useState("");
  const [internalSource, setInternalSource] = useState("");
  const [legalConsultant, setLegalConsultant] = useState("");
  const [externalConsultant, setExternalConsultant] = useState("");
  const [partnerNotes, setPartnerNotes] = useState("");
  const [sessions, setSessions] = useState<CriminalReportInvestigationSessionDto[]>([]);

  const readOnly = report?.status === "FINAL_ARCHIVED";

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await criminalReportsApi.get(id);
      setReport(data);
      setRegistrationDate(data.registrationDate);
      setReportNumber(data.reportNumber);
      setIncidentDate(data.incidentDate ?? "");
      setProsecutionOffice(data.prosecutionOffice ?? "");
      setSecurityCenter(data.securityCenter ?? "");
      setCharge(data.charge ?? "");
      setSubject(data.subject ?? "");
      setSummary(data.summary ?? "");
      setLegalRequest(data.legalRequest ?? "");
      setAssignedLawyerId(data.assignedLawyer?.id ?? "");
      const loadedClients =
        data.clients?.length > 0
          ? data.clients.map((c) => ({
              clientId: c.client.id,
              capacity: (c.capacity ?? "") as CriminalReportPartyCapacityDto | "",
            }))
          : data.client
            ? [{ clientId: data.client.id, capacity: "" as const }]
            : [{ clientId: "", capacity: "" as const }];
      setClientRows(loadedClients);
      const loadedOpponents =
        data.opponents?.length > 0
          ? data.opponents.map((o) => ({
              name: o.name,
              capacity: (o.capacity ?? "") as CriminalReportPartyCapacityDto | "",
            }))
          : data.opponentName
            ? [{ name: data.opponentName, capacity: (data.opponentCapacity ?? "") as CriminalReportPartyCapacityDto | "" }]
            : [{ name: "", capacity: "" as const }];
      setOpponentRows(loadedOpponents);
      setLocationRows(
        data.locations?.length > 0
          ? data.locations.map((l) => ({
              locationType: l.locationType ?? "",
              locationDescription: l.locationDescription ?? "",
            }))
          : [{ locationType: "", locationDescription: "" }],
      );
      setInternalSource(data.internalSource ?? "");
      setLegalConsultant(data.legalConsultant ?? "");
      setExternalConsultant(data.externalConsultant ?? "");
      setPartnerNotes(data.partnerNotes ?? "");
      setSessions(data.investigationSessions ?? []);
    } catch (e) {
      setError(e instanceof ApiRequestError ? e.message : "تعذّر تحميل البلاغ");
    } finally {
      setLoading(false);
    }
  }, [id]);

  useEffect(() => {
    void load();
  }, [load]);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const [clientsRes, ...staffPages] = await Promise.all([
          personsApi.list({ role: "CLIENT", size: 500 }),
          ...STAFF_ROLES.map((r) => personsApi.list({ role: r, size: 200 })),
        ]);
        if (!cancelled) {
          setClients(clientsRes.content);
          const merged = staffPages.flatMap((p) => p.content);
          const seen = new Set<string>();
          setLawyers(merged.filter((p) => !seen.has(p.id) && seen.add(p.id)));
        }
      } catch {
        /* optional preload */
      }
    })();
    return () => {
      cancelled = true;
    };
  }, []);

  async function handleSave() {
    if (!report || readOnly) return;
    setSaving(true);
    try {
      const selectedClients = clientRows.map((r) => r.clientId).filter(Boolean);
      if (selectedClients.length === 0) {
        notifyApiError(new Error("client required"), "اختر موكلًا واحدًا على الأقل");
        setSaving(false);
        return;
      }
      const opponents = opponentRows
        .filter((r) => r.name.trim())
        .map((r) => ({
          name: r.name.trim(),
          capacity: r.capacity || null,
        }));
      const locations: CriminalReportLocationDto[] = locationRows
        .filter((r) => r.locationType || r.locationDescription.trim())
        .map((r) => ({
          locationType: r.locationType || null,
          locationDescription: r.locationDescription.trim() || null,
        }));
      const updated = await criminalReportsApi.update(id, {
        registrationDate,
        reportNumber: reportNumber.trim() || null,
        incidentDate: incidentDate || null,
        prosecutionOffice: prosecutionOffice || null,
        securityCenter: securityCenter || null,
        charge: charge || null,
        subject: subject || null,
        summary: summary || null,
        legalRequest: legalRequest || null,
        clients: clientRows
          .filter((r) => r.clientId)
          .map((r) => ({ clientId: r.clientId, capacity: r.capacity || null })),
        opponents,
        assignedLawyerId: assignedLawyerId || null,
        locations,
        internalSource: internalSource || null,
        legalConsultant: legalConsultant || null,
        externalConsultant: externalConsultant || null,
        partnerNotes: partnerNotes || null,
        investigationSessions: sessions,
      });
      setReport(updated);
      setReportNumber(updated.reportNumber);
      setAssignedLawyerId(updated.assignedLawyer?.id ?? "");
      setClientRows(
        updated.clients?.length > 0
          ? updated.clients.map((c) => ({
              clientId: c.client.id,
              capacity: (c.capacity ?? "") as CriminalReportPartyCapacityDto | "",
            }))
          : updated.client
            ? [{ clientId: updated.client.id, capacity: "" as const }]
            : [{ clientId: "", capacity: "" as const }],
      );
      setOpponentRows(
        updated.opponents?.length > 0
          ? updated.opponents.map((o) => ({
              name: o.name,
              capacity: (o.capacity ?? "") as CriminalReportPartyCapacityDto | "",
            }))
          : [{ name: "", capacity: "" }],
      );
      setLocationRows(
        updated.locations?.length > 0
          ? updated.locations.map((l) => ({
              locationType: l.locationType ?? "",
              locationDescription: l.locationDescription ?? "",
            }))
          : [{ locationType: "", locationDescription: "" }],
      );
      notifySuccess("تم حفظ البلاغ");
    } catch (e) {
      notifyApiError(e, "تعذّر حفظ البلاغ");
    } finally {
      setSaving(false);
    }
  }

  async function handleStatusConfirm(values: CriminalReportStatusFormValues) {
    setStatusSubmitting(true);
    try {
      const updated = await criminalReportsApi.updateStatus(id, {
        status: values.status,
        archiveReason: values.archiveReason || null,
        grievancePeriodDays: values.grievancePeriodDays || null,
        grievanceDeadline: values.grievanceDeadline || null,
        finalArchiveReason: values.finalArchiveReason || null,
        decisionIssuer: values.decisionIssuer || null,
        decisionNumber: values.decisionNumber || null,
        decisionReceivedDate: values.decisionReceivedDate || null,
        finalDecisionBasis: values.finalDecisionBasis || null,
        appealPeriodDays: values.appealPeriodDays || null,
        finalDecisionLevel: values.finalDecisionLevel || null,
        linkedCaseId: values.linkedCaseId || null,
      });
      setReport(updated);
      setStatusPanelOpen(false);
      notifySuccess("تم تحديث حالة البلاغ");
    } catch (e) {
      notifyApiError(e, "تعذّر تحديث الحالة");
    } finally {
      setStatusSubmitting(false);
    }
  }

  if (loading) return <PageLoading label="جاري تحميل البلاغ..." />;
  if (error || !report) return <ApiErrorAlert message={error ?? "البلاغ غير موجود"} />;

  return (
    <div dir="rtl" className="space-y-6 pb-24 lg:pb-6">
      <DisputeModuleHero
        icon={ShieldAlert}
        eyebrow="البلاغات الجنائية"
        title={report.reportNumber}
        description={
          <div className="space-y-1">
            {report.charge?.trim() ? <p>{report.charge}</p> : null}
            <p className="text-white/65">
              {[
                `سنة ${report.reportYear}`,
                report.prosecutionOffice?.trim(),
                report.securityCenter?.trim(),
              ]
                .filter(Boolean)
                .join(" · ")}
            </p>
          </div>
        }
        badges={
          <span className="inline-flex items-center rounded-full bg-white/10 px-3 py-1 text-xs font-semibold text-white ring-1 ring-white/20">
            {CRIMINAL_REPORT_STATUS_AR[report.status]}
          </span>
        }
        actions={
          <>
            <Button
              variant="outline"
              asChild
              className="border-white/25 bg-white/5 text-white hover:bg-white/15 hover:text-white"
            >
              <Link href="/criminal-reports">
                <ArrowRight className="size-4" />
                القائمة
              </Link>
            </Button>
            <Button
              variant="outline"
              onClick={() => setStatusPanelOpen(true)}
              className="border-white/25 bg-white/5 text-white hover:bg-white/15 hover:text-white"
            >
              <Workflow className="size-4" />
              حالة البلاغ
            </Button>
            {!readOnly ? (
              <Button
                onClick={handleSave}
                disabled={saving}
                className="hidden border-white/25 bg-[var(--color-gold-400)] text-[var(--color-navy-950)] hover:bg-[var(--color-gold-300)] sm:inline-flex"
              >
                <Save className="size-4" />
                {saving ? "جاري الحفظ..." : "حفظ التعديلات"}
              </Button>
            ) : null}
          </>
        }
      />

      <CriminalReportSummaryCards report={report} />

      <CriminalReportWorkflowStepper status={report.status} />

      <CriminalReportSectionNav />

      <div className="grid gap-6 lg:grid-cols-[1fr_320px]">
        <div className="space-y-6">
          <div id="cr-details" className="scroll-mt-28">
            <SectionCard title="تفاصيل البلاغ" description="البيانات الإجرائية والجهات المختصة">
              <div className="grid gap-4 md:grid-cols-2">
                <div className="space-y-2">
                  <Label htmlFor="reportNumber">رقم البلاغ</Label>
                  <Input
                    id="reportNumber"
                    value={reportNumber}
                    onChange={(e) => setReportNumber(e.target.value)}
                    disabled={readOnly}
                    placeholder="مثال: 0001/2026"
                  />
                </div>
                <div className="space-y-2">
                  <Label>سنة البلاغ</Label>
                  <Input value={String(report.reportYear)} readOnly disabled />
                </div>
                <div className="space-y-2">
                  <Label htmlFor="registrationDate">تاريخ التسجيل</Label>
                  <Input
                    id="registrationDate"
                    type="date"
                    value={registrationDate}
                    onChange={(e) => setRegistrationDate(e.target.value)}
                    disabled={readOnly}
                  />
                </div>
                <div className="space-y-2">
                  <Label htmlFor="incidentDate">تاريخ الواقعة</Label>
                  <Input
                    id="incidentDate"
                    type="date"
                    value={incidentDate}
                    onChange={(e) => setIncidentDate(e.target.value)}
                    disabled={readOnly}
                  />
                </div>
                <div className="space-y-2">
                  <Label htmlFor="prosecutionOffice">النيابة / الادعاء</Label>
                  <select
                    id="prosecutionOffice"
                    className={selectClassName}
                    value={prosecutionOffice}
                    onChange={(e) => setProsecutionOffice(e.target.value)}
                    disabled={readOnly}
                  >
                    <option value="">—</option>
                    {PROSECUTION_OFFICES.map((o) => (
                      <option key={o} value={o}>
                        {o}
                      </option>
                    ))}
                  </select>
                </div>
                <div className="space-y-2">
                  <Label htmlFor="securityCenter">المركز الأمني</Label>
                  <select
                    id="securityCenter"
                    className={selectClassName}
                    value={securityCenter}
                    onChange={(e) => setSecurityCenter(e.target.value)}
                    disabled={readOnly}
                  >
                    <option value="">—</option>
                    {SECURITY_CENTERS.map((o) => (
                      <option key={o} value={o}>
                        {o}
                      </option>
                    ))}
                  </select>
                </div>
                <div className="space-y-2 md:col-span-2">
                  <Label htmlFor="charge">التهمة</Label>
                  <Input id="charge" value={charge} onChange={(e) => setCharge(e.target.value)} disabled={readOnly} />
                </div>
                <div className="space-y-2 md:col-span-2">
                  <Label htmlFor="subject">موضوع البلاغ</Label>
                  <Textarea
                    id="subject"
                    rows={3}
                    value={subject}
                    onChange={(e) => setSubject(e.target.value)}
                    disabled={readOnly}
                  />
                </div>
              </div>
            </SectionCard>
          </div>

          <div id="cr-content" className="scroll-mt-28">
            <SectionCard title="المحتوى القانوني" description="ملخص البلاغ والطلب المقدّم">
              <div className="grid gap-4">
                <div className="space-y-2">
                  <Label htmlFor="summary">ملخص البلاغ</Label>
                  <Textarea
                    id="summary"
                    rows={4}
                    value={summary}
                    onChange={(e) => setSummary(e.target.value)}
                    disabled={readOnly}
                    placeholder="ملخص موجز لوقائع البلاغ..."
                  />
                </div>
                <div className="space-y-2">
                  <Label htmlFor="legalRequest">الطلب القانوني</Label>
                  <Textarea
                    id="legalRequest"
                    rows={4}
                    value={legalRequest}
                    onChange={(e) => setLegalRequest(e.target.value)}
                    disabled={readOnly}
                    placeholder="الطلب القانوني المقدّم..."
                  />
                </div>
              </div>
            </SectionCard>
          </div>

          <div id="cr-parties" className="scroll-mt-28">
            <SectionCard title="الموكلون والخصوم">
              <div className="space-y-8">
                <div>
                  <div className="mb-3 flex items-center justify-between gap-2">
                    <p className="text-sm font-semibold text-[var(--color-foreground)]">الموكلون</p>
                    {!readOnly ? (
                      <Button
                        type="button"
                        variant="outline"
                        size="sm"
                        onClick={() => setClientRows((rows) => [...rows, { clientId: "", capacity: "" }])}
                      >
                        <Plus className="size-4" />
                        إضافة موكل
                      </Button>
                    ) : null}
                  </div>
                  <div className="space-y-2">
                    {clientRows.map((row, idx) => (
                      <div
                        key={idx}
                        className="grid gap-2 rounded-xl border border-[var(--color-border)] bg-[var(--color-surface-muted)]/30 p-3 md:grid-cols-[1fr_180px_auto]"
                      >
                        {readOnly ? (
                          <div className="md:col-span-2 text-sm">
                            <p className="font-medium">
                              {clients.find((c) => c.id === row.clientId)
                                ? clientDisplayName(clients.find((c) => c.id === row.clientId)!)
                                : report.clients?.[idx]?.client.fullName ?? report.client?.fullName ?? "—"}
                            </p>
                            {row.capacity ? (
                              <p className="mt-1 text-xs text-[var(--color-muted-foreground)]">
                                {CRIMINAL_REPORT_PARTY_CAPACITY_AR[row.capacity]}
                              </p>
                            ) : null}
                          </div>
                        ) : (
                          <>
                            <select
                              className={selectClassName}
                              value={row.clientId}
                              onChange={(e) => {
                                const next = [...clientRows];
                                next[idx] = { ...next[idx], clientId: e.target.value };
                                setClientRows(next);
                              }}
                            >
                              <option value="">اختر موكلًا</option>
                              {clients.map((c) => (
                                <option key={c.id} value={c.id}>
                                  {clientDisplayName(c)}
                                </option>
                              ))}
                            </select>
                            <select
                              className={selectClassName}
                              value={row.capacity}
                              onChange={(e) => {
                                const next = [...clientRows];
                                next[idx] = {
                                  ...next[idx],
                                  capacity: e.target.value as CriminalReportPartyCapacityDto | "",
                                };
                                setClientRows(next);
                              }}
                            >
                              <option value="">الصفة</option>
                              {CRIMINAL_REPORT_PARTY_CAPACITY_OPTIONS.map((o) => (
                                <option key={o.value} value={o.value}>
                                  {o.label}
                                </option>
                              ))}
                            </select>
                          </>
                        )}
                        {!readOnly && clientRows.length > 1 ? (
                          <Button
                            type="button"
                            variant="ghost"
                            size="icon"
                            aria-label="حذف الموكل"
                            onClick={() => setClientRows((rows) => rows.filter((_, i) => i !== idx))}
                          >
                            <Trash2 className="size-4" />
                          </Button>
                        ) : (
                          <span className="hidden md:block" />
                        )}
                      </div>
                    ))}
                  </div>
                  <div className="mt-4 space-y-2">
                    <Label htmlFor="assignedLawyerId">المحامي المكلف</Label>
                    {readOnly ? (
                      <p className="text-sm">{report.assignedLawyer?.fullName ?? "—"}</p>
                    ) : (
                      <select
                        id="assignedLawyerId"
                        className={selectClassName}
                        value={assignedLawyerId}
                        onChange={(e) => setAssignedLawyerId(e.target.value)}
                      >
                        <option value="">—</option>
                        {lawyers.map((l) => (
                          <option key={l.id} value={l.id}>
                            {clientDisplayName(l)}
                          </option>
                        ))}
                      </select>
                    )}
                  </div>
                </div>

                <div className="border-t border-[var(--color-border)] pt-6">
                  <div className="mb-3 flex items-center justify-between gap-2">
                    <p className="text-sm font-semibold text-[var(--color-foreground)]">الخصوم</p>
                    {!readOnly ? (
                      <Button
                        type="button"
                        variant="outline"
                        size="sm"
                        onClick={() => setOpponentRows((rows) => [...rows, { name: "", capacity: "" }])}
                      >
                        <Plus className="size-4" />
                        إضافة خصم
                      </Button>
                    ) : null}
                  </div>
                  <div className="space-y-2">
                    {opponentRows.map((row, idx) => (
                      <div
                        key={idx}
                        className="grid gap-2 rounded-xl border border-[var(--color-border)] bg-[var(--color-surface-muted)]/30 p-3 md:grid-cols-[1fr_180px_auto]"
                      >
                        <Input
                          placeholder="اسم الخصم"
                          value={row.name}
                          onChange={(e) => {
                            const next = [...opponentRows];
                            next[idx] = { ...next[idx], name: e.target.value };
                            setOpponentRows(next);
                          }}
                          disabled={readOnly}
                        />
                        <select
                          className={selectClassName}
                          value={row.capacity}
                          onChange={(e) => {
                            const next = [...opponentRows];
                            next[idx] = {
                              ...next[idx],
                              capacity: e.target.value as CriminalReportPartyCapacityDto | "",
                            };
                            setOpponentRows(next);
                          }}
                          disabled={readOnly}
                        >
                          <option value="">الصفة</option>
                          {CRIMINAL_REPORT_PARTY_CAPACITY_OPTIONS.map((o) => (
                            <option key={o.value} value={o.value}>
                              {o.label}
                            </option>
                          ))}
                        </select>
                        {!readOnly && opponentRows.length > 1 ? (
                          <Button
                            type="button"
                            variant="ghost"
                            size="icon"
                            aria-label="حذف الخصم"
                            onClick={() => setOpponentRows((rows) => rows.filter((_, i) => i !== idx))}
                          >
                            <Trash2 className="size-4" />
                          </Button>
                        ) : (
                          <span className="hidden md:block" />
                        )}
                      </div>
                    ))}
                  </div>
                </div>
              </div>
            </SectionCard>
          </div>

          <div id="cr-locations" className="scroll-mt-28">
            <SectionCard
              title="مواقع الواقعة"
              description="أماكن وقوع الحادث أو الجريمة"
              action={
                !readOnly ? (
                  <Button
                    type="button"
                    variant="outline"
                    size="sm"
                    onClick={() =>
                      setLocationRows((rows) => [...rows, { locationType: "", locationDescription: "" }])
                    }
                  >
                    <Plus className="size-4" />
                    إضافة موقع
                  </Button>
                ) : null
              }
            >
              {locationRows.filter((r) => r.locationType || r.locationDescription.trim()).length === 0 &&
              readOnly ? (
                <p className="text-sm text-[var(--color-muted-foreground)]">لا توجد مواقع مسجّلة.</p>
              ) : (
                <div className="space-y-2">
                  {locationRows.map((row, idx) => (
                    <div
                      key={idx}
                      className="grid gap-2 rounded-xl border border-[var(--color-border)] bg-[var(--color-surface-muted)]/30 p-3 md:grid-cols-[1fr_1fr_auto]"
                    >
                      {readOnly ? (
                        <div className="md:col-span-2 text-sm">
                          <p className="font-medium">
                            {(CRIMINAL_REPORT_LOCATION_TYPE_OPTIONS.find((o) => o.value === row.locationType)
                              ?.label ?? row.locationType) || "—"}
                          </p>
                          {row.locationDescription ? (
                            <p className="mt-1 text-[var(--color-muted-foreground)]">{row.locationDescription}</p>
                          ) : null}
                        </div>
                      ) : (
                        <>
                          <select
                            className={selectClassName}
                            value={row.locationType}
                            onChange={(e) => {
                              const next = [...locationRows];
                              next[idx] = { ...next[idx], locationType: e.target.value };
                              setLocationRows(next);
                            }}
                          >
                            <option value="">نوع الموقع</option>
                            {CRIMINAL_REPORT_LOCATION_TYPE_OPTIONS.map((o) => (
                              <option key={o.value} value={o.value}>
                                {o.label}
                              </option>
                            ))}
                          </select>
                          <Input
                            placeholder="وصف الموقع"
                            value={row.locationDescription}
                            onChange={(e) => {
                              const next = [...locationRows];
                              next[idx] = { ...next[idx], locationDescription: e.target.value };
                              setLocationRows(next);
                            }}
                          />
                        </>
                      )}
                      {!readOnly && locationRows.length > 1 ? (
                        <Button
                          type="button"
                          variant="ghost"
                          size="icon"
                          aria-label="حذف الموقع"
                          onClick={() => setLocationRows((rows) => rows.filter((_, i) => i !== idx))}
                        >
                          <Trash2 className="size-4" />
                        </Button>
                      ) : (
                        <span className="hidden md:block" />
                      )}
                    </div>
                  ))}
                </div>
              )}
            </SectionCard>
          </div>

          <div id="cr-sessions" className="scroll-mt-28">
            <SectionCard
              title="جلسات التحقيق"
              description={`${sessions.length} جلسة مسجّلة`}
              action={
                !readOnly ? (
                  <Button
                    type="button"
                    variant="outline"
                    size="sm"
                    onClick={() =>
                      setSessions((prev) => [
                        ...prev,
                        { sessionDate: new Date().toISOString().slice(0, 16), location: "", notes: "" },
                      ])
                    }
                  >
                    <Plus className="size-4" />
                    إضافة جلسة
                  </Button>
                ) : null
              }
            >
              {sessions.length === 0 ? (
                <div className="rounded-xl border border-dashed border-[var(--color-border)] bg-[var(--color-surface-muted)]/20 px-4 py-10 text-center">
                  <p className="text-sm text-[var(--color-muted-foreground)]">لا توجد جلسات تحقيق مسجّلة بعد.</p>
                  {!readOnly ? (
                    <Button
                      type="button"
                      variant="outline"
                      size="sm"
                      className="mt-4"
                      onClick={() =>
                        setSessions([
                          { sessionDate: new Date().toISOString().slice(0, 16), location: "", notes: "" },
                        ])
                      }
                    >
                      <Plus className="size-4" />
                      إضافة أول جلسة
                    </Button>
                  ) : null}
                </div>
              ) : (
                <div className="space-y-3">
                  {sessions.map((s, idx) => (
                    <div
                      key={s.id ?? idx}
                      className="grid gap-3 rounded-xl border border-[var(--color-border)] bg-[var(--color-surface-muted)]/30 p-3 md:grid-cols-3"
                    >
                      <div className="space-y-1">
                        <Label className="text-xs text-[var(--color-muted-foreground)]">التاريخ والوقت</Label>
                        <Input
                          type="datetime-local"
                          value={s.sessionDate?.slice(0, 16) ?? ""}
                          onChange={(e) => {
                            const next = [...sessions];
                            next[idx] = { ...next[idx], sessionDate: e.target.value };
                            setSessions(next);
                          }}
                          disabled={readOnly}
                        />
                      </div>
                      <div className="space-y-1">
                        <Label className="text-xs text-[var(--color-muted-foreground)]">المكان</Label>
                        <Input
                          placeholder="مكان الجلسة"
                          value={s.location ?? ""}
                          onChange={(e) => {
                            const next = [...sessions];
                            next[idx] = { ...next[idx], location: e.target.value };
                            setSessions(next);
                          }}
                          disabled={readOnly}
                        />
                      </div>
                      <div className="space-y-1">
                        <Label className="text-xs text-[var(--color-muted-foreground)]">ملاحظات</Label>
                        <Input
                          placeholder="ملاحظات الجلسة"
                          value={s.notes ?? ""}
                          onChange={(e) => {
                            const next = [...sessions];
                            next[idx] = { ...next[idx], notes: e.target.value };
                            setSessions(next);
                          }}
                          disabled={readOnly}
                        />
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </SectionCard>
          </div>
        </div>

        <aside className="space-y-4">
          {report.archiveReason ? (
            <SectionCard title="سبب الحفظ">
              <p className="text-sm">{report.archiveReason}</p>
              {report.grievanceDeadline ? (
                <p className="mt-2 text-xs text-[var(--color-muted-foreground)]">
                  آخر موعد للتظلم: {report.grievanceDeadline}
                </p>
              ) : null}
            </SectionCard>
          ) : null}

          {report.finalArchiveReason ? (
            <SectionCard title="الحفظ النهائي">
              <div className="space-y-2 text-sm">
                <p>{report.finalArchiveReason}</p>
                {report.decisionIssuer ? <p className="text-[var(--color-muted-foreground)]">الجهة: {report.decisionIssuer}</p> : null}
                {report.decisionNumber ? <p className="text-[var(--color-muted-foreground)]">رقم القرار: {report.decisionNumber}</p> : null}
                {report.decisionReceivedDate ? (
                  <p className="text-[var(--color-muted-foreground)]">تاريخ الاستلام: {report.decisionReceivedDate}</p>
                ) : null}
              </div>
            </SectionCard>
          ) : null}

          {report.linkedCaseNumber ? (
            <SectionCard title="القضية المرتبطة">
              <Link href={`/cases/${report.linkedCaseId}`} className="text-sm font-medium text-[var(--color-gold-400)] hover:underline">
                {report.linkedCaseNumber}
              </Link>
            </SectionCard>
          ) : null}

          <SectionCard title="معلومات إضافية">
            <div className="space-y-4">
              <div className="space-y-2">
                <Label htmlFor="internalSource">المصدر الداخلي</Label>
                <Input
                  id="internalSource"
                  value={internalSource}
                  onChange={(e) => setInternalSource(e.target.value)}
                  disabled={readOnly}
                />
              </div>
              <div className="space-y-2">
                <Label htmlFor="legalConsultant">المستشار القانوني</Label>
                <Input
                  id="legalConsultant"
                  value={legalConsultant}
                  onChange={(e) => setLegalConsultant(e.target.value)}
                  disabled={readOnly}
                />
              </div>
              <div className="space-y-2">
                <Label htmlFor="externalConsultant">مستشار خارجي</Label>
                <Input
                  id="externalConsultant"
                  value={externalConsultant}
                  onChange={(e) => setExternalConsultant(e.target.value)}
                  disabled={readOnly}
                />
              </div>
              <div className="space-y-2">
                <Label htmlFor="partnerNotes">ملاحظات الشركاء</Label>
                <Textarea
                  id="partnerNotes"
                  rows={3}
                  value={partnerNotes}
                  onChange={(e) => setPartnerNotes(e.target.value)}
                  disabled={readOnly}
                />
              </div>
            </div>
          </SectionCard>
        </aside>
      </div>

      {!readOnly ? (
        <div className="fixed inset-x-0 bottom-0 z-30 border-t border-[var(--color-border)] bg-[var(--color-surface)]/95 p-3 shadow-[var(--shadow-pop)] backdrop-blur sm:hidden">
          <Button className="w-full" onClick={handleSave} disabled={saving}>
            <Save className="size-4" />
            {saving ? "جاري الحفظ..." : "حفظ التعديلات"}
          </Button>
        </div>
      ) : null}

      <CriminalReportStatusPanel
        open={statusPanelOpen}
        currentStatus={report.status}
        onClose={() => setStatusPanelOpen(false)}
        onConfirm={handleStatusConfirm}
        submitting={statusSubmitting}
      />
    </div>
  );
}
