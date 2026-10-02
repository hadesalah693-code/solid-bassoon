import { z } from "zod";

export const clientRegistrationSchema = z
  .object({
    personType: z.enum(["INDIVIDUAL", "COMPANY"]),
    fullName: z
      .string()
      .max(150, "الاسم طويل جدا")
      .optional()
      .transform((s) => s?.trim() ?? ""),
    companyName: z
      .string()
      .max(200, "اسم الشركة طويل جدا")
      .optional()
      .transform((s) => s?.trim() ?? ""),
    commercialRegister: z
      .string()
      .max(100)
      .optional()
      .transform((s) => s?.trim() || undefined),
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
    email: z
      .string()
      .max(150)
      .optional()
      .transform((s) => s?.trim() || undefined),
    address: z.string().optional().transform((s) => s?.trim() || undefined),
    notes: z.string().optional().transform((s) => s?.trim() || undefined),
    createLoginAccount: z.boolean().optional().default(false),
    password: z.string().optional().transform((s) => s?.trim() || undefined),
    confirmPassword: z.string().optional().transform((s) => s?.trim() || undefined),
  })
  .superRefine((data, ctx) => {
    if (data.personType === "INDIVIDUAL" && !data.fullName) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        path: ["fullName"],
        message: "الاسم الكامل مطلوب للأفراد",
      });
    }
    if (data.personType === "COMPANY" && !data.companyName) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        path: ["companyName"],
        message: "اسم الشركة مطلوب",
      });
    }
    if (data.email && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(data.email)) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        path: ["email"],
        message: "البريد الإلكتروني غير صالح",
      });
    }
    if (data.createLoginAccount) {
      if (!data.email) {
        ctx.addIssue({
          code: z.ZodIssueCode.custom,
          path: ["email"],
          message: "البريد الإلكتروني مطلوب لإنشاء حساب دخول",
        });
      }
      if (!data.password || data.password.length < 6) {
        ctx.addIssue({
          code: z.ZodIssueCode.custom,
          path: ["password"],
          message: "كلمة المرور يجب ألا تقل عن 6 أحرف",
        });
      }
      if (data.password !== data.confirmPassword) {
        ctx.addIssue({
          code: z.ZodIssueCode.custom,
          path: ["confirmPassword"],
          message: "تأكيد كلمة المرور غير مطابق",
        });
      }
    }
  });

export type ClientRegistrationFormValues = z.infer<
  typeof clientRegistrationSchema
>;
