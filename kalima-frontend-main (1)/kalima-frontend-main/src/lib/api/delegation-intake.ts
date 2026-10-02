import { authenticatedFetch } from "./authenticated-fetch";
import { request } from "./client";
import type { DelegationAttorneyMatchDto } from "./delegation-attorneys";
import type { DelegationTypeDto } from "@/types/delegation";
import type { Party } from "@/types/common";
import type { OcrRecordDto } from "@/types/ocr";

export type DelegationIntakeDto = {
  ocrRecordId: string;
  suggestedClientName: string | null;
  suggestedClient: Party | null;
  suggestedAgentName: string | null;
  agentMatch: DelegationAttorneyMatchDto;
  notaryNumber: string | null;
  issuedAt: string | null;
  expiresAt: string | null;
  delegationType: DelegationTypeDto | null;
  issuingCountry: string | null;
  textExcerpt: string | null;
};

export const delegationIntakeApi = {
  upload: async (file: File, title?: string): Promise<OcrRecordDto> => {
    const form = new FormData();
    form.set("file", file);
    if (title?.trim()) form.set("title", title.trim());
    const res = await authenticatedFetch("/delegations/intake/ocr-upload", {
      method: "POST",
      headers: { Accept: "application/json" },
      body: form,
    });
    if (!res.ok) {
      const text = await res.text();
      throw new Error(text || "OCR upload failed");
    }
    return (await res.json()) as OcrRecordDto;
  },

  parseFromOcr: (ocrRecordId: string) =>
    request<DelegationIntakeDto>(`/delegations/intake/from-ocr/${ocrRecordId}`),

  linkOcr: (delegationId: string, ocrRecordId: string) =>
    request<void>(`/delegations/intake/${delegationId}/link-ocr/${ocrRecordId}`, {
      method: "POST",
    }),
};
