import type { PartySummaryDto } from "@/types/api";
import type { Party } from "@/types/common";

export type DisputeTypeDto =
  | "CIVIL"
  | "COMMERCIAL"
  | "LABOR"
  | "ADMINISTRATIVE"
  | "CRIMINAL"
  | "FAMILY"
  | "REAL_ESTATE"
  | "OTHER";

export type DisputeStatusDto =
  | "NEW"
  | "UNDER_REVIEW"
  | "WAITING_DOCUMENTS"
  | "FRIENDLY_SETTLEMENT"
  | "NEGOTIATION"
  | "MEDIATION"
  | "READY_TO_CONVERT"
  | "CONVERTED_TO_CASE"
  | "CLOSED"
  | "REJECTED";

export type LegalPathDto =
  | "FRIENDLY_SETTLEMENT"
  | "LEGAL_NOTICE"
  | "NEGOTIATION"
  | "MEDIATION"
  | "COURT_CASE"
  | "CRIMINAL_REPORT"
  | "ARBITRATION"
  | "ADMINISTRATIVE_ACTION";

export type RiskLevelDto = "LOW" | "MEDIUM" | "HIGH" | "CRITICAL";

/** Legal standing of primary client / opponent on the dispute row. */
export type DisputePartyCapacityDto =
  | "PLAINTIFF"
  | "DEFENDANT"
  | "CLAIMANT"
  | "RESPONDENT"
  | "LESSOR"
  | "LESSEE"
  | "EMPLOYER"
  | "EMPLOYEE"
  | "OTHER";

/** Work-team role on a dispute ({@code dispute_team_members.role}). */
export type DisputeTeamRoleDto =
  | "ASSIGNED_LAWYER"
  | "LEGAL_CONSULTANT"
  | "ADMINISTRATIVE_OFFICER"
  | "COORDINATION_REVIEW"
  | "TRANSLATION_OFFICER"
  | "INITIAL_REVIEW"
  | "FINAL_REVIEW";

export type DisputeTeamMemberDto = {
  id: string;
  role: DisputeTeamRoleDto;
  person: Party | null;
};

export type DisputeWorkflowStepDto =
  | "CREATED"
  | "INITIAL_REVIEW"
  | "DOCUMENT_COLLECTION"
  | "NEGOTIATION"
  | "MEDIATION"
  | "LEGAL_NOTICE"
  | "COURT_FILING"
  | "HEARINGS"
  | "JUDGMENT"
  | "CLOSED";

export type DisputeTimelineEventTypeDto =
  | "CREATED"
  | "STATUS_CHANGED"
  | "UPDATED"
  | "LAWYER_ASSIGNED"
  | "LEGAL_PATH_CHANGED"
  | "WORKFLOW_STEP_CHANGED"
  | "CONVERTED_TO_CASE"
  | "NOTE_ADDED";

export type DisputeTimelineEventDto = {
  id: string;
  eventType: DisputeTimelineEventTypeDto;
  title: string;
  description: string | null;
  createdAt: string;
  createdBy: Party | null;
};

export type DisputeListItemDto = {
  id: string;
  disputeNumber?: string | null;
  title: string;
  opponentName: string;
  disputeType: DisputeTypeDto;
  status: DisputeStatusDto;
  legalPath: LegalPathDto | null;
  currentStep: DisputeWorkflowStepDto;
  riskLevel: RiskLevelDto;
  claimAmount: number | null;
  client: Party | null;
  assignedLawyer: Party | null;
  convertedCaseId: string | null;
  createdAt: string;
};

export type DisputeResponseDto = {
  id: string;
  disputeNumber?: string | null;
  opponentName: string;
  title: string;
  description: string | null;
  disputeType: DisputeTypeDto;
  status: DisputeStatusDto;
  legalPath: LegalPathDto | null;
  currentStep: DisputeWorkflowStepDto;
  claimAmount: number | null;
  riskLevel: RiskLevelDto;
  successProbability: number | null;
  client: Party | null;
  clientRole: DisputePartyCapacityDto | null;
  assignedLawyer: Party | null;
  opponentRole: DisputePartyCapacityDto | null;
  convertedCaseId: string | null;
  createdAt: string;
  updatedAt: string;
  timeline: DisputeTimelineEventDto[];
  team: DisputeTeamMemberDto[];
};

export type DisputeCreateRequestDto = {
  title: string;
  opponentName: string;
  description?: string | null;
  disputeType: DisputeTypeDto;
  riskLevel: RiskLevelDto;
  legalPath?: LegalPathDto | null;
  claimAmount?: number | null;
  successProbability?: number | null;
  clientId: string;
  clientRole?: DisputePartyCapacityDto | null;
  opponentRole?: DisputePartyCapacityDto | null;
  assignedLawyerId?: string | null;
  administrativeOfficerId?: string | null;
};

export type DisputeUpdateRequestDto = {
  title?: string | null;
  opponentName?: string | null;
  description?: string | null;
  disputeType?: DisputeTypeDto | null;
  status?: DisputeStatusDto | null;
  legalPath?: LegalPathDto | null;
  currentStep?: DisputeWorkflowStepDto | null;
  claimAmount?: number | null;
  riskLevel?: RiskLevelDto | null;
  successProbability?: number | null;
  clientId?: string | null;
  clientRole?: DisputePartyCapacityDto | null;
  opponentRole?: DisputePartyCapacityDto | null;
  assignedLawyerId?: string | null;
};

