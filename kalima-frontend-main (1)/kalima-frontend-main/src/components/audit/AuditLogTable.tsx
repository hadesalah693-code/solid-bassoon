import { CheckCircle2, XCircle } from "lucide-react";

import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { Badge } from "@/components/ui/badge";
import { formatDateTime } from "@/lib/utils/format-date";
import { AUDIT_ACTION_LABELS } from "./audit-actions";
import type { AuditLog } from "@/types/audit";

export function AuditLogTable({ logs }: { logs: AuditLog[] }) {
  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead className="w-[26%] min-w-[220px]">الإجراء</TableHead>
          <TableHead>الفاعل</TableHead>
          <TableHead>العنصر</TableHead>
          <TableHead>الحالة</TableHead>
          <TableHead>IP</TableHead>
          <TableHead className="text-end">الوقت</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {logs.map((log) => (
          <TableRow key={log.id}>
            <TableCell>
              <div className="flex min-w-0 flex-col">
                <span className="truncate font-medium text-[var(--color-foreground)]">
                  {AUDIT_ACTION_LABELS[log.action] ?? log.action}
                </span>
                <code className="mt-0.5 block max-w-full truncate text-[11px] text-[var(--color-muted-foreground)] ltr-nums">
                  {log.action}
                </code>
              </div>
            </TableCell>
            <TableCell className="text-sm">
              {log.actorEmail ?? (
                <span className="text-[var(--color-muted-foreground)]">
                  النظام
                </span>
              )}
            </TableCell>
            <TableCell className="text-sm">
              {log.entityType ? (
                <div className="flex flex-col">
                  <span>{log.entityType}</span>
                  {log.entityId ? (
                    <code className="mt-0.5 text-[11px] text-[var(--color-muted-foreground)] ltr-nums">
                      {log.entityId.slice(0, 8)}…
                    </code>
                  ) : null}
                </div>
              ) : (
                "—"
              )}
            </TableCell>
            <TableCell>
              {log.success ? (
                <Badge tone="success" className="gap-1">
                  <CheckCircle2 className="size-3.5" />
                  ناجحة
                </Badge>
              ) : (
                <Badge tone="danger" className="gap-1">
                  <XCircle className="size-3.5" />
                  فاشلة
                </Badge>
              )}
            </TableCell>
            <TableCell className="ltr-nums text-xs text-[var(--color-muted-foreground)]">
              {log.ipAddress ?? "—"}
            </TableCell>
            <TableCell className="text-end text-xs text-[var(--color-muted-foreground)]">
              {formatDateTime(log.createdAt)}
            </TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );
}
