import { z } from "zod";

import type { CaseStatus } from "@/types/case";
import type { CasePriority } from "@/types/case";

const caseStatuses: [CaseStatus, ...CaseStatus[]] = [
  "NEW",
  "IN_PROGRESS",
  "ON_HOLD",
  "CLOSED",
  "ARCHIVED",
];

export const caseCreateSchema = z.object({
  caseNumber: z
    .string()
    .max(100, "رقم القضية طويل جداً"),
  courtCaseNumber: z.string().max(100),
  title: z
    .string()
    .min(1, "العنوان مطلوب")
    .max(250, "العنوان طويل جداً"),
  description: z.string(),
  caseType: z.string().max(100),
  courtName: z.string().max(200),
  courtCircuit: z.string().max(100),
  procedureStage: z.string().max(100),
  status: z.enum(caseStatuses),
  priority: z.enum(["LOW", "MEDIUM", "HIGH", "URGENT"] satisfies [CasePriority, ...CasePriority[]]),
  startDate: z.string(),
  clientId: z.string().min(1, "اختر الموكّل").uuid("معرّف عميل غير صالح"),
  assignedLawyerId: z
    .union([z.literal(""), z.string().uuid()])
    .transform((v) => (v === "" ? undefined : v)),
  /** Empty string means «no preset» — not sent to API. */
  disputePathType: z.string(),
});

/** Parsed values after `zodResolver` (includes field transforms). */
export type CaseCreateFormValues = z.infer<typeof caseCreateSchema>;

/** What react-hook-form holds before submit (e.g. empty string for optional selects). */
export type CaseCreateFormInput = z.input<typeof caseCreateSchema>;
