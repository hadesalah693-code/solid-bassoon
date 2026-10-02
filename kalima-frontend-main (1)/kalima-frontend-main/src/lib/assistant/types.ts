import type { AssistantTurn } from "@/lib/api/lawyer-assistant";

export type AssistantMessageRole = "user" | "assistant";

export type AssistantChatMessage = {
  id: string;
  role: AssistantMessageRole;
  content: string;
  createdAt: number;
  contextUsed?: boolean;
  documentsUsed?: boolean;
  provider?: string;
  model?: string;
  toolsUsed?: string[];
  isAgent?: boolean;
  /** Typewriter reveal in progress */
  streaming?: boolean;
};

export type PageAssistantContext = {
  caseId?: string;
  delegationId?: string;
  disputeId?: string;
  documentId?: string;
  pageLabel?: string;
  pageKind?: "case" | "delegation" | "dispute" | "document";
};

export const NO_CASE = "__none__";

export { type AssistantTurn };
