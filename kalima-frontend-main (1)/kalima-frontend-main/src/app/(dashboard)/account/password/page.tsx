"use client";

import { useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { KeyRound, Loader2 } from "lucide-react";

import { PageHeader } from "@/components/shared/PageHeader";
import { SectionCard } from "@/components/shared/SectionCard";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { staffApi } from "@/lib/api/staff";
import { ApiRequestError } from "@/lib/api/client";
import { notifySuccess } from "@/lib/feedback";
import {
  changeOwnPasswordSchema,
  type ChangeOwnPasswordFormValues,
} from "@/lib/validation/staff";

export default function ChangePasswordPage() {
  const {
    register,
    handleSubmit,
    reset,
    formState: { errors, isSubmitting },
  } = useForm<ChangeOwnPasswordFormValues>({
    resolver: zodResolver(changeOwnPasswordSchema),
    defaultValues: { oldPassword: "", newPassword: "", confirm: "" },
  });
  const [submitError, setSubmitError] = useState<string | null>(null);

  async function onSubmit(data: ChangeOwnPasswordFormValues) {
    setSubmitError(null);
    try {
      await staffApi.changeOwnPassword({
        oldPassword: data.oldPassword,
        newPassword: data.newPassword,
      });
      notifySuccess("تم تغيير كلمة المرور");
      reset({ oldPassword: "", newPassword: "", confirm: "" });
    } catch (e) {
      if (e instanceof ApiRequestError) {
        setSubmitError(e.details?.message ?? e.message);
      } else {
        setSubmitError("تعذر تغيير كلمة المرور");
      }
    }
  }

  return (
    <div className="mx-auto max-w-xl space-y-6">
      <PageHeader
        title="تغيير كلمة المرور"
        description="تحديث كلمة مرور حسابك الخاص (POST /api/staff/me/change-password)."
      />

      <SectionCard
        title="كلمة مرور جديدة"
        description="الحد الأدنى 8 أحرف. لن تتأثر جلسات الأجهزة الأخرى حالياً — سجّل خروج وعود للدخول إن لزم."
        action={
          <div className="flex size-10 items-center justify-center rounded-xl bg-[var(--color-gold-50)] text-[var(--color-gold-600)]">
            <KeyRound className="size-5" />
          </div>
        }
      >
        <form
          onSubmit={handleSubmit(onSubmit)}
          className="space-y-4"
          noValidate
        >
          {submitError ? (
            <p className="rounded-xl border border-[var(--color-danger)]/40 bg-[var(--color-danger)]/5 px-4 py-3 text-sm text-[var(--color-danger)]">
              {submitError}
            </p>
          ) : null}

          <div className="space-y-2">
            <Label htmlFor="cp-old">كلمة المرور الحالية *</Label>
            <Input
              id="cp-old"
              type="password"
              dir="ltr"
              autoComplete="current-password"
              disabled={isSubmitting}
              {...register("oldPassword")}
              aria-invalid={!!errors.oldPassword}
            />
            {errors.oldPassword ? (
              <p className="text-xs text-[var(--color-danger)]">
                {errors.oldPassword.message}
              </p>
            ) : null}
          </div>

          <div className="space-y-2">
            <Label htmlFor="cp-new">كلمة مرور جديدة *</Label>
            <Input
              id="cp-new"
              type="password"
              dir="ltr"
              autoComplete="new-password"
              disabled={isSubmitting}
              {...register("newPassword")}
              aria-invalid={!!errors.newPassword}
            />
            {errors.newPassword ? (
              <p className="text-xs text-[var(--color-danger)]">
                {errors.newPassword.message}
              </p>
            ) : null}
          </div>

          <div className="space-y-2">
            <Label htmlFor="cp-confirm">تأكيد كلمة المرور الجديدة *</Label>
            <Input
              id="cp-confirm"
              type="password"
              dir="ltr"
              autoComplete="new-password"
              disabled={isSubmitting}
              {...register("confirm")}
              aria-invalid={!!errors.confirm}
            />
            {errors.confirm ? (
              <p className="text-xs text-[var(--color-danger)]">
                {errors.confirm.message}
              </p>
            ) : null}
          </div>

          <div className="flex justify-end">
            <Button type="submit" variant="gold" disabled={isSubmitting}>
              {isSubmitting ? (
                <Loader2 className="size-4 animate-spin" />
              ) : (
                "حفظ"
              )}
            </Button>
          </div>
        </form>
      </SectionCard>
    </div>
  );
}
