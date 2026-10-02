import type { CasePriority, CaseFile, CaseStatus } from "@/types/case";
import type { Party } from "@/types/common";

/** Aligned with `com.kalima.backend.casefile.dto.PartySummary` + JSON field names. */
export type PartySummaryDto = {
  id: string;
  fullName: string | null;
  systemRole: string;
};

/** Aligned with `DomainEnums.DisputePathType` (kalima-backend). */
export type DisputePathTypeDto =
  | "CIVIL"
  | "COMMERCIAL"
  | "LABOR"
  | "CRIMINAL"
  | "ADMINISTRATIVE"
  | "ARBITRATION"
  | "CUSTOM";

/** Aligned with `CaseCreateRequest` in kalima-backend. */
export type CaseCreateRequestDto = {
  caseNumber?: string;
  title: string;
  clientId: string;
  courtCaseNumber?: string | null;
  description?: string | null;
  caseType?: string | null;
  courtName?: string | null;
  courtCircuit?: string | null;
  procedureStage?: string | null;
  status?: CaseStatus;
  startDate?: string | null;
  assignedLawyerId?: string | null;
  disputePathType?: DisputePathTypeDto | null;
  priority?: CasePriority;
  parentCaseId?: string | null;
};

/** Aligned with `CaseUpdateRequest` in kalima-backend. */
export type CaseUpdateRequestDto = {
  title: string;
  courtCaseNumber?: string | null;
  description?: string | null;
  caseType?: string | null;
  courtName?: string | null;
  courtCircuit?: string | null;
  procedureStage?: string | null;
  startDate?: string | null;
  closeDate?: string | null;
  assignedLawyerId?: string | null;
  priority?: CasePriority;
};

/** Aligned with `CaseResponse` in kalima-backend. */
export type CaseResponseDto = {
  id: string;
  caseNumber: string;
  courtCaseNumber: string | null;
  title: string;
  description: string | null;
  status: CaseStatus;
  priority: CasePriority;
  parentCaseId: string | null;
  caseType: string | null;
  courtName: string | null;
  courtCircuit: string | null;
  procedureStage: string | null;
  disputePathType?: DisputePathTypeDto | null;
  startDate: string | null;
  closeDate: string | null;
  archivedAt: string | null;
  client: PartySummaryDto;
  assignedLawyer: PartySummaryDto | null;
  createdBy: PartySummaryDto;
  createdAt: string;
  updatedAt: string;
};

export type CaseInternalNoteCreateDto = { content: string };

export type CaseInternalNoteDto = {
  id: string;
  content: string;
  author: PartySummaryDto;
  createdAt: string;
};

/** Aligned with `HearingCreateRequest` in kalima-backend. */
export type HearingCreateRequestDto = {
  hearingDate: string;
  courtName: string;
  chamber?: string | null;
  result?: string | null;
  notes?: string | null;
  nextHearingDate?: string | null;
  hearingType?: HearingTypeDto | null;
  status?: HearingStatusDto | null;
  outcomeCode?: string | null;
  didAttend?: boolean | null;
  presidingJudge?: string | null;
  previousHearingId?: string | null;
  durationMinutes?: number | null;
};

/** Aligned with `HearingUpdateRequest` — same shape as create (full replacement). */
export type HearingUpdateRequestDto = HearingCreateRequestDto;

export type HearingStatusDto =
  | "SCHEDULED"
  | "HELD"
  | "POSTPONED"
  | "CANCELLED"
  | "ADJOURNED";

export type HearingTypeDto =
  | "ORDINARY"
  | "DEFENSE"
  | "EVIDENCE"
  | "EXPEDITED"
  | "ARBITRATION"
  | "INVESTIGATION"
  | "APPEAL"
  | "SENTENCING"
  | "REVIEW";

export type HearingStatusUpdateRequestDto = {
  status: HearingStatusDto;
  didAttend?: boolean | null;
  outcomeCode?: string | null;
};

/** Aligned with `TaskCreateRequest` in kalima-backend. */
export type TaskCreateRequestDto = {
  title: string;
  description?: string | null;
  priority: "LOW" | "MEDIUM" | "HIGH" | "URGENT";
  status: "TODO" | "IN_PROGRESS" | "DONE" | "CANCELLED";
  dueDate?: string | null;
  assignedToId?: string | null;
};

/** Aligned with `TaskUpdateRequest` — same shape as create (full replacement). */
export type TaskUpdateRequestDto = TaskCreateRequestDto;

/** Aligned with `AdminTaskCreateRequest` — optional case/dispute links. */
export type AdminTaskCreateRequestDto = TaskCreateRequestDto & {
  caseId?: string | null;
  disputeId?: string | null;
  adminTaskType?: AdminTaskTypeDto | null;
};

/** Aligned with `AdminTaskUpdateRequest`. */
export type AdminTaskUpdateRequestDto = AdminTaskCreateRequestDto;

