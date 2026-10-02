import type { HearingOfficeItemDto } from "@/types/api";
import type { Hearing } from "@/types/hearing";

export function officeHearingToUi(h: HearingOfficeItemDto): Hearing {
  return {
    id: h.id,
    caseId: h.caseId,
    caseNumber: h.caseNumber,
    caseTitle: h.caseTitle,
    scheduledAt: h.hearingDate,
    court: h.courtName,
    room: h.chamber,
    status: h.status,
    notes: h.notes,
    assignedLawyer: h.assignedLawyer
      ? {
          id: h.assignedLawyer.id,
          fullName: h.assignedLawyer.fullName,
          systemRole: h.assignedLawyer.systemRole,
        }
      : null,
    client: {
      id: h.client.id,
      fullName: h.client.fullName,
      systemRole: h.client.systemRole,
    },
  };
}
