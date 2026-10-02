import { z } from "zod";

export const caseUpdateSchema = z.object({
  title: z
    .string()
    .min(1, "العنوان مطلوب")
    .max(250, "العنوان طويل جداً"),
  courtCaseNumber: z.string().max(100),
  description: z.string(),
  caseType: z.string().max(100),
  courtName: z.string().max(200),
  courtCircuit: z.string().max(100),
  procedureStage: z.string().max(100),
  startDate: z.string(),
  closeDate: z.string(),
  assignedLawyerId: z
    .union([z.literal(""), z.string().uuid()])
    .transform((v) => (v === "" ? undefined : v)),
});

export type CaseUpdateFormInput = z.input<typeof caseUpdateSchema>;
export type CaseUpdateFormValues = z.output<typeof caseUpdateSchema>;
