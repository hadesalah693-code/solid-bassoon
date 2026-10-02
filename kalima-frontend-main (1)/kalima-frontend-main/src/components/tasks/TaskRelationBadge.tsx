import { Badge } from "@/components/ui/badge";
import {
  TASK_RELATION_LABEL_AR,
  taskRelationKind,
} from "@/lib/tasks/task-relation";
import type { TaskResponseDto } from "@/types/api";

const TONE: Record<
  ReturnType<typeof taskRelationKind>,
  "neutral" | "info" | "navy"
> = {
  standalone: "neutral",
  case: "info",
  dispute: "navy",
};

type Props = {
  task: Pick<TaskResponseDto, "caseId" | "disputeId">;
  className?: string;
};

export function TaskRelationBadge({ task, className }: Props) {
  const kind = taskRelationKind(task);
  return (
    <Badge tone={TONE[kind]} className={className ?? "text-xs"}>
      {TASK_RELATION_LABEL_AR[kind]}
    </Badge>
  );
}
