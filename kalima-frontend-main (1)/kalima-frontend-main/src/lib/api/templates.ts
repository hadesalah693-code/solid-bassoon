import { request } from "./client";
import type {
  CreateTemplateRequest,
  TemplateResponse,
  TemplateType,
  UpdateTemplateRequest,
} from "@/types/template";

type TemplateListQuery = {
  type?: TemplateType;
};

/**
 * Backend endpoints (see {@code TemplateController}):
 *   GET    /api/templates           — ADMIN, SENIOR_LAWYER, LAWYER, SECRETARY, ACCOUNTANT
 *   POST   /api/templates           — ADMIN, SENIOR_LAWYER, SECRETARY
 *   GET    /api/templates/{id}      — same as list
 *   PATCH  /api/templates/{id}      — same as create
 *
 * Tenant scoping is handled server-side via the JWT-derived TenantContext;
 * no companyId is ever sent from the client.
 */
export const templatesApi = {
  list: (q: TemplateListQuery = {}) =>
    request<TemplateResponse[]>("/templates", {
      query: { ...(q.type ? { type: q.type } : {}) },
    }),
  get: (id: string) => request<TemplateResponse>(`/templates/${id}`),
  create: (body: CreateTemplateRequest) =>
    request<TemplateResponse>("/templates", { method: "POST", body }),
  update: (id: string, body: UpdateTemplateRequest) =>
    request<TemplateResponse>(`/templates/${id}`, { method: "PATCH", body }),
};
