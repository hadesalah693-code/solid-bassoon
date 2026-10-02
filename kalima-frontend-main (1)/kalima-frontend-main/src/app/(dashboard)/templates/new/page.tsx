import Link from "next/link";
import { ArrowRight, Scale } from "lucide-react";

import { PageHeader } from "@/components/shared/PageHeader";
import { TemplateCreateForm } from "@/components/templates/TemplateCreateForm";
import { Button } from "@/components/ui/button";

export default function NewTemplatePage() {
  return (
    <div className="space-y-6">
      <PageHeader
        title="محرر النماذج القانونية"
        description="صمِّم نموذجًا احترافيًّا بمتغيّرات ذكية — ستُستبدَل تلقائيًا بيانات العميل والقضية عند توليد المستند."
        actions={
          <Button variant="ghost" asChild>
            <Link
              href="/templates"
              className="inline-flex items-center gap-2 text-[var(--color-muted-foreground)]"
            >
              <ArrowRight className="size-4 rotate-180" />
              كل النماذج
            </Link>
          </Button>
        }
      />

      {/* Info ribbon */}
      <div className="flex items-start gap-3 rounded-xl border border-[var(--color-gold-200)] bg-[var(--color-gold-50)] px-4 py-3">
        <Scale className="mt-0.5 size-4 shrink-0 text-[var(--color-gold-600)]" />
        <p className="text-xs leading-relaxed text-[var(--color-gold-700)]">
          استخدم المتغيّرات الذكية{" "}
          <code className="font-mono text-[10px]">{"{{company.name}}"}</code>،{" "}
          <code className="font-mono text-[10px]">{"{{client.name}}"}</code>،{" "}
          <code className="font-mono text-[10px]">{"{{case.number}}"}</code>،{" "}
          <code className="font-mono text-[10px]">{"{{today}}"}</code> — وسيتولّى
          النظام استبدالها تلقائيًا في كل مستند يُنشأ من هذا النموذج.
        </p>
      </div>

      <TemplateCreateForm />
    </div>
  );
}
