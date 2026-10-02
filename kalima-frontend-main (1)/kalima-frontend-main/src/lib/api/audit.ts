import { request } from "./client";
import type { PagedResponse } from "@/types/common";
import type { AuditLog } from "@/types/audit";

/** Aligned with `AuditLogResponse` in kalima-backend. */
type AuditLogResponseDto = {
  id: string;
  companyId: string | null;
  actorPersonId: string | null;
  actorEmail: string | null;
  action: string;
  entityType: string | null;
  entityId: string | null;
  ipAddress: string | null;
  userAgent: string | null;
  metadata: string | null;
  success: boolean;
  errorMessage: string | null;
  createdAt: string;
};

function fromDto(d: AuditLogResponseDto): AuditLog {
  return {
    id: d.id,
    companyId: d.companyId,
    actorPersonId: d.actorPersonId,
    actorEmail: d.actorEmail,
    action: d.action,
    entityType: d.entityType,
    entityId: d.entityId,
    ipAddress: d.ipAddress,
    userAgent: d.userAgent,
    metadata: d.metadata,
    success: d.success,
    errorMessage: d.errorMessage,
    createdAt: d.createdAt,
  };
}

export const auditApi = {
  list: async (
    q: {
      page?: number;
      size?: number;
      /** Free-text (backend: `q`) on action, email, IP, metadata, etc. */
      q?: string;
      success?: boolean;
    } = {},
  ): Promise<PagedResponse<AuditLog>> => {
    const p = await request<PagedResponse<AuditLogResponseDto>>("/audit-logs", {
      query: {
        page: q.page ?? 0,
        size: q.size ?? 20,
        ...(q.q?.trim() ? { q: q.q.trim() } : {}),
        ...(q.success !== undefined ? { success: q.success } : {}),
      },
    });
    return { ...p, content: p.content.map(fromDto) };
  },
};
