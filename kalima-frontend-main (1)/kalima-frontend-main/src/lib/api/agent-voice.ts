import { request } from "./client";

/** Transcribed speech or typed command for the voice legal agent. */
export type VoiceAgentRequest = {
  text: string;
  /** When confirming a pending destructive action. */
  pendingActionId?: string;
  confirm?: boolean;
  caseId?: string;
  delegationId?: string;
  disputeId?: string;
};

export type VoiceAgentResponse = {
  reply: string;
  needsConfirmation: boolean;
  pendingActionId: string | null;
  pendingSummary: string | null;
  toolsUsed: string[];
  activeCaseId: string | null;
};

export type NightAgentReport = {
  highRiskCases: NightInsightItem[];
  warnings: NightInsightItem[];
  suggestions: NightInsightItem[];
  autoActions: NightAutoAction[];
};

export type NightInsightItem = {
  code: string;
  message: string;
  caseId: string | null;
  caseNumber: string | null;
};

export type NightAutoAction = {
  action: string;
  detail: string;
  caseId: string | null;
};

export const agentApi = {
  /** POST /api/agent/voice — STT text in, tool execution + Arabic reply out. */
  voice: (body: VoiceAgentRequest) =>
    request<VoiceAgentResponse>("/agent/voice", {
      method: "POST",
      body,
    }),

  /** GET /api/agent/night/report — latest nightly insight report. */
  nightReport: () => request<NightAgentReport>("/agent/night/report"),

  /** POST /api/agent/night/run — admin manual trigger. */
  runNightAgent: () =>
    request<NightAgentReport>("/agent/night/run", { method: "POST" }),
};
