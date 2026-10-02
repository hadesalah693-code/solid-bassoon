import { request } from "./client";
import type { PagedResponse } from "@/types/common";
import type {
  AddDisputeTimelineNoteRequestDto,
  CreateDisputeAdministrativeUpdateRequest,
  CreateDisputeStageRequest,
  DisputeAdministrativeUpdate,
  DisputeCaseLink,
  DisputeCreateRequestDto,
  DisputeListItemDto,
  DisputeResponseDto,
  DisputeStage,
  DisputeStatusDto,
  DisputeTypeDto,
  DisputeUpdateRequestDto,
  DisputeWorkflowStepDto,
  LinkDisputeCaseRequest,
  UpdateDisputeStageRequest,
  DisputeStageActionDto,
  DisputeActionSummaryDto,
  CompleteDisputeStageActionRequestDto,
  CreateDisputeStageActionRequest,
  UpdateDisputeStageActionRequest,
  UpdateDisputeTeamRequestDto,
  DisputeTeamMemberDto,
} from "@/types/dispute";

export type DisputesListQuery = {
  page?: number;
  size?: number;
  status?: DisputeStatusDto;
  type?: DisputeTypeDto;
  assignedLawyerId?: string;
  currentStep?: DisputeWorkflowStepDto;
  search?: string;
};

export const disputesApi = {
  list: (q: DisputesListQuery = {}) =>
    request<PagedResponse<DisputeListItemDto>>("/disputes", {
      query: {
        page: q.page ?? 0,
        size: q.size ?? 20,
        ...(q.status ? { status: q.status } : {}),
        ...(q.type ? { type: q.type } : {}),
        ...(q.assignedLawyerId ? { assignedLawyerId: q.assignedLawyerId } : {}),
        ...(q.currentStep ? { currentStep: q.currentStep } : {}),
        ...(q.search?.trim() ? { search: q.search.trim() } : {}),
      },
    }),

  get: (id: string) => request<DisputeResponseDto>(`/disputes/${id}`),

  create: (body: DisputeCreateRequestDto) =>
    request<DisputeResponseDto>("/disputes", { method: "POST", body }),

  patch: (id: string, body: DisputeUpdateRequestDto) =>
    request<DisputeResponseDto>(`/disputes/${id}`, { method: "PATCH", body }),

  addTimelineNote: (id: string, body: AddDisputeTimelineNoteRequestDto) =>
    request<DisputeResponseDto>(`/disputes/${id}/timeline`, {
      method: "POST",
      body,
    }),

  convertToCase: (id: string) =>
    request<DisputeResponseDto>(`/disputes/${id}/convert-to-case`, {
      method: "POST",
    }),

  listStages: (disputeId: string) =>
    request<DisputeStage[]>(`/disputes/${disputeId}/stages`),

  addStage: (disputeId: string, body: CreateDisputeStageRequest) =>
    request<DisputeStage>(`/disputes/${disputeId}/stages`, {
      method: "POST",
      body,
    }),

  updateStage: (
    disputeId: string,
    stageId: string,
    body: UpdateDisputeStageRequest,
  ) =>
    request<DisputeStage>(`/disputes/${disputeId}/stages/${stageId}`, {
      method: "PATCH",
      body,
    }),

  listLinkedCases: (disputeId: string) =>
    request<DisputeCaseLink[]>(`/disputes/${disputeId}/cases`),

  linkCase: (disputeId: string, body: LinkDisputeCaseRequest) =>
    request<DisputeCaseLink>(`/disputes/${disputeId}/cases`, {
      method: "POST",
      body,
    }),

  unlinkCase: (disputeId: string, caseId: string) =>
    request<void>(`/disputes/${disputeId}/cases/${caseId}`, {
      method: "DELETE",
    }),

  listAdministrativeUpdates: (disputeId: string) =>
    request<DisputeAdministrativeUpdate[]>(
      `/disputes/${disputeId}/updates`,
    ),

  addAdministrativeUpdate: (
    disputeId: string,
    body: CreateDisputeAdministrativeUpdateRequest,
  ) =>
    request<DisputeAdministrativeUpdate>(`/disputes/${disputeId}/updates`, {
      method: "POST",
      body,
    }),

  listStageActions: (disputeId: string, stageId?: string) =>
    request<DisputeStageActionDto[]>(`/disputes/${disputeId}/actions`, {
      query: stageId ? { stageId } : {},
    }),

  listStageActionsForStage: (disputeId: string, stageId: string) =>
    request<DisputeStageActionDto[]>(
      `/disputes/${disputeId}/stages/${stageId}/actions`,
    ),

  getActionSummary: (disputeId: string) =>
    request<DisputeActionSummaryDto>(`/disputes/${disputeId}/actions/summary`),

  addStageAction: (disputeId: string, body: CreateDisputeStageActionRequest) =>
    request<DisputeStageActionDto>(`/disputes/${disputeId}/actions`, {
      method: "POST",
      body,
    }),

  updateStageAction: (
    disputeId: string,
    actionId: string,
    body: UpdateDisputeStageActionRequest,
  ) =>
    request<DisputeStageActionDto>(
      `/disputes/${disputeId}/actions/${actionId}`,
      { method: "PATCH", body },
    ),

  completeStageAction: (
    disputeId: string,
    actionId: string,
    body: CompleteDisputeStageActionRequestDto,
  ) =>
    request<DisputeStageActionDto>(
      `/disputes/${disputeId}/actions/${actionId}/complete`,
      { method: "POST", body },
    ),

  cancelStageAction: (disputeId: string, actionId: string) =>
    request<DisputeStageActionDto>(
      `/disputes/${disputeId}/actions/${actionId}/cancel`,
      { method: "POST" },
    ),

  listTeam: (disputeId: string) =>
    request<DisputeTeamMemberDto[]>(`/disputes/${disputeId}/team`),

  updateTeam: (disputeId: string, body: UpdateDisputeTeamRequestDto) =>
    request<DisputeTeamMemberDto[]>(`/disputes/${disputeId}/team`, {
      method: "PUT",
      body,
    }),
};
