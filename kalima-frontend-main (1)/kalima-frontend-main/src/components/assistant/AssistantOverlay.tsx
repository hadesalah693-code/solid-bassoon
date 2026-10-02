"use client";

import { useEffect } from "react";

import { AssistantChatUI } from "@/components/assistant/AssistantChatUI";
import { useAssistant } from "@/components/assistant/AssistantProvider";
import { cn } from "@/lib/utils/cn";

export function AssistantOverlay() {
  const { open, closeAssistant } = useAssistant();

  useEffect(() => {
    if (!open) return;
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") closeAssistant();
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [open, closeAssistant]);

  if (!open) return null;

  return (
    <>
      <button
        type="button"
        aria-label="إغلاق المساعد"
        className="fixed inset-0 z-40 bg-black/40 backdrop-blur-[2px]"
        onClick={closeAssistant}
      />
      <div
        role="dialog"
        aria-modal="true"
        aria-label="المساعد القانوني"
        className={cn(
          "fixed z-50 flex flex-col bg-white shadow-2xl",
          "inset-x-0 bottom-0 max-h-[92vh] rounded-t-2xl",
          "sm:inset-x-auto sm:bottom-6 sm:start-6 sm:end-auto",
          "sm:h-[min(640px,calc(100vh-3rem))] sm:w-[min(420px,calc(100vw-3rem))] sm:rounded-2xl",
        )}
      >
        <AssistantChatUI variant="overlay" onClose={closeAssistant} />
      </div>
    </>
  );
}
