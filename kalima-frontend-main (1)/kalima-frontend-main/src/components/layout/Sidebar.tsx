"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useEffect, useState } from "react";
import { AnimatePresence, motion } from "framer-motion";
import {
  LayoutDashboard,
  Users,
  Briefcase,
  Gavel,
  Bell,
  MessageCircle,
  ShieldAlert,
  Scale,
  LogOut,
  UserPlus,
  Contact,
  Brain,
  FileText,
  FileSignature,
  ClipboardList,
  ChevronsRight,
  Handshake,
  CalendarDays,
  CalendarCheck,
  Fingerprint,
  Network,
  BadgeCheck,
  ScanText,
  HardHat,
  ArrowLeftRight,
  Receipt,
  PieChart,
  BookOpen,
  Wallet,
  Landmark,
  Banknote,
  CalendarRange,
  Percent,
Gauge,
  Timer,
  FileMinus,
  Repeat,
  Target,
} from "lucide-react";

import { cn } from "@/lib/utils/cn";
import { APP_NAME, APP_TAGLINE } from "@/lib/constants";
import { Button } from "@/components/ui/button";
import { ACCOUNTING_READ_ROLES } from "@/components/accounting/AccountingRoleGate";
import { useAuth } from "@/components/auth/auth-provider";
import { getStoredUser } from "@/lib/auth/session";
import { refreshMyOpenTasks, useMyOpenTasks } from "@/lib/hooks/use-my-open-tasks";
import { useUnreadMessages } from "@/lib/hooks/use-unread-messages";
import type { SystemRole } from "@/types/staff";

type NavItem = {
  href: string;
  label: string;
  icon: React.ComponentType<{ className?: string }>;
  allowedRoles?: SystemRole[];
};

type NavGroup = {
  label?: string;
  items: NavItem[];
};

/** Mirrors `AccountingRoleGate` — read access to the ledger. */
const ACCOUNTING_ROLES: SystemRole[] = [...ACCOUNTING_READ_ROLES];

/** Periods and tax rates are finance-only, matching their backend controllers. */
const ACCOUNTING_FINANCE_ROLES: SystemRole[] = ["ADMIN", "ACCOUNTANT"];

/** Mirrors `HrRoleGate` — read access to the HR module. */
const HR_ROLES: SystemRole[] = ["ADMIN", "ACCOUNTANT", "SECRETARY"];

