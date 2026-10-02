"use client";

import Link from "next/link";
import { ExternalLink, Pencil, Trash2, X } from "lucide-react";
import { AnimatePresence, motion } from "framer-motion";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { eventDotClass } from "@/lib/calendar/event-colors";
import { calendarEventTypeLabel, CALENDAR_STATUS_AR } from "@/lib/calendar/event-labels";
import { formatDateTime } from "@/lib/utils/format-date";
import type { CalendarEventDto } from "@/types/calendar";

type Props = {
  event: CalendarEventDto | null;
  open: boolean;
  canEdit: boolean;
  onClose: () => void;
  onEdit?: (event: CalendarEventDto) => void;
  onDelete?: (event: CalendarEventDto) => void;
  deleting?: boolean;
};

export function CalendarEventDetail({
  event,
  open,
  canEdit,
  onClose,
  onEdit,
  onDelete,
  deleting,
}: Props) {
  return (
    <AnimatePresence>
      {open && event ?
        <>
          <motion.button
            type="button"
            className="fixed inset-0 z-40 bg-black/40"
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            aria-label="إغلاق"
            onClick={onClose}
          />
          <motion.div
            role="dialog"
            aria-modal="true"
            className="fixed inset-x-4 top-[10vh] z-50 mx-auto max-w-lg rounded-2xl border border-[var(--color-border)] bg-[var(--color-surface)] p-5 shadow-xl sm:inset-x-auto sm:start-1/2 sm:-translate-x-1/2 rtl:sm:translate-x-1/2"
            initial={{ opacity: 0, y: 16 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: 16 }}
          >
            <div className="flex items-start justify-between gap-3">
              <div className="min-w-0">
                <div className="flex flex-wrap items-center gap-2">
                  <Badge className={eventDotClass(event)}>
                    {calendarEventTypeLabel(event)}
                  </Badge>
                  <Badge tone="navy">{CALENDAR_STATUS_AR[event.status]}</Badge>
                </div>
                <h2 className="mt-2 text-lg font-bold text-[var(--color-foreground)]">
                  {event.title}
                </h2>
              </div>
              <Button variant="ghost" size="icon" onClick={onClose} aria-label="إغلاق">
                <X className="size-4" />
              </Button>
            </div>

            <dl className="mt-4 space-y-2 text-sm">
              <div>
                <dt className="text-[var(--color-muted-foreground)]">البداية</dt>
                <dd className="font-medium ltr-nums">{formatDateTime(event.startAt)}</dd>
              </div>
              {event.endAt ?
                <div>
                  <dt className="text-[var(--color-muted-foreground)]">النهاية</dt>
                  <dd className="font-medium ltr-nums">{formatDateTime(event.endAt)}</dd>
                </div>
              : null}
              {event.clientName ?
                <div>
                  <dt className="text-[var(--color-muted-foreground)]">الموكل</dt>
                  <dd>
                    {event.clientId && event.sourceModule === "DELEGATION" ?
                      <Link
                        href={`/clients/${event.clientId}`}
                        className="font-medium text-[var(--color-navy-700)] hover:underline"
                      >
                        {event.clientName}
                      </Link>
                    : event.clientName}
                  </dd>
                </div>
              : null}
              {event.caseNumber ?
                <div>
                  <dt className="text-[var(--color-muted-foreground)]">القضية</dt>
                  <dd>
                    {event.caseId ?
                      <Link
                        href={`/cases/${event.caseId}`}
                        className="font-medium text-[var(--color-navy-700)] hover:underline"
                      >
                        #{event.caseNumber} — {event.caseTitle}
                      </Link>
                    : `#${event.caseNumber}`}
                  </dd>
                </div>
              : null}
              {event.assignedToName ?
                <div>
                  <dt className="text-[var(--color-muted-foreground)]">المُسند إليه</dt>
                  <dd>{event.assignedToName}</dd>
                </div>
              : null}
              {event.description ?
                <div>
                  <dt className="text-[var(--color-muted-foreground)]">الوصف</dt>
                  <dd className="whitespace-pre-wrap">{event.description}</dd>
                </div>
              : null}
            </dl>

            <div className="mt-5 flex flex-wrap gap-2">
              {event.sourceHref && event.sourceModule === "DELEGATION" ?
                <Button variant="outline" size="sm" asChild>
                  <Link href={event.sourceHref} className="inline-flex items-center gap-1">
                    <ExternalLink className="size-3.5" />
                    فتح التوكيل
                  </Link>
                </Button>
              : event.sourceHref ?
                <Button variant="outline" size="sm" asChild>
                  <Link href={event.sourceHref} className="inline-flex items-center gap-1">
                    <ExternalLink className="size-3.5" />
                    {event.editable ? "فتح المصدر" : "عرض في المصدر"}
                  </Link>
                </Button>
              : null}
              {canEdit && event.editable && onEdit ?
                <Button variant="outline" size="sm" onClick={() => onEdit(event)}>
                  <Pencil className="size-3.5" />
                  تعديل
                </Button>
              : null}
              {canEdit && event.editable && onDelete ?
                <Button
                  variant="destructive"
                  size="sm"
                  disabled={deleting}
                  onClick={() => onDelete(event)}
                >
                  <Trash2 className="size-3.5" />
                  حذف
                </Button>
              : null}
              {!event.editable ?
                <p className="text-xs text-[var(--color-muted-foreground)]">
                  هذا الموعد مُزامَن من وحدة أخرى — عدّله من المصدر الأصلي.
                </p>
              : null}
            </div>
          </motion.div>
        </>
      : null}
    </AnimatePresence>
  );
}
