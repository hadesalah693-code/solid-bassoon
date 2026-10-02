"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import dynamic from "next/dynamic";
import { useRouter } from "next/navigation";
import { motion } from "framer-motion";
import {
  Scale,
  Eye,
  EyeOff,
  Mail,
  Lock,
  ArrowLeft,
  Loader2,
} from "lucide-react";

import { Label } from "@/components/ui/label";
import { useAuth } from "@/components/auth/auth-provider";
import { ApiRequestError } from "@/lib/api/client";
import { authApi } from "@/lib/api/auth";
import { getDefaultRouteForUser } from "@/lib/auth/routes";
import { APP_NAME, APP_TAGLINE } from "@/lib/constants";
import { DEMO_TRIAL_ACCOUNTS } from "@/lib/constants/demo-accounts";

const LoginHeroPanel = dynamic(
  () =>
    import("@/components/auth/login/LoginHeroPanel").then((m) => m.LoginHeroPanel),
  { ssr: false, loading: () => null },
);

export default function LoginPage() {
  const router = useRouter();
  const { loading, isAuthenticated, user, loginWithResponse } = useAuth();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [rememberMe, setRememberMe] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (loading) return;
    if (isAuthenticated && user) {
      router.replace(getDefaultRouteForUser(user));
    }
  }, [loading, isAuthenticated, user, router]);

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    const trimmedEmail = email.trim();
    if (!trimmedEmail) {
      setError("أدخل البريد الإلكتروني");
      return;
    }
    if (password.length < 6) {
      setError("كلمة المرور يجب أن تكون 6 أحرف على الأقل");
      return;
    }
    setSubmitting(true);
    try {
      const data = await authApi.login({ email: trimmedEmail, password });
      loginWithResponse(data);
      router.replace(getDefaultRouteForUser(data.person));
    } catch (err) {
      if (err instanceof ApiRequestError) {
        setError(err.details?.message ?? err.message ?? "تعذر تسجيل الدخول");
        return;
      }
      if (err instanceof Error && "details" in err) {
        const details = (err as Error & { details?: { message?: string } }).details;
        setError(details?.message ?? err.message ?? "تعذر تسجيل الدخول");
        return;
      }
      setError("تعذر تسجيل الدخول");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <main className="relative flex min-h-screen overflow-hidden bg-[var(--color-navy-950)]">
      <div
        aria-hidden
        className="pointer-events-none absolute inset-0 overflow-hidden"
        style={{
          background:
            "radial-gradient(ellipse 80% 60% at 85% 15%, rgba(201,168,50,0.08) 0%, transparent 55%), radial-gradient(ellipse 70% 50% at 10% 90%, rgba(31,48,80,0.30) 0%, transparent 50%)",
        }}
      />

      <LoginHeroPanel />

      <section className="relative z-10 flex w-full items-center justify-center px-5 py-12 lg:w-1/2 lg:px-14">
        <motion.div
          initial={{ opacity: 0, y: 16 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.35, ease: "easeOut" }}
          className="w-full max-w-md"
        >
          <div className="mb-8 flex items-center justify-center gap-3 lg:hidden">
            <div className="flex size-11 items-center justify-center rounded-xl bg-[var(--color-gold-400)] text-[var(--color-navy-900)]">
              <Scale className="size-5" strokeWidth={2.5} />
            </div>
            <div className="text-start">
              <p className="text-base font-extrabold text-white">{APP_NAME}</p>
              <p className="text-[0.6875rem] text-[var(--color-navy-400)]">{APP_TAGLINE}</p>
            </div>
          </div>

          <div
            className="relative overflow-hidden rounded-2xl p-7 backdrop-blur-xl sm:p-9"
            style={{
              background: "rgba(13, 24, 48, 0.80)",
              border: "1px solid rgba(201,168,50,0.15)",
              boxShadow: "0 24px 64px rgba(0,0,0,0.50)",
            }}
          >
            <div className="mb-6 flex flex-col items-center gap-2">
              <div className="flex size-12 items-center justify-center rounded-xl bg-[var(--color-gold-400)]">
                <Scale className="size-6 text-[var(--color-navy-900)]" strokeWidth={2.5} />
              </div>
              <span className="text-sm font-extrabold text-[var(--color-gold-400)]">
                {APP_NAME}
              </span>
            </div>

            <div className="mb-7 text-center">
              <h1 className="text-2xl font-extrabold text-white">تسجيل الدخول</h1>
              <p className="mt-1.5 text-[0.8125rem] text-[var(--color-navy-400)]">
                أدخل بياناتك للوصول إلى لوحة التحكم
              </p>
            </div>

            <form noValidate onSubmit={onSubmit} className="space-y-4">
              <div className="space-y-1.5">
                <Label htmlFor="email" className="text-[0.8125rem] text-[var(--color-navy-300)]">
                  البريد الإلكتروني
                </Label>
                <div className="relative">
                  <Mail className="pointer-events-none absolute start-3.5 top-1/2 size-4 -translate-y-1/2 text-[var(--color-navy-500)]" />
                  <input
                    id="email"
                    type="email"
                    autoComplete="email"
                    required
                    dir="ltr"
                    value={email}
                    onChange={(e) => setEmail(e.target.value)}
                    placeholder="name@kalima.local"
                    className="h-11 w-full rounded-xl border border-white/10 bg-white/5 ps-10 pe-4 text-sm text-white placeholder:text-[var(--color-navy-600)] transition-[border-color,box-shadow] focus:border-[var(--color-gold-400)]/50 focus:outline-none focus:ring-2 focus:ring-[var(--color-gold-400)]/15"
                  />
                </div>
              </div>

              <div className="space-y-1.5">
                <Label htmlFor="password" className="text-[0.8125rem] text-[var(--color-navy-300)]">
                  كلمة المرور
                </Label>
                <div className="relative">
                  <Lock className="pointer-events-none absolute start-3.5 top-1/2 size-4 -translate-y-1/2 text-[var(--color-navy-500)]" />
                  <input
                    id="password"
                    type={showPassword ? "text" : "password"}
                    autoComplete="current-password"
                    required
                    minLength={6}
                    dir="ltr"
                    value={password}
                    onChange={(e) => setPassword(e.target.value)}
                    placeholder="••••••••"
                    className="h-11 w-full rounded-xl border border-white/10 bg-white/5 ps-10 pe-11 text-sm text-white placeholder:text-[var(--color-navy-600)] transition-[border-color,box-shadow] focus:border-[var(--color-gold-400)]/50 focus:outline-none focus:ring-2 focus:ring-[var(--color-gold-400)]/15"
                  />
                  <button
                    type="button"
                    onClick={() => setShowPassword((v) => !v)}
                    aria-label={showPassword ? "إخفاء كلمة المرور" : "إظهار كلمة المرور"}
                    className="absolute end-3 top-1/2 -translate-y-1/2 rounded-lg p-1.5 text-[var(--color-navy-500)] hover:text-[var(--color-navy-300)]"
                  >
                    {showPassword ? <EyeOff className="size-4" /> : <Eye className="size-4" />}
                  </button>
                </div>
              </div>

              <label className="flex cursor-pointer items-center gap-2.5">
                <input
                  type="checkbox"
                  checked={rememberMe}
                  onChange={(e) => setRememberMe(e.target.checked)}
                  className="size-4 accent-[var(--color-gold-400)]"
                />
                <span className="select-none text-[0.8125rem] text-[var(--color-navy-400)]">تذكرني</span>
              </label>

              {error ? (
                <div
                  role="alert"
                  className="rounded-xl border border-[var(--color-danger-border)] bg-[var(--color-danger-bg)] px-4 py-3 text-sm text-[var(--color-danger)]"
                >
                  {error}
                </div>
              ) : null}

              <button
                type="submit"
                disabled={submitting}
                className="h-12 w-full rounded-xl bg-[var(--color-gold-400)] font-extrabold text-[var(--color-navy-900)] shadow-[0_4px_20px_rgba(201,168,50,0.30)] transition-[background,box-shadow] hover:bg-[var(--color-gold-300)] hover:shadow-[0_6px_28px_rgba(201,168,50,0.40)] disabled:opacity-50"
              >
                <span className="flex items-center justify-center gap-2">
                  {submitting ? (
                    <>
                      <Loader2 className="size-4 animate-spin" />
                      جارٍ التحقق...
                    </>
                  ) : (
                    <>
                      تسجيل الدخول
                      <ArrowLeft className="size-4 rtl:rotate-180" />
                    </>
                  )}
                </span>
              </button>

              <p className="text-center text-[0.6875rem] text-[var(--color-navy-500)]">
                بالدخول أنت توافق على{" "}
                <Link href="#" className="text-[var(--color-gold-400)] hover:opacity-100 opacity-70">
                  شروط الاستخدام
                </Link>{" "}
                و{" "}
                <Link href="#" className="text-[var(--color-gold-400)] hover:opacity-100 opacity-70">
                  سياسة الخصوصية
                </Link>
                .
              </p>

              <div className="mt-4 text-center">
                <Link
                  href="/showcase"
                  className="inline-flex items-center gap-2 text-[0.8125rem] text-[var(--color-gold-400)] hover:opacity-90"
                >
                  معرض الأعمال
                  <ArrowLeft className="size-3.5 rtl:rotate-180" />
                </Link>
              </div>
            </form>

            {process.env.NODE_ENV === "development" ? (
              <div className="mt-5 space-y-2.5 rounded-xl border border-[var(--color-gold-400)]/15 bg-[var(--color-gold-400)]/[0.04] p-3.5">
                <p className="text-center text-[0.6875rem] font-bold text-[var(--color-gold-400)]">
                  حسابات تجربة (بيئة التطوير)
                </p>
                <div className="space-y-1.5">
                  {DEMO_TRIAL_ACCOUNTS.map((account) => (
                    <button
                      key={account.email}
                      type="button"
                      disabled={submitting}
                      onClick={() => {
                        setEmail(account.email);
                        setPassword(account.password);
                        setError(null);
                      }}
                      className="flex w-full flex-col items-start gap-0.5 rounded-lg border border-white/[0.06] px-3 py-2 text-start transition-colors hover:bg-white/[0.03] disabled:opacity-50"
                    >
                      <span className="text-[0.8125rem] font-semibold text-white">{account.role}</span>
                      <span className="text-[0.6875rem] text-[var(--color-navy-400)] ltr-nums" dir="ltr">
                        {account.email} / {account.password}
                      </span>
                    </button>
                  ))}
                </div>
              </div>
            ) : null}
          </div>
        </motion.div>
      </section>
    </main>
  );
}
