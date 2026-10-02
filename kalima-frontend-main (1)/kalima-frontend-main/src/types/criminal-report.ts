import type { Party } from "@/types/common";

export type CriminalReportStatusDto =
  | "UNDER_INVESTIGATION"
  | "REFERRAL"
  | "ARCHIVED"
  | "FINAL_ARCHIVED";

export type CriminalReportPartyCapacityDto =
  | "PLAINTIFF"
  | "DEFENDANT"
  | "COMPLAINANT"
  | "ACCUSED"
  | "CIVIL_CLAIMANT"
  | "APPELLANT"
  | "APPELLEE"
  | "PETITIONER"
  | "RESPONDENT"
  | "WITNESS"
  | "OTHER";

export type CriminalReportClientDto = {
  id?: string;
  client: Party;
  capacity: CriminalReportPartyCapacityDto | null;
};

export type CriminalReportOpponentDto = {
  id?: string;
  name: string;
  capacity: CriminalReportPartyCapacityDto | null;
};

export type CriminalReportLocationDto = {
  id?: string;
  locationType: string | null;
  locationDescription: string | null;
};

export type CriminalReportInvestigationSessionDto = {
  id?: string;
  sessionDate: string;
  location: string | null;
  notes: string | null;
};

export type CriminalReportListItemDto = {
  id: string;
  reportNumber: string;
  reportYear: number;
  registrationDate: string;
  prosecutionOffice: string | null;
  securityCenter: string | null;
  charge: string | null;
  status: CriminalReportStatusDto;
  clientName: string | null;
  clientId: string | null;
  clientNames: string | null;
  assignedLawyerName: string | null;
  opponentName: string | null;
  opponentNames: string | null;
  createdAt: string;
  updatedAt: string;
};

export type CriminalReportResponseDto = {
  id: string;
  reportNumber: string;
  reportYear: number;
  registrationDate: string;
  incidentDate: string | null;
  prosecutionOffice: string | null;
  securityCenter: string | null;
  charge: string | null;
  subject: string | null;
  summary: string | null;
  legalRequest: string | null;
  status: CriminalReportStatusDto;
  client: Party | null;
  clients: CriminalReportClientDto[];
  assignedLawyer: Party | null;
  opponentName: string | null;
  opponentCapacity: CriminalReportPartyCapacityDto | null;
  opponents: CriminalReportOpponentDto[];
  archiveReason: string | null;
  grievancePeriodDays: number | null;
  grievanceDeadline: string | null;
  archivedAt: string | null;
  finalArchiveReason: string | null;
  decisionIssuer: string | null;
  decisionNumber: string | null;
  decisionReceivedDate: string | null;
  finalDecisionBasis: string | null;
  appealPeriodDays: number | null;
  finalDecisionLevel: string | null;
  finalArchivedAt: string | null;
  linkedCaseId: string | null;
  linkedCaseNumber: string | null;
  linkedDisputeId: string | null;
  linkedDisputeTitle: string | null;
  internalSource: string | null;
  legalConsultant: string | null;
  externalConsultant: string | null;
  partnerNotes: string | null;
  locations: CriminalReportLocationDto[];
  investigationSessions: CriminalReportInvestigationSessionDto[];
  createdBy: Party | null;
  createdAt: string;
  updatedAt: string;
};

export type CriminalReportCreateRequestDto = {
  registrationDate: string;
  reportNumber?: string | null;
  incidentDate?: string | null;
  prosecutionOffice?: string | null;
  securityCenter?: string | null;
  charge?: string | null;
  subject?: string | null;
  summary?: string | null;
  legalRequest?: string | null;
  clientId?: string;
  clients?: { clientId: string; capacity?: CriminalReportPartyCapacityDto | null }[];
  assignedLawyerId?: string | null;
  opponentName?: string | null;
  opponentCapacity?: CriminalReportPartyCapacityDto | null;
  opponents?: { name: string; capacity?: CriminalReportPartyCapacityDto | null }[];
  linkedDisputeId?: string | null;
  internalSource?: string | null;
  legalConsultant?: string | null;
  externalConsultant?: string | null;
  partnerNotes?: string | null;
  locations?: CriminalReportLocationDto[];
  investigationSessions?: CriminalReportInvestigationSessionDto[];
};

export type CriminalReportUpdateRequestDto = Omit<
  CriminalReportCreateRequestDto,
  "clientId" | "registrationDate"
> & {
  registrationDate?: string;
  reportNumber?: string | null;
  clients?: { clientId: string; capacity?: CriminalReportPartyCapacityDto | null }[];
  opponents?: { name: string; capacity?: CriminalReportPartyCapacityDto | null }[];
  locations?: CriminalReportLocationDto[];
};

export type CriminalReportStatusUpdateRequestDto = {
  status: CriminalReportStatusDto;
  archiveReason?: string | null;
  grievancePeriodDays?: number | null;
  grievanceDeadline?: string | null;
  finalArchiveReason?: string | null;
  decisionIssuer?: string | null;
  decisionNumber?: string | null;
  decisionReceivedDate?: string | null;
  finalDecisionBasis?: string | null;
  appealPeriodDays?: number | null;
  finalDecisionLevel?: string | null;
  linkedCaseId?: string | null;
};
