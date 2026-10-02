import { request } from "./client";

/**
 * Result of GET /api/chat/scope/clients — only clients the actor is
 * allowed to chat with. Backend enforces the filter; the frontend
 * just renders.
 */
export type ChatScopeClient = {
  id: string;
  fullName: string | null;
  companyName: string | null;
  email: string | null;
};

/**
 * Result of GET /api/chat/scope/cases — only cases the actor is
 * allowed to chat in. Same authority guarantee as
 * {@link ChatScopeClient}.
 */
export type ChatScopeCase = {
  id: string;
  caseNumber: string;
  title: string;
  status: string;
  archived: boolean;
};

export const chatScopeApi = {
  listClients: () => request<ChatScopeClient[]>("/chat/scope/clients"),
  listCases: (clientId: string) =>
    request<ChatScopeCase[]>("/chat/scope/cases", { query: { clientId } }),
};
