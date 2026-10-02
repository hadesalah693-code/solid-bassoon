import { ClientRouteGuard } from "@/components/auth/client-route-guard";
import { ClientAppShell } from "@/components/layout/ClientAppShell";

export default function ClientPortalLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <ClientRouteGuard>
      <ClientAppShell>{children}</ClientAppShell>
    </ClientRouteGuard>
  );
}
