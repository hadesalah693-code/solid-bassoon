"use client";

import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { motion } from "framer-motion";
import { CheckCircle2, Loader2 } from "lucide-react";

import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { ApiErrorAlert } from "@/components/shared/ApiErrorAlert";
import { PageLoading } from "@/components/shared/PageLoading";
import { RoleBadge } from "@/components/shared/StatusBadge";
import {
  staffUpdateSchema,
  type StaffUpdateFormValues,
} from "@/lib/validation/staff";
import { staffApi } from "@/lib/api/staff";
import { ApiRequestError } from "@/lib/api/client";
import { getStoredUser } from "@/lib/auth/session";
import type { Staff } from "@/types/staff";

function staffToForm(s: Staff): StaffUpdateFormValues {
  return {
    fullName: s.fullName ?? "",
    nationalId: s.nationalId ?? undefined,
    phone: s.phone ?? undefined,
    address: s.address ?? undefined,
    notes: s.notes ?? undefined,
  };
}

export function StaffEditForm({ staffId }: { staffId: string }) {
  const [sessionReady, setSessionReady] = useState(false);
  const [isAdmin, setIsAdmin] = useState(false);
  const [loaded, setLoaded] = useState<Staff | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [loadVersion, setLoadVersion] = useState(0);
  const [success, setSuccess] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);

  useEffect(() => {
    setIsAdmin(getStoredUser()?.systemRole === "ADMIN");
    setSessionReady(true);
  }, []);

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors, isSubmitting },
  } = useForm<StaffUpdateFormValues>({
    resolver: zodResolver(staffUpdateSchema),
    defaultValues: {
      fullName: "",
      nationalId: undefined,
      phone: undefined,
      address: undefined,
      notes: undefined,
    },
  });

  useEffect(() => {
    if (!sessionReady) return;
    let cancelled = false;
    (async () => {
      setLoadError(null);
      try {
        const s = await staffApi.get(staffId);
        if (cancelled) return;
        setLoaded(s);
        reset(staffToForm(s));
      } catch (e) {
        if (cancelled) return;
        if (e instanceof ApiRequestError) {
          setLoadError(e.details?.message ?? e.message);
        } else {
          setLoadError("تعذر تحميل بيانات الموظف");
        }
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [staffId, sessionReady, reset, loadVersion]);

  async function onSubmit(data: StaffUpdateFormValues) {
    if (!isAdmin) return;
    setSubmitError(null);
    try {
      await staffApi.update(staffId, {
        fullName: data.fullName.trim(),
        nationalId: data.nationalId,
        phone: data.phone,
        address: data.address,
        notes: data.notes,
      });
      setSuccess(true);
    } catch (e) {
      if (e instanceof ApiRequestError) {
        setSubmitError(e.details?.message ?? e.message);
      } else {
        setSubmitError("تعذر حفظ التعديلات");
      }
    }
  }

  if (!sessionReady) {
    return (
      <div className="flex justify-center py-12 text-[var(--color-muted-foreground)]">
        <Loader2 className="size-8 animate-spin" aria-hidden />
      </div>
    );
  }

  if (loadError) {
    return (
      <ApiErrorAlert
        message={loadError}
        onRetry={() => setLoadVersion((v) => v + 1)}
      />
    );
  }

  if (!loaded) {
    return (
      <PageLoading
        label="جارٍ تحميل بيانات الموظف…"
        minHeightClassName="min-h-[12rem]"
      />
    );
  }

  if (!isAdmin) {
    return (
      <div className="space-y-4 text-sm text-[var(--color-muted-foreground)]">
        <p>
          تعديل بيانات الموظف متاح لـ{" "}
          <span className="font-medium text-[var(--color-foreground)]">ADMIN</span>{" "}
          فقط — <code className="ltr-nums">PUT /api/staff/{"{id}"}</code>.
        </p>
        <div className="rounded-xl border border-[var(--color-border)] bg-[var(--color-surface-muted)]/50 p-4">
          <p className="font-medium text-[var(--color-foreground)]">
            {loaded.fullName}
          </p>
          <p className="mt-1 ltr-nums text-xs">{loaded.email ?? "—"}</p>
          <div className="mt-2">
            <RoleBadge role={loaded.systemRole} />
          </div>
        </div>
      </div>
    );
  }

  if (success) {
    return (
      <motion.div
        initial={{ opacity: 0, y: 6 }}
        animate={{ opacity: 1, y: 0 }}
        className="rounded-2xl border border-[var(--color-success)]/30 bg-[var(--color-success-bg)]/50 p-8 text-center"
      >
        <CheckCircle2 className="mx-auto size-12 text-[var(--color-success)]" />
        <h3 className="mt-4 text-lg font-semibold text-[var(--color-foreground)]">
          تم حفظ التعديلات
        </h3>
        <Button
          type="button"
          variant="gold"
          className="mt-6"
          onClick={() => setSuccess(false)}
        >
          متابعة التعديل
        </Button>
      </motion.div>
    );
  }

  return (
    <form onSubmit={handleSubmit(onSubmit)} className="space-y-8">
      <div className="rounded-xl border border-[var(--color-border)] bg-[var(--color-surface-muted)]/40 px-4 py-3 text-sm">
        <p className="text-[var(--color-muted-foreground)]">البريد (للقراءة فقط)</p>
        <p className="ltr-nums font-medium text-[var(--color-foreground)]">
          {loaded.email ?? "—"}
        </p>
        <div className="mt-2 flex items-center gap-2">
          <span className="text-[var(--color-muted-foreground)]">الدور:</span>
          <RoleBadge role={loaded.systemRole} />
        </div>
        <p className="mt-2 text-xs text-[var(--color-muted-foreground)]">
          لا يدعم الـ API تغيير البريد أو الدور من هذا المسار.
        </p>
      </div>

      {submitError ? (
        <p className="rounded-xl border border-[var(--color-danger)]/40 bg-[var(--color-danger)]/5 px-4 py-3 text-sm text-[var(--color-danger)]">
          {submitError}
        </p>
      ) : null}

      <div className="space-y-2">
        <Label htmlFor="ed-fullName">
          الاسم الكامل <span className="text-[var(--color-danger)]">*</span>
        </Label>
        <Input
          id="ed-fullName"
          dir="rtl"
          {...register("fullName")}
          aria-invalid={!!errors.fullName}
        />
        {errors.fullName ? (
          <p className="text-xs text-[var(--color-danger)]">
            {errors.fullName.message}
          </p>
        ) : null}
      </div>

      <div className="grid gap-4 sm:grid-cols-2">
        <div className="space-y-2">
          <Label htmlFor="ed-nationalId">الرقم القومي / الهوية</Label>
          <Input
            id="ed-nationalId"
            dir="ltr"
            className="ltr-nums"
            {...register("nationalId")}
          />
        </div>
        <div className="space-y-2">
          <Label htmlFor="ed-phone">الهاتف</Label>
          <Input
            id="ed-phone"
            dir="ltr"
            className="ltr-nums"
            {...register("phone")}
          />
        </div>
      </div>

      <div className="space-y-2">
        <Label htmlFor="ed-address">العنوان</Label>
        <Input id="ed-address" dir="rtl" {...register("address")} />
      </div>

      <div className="space-y-2">
        <Label htmlFor="ed-notes">ملاحظات</Label>
        <Textarea id="ed-notes" dir="rtl" {...register("notes")} />
      </div>

      <div className="flex flex-col gap-3 sm:flex-row sm:justify-end">
        <Button
          type="button"
          variant="outline"
          onClick={() => reset(staffToForm(loaded))}
          disabled={isSubmitting}
        >
          إعادة التعيين
        </Button>
        <Button type="submit" variant="gold" disabled={isSubmitting}>
          {isSubmitting ? "جارٍ الحفظ…" : "حفظ التعديلات"}
        </Button>
      </div>
    </form>
  );
}
