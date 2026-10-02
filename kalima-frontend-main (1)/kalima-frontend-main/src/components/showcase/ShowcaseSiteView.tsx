"use client";

import Link from "next/link";
import { motion } from "framer-motion";
import {
  ArrowLeft,
  Clock,
  Mail,
  MapPin,
  Phone,
  Scale,
  CheckCircle2,
} from "lucide-react";

import { APP_NAME } from "@/lib/constants";
import type { ShowcaseSite } from "@/lib/showcase/sites";

type Props = {
  site: ShowcaseSite;
};

export function ShowcaseSiteView({ site }: Props) {
  const Icon = site.icon;

  return (
    <div className="min-h-screen" style={{ background: "#fafbfc" }}>
      {/* Navbar */}
      <nav
        className="sticky top-0 z-50 border-b backdrop-blur-md"
        style={{
          background: `${site.theme.primary}ee`,
          borderColor: `${site.theme.accent}33`,
        }}
      >
        <div className="mx-auto flex max-w-6xl items-center justify-between px-6 py-4">
          <div className="flex items-center gap-3">
            <div
              className="flex size-10 items-center justify-center rounded-xl"
              style={{ background: site.theme.accent, color: site.theme.primary }}
            >
              <Icon className="size-5" />
            </div>
            <div>
              <p className="font-bold text-white">{site.firmName}</p>
              <p className="text-xs" style={{ color: `${site.theme.accent}cc` }}>
                {site.specialization}
              </p>
            </div>
          </div>
          <div className="flex items-center gap-3">
            <Link
              href="/showcase"
              className="hidden items-center gap-1.5 rounded-lg px-3 py-2 text-sm text-white/80 hover:bg-white/10 sm:flex"
            >
              <ArrowLeft className="size-4 rtl:rotate-180" />
              معرض الأعمال
            </Link>
            <a
              href={`tel:${site.contact.phone.replace(/\s/g, "")}`}
              className="rounded-xl px-4 py-2 text-sm font-semibold"
              style={{ background: site.theme.accent, color: site.theme.primary }}
            >
              تواصل معنا
            </a>
          </div>
        </div>
      </nav>

      {/* Hero */}
      <section
        className="relative overflow-hidden px-6 py-24 sm:py-32"
        style={{ background: site.theme.gradient }}
      >
        <div
          aria-hidden
          className="pointer-events-none absolute inset-0"
          style={{ background: site.theme.heroBg }}
        />
        <div className="relative mx-auto max-w-4xl text-center">
          <motion.span
            initial={{ opacity: 0, y: 10 }}
            animate={{ opacity: 1, y: 0 }}
            className="inline-block rounded-full px-4 py-1.5 text-sm font-semibold"
            style={{
              background: `${site.theme.accent}22`,
              color: site.theme.accent,
              border: `1px solid ${site.theme.accent}44`,
            }}
          >
            {site.specialization} — {site.specializationEn}
          </motion.span>
          <motion.h1
            initial={{ opacity: 0, y: 16 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ delay: 0.1 }}
            className="mt-6 text-4xl font-bold leading-tight text-white sm:text-5xl"
          >
            {site.heroHeadline}
          </motion.h1>
          <motion.p
            initial={{ opacity: 0, y: 16 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ delay: 0.2 }}
            className="mx-auto mt-5 max-w-2xl text-lg leading-relaxed text-white/75"
          >
            {site.heroSubheadline}
          </motion.p>
          <motion.div
            initial={{ opacity: 0, y: 16 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ delay: 0.3 }}
            className="mt-10 flex flex-wrap items-center justify-center gap-4"
          >
            <a
              href="#contact"
              className="rounded-xl px-8 py-3.5 text-base font-semibold shadow-lg"
              style={{ background: site.theme.accent, color: site.theme.primary }}
            >
              احجز استشارة
            </a>
            <a
              href="#services"
              className="rounded-xl border border-white/30 px-8 py-3.5 text-base font-semibold text-white hover:bg-white/10"
            >
              خدماتنا
            </a>
          </motion.div>
        </div>
      </section>

      {/* Stats */}
      <section className="relative -mt-10 mx-auto max-w-5xl px-6">
        <div className="grid grid-cols-2 gap-4 rounded-2xl bg-white p-6 shadow-[var(--shadow-pop)] sm:grid-cols-4">
          {site.stats.map((stat, i) => (
            <motion.div
              key={stat.label}
              initial={{ opacity: 0, y: 12 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ delay: 0.1 * i }}
              className="text-center"
            >
              <p
                className="text-2xl font-bold sm:text-3xl"
                style={{ color: site.theme.primary }}
              >
                {stat.value}
              </p>
              <p className="mt-1 text-xs text-[var(--color-muted-foreground)] sm:text-sm">
                {stat.label}
              </p>
            </motion.div>
          ))}
        </div>
      </section>

      {/* About */}
      <section className="mx-auto max-w-5xl px-6 py-20">
        <div className="grid items-center gap-12 lg:grid-cols-2">
          <div>
            <p
              className="text-sm font-semibold"
              style={{ color: site.theme.primaryLight }}
            >
              عن المكتب
            </p>
            <h2 className="mt-2 text-3xl font-bold text-[var(--color-foreground)]">
              {site.firmName}
            </h2>
            <p className="mt-4 leading-relaxed text-[var(--color-muted-foreground)]">
              {site.description}
            </p>
            <p className="mt-3 text-sm text-[var(--color-muted-foreground)]">
              تأسّس عام {site.yearEstablished} — أكثر من {site.casesWon} قضية
              ناجحة.
            </p>
            <ul className="mt-6 space-y-3">
              {site.highlights.map((h) => (
                <li key={h} className="flex items-start gap-2.5 text-sm">
                  <CheckCircle2
                    className="mt-0.5 size-4 shrink-0"
                    style={{ color: site.theme.accent }}
                  />
                  <span className="text-[var(--color-foreground)]">{h}</span>
                </li>
              ))}
            </ul>
          </div>
          <div
            className="flex aspect-[4/3] items-center justify-center rounded-3xl"
            style={{
              background: site.theme.gradient,
            }}
          >
            <Icon className="size-24 opacity-30" style={{ color: site.theme.accent }} />
          </div>
        </div>
      </section>

      {/* Services */}
      <section id="services" className="bg-[var(--color-surface-muted)] px-6 py-20">
        <div className="mx-auto max-w-5xl">
          <div className="mb-12 text-center">
            <p
              className="text-sm font-semibold"
              style={{ color: site.theme.primaryLight }}
            >
              ما نقدّمه
            </p>
            <h2 className="mt-2 text-3xl font-bold text-[var(--color-foreground)]">
              خدمات {site.specialization}
            </h2>
          </div>
          <div className="grid gap-6 sm:grid-cols-2">
            {site.services.map((svc, i) => (
              <motion.div
                key={svc.title}
                initial={{ opacity: 0, y: 16 }}
                whileInView={{ opacity: 1, y: 0 }}
                viewport={{ once: true }}
                transition={{ delay: i * 0.08 }}
                className="rounded-2xl border border-[var(--color-border)] bg-white p-6 shadow-[var(--shadow-soft)]"
              >
                <div
                  className="mb-4 flex size-11 items-center justify-center rounded-xl"
                  style={{ background: `${site.theme.primary}15`, color: site.theme.primary }}
                >
                  <Icon className="size-5" />
                </div>
                <h3 className="text-lg font-bold text-[var(--color-foreground)]">
                  {svc.title}
                </h3>
                <p className="mt-2 text-sm leading-relaxed text-[var(--color-muted-foreground)]">
                  {svc.description}
                </p>
              </motion.div>
            ))}
          </div>
        </div>
      </section>

      {/* Contact */}
      <section id="contact" className="mx-auto max-w-5xl px-6 py-20">
        <div
          className="overflow-hidden rounded-3xl"
          style={{ background: site.theme.gradient }}
        >
          <div className="grid gap-0 lg:grid-cols-2">
            <div className="p-10">
              <h2 className="text-3xl font-bold text-white">تواصل معنا</h2>
              <p className="mt-3 text-white/70">
                {site.tagline}
              </p>
              <div className="mt-8 space-y-4">
                <ContactRow icon={Phone} label={site.contact.phone} />
                <ContactRow icon={Mail} label={site.contact.email} dir="ltr" />
                <ContactRow icon={MapPin} label={site.contact.address} />
                <ContactRow icon={Clock} label={site.contact.hours} />
              </div>
            </div>
            <div className="flex flex-col justify-center bg-white/10 p-10 backdrop-blur-sm">
              <p className="text-lg font-semibold text-white">احجز استشارة مجانية</p>
              <p className="mt-2 text-sm text-white/70">
                املأ البيانات وسنتواصل معك خلال 24 ساعة.
              </p>
              <form
                onSubmit={(e) => e.preventDefault()}
                className="mt-6 space-y-4"
              >
                <input
                  type="text"
                  placeholder="الاسم الكامل"
                  className="h-11 w-full rounded-xl border border-white/20 bg-white/10 px-4 text-sm text-white placeholder:text-white/50 focus:outline-none focus:ring-2 focus:ring-white/30"
                />
                <input
                  type="tel"
                  placeholder="رقم الجوال"
                  dir="ltr"
                  className="h-11 w-full rounded-xl border border-white/20 bg-white/10 px-4 text-sm text-white placeholder:text-white/50 focus:outline-none focus:ring-2 focus:ring-white/30"
                />
                <textarea
                  placeholder="اشرح قضيتك باختصار..."
                  rows={3}
                  className="w-full rounded-xl border border-white/20 bg-white/10 px-4 py-3 text-sm text-white placeholder:text-white/50 focus:outline-none focus:ring-2 focus:ring-white/30"
                />
                <button
                  type="submit"
                  className="h-11 w-full rounded-xl text-sm font-semibold"
                  style={{ background: site.theme.accent, color: site.theme.primary }}
                >
                  إرسال الطلب
                </button>
              </form>
            </div>
          </div>
        </div>
      </section>

      {/* Footer */}
      <footer className="border-t border-[var(--color-border)] bg-white px-6 py-8">
        <div className="mx-auto flex max-w-5xl flex-col items-center justify-between gap-4 sm:flex-row">
          <div className="flex items-center gap-2 text-sm text-[var(--color-muted-foreground)]">
            <Icon className="size-4" style={{ color: site.theme.primary }} />
            <span>{site.firmName}</span>
            <span>·</span>
            <span>© {new Date().getFullYear()}</span>
          </div>
          <Link
            href="/showcase"
            className="flex items-center gap-2 rounded-lg border border-[var(--color-border)] px-4 py-2 text-xs text-[var(--color-muted-foreground)] hover:bg-[var(--color-surface-muted)]"
          >
            <Scale className="size-3.5" />
            موقع تجريبي — مدعوم من {APP_NAME}
          </Link>
        </div>
      </footer>
    </div>
  );
}

function ContactRow({
  icon: Icon,
  label,
  dir,
}: {
  icon: React.ComponentType<{ className?: string }>;
  label: string;
  dir?: "ltr" | "rtl";
}) {
  return (
    <div className="flex items-center gap-3 text-white/85">
      <div className="flex size-9 shrink-0 items-center justify-center rounded-lg bg-white/10">
        <Icon className="size-4" />
      </div>
      <span className="text-sm" dir={dir}>
        {label}
      </span>
    </div>
  );
}
