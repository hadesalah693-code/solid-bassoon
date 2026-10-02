import { request } from "./client";
import type {
  CreateDisputePartyRequestDto,
  DisputePartyDto,
  UpdateDisputePartyRequestDto,
} from "@/types/dispute";

export const disputePartiesApi = {
  list: (disputeId: string) =>
    request<DisputePartyDto[]>(`/disputes/${disputeId}/parties`),
  create: (disputeId: string, body: CreateDisputePartyRequestDto) =>
    request<DisputePartyDto>(`/disputes/${disputeId}/parties`, {
      method: "POST",
      body,
    }),
  patch: (
    disputeId: string,
    partyId: string,
    body: UpdateDisputePartyRequestDto,
  ) =>
    request<DisputePartyDto>(`/disputes/${disputeId}/parties/${partyId}`, {
      method: "PATCH",
      body,
    }),
  remove: (disputeId: string, partyId: string) =>
    request<void>(`/disputes/${disputeId}/parties/${partyId}`, {
      method: "DELETE",
    }),
};
