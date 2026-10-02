import type { Metadata } from "next";

export const metadata: Metadata = {
  title: "تسجيل عميل",
};

export default function ClientRegistrationLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return children;
}
