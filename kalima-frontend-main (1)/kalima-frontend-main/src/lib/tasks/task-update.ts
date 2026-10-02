import type { AdminTaskUpdateRequestDto, TaskResponseDto } from "@/types/api";

export function buildAdminTaskUpdate(
  task: TaskResponseDto,
  patch: Partial<AdminTaskUpdateRequestDto> = {},
): AdminTaskUpdateRequestDto {
  return {
    title: patch.title ?? task.title,
    description:
      patch.description !== undefined ? patch.description : task.description,
    priority: patch.priority ?? task.priority,
    status: patch.status ?? task.status,
    dueDate: patch.dueDate !== undefined ? patch.dueDate : task.dueDate,
    assignedToId:
      patch.assignedToId !== undefined
        ? patch.assignedToId
        : (task.assignedTo?.id ?? null),
    caseId: patch.caseId !== undefined ? patch.caseId : task.caseId,
    disputeId:
      patch.disputeId !== undefined ? patch.disputeId : task.disputeId,
    adminTaskType:
      patch.adminTaskType !== undefined
        ? patch.adminTaskType
        : task.adminTaskType,
  };
}

export function toDateTimeLocal(value: string | null | undefined): string {
  if (!value) return "";
  return value.length >= 16 ? value.slice(0, 16) : value;
}
