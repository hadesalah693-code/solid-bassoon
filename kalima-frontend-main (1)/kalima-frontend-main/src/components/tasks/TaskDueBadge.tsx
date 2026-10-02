import { Badge } from "@/components/ui/badge";
import {
  getTaskDueUrgency,
  TASK_DUE_LABEL_AR,
  type TaskDueUrgency,
} from "@/lib/tasks/task-due";
import type { TaskResponseDto } from "@/types/api";

const TONE: Record<
  TaskDueUrgency,
  "danger" | "warning" | "info" | "neutral"
> = {
  overdue: "danger",
  today: "warning",
  upcoming: "info",
};

export function TaskDueBadge({ task }: { task: TaskResponseDto }) {
  const urgency = getTaskDueUrgency(task.dueDate, task.status);
  if (!urgency) return null;
  return (
    <Badge tone={TONE[urgency]} className="text-xs">
      {TASK_DUE_LABEL_AR[urgency]}
    </Badge>
  );
}
