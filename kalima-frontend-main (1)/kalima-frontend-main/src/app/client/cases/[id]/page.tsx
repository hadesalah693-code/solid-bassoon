"use client";

import { useEffect, useMemo, useState } from "react";
import Link from "next/link";
import { useParams } from "next/navigation";
import { ArrowRight, Briefcase, CalendarClock, Clock3 } from "lucide-react";

import { CaseDocumentsPanel } from "@/components/cases/CaseDocumentsPanel";
import { CaseChatButton } from "@/components/cases/CaseMessagesPanel";
import { ClientCaseUpdates } from "@/components/cases/ClientCaseUpdates";
import { ApiErrorAlert } from "@/components/shared/ApiErrorAlert";
import { EmptyState } from "@/components/shared/EmptyState";
import { PageHeader } from "@/components/shared/PageHeader";
import { PageLoading } from "@/components/shared/PageLoading";
import { SectionCard } from "@/components/shared/SectionCard";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { ApiRequestError } from "@/lib/api/client";
import { casesApi } from "@/lib/api/cases";
import { hearingsApi } from "@/lib/api/hearings";
import { formatDate, formatDateTime } from "@/lib/utils/format-date";
import { caseFileFromDto } from "@/types/api";
import type { HearingResponseDto } from "@/types/api";
import type { CaseFile } from "@/types/case";
import { CaseStatusBadge } from "@/components/shared/StatusBadge";

const CASES_LIST = "/client/cases";