/** Aligned with `DomainEnums.AdminTaskType` (kalima-backend). */
export type AdminTaskTypeDto =
  | "NEW_LAWSUIT_REGISTRATION"
  | "APPEAL_REGISTRATION"
  | "EMAIL_REVIEW"
  | "ENFORCEMENT_LAWSUITS"
  | "ENFORCEMENT_REQUESTS"
  | "COURTS_TAQADI_SYSTEM"
  | "PUBLIC_PROSECUTION_SYSTEM"
  | "DELEGATIONS"
  | "TRANSLATION"
  | "MEMO_COORDINATION_REVIEW"
  | "HEARING_DATE_FOLLOWUP"
  | "EXPERT_REPORT_FOLLOWUP"
  | "OPPONENT_APPEAL_FOLLOWUP"
  | "OPPONENT_ENFORCEMENT_FOLLOWUP"
  | "FAVORABLE_ENFORCEMENT_FOLLOWUP"
  | "OPPONENT_MEMO_FOLLOWUP"
  | "CRIMINAL_CASE_FOLLOWUP"
  | "OPPONENT_CASSATION_FOLLOWUP"
  | "OPPONENT_GRIEVANCE_FOLLOWUP"
  | "ENFORCEMENT_DISBURSEMENT_FOLLOWUP"
  | "LABOR_COMMITTEE_REFERRAL_FOLLOWUP"
  | "CIVIL_COURT_REFERRAL_FOLLOWUP"
  | "CRIMINAL_COURT_REFERRAL_FOLLOWUP"
  | "JUDGMENT_AMENDMENT_ENFORCEMENT_FOLLOWUP"
  | "APPEAL_COURT_REFERRAL_FOLLOWUP"
  | "PROSECUTION_ENFORCEMENT_JUDGMENT_AMENDMENT"
  | "ENFORCEMENT_REVIEW"
  | "COURT_REVIEW"
  | "EXPERT_DEPOSIT_PAYMENT"
  | "STRIKE_RENEWAL"
  | "PUBLIC_PROSECUTION_REVIEW";

export type CaseParticipantRoleDto =
  | "CLIENT"
  | "ASSIGNED_LAWYER"
  | "SECONDARY_LAWYER"
  | "OPPOSING_PARTY"
  | "WITNESS"
  | "EXPERT"
  | "JUDGE"
  | "COURT_OFFICIAL"
  | "THIRD_PARTY";

export type ParticipantResponseDto = {
  id: string;
  caseId: string;
  person: PartySummaryDto;
  role: CaseParticipantRoleDto;
  notes: string | null;
  createdAt: string;
};

export type CaseDocumentCategoryDto =
  | "POWER_OF_ATTORNEY"
  | "CONTRACT"
  | "COURT_FILING"
  | "JUDGMENT"
  | "EVIDENCE"
  | "RECEIPT"
  | "OTHER";

export type CaseDocumentVisibilityDto = "STAFF_ONLY" | "CLIENT_VISIBLE";

export type CaseDocumentResponseDto = {
  id: string;
  caseId: string;
  originalFilename: string;
  contentType: string;
  sizeBytes: number;
  category: CaseDocumentCategoryDto;
  visibility: CaseDocumentVisibilityDto;
  description: string | null;
  uploadedBy: PartySummaryDto;
  createdAt: string;
  updatedAt: string;
};

/** GET /api/disputes/{id}/documents */
export type DisputeDocumentResponseDto = {
  id: string;
  disputeId: string;
  /** Present when the file is linked to a dispute stage. */
  stageId: string | null;
  originalFilename: string;
  contentType: string;
  sizeBytes: number;
  category: CaseDocumentCategoryDto;
  visibility: CaseDocumentVisibilityDto;
  description: string | null;
  uploadedBy: PartySummaryDto;
  createdAt: string;
  updatedAt: string;
};

export type MessageSenderType = "USER" | "BOT";

/** Aligned with `MessageInboxItemResponse` in kalima-backend. */
export type MessageInboxItemDto = {
  caseId: string;
  caseNumber: string;
  caseTitle: string;
  client: PartySummaryDto;
  assignedLawyer: PartySummaryDto | null;
  /** {@code null} when no messages have been exchanged for this case yet. */
  lastMessage: MessageResponseDto | null;
  unreadCount: number;
};

export type MessagePageResponseDto = {
  items: MessageResponseDto[];
  hasMore: boolean;
  nextCursor: string | null;
};

export type MessageResponseDto = {
  id: string;
  caseId: string;
  /** {@code null} only when {@link senderType} is {@code "BOT"}. */
  sender: PartySummaryDto | null;
  /** {@code null} when the receiver was deleted; defensive on the FE. */
  receiver: PartySummaryDto | null;
  senderType: MessageSenderType;
  content: string;
  /** URL to a file attachment; {@code null} when no attachment. */
  attachmentUrl: string | null;
  /** Nested preview of the replied-to message; {@code null} when not a reply. */
  replyTo: ReplyPreviewDto | null;
  read: boolean;
  readAt: string | null;
  createdAt: string;
  /** Emoji reactions keyed by emoji character, value is count. */
  reactions: Record<string, number>;
  /** The current user's own reaction emoji, or {@code null}. */
  myReaction: string | null;
};

