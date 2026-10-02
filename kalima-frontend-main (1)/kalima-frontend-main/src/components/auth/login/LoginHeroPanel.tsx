"use client";

import { motion, useReducedMotion } from "framer-motion";
import { Gavel, MessageSquare, Scale, ShieldCheck } from "lucide-react";

import { APP_NAME, APP_TAGLINE } from "@/lib/constants";

const LEATHER_COLORS = [
  "#5c1f1f", "#6b3410", "#3d1f1f", "#1f3d2a",
  "#2d1f3d", "#5c4520", "#3d2a1f", "#4a2020",
] as const;

function seededRand(seed: number): number {
  const x = Math.sin(seed * 9301 + 49297) * 233280;
  return x - Math.floor(x);
}

type ShelfBook = { height: number; width: number; color: string };

function buildShelves(rows: number, perRow: number): ShelfBook[][] {
  return Array.from({ length: rows }, (_, r) =>
    Array.from({ length: perRow }, (_, b) => {
      const s = r * perRow + b + 1;
      return {
        height: Math.round(72 + seededRand(s) * 32),
        width: Math.round(12 + seededRand(s + 100) * 7),
        color: LEATHER_COLORS[Math.floor(seededRand(s + 200) * LEATHER_COLORS.length)],
      };
    }),
  );
}

const SHELVES = buildShelves(3, 14);

const BOOKS = [
  { x: 8, y: 12, w: 52, rot: -18, dy: 11, dur: 7, del: 0, op: 0.18, color: "#1e2d4e" },
  { x: 62, y: 10, w: 44, rot: -8, dy: 14, dur: 6.5, del: 0.5, op: 0.16, color: "#2a3d65" },
  { x: 28, y: 55, w: 38, rot: 16, dy: 9, dur: 8, del: 1.2, op: 0.14, color: "#161f36" },
  { x: 78, y: 52, w: 46, rot: 20, dy: 10, dur: 7.5, del: 2, op: 0.13, color: "#1e2d4e" },
] as const;

const FEATURES = [
  { icon: Gavel, label: "إدارة القضايا", desc: "تتبّع حالة كل قضية وأطرافها لحظياً" },
  { icon: ShieldCheck, label: "متابعة الجلسات", desc: "جدولة وتنبيهات ذكية قبل كل موعد" },
  { icon: MessageSquare, label: "التواصل مع العملاء", desc: "تواصل مباشر ومنظم داخل النظام" },
] as const;

function Book3D({ color }: { color: string }) {
  return (
    <svg viewBox="0 0 62 84" fill="none" xmlns="http://www.w3.org/2000/svg" style={{ display: "block", width: "100%", height: "auto" }}>
      <rect x="49" y="7" width="10" height="72" rx="1.5" fill="rgba(240,228,196,0.50)" />
      <rect x="5" y="2" width="46" height="80" rx="2.5" fill={color} stroke="rgba(212,175,55,0.45)" strokeWidth="0.7" />
      <rect x="2" y="2" width="6" height="80" rx="1.5" fill="rgba(212,175,55,0.65)" />
      <rect x="14" y="17" width="28" height="2.2" rx="1.1" fill="rgba(212,175,55,0.45)" />
      <rect x="14" y="23" width="20" height="1.8" rx="0.9" fill="rgba(212,175,55,0.30)" />
    </svg>
  );
}

function FloatingBooks({ animate }: { animate: boolean }) {
  return (
    <div aria-hidden className="pointer-events-none absolute inset-0 overflow-hidden">
      {BOOKS.map((b, i) =>
        animate ? (
          <motion.div
            key={i}
            className="absolute"
            style={{
              left: `${b.x}%`,
              top: `${b.y}%`,
              width: b.w,
              opacity: b.op,
              transform: `perspective(320px) rotateX(10deg) rotateY(${b.rot > 0 ? -12 : 12}deg) rotate(${b.rot}deg)`,
            }}
            animate={{ y: [0, -b.dy, 0] }}
            transition={{ duration: b.dur, repeat: Infinity, ease: "easeInOut", delay: b.del }}
          >
            <Book3D color={b.color} />
          </motion.div>
        ) : (
          <div
            key={i}
            className="absolute"
            style={{
              left: `${b.x}%`,
              top: `${b.y}%`,
              width: b.w,
              opacity: b.op,
              transform: `perspective(320px) rotateX(10deg) rotateY(${b.rot > 0 ? -12 : 12}deg) rotate(${b.rot}deg)`,
            }}
          >
            <Book3D color={b.color} />
          </div>
        ),
      )}
    </div>
  );
}

