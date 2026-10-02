import { authenticatedFetch } from "./authenticated-fetch";
import { request } from "./client";
import type { Party } from "@/types/common";

export type DelegationAttorneyMatchType = "EXACT" | "PARTIAL" | "NONE";

export type DelegationAttorneyMatchDto = {
  matched: boolean;
  person: Party | null;
  matchType: DelegationAttorneyMatchType;
};

export type DelegationOfficeAttorneyDto = {
  id: string;
  person: Party;
  active: boolean;
  isDefault: boolean;
  createdAt: string;
  updatedAt: string;
};

export type DelegationOfficeAttorneyCreateInput = {
  personId: string;
  active?: boolean;
  isDefault?: boolean;
};

export type DelegationOfficeAttorneyUpdateInput = {
  active?: boolean;
  isDefault?: boolean;
};

export const delegationAttorneysApi = {
  list: (activeOnly = false) =>
    request<DelegationOfficeAttorneyDto[]>("/delegations/settings/attorneys", {
      query: { activeOnly },
    }),

  autoAgent: async (): Promise<Party | null> => {
    const res = await authenticatedFetch("/delegations/settings/attorneys/auto");
    if (res.status === 204) return null;
    if (!res.ok) {
      const text = await res.text();
      throw new Error(text || "Failed to resolve auto agent");
    }
    return (await res.json()) as Party;
  },

  match: (name: string) =>
    request<DelegationAttorneyMatchDto>("/delegations/settings/attorneys/match", {
      query: { name },
    }),

  create: (body: DelegationOfficeAttorneyCreateInput) =>
    request<DelegationOfficeAttorneyDto>("/delegations/settings/attorneys", {
      method: "POST",
      body,
    }),

  update: (id: string, body: DelegationOfficeAttorneyUpdateInput) =>
    request<DelegationOfficeAttorneyDto>(`/delegations/settings/attorneys/${id}`, {
      method: "PATCH",
      body,
    }),

  remove: (id: string) =>
    request<void>(`/delegations/settings/attorneys/${id}`, { method: "DELETE" }),
};
