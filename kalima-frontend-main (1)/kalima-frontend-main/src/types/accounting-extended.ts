export type NoteType = "CREDIT" | "DEBIT";

export interface CreditDebitNote {
  id: string;
  noteNumber: string;
  noteType: NoteType;
  noteDate: string;
  invoiceId: string | null;
  invoiceNumber: string | null;
  clientId: string | null;
  clientName: string | null;
  subtotal: number;
  taxRate: number;
  taxAmount: number;
  total: number;
  reason: string | null;
  notes: string | null;
  journalEntryId: string | null;
  voided: boolean;
  voidedAt: string | null;
}

export interface Refund {
  id: string;
  refundNumber: string;
  refundDate: string;
  invoiceId: string | null;
  invoiceNumber: string | null;
  clientId: string | null;
  clientName: string | null;
  amount: number;
  paymentMethod: string | null;
  bankAccountId: string | null;
  bankAccountName: string | null;
  reason: string | null;
  notes: string | null;
  journalEntryId: string | null;
  voided: boolean;
  voidedAt: string | null;
}

export interface TimeEntry {
  id: string;
  entryNumber: string;
  caseId: string | null;
  caseNumber: string | null;
  caseTitle: string | null;
  lawyerId: string | null;
  lawyerName: string | null;
  workDate: string;
  durationMinutes: number;
  description: string | null;
  billable: boolean;
  hourlyRate: number;
  amount: number;
  invoiceId: string | null;
  journalEntryId: string | null;
  voided: boolean;
  voidedAt: string | null;
}

export interface CaseWorkSummary {
  caseId: string;
  totalWorkValue: number;
  unbilledValue: number;
  billedValue: number;
  entries: TimeEntry[];
}

export interface TimeBillingResult {
  invoiceId: string;
  invoiceNumber: string;
  total: number;
  subtotal: number;
  billedEntries: number;
}

export interface Vendor {
  id: string;
  name: string;
  nameAr: string | null;
  email: string | null;
  phone: string | null;
  taxNumber: string | null;
  address: string | null;
  currency: string;
  expenseAccountId: string | null;
  active: boolean;
  outstandingPayable: number;
  notes: string | null;
  createdAt: string;
}

export type VendorBillStatus =
  | "OPEN"
  | "PARTIALLY_PAID"
  | "PAID"
  | "CANCELLED";

export interface VendorBill {
  id: string;
  billNumber: string;
  vendorId: string | null;
  vendorName: string | null;
  billDate: string;
  dueDate: string | null;
  subtotal: number;
  taxRate: number;
  taxAmount: number;
  total: number;
  amountPaid: number;
  remaining: number;
  status: VendorBillStatus;
  overdue: boolean;
  expenseAccountId: string | null;
  reference: string | null;
  notes: string | null;
  journalEntryId: string | null;
  voided: boolean;
  voidedAt: string | null;
}

export interface VendorPayment {
  id: string;
  billId: string | null;
  billNumber: string | null;
  vendorName: string | null;
  paymentDate: string;
  amount: number;
  method: string | null;
  bankAccountId: string | null;
  bankAccountName: string | null;
  reference: string | null;
  notes: string | null;
  journalEntryId: string | null;
  billRemaining: number | null;
  voided: boolean;
  voidedAt: string | null;
}

export interface PayablesSummary {
  outstandingPayable: number;
  overdueBills: number;
}

export type ReconciliationStatus = "IN_PROGRESS" | "COMPLETED" | "CANCELLED";

export interface BankReconciliation {
  id: string;
  bankAccountId: string | null;
  bankAccountName: string | null;
  statementDate: string;
  statementClosingBalance: number;
  reconciledBalance: number;
  difference: number;
  status: ReconciliationStatus;
  completedAt: string | null;
  notes: string | null;
  createdAt: string;
}

export type MatchType = "RECEIPT" | "PAYMENT" | "TRANSFER" | "EXPENSE";

export interface StatementLine {
  id: string;
  reconciliationId: string | null;
  bankAccountId: string | null;
  lineDate: string;
  description: string | null;
  reference: string | null;
  amount: number;
  matchedType: MatchType | null;
  matchedId: string | null;
  matchedAt: string | null;
  matched: boolean;
  notes: string | null;
}

export interface MatchSuggestion {
  type: MatchType;
  id: string;
  reference: string | null;
  amount: number;
  date: string;
}

export type BudgetPeriod = "MONTH" | "QUARTER" | "YEAR" | "CUSTOM";

export interface Budget {
  id: string;
  name: string;
  fiscalYear: number;
  period: BudgetPeriod;
  periodStart: string;
  periodEnd: string;
  accountId: string | null;
  accountCode: string | null;
  accountName: string | null;
  budgetAmount: number;
  actualAmount: number;
  variance: number;
  utilisationPercent: number;
  overBudget: boolean;
  notes: string | null;
}

export type RecurrenceFrequency =
  | "WEEKLY"
  | "MONTHLY"
  | "QUARTERLY"
  | "YEARLY";

export interface RecurringInvoice {
  id: string;
  clientId: string | null;
  clientName: string | null;
  caseId: string | null;
  description: string;
  frequency: RecurrenceFrequency;
  intervalCount: number;
  startDate: string;
  endDate: string | null;
  nextRunDate: string;
  subtotal: number;
  taxRate: number;
  taxAmount: number;
  total: number;
  active: boolean;
  timesGenerated: number;
  lastGeneratedDate: string | null;
  notes: string | null;
}

export interface CaseProfitability {
  caseId: string;
  caseNumber: string | null;
  caseTitle: string | null;
  revenue: number;
  cost: number;
  profit: number;
  marginPercent: number;
}

export interface PortfolioSummary {
  revenue: number;
  cost: number;
  profit: number;
  marginPercent: number;
  profitableCases: number;
  losingCases: number;
  breakevenCases: number;
}

export interface AccountingDashboard {
  from: string;
  to: string;
  cash: number;
  receivables: number;
  payables: number;
  clientLiabilities: number;
  revenue: number;
  expenses: number;
  netProfit: number;
  arOverdue: number;
  outstandingPayable: number;
  overdueBills: number;
  reconciliationsInProgress: number;
  profitability: PortfolioSummary;
}