import { UserPlus } from "lucide-react";

import { ClientDelegationsPanel } from "@/components/delegations/ClientDelegationsPanel";
import { PageHeader } from "@/components/shared/PageHeader";
import { SectionCard } from "@/components/shared/SectionCard";
import { ClientArchivePanel } from "@/components/clients/ClientArchivePanel";
import { ClientRegistrationForm } from "@/components/clients/ClientRegistrationForm";

type PageProps = { params: Promise<{ id: string }> };

export default async function EditClientPage({ params }: PageProps) {
  const { id } = await params;
  return (
    <div className="mx-auto max-w-3xl space-y-6">
      <PageHeader
        title="تعديل بيانات العميل"
        description="تحديث بيانات عميل محفوظ (PUT /api/persons/{id}) — فقط لمن لديه الصلاحية."
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
        <ClientRegistrationForm mode="edit" personId={id} />
      </SectionCard>

      <ClientArchivePanel personId={id} />

      <ClientDelegationsPanel clientId={id} />
    </div>
  );
}
