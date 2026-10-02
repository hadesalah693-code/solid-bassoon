"use client";

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import {
  Building2,
  ChevronLeft,
  FileSignature,
  Loader2,
  Save,
  Settings2,
  ShieldAlert,
  Users,
} from "lucide-react";
import { z } from "zod";

import { useAuth } from "@/components/auth/auth-provider";
import { DisputeModuleHero } from "@/components/disputes/DisputeModuleHero";
import { ApiErrorAlert } from "@/components/shared/ApiErrorAlert";
import { PageLoading } from "@/components/shared/PageLoading";
import { SectionCard } from "@/components/shared/SectionCard";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { officeSettingsApi } from "@/lib/api/office-settings";
import { ApiRequestError } from "@/lib/api/client";
import { notifyApiError, notifySuccess } from "@/lib/feedback";
import type { OfficeSettingsDto } from "@/types/office-settings";
import type { SystemRole } from "@/types/staff";

const officeSettingsSchema = z.object({
  name: z.string().trim().min(1, "اسم المكتب مطلوب").max(200),
  legalName: z.string().max(250).optional().or(z.literal("")),
  commercialRegister: z.string().max(100).optional().or(z.literal("")),
  taxNumber: z.string().max(100).optional().or(z.literal("")),
  phone: z.string().max(30).optional().or(z.literal("")),
  email: z
    .string()
    .email("البريد الإلكتروني غير صالح")
    .max(150)
    .optional()
    .or(z.literal("")),
  address: z.string().max(2000).optional().or(z.literal("")),
  website: z.string().max(200).optional().or(z.literal("")),
  city: z.string().max(100).optional().or(z.literal("")),
  countryCode: z
    .string()
    .regex(/^[A-Z]{2}$/, "رمز الدولة يجب أن يكون حرفين")
    .default("SA"),
});

type OfficeSettingsFormValues = z.infer<typeof officeSettingsSchema>;

function canEdit(role: SystemRole | null | undefined) {
  return role === "ADMIN";
}

function toFormValues(data: OfficeSettingsDto): OfficeSettingsFormValues {
  return {
    name: data.name,
    legalName: data.legalName ?? "",
    commercialRegister: data.commercialRegister ?? "",
    taxNumber: data.taxNumber ?? "",
    phone: data.phone ?? "",
    email: data.email ?? "",
    address: data.address ?? "",
    website: data.website ?? "",
    city: data.city ?? "",
    countryCode: data.countryCode || "SA",
  };
}

function toUpdatePayload(values: OfficeSettingsFormValues) {
  return {
    name: values.name.trim(),
    legalName: values.legalName?.trim() || null,
    commercialRegister: values.commercialRegister?.trim() || null,
    taxNumber: values.taxNumber?.trim() || null,
    phone: values.phone?.trim() || null,
    email: values.email?.trim() || null,
    address: values.address?.trim() || null,
    website: values.website?.trim() || null,
    city: values.city?.trim() || null,
    countryCode: values.countryCode.trim().toUpperCase(),
  };
}

const QUICK_LINKS = [
  {
    href: "/delegations/settings",
    label: "وكلاء التوكيلات",
    description: "تحديد الوكلاء الافتراضيين في نماذج التوكيل",
    icon: FileSignature,
    roles: ["ADMIN", "SENIOR_LAWYER", "LAWYER", "SECRETARY", "ACCOUNTANT"] as SystemRole[],
  },
  {
    href: "/staff",
    label: "الموظفون",
    description: "إدارة حسابات فريق المكتب",
    icon: Users,
    roles: ["ADMIN", "SECRETARY"] as SystemRole[],
  },
  {
    href: "/audit-logs",
    label: "سجل النشاط",
    description: "مراجعة عمليات النظام داخل المكتب",
    icon: ShieldAlert,
    roles: ["ADMIN"] as SystemRole[],
  },
] as const;

