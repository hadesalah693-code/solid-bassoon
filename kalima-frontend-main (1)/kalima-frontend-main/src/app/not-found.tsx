import Link from "next/link";
import { Scale } from "lucide-react";

import { Button } from "@/components/ui/button";

export default function NotFound() {
  return (
    <div className="flex min-h-screen w-full items-center justify-center px-6">
      <div className="max-w-md text-center">
        <div className="mx-auto flex size-14 items-center justify-center rounded-2xl bg-[var(--color-navy-900)] text-[var(--color-gold-400)]">
          <Scale className="size-6" />
        </div>
        <h1 className="mt-6 text-3xl font-bold">الصفحة غير موجودة</h1>
        <p className="mt-2 text-[var(--color-muted-foreground)]">
          يبدو أن الرابط الذي تحاول الوصول إليه لم يعد متاحاً.
        </p>
        <div className="mt-6">
          <Button variant="gold" asChild>
            <Link href="/dashboard">العودة إلى الرئيسية</Link>
          </Button>
        </div>
      </div>
    </div>
  );
}
