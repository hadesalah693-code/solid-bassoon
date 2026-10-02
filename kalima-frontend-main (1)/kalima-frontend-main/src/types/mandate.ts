import type { Party } from "@/types/common";

/** Mirrors `DomainEnums.MandateStatus` (kalima-backend). */
export type MandateStatusDto =
  | "PENDING"
  | "CONFIRMED"
  | "IN_PROGRESS"
  | "COMPLETED"
  | "CANCELLED";

/** Mirrors `DomainEnums.MandatePriority`. */
export type MandatePriorityDto = "LOW" | "MEDIUM" | "HIGH" | "URGENT";

/** Mirrors `DomainEnums.MandatePaymentStatus`. */
export type MandatePaymentStatusDto = "UNPAID" | "PARTIALLY_PAID" | "PAID";

/** Mirrors `DomainEnums.MandateDocumentType`. */
export type MandateDocumentTypeDto =
  | "POWER_OF_ATTORNEY"
  | "SUBSTITUTION_REQUEST"
  | "ID_COPY"
  | "CASE_DOCUMENT"
  | "HEARING_MINUTES"
  | "OTHER";

/** Mirrors `DomainEnums.MandateEventType`. */
export type MandateEventTypeDto =
  | "CREATED"
  | "STATUS_CHANGED"
  | "CONFIRMED"
  | "ATTENDED_SESSION"
  | "SESSION_RESULT_RECORDED"
  | "PAYMENT_RECORDED"
  | "CLOSED"
  | "CANCELLED"
  | "NOTE_ADDED";

/** Lightweight row for the mandates list. */
export type MandateListItemDto = {
  id: string;
  mandateNumber: string;
  status: MandateStatusDto;
  priority: MandatePriorityDto;
  paymentStatus: MandatePaymentStatusDto;
  caseNumber: string;
  caseTitle: string;
  client: Party | null;
  originalLawyer: Party | null;
  substitutedLawyer: Party | null;
  sessionDate: string | null;
  courtName: string | null;
  fees: string;
  currency: string;
  createdAt: string;
};

/** Full detail response for a mandate. */
export type MandateResponseDto = {
  id: string;
  mandateNumber: string;
  mandateYear: number;
  status: MandateStatusDto;
  priority: MandatePriorityDto;
  paymentStatus: MandatePaymentStatusDto;
  currency: string;
  fees: string;
  amountPaid: string;
  remaining: string;
  caseId: string;
  caseNumber: string;
  caseTitle: string;
  courtName: string | null;
  hearingId: string | null;
  nextHearingId: string | null;
  sessionDate: string | null;
  courtCircuit: string | null;
  courtroom: string | null;
  client: Party | null;
  originalLawyer: Party | null;
  substitutedLawyer: Party | null;
  reason: string | null;
  notes: string | null;
  instructions: string | null;
  cancelledReason: string | null;
  sessionResult: string | null;
  courtDecision: string | null;
  didAttend: boolean | null;
  nextSessionDate: string | null;
  nextSessionNotes: string | null;
  closedAt: string | null;
  createdAt: string;
  updatedAt: string;
};

/** Aggregate statistics for the mandates header cards. */
export type MandateStatisticsDto = {
  total: number;
  today: number;
  upcoming: number;
  completed: number;
  cancelled: number;
  totalFees: string;
  totalPaid: string;
  totalRemaining: string;
};

/** Request body for creating a mandate. */
export type MandateCreateRequestDto = {
  caseId: string;
  hearingId?: string | null;
  nextHearingId?: string | null;
  clientId: string;
  originalLawyerId: string;
  substitutedLawyerId: string;
  priority: MandatePriorityDto;
  sessionDate?: string | null;
  courtName?: string | null;
  courtCircuit?: string | null;
  courtroom?: string | null;
  reason?: string | null;
  notes?: string | null;
  instructions?: string | null;
  fees?: string | null;
  currency?: string | null;
  amountPaid?: string | null;
};

/** Optional-field update. */
export type MandateUpdateRequestDto = Partial<MandateCreateRequestDto> & {
  status?: MandateStatusDto;
  cancelledReason?: string | null;
};

export type MandateStatusUpdateRequestDto = {
  status: MandateStatusDto;
  cancelledReason?: string | null;
};

export type MandateSessionResultRequestDto = {
  didAttend: boolean;
  sessionResult?: string | null;
  courtDecision?: string | null;
  notes?: string | null;
  nextSessionDate?: string | null;
  nextSessionNotes?: string | null;
};

export type MandatePaymentRequestDto = {
  amount: string;
  paymentDate: string;
  method?: string | null;
  notes?: string | null;
};

export type MandatePaymentDto = {
  id: string;
  amount: string;
  paymentDate: string;
  method: string | null;
  notes: string | null;
  recordedByEmail: string | null;
  createdAt: string;
};

export type MandateEventDto = {
  id: string;
  eventType: MandateEventTypeDto;
  note: string | null;
  actorEmail: string | null;
  createdAt: string;
};

export type MandateDocumentDto = {
  id: string;
  mandateId: string;
  originalFilename: string;
  contentType: string;
  sizeBytes: number;
  description: string | null;
  documentType: MandateDocumentTypeDto;
  uploadedBy: Party | null;
  createdAt: string;
  updatedAt: string;
};