function BookSpine({ height, width, color }: ShelfBook) {
  return (
    <div
      className="relative shrink-0 rounded-t-[2px]"
      style={{
        height: `${height}px`,
        width: `${width}px`,
        background: `linear-gradient(90deg, rgba(0,0,0,0.45) 0%, ${color} 18%, ${color} 82%, rgba(0,0,0,0.5) 100%)`,
      }}
    />
  );
}

function Library3D() {
  return (
    <div
      aria-hidden
      className="pointer-events-none absolute inset-0 flex flex-col justify-center gap-4 px-4 opacity-40"
      style={{
        transform: "perspective(1400px) rotateY(-6deg) rotateX(1deg) scale(1.05)",
        transformOrigin: "center",
      }}
    >
      {SHELVES.map((books, i) => (
        <div key={i} className="relative">
          <div className="flex items-end justify-center gap-[2px] px-2">
            {books.map((b, j) => (
              <BookSpine key={j} {...b} />
            ))}
          </div>
          <div className="h-[6px] rounded-[1px] bg-[#2a1810]" />
        </div>
      ))}
    </div>
  );
}

export function LoginHeroPanel() {
  const reduceMotion = useReducedMotion();
  const animate = !reduceMotion;

  return (
    <aside className="relative hidden lg:flex lg:w-1/2 flex-col">
      <div className="absolute inset-0 bg-hero-grid opacity-35" />
      <Library3D />
      <div
        className="absolute inset-0"
        style={{
          background:
            "linear-gradient(180deg, rgba(11,20,38,0.85) 0%, rgba(11,20,38,0.2) 40%, rgba(11,20,38,0.2) 60%, rgba(6,11,24,0.92) 100%)",
        }}
      />
      <FloatingBooks animate={animate} />

      <div className="relative z-10 flex h-full flex-col justify-between p-14">
        <div className="flex items-center gap-3">
          <div className="flex size-12 items-center justify-center rounded-2xl bg-[var(--color-gold-400)] text-[var(--color-navy-900)] shadow-[0_0_32px_rgba(212,175,55,0.45)]">
            <Scale className="size-6" />
          </div>
          <div>
            <p className="text-xl font-bold text-white">{APP_NAME}</p>
            <p className="text-xs text-[var(--color-navy-300)]">{APP_TAGLINE}</p>
          </div>
        </div>

        <div className="flex flex-col items-center gap-10 text-center">
          <div className="relative flex size-36 items-center justify-center rounded-full border border-[var(--color-gold-400)]/35 bg-[var(--color-navy-900)]/90 shadow-[0_0_60px_rgba(212,175,55,0.2)]">
            <Scale className="size-16 text-[var(--color-gold-400)]" />
          </div>
          <div>
            <h2 className="text-3xl font-bold leading-snug text-white">
              إدارة مكتبك القانوني،
              <br />
              <span className="text-[var(--color-gold-400)]">بهدوء واحترافية.</span>
            </h2>
            <p className="mt-3 text-sm leading-relaxed text-[var(--color-navy-200)]">
              نظام موحّد للقضايا، الجلسات، الموظفين، والعملاء.
            </p>
          </div>
          <div className="flex w-full max-w-xs flex-col gap-3">
            {FEATURES.map((f) => {
              const Icon = f.icon;
              return (
                <div
                  key={f.label}
                  className="flex items-center gap-3 rounded-2xl border border-white/10 bg-white/5 px-4 py-3 backdrop-blur-sm"
                >
                  <div className="flex size-9 shrink-0 items-center justify-center rounded-xl bg-[var(--color-gold-400)]/15 text-[var(--color-gold-400)]">
                    <Icon className="size-4" />
                  </div>
                  <div className="text-start">
                    <p className="text-sm font-semibold text-white">{f.label}</p>
                    <p className="text-xs text-[var(--color-navy-300)]">{f.desc}</p>
                  </div>
                </div>
              );
            })}
          </div>
        </div>

        <p className="text-xs text-[var(--color-navy-500)]">
          © {new Date().getFullYear()} {APP_NAME}. جميع الحقوق محفوظة.
        </p>
      </div>
    </aside>
  );
}
