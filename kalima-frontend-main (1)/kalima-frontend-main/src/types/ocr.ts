import type { PartySummaryDto } from "@/types/api";

export const OCR_STATUSES = [
  "PENDING",
  "PROCESSING",
  "COMPLETED",
  "FAILED",
  "CANCELLED",
] as const;

export type OcrStatus = (typeof OCR_STATUSES)[number];

export const OCR_DOCUMENT_TYPES = [
  "SCAN",
  "CONTRACT",
  "COURT_FILING",
  "ID",
  "OTHER",
] as const;

export type OcrDocumentType = (typeof OCR_DOCUMENT_TYPES)[number];

export const OCR_RELATION_TYPES = [
  "CASE",
  "DISPUTE",
  "CLIENT",
  "TASK",
  "DOCUMENT",
  "DELEGATION",
] as const;

export type OcrRelationType = (typeof OCR_RELATION_TYPES)[number];

export const OCR_PROVIDERS = ["AZURE_DI", "TIKA", "MANUAL"] as const;

export type OcrProvider = (typeof OCR_PROVIDERS)[number];

export type OcrRelationDto = {
  id: string;
  relationType: OcrRelationType;
  relationId: string;
  createdAt: string;
};

export type OcrRelationInput = {
  relationType: OcrRelationType;
  relationId: string;
};

/** List endpoint - no extracted/edited text bodies. */
export type OcrRecordListDto = {
  id: string;
  title: string;
  originalFilename: string;
  contentType: string;
  sizeBytes: number;
  documentType: OcrDocumentType;
  language: string | null;
  confidenceScore: number | null;
  status: OcrStatus;
  errorMessage: string | null;
  provider: OcrProvider | null;
  uploadedBy: PartySummaryDto;
  processingStartedAt: string | null;
  processingCompletedAt: string | null;
  relations: OcrRelationDto[];
  createdAt: string;
  updatedAt: string;
};

export type OcrRecordDto = OcrRecordListDto & {
  extractedText: string | null;
  editedText: string | null;
  aiMetadata: Record<string, unknown> | null;
};

export type OcrRecordUpdateRequest = {
  title?: string | null;
  editedText?: string | null;
  documentType?: OcrDocumentType | null;
  relations?: OcrRelationInput[] | null;
};

export type OcrActiveUserStat = {
  userId: string;
  fullName: string | null;
  uploadCount: number;
};

export type OcrStatsDto = {
  totalDocuments: number;
  processedToday: number;
  processing: number;
  failed: number;
  successRatePercent: number;
  mostActiveUsers: OcrActiveUserStat[];
};

export type OcrSearchQuery = {
  q?: string;
  status?: OcrStatus;
  documentType?: OcrDocumentType;
  uploadedBy?: string;
  from?: string;
  to?: string;
  caseId?: string;
  clientId?: string;
  disputeId?: string;
  taskId?: string;
  documentId?: string;
  page?: number;
  size?: number;
  sort?: string;
};