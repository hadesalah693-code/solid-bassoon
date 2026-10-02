import { readFileSync } from "fs";
import { join } from "path";

let cachedIconDataUrl: string | null = null;

/** PNG app icon for Next.js metadata routes (build-time). */
export function getAppIconDataUrl(): string {
  if (!cachedIconDataUrl) {
    const buffer = readFileSync(
      join(process.cwd(), "public/icons/icon-512.png"),
    );
    cachedIconDataUrl = `data:image/png;base64,${buffer.toString("base64")}`;
  }
  return cachedIconDataUrl;
}
