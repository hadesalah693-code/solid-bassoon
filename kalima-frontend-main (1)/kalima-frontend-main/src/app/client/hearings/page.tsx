"use client";

import { useEffect, useMemo, useState } from "react";
import Link from "next/link";
import { CalendarClock, FileText } from "lucide-react";

import { ApiErrorAlert } from "@/components/shared/ApiErrorAlert";
import { EmptyState } from "@/components/shared/EmptyState";
import { PageHeader } from "@/components/shared/PageHeader";
import { PageLoading } from "@/components/shared/PageLoading";
import { Card } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { getStoredUser } from "@/lib/auth/session";
import { casesApi } from "@/lib/api/cases";
import { hearingsApi } from "@/lib/api/hearings";
import { formatDateTime } from "@/lib/utils/format-date";
import type { HearingResponseDto } from "@/types/api";

type HearingWithCase = {
  hearing: HearingResponseDto;
  caseId: string;
  caseTitle: string;
  caseNumber: string;
};

const CASES_BASE = "/client/cases";
const CASES_PAGE_SIZE = 100;
const HEARINGS_PER_CASE_PAGE = 100;
const HEARING_CONCURRENCY = 4;

async function mapInChunks<T, R>(
  items: T[],
  concurrency: number,
  fn: (item: T) => Promise<R>,
): Promise<R[]> {
  if (items.length === 0) return [];
  const out: R[] = [];
  for (let i = 0; i < items.length; i += concurrency) {
    const chunk = items.slice(i, i + concurrency);
    const part = await Promise.all(chunk.map((item) => fn(item)));
    out.push(...part);
  }
  return out;
}

function ClientHearingsInner() {
  const [hearings, setHearings] = useState<HearingWithCase[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [fetchId, setFetchId] = useState(0);

  useEffect(() => {
    const u = getStoredUser();
    if (!u?.id) return;
    let cancelled = false;
    (async () => {
      setLoading(true);
      setError(null);
      try {
        const casesPage = await casesApi.list({
          clientId: u.id,
          page: 0,
          size: CASES_PAGE_SIZE,
          includeArchived: true,
        });
        if (cancelled) return;
        const rows = await mapInChunks(
          casesPage.content,
          HEARING_CONCURRENCY,
          async (c) => {
            try {
              const page = await hearingsApi.listByCase(c.id, {
                page: 0,
                size: HEARINGS_PER_CASE_PAGE,
              });
              return page.content.map((h) => ({
                hearing: h,
                caseId: c.id,
                caseTitle: c.title,
                caseNumber: c.caseNumber,
              }));
            } catch {
              return [];
            }
          },
        );
        if (cancelled) return;
        setHearings(
          rows.flat().sort(
            (a, b) =>
              new Date(b.hearing.hearingDate).getTime() -
              new Date(a.hearing.hearingDate).getTime(),
          ),
        );
      } catch {
        if (cancelled) return;
        setError("تعذر تحميل الجلسات");
        setHearings([]);
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [fetchId]);

  const { upcoming, past } = useMemo(() => {
    const now = Date.now();
    const all = [...hearings].sort(
      (a, b) =>
        new Date(a.hearing.hearingDate).getTime() -
        new Date(b.hearing.hearingDate).getTime(),
    );
    return {
      upcoming: all.filter(
        (x) => new Date(x.hearing.hearingDate).getTime() >= now,
      ),
      past: all
        .filter((x) => new Date(x.hearing.hearingDate).getTime() < now)
        .reverse(),
    };
  }, [hearings]);

  return (
    <div className="space-y-8">
      <PageHeader
        title="جلساتي"
        description="كل جلسات قضاياك مرتبة — القادمة أولًا ثم السابقة."
      />

      {error ? <ApiErrorAlert message={error} onRetry={() => setFetchId((n) => n + 1)} /> : null}

      {loading ? (
        <PageLoading label="جارٍ تحميل الجلسات…" minHeightClassName="min-h-[12rem]" />
      ) : hearings.length === 0 ? (
        <EmptyState
          title="لا جلسات"
          description="عند جدولة أي جلسة على قضاياك ستظهر هنا."
          icon={CalendarClock}
        />
      ) : (
        <div className="space-y-8">
          <section>
            <h2 className="mb-3 text-lg font-semibold">الجلسات القادمة</h2>
            {upcoming.length === 0 ? (
              <EmptyState
                title="لا جلسات قادمة"
                description="جميع جلساتك السابقة مؤرشفة هنا."
                icon={CalendarClock}
                compact
              />
            ) : (
              <div className="grid gap-4 sm:grid-cols-2">
                {upcoming.map((x) => (
                  <HearingCard key={`${x.caseId}:${x.hearing.id}`} data={x} upcoming />
                ))}
              </div>
            )}
          </section>

          <section>
            <h2 className="mb-3 text-lg font-semibold">الجلسات السابقة</h2>
            {past.length === 0 ? (
              <EmptyState
                title="لا جلسات سابقة"
                description="لا توجد جلسات مسجلة بعد."
                icon={FileText}
                compact
              />
            ) : (
              <div className="grid gap-4 sm:grid-cols-2">
                {past.map((x) => (
                  <HearingCard key={`${x.caseId}:${x.hearing.id}`} data={x} />
                ))}
              </div>
            )}
          </section>
        </div>
      )}
    </div>
  );
}

function HearingCard({ data, upcoming }: { data: HearingWithCase; upcoming?: boolean }) {
  return (
    <Card className="p-4">
      <div className="flex flex-col gap-2 sm:flex-row sm:items-start sm:justify-between">
        <div className="min-w-0 space-y-1">
          <p className={`text-sm font-semibold ${upcoming ? "text-[var(--color-gold-700)]" : "text-[var(--color-foreground)]"}`}>
            {upcoming ? "قادمة" : "سابقة"}
          </p>
          <p className="text-sm ltr-nums text-[var(--color-foreground)]">
            {formatDateTime(data.hearing.hearingDate)}
          </p>
          <p className="text-sm text-[var(--color-muted-foreground)]">
            {data.hearing.courtName}
            {data.hearing.chamber ? ` · ${data.hearing.chamber}` : ""}
          </p>
        </div>
        <Button variant="outline" size="sm" asChild>
          <Link href={`${CASES_BASE}/${data.caseId}`} className="inline-flex items-center gap-1">
            <FileText className="size-4" />
            {data.caseTitle}
          </Link>
        </Button>
      </div>
      {data.hearing.result ? (
        <p className="mt-3 border-t pt-3 text-sm text-[var(--color-foreground)]">
          النتيجة: {data.hearing.result}
        </p>
      ) : null}
    </Card>
  );
}

export default function ClientHearingsPage() {
  return <ClientHearingsInner />;
}
