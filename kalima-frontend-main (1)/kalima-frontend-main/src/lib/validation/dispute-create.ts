import { z } from "zod";

const disputeType = z.enum([
  "CIVIL",
  "COMMERCIAL",
  "LABOR",
  "ADMINISTRATIVE",
  "CRIMINAL",
  "FAMILY",
  "REAL_ESTATE",
  "OTHER",
]);

const riskLevel = z.enum(["LOW", "MEDIUM", "HIGH", "CRITICAL"]);

const partyCapacity = z.enum([
  "PLAINTIFF",
  "DEFENDANT",
  "CLAIMANT",
  "RESPONDENT",
  "LESSOR",
  "LESSEE",
  "EMPLOYER",
  "EMPLOYEE",
  "OTHER",
]);

export const disputeCreateSchema = z.object({
  title: z.string().trim().min(1, "العنوان مطلوب").max(250),
  clientId: z.string().uuid("اختر عميلاً"),
  clientRole: partyCapacity.optional(),
  opponentName: z.string().trim().min(1, "اسم الخصم مطلوب").max(250),
  opponentRole: partyCapacity.optional(),
  description: z.string().optional(),
  disputeType: disputeType,
  claimAmount: z
    .string()
    .optional()
    .transform((s) => (s?.trim() === "" ? undefined : s))
    .pipe(z.coerce.number().nonnegative().optional()),
  riskLevel: riskLevel,
  legalPath: z.string().optional(),
  administrativeOfficerId: z.string().optional(),
  successProbability: z
    .string()
    .optional()
    .transform((s) => (s?.trim() === "" ? undefined : s))
    .pipe(
      z.coerce
        .number()
        .min(0)
        .max(100)
        .optional(),
    ),
});

export type DisputeCreateFormValues = z.infer<typeof disputeCreateSchema>;
