export type AuditLog = {
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
