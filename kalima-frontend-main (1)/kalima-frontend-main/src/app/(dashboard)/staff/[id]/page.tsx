import { Pencil } from "lucide-react";

import { PageHeader } from "@/components/shared/PageHeader";
import { SectionCard } from "@/components/shared/SectionCard";
import { StaffEditForm } from "@/components/staff/StaffEditForm";

type PageProps = { params: Promise<{ id: string }> };

export default async function EditStaffPage({ params }: PageProps) {
  const { id } = await params;
  return (
    <div className="mx-auto max-w-3xl space-y-6">
      <PageHeader
        title="تعديل موظف"
        description="تحديث الاسم والتفاصيل الاختيارية — البريد والدور غير قابلين للتغيير عبر هذا المسار."
      />

      <SectionCard
        title="تعديل البيانات"
        description="PUT /api/staff/{id} — لـ ADMIN فقط."
        action={
          <div className="flex size-10 items-center justify-center rounded-xl bg-[var(--color-gold-50)] text-[var(--color-gold-600)]">
            <Pencil className="size-5" />
          </div>
        }
      >
        <StaffEditForm staffId={id} />
      </SectionCard>
    </div>
  );
}
