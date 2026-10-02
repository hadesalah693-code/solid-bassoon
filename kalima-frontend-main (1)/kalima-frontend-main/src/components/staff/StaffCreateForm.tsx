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
import { cn } from "@/lib/utils/cn";
import {
  staffCreateSchema,
  staffSystemRoles,
  type StaffCreateFormValues,
} from "@/lib/validation/staff";
import { staffApi } from "@/lib/api/staff";
import { ApiRequestError } from "@/lib/api/client";
import { getStoredUser } from "@/lib/auth/session";
import type { StaffCreateRequest } from "@/types/staff";

const ROLE_LABEL: Record<(typeof staffSystemRoles)[number], string> = {
  ADMIN: "مدير",
  SENIOR_LAWYER: "محامي أول",
  LAWYER: "محامي",
  SECRETARY: "سكرتير",
  ACCOUNTANT: "محاسب",
};

const defaults: StaffCreateFormValues = {
  systemRole: "LAWYER",
  fullName: "",
  email: "",
  password: "",
  passwordConfirm: "",
  nationalId: undefined,
  phone: undefined,
  address: undefined,
  notes: undefined,
};

function toApiBody(data: StaffCreateFormValues): StaffCreateRequest {
  return {
    systemRole: data.systemRole,
    fullName: data.fullName.trim(),
    email: data.email.trim(),
    password: data.password,
    nationalId: data.nationalId?.trim() || undefined,
    phone: data.phone?.trim() || undefined,
    address: data.address?.trim() || undefined,
    notes: data.notes?.trim() || undefined,
  };
}

