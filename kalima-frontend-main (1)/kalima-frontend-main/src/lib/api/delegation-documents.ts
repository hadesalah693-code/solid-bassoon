import { authenticatedFetch } from "./authenticated-fetch";
import { request } from "./client";
import type { DelegationDocumentDto, DelegationDocumentTypeDto } from "@/types/delegation";

export const delegationDocumentsApi = {
  list: (delegationId: string) =>
    request<DelegationDocumentDto[]>(`/delegations/${delegationId}/documents`),

  upload: async (
    delegationId: string,
    file: File,
    description?: string | null,
    documentType?: DelegationDocumentTypeDto,
  ) => {
    const form = new FormData();
    form.set("file", file);
    if (description?.trim()) {
      form.set("description", description.trim());
    }
    if (documentType) {
      form.set("documentType", documentType);
    }
    const res = await authenticatedFetch(`/delegations/${delegationId}/documents`, {
      method: "POST",
      headers: { Accept: "application/json" },
      body: form,
    });
    return (await res.json()) as DelegationDocumentDto;
  },

  attachFromOcr: (delegationId: string, ocrRecordId: string) =>
    request<DelegationDocumentDto>(
      `/delegations/${delegationId}/documents/from-ocr/${ocrRecordId}`,
      { method: "POST" },
    ),

  download: async (delegationId: string, documentId: string) => {
    const res = await authenticatedFetch(
      `/delegations/${delegationId}/documents/${documentId}/download`,
    );
    return res.blob();
  },

  remove: (delegationId: string, documentId: string) =>
    request<void>(`/delegations/${delegationId}/documents/${documentId}`, {
      method: "DELETE",
    }),
};
