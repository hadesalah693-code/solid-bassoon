"use client";

import { Toaster } from "@/components/ui/sonner";
import {
  AuthBootstrapGate,
  AuthProvider,
} from "@/components/auth/auth-provider";

export function AppProviders({ children }: { children: React.ReactNode }) {
  return (
    <AuthProvider>
      <AuthBootstrapGate>
        {children}
      </AuthBootstrapGate>
      <Toaster />
    </AuthProvider>
  );
}
