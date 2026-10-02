import { authenticatedFetch } from "./authenticated-fetch";
import { request } from "./client";
import type { PagedResponse } from "@/types/common";
import type {
  OcrRecordDto,
  OcrRecordListDto,
  OcrRecordUpdateRequest,
  OcrRelationInput,
  OcrSearchQuery,
  OcrStatsDto,
} from "@/types/ocr";

export const OCR_PAGE_SIZE = 20;

export type OcrUploadParams = {
  file: File;
  title?: string;
  documentType?: OcrRecordDto["documentType"];
  relations?: OcrRelationInput[];
  onProgress?: (percent: number) => void;
};

export const ocrApi = {
  stats: () => request<OcrStatsDto>("/ocr/stats"),

  search: (q: OcrSearchQuery = {}) =>
    request<PagedResponse<OcrRecordListDto>>("/ocr", {
      query: {
        q: q.q?.trim() || undefined,
        status: q.status,
        documentType: q.documentType,
        uploadedBy: q.uploadedBy,
        from: q.from,
        to: q.to,
        caseId: q.caseId,
        clientId: q.clientId,
        disputeId: q.disputeId,
        taskId: q.taskId,
        documentId: q.documentId,
        page: q.page ?? 0,
        size: q.size ?? OCR_PAGE_SIZE,
        sort: q.sort,
      },
    }),

  getById: (id: string) => request<OcrRecordDto>(`/ocr/${id}`),

  update: (id: string, body: OcrRecordUpdateRequest) =>
    request<OcrRecordDto>(`/ocr/${id}`, { method: "PUT", body }),

  process: (id: string, rerun = false) =>
    request<OcrRecordDto>(`/ocr/${id}/process`, {
      method: "POST",
      query: { rerun },
    }),

  remove: (id: string) =>
    request<void>(`/ocr/${id}`, { method: "DELETE" }),

  upload: async ({
    file,
    title,
    documentType,
    relations,
  }: OcrUploadParams): Promise<OcrRecordDto> => {
    const form = new FormData();
    form.set("file", file);
    if (title?.trim()) form.set("title", title.trim());
    if (documentType) form.set("documentType", documentType);
    if (relations?.length) {
      form.set("relations", JSON.stringify(relations));
    }
    const res = await authenticatedFetch("/ocr/upload", {
      method: "POST",
      headers: { Accept: "application/json" },
      body: form,
    });
    return (await res.json()) as OcrRecordDto;
  },

  downloadOriginal: async (id: string) => {
    const res = await authenticatedFetch(`/ocr/${id}/download`);
    return res.blob();
  },

  downloadText: async (id: string) => {
    const res = await authenticatedFetch(`/ocr/${id}/text/download`);
    return res.blob();
  },
};
