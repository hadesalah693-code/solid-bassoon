import { UserPlus } from "lucide-react";

import { PageHeader } from "@/components/shared/PageHeader";
import { SectionCard } from "@/components/shared/SectionCard";
import { StaffCreateForm } from "@/components/staff/StaffCreateForm";

export default function NewStaffPage() {
  return (
    <div className="mx-auto max-w-3xl space-y-6">
      <PageHeader
        title="إضافة موظف"
        description="إنشاء حساب موظف بكلمة مرور — يتطلب صلاحية مدير (POST /api/staff)."
      />

      <SectionCard
        title="بيانات الموظف"
        description="البريد مطلوب وفريد؛ باقي الحقول اختيارية حسب الـ API."
        action={
          <div className="flex size-10 items-center justify-center rounded-xl bg-[var(--color-gold-50)] text-[var(--color-gold-600)]">
            <UserPlus className="size-5" />
          </div>
        }
      >
        <StaffCreateForm />
      </SectionCard>
    </div>
  );
}