const NAV_GROUPS: NavGroup[] = [
  {
    items: [
      { href: "/dashboard", label: "الرئيسية", icon: LayoutDashboard },
    ],
  },
  {
    label: "الإدارة القانونية",
    items: [
      { href: "/cases", label: "القضايا", icon: Briefcase },
      { href: "/hearings", label: "الجلسات", icon: Gavel, allowedRoles: ["ADMIN", "SENIOR_LAWYER", "LAWYER", "SECRETARY", "ACCOUNTANT"] },
      { href: "/memos", label: "المذكرات", icon: FileText, allowedRoles: ["ADMIN", "SENIOR_LAWYER", "LAWYER", "SECRETARY"] },
      { href: "/requests", label: "طلبات تداول الملفات", icon: ArrowLeftRight, allowedRoles: ["ADMIN", "SENIOR_LAWYER", "LAWYER", "SECRETARY"] },
      { href: "/requests", label: "طلبات تداول الملفات", icon: ArrowLeftRight, allowedRoles: ["ADMIN", "SENIOR_LAWYER", "LAWYER", "SECRETARY"] },
      { href: "/disputes", label: "النزاعات", icon: Handshake },
      {
        href: "/criminal-reports",
        label: "البلاغات الجنائية",
        icon: ShieldAlert,
        allowedRoles: ["ADMIN", "SENIOR_LAWYER", "LAWYER", "SECRETARY", "ACCOUNTANT"],
      },
      {
        href: "/labor-complaints",
        label: "الشكاوى العمالية",
        icon: HardHat,
        allowedRoles: ["ADMIN", "SENIOR_LAWYER", "LAWYER", "SECRETARY", "ACCOUNTANT"],
      },
    ],
  },
  {
    label: "التفويضات",
    items: [
      {
        href: "/delegations",
        label: "التوكيلات",
        icon: FileSignature,
        allowedRoles: ["ADMIN", "SENIOR_LAWYER", "LAWYER", "SECRETARY", "ACCOUNTANT"],
      },
      {
        href: "/mandates",
        label: "الإنابات",
        icon: ArrowLeftRight,
        allowedRoles: ["ADMIN", "SENIOR_LAWYER", "LAWYER", "SECRETARY", "ACCOUNTANT"],
      },
    ],
  },
  {
    label: "التواصل",
    items: [
      { href: "/messages", label: "المحادثات", icon: MessageCircle },
      {
        href: "/lawyer-assistant",
        label: "المساعد القانوني",
        icon: Brain,
        allowedRoles: ["ADMIN", "SENIOR_LAWYER", "LAWYER", "SECRETARY", "ACCOUNTANT"],
      },
    ],
  },
  {
    label: "العملاء والإداريين",
    items: [
      {
        href: "/clients",
        label: "العملاء",
        icon: Contact,
        allowedRoles: ["ADMIN", "SECRETARY", "LAWYER", "SENIOR_LAWYER"],
      },
      {
        href: "/clients/new",
        label: "تسجيل عميل",
        icon: UserPlus,
        allowedRoles: ["ADMIN", "SECRETARY"],
      },
    ],
  },
  {
    label: "المالية والمحاسبة",
    items: [
      {
        href: "/invoices",
        label: "الفواتير",
        icon: Receipt,
        allowedRoles: ["ADMIN", "SENIOR_LAWYER", "LAWYER", "SECRETARY", "ACCOUNTANT"],
      },
      {
        href: "/accounting",
        label: "نظرة عامة",
        icon: PieChart,
        allowedRoles: ACCOUNTING_ROLES,
      },
      {
        href: "/accounting/journal",
        label: "دفتر اليومية",
        icon: FileText,
        allowedRoles: ACCOUNTING_ROLES,
      },
      {
        href: "/accounting/accounts",
        label: "دليل الحسابات",
        icon: BookOpen,
        allowedRoles: ACCOUNTING_ROLES,
      },
      {
        href: "/accounting/expenses",
        label: "المصروفات",
        icon: Wallet,
        allowedRoles: ACCOUNTING_ROLES,
      },
      {
        href: "/accounting/receipts",
        label: "سندات القبض",
        icon: Banknote,
        allowedRoles: ACCOUNTING_ROLES,
      },
      {
        href: "/accounting/bank-accounts",
        label: "الحسابات البنكية",
        icon: Landmark,
        allowedRoles: ACCOUNTING_ROLES,
      },
      {
        href: "/accounting/client-accounts",
        label: "حسابات العملاء",
        icon: Users,
        allowedRoles: ACCOUNTING_ROLES,
      },
      {
        href: "/accounting/periods",
        label: "الفترات المالية",
        icon: CalendarRange,
        allowedRoles: ACCOUNTING_FINANCE_ROLES,
      },
      {
        href: "/accounting/tax-rates",
        label: "نسب الضريبة",
        icon: Percent,
        allowedRoles: ACCOUNTING_FINANCE_ROLES,
      },
      {
        href: "/accounting/dashboard",
        label: "لوحة المؤشرات",
        icon: Gauge,
        allowedRoles: ACCOUNTING_ROLES,
      },
      {
        href: "/accounting/time-entries",
        label: "سجل الوقت",
        icon: Timer,
        allowedRoles: ACCOUNTING_ROLES,
      },
      {
        href: "/accounting/credit-notes",
        label: "إشعارات الخصم والمدينة",
        icon: FileMinus,
        allowedRoles: ACCOUNTING_ROLES,
      },
      {
        href: "/accounting/vendor-bills",
        label: "فواتير الموردين",
        icon: FileText,
        allowedRoles: ACCOUNTING_ROLES,
      },
      {
        href: "/accounting/recurring-invoices",
        label: "الفواتير المتكررة",
        icon: Repeat,
        allowedRoles: ACCOUNTING_ROLES,
      },
      {
        href: "/accounting/reconciliation",
        label: "التسوية البنكية",
        icon: Scale,
        allowedRoles: ACCOUNTING_ROLES,
      },
      {
        href: "/accounting/budgets",
        label: "الميزانيات",
        icon: Target,
        allowedRoles: ACCOUNTING_ROLES,
      },
    ],
  },
  {
    label: "الجدول والمهام",
    items: [
      {
        href: "/calendar",
        label: "التقويم",
        icon: CalendarDays,
        allowedRoles: ["ADMIN", "SENIOR_LAWYER", "LAWYER", "SECRETARY", "ACCOUNTANT"],
      },
      { href: "/hearings", label: "الجلسات", icon: Gavel },
      {
        href: "/tasks",
        label: "المهام",
        icon: ClipboardList,
        allowedRoles: ["ADMIN", "SENIOR_LAWYER", "LAWYER", "SECRETARY", "ACCOUNTANT"],
      },
    ],
  },
  {
    label: "الموارد البشرية",
    items: [
      {
        href: "/hr/employees",
        label: "الموظفون",
        icon: Users,
        allowedRoles: HR_ROLES,
      },
      {
        href: "/hr/attendance",
        label: "الحضور والانصراف",
        icon: CalendarCheck,
        allowedRoles: HR_ROLES,
      },
      {
        href: "/hr/attendance-devices",
        label: "أجهزة البصمة",
        icon: Fingerprint,
        allowedRoles: HR_ROLES,
      },
      {
        href: "/hr/departments",
        label: "الأقسام",
        icon: Network,
        allowedRoles: HR_ROLES,
      },
      {
        href: "/hr/positions",
        label: "المسميات الوظيفية",
        icon: BadgeCheck,
        allowedRoles: HR_ROLES,
      },
    ],
  },
  {
    label: "المستندات",
    items: [
      {
        href: "/templates",
        label: "النماذج",
        icon: FileText,
        allowedRoles: ["ADMIN", "SENIOR_LAWYER", "LAWYER", "SECRETARY", "ACCOUNTANT"],
      },
      {
        href: "/documents",
        label: "المستندات",
        icon: FileSignature,
        allowedRoles: ["ADMIN", "SENIOR_LAWYER", "LAWYER", "SECRETARY", "ACCOUNTANT"],
      },
      {
        href: "/ocr",
        label: "OCR",
        icon: ScanText,
        allowedRoles: ["ADMIN", "SENIOR_LAWYER", "LAWYER", "SECRETARY", "ACCOUNTANT"],
      },
    ],
  },
  {
    label: "النظام",
    items: [
      {
        href: "/staff",
        label: "الموظفون",
        icon: Users,
        allowedRoles: ["ADMIN", "SECRETARY"],
      },
      { href: "/notifications", label: "الإشعارات", icon: Bell },
      {
        href: "/audit-logs",
        label: "سجل النشاط",
        icon: ShieldAlert,
        allowedRoles: ["ADMIN"],
      },
    ],
  },
];

