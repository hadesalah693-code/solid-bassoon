import type { Party } from "./common";

export type CaseStatus =
  | "NEW"
  | "IN_PROGRESS"
  | "ON_HOLD"
  | "CLOSED"
  | "ARCHIVED";

export type CasePriority = "LOW" | "MEDIUM" | "HIGH" | "URGENT";

export type CaseFile = {
  id: string;
  caseNumber: string;
  courtCaseNumber: string | null;
  title: string;
  description: string | null;
  status: CaseStatus;
  priority: CasePriority;
  caseType: string | null;
  /** اسم المحكمة (مستوى الملف). */
  courtName: string | null;
  /** الدائرة / الدائرة القضائية. */
  courtCircuit: string | null;
  /** مرحلة الإجراء. */
  procedureStage: string | null;
  startDate: string | null;
  closeDate: string | null;
  archivedAt: string | null;
  client: Party;
  assignedLawyer: Party | null;
  createdBy: Party;
  nextHearingAt: string | null;
  parentCaseId: string | null;
  parentCase: CaseFile | null;
  createdAt: string;
  updatedAt: string;
};
