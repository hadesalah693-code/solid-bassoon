/**
 * Aligned with `CaseHistoryEntry` in kalima-backend
 * (`com.kalima.backend.casefile.dto.CaseHistoryEntry`).
 *
 * `metadata` is the raw JSON string written at audit time. The timeline
 * renderer parses it defensively and never treats its absence as an error.
 */
export type CaseHistoryEntry = {
  id: string;
  action: string;
  entityType: string | null;
  entityId: string | null;
  actorPersonId: string | null;
  actorName: string | null;
  actorEmail: string | null;
  metadata: string | null;
  success: boolean;
  createdAt: string;
};