interface SidebarProps {
  open?: boolean;
  onClose?: () => void;
  collapsed?: boolean;
  onCollapsedChange?: (collapsed: boolean) => void;
}

export function Sidebar({
  open,
  onClose,
  collapsed = false,
  onCollapsedChange,
}: SidebarProps) {
  const { logout } = useAuth();
  const [isLg, setIsLg] = useState(false);
  const [role, setRole] = useState<SystemRole | null>(null);
  useEffect(() => {
    const mq = window.matchMedia("(min-width: 1024px)");
    const next = () => setIsLg(mq.matches);
    next();
    mq.addEventListener("change", next);
    return () => mq.removeEventListener("change", next);
  }, []);
  useEffect(() => {
    setRole(getStoredUser()?.systemRole ?? null);
  }, []);

  const desktopRail = collapsed && isLg;

  function handleLogout() {
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
            transition={{ duration: 0.15 }}
            onClick={onClose}
            className="fixed inset-0 z-40 bg-black/30 backdrop-blur-sm lg:hidden"
          />
        ) : null}
      </AnimatePresence>

      <aside
        className={cn(
          "fixed inset-y-0 end-0 z-50 flex shrink-0 flex-col border-e border-[var(--color-navy-800)]/80 bg-[var(--color-navy-900)] text-[var(--color-navy-50)] transition-[width,transform] duration-300 ease-out motion-reduce:transition-none lg:translate-x-0",
          open ? "translate-x-0 rtl:translate-x-0" : "translate-x-full rtl:-translate-x-full",
          desktopRail ? "w-[4.75rem]" : "w-72",
        )}
      >
        <BrandHeader collapsed={desktopRail} />

        {open && isLg ? (
          <div className="hidden shrink-0 border-b border-[var(--color-navy-800)]/80 px-3 py-2 lg:flex lg:justify-center">
            <button
              type="button"
              aria-label={
                collapsed ? "توسيع القائمة الجانبية" : "طي القائمة الجانبية"
              }
              aria-expanded={!collapsed}
              title={collapsed ? "توسيع القائمة" : "طي القائمة"}
              className={cn(
                "flex size-7 items-center justify-center rounded-lg border border-[var(--color-navy-700)]/60 bg-[var(--color-navy-800)]/50 text-[var(--color-navy-400)] transition-all duration-200 hover:bg-[var(--color-navy-700)]/50 hover:text-[var(--color-gold-300)] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--color-gold-400)]",
              )}
              onClick={() => onCollapsedChange?.(!collapsed)}
            >
              <ChevronsRight
                className={cn(
                  "size-3.5 transition-transform duration-300 ease-out",
                  collapsed && "rotate-180",
                )}
              />
            </button>
          </div>
        ) : null}

        <NavGroups
          role={role}
          collapsed={desktopRail}
          onNavigate={isLg ? undefined : onClose}
        />

        <SidebarFooter collapsed={desktopRail} onLogout={handleLogout} />
      </aside>
    </>
  );
}

