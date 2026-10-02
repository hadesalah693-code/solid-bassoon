"use client";

import Link from "next/link";
import { motion } from "framer-motion";
import { ArrowLeft, ExternalLink, Scale } from "lucide-react";

import { APP_NAME } from "@/lib/constants";
import { SHOWCASE_SITES } from "@/lib/showcase/sites";

export function ShowcasePortfolio() {
  return (
    <div className="min-h-screen bg-[var(--color-navy-950)]">
      <div
        aria-hidden
        className="pointer-events-none fixed inset-0"
        style={{
          background:
            "radial-gradient(ellipse 80% 60% at 85% 15%, rgba(212,175,55,0.12) 0%, transparent 55%), radial-gradient(ellipse 70% 50% at 10% 90%, rgba(30,45,78,0.35) 0%, transparent 50%)",
        }}
      />

      <header className="relative z-10 border-b border-white/10">
        <div className="mx-auto flex max-w-6xl items-center justify-between px-6 py-5">
          <Link href="/login" className="flex items-center gap-3">
            <div className="flex size-10 items-center justify-center rounded-xl bg-[var(--color-gold-400)] text-[var(--color-navy-900)]">
              <Scale className="size-5" />
            </div>
            <div>
              <p className="font-bold text-white">{APP_NAME}</p>
              <p className="text-xs text-[var(--color-navy-400)]">معرض أعمال سابقة</p>
            </div>
          </Link>
          <Link
            href="/login"
            className="flex items-center gap-2 rounded-xl border border-white/15 px-4 py-2 text-sm text-[var(--color-navy-200)] hover:bg-white/5"
          >
            <ArrowLeft className="size-4 rtl:rotate-180" />
            العودة لتسجيل الدخول
          </Link>
        </div>
      </header>

      <main className="relative z-10 mx-auto max-w-6xl px-6 py-16">
        <motion.div
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.5 }}
          className="mb-14 text-center"
        >
          <p className="mb-3 text-sm font-semibold tracking-wide text-[var(--color-gold-400)]">
            أعمالنا السابقة
          </p>
          <h1 className="text-4xl font-bold text-white sm:text-5xl">
            مواقع مكاتب محاماة
            <br />
            <span className="text-[var(--color-gold-400)]">بتخصصات مختلفة</span>
          </h1>
          <p className="mx-auto mt-5 max-w-2xl text-base leading-relaxed text-[var(--color-navy-300)]">
            ثلاثة نماذج حية لمكاتب قانونية متخصصة — جاهزة للعرض على العملاء.
            كل موقع مصمّم ليعكس هوية التخصص ويُبرز احترافية المكتب.
          </p>
        </motion.div>

        <div className="grid gap-8 md:grid-cols-2 lg:grid-cols-3">
          {SHOWCASE_SITES.map((site, i) => {
            const Icon = site.icon;
            return (
              <motion.article
                key={site.slug}
                initial={{ opacity: 0, y: 24 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ duration: 0.45, delay: i * 0.1 }}
                className="group flex flex-col overflow-hidden rounded-3xl border border-white/10 bg-white/[0.04] backdrop-blur-sm"
              >
                <div
                  className="relative flex h-44 items-center justify-center overflow-hidden"
                  style={{ background: site.theme.gradient }}
                >
                  <div
                    className="absolute inset-0 opacity-60"
                    style={{ background: site.theme.heroBg }}
                  />
                  <div
                    className="relative flex size-20 items-center justify-center rounded-2xl shadow-lg"
                    style={{
                      background: `${site.theme.primary}cc`,
                      border: `2px solid ${site.theme.accent}55`,
                    }}
                  >
                    <Icon className="size-10" style={{ color: site.theme.accent }} />
                  </div>
                  <span
                    className="absolute start-4 top-4 rounded-full px-3 py-1 text-xs font-semibold"
                    style={{
                      background: `${site.theme.accent}22`,
                      color: site.theme.accent,
                      border: `1px solid ${site.theme.accent}44`,
                    }}
                  >
                    {site.specialization}
                  </span>
                </div>

                <div className="flex flex-1 flex-col p-6">
                  <h2 className="text-xl font-bold text-white">{site.firmName}</h2>
                  <p className="mt-2 text-sm leading-relaxed text-[var(--color-navy-300)]">
                    {site.description}
                  </p>

                  <div className="mt-5 flex flex-wrap gap-2">
                    {site.stats.slice(0, 2).map((stat) => (
                      <span
                        key={stat.label}
                        className="rounded-lg bg-white/5 px-3 py-1.5 text-xs text-[var(--color-navy-200)]"
                      >
                        <span className="font-bold text-white">{stat.value}</span>{" "}
                        {stat.label}
                      </span>
                    ))}
                  </div>

                  <div className="mt-6 flex gap-3">
                    <Link
                      href={`/showcase/${site.slug}`}
                      className="flex flex-1 items-center justify-center gap-2 rounded-xl py-3 text-sm font-semibold transition-opacity hover:opacity-90"
                      style={{
                        background: `linear-gradient(135deg, ${site.theme.primaryLight}, ${site.theme.primary})`,
                        color: "#fff",
                      }}
                    >
                      <ExternalLink className="size-4" />
                      معاينة الموقع
                    </Link>
                  </div>
                </div>
              </motion.article>
            );
          })}
        </div>

        <motion.div
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          transition={{ delay: 0.5 }}
          className="mt-16 rounded-2xl border border-[var(--color-gold-400)]/25 bg-[var(--color-gold-400)]/5 p-8 text-center"
        >
          <p className="text-lg font-semibold text-white">
            هل تريد موقعاً مماثلاً لمكتبك؟
          </p>
          <p className="mt-2 text-sm text-[var(--color-navy-300)]">
            نظام {APP_NAME} يوفّر إدارة متكاملة للقضايا والعملاء — مع إمكانية تخصيص
            بوابة العملاء حسب هوية مكتبك.
          </p>
          <Link
            href="/login"
            className="mt-5 inline-flex items-center gap-2 rounded-xl bg-[var(--color-gold-400)] px-6 py-3 text-sm font-semibold text-[var(--color-navy-900)] hover:opacity-90"
          >
            جرّب النظام الآن
            <ArrowLeft className="size-4 rtl:rotate-180" />
          </Link>
        </motion.div>
      </main>

      <footer className="relative z-10 border-t border-white/10 py-6 text-center text-xs text-[var(--color-navy-500)]">
        © {new Date().getFullYear()} {APP_NAME}. جميع الحقوق محفوظة.
      </footer>
    </div>
  );
}