export default function OfficeSettingsPage() {
  const { user } = useAuth();
  const role = user?.systemRole as SystemRole | undefined;
  const editable = canEdit(role);

  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors, isSubmitting, isDirty },
  } = useForm<OfficeSettingsFormValues>({
    resolver: zodResolver(officeSettingsSchema),
    defaultValues: {
      name: "",
      legalName: "",
      commercialRegister: "",
      taxNumber: "",
      phone: "",
      email: "",
      address: "",
      website: "",
      city: "",
      countryCode: "SA",
    },
  });

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await officeSettingsApi.get();
      reset(toFormValues(data));
    } catch (e) {
      setError(e instanceof ApiRequestError ? e.message : "تعذّر تحميل إعدادات المكتب");
    } finally {
      setLoading(false);
    }
  }, [reset]);

  useEffect(() => {
    void load();
  }, [load]);

  async function onSubmit(values: OfficeSettingsFormValues) {
    try {
      const updated = await officeSettingsApi.update(toUpdatePayload(values));
      reset(toFormValues(updated));
      notifySuccess("تم حفظ إعدادات المكتب");
    } catch (e) {
      notifyApiError(e, "تعذّر حفظ إعدادات المكتب");
    }
  }

  const visibleLinks = QUICK_LINKS.filter(
    (link) => !role || link.roles.includes(role),
  );

  if (loading) return <PageLoading label="جاري تحميل إعدادات المكتب..." />;

  return (
    <div className="space-y-6">
      <DisputeModuleHero
        eyebrow="إعدادات المكتب"
        title="ملف المكتب"
        description="بيانات المكتب القانوني التي تظهر في المستندات والتواصل — خاصة بكل مكتب على حدة."
        icon={Settings2}
      />

      {error ? <ApiErrorAlert message={error} onRetry={() => void load()} /> : null}

      <SectionCard
        title="البيانات الأساسية"
        description="معلومات التسجيل والتواصل الخاصة بالمكتب الحالي."
        contentClassName="space-y-5"
      >
        {!editable ? (
          <p className="rounded-xl border border-[var(--color-border)] bg-[var(--color-surface-muted)]/40 px-4 py-3 text-sm text-[var(--color-muted-foreground)]">
            عرض فقط — تعديل إعدادات المكتب متاح لمدير النظام.
          </p>
        ) : null}

        <form className="space-y-5" onSubmit={handleSubmit(onSubmit)}>
          <div className="grid gap-4 sm:grid-cols-2">
            <div className="space-y-2 sm:col-span-2">
              <Label htmlFor="name">اسم المكتب *</Label>
              <Input id="name" {...register("name")} disabled={!editable} />
              {errors.name ? (
                <p className="text-xs text-[var(--color-danger)]">{errors.name.message}</p>
              ) : null}
            </div>

            <div className="space-y-2 sm:col-span-2">
              <Label htmlFor="legalName">الاسم القانوني</Label>
              <Input id="legalName" {...register("legalName")} disabled={!editable} />
            </div>

            <div className="space-y-2">
              <Label htmlFor="commercialRegister">السجل التجاري</Label>
              <Input
                id="commercialRegister"
                {...register("commercialRegister")}
                disabled={!editable}
              />
            </div>

            <div className="space-y-2">
              <Label htmlFor="taxNumber">الرقم الضريبي</Label>
              <Input id="taxNumber" {...register("taxNumber")} disabled={!editable} />
            </div>

            <div className="space-y-2">
              <Label htmlFor="phone">الهاتف</Label>
              <Input id="phone" type="tel" {...register("phone")} disabled={!editable} />
            </div>

            <div className="space-y-2">
              <Label htmlFor="email">البريد الإلكتروني</Label>
              <Input id="email" type="email" {...register("email")} disabled={!editable} />
              {errors.email ? (
                <p className="text-xs text-[var(--color-danger)]">{errors.email.message}</p>
              ) : null}
            </div>

            <div className="space-y-2 sm:col-span-2">
              <Label htmlFor="address">العنوان</Label>
              <Input id="address" {...register("address")} disabled={!editable} />
            </div>

            <div className="space-y-2">
              <Label htmlFor="city">المدينة</Label>
              <Input id="city" {...register("city")} disabled={!editable} />
            </div>

            <div className="space-y-2">
              <Label htmlFor="countryCode">رمز الدولة</Label>
              <Input
                id="countryCode"
                {...register("countryCode")}
                disabled={!editable}
                className="uppercase"
                maxLength={2}
              />
              {errors.countryCode ? (
                <p className="text-xs text-[var(--color-danger)]">{errors.countryCode.message}</p>
              ) : null}
            </div>

            <div className="space-y-2 sm:col-span-2">
              <Label htmlFor="website">الموقع الإلكتروني</Label>
              <Input
                id="website"
                type="url"
                placeholder="https://"
                {...register("website")}
                disabled={!editable}
              />
            </div>
          </div>

          {editable ? (
            <div className="flex justify-end border-t border-[var(--color-border)] pt-4">
              <Button
                type="submit"
                disabled={isSubmitting || !isDirty}
                className="gap-2 bg-[var(--color-gold-400)] text-[var(--color-navy-900)] hover:bg-[var(--color-gold-300)]"
              >
                {isSubmitting ? (
                  <Loader2 className="size-4 animate-spin" aria-hidden />
                ) : (
                  <Save className="size-4" aria-hidden />
                )}
                {isSubmitting ? "جاري الحفظ..." : "حفظ التغييرات"}
              </Button>
            </div>
          ) : null}
        </form>
      </SectionCard>

      {visibleLinks.length > 0 ? (
        <SectionCard
          title="إعدادات فرعية"
          description="إدارة أقسام أخرى مرتبطة بإعدادات المكتب."
          contentClassName="grid gap-3 sm:grid-cols-2 lg:grid-cols-3"
        >
          {visibleLinks.map((link) => {
            const Icon = link.icon;
            return (
              <Link
                key={link.href}
                href={link.href}
                className="group flex flex-col gap-2 rounded-xl border border-[var(--color-border)] bg-[var(--color-surface)] p-4 transition-colors hover:border-[var(--color-gold-400)]/40 hover:bg-[var(--color-surface-muted)]/50"
              >
                <div className="flex items-center justify-between gap-2">
                  <span className="inline-flex size-9 items-center justify-center rounded-lg bg-[var(--color-navy-900)]/5 text-[var(--color-navy-800)] group-hover:bg-[var(--color-gold-400)]/15 group-hover:text-[var(--color-gold-600)]">
                    <Icon className="size-4" aria-hidden />
                  </span>
                  <ChevronLeft
                    className="size-4 text-[var(--color-muted-foreground)] transition-transform group-hover:-translate-x-0.5"
                    aria-hidden
                  />
                </div>
                <div>
                  <p className="font-medium text-[var(--color-foreground)]">{link.label}</p>
                  <p className="mt-1 text-xs leading-relaxed text-[var(--color-muted-foreground)]">
                    {link.description}
                  </p>
                </div>
              </Link>
            );
          })}
        </SectionCard>
      ) : null}

      <div className="flex items-center gap-2 rounded-xl border border-dashed border-[var(--color-border)] px-4 py-3 text-xs text-[var(--color-muted-foreground)]">
        <Building2 className="size-4 shrink-0" aria-hidden />
        <span>كل مكتب يرى ويعدّل بياناته فقط — العزل بين المكاتب مفعّل على مستوى النظام.</span>
      </div>
    </div>
  );
}
