"use client";

import Link from "next/link";
import { Eye, MoreVertical, Star } from "lucide-react";

import { Button } from "@/components/ui/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { delegationsApi } from "@/lib/api/delegations";
import { notifyApiError } from "@/lib/feedback";

type Props = {
  delegationId: string;
  starred: boolean;
  onStarChange?: (starred: boolean) => void;
};

export function DelegationCardMenu({ delegationId, starred, onStarChange }: Props) {
  async function toggleStar() {
    const next = !starred;
    onStarChange?.(next);
    try {
      await delegationsApi.patch(delegationId, { starred: next });
    } catch (e) {
      onStarChange?.(starred);
      notifyApiError(e, "تعذّر تحديث التمييز");
    }
  }

  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <Button
          type="button"
          variant="ghost"
          size="icon"
          className="size-8 shrink-0 text-[var(--color-muted-foreground)] hover:text-[var(--color-foreground)]"
          aria-label="المزيد من الإجراءات"
        >
          <MoreVertical className="size-4" aria-hidden />
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end">
        <DropdownMenuItem asChild>
          <Link href={`/delegations/${delegationId}`} className="gap-2">
            <Eye className="size-4" aria-hidden />
            عرض التفاصيل
          </Link>
        </DropdownMenuItem>
        <DropdownMenuSeparator />
        <DropdownMenuItem onClick={() => void toggleStar()} className="gap-2">
          <Star className={starred ? "size-4 fill-current text-[var(--color-gold-500)]" : "size-4"} aria-hidden />
          {starred ? "إزالة من المميزة" : "تمييز التوكيل"}
        </DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  );
}
