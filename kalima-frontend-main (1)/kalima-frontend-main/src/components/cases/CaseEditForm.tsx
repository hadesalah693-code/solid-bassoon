"use client";

import { useCallback, useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";

import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { cn } from "@/lib/utils/cn";
import { casesApi } from "@/lib/api/cases";
import { staffApi } from "@/lib/api/staff";
import { ApiRequestError } from "@/lib/api/client";
import { notifyApiError, notifySuccess } from "@/lib/feedback";
import { caseFileFromDto } from "@/types/api";
import {
  caseUpdateSchema,
  type CaseUpdateFormInput,
  type CaseUpdateFormValues,
} from "@/lib/validation/case-update";
import type { CaseFile } from "@/types/case";
import type { CaseUpdateRequestDto } from "@/types/api";
import type { Staff } from "@/types/staff";

const selectClassName = cn(
  "flex h-10 w-full cursor-pointer appearance-none rounded-xl border border-[var(--color-border)] bg-[var(--color-surface)] px-3.5 py-2 text-sm text-[var(--color-foreground)] shadow-sm transition-colors",
  "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--color-gold-400)] focus-visible:border-[var(--color-gold-400)]",
  "disabled:cursor-not-allowed disabled:opacity-50",
);

function emptyToNull(s: string | undefined): string | null {
  const t = s?.trim();
  return t ? t : null;
}

function toUpdatePayload(data: CaseUpdateFormValues): CaseUpdateRequestDto {
  return {
    title: data.title.trim(),
    courtCaseNumber: emptyToNull(data.courtCaseNumber),
    description: emptyToNull(data.description),
    caseType: emptyToNull(data.caseType),
    courtName: emptyToNull(data.courtName),
    courtCircuit: emptyToNull(data.courtCircuit),
    procedureStage: emptyToNull(data.procedureStage),
    startDate: emptyToNull(data.startDate),
    closeDate: emptyToNull(data.closeDate),
    assignedLawyerId: data.assignedLawyerId ?? null,
  };
}

function caseToForm(c: CaseFile): CaseUpdateFormInput {
  return {
    title: c.title,
    courtCaseNumber: c.courtCaseNumber ?? "",
    description: c.description ?? "",
    caseType: c.caseType ?? "",
    courtName: c.courtName ?? "",
    courtCircuit: c.courtCircuit ?? "",
    procedureStage: c.procedureStage ?? "",
    startDate: c.startDate ? c.startDate.slice(0, 10) : "",
    closeDate: c.closeDate ? c.closeDate.slice(0, 10) : "",
    assignedLawyerId: c.assignedLawyer?.id ?? "",
  };
}

type CaseEditFormProps = {
  caseData: CaseFile;
  onSaved: (next: CaseFile) => void;
};

export function CaseEditForm({ caseData, onSaved }: CaseEditFormProps) {
  const [lawyers, setLawyers] = useState<Staff[]>([]);
  const [loadStaff, setLoadStaff] = useState(0);
  const archived = Boolean(caseData.archivedAt);

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors, isSubmitting, isDirty },
  } = useForm<CaseUpdateFormInput, unknown, CaseUpdateFormValues>({
    resolver: zodResolver(caseUpdateSchema),
    defaultValues: caseToForm(caseData),
  });

  useEffect(() => {
    reset(caseToForm(caseData));
  }, [caseData, reset]);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const [lawA, lawB] = await Promise.all([
          staffApi.list({ page: 0, size: 200, role: "LAWYER" }),
          staffApi.list({ page: 0, size: 200, role: "SENIOR_LAWYER" }),
        ]);
        if (cancelled) return;
        const byId = new Map<string, Staff>();
        [...lawA.content, ...lawB.content].forEach((s) => {
          if (s.isActive) {
            byId.set(s.id, s);
          }
        });
        setLawyers([...byId.values()].sort((a, b) =>
          a.fullName.localeCompare(b.fullName, "ar"),
        ));
      } catch {
        if (!cancelled) setLawyers([]);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [loadStaff]);

  const onSubmit = useCallback(
    async (data: CaseUpdateFormValues) => {
      if (archived) return;
      try {
        const dto = toUpdatePayload(data);
        const next = await casesApi.update(caseData.id, dto);
        onSaved(caseFileFromDto(next));
        notifySuccess("تم حفظ بيانات القضية");
      } catch (e) {
        if (e instanceof ApiRequestError) {
          notifyApiError(e, e.details?.message ?? e.message);
        } else {
          notifyApiError(e, "تعذر حفظ التعديلات");
        }
      }
    },
    [archived, caseData.id, onSaved],
  );

  if (archived) {
    return (
      <p className="text-sm text-[var(--color-muted-foreground)]">
        القضية مؤرشفة — لا يمكن تعديل بيانات الملف. استرجعها من الإدارة إن
        وُفّر ذلك.
      </p>
    );
  }

  return (
    <form onSubmit={handleSubmit(onSubmit)} className="space-y-4" noValidate>
      <div className="grid gap-4 sm:grid-cols-2">
        <div className="space-y-2 sm:col-span-2">
          <Label htmlFor="edit-title">عنوان القضية *</Label>
          <Input id="edit-title" disabled={isSubmitting} {...register("title")} />
          {errors.title ? (
            <p className="text-sm text-[var(--color-danger)]">
              {errors.title.message}
            </p>
          ) : null}
        </div>
        <div className="space-y-2">
          <Label htmlFor="edit-courtCase">رقم القضية (المحكمة)</Label>
          <Input
            id="edit-courtCase"
            disabled={isSubmitting}
            {...register("courtCaseNumber")}
          />
        </div>
        <div className="space-y-2">
          <Label htmlFor="edit-caseType">نوع القضية</Label>
          <Input id="edit-caseType" disabled={isSubmitting} {...register("caseType")} />
        </div>
        <div className="space-y-2 sm:col-span-2">
          <Label htmlFor="edit-courtName">اسم المحكمة</Label>
          <Input id="edit-courtName" disabled={isSubmitting} {...register("courtName")} />
        </div>
        <div className="space-y-2">
          <Label htmlFor="edit-circuit">الدائرة</Label>
          <Input id="edit-circuit" disabled={isSubmitting} {...register("courtCircuit")} />
        </div>
        <div className="space-y-2">
          <Label htmlFor="edit-stage">مرحلة الإجراء</Label>
          <Input
            id="edit-stage"
            disabled={isSubmitting}
            {...register("procedureStage")}
          />
        </div>
        <div className="space-y-2 sm:col-span-2">
          <Label htmlFor="edit-desc">وصف</Label>
          <Textarea
            id="edit-desc"
            rows={3}
            className="resize-y"
            disabled={isSubmitting}
            {...register("description")}
          />
        </div>
        <div className="space-y-2">
          <Label htmlFor="edit-start">تاريخ البداية</Label>
          <Input
            id="edit-start"
            type="date"
            disabled={isSubmitting}
            {...register("startDate")}
          />
        </div>
        <div className="space-y-2">
          <Label htmlFor="edit-close">تاريخ الإغلاق</Label>
          <Input
            id="edit-close"
            type="date"
            disabled={isSubmitting}
            {...register("closeDate")}
          />
        </div>
        <div className="space-y-2 sm:col-span-2">
          <Label htmlFor="edit-lawyer">المحامي المسؤول</Label>
          <select
            id="edit-lawyer"
            className={selectClassName}
            disabled={isSubmitting}
            {...register("assignedLawyerId")}
          >
            <option value="">— بدون —</option>
            {lawyers.map((l) => (
              <option key={l.id} value={l.id}>
                {l.fullName}{" "}
                {l.systemRole === "SENIOR_LAWYER" ? "(مستشار)" : "(محامٍ)"}
              </option>
            ))}
          </select>
        </div>
      </div>
      <div className="flex items-center gap-2">
        <Button type="submit" variant="gold" disabled={isSubmitting || !isDirty}>
          {isSubmitting ? "جارٍ الحفظ…" : "حفظ التعديلات"}
        </Button>
        {lawyers.length === 0 ? (
          <Button
            type="button"
            variant="ghost"
            size="sm"
            onClick={() => setLoadStaff((n) => n + 1)}
          >
            إعادة تحميل قائمة المحامين
          </Button>
        ) : null}
      </div>
    </form>
  );
}
