"use client";

import { useState } from "react";
import Link from "next/link";
import { MoreHorizontal, Mail, Phone, KeyRound, Ban, Pencil, Loader2 } from "lucide-react";

import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { Avatar, AvatarFallback } from "@/components/ui/avatar";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  ActiveStatusBadge,
  RoleBadge,
} from "@/components/shared/StatusBadge";
import { formatRelative } from "@/lib/utils/format-date";
import { staffResetPasswordSchema } from "@/lib/validation/staff";
import { staffApi } from "@/lib/api/staff";
import { ApiRequestError } from "@/lib/api/client";
import type { Staff } from "@/types/staff";

function initials(name: string) {
  return name
    .split(" ")
    .filter(Boolean)
    .slice(0, 2)
    .map((p) => p[0])
    .join("");
}

type StaffTableProps = {
  staff: Staff[];
  canManage: boolean;
  currentUserId: string | null;
  onRefresh: () => void;
  onNotify: (message: string) => void;
};

/**
 * Table row actions: edit link, reset password, enable/disable.
 * All mutations require {@link canManage} (API: ADMIN for write operations).
 * Self-disable is blocked by the backend — we disable the option in the menu.
 */
export function StaffTable({
  staff,
  canManage,
  currentUserId,
  onRefresh,
  onNotify,
}: StaffTableProps) {
  const [resetFor, setResetFor] = useState<Staff | null>(null);
  const [toggleFor, setToggleFor] = useState<Staff | null>(null);
  const [newPassword, setNewPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [actionError, setActionError] = useState<string | null>(null);
  const [pending, setPending] = useState(false);

  function closeOverlays() {
    setResetFor(null);
    setToggleFor(null);
    setNewPassword("");
    setConfirmPassword("");
    setActionError(null);
    setPending(false);
  }

  async function submitReset() {
    if (!resetFor) return;
    setActionError(null);
    const parsed = staffResetPasswordSchema.safeParse({
      newPassword,
      confirm: confirmPassword,
    });
    if (!parsed.success) {
      const c = parsed.error.flatten().fieldErrors;
      setActionError(
        c.newPassword?.[0] ?? c.confirm?.[0] ?? "تحقق من كلمات المرور",
      );
      return;
    }
    setPending(true);
    try {
      await staffApi.resetPassword(resetFor.id, {
        newPassword: parsed.data.newPassword,
      });
      onNotify("تم تعيين كلمة مرور جديدة");
      onRefresh();
      closeOverlays();
    } catch (e) {
      if (e instanceof ApiRequestError) {
        setActionError(e.details?.message ?? e.message);
      } else {
        setActionError("تعذر إعادة تعيين كلمة المرور");
      }
    } finally {
      setPending(false);
    }
  }

  async function submitToggle() {
    if (!toggleFor) return;
    setActionError(null);
    setPending(true);
    try {
      if (toggleFor.isActive) {
        await staffApi.disable(toggleFor.id);
        onNotify("تم إيقاف الحساب");
      } else {
        await staffApi.enable(toggleFor.id);
        onNotify("تم تفعيل الحساب");
      }
      onRefresh();
      closeOverlays();
    } catch (e) {
      if (e instanceof ApiRequestError) {
        setActionError(e.details?.message ?? e.message);
      } else {
        setActionError("تعذر تنفيذ العملية");
      }
    } finally {
      setPending(false);
    }
  }

  return (
    <>
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead>الموظف</TableHead>
            <TableHead>الدور</TableHead>
            <TableHead>التواصل</TableHead>
            <TableHead>آخر دخول</TableHead>
            <TableHead>الحالة</TableHead>
            <TableHead className="text-end">إجراءات</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {staff.map((s) => {
            const isSelf = s.id === currentUserId;
            return (
              <TableRow key={s.id}>
                <TableCell>
                  <div className="flex items-center gap-3">
                    <Avatar className="size-9">
                      <AvatarFallback>
                        {initials(s.fullName) || "?"}
                      </AvatarFallback>
                    </Avatar>
                    <div className="min-w-0">
                      <p className="truncate text-sm font-semibold text-[var(--color-foreground)]">
                        {s.fullName}
                      </p>
                      <p className="truncate text-xs text-[var(--color-muted-foreground)] ltr-nums">
                        {s.email ?? "—"}
                      </p>
                    </div>
                  </div>
                </TableCell>
                <TableCell>
                  <RoleBadge role={s.systemRole} />
                </TableCell>
                <TableCell>
                  <div className="flex flex-col gap-0.5 text-xs text-[var(--color-muted-foreground)]">
                    <span className="inline-flex items-center gap-1.5 ltr-nums">
                      <Mail className="size-3.5" /> {s.email ?? "—"}
                    </span>
                    {s.phone ? (
                      <span className="inline-flex items-center gap-1.5 ltr-nums">
                        <Phone className="size-3.5" /> {s.phone}
                      </span>
                    ) : null}
                  </div>
                </TableCell>
                <TableCell className="text-xs text-[var(--color-muted-foreground)]">
                  {s.lastLoginAt ? (
                    formatRelative(s.lastLoginAt)
                  ) : (
                    <span className="italic">لم يسجّل بعد</span>
                  )}
                </TableCell>
                <TableCell>
                  <ActiveStatusBadge active={s.isActive} />
                </TableCell>
                <TableCell className="text-end">
                  {canManage ? (
                    <DropdownMenu>
                      <DropdownMenuTrigger asChild>
                        <Button
                          variant="ghost"
                          size="icon"
                          aria-label="إجراءات"
                        >
                          <MoreHorizontal className="size-4" />
                        </Button>
                      </DropdownMenuTrigger>
                      <DropdownMenuContent align="end">
                        <DropdownMenuItem asChild>
                          <Link
                            href={`/staff/${s.id}`}
                            className="flex cursor-pointer items-center gap-2"
                          >
                            <Pencil className="size-4 text-[var(--color-muted-foreground)]" />
                            تعديل
                          </Link>
                        </DropdownMenuItem>
                        <DropdownMenuItem
                          onSelect={(e) => {
                            e.preventDefault();
                            setActionError(null);
                            setNewPassword("");
                            setConfirmPassword("");
                            setResetFor(s);
                          }}
                        >
                          <KeyRound className="size-4 text-[var(--color-muted-foreground)]" />
                          إعادة تعيين كلمة المرور
                        </DropdownMenuItem>
                        <DropdownMenuSeparator />
                        <DropdownMenuItem
                          disabled={isSelf && s.isActive}
                          className="text-[var(--color-danger)] focus:text-[var(--color-danger)]"
                          title={
                            isSelf && s.isActive
                              ? "لا يمكنك إيقاف حسابك (السيرفر يرفض)"
                              : undefined
                          }
                          onSelect={(e) => {
                            e.preventDefault();
                            setActionError(null);
                            setToggleFor(s);
                          }}
                        >
                          <Ban className="size-4" />
                          {s.isActive ? "إيقاف الحساب" : "إعادة تفعيل"}
                        </DropdownMenuItem>
                      </DropdownMenuContent>
                    </DropdownMenu>
                  ) : (
                    <span className="text-xs text-[var(--color-muted-foreground)]">
                      —
                    </span>
                  )}
                </TableCell>
              </TableRow>
            );
          })}
        </TableBody>
      </Table>

      {resetFor ? (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4"
          role="dialog"
          aria-modal
          aria-labelledby="reset-pw-title"
        >
          <button
            type="button"
            className="absolute inset-0 cursor-default"
            aria-label="إغلاق"
            onClick={closeOverlays}
          />
          <div className="relative z-10 w-full max-w-md rounded-2xl border border-[var(--color-border)] bg-[var(--color-surface)] p-6 shadow-[var(--shadow-soft)]">
            <h3
              id="reset-pw-title"
              className="text-base font-semibold text-[var(--color-foreground)]"
            >
              إعادة تعيين كلمة المرور
            </h3>
            <p className="mt-1 text-sm text-[var(--color-muted-foreground)] ltr-nums">
              {resetFor.email}
            </p>
            {actionError ? (
              <p className="mt-3 text-sm text-[var(--color-danger)]">
                {actionError}
              </p>
            ) : null}
            <div className="mt-4 space-y-3">
              <div className="space-y-1.5">
                <Label htmlFor="rp1">كلمة مرور جديدة</Label>
                <Input
                  id="rp1"
                  type="password"
                  dir="ltr"
                  value={newPassword}
                  onChange={(e) => setNewPassword(e.target.value)}
                  autoComplete="new-password"
                />
              </div>
              <div className="space-y-1.5">
                <Label htmlFor="rp2">تأكيد</Label>
                <Input
                  id="rp2"
                  type="password"
                  dir="ltr"
                  value={confirmPassword}
                  onChange={(e) => setConfirmPassword(e.target.value)}
                  autoComplete="new-password"
                />
              </div>
            </div>
            <div className="mt-6 flex flex-wrap justify-end gap-2">
              <Button
                type="button"
                variant="outline"
                onClick={closeOverlays}
                disabled={pending}
              >
                إلغاء
              </Button>
              <Button
                type="button"
                variant="gold"
                onClick={submitReset}
                disabled={pending}
              >
                {pending ? (
                  <Loader2 className="size-4 animate-spin" />
                ) : (
                  "تأكيد"
                )}
              </Button>
            </div>
          </div>
        </div>
      ) : null}

      {toggleFor ? (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4"
          role="dialog"
          aria-modal
          aria-labelledby="toggle-title"
        >
          <button
            type="button"
            className="absolute inset-0 cursor-default"
            aria-label="إغلاق"
            onClick={closeOverlays}
          />
          <div className="relative z-10 w-full max-w-md rounded-2xl border border-[var(--color-border)] bg-[var(--color-surface)] p-6 shadow-[var(--shadow-soft)]">
            <h3
              id="toggle-title"
              className="text-base font-semibold text-[var(--color-foreground)]"
            >
              {toggleFor.isActive ? "إيقاف الحساب؟" : "تفعيل الحساب؟"}
            </h3>
            <p className="mt-1 text-sm text-[var(--color-muted-foreground)]">
              {toggleFor.fullName} — {toggleFor.email}
            </p>
            {actionError ? (
              <p className="mt-3 text-sm text-[var(--color-danger)]">
                {actionError}
              </p>
            ) : null}
            <div className="mt-6 flex flex-wrap justify-end gap-2">
              <Button
                type="button"
                variant="outline"
                onClick={closeOverlays}
                disabled={pending}
              >
                إلغاء
              </Button>
              <Button
                type="button"
                variant={toggleFor.isActive ? "destructive" : "gold"}
                onClick={submitToggle}
                disabled={pending}
              >
                {pending ? (
                  <Loader2 className="size-4 animate-spin" />
                ) : (
                  "تأكيد"
                )}
              </Button>
            </div>
          </div>
        </div>
      ) : null}
    </>
  );
}
