"use client";

import { useCallback, useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import Link from "next/link";
import { Briefcase } from "lucide-react";

import { ApiErrorAlert } from "@/components/shared/ApiErrorAlert";
import { PageLoading } from "@/components/shared/PageLoading";
import { SearchInput } from "@/components/shared/SearchInput";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { cn } from "@/lib/utils/cn";
import { casesApi } from "@/lib/api/cases";
import { personsApi } from "@/lib/api/persons";
import { staffApi } from "@/lib/api/staff";
import { ApiRequestError } from "@/lib/api/client";
import { getStoredUser } from "@/lib/auth/session";
import { notifyApiError, notifySuccess } from "@/lib/feedback";
import { useDebounce } from "@/hooks/use-debounce";
import {
  caseCreateSchema,
  type CaseCreateFormInput,
  type CaseCreateFormValues,
} from "@/lib/validation/case-create";
import type { CaseCreateRequestDto, DisputePathTypeDto } from "@/types/api";
import type { CasePriority, CaseStatus } from "@/types/case";
import type { ClientPerson } from "@/types/person";
import type { Staff } from "@/types/staff";

const selectClassName = cn(
  "flex h-10 w-full cursor-pointer appearance-none rounded-xl border border-[var(--color-border)] bg-[var(--color-surface)] px-3.5 py-2 text-sm text-[var(--color-foreground)] shadow-sm transition-colors",
  "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--color-gold-400)] focus-visible:border-[var(--color-gold-400)]",
  "disabled:cursor-not-allowed disabled:opacity-50",
);

const STATUS_OPTIONS: { value: CaseStatus; label: string }[] = [
  { value: "NEW", label: "جديد" },
  { value: "IN_PROGRESS", label: "قيد المعالجة" },
  { value: "ON_HOLD", label: "مُعلّق" },
  { value: "CLOSED", label: "مُغلق" },
  { value: "ARCHIVED", label: "مؤرشف" },
];

const DISPUTE_PATH_SELECT_OPTIONS: {
  value: "" | DisputePathTypeDto;
  label: string;
}[] = [
  { value: "", label: "بدون توليد تلقائي" },
  { value: "CIVIL", label: "مدني" },
  { value: "COMMERCIAL", label: "تجاري" },
  { value: "LABOR", label: "عمالي" },
  { value: "CRIMINAL", label: "جنائي" },
  { value: "ADMINISTRATIVE", label: "إداري" },
  { value: "ARBITRATION", label: "تحكيم" },
  { value: "CUSTOM", label: "مخصص" },
];

/** Short preview shown when user picks a path (not «بدون توليد»). */
const DISPUTE_PATH_PREVIEW: Record<DisputePathTypeDto, string> = {
  CIVIL:
    "سيتم إنشاء مراحل مثل: فتح القضية، إنذار، رفع الدعوى، الجلسة الأولى، الحكم، الاستئناف.",
  COMMERCIAL:
    "سيتم إنشاء مراحل مثل: فتح الملف التجاري، مطالبة تجارية، رفع الدعوى التجارية، مستندات ومذكرات، حكم تجاري.",
  LABOR:
    "سيتم إنشاء مراحل مثل: فتح النزاع العمالي، شكوى أو مطالبة، رفع الدعوى العمالية، مستندات العمل، الحكم.",
  CRIMINAL:
    "سيتم إنشاء مراحل مثل: فتح الملف الجنائي، بلاغ / محضر، جلسة أولى، دفاع ومذكرات، حكم.",
  ADMINISTRATIVE:
    "سيتم إنشاء مراحل مثل: تظلم إداري، رفع الدعوى الإدارية، أول جلسة، مذكرات ومستندات، حكم إداري.",
  ARBITRATION:
    "سيتم إنشاء مراحل مثل: إخطار التحكيم، تسجيل طلب التحكيم، جلسة إجرائية، تبادل مذكرات، حكم التحكيم.",
  CUSTOM:
    "لن يتم توليد مراحل تلقائية. يمكنك إضافتها يدويًا من تبويب مسار النزاع.",
};

function clientLabel(p: ClientPerson) {
  if (p.personType === "COMPANY") {
    return p.companyName?.trim() || p.id;
  }
  return p.fullName?.trim() || p.id;
}

function toCreatePayload(data: CaseCreateFormValues): CaseCreateRequestDto {
  const body: CaseCreateRequestDto = {
    caseNumber: data.caseNumber.trim(),
    title: data.title.trim(),
    clientId: data.clientId,
    status: data.status,
    priority: data.priority,
  };
  if (!body.caseNumber) delete body.caseNumber;
  const court = data.courtCaseNumber?.trim();
  if (court) {
    body.courtCaseNumber = court;
  }
  const desc = data.description?.trim();
  if (desc) {
    body.description = desc;
  }
  const ct = data.caseType?.trim();
  if (ct) {
    body.caseType = ct;
  }
  const cn = data.courtName?.trim();
  if (cn) {
    body.courtName = cn;
  }
  const cct = data.courtCircuit?.trim();
  if (cct) {
    body.courtCircuit = cct;
  }
  const ps = data.procedureStage?.trim();
  if (ps) {
    body.procedureStage = ps;
  }
  const sd = data.startDate?.trim();
  if (sd) {
    body.startDate = sd;
  }
  if (data.assignedLawyerId) {
    body.assignedLawyerId = data.assignedLawyerId;
  }
  if (data.disputePathType.trim()) {
    body.disputePathType = data.disputePathType.trim() as DisputePathTypeDto;
  }
  return body;
}

const defaultForm: CaseCreateFormInput = {
  caseNumber: "",
  courtCaseNumber: "",
  title: "",
  description: "",
  caseType: "",
  courtName: "",
  courtCircuit: "",
  procedureStage: "",
  status: "NEW",
  startDate: "",
  clientId: "",
  assignedLawyerId: "",
  disputePathType: "",
  priority: "MEDIUM",
};

export function CaseCreateForm() {
  const router = useRouter();
  const [clientSearch, setClientSearch] = useState("");
  const debouncedClientSearch = useDebounce(clientSearch, 300);
  const [clients, setClients] = useState<ClientPerson[]>([]);
  const [lawyers, setLawyers] = useState<Staff[]>([]);
  const [loadingOptions, setLoadingOptions] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [optionsFetch, setOptionsFetch] = useState(0);
  // POST /api/persons requires [ADMIN, SECRETARY]; hide the inline link otherwise.
  const [canCreateClient, setCanCreateClient] = useState(false);
  useEffect(() => {
    const r = getStoredUser()?.systemRole;
    setCanCreateClient(r === "ADMIN" || r === "SECRETARY");
  }, []);

  const {
    register,
    watch,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<CaseCreateFormInput, unknown, CaseCreateFormValues>({
    resolver: zodResolver(caseCreateSchema),
    defaultValues: defaultForm,
  });

  const loadOptions = useCallback(async () => {
    setLoadingOptions(true);
    setLoadError(null);
    try {
      const [clientsRes, lawA, lawB] = await Promise.all([
        personsApi.list({
          page: 0,
          size: 100,
          role: "CLIENT",
          search: debouncedClientSearch.trim() || undefined,
        }),
        staffApi.list({ page: 0, size: 200, role: "LAWYER" }),
        staffApi.list({ page: 0, size: 200, role: "SENIOR_LAWYER" }),
      ]);
      setClients(clientsRes.content.filter((c) => c.active));
      const byId = new Map<string, Staff>();
      [...lawA.content, ...lawB.content].forEach((s) => {
        if (s.isActive) {
          byId.set(s.id, s);
        }
      });
      setLawyers([...byId.values()].sort((a, b) =>
        a.fullName.localeCompare(b.fullName, "ar"),
      ));
    } catch (e) {
      if (e instanceof ApiRequestError) {
        setLoadError(e.details?.message ?? e.message);
      } else {
        setLoadError("تعذر تحميل قوائم الاختيار");
      }
      setClients([]);
      setLawyers([]);
    } finally {
      setLoadingOptions(false);
    }
  }, [debouncedClientSearch]);

  useEffect(() => {
    void loadOptions();
  }, [loadOptions, optionsFetch]);

  const disputePathSelection = watch("disputePathType");
  const disputePathPreview =
    disputePathSelection && disputePathSelection !== ""
      ? DISPUTE_PATH_PREVIEW[disputePathSelection as DisputePathTypeDto]
      : null;

  async function onSubmit(data: CaseCreateFormValues) {
    try {
      const created = await casesApi.create(toCreatePayload(data));
      notifySuccess("تمت إضافة القضية", `رقم #${created.caseNumber}`);
      if (created.disputePathType) {
        router.push(`/cases/${created.id}?section=disputePath`);
      } else {
        router.push(`/cases/${created.id}`);
      }
    } catch (e) {
      notifyApiError(e, "تعذر حفظ القضية");
    }
  }

  if (loadError) {
    return (
      <ApiErrorAlert
        message={loadError}
        onRetry={() => {
          setLoadError(null);
          setOptionsFetch((n) => n + 1);
        }}
      />
    );
  }

  const activeClientsEmpty =
    !loadingOptions && clients.length === 0 && !debouncedClientSearch.trim();

  return (
    <form
      onSubmit={handleSubmit(onSubmit)}
      className="space-y-6"
      noValidate
    >
      {loadingOptions ? (
        <PageLoading
          label="جارٍ تحميل العملاء والمحامين…"
          minHeightClassName="min-h-[8rem]"
        />
      ) : null}

      <div className="grid gap-5 sm:grid-cols-2">
        <div className="space-y-2 sm:col-span-2">
          <Label htmlFor="clientId">الموكّل *</Label>
          <p className="text-xs text-[var(--color-muted-foreground)]">
            ابحث لتضييق القائمة — يجب أن يكون لدى العميل دور «عميل» في النظام.
            {canCreateClient ? (
              <>
                {" "}
                <Link
                  href="/clients/new"
                  className="font-medium text-[var(--color-gold-600)] underline-offset-2 hover:underline"
                >
                  تسجيل عميل جديد
                </Link>
              </>
            ) : null}
          </p>
          <SearchInput
            value={clientSearch}
            onChange={setClientSearch}
            placeholder="ابحث بالاسم…"
            className="mb-2 max-w-md"
            disabled={loadingOptions}
          />
          <select
            id="clientId"
            className={selectClassName}
            disabled={loadingOptions}
            {...register("clientId")}
          >
            <option value="">— اختر الموكّل —</option>
            {clients.map((c) => (
              <option key={c.id} value={c.id}>
                {clientLabel(c)}
              </option>
            ))}
          </select>
          {errors.clientId ? (
            <p className="text-sm text-[var(--color-danger)]">
              {errors.clientId.message}
            </p>
          ) : null}
        </div>

        <div className="space-y-2">
          <Label htmlFor="caseNumber">رقم القضية (داخلي، اختياري)</Label>
          <Input
            id="caseNumber"
            autoComplete="off"
            disabled={isSubmitting}
            {...register("caseNumber")}
          />
          {errors.caseNumber ? (
            <p className="text-sm text-[var(--color-danger)]">
              {errors.caseNumber.message}
            </p>
          ) : null}
          <p className="text-xs text-[var(--color-muted-foreground)]">
            يُنشأ رقم تلقائي عند ترك الحقل فارغًا.
          </p>
        </div>

        <div className="space-y-2">
          <Label htmlFor="priority">الأولوية</Label>
          <select id="priority" className={selectClassName} disabled={isSubmitting} {...register("priority")}>
            {([
              ["LOW", "منخفضة"],
              ["MEDIUM", "متوسطة"],
              ["HIGH", "عالية"],
              ["URGENT", "عاجلة"],
            ] as [CasePriority, string][]).map(([value, label]) => (
              <option key={value} value={value}>{label}</option>
            ))}
          </select>
        </div>

        <div className="space-y-2">
          <Label htmlFor="courtCaseNumber">رقم القضية (المحكمة)</Label>
          <Input
            id="courtCaseNumber"
            autoComplete="off"
            disabled={isSubmitting}
            {...register("courtCaseNumber")}
          />
        </div>

        <div className="space-y-2 sm:col-span-2">
          <Label htmlFor="title">عنوان القضية *</Label>
          <Input
            id="title"
            autoComplete="off"
            disabled={isSubmitting}
            {...register("title")}
          />
          {errors.title ? (
            <p className="text-sm text-[var(--color-danger)]">
              {errors.title.message}
            </p>
          ) : null}
        </div>

        <div className="space-y-2 sm:col-span-2">
          <Label htmlFor="description">وصف (اختياري)</Label>
          <Textarea
            id="description"
            rows={3}
            disabled={isSubmitting}
            className="resize-y"
            {...register("description")}
          />
        </div>

        <div className="space-y-2">
          <Label htmlFor="caseType">نوع القضية (اختياري)</Label>
          <Input
            id="caseType"
            autoComplete="off"
            disabled={isSubmitting}
            {...register("caseType")}
          />
        </div>

        <div className="space-y-2 sm:col-span-2">
          <Label htmlFor="courtName">اسم المحكمة (اختياري)</Label>
          <Input
            id="courtName"
            autoComplete="off"
            placeholder="مثال: محكمة شمال القاهرة الابتدائية"
            disabled={isSubmitting}
            {...register("courtName")}
          />
        </div>

        <div className="space-y-2">
          <Label htmlFor="courtCircuit">الدائرة (اختياري)</Label>
          <Input
            id="courtCircuit"
            autoComplete="off"
            disabled={isSubmitting}
            {...register("courtCircuit")}
          />
        </div>

        <div className="space-y-2">
          <Label htmlFor="procedureStage">مرحلة الإجراء (اختياري)</Label>
          <Input
            id="procedureStage"
            autoComplete="off"
            placeholder="مثال: أول درجة، استئناف"
            disabled={isSubmitting}
            {...register("procedureStage")}
          />
        </div>

        <div className="space-y-2">
          <Label htmlFor="status">الحالة</Label>
          <select
            id="status"
            className={selectClassName}
            disabled={isSubmitting}
            {...register("status")}
          >
            {STATUS_OPTIONS.map((o) => (
              <option key={o.value} value={o.value}>
                {o.label}
              </option>
            ))}
          </select>
        </div>

        <div className="space-y-2">
          <Label htmlFor="startDate">تاريخ البداية (اختياري)</Label>
          <Input
            id="startDate"
            type="date"
            disabled={isSubmitting}
            {...register("startDate")}
          />
        </div>

        <div className="space-y-2">
          <Label htmlFor="assignedLawyerId">المحامي المسؤول (اختياري)</Label>
          <select
            id="assignedLawyerId"
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

        <div className="space-y-2 sm:col-span-2">
          <Label htmlFor="disputePathType">نوع مسار النزاع</Label>
          <p className="text-xs leading-relaxed text-[var(--color-muted-foreground)]">
            اختر نوع المسار لتوليد مراحل النزاع تلقائيًا بعد إنشاء القضية.
            اختر &quot;مخصص&quot; إن كنت تريد إضافة المراحل يدويًا.
          </p>
          <select
            id="disputePathType"
            className={selectClassName}
            disabled={isSubmitting}
            {...register("disputePathType")}
          >
            {DISPUTE_PATH_SELECT_OPTIONS.map((o) => (
              <option key={o.value || "_none"} value={o.value}>
                {o.label}
              </option>
            ))}
          </select>
          {disputePathPreview ? (
            <p
              role="note"
              className="rounded-lg border border-[var(--color-border)] bg-[var(--color-surface-muted)]/40 px-3 py-2 text-xs leading-relaxed text-[var(--color-foreground)]"
            >
              {disputePathPreview}
            </p>
          ) : null}
        </div>
      </div>

      {activeClientsEmpty ? (
        <div
          className="flex items-start gap-3 rounded-xl border border-[var(--color-border)] bg-[var(--color-surface-muted)]/50 p-4 text-sm"
          role="status"
        >
          <Briefcase className="mt-0.5 size-5 shrink-0 text-[var(--color-muted-foreground)]" />
          <p className="text-[var(--color-muted-foreground)]">
            {canCreateClient
              ? "لا يوجد عملاء نشطون مسجّلون. أضف عميلاً أولاً من «تسجيل عميل» ثم عُد لإنشاء قضية."
              : "لا يوجد عملاء نشطون مسجّلون. يرجى التواصل مع المدير أو السكرتارية لإضافة عميل قبل إنشاء القضية."}
          </p>
        </div>
      ) : null}

      <div className="flex flex-wrap items-center gap-3 border-t border-[var(--color-border)] pt-6">
        <Button
          type="submit"
          variant="gold"
          disabled={isSubmitting || loadingOptions}
        >
          {isSubmitting ? "جارٍ الحفظ…" : "حفظ القضية"}
        </Button>
        <Button type="button" variant="ghost" asChild>
          <Link href="/cases">إلغاء</Link>
        </Button>
      </div>
    </form>
  );
}
