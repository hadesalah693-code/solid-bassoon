import Link from "next/link";
import { ArrowRight, Briefcase } from "lucide-react";

import { PageHeader } from "@/components/shared/PageHeader";
import { SectionCard } from "@/components/shared/SectionCard";
import { CaseCreateForm } from "@/components/cases/CaseCreateForm";
import { Button } from "@/components/ui/button";

export default function NewCasePage() {
  return (
    <div className="mx-auto max-w-3xl space-y-6">
      <PageHeader
        title="قضية جديدة"
        description="تسجيل قضية وربطها بموكّل — عبر POST /api/cases."
        actions={
          <Button variant="ghost" asChild>
            <Link
              href="/cases"
              className="inline-flex items-center gap-2 text-[var(--color-muted-foreground)]"
            >
              <ArrowRight className="size-4 rotate-180" />
              كل القضايا
            </Link>
          </Button>
        }
      />

      <SectionCard
        title="بيانات القضية"
        description="رقم القضية الداخلي فريد داخل مكتبك. الموكّل يجب أن يكون مسجّلاً مسبقاً."
        action={
          <div className="flex size-10 items-center justify-center rounded-xl bg-[var(--color-gold-50)] text-[var(--color-gold-600)]">
            <Briefcase className="size-5" />
          </div>
        }
      >
        <CaseCreateForm />
      </SectionCard>
    </div>
  );
}
