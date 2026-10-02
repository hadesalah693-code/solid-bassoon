"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useEffect, useState } from "react";
import { AnimatePresence, motion } from "framer-motion";
import { Home, LogOut, Briefcase, Bell, Scale } from "lucide-react";

import { cn } from "@/lib/utils/cn";
import { APP_NAME, APP_TAGLINE } from "@/lib/constants";
import { Button } from "@/components/ui/button";
import { useAuth } from "@/components/auth/auth-provider";

const CLIENT_NAV: { href: string; label: string; icon: React.ComponentType<{ className?: string }> }[] = [
  { href: "/client/dashboard", label: "الرئيسية", icon: Home },
  { href: "/client/cases", label: "قضاياي", icon: Briefcase },
  { href: "/client/notifications", label: "الإشعارات", icon: Bell },
];

interface ClientSidebarProps {
  open?: boolean;
  onClose?: () => void;
}

export function ClientSidebar({ open, onClose }: ClientSidebarProps) {
  const [isLg, setIsLg] = useState(false);
  const { logout } = useAuth();
  useEffect(() => {
    const mq = window.matchMedia("(min-width: 1024px)");
    const next = () => setIsLg(mq.matches);
    next();
    mq.addEventListener("change", next);
    return () => mq.removeEventListener("change", next);
  }, []);

  function onLogout() {
    void logout();
  }

  return (
    <>
      <AnimatePresence>
        {open ? (
          <motion.button
            aria-label="إغلاق القائمة"
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            onClick={onClose}
            className="fixed inset-0 z-40 bg-black/40 lg:hidden"
          />
        ) : null}
      </AnimatePresence>

      <aside
        className={cn(
          "fixed inset-y-0 end-0 z-50 w-72 border-e border-[var(--color-navy-800)] bg-[var(--color-navy-900)] text-[var(--color-navy-50)] transition-transform",
          open ? "translate-x-0" : "translate-x-full rtl:-translate-x-full",
        )}
      >
        <div className="flex h-full flex-col">
          <div className="flex items-center gap-3 px-6 py-6 border-b border-[var(--color-navy-800)]">
            <div className="flex size-11 items-center justify-center rounded-2xl bg-[var(--color-gold-400)] text-[var(--color-navy-900)]">
              <Scale className="size-6" />
            </div>
            <div className="flex flex-col leading-tight">
              <span className="text-lg font-bold">{APP_NAME}</span>
              <span className="text-xs text-[var(--color-navy-300)]">بوابة الموكّل</span>
              <span className="text-xs text-[var(--color-navy-400)]">{APP_TAGLINE}</span>
            </div>
          </div>
          <ClientNavList onNavigate={isLg ? undefined : onClose} />
          <div className="px-3 pb-5 pt-3 border-t border-[var(--color-navy-800)]">
            <Button
              type="button"
              variant="ghost"
              className="w-full justify-start gap-3 text-[var(--color-navy-200)] hover:bg-[var(--color-navy-800)]/60 hover:text-white"
              onClick={onLogout}
            >
              <LogOut className="size-4" />
              تسجيل الخروج
            </Button>
          </div>
        </div>
      </aside>
    </>
  );
}

function ClientNavList({ onNavigate }: { onNavigate?: () => void }) {
  const pathname = usePathname();
  return (
    <nav className="flex-1 overflow-y-auto px-3 py-4">
      <ul className="flex flex-col gap-1">
        {CLIENT_NAV.map((item) => {
          const base = item.href.split("#")[0]!;
          const active =
            pathname === base || pathname.startsWith(`${base}/`);
          const Icon = item.icon;
          return (
            <li key={item.href}>
              <Link
                href={item.href}
                onClick={onNavigate}
                className={cn(
                  "group relative flex items-center gap-3 rounded-xl px-3.5 py-2.5 text-sm font-medium transition-colors",
                  active
                    ? "bg-[var(--color-navy-800)] text-white"
                    : "text-[var(--color-navy-200)] hover:bg-[var(--color-navy-800)]/60 hover:text-white",
                )}
              >
                {active ? (
                  <motion.span
                    layoutId="client-sidebar-active"
                    className="absolute inset-y-1 end-0 w-1 rounded-s-full bg-[var(--color-gold-400)]"
                    transition={{ type: "spring", duration: 0.35 }}
                  />
                ) : null}
                <Icon
                  className={cn(
                    "size-4 shrink-0",
                    active ? "text-[var(--color-gold-400)]" : "text-[var(--color-navy-400)]",
                  )}
                />
                <span>{item.label}</span>
              </Link>
            </li>
          );
        })}
      </ul>
    </nav>
  );
}
