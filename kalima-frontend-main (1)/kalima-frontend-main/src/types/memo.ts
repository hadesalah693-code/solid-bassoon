export type MemoType = "DEFENSE_MEMO" | "RESPONSE_MEMO" | "LEGAL_OPINION" | "GENERAL_MEMO";
export type MemoStatus = "DRAFT" | "UNDER_REVIEW" | "APPROVED" | "SENT" | "ARCHIVED";

export type MemoResponse = {
  id: string;
  caseId: string;
  caseNumber: string;
  caseTitle: string;
  title: string;
  memoType: MemoType;
  status: MemoStatus;
  author: { id: string; fullName: string | null; systemRole: string };
  reviewer: { id: string; fullName: string | null; systemRole: string } | null;
  content: string;
  submittedAt: string | null;
  approvedAt: string | null;
  sentAt: string | null;
  createdAt: string;
  updatedAt: string;
};

export type MemoWriteRequest = { title: string; memoType: MemoType; content: string };