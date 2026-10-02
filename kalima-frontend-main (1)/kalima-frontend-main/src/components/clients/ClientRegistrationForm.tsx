"use client";

import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { motion } from "framer-motion";
import { Building2, CheckCircle2, User } from "lucide-react";

import { ApiErrorAlert } from "@/components/shared/ApiErrorAlert";
import { PageLoading } from "@/components/shared/PageLoading";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { cn } from "@/lib/utils/cn";
import {
  clientRegistrationSchema,
  type ClientRegistrationFormValues,
} from "@/lib/validation/client-registration";
import { personsApi } from "@/lib/api/persons";
import { ApiRequestError } from "@/lib/api/client";
import type { ClientPerson, PersonRequest } from "@/types/person";

const defaults: ClientRegistrationFormValues = {
  personType: "INDIVIDUAL",
  fullName: "",
  companyName: "",
  commercialRegister: undefined,
  nationalId: undefined,
  phone: undefined,
  email: undefined,
  address: undefined,
  notes: undefined,
  createLoginAccount: false,
  password: undefined,
  confirmPassword: undefined,
};

function toPersonRequest(
  data: ClientRegistrationFormValues,
  forUpdate: boolean,
): PersonRequest {
  const req: PersonRequest = {
    personType: data.personType,
    systemRole: forUpdate ? "CLIENT" : null,
    nationalId: data.nationalId,
    commercialRegister: data.commercialRegister,
    phone: data.phone,
    email: data.email,
    address: data.address,
    notes: data.notes,
  };
  if (data.personType === "INDIVIDUAL") {
    req.fullName = data.fullName;
  }
  if (data.personType === "COMPANY") {
    req.companyName = data.companyName;
  }
  if (!forUpdate && data.createLoginAccount && data.password) {
    req.password = data.password;
  }
  return req;
}

function personToFormValues(p: ClientPerson): ClientRegistrationFormValues {
  return {
    personType: p.personType,
    fullName: p.fullName ?? "",
    companyName: p.companyName ?? "",
    commercialRegister: p.commercialRegister ?? undefined,
    nationalId: p.nationalId ?? undefined,
    phone: p.phone ?? undefined,
    email: p.email ?? undefined,
    address: p.address ?? undefined,
    notes: p.notes ?? undefined,
    createLoginAccount: false,
    password: undefined,
    confirmPassword: undefined,
  };
}

export type ClientRegistrationFormProps = {
  mode?: "create" | "edit";
  personId?: string;
};