function BrandHeader({ collapsed }: { collapsed: boolean }) {
  return (
    <div
      className={cn(
        "border-b border-[var(--color-navy-800)]/80 transition-all duration-300 ease-out motion-reduce:transition-none",
        collapsed ? "px-3 py-5" : "px-5 py-5",
      )}
    >
      <div
        className={cn(
          "flex items-center gap-3 transition-all duration-300 ease-out motion-reduce:transition-none",
          collapsed && "justify-center",
        )}
      >
        <div className="flex size-10 shrink-0 items-center justify-center rounded-xl bg-[var(--color-gold-400)] text-[var(--color-navy-900)]">
          <Scale className="size-5" strokeWidth={2.5} />
        </div>
        <div
          className={cn(
            "min-w-0 flex-col overflow-hidden transition-all duration-300 ease-out motion-reduce:transition-none",
            collapsed
              ? "max-h-0 max-w-0 scale-95 opacity-0"
              : "max-h-28 max-w-[14rem] scale-100 opacity-100",
          )}
          aria-hidden={collapsed || undefined}
        >
          <span className="text-base font-extrabold tracking-tight">{APP_NAME}</span>
          <span className="text-[0.6875rem] text-[var(--color-navy-400)]">
            {APP_TAGLINE}
          </span>
        </div>
      </div>
    </div>
  );
}

const TASKS_ROLES: SystemRole[] = [
  "ADMIN",
  "SENIOR_LAWYER",
  "LAWYER",
  "SECRETARY",
  "ACCOUNTANT",
];

