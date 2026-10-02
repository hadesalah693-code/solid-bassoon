import { isBefore, isSameDay, parseISO, startOfDay } from "date-fns";

import type { TaskResponseDto } from "@/types/api";

export type TaskDueUrgency = "overdue" | "today" | "upcoming";

const CLOSED: TaskResponseDto["status"][] = ["DONE", "CANCELLED"];

export function getTaskDueUrgency(
  dueDate: string | null | undefined,
  status: TaskResponseDto["status"],
): TaskDueUrgency | null {
  if (!dueDate || CLOSED.includes(status)) return null;
  const dueDay = startOfDay(parseISO(dueDate));
  const today = startOfDay(new Date());
  if (isBefore(dueDay, today)) return "overdue";
  if (isSameDay(dueDay, today)) return "today";
  return "upcoming";
}

export const TASK_DUE_LABEL_AR: Record<TaskDueUrgency, string> = {
  overdue: "متأخرة",
  today: "اليوم",
  upcoming: "قادمة",
};
