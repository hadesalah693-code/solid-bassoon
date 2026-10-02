export type CirculationStatus = "PENDING" | "APPROVED" | "IN_HAND" | "RETURNED" | "REJECTED" | "CANCELLED";
export type CirculationEventType = "REQUESTED" | "APPROVED" | "REJECTED" | "HANDED_OVER" | "RETURNED" | "CANCELLED";

export type CirculationEvent = {
  id: string;
  eventType: CirculationEventType;
  actorId: string;
  actorName: string | null;
  occurredAt: string;
  notes: string | null;
};

export type CirculationRequest = {
  id: string;
  caseId: string;
  caseNumber: string;
  caseTitle: string;
  fromPerson: { id: string; fullName: string | null; systemRole: string };
  toPerson: { id: string; fullName: string | null; systemRole: string };
  requestedBy: { id: string; fullName: string | null; systemRole: string };
  approvedBy: { id: string; fullName: string | null; systemRole: string } | null;
  requestedAt: string;
  dueBackAt: string | null;
  handedAt: string | null;
  returnedAt: string | null;
  status: CirculationStatus;
  reason: string;
  notes: string | null;
  events: CirculationEvent[];
};

export type CirculationCreateRequest = {
  caseId: string;
  fromPersonId: string;
  toPersonId: string;
  dueBackAt?: string | null;
  reason: string;
  notes?: string | null;
};