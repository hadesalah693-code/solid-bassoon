import { hearingsApi } from "@/lib/api/hearings";
import type { HearingResponseDto } from "@/types/api";

export type NextUpcomingHearing = {
  hearing: HearingResponseDto;
  caseId: string;
  caseTitle: string;
  caseNumber: string;
};

type CaseRef = { id: string; title: string; caseNumber: string };

const DEFAULT_MAX_CASES = 15;
const DEFAULT_CONCURRENCY = 4;
const HEARINGS_PER_CASE_PAGE = 100;

type CaseWithPage = { c: CaseRef; page: Awaited<ReturnType<typeof hearingsApi.listByCase>> | null };

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

/**
 * Picks the nearest future {@link HearingResponseDto#hearingDate} across
 * cases. Uses GET /api/cases/{id}/hearings with bounded concurrency (no
 * single burst of N parallel requests) and a cap on how many case IDs to
 * consider.
 */
export async function findNextUpcomingHearing(
  cases: CaseRef[],
  options?: { maxCases?: number; concurrency?: number },
): Promise<NextUpcomingHearing | null> {
  const cap = options?.maxCases ?? DEFAULT_MAX_CASES;
  const concurrency = Math.max(1, options?.concurrency ?? DEFAULT_CONCURRENCY);
  const list = cases.slice(0, cap);
  if (list.length === 0) return null;
  const now = Date.now();
  const candidates: NextUpcomingHearing[] = [];

  const results: CaseWithPage[] = await mapInChunks(list, concurrency, async (c) => {
    try {
      const page = await hearingsApi.listByCase(c.id, {
        page: 0,
        size: HEARINGS_PER_CASE_PAGE,
      });
      return { c, page };
    } catch {
      return { c, page: null };
    }
  });

  for (const { c, page } of results) {
    if (!page) continue;
    for (const h of page.content) {
      const t = new Date(h.hearingDate).getTime();
      if (Number.isFinite(t) && t > now) {
        candidates.push({
          hearing: h,
          caseId: c.id,
          caseTitle: c.title,
          caseNumber: c.caseNumber,
        });
      }
    }
  }
  if (candidates.length === 0) return null;
  candidates.sort(
    (a, b) =>
      new Date(a.hearing.hearingDate).getTime() -
      new Date(b.hearing.hearingDate).getTime(),
  );
  return candidates[0] ?? null;
}