export function StaffCreateForm() {
  const [sessionReady, setSessionReady] = useState(false);
  const [isAdmin, setIsAdmin] = useState(false);

  useEffect(() => {
    setIsAdmin(getStoredUser()?.systemRole === "ADMIN");
    setSessionReady(true);
  }, []);

  const {
    register,
    handleSubmit,
    watch,
    setValue,
    reset,
    formState: { errors, isSubmitting },
  } = useForm<StaffCreateFormValues>({
    resolver: zodResolver(staffCreateSchema),
    defaultValues: defaults,
  });

  const systemRole = watch("systemRole");
  const [success, setSuccess] = useState<string | null>(null);
  const [submitError, setSubmitError] = useState<string | null>(null);

  async function onSubmit(data: StaffCreateFormValues) {
    setSubmitError(null);
    try {
      const created = await staffApi.create(toApiBody(data));
      setSuccess(created.fullName);
      reset(defaults);
    } catch (e) {
      if (e instanceof ApiRequestError) {
        setSubmitError(e.details?.message ?? e.message);
      } else {
        setSubmitError("تعذر إنشاء الحساب");
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

  if (!isAdmin) {
    return (
      <p className="text-sm text-[var(--color-muted-foreground)]">
        إضافة موظف متاحة لـ{" "}
        <span className="font-medium text-[var(--color-foreground)]">ADMIN</span>{" "}
        فقط — الباك إند:{" "}
        <code className="ltr-nums">POST /api/staff</code> +{" "}
        <code>hasRole(&apos;ADMIN&apos;)</code>.
      </p>
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
          تم إنشاء الموظف
        </h3>
        <p className="mt-2 text-sm text-[var(--color-muted-foreground)]">
          الاسم:{" "}
          <span className="font-medium text-[var(--color-foreground)]">
            {success}
          </span>
        </p>
        <Button
          type="button"
          variant="gold"
          className="mt-6"
          onClick={() => setSuccess(null)}
        >
          إضافة موظف آخر
        </Button>
      </motion.div>
    );
  }

  return (
    <form onSubmit={handleSubmit(onSubmit)} className="space-y-8">
      {submitError ? (
        <p className="rounded-xl border border-[var(--color-danger)]/40 bg-[var(--color-danger)]/5 px-4 py-3 text-sm text-[var(--color-danger)]">
          {submitError}
        </p>
      ) : null}

      <div className="space-y-3">
        <Label>الدور</Label>
        <div className="flex flex-wrap gap-1 rounded-xl border border-[var(--color-border)] bg-[var(--color-surface)] p-1">
          {staffSystemRoles.map((r) => (
            <button
              key={r}
              type="button"
              onClick={() =>
                setValue("systemRole", r, { shouldValidate: true, shouldDirty: true })
              }
              className={cn(
                "whitespace-nowrap rounded-lg px-3 py-1.5 text-xs font-medium transition-colors",
                systemRole === r
                  ? "bg-[var(--color-navy-800)] text-white"
                  : "text-[var(--color-muted-foreground)] hover:text-[var(--color-foreground)]",
              )}
            >
              {ROLE_LABEL[r]}
            </button>
          ))}
        </div>
        <input type="hidden" {...register("systemRole")} />
        {errors.systemRole ? (
          <p className="text-xs text-[var(--color-danger)]">
            {errors.systemRole.message}
          </p>
        ) : null}
      </div>

      <div className="space-y-2">
        <Label htmlFor="st-fullName">
          الاسم الكامل <span className="text-[var(--color-danger)]">*</span>
        </Label>
        <Input
          id="st-fullName"
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

      <div className="space-y-2">
        <Label htmlFor="st-email">
          البريد <span className="text-[var(--color-danger)]">*</span>
        </Label>
        <Input
          id="st-email"
          type="email"
          dir="ltr"
          className="ltr-nums"
          {...register("email")}
          aria-invalid={!!errors.email}
        />
        {errors.email ? (
          <p className="text-xs text-[var(--color-danger)]">
            {errors.email.message}
          </p>
        ) : null}
        <p className="text-xs text-[var(--color-muted-foreground)]">
          البريد فريد عالمياً ولا يُتغيّر لاحقاً (تصميم الـ API الحالي).
        </p>
      </div>

      <div className="grid gap-4 sm:grid-cols-2">
        <div className="space-y-2">
          <Label htmlFor="st-password">
            كلمة المرور <span className="text-[var(--color-danger)]">*</span>
          </Label>
          <Input
            id="st-password"
            type="password"
            dir="ltr"
            autoComplete="new-password"
            {...register("password")}
            aria-invalid={!!errors.password}
          />
          {errors.password ? (
            <p className="text-xs text-[var(--color-danger)]">
              {errors.password.message}
            </p>
          ) : null}
        </div>
        <div className="space-y-2">
          <Label htmlFor="st-password2">تأكيد كلمة المرور *</Label>
          <Input
            id="st-password2"
            type="password"
            dir="ltr"
            autoComplete="new-password"
            {...register("passwordConfirm")}
            aria-invalid={!!errors.passwordConfirm}
          />
          {errors.passwordConfirm ? (
            <p className="text-xs text-[var(--color-danger)]">
              {errors.passwordConfirm.message}
            </p>
          ) : null}
        </div>
      </div>

      <div className="grid gap-4 sm:grid-cols-2">
        <div className="space-y-2">
          <Label htmlFor="st-nationalId">الرقم القومي / الهوية</Label>
          <Input
            id="st-nationalId"
            dir="ltr"
            className="ltr-nums"
            {...register("nationalId")}
          />
        </div>
        <div className="space-y-2">
          <Label htmlFor="st-phone">الهاتف</Label>
          <Input
            id="st-phone"
            dir="ltr"
            className="ltr-nums"
            {...register("phone")}
          />
        </div>
      </div>

      <div className="space-y-2">
        <Label htmlFor="st-address">العنوان</Label>
        <Input id="st-address" dir="rtl" {...register("address")} />
      </div>

      <div className="space-y-2">
        <Label htmlFor="st-notes">ملاحظات</Label>
        <Textarea id="st-notes" dir="rtl" {...register("notes")} />
      </div>

      <div className="flex flex-col gap-3 sm:flex-row sm:justify-end">
        <Button
          type="button"
          variant="outline"
          onClick={() => reset(defaults)}
          disabled={isSubmitting}
        >
          مسح الحقول
        </Button>
        <Button type="submit" variant="gold" disabled={isSubmitting}>
          {isSubmitting ? "جارٍ الحفظ…" : "حفظ الموظف"}
        </Button>
      </div>
    </form>
  );
}
