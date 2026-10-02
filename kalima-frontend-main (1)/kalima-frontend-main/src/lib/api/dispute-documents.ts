import { authenticatedFetch } from "./authenticated-fetch";
import { request } from "./client";
import type {
  CaseDocumentCategoryDto,
  CaseDocumentVisibilityDto,
  DisputeDocumentResponseDto,
} from "@/types/api";

export type DisputeDocumentUploadInput = {
  file: File;
  category: CaseDocumentCategoryDto;
  visibility: CaseDocumentVisibilityDto;
  description?: string | null;
  /** Optional link to a dispute stage (must belong to this dispute). */
  stageId?: string | null;
};

export const disputeDocumentsApi = {
  list: (disputeId: string, stageId?: string | null) =>
    request<DisputeDocumentResponseDto[]>(`/disputes/${disputeId}/documents`, {
      query: stageId ? { stageId } : {},
    }),

  upload: async (disputeId: string, input: DisputeDocumentUploadInput) => {
    const form = new FormData();
    form.set("file", input.file);
    form.set("category", input.category);
    form.set("visibility", input.visibility);
    if (input.description?.trim()) {
      form.set("description", input.description.trim());
    }
    if (input.stageId?.trim()) {
      form.set("stageId", input.stageId.trim());
    }
    const path = `/disputes/${disputeId}/documents`;
    const res = await authenticatedFetch(path, {
      method: "POST",
      headers: { Accept: "application/json" },
      body: form,
    });
    return (await res.json()) as DisputeDocumentResponseDto;
  },

  download: async (disputeId: string, documentId: string) => {
    const path = `/disputes/${disputeId}/documents/${documentId}/download`;
    const res = await authenticatedFetch(path);
    return res.blob();
  },

  remove: (disputeId: string, documentId: string) =>
    request<void>(`/disputes/${disputeId}/documents/${documentId}`, {
      method: "DELETE",
    }),
};
