import { ImageResponse } from "next/og";

import { getAppIconDataUrl } from "@/lib/pwa/app-icon-data";

export const size = { width: 32, height: 32 };
export const contentType = "image/png";

export default function Icon() {
  return new ImageResponse(
    (
      // eslint-disable-next-line @next/next/no-img-element -- ImageResponse requires img
      <img
        alt=""
        src={getAppIconDataUrl()}
        width={32}
        height={32}
        style={{ borderRadius: 8 }}
      />
    ),
    { ...size },
  );
}
