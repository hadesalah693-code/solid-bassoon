/**
 * Mirrors the backend Templates / Fillable Forms module DTOs.
 *
 * Source of truth (Java):
 *   com.kalima.backend.template.dto.*
 *   com.kalima.backend.domain.entity.DomainEnums.{TemplateType,DocumentStatus}
 */

export const TEMPLATE_TYPES = [
  "QUOTATION",
  "INVOICE",
  "RECEIPT",
  "LEGAL_SERVICE_CONTRACT",
  "RETAINER_AGREEMENT",
  "POWER_OF_ATTORNEY",
] as const;
export type TemplateType = (typeof TEMPLATE_TYPES)[number];

export const DOCUMENT_STATUSES = [
  "DRAFT",
  "UNDER_REVIEW",
  "APPROVED",
  "SENT",
] as const;
export type DocumentStatus = (typeof DOCUMENT_STATUSES)[number];

/** GET / POST / PATCH /api/templates response shape. */
export type TemplateResponse = {
  id: string;
  name: string;
  type: TemplateType;
  content: string;
  isDefault: boolean;
  createdAt: string;
  updatedAt: string;
};

export type CreateTemplateRequest = {
  name: string;
  type: TemplateType;
  content: string;
  isDefault?: boolean;
};

export type UpdateTemplateRequest = CreateTemplateRequest;

/** GET / POST /api/documents response shape. */
export type GeneratedDocumentResponse = {
  id: string;
  templateId: string | null;
  templateName: string | null;
  templateType: TemplateType | null;
  clientId: string | null;
  clientName: string | null;
  caseId: string | null;
  caseNumber: string | null;
  title: string;
  status: DocumentStatus;
  renderedContent: string;
  createdAt: string;
  updatedAt: string;
};

export type GenerateDocumentRequest = {
  templateId: string;
  clientId?: string | null;
  caseId?: string | null;
  title?: string | null;
};

export type UpdateDocumentStatusRequest = {
  status: DocumentStatus;
};
