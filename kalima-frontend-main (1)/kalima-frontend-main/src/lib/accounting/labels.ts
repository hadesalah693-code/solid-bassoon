import type {
  AccountTypeDto,
  ExpenseCategoryDto,
  FinancialPeriodStatusDto,
  JournalSourceDto,
} from "@/types/accounting";

type Tone = "neutral" | "info" | "warning" | "success" | "danger" | "navy" | "gold";

export const ACCOUNT_TYPE_AR: Record<AccountTypeDto, string> = {
  ASSET: "أصول",
  LIABILITY: "التزامات",
  EQUITY: "حقوق ملكية",
  REVENUE: "إيرادات",
  EXPENSE: "مصروفات",
};

export const ACCOUNT_TYPE_TONE: Record<AccountTypeDto, Tone> = {
  ASSET: "navy",
  LIABILITY: "warning",
  EQUITY: "info",
  REVENUE: "success",
  EXPENSE: "danger",
};

export const ACCOUNT_TYPE_OPTIONS = (
  Object.entries(ACCOUNT_TYPE_AR) as [AccountTypeDto, string][]
).map(([value, label]) => ({ value, label }));

export const JOURNAL_SOURCE_AR: Record<JournalSourceDto, string> = {
  MANUAL: "قيد يدوي",
  INVOICE: "فاتورة",
  PAYMENT: "سداد فاتورة",
  EXPENSE: "مصروف",
  RECEIPT: "سند قبض",
  OPENING: "رصيد افتتاحي",
  REVERSAL: "قيد عكسي",
  BANK_TRANSFER: "تحويل بنكي",
};

export const JOURNAL_SOURCE_TONE: Record<JournalSourceDto, Tone> = {
  MANUAL: "neutral",
  INVOICE: "navy",
  PAYMENT: "success",
  EXPENSE: "danger",
  RECEIPT: "info",
  OPENING: "gold",
  REVERSAL: "warning",
  BANK_TRANSFER: "navy",
};

export const JOURNAL_SOURCE_OPTIONS = (
  Object.entries(JOURNAL_SOURCE_AR) as [JournalSourceDto, string][]
).map(([value, label]) => ({ value, label }));

export const PERIOD_STATUS_AR: Record<FinancialPeriodStatusDto, string> = {
  OPEN: "مفتوحة",
  CLOSED: "مغلقة",
  LOCKED: "مقفلة",
};

export const PERIOD_STATUS_TONE: Record<FinancialPeriodStatusDto, Tone> = {
  OPEN: "success",
  CLOSED: "warning",
  LOCKED: "danger",
};

export const EXPENSE_CATEGORY_AR: Record<ExpenseCategoryDto, string> = {
  SALARIES: "رواتب",
  RENT: "إيجار",
  UTILITIES: "خدمات",
  MARKETING: "تسويق",
  SOFTWARE: "برمجيات",
  GOVERNMENT_FEES: "رسوم حكومية",
  LEGAL_FEES: "أتعاب قانونية",
  TRANSPORT: "نقل",
  SUPPLIES: "مستلزمات",
  OTHER: "أخرى",
};

export const EXPENSE_CATEGORY_OPTIONS = (
  Object.entries(EXPENSE_CATEGORY_AR) as [ExpenseCategoryDto, string][]
).map(([value, label]) => ({ value, label }));

export const PAYMENT_METHOD_OPTIONS = [
  { value: "CASH", label: "نقداً" },
  { value: "BANK_TRANSFER", label: "تحويل بنكي" },
  { value: "CHEQUE", label: "شيك" },
  { value: "CARD", label: "بطاقة" },
  { value: "OTHER", label: "أخرى" },
] as const;

/**
 * The ledger posts in a single base currency with no FX translation, so the
 * picker offers SAR only. The backend rejects anything else with a 400.
 */
export const BASE_CURRENCY = "SAR";

export const CURRENCY_OPTIONS = [
  { value: BASE_CURRENCY, label: "ر.س — ريال سعودي" },
] as const;