export function ClientRegistrationForm({
  mode = "create",
  personId,
}: ClientRegistrationFormProps) {
  const isEdit = mode === "edit" && Boolean(personId);

  const {
    register,
    handleSubmit,
    watch,
    setValue,
    reset,
    formState: { errors, isSubmitting },
  } = useForm<ClientRegistrationFormValues>({
    resolver: zodResolver(clientRegistrationSchema),
    defaultValues: defaults,
  });

  const personType = watch("personType");
  const createLoginAccount = watch("createLoginAccount");
  const [success, setSuccess] = useState<string | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [loadVersion, setLoadVersion] = useState(0);
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [loadingPerson, setLoadingPerson] = useState(isEdit);
  const [initialSnapshot, setInitialSnapshot] =
    useState<ClientRegistrationFormValues | null>(null);

  useEffect(() => {
    if (!isEdit || !personId) return;
    let cancelled = false;
    (async () => {
      setLoadingPerson(true);
      setLoadError(null);
      try {
        const p = await personsApi.get(personId);
        if (cancelled) return;
        const values = personToFormValues(p);
        setInitialSnapshot(values);
        reset(values);
      } catch (e) {
        if (cancelled) return;
        if (e instanceof ApiRequestError) {
          setLoadError(e.details?.message ?? e.message);
        } else {
          setLoadError("تعذر تحميل بيانات العميل");
        }
      } finally {
        if (!cancelled) setLoadingPerson(false);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [isEdit, personId, reset, loadVersion]);

  async function onSubmit(data: ClientRegistrationFormValues) {
    setSuccess(null);
    setSubmitError(null);
    try {
      if (isEdit && personId) {
        await personsApi.update(personId, toPersonRequest(data, true));
        setInitialSnapshot(data);
        setSuccess("updated");
      } else {
        await personsApi.create(toPersonRequest(data, false));
        const label =
          data.personType === "INDIVIDUAL"
            ? data.fullName
            : data.companyName ?? "العميل";
        setSuccess(label);
        setInitialSnapshot(null);
        reset(defaults);
      }
    } catch (e) {
      if (e instanceof ApiRequestError) {
        setSubmitError(e.details?.message ?? e.message);
      } else {
        setSubmitError("تعذر حفظ البيانات");
      }
    }
  }

  if (loadError) {
    return (
      <ApiErrorAlert
        message={loadError}
        onRetry={() => setLoadVersion((v) => v + 1)}
      />
    );
  }

  if (loadingPerson) {
    return (
      <PageLoading
        label="جارٍ تحميل بيانات العميل…"
        minHeightClassName="min-h-[12rem]"
      />
    );
  }

  if (success) {
    const isUpdated = success === "updated";
    return (
      <motion.div
        initial={{ opacity: 0, y: 6 }}
        animate={{ opacity: 1, y: 0 }}
        className="rounded-2xl border border-[var(--color-success)]/30 bg-[var(--color-success-bg)]/50 p-8 text-center"
      >
        <CheckCircle2 className="mx-auto size-12 text-[var(--color-success)]" />
        <h3 className="mt-4 text-lg font-semibold text-[var(--color-foreground)]">
          {isUpdated ? "تم حفظ التعديلات" : "تمّ تسجيل العميل"}
        </h3>
        {!isUpdated ? (
          <p className="mt-2 text-sm text-[var(--color-muted-foreground)]">
            الاسم المسجّل:{" "}
            <span className="font-medium text-[var(--color-foreground)]">
              {success}
            </span>
          </p>
        ) : (
          <p className="mt-2 text-sm text-[var(--color-muted-foreground)]">
            تم تحديث بيانات العميل في النظام.
          </p>
        )}
        <Button
          type="button"
          variant="gold"
          className="mt-6"
          onClick={() => setSuccess(null)}
        >
          {isEdit ? "متابعة التعديل" : "تسجيل عميل آخر"}
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

      <section className="space-y-3">
        <Label className="text-[var(--color-foreground)]">نوع العميل</Label>
        <div className="grid gap-3 sm:grid-cols-2">
          <TypeCard
            active={personType === "INDIVIDUAL"}
            onClick={() =>
              setValue("personType", "INDIVIDUAL", {
                shouldValidate: true,
                shouldDirty: true,
              })
            }
            icon={User}
            title="فرد"
            description="شخص طبيعي"
          />
          <TypeCard
            active={personType === "COMPANY"}
            onClick={() =>
              setValue("personType", "COMPANY", {
                shouldValidate: true,
                shouldDirty: true,
              })
            }
            icon={Building2}
            title="شركة"
            description="شخص اعتباري"
          />
        </div>
        <input type="hidden" {...register("personType")} />
      </section>

      {personType === "INDIVIDUAL" ? (
        <div className="space-y-2">
          <Label htmlFor="fullName">
            الاسم الكامل <span className="text-[var(--color-danger)]">*</span>
          </Label>
          <Input
            id="fullName"
            dir="rtl"
            placeholder="مثال: أحمد محمد علي"
            {...register("fullName")}
            aria-invalid={!!errors.fullName}
          />
          {errors.fullName ? (
            <p className="text-xs text-[var(--color-danger)]">
              {errors.fullName.message}
            </p>
          ) : null}
        </div>
      ) : (
        <>
          <div className="space-y-2">
            <Label htmlFor="companyName">
              اسم الشركة <span className="text-[var(--color-danger)]">*</span>
            </Label>
            <Input
              id="companyName"
              dir="rtl"
              placeholder="مثال: شركة النيل للاستيراد"
              {...register("companyName")}
              aria-invalid={!!errors.companyName}
            />
            {errors.companyName ? (
              <p className="text-xs text-[var(--color-danger)]">
                {errors.companyName.message}
              </p>
            ) : null}
          </div>
          <div className="space-y-2">
            <Label htmlFor="commercialRegister">السجل التجاري</Label>
            <Input
              id="commercialRegister"
              dir="ltr"
              className="ltr-nums"
              placeholder="اختياري"
              {...register("commercialRegister")}
            />
          </div>
        </>
      )}

      <div className="grid gap-4 sm:grid-cols-2">
        <div className="space-y-2">
          <Label htmlFor="nationalId">الرقم القومي / الهوية</Label>
          <Input
            id="nationalId"
            dir="ltr"
            className="ltr-nums"
            placeholder="اختياري"
            {...register("nationalId")}
          />
        </div>
        <div className="space-y-2">
          <Label htmlFor="phone">رقم الهاتف</Label>
          <Input
            id="phone"
            dir="ltr"
            className="ltr-nums"
            placeholder="اختياري"
            {...register("phone")}
          />
        </div>
      </div>

      <div className="space-y-2">
        <Label htmlFor="email">البريد الإلكتروني</Label>
        <Input
          id="email"
          type="email"
          dir="ltr"
          placeholder="اختياري"
          {...register("email")}
          aria-invalid={!!errors.email}
        />
        {errors.email ? (
          <p className="text-xs text-[var(--color-danger)]">
            {errors.email.message}
          </p>
        ) : null}
      </div>

      <div className="space-y-2">
        <Label htmlFor="address">العنوان</Label>
        <Input
          id="address"
          dir="rtl"
          placeholder="اختياري"
          {...register("address")}
        />
      </div>

      <div className="space-y-2">
        <Label htmlFor="notes">ملاحظات</Label>
        <Textarea
          id="notes"
          dir="rtl"
          placeholder="تفاصيل إضافية عن العميل…"
          {...register("notes")}
        />
      </div>

      {!isEdit ? (
        <section className="space-y-4 rounded-2xl border border-[var(--color-border)] bg-[var(--color-surface-muted)]/35 p-4">
          <label className="flex cursor-pointer items-center gap-3 text-sm font-medium text-[var(--color-foreground)]">
            <input
              type="checkbox"
              className="size-4 rounded border-[var(--color-border)]"
              {...register("createLoginAccount")}
            />
            إنشاء حساب دخول للعميل
          </label>
          {createLoginAccount ? (
            <div className="grid gap-4 sm:grid-cols-2">
              <div className="space-y-2">
                <Label htmlFor="password">كلمة المرور</Label>
                <Input
                  id="password"
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
                <Label htmlFor="confirmPassword">تأكيد كلمة المرور</Label>
                <Input
                  id="confirmPassword"
                  type="password"
                  dir="ltr"
                  autoComplete="new-password"
                  {...register("confirmPassword")}
                  aria-invalid={!!errors.confirmPassword}
                />
                {errors.confirmPassword ? (
                  <p className="text-xs text-[var(--color-danger)]">
                    {errors.confirmPassword.message}
                  </p>
                ) : null}
              </div>
              <p className="text-xs text-[var(--color-muted-foreground)] sm:col-span-2">
                يستخدم العميل البريد الإلكتروني أعلاه لتسجيل الدخول. بعد ربطه بقضية، سيظهر له فقط ملفاته وقضاياه.
              </p>
            </div>
          ) : null}
        </section>
      ) : null}

      <div className="rounded-xl border border-dashed border-[var(--color-border)] bg-[var(--color-surface-muted)]/50 px-4 py-3 text-xs text-[var(--color-muted-foreground)]">
        {isEdit
          ? "الحفظ يرسل طلباً إلى"
          : "يتم إرسال البيانات إلى"}{" "}
        <code className="rounded bg-[var(--color-surface)] px-1 py-0.5 ltr-nums">
          {isEdit ? "PUT" : "POST"} /api/persons
          {isEdit && personId ? `/${personId}` : ""}
        </code>{" "}
        (للمستخدمين <span className="font-medium">ADMIN</span> /{" "}
        <span className="font-medium">SECRETARY</span>).
      </div>

      <div className="flex flex-col gap-3 sm:flex-row sm:justify-end">
        <Button
          type="button"
          variant="outline"
          onClick={() =>
            reset(isEdit ? (initialSnapshot ?? defaults) : defaults)
          }
          disabled={isSubmitting}
        >
          مسح الحقول
        </Button>
        <Button type="submit" variant="gold" disabled={isSubmitting}>
          {isSubmitting
            ? "جارٍ الحفظ…"
            : isEdit
              ? "حفظ التعديلات"
              : "حفظ العميل"}
        </Button>
      </div>
    </form>
  );
}

function TypeCard({
  active,
  onClick,
  icon: Icon,
  title,
  description,
}: {
  active: boolean;
  onClick: () => void;
  icon: React.ComponentType<{ className?: string }>;
  title: string;
  description: string;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={cn(
        "flex flex-col items-start gap-2 rounded-2xl border px-4 py-4 text-start transition-all",
        active
          ? "border-[var(--color-gold-400)] bg-[var(--color-gold-50)] shadow-[var(--shadow-soft)]"
          : "border-[var(--color-border)] bg-[var(--color-surface)] hover:border-[var(--color-navy-300)]/40 hover:bg-[var(--color-surface-muted)]/60",
      )}
    >
      <Icon
        className={cn(
          "size-5",
          active
            ? "text-[var(--color-gold-600)]"
            : "text-[var(--color-muted-foreground)]",
        )}
      />
      <span className="font-semibold text-[var(--color-foreground)]">
        {title}
      </span>
      <span className="text-xs text-[var(--color-muted-foreground)]">
        {description}
      </span>
    </button>
  );
}
