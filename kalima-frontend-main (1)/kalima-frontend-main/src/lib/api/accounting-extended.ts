import { request } from "@/lib/api/client";
import type {
  AccountingDashboard,
  BankReconciliation,
  Budget,
  BudgetPeriod,
  CaseProfitability,
  CaseWorkSummary,
  CreditDebitNote,
  MatchSuggestion,
  MatchType,
  NoteType,
  PayablesSummary,
  PortfolioSummary,
  RecurringInvoice,
  RecurrenceFrequency,
  Refund,
  StatementLine,
  TimeBillingResult,
  TimeEntry,
  Vendor,
  VendorBill,
  VendorBillStatus,
  VendorPayment,
} from "@/types/accounting-extended";

const range = (from?: string, to?: string) => ({
  ...(from ? { from } : {}),
  ...(to ? { to } : {}),
});

/** Drops undefined entries so an unset filter is not sent as an empty query param. */
const filters = <T extends object>(values: T) =>
  Object.fromEntries(Object.entries(values).filter(([, value]) => value !== undefined && value !== null)) as
    Partial<T>;

/** Credit and debit notes. */
export const creditDebitNotesApi = {
  list: (params?: { clientId?: string; noteType?: NoteType; from?: string; to?: string }) =>
    request<CreditDebitNote[]>("/accounting/credit-debit-notes", {
      query: params ? filters({ clientId: params.clientId, noteType: params.noteType, ...range(params.from, params.to) }) : undefined,
    }),

  get: (id: string) => request<CreditDebitNote>(`/accounting/credit-debit-notes/${id}`),

  create: (body: {
    noteType: NoteType;
    invoiceId?: string;
    clientId?: string;
    noteDate?: string;
    subtotal: number;
    taxRate?: number;
    taxAmount?: number;
    reason?: string;
    notes?: string;
  }) => request<CreditDebitNote>("/accounting/credit-debit-notes", { method: "POST", body }),

  voidNote: (id: string) =>
    request<void>(`/accounting/credit-debit-notes/${id}`, { method: "DELETE" }),
};

/** Refunds paid back to clients. */
export const refundsApi = {
  list: (params?: { clientId?: string; invoiceId?: string; from?: string; to?: string }) =>
    request<Refund[]>("/accounting/refunds", {
      query: params ? filters({ clientId: params.clientId, invoiceId: params.invoiceId, ...range(params.from, params.to) }) : undefined,
    }),

  get: (id: string) => request<Refund>(`/accounting/refunds/${id}`),

  create: (body: {
    invoiceId?: string;
    clientId?: string;
    refundDate?: string;
    amount: number;
    bankAccountId: string;
    paymentMethod?: string;
    reason: string;
    notes?: string;
  }) => request<Refund>("/accounting/refunds", { method: "POST", body }),

  voidRefund: (id: string) => request<void>(`/accounting/refunds/${id}`, { method: "DELETE" }),
};

/** Time tracking. */
export const timeEntriesApi = {
  list: (params?: {
    caseId?: string;
    lawyerId?: string;
    from?: string;
    to?: string;
  }) =>
    request<TimeEntry[]>("/accounting/time-entries", {
      query: params ? filters({ caseId: params.caseId, lawyerId: params.lawyerId, ...range(params.from, params.to) }) : undefined,
    }),

  get: (id: string) => request<TimeEntry>(`/accounting/time-entries/${id}`),

  caseSummary: (caseId: string) =>
    request<CaseWorkSummary>(`/accounting/time-entries/case/${caseId}/summary`),

  billCase: (caseId: string, body: { clientId?: string; issue?: boolean; dueDate?: string; notes?: string }) =>
    request<TimeBillingResult>(`/accounting/time-entries/case/${caseId}/bill`, { method: "POST", body }),

  create: (body: {
    caseId: string;
    lawyerId?: string;
    workDate?: string;
    durationMinutes: number;
    description?: string;
    billable?: boolean;
    hourlyRate?: number;
  }) => request<TimeEntry>("/accounting/time-entries", { method: "POST", body }),

  voidEntry: (id: string) => request<void>(`/accounting/time-entries/${id}`, { method: "DELETE" }),
};

