"use client";

import {
  createContext,
  useCallback,
  useContext,
  useMemo,
  useState,
  type ReactNode,
} from "react";
import { usePathname } from "next/navigation";

import {
  useLawyerAssistant,
  type LawyerAssistantState,
} from "@/hooks/use-lawyer-assistant";
import { parsePageContext } from "@/lib/assistant/page-context";
import type { PageAssistantContext } from "@/lib/assistant/types";

type AssistantContextValue = LawyerAssistantState & {
  open: boolean;
  openAssistant: () => void;
  closeAssistant: () => void;
  toggleAssistant: () => void;
  pageContext: PageAssistantContext;
};

const AssistantContext = createContext<AssistantContextValue | null>(null);

export function AssistantProvider({ children }: { children: ReactNode }) {
  const pathname = usePathname();
  const pageContext = useMemo(
    () => parsePageContext(pathname ?? ""),
    [pathname],
  );
  const [open, setOpen] = useState(false);

  const assistant = useLawyerAssistant({
    pageContext,
    syncPageCase: true,
  });

  const openAssistant = useCallback(() => setOpen(true), []);
  const closeAssistant = useCallback(() => setOpen(false), []);
  const toggleAssistant = useCallback(() => setOpen((v) => !v), []);

  const value = useMemo<AssistantContextValue>(
    () => ({
      ...assistant,
      open,
      openAssistant,
      closeAssistant,
      toggleAssistant,
      pageContext,
    }),
    [assistant, open, openAssistant, closeAssistant, toggleAssistant, pageContext],
  );

  return (
    <AssistantContext.Provider value={value}>
      {children}
    </AssistantContext.Provider>
  );
}

export function useAssistant(): AssistantContextValue {
  const ctx = useContext(AssistantContext);
  if (!ctx) {
    throw new Error("useAssistant must be used within AssistantProvider");
  }
  return ctx;
}
