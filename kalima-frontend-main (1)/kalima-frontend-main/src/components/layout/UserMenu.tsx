"use client";

import Link from "next/link";
import { KeyRound, LogOut } from "lucide-react";

import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { Avatar, AvatarFallback } from "@/components/ui/avatar";
import type { CurrentUser } from "@/types/user";

const ROLE_LABELS: Record<CurrentUser["systemRole"], string> = {
  ADMIN: "مدير",
  SENIOR_LAWYER: "محامي أول",
  LAWYER: "محامي",
  SECRETARY: "سكرتير",
  ACCOUNTANT: "محاسب",
  CLIENT: "عميل",
  CLIENT_USER: "بوابة العميل",
};

function initials(name: string) {
  return name
    .split(" ")
    .slice(0, 2)
    .map((part) => part[0])
    .join("");
}

export function UserMenu({
  user,
  onLogout,
}: {
  user: CurrentUser;
  onLogout: () => void;
}) {
  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <button className="flex items-center gap-2.5 rounded-xl px-2 py-1.5 text-start transition-colors hover:bg-[var(--color-surface-muted)]">
          <Avatar className="size-8">
            <AvatarFallback>{initials(user.fullName)}</AvatarFallback>
          </Avatar>
          <div className="hidden flex-col leading-tight md:flex">
            <span className="text-[0.8125rem] font-bold">{user.fullName}</span>
            <span className="text-[0.6875rem] text-[var(--color-muted-foreground)]">
              {ROLE_LABELS[user.systemRole]}
            </span>
          </div>
        </button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end" className="w-56">
        <DropdownMenuLabel>
          <div className="flex flex-col">
            <span className="text-sm font-bold">{user.fullName}</span>
            <span className="text-xs font-normal text-[var(--color-muted-foreground)] ltr-nums" dir="ltr">
              {user.email}
            </span>
          </div>
        </DropdownMenuLabel>
        <DropdownMenuSeparator />
        {user.systemRole !== "CLIENT" && (
          <>
            <DropdownMenuItem asChild>
              <Link
                href="/account/password"
                className="flex cursor-pointer items-center gap-2"
              >
                <KeyRound className="size-4 text-[var(--color-muted-foreground)]" />
                تغيير كلمة المرور
              </Link>
            </DropdownMenuItem>
            <DropdownMenuSeparator />
          </>
        )}
        <DropdownMenuItem
          onSelect={(e) => {
            e.preventDefault();
            onLogout();
          }}
          className="text-[var(--color-danger)] focus:text-[var(--color-danger)]"
        >
          <LogOut className="size-4" />
          تسجيل الخروج
        </DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  );
}
