"use client";

import { usePathname } from "next/navigation";

import { AssistantOrb } from "@/components/assistant/AssistantOrb";
import { AssistantOverlay } from "@/components/assistant/AssistantOverlay";

export function GlobalAssistant() {
  const pathname = usePathname();
  const hideFab = pathname === "/lawyer-assistant";

  return (
    <>
      {!hideFab ? <AssistantOrb /> : null}
      <AssistantOverlay />
    </>
  );
}
