"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import Link from "next/link";
import { ArrowRight } from "lucide-react";

import { useAuth } from "@/components/auth/auth-provider";
import { OcrDropzoneUploader } from "@/components/ocr/OcrDropzoneUploader";
import { ApiErrorAlert } from "@/components/shared/ApiErrorAlert";
import { PageHeader } from "@/components/shared/PageHeader";
import { SectionCard } from "@/components/shared/SectionCard";
import { Button } from "@/components/ui/button";
import { ocrApi } from "@/lib/api/ocr";
import { ApiRequestError } from "@/lib/api/client";
import { notifyApiError, notifySuccess } from "@/lib/feedback";
import type { SystemRole } from "@/types/staff";

function canUpload(role: SystemRole | null | undefined) {
  return role === "ADMIN" || role === "SENIOR_LAWYER" || role === "LAWYER";
}

export default function OcrUploadPage() {
  const router = useRouter();
  const { user } = useAuth();
  const role = user?.systemRole as SystemRole | undefined;
  const [uploading, setUploading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  if (!canUpload(role)) {
    return (
      <div className="space-y-4">
        <PageHeader title="رفع مستند OCR" />
        <ApiErrorAlert message="ليس لديك صلاحية رفع مستندات OCR." />
        <Button asChild variant="outline">
          <Link href="/ocr">العودة</Link>
        </Button>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <PageHeader
        title="رفع مستند OCR"
        description="ارفع PDF أو صورة لاستخراج النص تلقائياً."
        actions={
          <Button asChild variant="outline" size="sm">
            <Link href="/ocr">
              <ArrowRight className="size-4" aria-hidden />
              لوحة OCR
            </Link>
          </Button>
        }
      />

      {error ? <ApiErrorAlert message={error} onRetry={() => setError(null)} /> : null}

      <SectionCard title="رفع ملف">
        <OcrDropzoneUploader
          uploading={uploading}
          onUpload={async ({ file, title, documentType, relations }) => {
            setUploading(true);
            setError(null);
            try {
              const created = await ocrApi.upload({
                file,
                title,
                documentType,
                relations,
              });
              notifySuccess("تم رفع المستند");
              router.push(`/ocr/${created.id}`);
            } catch (e) {
              const msg =
                e instanceof ApiRequestError
                  ? (e.details?.message ?? e.message)
                  : "تعذّر رفع المستند";
              setError(msg);
              notifyApiError(e, "تعذّر رفع المستند");
            } finally {
              setUploading(false);
            }
          }}
        />
      </SectionCard>
    </div>
  );
}
