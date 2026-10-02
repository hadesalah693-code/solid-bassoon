import { disputeEventsApi } from "@/lib/api/disputeEvents";
import { tasksApi } from "@/lib/api/tasks";
import {
  compareDisputeEvents,
  disputeCurrentStage,
} from "@/lib/cases/dispute-events";
import type {
  CaseResponseDto,
  DisputeEventResponseDto,
  TaskResponseDto,
} from "@/types/api";
import type { Hearing } from "@/types/hearing";

/** Same shape as {@link DashboardLitigationRow} — kept here to avoid lib→components imports. */
export type LitigationSampleRow = {
  case: CaseResponseDto;
  eventsSorted: DisputeEventResponseDto[];
  openTasks: TaskResponseDto[];
  hearing: Hearing | null;
};

async function mapWithConcurrency<T, R>(
  items: readonly T[],
  concurrency: number,
  mapper: (item: T) => Promise<R>,
): Promise<R[]> {
  if (items.length === 0) return [];
  const cap = Math.max(1, Math.min(concurrency, items.length));
  const results = new Array<R>(items.length);
  let cursor = 0;

  async function worker() {
    for (;;) {
      const i = cursor++;
      if (i >= items.length) break;
      results[i] = await mapper(items[i]);
    }
  }

  await Promise.all(Array.from({ length: cap }, () => worker()));
  return results;
}

/**
 * Hydrates litigation rows for the dashboard sample: dispute events + case tasks per case,
 * capped parallel calls to reduce backend bursts.
 */
export async function enrichLitigationSampleCases(
  casesArr: CaseResponseDto[],
  hearingByCaseId: Map<string, Hearing>,
  options: {
    concurrency?: number;
  } = {},
): Promise<{ rows: LitigationSampleRow[]; disputeStagesHot: number }> {
  const concurrency = options.concurrency ?? 4;

  const settled =
    casesArr.length === 0 ?
      []
    : await mapWithConcurrency(casesArr, concurrency, async (c) => {
        const [evR, tkR] = await Promise.allSettled([
          disputeEventsApi.list(c.id, { page: 0, size: 100 }),
          tasksApi.listByCase(c.id, { page: 0, size: 80 }),
        ]);
        const eventsSorted =
          evR.status === "fulfilled" ?
            [...evR.value.content].sort(compareDisputeEvents)
          : [];
        const listTasks = tkR.status === "fulfilled" ? tkR.value.content : [];
        return {
          case: c,
          eventsSorted,
          openTasks: listTasks,
          hearing: hearingByCaseId.get(c.id) ?? null,
        } satisfies LitigationSampleRow;
      });

  let disputeStagesHot = 0;
  for (const r of settled) {
    if (disputeCurrentStage(r.eventsSorted)) disputeStagesHot++;
  }
  return { rows: settled, disputeStagesHot };
}
