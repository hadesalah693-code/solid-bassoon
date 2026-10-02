import type { DisputeStageActionDto } from "@/types/dispute";

function isActiveActionStatus(status: string) {
  const u = status.toUpperCase();
  return u === "OPEN" || u === "IN_PROGRESS";
}

/**
 * Progress metrics for actions belonging to a single stage.
 *
 * Percentage rule (batch K): completed / (total − cancelled). Cancelled rows do not
 * count toward the denominator so the bar reflects «ما تبقّى للإنجاز» among meaningful tasks.
 */
export type StageActionProgress = {
  totalAll: number;
  cancelled: number;
  completed: number;
  active: number;
  overdue: number;
  /** totalAll minus cancelled — used as denominator for percentage */
  totalForProgress: number;
  /** 0–100, rounded */
  percentage: number;
};

export function computeStageActionProgress(
  actions: DisputeStageActionDto[],
): StageActionProgress {
  const totalAll = actions.length;
  let cancelled = 0;
  let completed = 0;
  let active = 0;
  let overdue = 0;

  for (const a of actions) {
    const st = a.status.toUpperCase();
    if (st === "CANCELLED") {
      cancelled++;
      continue;
    }
    if (st === "DONE") {
      completed++;
      continue;
    }
    if (isActiveActionStatus(st)) {
      active++;
      if (a.overdue) overdue++;
    }
  }

  const totalForProgress = totalAll - cancelled;
  const percentage =
    totalForProgress > 0 ? Math.round((100 * completed) / totalForProgress) : 0;

  return {
    totalAll,
    cancelled,
    completed,
    active,
    overdue,
    totalForProgress,
    percentage,
  };
}

export function actionsForStage(
  all: DisputeStageActionDto[],
  stageId: string,
): DisputeStageActionDto[] {
  return all.filter((a) => a.stageId === stageId);
}
