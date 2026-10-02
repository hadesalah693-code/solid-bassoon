import { StaffRouteGuard } from "@/components/auth/staff-route-guard";
import { AppShell } from "@/components/layout/AppShell";

export default function DashboardLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <StaffRouteGuard>
      <AppShell>{children}</AppShell>
    </StaffRouteGuard>
  );
}