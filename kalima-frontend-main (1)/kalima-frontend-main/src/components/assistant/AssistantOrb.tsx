"use client";

import { Brain } from "lucide-react";

import { useAssistant } from "@/components/assistant/AssistantProvider";
import { cn } from "@/lib/utils/cn";

type AssistantOrbProps = {
  className?: string;
};

export function AssistantOrb({ className }: AssistantOrbProps) {
  const { open, toggleAssistant, thinking, listening, voiceConversation } =
    useAssistant();

  const active = open || thinking || listening || voiceConversation;

  return (
    <button
      type="button"
      onClick={toggleAssistant}
      aria-label={open ? "إغلاق المساعد القانوني" : "فتح المساعد القانوني"}
      aria-expanded={open}
      className={cn(
        "fixed bottom-6 start-6 z-50 flex size-14 items-center justify-center rounded-full",
        "bg-gradient-to-br from-[var(--color-gold-400)] to-[var(--color-gold-600)]",
        "text-white shadow-lg shadow-[var(--color-gold-500)]/30",
        "transition-transform hover:scale-105 active:scale-95",
        "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--color-gold-400)] focus-visible:ring-offset-2",
        active && !open && "animate-pulse",
        className,
      )}
    >
      <Brain className="size-6" />
      {listening ? (
        <span className="absolute -top-0.5 -end-0.5 flex size-3.5">
          <span className="absolute inline-flex size-full animate-ping rounded-full bg-red-400 opacity-75" />
          <span className="relative inline-flex size-3.5 rounded-full bg-red-500" />
        </span>
      ) : null}
    </button>
  );
}
