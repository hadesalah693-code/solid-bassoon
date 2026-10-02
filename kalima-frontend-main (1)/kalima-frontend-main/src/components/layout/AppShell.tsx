"use client";

import { useLayoutEffect, useState } from "react";

import { AssistantProvider } from "@/components/assistant/AssistantProvider";
import { GlobalAssistant } from "@/components/assistant/GlobalAssistant";
import { cn } from "@/lib/utils/cn";
import { Sidebar } from "./Sidebar";
import { Topbar } from "./Topbar";

export function AppShell({ children }: { children: React.ReactNode }) {
  const [sidebarOpen, setSidebarOpen] = useState(false);
  const [sidebarCollapsed, setSidebarCollapsed] = useState(false);

  useLayoutEffect(() => {
    if (window.matchMedia("(min-width: 1024px)").matches) {
      setSidebarOpen(true);
    }
  }, []);

  return (
    <AssistantProvider>
      <div className="min-h-screen w-full bg-[var(--color-background)]">
        <Sidebar
          open={sidebarOpen}
          collapsed={sidebarCollapsed}
          onCollapsedChange={setSidebarCollapsed}
          onClose={() => setSidebarOpen(false)}
        />
        <div
          className={cn(
            "flex min-h-screen flex-col transition-[padding] duration-300 ease-out",
            sidebarOpen &&
              (sidebarCollapsed ? "lg:pe-[4.75rem]" : "lg:pe-72"),
          )}
        >
          <Topbar
            sidebarOpen={sidebarOpen}
            onToggleSidebar={() => setSidebarOpen((v) => !v)}
          />
          <main className="flex-1 px-4 py-5 sm:px-6 lg:px-8 lg:py-6">
            {children}
          </main>
        </div>
        <GlobalAssistant />
      </div>
    </AssistantProvider>
  );
}
