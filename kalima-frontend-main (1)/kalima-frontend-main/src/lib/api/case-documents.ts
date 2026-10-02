import { authenticatedFetch } from "./authenticated-fetch";
import { request } from "./client";
import type {
  CaseDocumentCategoryDto,
  CaseDocumentResponseDto,
  CaseDocumentVisibilityDto,
} from "@/types/api";

export type CaseDocumentUploadInput = {
  file: File;
  category: CaseDocumentCategoryDto;
  visibility: CaseDocumentVisibilityDto;
  description?: string | null;
};

export const caseDocumentsApi = {
  list: (caseId: string) =>
    request<CaseDocumentResponseDto[]>(`/cases/${caseId}/documents`),

  upload: async (caseId: string, input: CaseDocumentUploadInput) => {
    const form = new FormData();
    form.set("file", input.file);
    form.set("category", input.category);
    form.set("visibility", input.visibility);
    if (input.description?.trim()) {
      form.set("description", input.description.trim());
    }
    const path = `/cases/${caseId}/documents`;
    const res = await authenticatedFetch(path, {
      method: "POST",
      headers: { Accept: "application/json" },
      body: form,
    });
    return (await res.json()) as CaseDocumentResponseDto;
  },

  download: async (caseId: string, documentId: string) => {
    const path = `/cases/${caseId}/documents/${documentId}/download`;
    const res = await authenticatedFetch(path);
    return res.blob();
  },

  remove: (caseId: string, documentId: string) =>
    request<void>(`/cases/${caseId}/documents/${documentId}`, {
      method: "DELETE",
    }),
};
