import { request } from "./client";

export type ChatbotAskRequest = { question: string };

export type ChatbotAskResponse = {
  answer: string;
  /** Backend label, e.g. NEXT_HEARING | CASE_STATUS | DOCUMENTS | LAWYER_HANDOFF */
  matchedIntent: string;
};

export const chatbotApi = {
  /** POST /api/cases/{caseId}/chatbot/ask */
  ask: (caseId: string, body: ChatbotAskRequest) =>
    request<ChatbotAskResponse>(`/cases/${caseId}/chatbot/ask`, {
      method: "POST",
      body,
    }),
};
