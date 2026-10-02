import { request } from "./client";
import type {
  CaseParticipantRoleDto,
  ParticipantResponseDto,
} from "@/types/api";

export type ParticipantAddRequestDto = {
  personId: string;
  role: CaseParticipantRoleDto;
  notes?: string | null;
};

export const caseParticipantsApi = {
  list: (caseId: string) =>
    request<ParticipantResponseDto[]>(`/cases/${caseId}/participants`),
  add: (caseId: string, body: ParticipantAddRequestDto) =>
    request<ParticipantResponseDto>(`/cases/${caseId}/participants`, {
      method: "POST",
      body,
    }),
  remove: (caseId: string, participantId: string) =>
    request<void>(`/cases/${caseId}/participants/${participantId}`, {
      method: "DELETE",
    }),
};
