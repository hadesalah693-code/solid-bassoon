import type { Party } from "./common";

export type HearingStatus =
  | "SCHEDULED"
  | "HELD"
  | "POSTPONED"
  | "CANCELLED"
  | "ADJOURNED";

export type HearingType =
  | "ORDINARY"
  | "DEFENSE"
  | "EVIDENCE"
  | "EXPEDITED"
  | "ARBITRATION"
  | "INVESTIGATION"
  | "APPEAL"
  | "SENTENCING"
  | "REVIEW";

export type Hearing = {
  id: string;
  caseId: string;
  caseNumber: string;
  caseTitle: string;
  scheduledAt: string;
  court: string | null;
  room: string | null;
  status: HearingStatus;
  notes: string | null;
  assignedLawyer: Party | null;
  client: Party;
};
