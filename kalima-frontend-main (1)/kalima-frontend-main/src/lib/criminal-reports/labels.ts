import type { CriminalReportPartyCapacityDto, CriminalReportStatusDto } from '@/types/criminal-report';

export const CRIMINAL_REPORT_STATUS_AR: Record<CriminalReportStatusDto, string> = {
  UNDER_INVESTIGATION: 'قيد التحقيق',
  REFERRAL: 'إحالة',
  ARCHIVED: 'حفظ',
  FINAL_ARCHIVED: 'حفظ نهائي',
};

export const CRIMINAL_REPORT_STATUS_TONE: Record<
  CriminalReportStatusDto,
  "warning" | "info" | "neutral" | "success"
> = {
  UNDER_INVESTIGATION: "warning",
  REFERRAL: "info",
  ARCHIVED: "neutral",
  FINAL_ARCHIVED: "success",
};

export const CRIMINAL_REPORT_WORKFLOW_STEPS: {
  status: CriminalReportStatusDto;
  label: string;
  caption?: string;
}[] = [
  { status: 'UNDER_INVESTIGATION', label: 'قيد التحقيق' },
  { status: 'REFERRAL', label: 'إحالة', caption: 'فتح قضية في المحكمة الجنائية' },
  { status: 'ARCHIVED', label: 'حفظ' },
  { status: 'FINAL_ARCHIVED', label: 'حفظ نهائي' },
];

export const CRIMINAL_REPORT_PARTY_CAPACITY_AR: Record<CriminalReportPartyCapacityDto, string> = {
  PLAINTIFF: 'مدعى',
  DEFENDANT: 'مدعى عليه',
  COMPLAINANT: 'شاكي',
  ACCUSED: 'متهم',
  CIVIL_CLAIMANT: 'مدعى بالحق المدني',
  APPELLANT: 'مستأنف',
  APPELLEE: 'مستأنف ضده',
  PETITIONER: 'طالب',
  RESPONDENT: 'مطلوب ضده',
  WITNESS: 'شاهد',
  OTHER: 'أخرى',
};

export const CRIMINAL_REPORT_PARTY_CAPACITY_OPTIONS = (Object.entries(CRIMINAL_REPORT_PARTY_CAPACITY_AR) as [CriminalReportPartyCapacityDto, string][]).map(([value, label]) => ({ value, label }));

export const GRIEVANCE_PERIOD_UNIT_OPTIONS = [
  { value: 'DAY', label: 'يوم' },
  { value: 'WEEK', label: 'أسبوع' },
  { value: 'MONTH', label: 'شهر' },
] as const;

export const CRIMINAL_REPORT_LOCATION_TYPE_OPTIONS = [
  { value: 'CRIME_SCENE', label: 'مسقط الجريمة' },
  { value: 'INCIDENT', label: 'موقع الحادث' },
  { value: 'CLIENT_ADDRESS', label: 'عنوان الموكل' },
  { value: 'WITNESS', label: 'موقع الشاهد' },
  { value: 'OTHER', label: 'أخرى' },
] as const;

export const PROSECUTION_OFFICES = [
  "النيابة العامة",
  "مكتب الحجز والمصادرة",
  "نيابة شرق العاصمة",
  "نيابة غرب العاصمة",
  "نيابة الجنوب",
  "نيابة الريان الكلية",
  "نيابة دخان",
  "نيابة الشمال",
  "النيابات المتخصصة",
  "نيابة الجرائم الاقتصادية وغسل الأموال",
  "نيابة جرائم أمن الدولة والإرهاب",
  "نيابة التميز والاستئناف",
  "نيابة تنفيذ الأحكام والشيكات",
  "نيابة المخدرات",
  "نيابة البيئة والبلدية",
  "نيابة الأحداث والأسرة",
  "نيابة التعاون الدولي",
  "نيابة الجرائم الإلكترونية",
  "نيابة الصحة",
  "نيابة التجارة وشؤون المستهلك",
  "نيابة شؤون الإقامة",
  "نيابة المرور",
  "إدارة التفتيش القضائي",
] as const;

export const SECURITY_CENTERS = [
  "قسم شرطة مسيعيد",
  "إدارة أمن دخان",
  "قسم شرطة مدينة خليفة",
  "قسم شرطة الوكرة",
  "قسم شرطة المدينة الصناعية",
  "قسم شرطة مكينس",
  "إدارة أمن الريان",
  "إدارة أمن العاصمة",
  "قسم شرطة السد",
  "إدارة أمن المطار",
  "قسم شرطة مدينة الشمال",
  "قسم شرطة الخور",
  "قسم شرطة أم صلال",
  "مرور مدينة خليفة",
  "مرور الريان",
  "مرور المطار",
  "مرور الخور",
  "إدارة شرطة النجدة (الفزعة)",
  "إدارة شرطة الأحداث",
  "مكافحة المخدرات",
  "وزارة الداخلية",
  "جهاز أمن الدولة",
  "قسم شيكات العاصمة",
  "إدارة البحث الجنائي",
  "إدارة أمن الجنوب",
  "مرور المخالفات",
  "قسم تحقيق المرور",
  "هيئة الاستخبارات والأمن",
  "إدارة أمن الشمال",
  "قسم شرطة دخان",
  "قسم شرطة الشحانية",
  "قسم شرطةالمعمورة",
  "قسم شرطة مسيمير",
  "قسم شرطة الزبارة",
  "قسم شرطة رأس لفان",
  "مرور المعمورة",
  "مرور الوكرة",
  "مرور مسيعيد",
  "مرور دخان",
  "مرور مكينس",
  "مرور أم صلال",
  "قسم شرطة الجميلية",
  "قسم شرطة شرق العاصمة",
  "قسم شيكات الجنوب",
  "قسم شيكات الشمال",
  "قسم شيكات الريان",
  "قسم شيكات دخان",
  "قسم شيكات الرويس",
  "قسم التخطيط والسلامة المرورية",
  "قسم مكافحة التسول",
  "إدارة البحث والمتابعة",
  "مرور الشحانية",
  "مرور الصناعية",
  "قسم شرطة الظعاين",
  "مرور الشمال",
  "مرور الزبارة",
  "مرور رأس لفان",
  "مرور الظعاين",
  "مرور الجميلية",
  "مرور الرويس",
  "قسم شرطة أم باب",
  "مرور التحريات",
  "مرور أم باب",
  "مرور الجنوب",
  "قسم مكافحة الجرائم الاقتصادية",
  "قسم شرطة عنيزة",
  "إدارة الاتصال للشرطة العربية والدولية",
  "قسم مكافحة الاتجار بالبشر",
  "مرور سوق واقف",
  "مرور الولوه",
  "مرور أبو سمرة",
  "مكتب التحقيق المروري في شركات التأمين",
  "مرور الثمامة",
  "إدارة الامن الوقائي",
  "إدارة مكافحة الجرائم الاقتصادية",
  "قسم شرطة السيلية",
  "مركز شرطة لوسيل",
  "السد",
  "مرور امسيعيد",
  "أخرى",
] as const;