export type UpdateDisputeTeamRequestDto = {
  assignments: { role: DisputeTeamRoleDto; personId: string | null }[];
};

export type AddDisputeTimelineNoteRequestDto = {
  title: string;
  description?: string | null;
};

export type DisputeStage = {
  id: string;
  disputeId: string;
  stageType: string;
  status: string;
  title: string | null;
  startedAt: string | null;
  closedAt: string | null;
  /** Outcome code when stage was closed (e.g. SUCCESS). */
  outcome?: string | null;
  outcomeNote?: string | null;
  createdAt: string;
  updatedAt: string;
};

export type CreateDisputeStageRequest = {
  stageType: string;
  status?: string | null;
  title?: string | null;
  startedAt?: string | null;
  closedAt?: string | null;
};

export type UpdateDisputeStageRequest = {
  title?: string | null;
  status?: string | null;
  startedAt?: string | null;
  closedAt?: string | null;
  /** Required when closing a stage for the first time (backend validates). */
  closingNote?: string | null;
  /** Required when closing a stage for the first time. */
  outcome?: string | null;
  outcomeNote?: string | null;
};

export type DisputeCaseLink = {
  id: string;
  disputeId: string;
  caseId: string;
  linkRole: string;
  createdAt: string;
};

export type LinkDisputeCaseRequest = {
  caseId: string;
  linkRole?: string | null;
};

export type DisputeAdministrativeUpdate = {
  id: string;
  disputeId: string;
  stageId: string | null;
  updateType: string;
  title: string;
  description: string | null;
  occurredAt: string | null;
  createdBy: Party | null;
  createdAt: string;
};

export type CreateDisputeAdministrativeUpdateRequest = {
  updateType: string;
  title: string;
  description?: string | null;
  occurredAt?: string | null;
  stageId?: string | null;
};

export type DisputeStageActionStatusDto =
  | "OPEN"
  | "IN_PROGRESS"
  | "DONE"
  | "CANCELLED";

export type DisputeStageActionPriorityDto =
  | "LOW"
  | "MEDIUM"
  | "HIGH"
  | "URGENT";

export type DisputeStageActionTypeDto =
  | "CALL"
  | "MEETING"
  | "DOCUMENT"
  | "DRAFT"
  | "REVIEW"
  | "SEND_NOTICE"
  | "FOLLOW_UP"
  | "COURT_PREPARATION"
  | "PAYMENT"
  | "OTHER";

export type DisputeStageActionDto = {
  id: string;
  disputeId: string;
  stageId: string | null;
  actionType: string;
  title: string;
  description: string | null;
  status: string;
  priority: string;
  dueAt: string | null;
  completedAt: string | null;
  completionNote?: string | null;
  completedBy?: Party | null;
  assignedTo: Party | null;
  createdBy: Party | null;
  createdAt: string;
  updatedAt: string;
  overdue: boolean;
};

export type DisputeActionSummaryDto = {
  openCount: number;
  overdueCount: number;
  doneCount: number;
  cancelledCount: number;
};

export type CreateDisputeStageActionRequest = {
  stageId?: string | null;
  actionType: string;
  title: string;
  description?: string | null;
  priority?: string | null;
  dueAt?: string | null;
  assignedTo?: string | null;
};

export type UpdateDisputeStageActionRequest = {
  unlinkStage?: boolean | null;
  stageId?: string | null;
  actionType?: string | null;
  title?: string | null;
  description?: string | null;
  status?: string | null;
  priority?: string | null;
  dueAt?: string | null;
  clearDueAt?: boolean | null;
  assignedTo?: string | null;
  clearAssignee?: boolean | null;
  /** Required when setting status to DONE via PATCH. */
  completionNote?: string | null;
};

export type CompleteDisputeStageActionRequestDto = {
  completionNote: string;
};

/** DomainEnums.DisputePartyRole */
export type DisputePartyRoleDto =
  | "CO_CLIENT"
  | "OPPOSED_PARTY"
  | "CO_OPPONENT"
  | "REPRESENTATIVE"
  | "WITNESS"
  | "EXPERT"
  | "THIRD_PARTY"
  | "OTHER";

export type DisputePartyDto = {
  id: string;
  disputeId: string;
  role: DisputePartyRoleDto;
  person: PartySummaryDto | null;
  displayName: string | null;
  phone: string | null;
  notes: string | null;
  sortOrder: number;
};

export type CreateDisputePartyRequestDto = {
  role: DisputePartyRoleDto;
  personId?: string | null;
  displayName?: string | null;
  phone?: string | null;
  notes?: string | null;
  sortOrder?: number | null;
};

export type UpdateDisputePartyRequestDto = {
  role?: DisputePartyRoleDto | null;
  personId?: string | null;
  displayName?: string | null;
  phone?: string | null;
  notes?: string | null;
  sortOrder?: number | null;
};
