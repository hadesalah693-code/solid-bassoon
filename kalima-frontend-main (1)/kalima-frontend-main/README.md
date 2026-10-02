# Kalima Frontend (Next)

واجهة نظام إدارة مكاتب المحاماة **كلمة** — مبنية على Next.js 15 + Tailwind v4 + shadcn-style UI، بتصميم Arabic-first (RTL) واحترافي.

## التشغيل

```bash
npm install
cp .env.local.example .env.local
npm run dev
```

الصفحة تفتح على <http://localhost:3000>. الشاشات كلها تعمل بـ mock data، جاهزة للربط بالـ backend لاحقاً عبر `src/lib/api/*`.

## الهيكلة

```
src/
├─ app/              صفحات App Router مجمّعة في (auth) و (dashboard)
├─ components/       ui + layout + shared + feature
├─ lib/              api + mock + utils + constants
├─ hooks/            React hooks
└─ types/            TypeScript types (متطابقة مع الـ backend)
```

## الحسابات التجريبية (dev + backend)

شغّل الـ backend بـ profile `dev` ثم سجّل الدخول من `/login` — في وضع التطوير تظهر أزرار الحسابات التجريبية.

| الدور | البريد | كلمة المرور |
|--------|--------|-------------|
| مدير | `admin@kalima.local` | `admin123` |
| محامٍ | `lawyer@kalima.local` | `admin123` |
| عميل | `client@kalima.local` | `client123` |

قضية تجريبية: **DEMO-CLIENT-001** (رسائل + جلسة قادمة).

```bash
# Backend
cd kalima-backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

# Frontend
cd kalima-frontend-next
npm run dev
```

اختبار API: `scripts/user-trial-smoke.ps1`

## Design system

- ألوان: كحلي داكن + ذهبي (تيمة قانونية)
- خط: Cairo (Google Fonts عبر next/font)
- RTL logical properties (`ps-*`, `pe-*`, `ms-*`, `me-*`)
- Cards: `rounded-2xl` + soft shadows