function NavGroups({
  role,
  onNavigate,
  collapsed,
}: {
  role: SystemRole | null;
  onNavigate?: () => void;
  collapsed: boolean;
}) {
  const pathname = usePathname();
  const messagesUnread = useUnreadMessages();
  const myOpenTasks = useMyOpenTasks();

  useEffect(() => {
    if (!role || !TASKS_ROLES.includes(role)) return;
    void refreshMyOpenTasks();
  }, [role]);

  const visibleGroups = NAV_GROUPS.map((group) => ({
    ...group,
    items: group.items.filter((item) => {
      if (!item.allowedRoles) return true;
      return role ? item.allowedRoles.includes(role) : false;
    }),
  })).filter((group) => group.items.length > 0);

  return (
    <nav
      className={cn(
        "min-h-0 flex-1 overflow-x-hidden overflow-y-auto transition-[padding] duration-300 ease-out motion-reduce:transition-none",
        collapsed ? "px-2 py-3" : "px-3 py-4",
      )}
    >
      <ul className="flex flex-col gap-0.5">
        {visibleGroups.map((group, gi) => (
          <li key={gi} className="flex flex-col">
            {group.label && !collapsed ? (
              <span className="mb-1.5 mt-3 px-3 text-[0.625rem] font-bold uppercase tracking-[0.14em] text-[var(--color-navy-500)] first:mt-0">
                {group.label}
              </span>
            ) : group.label && collapsed ? (
              <div className="mx-auto my-2 h-px w-5 bg-[var(--color-navy-700)]" />
            ) : null}
            {group.items.map((item) => {
              const active = (() => {
                if (!pathname) return false;
                if (item.href === "/clients") {
                  return (
                    pathname === "/clients" ||
                    (pathname.startsWith("/clients/") &&
                      !pathname.startsWith("/clients/new"))
                  );
                }
                if (item.href === "/clients/new") {
                  return pathname === "/clients/new";
                }
                if (item.href === "/accounting") {
                  return pathname === "/accounting";
                }
                return (
                  pathname === item.href ||
                  pathname.startsWith(`${item.href}/`)
                );
              })();
              const Icon = item.icon;
              return (
                <li key={item.href}>
                  <Link
                    href={item.href}
                    title={collapsed ? item.label : undefined}
                    aria-label={collapsed ? item.label : undefined}
                    onClick={onNavigate}
                    className={cn(
                      "group relative flex items-center rounded-lg py-2 text-[0.8125rem] font-medium transition-all duration-150",
                      collapsed ? "justify-center px-2" : "justify-start gap-3 px-3",
                      active
                        ? "bg-[var(--color-gold-400)]/10 text-[var(--color-gold-300)]"
                        : "text-[var(--color-navy-300)] hover:bg-white/[0.04] hover:text-[var(--color-navy-100)]",
                    )}
                  >
                    {active ? (
                      <motion.span
                        layoutId="sidebar-active"
                        className="pointer-events-none absolute inset-y-1.5 end-0 w-[3px] rounded-s-full bg-[var(--color-gold-400)]"
                        transition={{ type: "spring", stiffness: 380, damping: 30 }}
                      />
                    ) : null}
                    <span className="relative shrink-0">
                      <Icon
                        className={cn(
                          "size-[1.125rem] transition-colors duration-150",
                          active
                            ? "text-[var(--color-gold-400)]"
                            : "text-[var(--color-navy-500)] group-hover:text-[var(--color-navy-300)]",
                        )}
                      />
                      {item.href === "/messages" && messagesUnread > 0 && (
                        <span className="absolute -end-1.5 -top-1.5 flex min-w-[0.85rem] items-center justify-center rounded-full bg-[var(--color-danger)] px-0.5 text-[7px] font-bold leading-none text-white">
                          {messagesUnread > 99 ? "99+" : messagesUnread}
                        </span>
                      )}
                      {item.href === "/tasks" && myOpenTasks > 0 && (
                        <span className="absolute -end-1.5 -top-1.5 flex min-w-[0.85rem] items-center justify-center rounded-full bg-[var(--color-gold-400)] px-0.5 text-[7px] font-bold leading-none text-[var(--color-navy-900)]">
                          {myOpenTasks > 99 ? "99+" : myOpenTasks}
                        </span>
                      )}
                    </span>
                    <span
                      className={cn(
                        "min-w-0 truncate transition-[max-width,opacity] duration-200 ease-out",
                        collapsed
                          ? "me-0 max-w-0 overflow-hidden opacity-0"
                          : "max-w-[16rem] opacity-100",
                      )}
                    >
                      {item.label}
                    </span>
                  </Link>
                </li>
              );
            })}
          </li>
        ))}
      </ul>
    </nav>
  );
}

function SidebarFooter({
  collapsed,
  onLogout,
}: {
  collapsed: boolean;
  onLogout: () => void;
}) {
  return (
    <div
      className={cn(
        "mt-auto border-t border-[var(--color-navy-800)]/80 transition-[padding] duration-300 ease-out motion-reduce:transition-none",
        collapsed ? "px-2 py-3" : "px-3 py-3",
      )}
    >
      <Button
        type="button"
        variant="ghost"
        title={collapsed ? "تسجيل الخروج" : undefined}
        aria-label={collapsed ? "تسجيل الخروج" : undefined}
        className={cn(
          "w-full gap-0 text-[var(--color-navy-400)] hover:bg-white/[0.04] hover:text-[var(--color-navy-200)]",
          collapsed ? "justify-center px-2" : "justify-start gap-3 px-3",
        )}
        onClick={onLogout}
      >
        <LogOut className="size-[1.125rem] shrink-0" />
        <span
          className={cn(
            "overflow-hidden whitespace-nowrap transition-[max-width,opacity] duration-200 ease-out",
            collapsed ? "max-w-0 opacity-0" : "max-w-[12rem] opacity-100 ps-1",
          )}
        >
          تسجيل الخروج
        </span>
      </Button>
    </div>
  );
}
