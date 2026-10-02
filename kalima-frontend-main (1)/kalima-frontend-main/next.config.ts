import type { NextConfig } from "next";
import withPWAInit from "@ducanh2912/next-pwa";

import { resolveApiBaseUrl } from "./src/lib/resolve-api-base-url";

const withPWA = withPWAInit({
  dest: "public",
  disable: process.env.NODE_ENV === "development",
  register: true,
  reloadOnOnline: true,
  cacheOnFrontEndNav: true,
  workboxOptions: {
    disableDevLogs: true,
    skipWaiting: true,
    clientsClaim: true,
  },
});

const nextConfig: NextConfig = {
  reactStrictMode: true,
  env: {
    NEXT_PUBLIC_API_BASE_URL: resolveApiBaseUrl(),
  },
  async rewrites() {
    // Let direct access on :3000 reach the backend too (the production entry
    // point is the Caddy proxy on :8081, but localhost:3000 should not 404).
    return [
      {
        source: "/api/:path*",
        destination: "http://localhost:8080/api/:path*",
      },
    ];
  },
  experimental: {
    optimizePackageImports: ["lucide-react", "framer-motion"],
  },
};

export default withPWA(nextConfig);
