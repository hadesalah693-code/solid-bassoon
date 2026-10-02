"use client";

import { Star } from "lucide-react";

import { Button } from "@/components/ui/button";
import { delegationsApi } from "@/lib/api/delegations";
import { notifyApiError } from "@/lib/feedback";
import { cn } from "@/lib/utils/cn";

type DelegationStarButtonProps = {
  delegationId: string;
  starred: boolean;
  onChange?: (starred: boolean) => void;
  className?: string;
  size?: "sm" | "md";
};

export function DelegationStarButton({
  delegationId,
  starred,
  onChange,
  className,
  size = "sm",
}: DelegationStarButtonProps) {
  async function toggle() {
    const next = !starred;
    onChange?.(next);
    try {
      await delegationsApi.patch(delegationId, { starred: next });
    } catch (e) {
      onChange?.(starred);
      notifyApiError(e, "تعذّر تحديث التمييز");
    }
  }

  return (
    <Button
      type="button"
      variant="ghost"
      size={size === "sm" ? "icon" : "sm"}
      className={cn(
        size === "sm" ? "size-8 shrink-0" : "gap-1.5",
        starred ? "text-[var(--color-gold-500)]" : "text-[var(--color-muted-foreground)]",
        className,
      )}
      aria-label={starred ? "إزالة من المميزة" : "إضافة للمميزة"}
      title={starred ? "إزالة من المميزة" : "تمييز"}
      onClick={() => void toggle()}
    >
      <Star className={cn("size-4", starred && "fill-current")} aria-hidden />
      {size === "md" ? (starred ? "مميز" : "تمييز") : null}
    </Button>
  );
}
