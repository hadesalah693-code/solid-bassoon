import type { Party } from "@/types/common";

export type DelegationTypeDto =
  | "GENERAL"
  | "SPECIAL"
  | "JUDICIAL"
  | "COMMERCIAL"
  | "ADMINISTRATIVE"
  | "OFFICIAL"
  | "OTHER";

export type DelegationStatusDto = "ACTIVE" | "EXPIRED" | "CANCELLED";

export type DelegationCancelReasonDto = "BY_PRINCIPAL" | "BY_AGENT" | "OTHER";

export type DelegationDocumentTypeDto = "ORIGINAL" | "CANCELLATION" | "OTHER";

export type DelegationListItemDto = {
  id: string;
  delegationNumber: string;
  notaryNumber: string | null;
  title: string;
  delegationType: DelegationTypeDto;
  status: DelegationStatusDto;
  issuingCountry: string;
  issuedAt: string;
  expiresAt: string;
  daysRemaining: number | null;
  starred: boolean;
  client: Party | null;
  agents: Party[];
  agent: Party | null;
  createdAt: string;
};

export type DelegationResponseDto = DelegationListItemDto & {
  cancelReason: DelegationCancelReasonDto | null;
  cancelReasonNote: string | null;
  cancelledAt: string | null;
  cancellationAnnouncementAt: string | null;
  partners: Party[];
  notes: string | null;
  reminderOffsets: number[];
  reminderRecipientIds: string[];
  updatedAt: string;
};

export type DelegationCreateRequestDto = {
  clientId: string;
  agentId?: string | null;
  agentIds?: string[];
  delegationType: DelegationTypeDto;
  issuingCountry: string;
  issuedAt: string;
  expiresAt?: string | null;
  notaryNumber?: string | null;
  partnerIds?: string[];
  notes?: string | null;
  reminderOffsets?: number[];
  reminderRecipientIds?: string[];
};

export type DelegationUpdateRequestDto = {
  agentId?: string | null;
  agentIds?: string[];
  delegationType?: DelegationTypeDto;
  issuingCountry?: string;
  status?: DelegationStatusDto;
  cancelReason?: DelegationCancelReasonDto | null;
  cancelReasonNote?: string | null;
  cancelledAt?: string | null;
  cancellationAnnouncementAt?: string | null;
  issuedAt?: string;
  expiresAt?: string | null;
  notaryNumber?: string | null;
  partnerIds?: string[];
  notes?: string | null;
  reminderOffsets?: number[];
  reminderRecipientIds?: string[];
  starred?: boolean;
};

export type DelegationCaseLinkDto = {
  id: string;
  delegationId: string;
  caseId: string;
  caseNumber: string;
  caseTitle: string;
  createdAt: string;
};

export type DelegationDocumentDto = {
  id: string;
  delegationId: string;
  originalFilename: string;
  contentType: string;
  sizeBytes: number;
  description: string | null;
  documentType: DelegationDocumentTypeDto;
  uploadedBy: Party | null;
  createdAt: string;
  updatedAt: string;
};

export type DelegationActivityDto = {
  id: string;
  actorEmail: string | null;
  action: string;
  metadata: string | null;
  success: boolean;
  createdAt: string;
};

/** UI-only: expires within N days but still ACTIVE */
export type DelegationDisplayStatus = DelegationStatusDto | "EXPIRING_SOON";
