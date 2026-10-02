"use client";

import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
  type ReactNode,
} from "react";

import type { LoginResponse } from "@/lib/api/auth-types";
import { RouteGuardShell } from "@/components/auth/route-guard-shell";
import { getAuthSnapshot, subscribeAuth } from "@/lib/auth/auth-store";
import {
  applyLoginSession,
  bootstrapSession,
  logoutSession,
  refreshSession,
  syncSessionFromStorage,
} from "@/lib/auth/auth-session-service";
import { applySyncSessionRestore } from "@/lib/auth/session-sync";
import { getExpiresAt, isTokenExpired } from "@/lib/auth/session";
import { STORAGE_KEYS, TOKEN_REFRESH_SKEW_MS } from "@/lib/constants";
import type { CurrentUser } from "@/types/user";

type AuthContextValue = {
  user: CurrentUser | null;
  accessToken: string | null;
  isAuthenticated: boolean;
  loading: boolean;
  bootstrap: () => Promise<void>;
  refreshSession: () => Promise<boolean>;
  logout: () => Promise<void>;
  loginWithResponse: (data: LoginResponse) => void;
};

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<CurrentUser | null>(null);
  const [accessToken, setAccessToken] = useState<string | null>(null);
  const [isAuthenticated, setIsAuthenticated] = useState(false);
  const [loading, setLoading] = useState(true);
  const refreshTimerRef = useRef<number | null>(null);

  const applySnapshot = useCallback(() => {
    const snap = getAuthSnapshot();
    setUser(snap.user);
    setAccessToken(snap.accessToken);
    setIsAuthenticated(snap.isAuthenticated);
  }, []);

  const scheduleProactiveRefresh = useCallback(() => {
    if (refreshTimerRef.current != null) {
      window.clearTimeout(refreshTimerRef.current);
      refreshTimerRef.current = null;
    }
    const expiresAt = getExpiresAt();
    if (!expiresAt) return;
    const delay = expiresAt.getTime() - Date.now() - TOKEN_REFRESH_SKEW_MS;
    if (delay <= 0) {
      void refreshSession().then((ok) => {
        if (ok) applySnapshot();
      });
      return;
    }
    refreshTimerRef.current = window.setTimeout(() => {
      void refreshSession().then((ok) => {
        if (ok) applySnapshot();
        scheduleProactiveRefresh();
      });
    }, delay);
  }, [applySnapshot]);

  const bootstrap = useCallback(async () => {
    const sync = applySyncSessionRestore();
    applySnapshot();

    if (sync !== "needs_refresh") {
      setLoading(false);
      if (sync === "authenticated") {
        scheduleProactiveRefresh();
      }
      return;
    }

    setLoading(true);
    await bootstrapSession();
    applySnapshot();
    setLoading(false);
    if (getAuthSnapshot().isAuthenticated) {
      scheduleProactiveRefresh();
    }
  }, [applySnapshot, scheduleProactiveRefresh]);

  const logout = useCallback(async () => {
    if (refreshTimerRef.current != null) {
      window.clearTimeout(refreshTimerRef.current);
    }
    await logoutSession(true);
  }, []);

  const loginWithResponse = useCallback(
    (data: LoginResponse) => {
      applyLoginSession(data);
      applySnapshot();
      scheduleProactiveRefresh();
    },
    [applySnapshot, scheduleProactiveRefresh],
  );

  useEffect(() => {
    void bootstrap();
  }, [bootstrap]);

  useEffect(() => {
    return subscribeAuth(() => {
      applySnapshot();
    });
  }, [applySnapshot]);

  useEffect(() => {
    const onStorage = (event: StorageEvent) => {
      if (
        event.key !== STORAGE_KEYS.token &&
        event.key !== STORAGE_KEYS.user &&
        event.key !== STORAGE_KEYS.expiresAt
      ) {
        return;
      }
      syncSessionFromStorage();
      applySnapshot();
      if (getAuthSnapshot().isAuthenticated) {
        scheduleProactiveRefresh();
      }
    };
    window.addEventListener("storage", onStorage);
    return () => window.removeEventListener("storage", onStorage);
  }, [applySnapshot, scheduleProactiveRefresh]);

  useEffect(() => {
    const onVisibility = () => {
      if (document.visibilityState !== "visible") return;
      if (!isAuthenticated) return;
      if (isTokenExpired(TOKEN_REFRESH_SKEW_MS)) {
        void refreshSession().then((ok) => {
          if (ok) {
            applySnapshot();
            scheduleProactiveRefresh();
          }
        });
      }
    };
    document.addEventListener("visibilitychange", onVisibility);
    return () => document.removeEventListener("visibilitychange", onVisibility);
  }, [isAuthenticated, applySnapshot, scheduleProactiveRefresh]);

  const value = useMemo<AuthContextValue>(
    () => ({
      user,
      accessToken,
      isAuthenticated,
      loading,
      bootstrap,
      refreshSession,
      logout,
      loginWithResponse,
    }),
    [
      user,
      accessToken,
      isAuthenticated,
      loading,
      bootstrap,
      logout,
      loginWithResponse,
    ],
  );

  return (
    <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
  );
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) {
    throw new Error("useAuth must be used within AuthProvider");
  }
  return ctx;
}

export function AuthBootstrapGate({ children }: { children: ReactNode }) {
  const { loading } = useAuth();

  return (
    <>
      {loading ? (
        <div className="fixed inset-0 z-[100] bg-[var(--color-background)]">
          <RouteGuardShell label="جارٍ التحقق من الجلسة…" />
        </div>
      ) : null}
      {children}
    </>
  );
}
