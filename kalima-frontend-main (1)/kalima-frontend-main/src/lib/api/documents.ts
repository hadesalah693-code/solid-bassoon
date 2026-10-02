import { request } from "./client";
import type {
  DocumentStatus,
  GenerateDocumentRequest,
  GeneratedDocumentResponse,
  UpdateDocumentStatusRequest,
} from "@/types/template";

type DocumentListQuery = {
  status?: DocumentStatus;
  caseId?: string;
  clientId?: string;
};

/**
 * Backend endpoints (see {@code GeneratedDocumentController}):
 *   POST   /api/documents/generate   — ADMIN, SENIOR_LAWYER, LAWYER, SECRETARY
 *   GET    /api/documents            — + ACCOUNTANT
 *   GET    /api/documents/{id}       — + ACCOUNTANT
 *   PATCH  /api/documents/{id}/status — ADMIN, SENIOR_LAWYER, LAWYER, SECRETARY
 *
 * Tenant scoping is enforced server-side; the placeholder rendering pipeline
 * (today / client.name / case.number / company.name) lives in
 * {@code DocumentGenerationService}.
 */
export const documentsApi = {
  list: (q: DocumentListQuery = {}) =>
    request<GeneratedDocumentResponse[]>("/documents", {
      query: {
        ...(q.status ? { status: q.status } : {}),
        ...(q.caseId ? { caseId: q.caseId } : {}),
        ...(q.clientId ? { clientId: q.clientId } : {}),
      },
    }),
  get: (id: string) => request<GeneratedDocumentResponse>(`/documents/${id}`),
  generate: (body: GenerateDocumentRequest) =>
    request<GeneratedDocumentResponse>("/documents/generate", {
      method: "POST",
      body,
    }),
  updateStatus: (id: string, body: UpdateDocumentStatusRequest) =>
    request<GeneratedDocumentResponse>(`/documents/${id}/status`, {
      method: "PATCH",
      body,
    }),
};
