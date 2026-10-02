import { z } from "zod";

/** Roles allowed on POST /api/staff (non-CLIENT). */
export const staffSystemRoles = [
  "ADMIN",
  "SENIOR_LAWYER",
  "LAWYER",
  "SECRETARY",
  "ACCOUNTANT",
] as const;

export const staffCreateSchema = z
  .object({
    systemRole: z.enum(staffSystemRoles),
    fullName: z
      .string()
      .min(1, "الاسم مطلوب")
      .max(150, "الاسم طويل جداً"),
    email: z
      .string()
      .min(1, "البريد مطلوب")
      .email("البريد غير صالح")
      .max(150, "البريد طويل جداً"),
    password: z
      .string()
      .min(8, "كلمة المرور 8 أحرف على الأقل")
      .max(100, "كلمة المرور طويلة جداً"),
    passwordConfirm: z.string(),
    nationalId: z
      .string()
      .max(50)
      .optional()
      .transform((s) => s?.trim() || undefined),
    phone: z
      .string()
      .max(30)
      .optional()
      .transform((s) => s?.trim() || undefined),
    address: z.string().optional().transform((s) => s?.trim() || undefined),
    notes: z.string().optional().transform((s) => s?.trim() || undefined),
  })
  .refine((d) => d.password === d.passwordConfirm, {
    path: ["passwordConfirm"],
    message: "تأكيد كلمة المرور غير مطابق",
  });

export type StaffCreateFormValues = z.infer<typeof staffCreateSchema>;

export const staffUpdateSchema = z.object({
  fullName: z
    .string()
    .min(1, "الاسم مطلوب")
    .max(150, "الاسم طويل جداً"),
  nationalId: z
    .string()
    .max(50)
    .optional()
    .transform((s) => s?.trim() || undefined),
  phone: z
    .string()
    .max(30)
    .optional()
    .transform((s) => s?.trim() || undefined),
  address: z.string().optional().transform((s) => s?.trim() || undefined),
  notes: z.string().optional().transform((s) => s?.trim() || undefined),
});

export type StaffUpdateFormValues = z.infer<typeof staffUpdateSchema>;

export const staffResetPasswordSchema = z
  .object({
    newPassword: z
      .string()
      .min(8, "8 أحرف على الأقل")
      .max(100, "طويلة جداً"),
    confirm: z.string(),
  })
  .refine((d) => d.newPassword === d.confirm, {
    path: ["confirm"],
    message: "غير مطابق",
  });

export type StaffResetPasswordFormValues = z.infer<
  typeof staffResetPasswordSchema
>;

/**
 * Self-service change password (POST /api/staff/me/change-password).
 * Backend body: `{ oldPassword, newPassword }`. Minimum length matches the
 * backend's `@Size(min = 8)` constraint on `newPassword`.
 */
export const changeOwnPasswordSchema = z
  .object({
    oldPassword: z.string().min(1, "أدخل كلمة المرور الحالية"),
    newPassword: z
      .string()
      .min(8, "8 أحرف على الأقل")
      .max(100, "طويلة جداً"),
    confirm: z.string(),
  })
  .refine((d) => d.newPassword === d.confirm, {
    path: ["confirm"],
    message: "غير مطابق",
  })
  .refine((d) => d.oldPassword !== d.newPassword, {
    path: ["newPassword"],
    message: "كلمة المرور الجديدة يجب أن تختلف عن الحالية",
  });

export type ChangeOwnPasswordFormValues = z.infer<
  typeof changeOwnPasswordSchema
>;