export type VendorCreateRequest = {
  name: string;
  nameAr?: string;
  email?: string;
  phone?: string;
  taxNumber?: string;
  address?: string;
  expenseAccountId?: string;
  notes?: string;
};

export type VendorBillCreateRequest = {
  vendorId: string;
  billDate?: string;
  dueDate?: string;
  subtotal: number;
  taxRate?: number;
  taxAmount?: number;
  expenseAccountId: string;
  reference?: string;
  notes?: string;
};

export type BudgetCreateRequest = {
  name: string;
  fiscalYear?: number;
  period?: BudgetPeriod;
  /** Required when period is MONTH (1-12). */
  periodMonth?: number;
  /** Required when period is QUARTER (1-4). */
  quarter?: number;
  periodStart?: string;
  periodEnd?: string;
  accountId?: string;
  budgetAmount: number;
  notes?: string;
};

/** Vendors, bills and payments. */
export const payablesApi = {
  listVendors: (params?: { activeOnly?: boolean; q?: string }) =>
    request<Vendor[]>("/accounting/vendors", { query: params }),

  getVendor: (id: string) => request<Vendor>(`/accounting/vendors/${id}`),

  createVendor: (body: VendorCreateRequest) =>
    request<Vendor>("/accounting/vendors", { method: "POST", body }),

  updateVendor: (id: string, body: VendorCreateRequest) =>
    request<Vendor>(`/accounting/vendors/${id}`, { method: "PUT", body }),

  deactivateVendor: (id: string) =>
    request<void>(`/accounting/vendors/${id}`, { method: "DELETE" }),

  listBills: (params?: {
    vendorId?: string;
    status?: VendorBillStatus;
    from?: string;
    to?: string;
  }) =>
    request<VendorBill[]>("/accounting/vendors/bills", {
      query: params ? { vendorId: params.vendorId, status: params.status, ...range(params.from, params.to) } : undefined,
    }),

  createBill: (body: VendorBillCreateRequest) =>
    request<VendorBill>("/accounting/vendors/bills", { method: "POST", body }),

  cancelBill: (id: string) => request<void>(`/accounting/vendors/bills/${id}`, { method: "DELETE" }),

  listPayments: (params?: { billId?: string; from?: string; to?: string }) =>
    request<VendorPayment[]>("/accounting/vendors/payments", {
      query: params ? { billId: params.billId, ...range(params.from, params.to) } : undefined,
    }),

  payBill: (body: {
    billId: string;
    paymentDate?: string;
    amount: number;
    bankAccountId: string;
    method?: string;
    reference?: string;
    notes?: string;
  }) => request<VendorPayment>("/accounting/vendors/payments", { method: "POST", body }),

  voidPayment: (id: string) =>
    request<void>(`/accounting/vendors/payments/${id}`, { method: "DELETE" }),

  summary: (on?: string) =>
    request<PayablesSummary>("/accounting/vendors/payables-summary", { query: { on } }),
};

/** Bank reconciliation. */
export const reconciliationApi = {
  open: (body: {
    bankAccountId: string;
    statementDate: string;
    statementClosingBalance: number;
    notes?: string;
  }) => request<BankReconciliation>("/accounting/reconciliations", { method: "POST", body }),

  list: (bankAccountId?: string) =>
    request<BankReconciliation[]>("/accounting/reconciliations", { query: { bankAccountId } }),

  get: (id: string) => request<BankReconciliation>(`/accounting/reconciliations/${id}`),

  addLine: (
    id: string,
    body: { lineDate?: string; description?: string; reference?: string; amount: number; notes?: string },
  ) =>
    request<StatementLine>(`/accounting/reconciliations/${id}/lines`, { method: "POST", body }),

  listLines: (id: string, onlyUnmatched = false) =>
    request<StatementLine[]>(`/accounting/reconciliations/${id}/lines`, {
      query: { onlyUnmatched },
    }),

  suggestions: (lineId: string) =>
    request<MatchSuggestion[]>(`/accounting/reconciliations/lines/${lineId}/suggestions`),

  match: (lineId: string, type: MatchType, matchedId: string) =>
    request<StatementLine>(`/accounting/reconciliations/lines/${lineId}/match`, {
      method: "PATCH",
      query: { type, matchedId },
    }),

  unmatch: (lineId: string) =>
    request<StatementLine>(`/accounting/reconciliations/lines/${lineId}/unmatch`, {
      method: "PATCH",
    }),

  complete: (id: string) =>
    request<BankReconciliation>(`/accounting/reconciliations/${id}/complete`, { method: "POST" }),
};

