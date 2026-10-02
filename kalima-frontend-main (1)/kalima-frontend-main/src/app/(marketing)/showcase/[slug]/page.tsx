import type { Metadata } from "next";
import { notFound } from "next/navigation";

import { ShowcaseSiteView } from "@/components/showcase/ShowcaseSiteView";
import { getShowcaseSite } from "@/lib/showcase/sites";

type Props = {
  params: Promise<{ slug: string }>;
};

// The site object carries a Lucide `icon` (a React component function) which
// cannot be serialized at build time during prerendering. Force dynamic
// rendering so the route is served on demand instead of statically generated.
export const dynamic = "force-dynamic";

export async function generateMetadata({ params }: Props): Promise<Metadata> {
  const { slug } = await params;
  const site = getShowcaseSite(slug);
  if (!site) return { title: "غير موجود" };
  return {
    title: site.firmName,
    description: site.description,
  };
}

export default async function ShowcaseSitePage({ params }: Props) {
  const { slug } = await params;
  const site = getShowcaseSite(slug);
  if (!site) notFound();
  return <ShowcaseSiteView site={site} />;
}
