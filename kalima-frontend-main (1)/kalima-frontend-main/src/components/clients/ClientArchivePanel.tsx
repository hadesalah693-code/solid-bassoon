"use client";

import { useEffect, useState } from "react";
import { Archive, Loader2 } from "lucide-react";

import { SectionCard } from "@/components/shared/SectionCard";
import { Button } from "@/components/ui/button";
import { personsApi } from "@/lib/api/persons";
import { ApiRequestError } from "@/lib/api/client";
import { getStoredUser } from "@/lib/auth/session";
import { notifyApiError, notifySuccess } from "@/lib/feedback";

/**
 * Admin-only client archive panel. Mounted below the edit form on
 * `/clients/[id]`. Self-fetches the person once to expose its archived state;
 * stays hidden when the current user is not an ADMIN (matches the backend's
 * `POST /api/persons/{id}/archive` @PreAuthorize rule).
 */
export function ClientArchivePanel({ personId }: { personId: string }) {
  const [isAdmin, setIsAdmin] = useState(false);
  const [archivedAt, setArchivedAt] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [pending, setPending] = useState(false);
  const [loadError, setLoadError] = useState<string | null>(null);

  useEffect(() => {
    setIsAdmin(getStoredUser()?.systemRole === "ADMIN");
  }, []);

  useEffect(() => {
    if (!isAdmin) {
      setLoading(false);
      return;
    }
    let cancelled = false;
    (async () => {
      setLoading(true);
      setLoadError(null);
      try {
        const p = await personsApi.get(personId);
        if (cancelled) return;
        setArchivedAt(p.archivedAt);
      } catch (e) {
        if (cancelled) return;
        if (e instanceof ApiRequestError) {
          setLoadError(e.details?.message ?? e.message);
        } else {
          setLoadError("تعذر قراءة حالة العميل");
        }
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [isAdmin, personId]);

  async function onArchive() {
    if (
      !window.confirm(
        "أرشفة هذا العميل؟ الإجراء عكسي فقط من جهة قاعدة البيانات (لا يوجد زر استرجاع حالياً).",
      )
    ) {
      return;
    }
    setPending(true);
    try {
      const p = await personsApi.archive(personId);
      setArchivedAt(p.archivedAt);
      notifySuccess("تمت أرشفة العميل");
    } catch (e) {
      notifyApiError(e, "تعذر أرشفة العميل");
    } finally {
      setPending(false);
    }
  }

  if (!isAdmin) return null;

  return (
    <SectionCard
      title="إجراءات الإدارة"
      description="POST /api/persons/{id}/archive — متاح للـ ADMIN فقط."
      action={
        <div className="flex size-10 items-center justify-center rounded-xl bg-[var(--color-surface-muted)] text-[var(--color-muted-foreground)]">
          <Archive className="size-5" />
        </div>
      }
    >
      {loading ? (
        <p className="text-sm text-[var(--color-muted-foreground)]">
          جارٍ تحميل الحالة…
        </p>
      ) : loadError ? (
        <p className="text-sm text-[var(--color-danger)]">{loadError}</p>
      ) : archivedAt ? (
        <p className="text-sm text-[var(--color-muted-foreground)]">
          هذا العميل مؤرشف منذ{" "}
          <span className="ltr-nums">{archivedAt.slice(0, 10)}</span>.
        </p>
      ) : (
        <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
          <p className="text-sm text-[var(--color-muted-foreground)]">
            أرشفة العميل تُخفيه من القوائم الافتراضية، ولا تحذف أي قضايا مرتبطة.
          </p>
          <Button
            type="button"
            variant="destructive"
            size="sm"
            onClick={() => void onArchive()}
            disabled={pending}
          >
            {pending ? (
              <Loader2 className="size-4 animate-spin" />
            ) : (
              <Archive className="size-4" />
            )}
            أرشفة العميل
          </Button>
        </div>
      )}
    </SectionCard>
  );
}
