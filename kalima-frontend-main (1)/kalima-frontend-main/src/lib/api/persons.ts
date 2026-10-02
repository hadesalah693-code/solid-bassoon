import { request } from "./client";
import type { PagedResponse } from "@/types/common";
import type { ClientPerson, PersonRequest } from "@/types/person";

type PersonListQuery = {
  page?: number;
  size?: number;
  role?: "CLIENT" | "ADMIN" | "LAWYER" | string;
  search?: string;
};

export const personsApi = {
  list: (q: PersonListQuery = {}) =>
    request<PagedResponse<ClientPerson>>("/persons", {
      query: {
        page: q.page ?? 0,
        size: q.size ?? 20,
        ...(q.role ? { role: q.role } : {}),
        ...(q.search?.trim() ? { search: q.search.trim() } : {}),
      },
    }),
  get: (id: string) => request<ClientPerson>(`/persons/${id}`),
  create: (body: PersonRequest) =>
    request<ClientPerson>("/persons", { method: "POST", body }),
  update: (id: string, body: PersonRequest) =>
    request<ClientPerson>(`/persons/${id}`, { method: "PUT", body }),
  /** POST /api/persons/{id}/archive — [ADMIN]. Returns updated person. */
  archive: (id: string) =>
    request<ClientPerson>(`/persons/${id}/archive`, { method: "POST" }),
};
