const isProd = process.env.NODE_ENV === "production";

const prodAdminEmail = process.env.NEXT_PUBLIC_PROD_ADMIN_EMAIL ?? "admin@kalima.com";
const prodAdminPassword = process.env.NEXT_PUBLIC_PROD_ADMIN_PASSWORD ?? "KalimaAdmin2026!";

/** Local demo accounts for development, and Render/Vercel production bootstrap credentials when available. */
export const DEMO_TRIAL_ACCOUNTS = isProd
  ? [
      {
        role: "مدير المكتب",
        email: prodAdminEmail,
        password: prodAdminPassword,
        hint: "لوحة التحكم — كل القضايا والإعدادات",
      },
    ]
  : [
      {
        role: "مدير المكتب",
        email: "admin@kalima.local",
        password: "admin123",
        hint: "لوحة التحكم — كل القضايا والإعدادات",
      },
      {
        role: "محامٍ",
        email: "lawyer@kalima.local",
        password: "admin123",
        hint: "قضية DEMO-CLIENT-001 + رسائل + جلسة",
      },
      {
        role: "عميل",
        email: "client@kalima.local",
        password: "client123",
        hint: "بوابة العميل — قضاياي + متابعة",
      },
      {
        role: "عميل",
        email: "client@kalima.local",
        password: "client123",
        hint: "بوابة العميل — قضاياي + متابعة",
      },
      {
        role: "بوابة العميل (جديدة)",
        email: "portal@kalima.local",
        password: "portal123",
        hint: "لوحة العميل الجديدة — قضاياي ووثائقي وجلساتي",
      },
    ];