/** Budgets. */
export const budgetsApi = {
  list: (fiscalYear?: number) => request<Budget[]>("/accounting/budgets", { query: { fiscalYear } }),

  get: (id: string) => request<Budget>(`/accounting/budgets/${id}`),

  create: (body: BudgetCreateRequest) =>
    request<Budget>("/accounting/budgets", { method: "POST", body }),

  update: (id: string, body: BudgetCreateRequest) =>
    request<Budget>(`/accounting/budgets/${id}`, { method: "PUT", body }),

  remove: (id: string) => request<void>(`/accounting/budgets/${id}`, { method: "DELETE" }),
};

/** Recurring invoice templates. */
export type RecurringRunResult = {
  templateId: string;
  invoiceId: string;
  invoiceNumber: string;
  runDate: string;
  nextRunDate: string;
  total: number;
};

export const recurringInvoicesApi = {
  list: (clientId?: string) =>
    request<RecurringInvoice[]>("/accounting/recurring-invoices", { query: { clientId } }),

  get: (id: string) => request<RecurringInvoice>(`/accounting/recurring-invoices/${id}`),

  due: (on?: string) => request<RecurringInvoice[]>("/accounting/recurring-invoices/due", { query: { on } }),

  create: (body: {
    clientId: string;
    caseId?: string;
    description: string;
    frequency: RecurrenceFrequency;
    intervalCount?: number;
    startDate: string;
    endDate?: string;
    subtotal: number;
    taxRate?: number;
    taxAmount?: number;
    notes?: string;
  }) => request<RecurringInvoice>("/accounting/recurring-invoices", { method: "POST", body }),

  /** Issues the invoice for the run that is due and advances the schedule. */
  generate: (id: string, runDate?: string, dueDate?: string) =>
    request<RecurringRunResult>(`/accounting/recurring-invoices/${id}/generate`, {
      method: "POST",
      query: { runDate, dueDate },
    }),

  markGenerated: (id: string, runDate?: string) =>
    request<RecurringInvoice>(`/accounting/recurring-invoices/${id}/generated`, {
      method: "POST",
      query: { runDate },
    }),

  deactivate: (id: string) =>
    request<void>(`/accounting/recurring-invoices/${id}`, { method: "DELETE" }),
};

/** Management reporting. */
export const accountingReportsApi = {
  caseProfitability: (params?: { from?: string; to?: string }) =>
    request<CaseProfitability[]>("/accounting/reports/case-profitability", {
      query: params ? range(params.from, params.to) : undefined,
    }),

  caseProfitabilityDetail: (caseId: string, params?: { from?: string; to?: string }) =>
    request<CaseProfitability>(`/accounting/reports/case-profitability/${caseId}`, {
      query: params ? range(params.from, params.to) : undefined,
    }),

  profitabilitySummary: (params?: { from?: string; to?: string }) =>
    request<PortfolioSummary>("/accounting/reports/profitability-summary", {
      query: params ? range(params.from, params.to) : undefined,
    }),

  dashboard: (params?: { from?: string; to?: string }) =>
    request<AccountingDashboard>("/accounting/reports/dashboard", {
      query: params ? range(params.from, params.to) : undefined,
    }),
};