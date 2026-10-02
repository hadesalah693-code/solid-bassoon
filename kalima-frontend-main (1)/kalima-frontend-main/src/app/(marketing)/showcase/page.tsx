import type { Metadata } from "next";

import { ShowcasePortfolio } from "@/components/showcase/ShowcasePortfolio";

export const metadata: Metadata = {
  title: "معرض الأعمال",
  description: "مواقع مكاتب محاماة بتخصصات مختلفة — نماذج سابقة للعرض على العملاء",
};

export default function ShowcasePage() {
  return <ShowcasePortfolio />;
}