export type ReplyPreviewDto = {
  id: string;
  content: string;
  senderName: string | null;
};

export type HearingResponseDto = {
  id: string;
  caseId: string;
  hearingDate: string;
  courtName: string;
  chamber: string | null;
  result: string | null;
  notes: string | null;
  nextHearingDate: string | null;
  hearingType: HearingTypeDto;
  status: HearingStatusDto;
  outcomeCode: string | null;
  didAttend: boolean | null;
  presidingJudge: string | null;
  previousHearingId: string | null;
  durationMinutes: number | null;
  createdBy: PartySummaryDto;
  createdAt: string;
  updatedAt: string;
};

/** `GET /api/hearings` — tenant list with case context (see kalima-backend). */
export type HearingOfficeItemDto = {
  id: string;
  caseId: string;
  caseNumber: string;
  caseTitle: string;
  hearingDate: string;
  courtName: string;
  chamber: string | null;
  result: string | null;
  notes: string | null;
  hearingType: HearingTypeDto;
  status: HearingStatusDto;
  outcomeCode: string | null;
  didAttend: boolean | null;
  presidingJudge: string | null;
  previousHearingId: string | null;
  durationMinutes: number | null;
  client: PartySummaryDto;
  assignedLawyer: PartySummaryDto | null;
  createdBy: PartySummaryDto;
  createdAt: string;
};

/** Aligned with `TaskResponse` in kalima-backend. */
export type TaskResponseDto = {
  id: string;
  caseId: string | null;
  disputeId: string | null;
  caseNumber: string | null;
  caseTitle: string | null;
  disputeNumber: string | null;
  disputeTitle: string | null;
  title: string;
  description: string | null;
  priority: "LOW" | "MEDIUM" | "HIGH" | "URGENT";
  status: "TODO" | "IN_PROGRESS" | "DONE" | "CANCELLED";
  dueDate: string | null;
  assignedTo: PartySummaryDto | null;
  createdBy: PartySummaryDto;
  adminTaskType: AdminTaskTypeDto | null;
  completedAt: string | null;
  createdAt: string;
  updatedAt: string;
};

/** Aligned with `DomainEnums.DisputeEventType` (kalima-backend). */
export type DisputeEventTypeDto =
  | "CASE_OPENED"
  | "WARNING_NOTICE"
  | "CLAIM_FILED"
  | "FIRST_HEARING"
  | "MEMO_EXCHANGE"
  | "JUDGMENT"
  | "APPEAL"
  | "OTHER";

/** Aligned with `DomainEnums.DisputeEventStatus` (kalima-backend). */
export type DisputeEventStatusDto =
  | "DONE"
  | "CURRENT"
  | "UPCOMING"
  | "CANCELLED";

/** Aligned with `DisputeEventResponse` in kalima-backend. */
export type DisputeEventResponseDto = {
  id: string;
  caseId: string;
  type: DisputeEventTypeDto;
  status: DisputeEventStatusDto;
  title: string;
  summary: string | null;
  eventDate: string | null;
  sortOrder: number;
  createdAt: string;
  updatedAt: string;
};

/** Aligned with `DisputeEventCreateRequest` — `sortOrder` optional (server defaults). */
export type DisputeEventCreateRequestDto = {
  type: DisputeEventTypeDto;
  status: DisputeEventStatusDto;
  title: string;
  summary?: string | null;
  eventDate?: string | null;
  sortOrder?: number | null;
};

/** Aligned with `DisputeEventPatchRequest` — full replacement on PATCH. */
export type DisputeEventPatchRequestDto = {
  type: DisputeEventTypeDto;
  status: DisputeEventStatusDto;
  title: string;
  summary?: string | null;
  eventDate?: string | null;
  sortOrder: number;
};

const DEFAULT_CASE_PRIORITY: CasePriority = "MEDIUM";

function partyFromDto(p: PartySummaryDto): Party {
  return {
    id: p.id,
    fullName: p.fullName,
    systemRole: p.systemRole,
  };
}

/**
 * UI model still uses {@link CaseFile} (includes `priority` + `nextHearingAt` the API omits on case).
 */
export function caseFileFromDto(c: CaseResponseDto): CaseFile {
  return {
    id: c.id,
    caseNumber: c.caseNumber,
    courtCaseNumber: c.courtCaseNumber,
    title: c.title,
    description: c.description,
    status: c.status,
    priority: c.priority ?? DEFAULT_CASE_PRIORITY,
    caseType: c.caseType,
    courtName: c.courtName,
    courtCircuit: c.courtCircuit,
    procedureStage: c.procedureStage,
    startDate: c.startDate,
    closeDate: c.closeDate,
    archivedAt: c.archivedAt,
    client: partyFromDto(c.client),
    assignedLawyer: c.assignedLawyer
      ? partyFromDto(c.assignedLawyer)
      : null,
    createdBy: partyFromDto(c.createdBy),
    nextHearingAt: null,
    parentCaseId: null,
    parentCase: null,
    createdAt: c.createdAt,
    updatedAt: c.updatedAt,
  };
}
