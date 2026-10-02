export type InvoiceStatusDto =
  | "DRAFT"
  | "ISSUED"
  | "PARTIALLY_PAID"
  | "PAID"
  | "OVERDUE"
  | "CANCELLED";

/** Money is serialised by Jackson as a JSON number. */
export type MoneyDto = number;

export type InvoiceListItemDto = {
  id: string;
  invoiceNumber: string;
  status: InvoiceStatusDto;
  clientName: string | null;
  caseNumber: string | null;
  issueDate: string | null;
  dueDate: string | null;
  total: MoneyDto;
  amountPaid: MoneyDto;
  remaining: MoneyDto;
};

export type InvoicePaymentDto = {
  id: string;
  invoiceId: string;
  amount: MoneyDto;
  paymentDate: string;
  method: string | null;
  bankAccountId: string | null;
  bankAccountName: string | null;
  reference: string | null;
  notes: string | null;
  journalEntryId: string | null;
  journalEntryNumber: string | null;
  recordedBy: string | null;
  createdAt: string | null;
};

export type InvoiceResponseDto = {
  id: string;
  invoiceNumber: string;
  status: InvoiceStatusDto;
  companyId: string;
  clientId: string;
  clientName: string | null;
  caseId: string | null;
  caseNumber: string | null;
  issueDate: string | null;
  dueDate: string | null;
  subtotal: MoneyDto;
  taxRate: MoneyDto;
  taxAmount: MoneyDto;
  discount: MoneyDto;
  total: MoneyDto;
  amountPaid: MoneyDto;
  remaining: MoneyDto;
  notes: string | null;
  recordedBy: string | null;
  recordedByEmail: string | null;
  createdAt: string | null;
  /** Set once the invoice has been issued and revenue recognised. */
  journalEntryId: string | null;
  journalEntryNumber: string | null;
  payments: InvoicePaymentDto[];
};

export type InvoiceStatisticsDto = {
  total: number;
  paid: number;
  totalBilled: MoneyDto;
  totalCollected: MoneyDto;
  totalRemaining: MoneyDto;
};

export type InvoiceCreateRequestDto = {
  clientId: string;
  caseId?: string | null;
  issueDate: string;
  dueDate?: string | null;
  subtotal: MoneyDto;
  taxRate?: MoneyDto | null;
  discount?: MoneyDto | null;
  notes?: string | null;
};

export type InvoiceStatusUpdateRequestDto = {
  status: InvoiceStatusDto;
};

export type InvoicePaymentRequestDto = {
  amount: MoneyDto;
  paymentDate: string;
  method?: string | null;
  /** Cash/bank account that the payment is credited to. Defaults to cash. */
  bankAccountId?: string | null;
  reference?: string | null;
  notes?: string | null;
};

export const INVOICE_PAYMENT_METHODS = [
  { value: "CASH", label: "نقداً" },
  { value: "BANK_TRANSFER", label: "تحويل بنكي" },
  { value: "CHEQUE", label: "شيك" },
  { value: "CARD", label: "بطاقة" },
  { value: "OTHER", label: "أخرى" },
] as const;

export const INVOICE_CURRENCY_AR: Record<string, string> = {
  SAR: "ر.س",
  QAR: "ر.ق",
  AED: "د.إ",
  KWD: "د.ك",
  EGP: "ج.م",
  USD: "$",
};