export default function ClientCaseDetailPage() {
  const params = useParams();
  const id = typeof params.id === "string" ? params.id : "";

  const [caseData, setCaseData] = useState<CaseFile | null>(null);
  const [hearings, setHearings] = useState<HearingResponseDto[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [loadVersion, setLoadVersion] = useState(0);

  useEffect(() => {
    if (!id) return;
    let cancelled = false;
    (async () => {
      setLoading(true);
      setError(null);
      try {
        const [c, h] = await Promise.all([
          casesApi.get(id),
          hearingsApi.listByCase(id, { page: 0, size: 100 }),
        ]);
        if (cancelled) return;
        setCaseData(caseFileFromDto(c));
        setHearings(h.content);
      } catch (e) {
        if (cancelled) return;
        setError(
          e instanceof ApiRequestError
            ? e.details?.message ?? e.message
            : "تعذر تحميل بيانات القضية",
        );
        setCaseData(null);
        setHearings([]);
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [id, loadVersion]);

  function refresh() {
    setLoadVersion((v) => v + 1);
  }

  const nextHearing = useMemo(() => {
    const now = Date.now();
    return (
      hearings
        .filter((h) => new Date(h.hearingDate).getTime() >= now)
        .sort(
          (a, b) =>
            new Date(a.hearingDate).getTime() -
            new Date(b.hearingDate).getTime(),
        )[0] ?? null
    );
  }, [hearings]);

  const lastHearing = useMemo(() => {
    const now = Date.now();
    return (
      hearings
        .filter((h) => new Date(h.hearingDate).getTime() < now)
        .sort(
          (a, b) =>
            new Date(b.hearingDate).getTime() -
            new Date(a.hearingDate).getTime(),
        )[0] ?? null
    );
  }, [hearings]);

  if (!id) return null;

  if (loading) {
    return (
      <PageLoading
        label="جاري تحميل متابعة القضية..."
        minHeightClassName="min-h-[40vh]"
        className="w-full"
      />
    );
  }

  if (error || !caseData) {
    return (
      <div className="space-y-4">
        <Button variant="ghost" size="sm" asChild>
          <Link href={CASES_LIST} className="inline-flex items-center gap-1">
            <ArrowRight className="size-4 rotate-180" />
            قضاياي
          </Link>
        </Button>
        {error ? (
          <ApiErrorAlert
            message={error}
            title="تعذر فتح متابعة القضية"
            onRetry={refresh}
          />
        ) : (
          <p className="text-sm text-[var(--color-muted-foreground)]">
            القضية غير متاحة لهذا الحساب.
          </p>
        )}
      </div>
    );
  }

  const subtitleParts = [
    `#${caseData.caseNumber}`,
    caseData.courtCaseNumber ? `رقم محكمة ${caseData.courtCaseNumber}` : null,
  ].filter(Boolean);

  return (
    <div className="space-y-8">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
        <div>
          <Button variant="ghost" size="sm" asChild className="mb-2 -ms-1">
            <Link
              href={CASES_LIST}
              className="inline-flex items-center gap-1 text-[var(--color-muted-foreground)]"
            >
              <ArrowRight className="size-4 rotate-180" />
              قضاياي
            </Link>
          </Button>
          <PageHeader
            title={caseData.title}
            description={subtitleParts.join(" · ")}
          />
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <CaseStatusBadge value={caseData.status} />
        </div>
      </div>

      <div className="grid gap-3 md:grid-cols-3">
        <CaseFollowCard
          icon={Briefcase}
          label="حالة القضية"
          value={<CaseStatusBadge value={caseData.status} />}
          tone="navy"
        />
        <CaseFollowCard
          icon={CalendarClock}
          label="الجلسة القادمة"
          value={
            nextHearing
              ? formatDateTime(nextHearing.hearingDate)
              : "لا توجد جلسة قادمة"
          }
          tone="gold"
        />
        <CaseFollowCard
          icon={Clock3}
          label="آخر جلسة"
          value={
            lastHearing
              ? formatDateTime(lastHearing.hearingDate)
              : "لم تسجل جلسات سابقة"
          }
          tone="muted"
        />
      </div>

      <SectionCard title="ملخص القضية" className="p-4">
        <dl className="grid gap-3 text-sm sm:grid-cols-2">
          <InfoItem label="رقم الملف" value={caseData.caseNumber} ltr />
          <InfoItem
            label="المحامي المسؤول"
            value={caseData.assignedLawyer?.fullName ?? "غير محدد"}
          />
          {caseData.courtName ? (
            <InfoItem label="المحكمة" value={caseData.courtName} />
          ) : null}
          {caseData.courtCircuit ? (
            <InfoItem label="الدائرة" value={caseData.courtCircuit} />
          ) : null}
          {caseData.procedureStage ? (
            <InfoItem label="مرحلة الإجراء" value={caseData.procedureStage} />
          ) : null}
          {caseData.caseType ? (
            <InfoItem label="نوع القضية" value={caseData.caseType} />
          ) : null}
          <InfoItem
            label="تاريخ البداية"
            value={caseData.startDate ? formatDate(caseData.startDate) : "غير محدد"}
            ltr
          />
          {caseData.description ? (
            <div className="sm:col-span-2">
              <dt className="text-[var(--color-muted-foreground)]">الوصف</dt>
              <dd className="text-[var(--color-foreground)]">
                {caseData.description}
              </dd>
            </div>
          ) : null}
        </dl>
      </SectionCard>

      <ClientCaseUpdates caseId={id} reloadKey={loadVersion} />

      <CaseChatButton caseId={id} disabled={Boolean(caseData.archivedAt)} />

      <CaseDocumentsPanel caseId={id} canUpload={false} />

      <section>
        <h2 className="mb-3 flex items-center gap-2 text-lg font-semibold">
          <CalendarClock className="size-5 text-[var(--color-navy-700)]" />
          جلسات القضية
        </h2>
        {hearings.length === 0 ? (
          <EmptyState
            title="لا توجد جلسات مسجلة"
            description="ستظهر هنا الجلسات عند جدولتها من قبل المكتب."
            icon={Briefcase}
          />
        ) : (
          <div className="flex flex-col gap-3">
            {hearings.map((h) => (
              <Card key={h.id} className="p-4">
                <div className="flex flex-col gap-2 sm:flex-row sm:items-start sm:justify-between">
                  <div>
                    <p className="text-xs text-[var(--color-muted-foreground)] ltr-nums">
                      {formatDateTime(h.hearingDate)}
                    </p>
                    <p className="mt-1 font-semibold text-[var(--color-foreground)]">
                      {h.courtName}
                    </p>
                    {h.chamber ? (
                      <p className="text-sm text-[var(--color-muted-foreground)]">
                        {h.chamber}
                      </p>
                    ) : null}
                  </div>
                </div>
                {h.result ? (
                  <p className="mt-2 text-sm text-[var(--color-foreground)]">
                    {h.result}
                  </p>
                ) : null}
                {h.notes ? (
                  <p className="mt-1 text-xs text-[var(--color-muted-foreground)]">
                    {h.notes}
                  </p>
                ) : null}
                {h.nextHearingDate ? (
                  <p className="mt-2 text-xs text-[var(--color-navy-700)] ltr-nums">
                    جلسة لاحقة: {formatDateTime(h.nextHearingDate)}
                  </p>
                ) : null}
              </Card>
            ))}
          </div>
        )}
      </section>
    </div>
  );
}

function InfoItem({
  label,
  value,
  ltr,
}: {
  label: string;
  value: React.ReactNode;
  ltr?: boolean;
}) {
  return (
    <div>
      <dt className="text-[var(--color-muted-foreground)]">{label}</dt>
      <dd className={ltr ? "font-medium ltr-nums" : "font-medium"}>{value}</dd>
    </div>
  );
}

function CaseFollowCard({
  icon: Icon,
  label,
  value,
  tone,
}: {
  icon: React.ComponentType<{ className?: string }>;
  label: string;
  value: React.ReactNode;
  tone: "navy" | "gold" | "muted";
}) {
  const toneClass =
    tone === "gold"
      ? "bg-[var(--color-gold-50)] text-[var(--color-gold-700)]"
      : tone === "navy"
        ? "bg-[var(--color-navy-50)] text-[var(--color-navy-700)]"
        : "bg-[var(--color-surface-muted)] text-[var(--color-muted-foreground)]";
  return (
    <Card className="p-4">
      <div className="flex items-start gap-3">
        <div className={`flex size-10 items-center justify-center rounded-xl ${toneClass}`}>
          <Icon className="size-5" />
        </div>
        <div className="min-w-0">
          <p className="text-xs text-[var(--color-muted-foreground)]">{label}</p>
          <div className="mt-1 text-sm font-medium ltr-nums">{value}</div>
        </div>
      </div>
    </Card>
  );
}
