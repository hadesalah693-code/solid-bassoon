import type { Party } from "@/types/common";
import type { DisputeTeamMemberDto } from "@/types/dispute";

export type LaborComplaintStatusDto = "ACTIVE" | "SAVED" | "REFERRAL_TO_COMMITTEE";

export type LaborComplaintPartyCapacityDto =
  | "PLAINTIFF"
  | "DEFENDANT"
  | "EMPLOYEE"
  | "EMPLOYER"
  | "OTHER";

export type LaborComplaintClientDto = {
  id?: string;
  client: Party;
  capacity: LaborComplaintPartyCapacityDto | null;
};

export type LaborComplaintOpponentDto = {
  id?: string;
  name: string;
  capacity: LaborComplaintPartyCapacityDto | null;
};

export type LaborComplaintMolSessionDto = {
  id?: string;
  sessionDate: string;
  location: string | null;
  notes: string | null;
};

export type LaborComplaintDocumentDto = {
  id: string;
  originalFilename: string;
  contentType: string;
  sizeBytes: number;
  description: string | null;
  uploadedBy: Party | null;
  createdAt: string;
};

export type LaborComplaintListItemDto = {
  id: string;
  complaintNumber: string;
  complaintYear: number;
  registrationDate: string;
  receiptDate: string;
  subject: string | null;
  status: LaborComplaintStatusDto;
  clientName: string | null;
  clientId: string | null;
  clientNames: string | null;
  assignedLawyerName: string | null;
  opponentName: string | null;
  opponentNames: string | null;
  internalReference: string | null;
  createdAt: string;
  updatedAt: string;
};

export type LaborComplaintResponseDto = {
  id: string;
  complaintNumber: string;
  complaintYear: number;
  registrationDate: string;
  receiptDate: string;
  subject: string | null;
  summary: string | null;
  legalRequest: string | null;
  status: LaborComplaintStatusDto;
  client: Party | null;
  clients: LaborComplaintClientDto[];
  assignedLawyer: Party | null;
  opponentName: string | null;
  opponentCapacity: LaborComplaintPartyCapacityDto | null;
  opponents: LaborComplaintOpponentDto[];
  savedReason: string | null;
  savedAt: string | null;
  referralNotes: string | null;
  referredAt: string | null;
  linkedCaseId: string | null;
  linkedCaseNumber: string | null;
  linkedDisputeId: string | null;
  linkedDisputeTitle: string | null;
  internalReference: string | null;
  externalPartners: string | null;
  opponentRepresentative: string | null;
  cooperatingOffices: string | null;
  linkedDisputeTeam: { members: DisputeTeamMemberDto[] } | null;
  molSessions: LaborComplaintMolSessionDto[];
  createdBy: Party | null;
  createdAt: string;
  updatedAt: string;
};

export type LaborComplaintCreateRequestDto = {
  registrationDate?: string;
  receiptDate?: string;
  complaintYear?: number;
  complaintNumber?: string | null;
  subject?: string | null;
  summary?: string | null;
  legalRequest?: string | null;
  clientId?: string;
  clients?: { clientId: string; capacity?: LaborComplaintPartyCapacityDto | null }[];
  assignedLawyerId?: string | null;
  opponentName?: string | null;
  opponentCapacity?: LaborComplaintPartyCapacityDto | null;
  opponents?: { name: string; capacity?: LaborComplaintPartyCapacityDto | null }[];
  linkedDisputeId?: string | null;
  internalReference?: string | null;
  externalPartners?: string | null;
  opponentRepresentative?: string | null;
  cooperatingOffices?: string | null;
  molSessions?: LaborComplaintMolSessionDto[];
};

export type LaborComplaintUpdateRequestDto = Omit<
  LaborComplaintCreateRequestDto,
  "clientId" | "registrationDate" | "receiptDate"
> & {
  registrationDate?: string;
  receiptDate?: string;
  complaintNumber?: string | null;
};

export type LaborComplaintStatusUpdateRequestDto = {
  status: LaborComplaintStatusDto;
  savedReason?: string | null;
  referralNotes?: string | null;
  linkedCaseId?: string | null;
};
