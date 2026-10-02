import type { LucideIcon } from "lucide-react";
import { Briefcase, Heart, Shield } from "lucide-react";

export type ShowcaseSite = {
  slug: string;
  firmName: string;
  tagline: string;
  specialization: string;
  specializationEn: string;
  description: string;
  heroHeadline: string;
  heroSubheadline: string;
  icon: LucideIcon;
  theme: {
    primary: string;
    primaryLight: string;
    accent: string;
    gradient: string;
    heroBg: string;
  };
  stats: { value: string; label: string }[];
  services: { title: string; description: string }[];
  highlights: string[];
  contact: {
    phone: string;
    email: string;
    address: string;
    hours: string;
  };
  yearEstablished: number;
  casesWon: string;
};

export const SHOWCASE_SITES: ShowcaseSite[] = [
  {
    slug: "criminal-defense",
    firmName: "مكتب الدفاع الجنائي",
    tagline: "حماية حقوقك في مواجهة التهم الجنائية",
    specialization: "القانون الجنائي",
    specializationEn: "Criminal Law",
    description:
      "مكتب متخصص في الدفاع الجنائي والاستئناف، يقدّم تمثيلاً قانونياً قوياً أمام المحاكم الجزائية ونيابات الجمهورية.",
    heroHeadline: "دفاع جنائي متخصص",
    heroSubheadline:
      "نحمي حقوق المتهمين ونضمن محاكمة عادلة في كل مرحلة — من التحقيق حتى الاستئناف.",
    icon: Shield,
    theme: {
      primary: "#7f1d1d",
      primaryLight: "#991b1b",
      accent: "#d4af37",
      gradient: "linear-gradient(135deg, #450a0a 0%, #1e1b4b 50%, #0b1426 100%)",
      heroBg:
        "radial-gradient(ellipse 70% 50% at 80% 20%, rgba(127,29,29,0.35) 0%, transparent 60%), radial-gradient(ellipse 60% 40% at 10% 80%, rgba(30,45,78,0.4) 0%, transparent 50%)",
    },
    stats: [
      { value: "+320", label: "قضية جنائية" },
      { value: "94%", label: "نسبة النجاح" },
      { value: "18", label: "سنة خبرة" },
      { value: "24/7", label: "استجابة طوارئ" },
    ],
    services: [
      {
        title: "الدفاع أمام المحاكم الجزائية",
        description: "تمثيل شامل في جلسات المحاكمة والمرافعات الشفوية والكتابية.",
      },
      {
        title: "التحقيق والاستجواب",
        description: "حضور مع المتهم أثناء التحقيقات وضمان حقوقه الدستورية.",
      },
      {
        title: "الاستئناف والنقض",
        description: "مراجعة الأحكام والطعن أمام محاكم الاستئناف والنقض.",
      },
      {
        title: "الكفالة والإفراج المؤقت",
        description: "تقديم طلبات الكفالة والإفراج بكفالة في القضايا المناسبة.",
      },
    ],
    highlights: [
      "خبرة في قضايا الأموال العامة والجرائم الاقتصادية",
      "فريق متخصص في الدفاع عن المتهمين في قضايا المخدرات",
      "متابعة لحظية لمواعيد الجلسات والتحقيقات",
    ],
    contact: {
      phone: "+966 11 456 7890",
      email: "info@defense-law.sa",
      address: "الرياض — حي العليا، برج العدالة",
      hours: "الأحد – الخميس: 9 ص – 6 م | طوارئ: 24/7",
    },
    yearEstablished: 2008,
    casesWon: "+280",
  },
  {
    slug: "commercial-law",
    firmName: "مكتب الاستشارات التجارية",
    tagline: "شريكك القانوني في عالم الأعمال",
    specialization: "القانون التجاري",
    specializationEn: "Commercial Law",
    description:
      "مكتب رائد في الاستشارات التجارية وصياغة العقود وحل النزاعات بين الشركات، يخدم رواد الأعمال والمؤسسات الكبرى.",
    heroHeadline: "استشارات تجارية متكاملة",
    heroSubheadline:
      "نحمي مصالح شركتك ونصيغ عقوداً محكمة — من التأسيس حتى حل النزاعات التجارية.",
    icon: Briefcase,
    theme: {
      primary: "#1e2d4e",
      primaryLight: "#2a3d65",
      accent: "#d4af37",
      gradient: "linear-gradient(135deg, #0b1426 0%, #1e2d4e 50%, #2a3d65 100%)",
      heroBg:
        "radial-gradient(ellipse 80% 60% at 75% 15%, rgba(212,175,55,0.18) 0%, transparent 55%), radial-gradient(ellipse 70% 50% at 15% 85%, rgba(30,45,78,0.35) 0%, transparent 50%)",
    },
    stats: [
      { value: "+450", label: "عقد تجاري" },
      { value: "+120", label: "شركة عميلة" },
      { value: "22", label: "سنة خبرة" },
      { value: "98%", label: "رضا العملاء" },
    ],
    services: [
      {
        title: "تأسيس الشركات",
        description: "إعداد عقود التأسيس والنظام الأساسي والتراخيص التجارية.",
      },
      {
        title: "صياغة العقود",
        description: "عقود بيع وشراء، وكالات تجارية، اتفاقيات شراكة وامتياز.",
      },
      {
        title: "حل النزاعات التجارية",
        description: "التحكيم والوساطة والتقاضي في المنازعات بين الشركاء والموردين.",
      },
      {
        title: "الامتثال والحوكمة",
        description: "مراجعة السياسات الداخلية وضمان الامتثال للأنظمة السعودية.",
      },
    ],
    highlights: [
      "خبرة في عقود الامتياز التجاري والاستثمار الأجنبي",
      "فريق ثنائي اللغة للتعامل مع الشركات الدولية",
      "إدارة متكاملة للنزاعات عبر نظام كلمة",
    ],
    contact: {
      phone: "+966 11 234 5678",
      email: "contact@commercial-law.sa",
      address: "جدة — كورنيش البحر الأحمر، مركز الأعمال",
      hours: "الأحد – الخميس: 8:30 ص – 5:30 م",
    },
    yearEstablished: 2004,
    casesWon: "+380",
  },
  {
    slug: "family-law",
    firmName: "مكتب الأسرة والأحوال الشخصية",
    tagline: "نرافقك بلطف في أصعب اللحظات",
    specialization: "الأحوال الشخصية",
    specializationEn: "Family Law",
    description:
      "مكتب متخصص في قضايا الأسرة — الطلاق، الحضانة، النفقة، والمواريث — بأسلوب إنساني يحفظ كرامة جميع الأطراف.",
    heroHeadline: "قضايا أسرية بحساسية واحتراف",
    heroSubheadline:
      "نفهم أن قضايا الأسرة شخصية — نقدّم استشارة هادئة وحلولاً عادلة تحفظ مصالح الأطفال والعائلة.",
    icon: Heart,
    theme: {
      primary: "#581c87",
      primaryLight: "#7e22ce",
      accent: "#e879a0",
      gradient: "linear-gradient(135deg, #3b0764 0%, #581c87 40%, #1e1b4b 100%)",
      heroBg:
        "radial-gradient(ellipse 70% 55% at 70% 25%, rgba(126,34,206,0.25) 0%, transparent 55%), radial-gradient(ellipse 60% 45% at 20% 75%, rgba(232,121,160,0.12) 0%, transparent 50%)",
    },
    stats: [
      { value: "+600", label: "قضية أسرية" },
      { value: "91%", label: "تسوية ودية" },
      { value: "15", label: "سنة خبرة" },
      { value: "100%", label: "سرية تامة" },
    ],
    services: [
      {
        title: "الطلاق والخلع",
        description: "إجراءات الطلاق الاتفاقي والخلع مع صياغة اتفاقيات التسوية.",
      },
      {
        title: "الحضانة والزيارة",
        description: "تنظيم حقوق الحضانة والرؤية بما يخدم مصلحة الأطفال.",
      },
      {
        title: "النفقة والمهر",
        description: "حساب النفقة وتقديم الطلبات وتنفيذ الأحكام.",
      },
      {
        title: "المواريث والوصايا",
        description: "قسمة التركات وصياغة الوصايا وفق أحكام الشريعة.",
      },
    ],
    highlights: [
      "جلسات استشارة خاصة ببيئة آمنة وسرية",
      "خبرة في قضايا الحضانة الدولية",
      "وساطة أسرية قبل اللجوء للقضاء",
    ],
    contact: {
      phone: "+966 11 345 6789",
      email: "care@family-law.sa",
      address: "الرياض — حي الملز، مجمع الاستشارات",
      hours: "الأحد – الخميس: 10 ص – 7 م | السبت: 10 ص – 2 م",
    },
    yearEstablished: 2011,
    casesWon: "+520",
  },
];

export function getShowcaseSite(slug: string): ShowcaseSite | undefined {
  return SHOWCASE_SITES.find((s) => s.slug === slug);
}

export function getShowcaseSlugs(): string[] {
  return SHOWCASE_SITES.map((s) => s.slug);
}
