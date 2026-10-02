import type { TaskResponseDto } from "@/types/api";

export type TaskRelationKind = "standalone" | "case" | "dispute";

export type TaskRelationType = "none" | "case" | "dispute";

/** UX labels for task relation badges. */
export const TASK_RELATION_LABEL_AR: Record<TaskRelationKind, string> = {
  standalone: "مهمة مستقلة",
  case: "مرتبطة بقضية",
  dispute: "مرتبطة بنزاع",
};

export function taskRelationKind(
  task: Pick<TaskResponseDto, "caseId" | "disputeId">,
): TaskRelationKind {
  if (task.caseId) return "case";
  if (task.disputeId) return "dispute";
  return "standalone";
}

export function relationTypeFromDefaults(
  defaultCaseId?: string | null,
  defaultDisputeId?: string | null,
): TaskRelationType {
  if (defaultCaseId) return "case";
  if (defaultDisputeId) return "dispute";
  return "none";
}

export function relationKindFromType(type: TaskRelationType): TaskRelationKind {
  if (type === "case") return "case";
  if (type === "dispute") return "dispute";
  return "standalone";
}
