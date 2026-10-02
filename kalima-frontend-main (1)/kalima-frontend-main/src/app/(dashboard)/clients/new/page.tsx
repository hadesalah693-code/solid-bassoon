"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { ShieldAlert, UserPlus } from "lucide-react";

import { EmptyState } from "@/components/shared/EmptyState";
import { PageHeader } from "@/components/shared/PageHeader";
import { PageLoading } from "@/components/shared/PageLoading";
import { SectionCard } from "@/components/shared/SectionCard";
import { Button } from "@/components/ui/button";
import { ClientRegistrationForm } from "@/components/clients/ClientRegistrationForm";
import { getStoredUser } from "@/lib/auth/session";
import type { SystemRole } from "@/types/staff";

// Backend: POST /api/persons requires ADMIN or SECRETARY. Guard the page so
// direct URL access by other roles shows an access notice instead of a form
// that would 403 on submit.
const ALLOWED: readonly SystemRole[] = ["ADMIN", "SECRETARY"];

export default function NewClientPage() {
  const [ready, setReady] = useState(false);
  const [allowed, setAllowed] = useState(false);

  useEffect(() => {
    const role = getStoredUser()?.systemRole;
    setAllowed(role ? ALLOWED.includes(role) : false);
    setReady(true);
  }, []);

  if (!ready) {
    return (
      <div className="mx-auto max-w-3xl space-y-6">
        <PageLoading
          label="جارٍ التحقق من الصلاحيات…"
          minHeightClassName="min-h-[12rem]"
        />
      </div>
    );
  }

  if (!allowed) {
    return (
      <div className="mx-auto max-w-3xl space-y-6">
        <PageHeader
          title="تسجيل عميل"
          description="هذه الصفحة تتطلب صلاحية محددة."
        />
        <EmptyState
          icon={ShieldAlert}
          title="ليست لديك صلاحية لإضافة عملاء"
          description="إضافة العملاء متاحة للمدير (ADMIN) والسكرتارية (SECRETARY) فقط — POST /api/persons."
          action={
            <Button variant="outline" asChild>
              <Link href="/clients">العودة لقائمة العملاء</Link>
            </Button>
          }
        />
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-3xl space-y-6">
      <PageHeader
        title="تسجيل عميل"
        description="إضافة عميل جديد (فرد أو شركة) — يُحفَظ عبر POST /api/persons."
      />

      <SectionCard
        title="بيانات العميل"
        description="الحقول المطلوبة مُعلّمة بـ * — باقي الحقول اختيارية."
        action={
          <div className="flex size-10 items-center justify-center rounded-xl bg-[var(--color-gold-50)] text-[var(--color-gold-600)]">
            <UserPlus className="size-5" />
          </div>
        }
      >
        <ClientRegistrationForm />
      </SectionCard>
    </div>
  );
}
