import type { PagedResponse } from "@/types/common";

/** DTOs mirroring the Spring Boot `/api/accounting` surface. */

/** Jackson serialises `BigDecimal` as a plain JSON number. */
export type MoneyDto = number;

export type AccountTypeDto = "ASSET" | "LIABILITY" | "EQUITY" | "REVENUE" | "EXPENSE";

export type JournalSourceDto =
  | "MANUAL"
  | "INVOICE"
  | "PAYMENT"
  | "EXPENSE"
  | "RECEIPT"
  | "OPENING"
  | "REVERSAL"
  | "BANK_TRANSFER";

export type FinancialPeriodStatusDto = "OPEN" | "CLOSED" | "LOCKED";

export type ExpenseCategoryDto =
  | "SALARIES"
  | "RENT"
  | "UTILITIES"
  | "MARKETING"
  | "SOFTWARE"
  | "GOVERNMENT_FEES"
  | "LEGAL_FEES"
  | "TRANSPORT"
  | "SUPPLIES"
  | "OTHER";

// ---------------------------------------------------------------- accounts

export type AccountResponseDto = {
  id: string;
  code: string;
  nameAr: string;
  nameEn: string | null;
  type: AccountTypeDto;
  parentId: string | null;
  parentCode: string | null;
  active: boolean;
  system: boolean;
  description: string | null;
};

export type AccountCreateRequestDto = {
  code: string;
  nameAr: string;
  nameEn?: string | null;
  type: AccountTypeDto;
  parentId?: string | null;
  description?: string | null;
};

export type AccountUpdateRequestDto = {
  nameAr: string;
  nameEn?: string | null;
  parentId?: string | null;
  description?: string | null;
  active: boolean;
};

// ---------------------------------------------------------------- journal

export type JournalLineDto = {
  id: string;
  lineNo: number;
  accountId: string;
  accountCode: string;
  accountName: string;
  accountType: AccountTypeDto;
  debit: MoneyDto;
  credit: MoneyDto;
  description: string | null;
  clientId: string | null;
  clientName: string | null;
  invoiceId: string | null;
};

export type JournalEntryResponseDto = {
  id: string;
  entryNumber: string;
  entryDate: string;
  description: string;
  referenceType: string | null;
  referenceId: string | null;
  source: JournalSourceDto;
  posted: boolean;
  totalDebit: MoneyDto;
  totalCredit: MoneyDto;
  createdBy: string | null;
  lines: JournalLineDto[];
};

export type JournalLineRequestDto = {
  accountId: string;
  debit: MoneyDto;
  credit: MoneyDto;
  description?: string | null;
  clientId?: string | null;
  invoiceId?: string | null;
  bankAccountId?: string | null;
};

export type JournalEntryCreateRequestDto = {
  entryDate: string;
  description: string;
  lines: JournalLineRequestDto[];
};

// ------------------------------------------------------------ opening balances

/**
 * One opening-balance line. `amount` is signed: positive debits the account,
 * negative credits it — so liabilities and equity take negative amounts. Revenue and
 * expense accounts are rejected by the backend; a P&L cannot be opened.
 */
export type OpeningBalanceLineRequestDto = {
  accountId: string;
  amount: string | number;
};

export type OpeningBalanceRequestDto = {
  effectiveDate: string;
  notes?: string | null;
  lines: OpeningBalanceLineRequestDto[];
};

export type OpeningBalanceResponseDto = {
  journalEntryId: string;
  entryNumber: string;
  effectiveDate: string;
  totalDebit: string | number;
  totalCredit: string | number;
  accounts: {
    accountId: string;
    code: string;
    nameAr: string;
    balance: string | number;
  }[];
  createdBy: string;
};

// ------------------------------------------------------------ bank accounts

export type BankAccountResponseDto = {
  id: string;
  nameAr: string;
  bankName: string | null;
  iban: string | null;
  accountNumber: string | null;
  currency: string;
  accountId: string;
  accountCode: string;
  openingBalance: MoneyDto;
  movement: MoneyDto;
  balance: MoneyDto;
  active: boolean;
};

export type BankAccountCreateRequestDto = {
  nameAr: string;
  accountId?: string | null;
  bankName?: string | null;
  iban?: string | null;
  accountNumber?: string | null;
  currency?: string;
  openingBalance?: MoneyDto | null;
};

export type BankTransferRequestDto = {
  sourceBankAccountId: string;
  destinationBankAccountId: string;
  amount: MoneyDto;
  transferDate: string;
  reference?: string | null;
  notes?: string | null;
};

export type BankTransferResponseDto = {
  id: string;
  sourceBankAccountId: string;
  sourceBankAccountName: string;
  destinationBankAccountId: string;
  destinationBankAccountName: string;
  amount: MoneyDto;
  currency: string;
  transferDate: string;
  reference: string | null;
  notes: string | null;
  journalEntryId: string | null;
  journalEntryNumber: string | null;
};

// ------------------------------------------------------------ client accounts

export type ClientAccountResponseDto = {
  id: string;
  personId: string;
  clientName: string | null;
  accountId: string;
  accountCode: string;
  openingBalance: MoneyDto;
  balance: MoneyDto;
  creditLimit: MoneyDto | null;
  invoiced: MoneyDto;
  paid: MoneyDto;
  outstanding: MoneyDto;
  notes: string | null;
};

export type ClientAccountRequestDto = {
  openingBalance?: MoneyDto | null;
  creditLimit?: MoneyDto | null;
  notes?: string | null;
};

// ----------------------------------------------------------------- expenses

export type ExpenseResponseDto = {
  id: string;
  expenseNumber: string;
  expenseDate: string;
  category: ExpenseCategoryDto;
  payee: string | null;
  description: string;
  accountId: string;
  accountCode: string;
  accountName: string;
  bankAccountId: string | null;
  bankAccountName: string | null;
  clientId: string | null;
  clientName: string | null;
  subtotal: MoneyDto;
  taxRate: MoneyDto;
  taxAmount: MoneyDto;
  total: MoneyDto;
  paymentMethod: string | null;
  reference: string | null;
  recurring: boolean;
  journalEntryId: string | null;
  journalEntryNumber: string | null;
  notes: string | null;
  recordedBy: string | null;
  createdAt: string | null;
};

export type ExpenseCreateRequestDto = {
  expenseDate: string;
  category: ExpenseCategoryDto;
  description: string;
  payee?: string | null;
  accountId?: string | null;
  bankAccountId?: string | null;
  clientId?: string | null;
  subtotal: MoneyDto;
  taxRate?: MoneyDto | null;
  paymentMethod?: string | null;
  reference?: string | null;
  recurring?: boolean;
  notes?: string | null;
};

// ----------------------------------------------------------------- receipts

export type ReceiptResponseDto = {
  id: string;
  receiptNumber: string;
  receiptDate: string;
  clientId: string | null;
  clientName: string | null;
  invoiceId: string | null;
  bankAccountId: string;
  bankAccountName: string;
  amount: MoneyDto;
  currency: string;
  paymentMethod: string | null;
  reference: string | null;
  deposit: boolean;
  journalEntryId: string | null;
  journalEntryNumber: string | null;
  notes: string | null;
  recordedBy: string | null;
  createdAt: string | null;
};

export type ReceiptCreateRequestDto = {
  receiptDate: string;
  bankAccountId: string;
  amount: MoneyDto;
  clientId?: string | null;
  invoiceId?: string | null;
  currency?: string;
  paymentMethod?: string | null;
  reference?: string | null;
  deposit?: boolean;
  notes?: string | null;
};

// ------------------------------------------------------------------ periods

export type FinancialPeriodResponseDto = {
  id: string;
  name: string;
  startDate: string;
  endDate: string;
  status: FinancialPeriodStatusDto;
  closedAt: string | null;
  closedBy: string | null;
  entryCount: number;
};

export type FinancialPeriodRequestDto = {
  name: string;
  startDate: string;
  endDate: string;
};

// ---------------------------------------------------------------- tax rates

export type TaxRateResponseDto = {
  id: string;
  nameAr: string;
  rate: MoneyDto;
  accountId: string | null;
  active: boolean;
  defaultRate: boolean;
};

export type TaxRateRequestDto = {
  nameAr: string;
  rate: MoneyDto;
  accountId?: string | null;
  active?: boolean;
  defaultRate?: boolean;
};

// ------------------------------------------------------------------ reports

export type AccountAmountDto = {
  accountId: string;
  code: string;
  nameAr: string;
  type: AccountTypeDto;
  debit: MoneyDto;
  credit: MoneyDto;
  balance: MoneyDto;
};

export type TrialRowDto = AccountAmountDto;

export type BankBalanceDto = {
  id: string;
  nameAr: string;
  bankName: string | null;
  currency: string;
  openingBalance: MoneyDto;
  movement: MoneyDto;
  balance: MoneyDto;
};

export type AgingBucketDto = {
  label: string;
  amount: MoneyDto;
  count: number;
};

export type AccountingReportResponseDto = {
  from: string;
  to: string;
  revenue: MoneyDto;
  cogs: MoneyDto;
  grossProfit: MoneyDto;
  operatingExpenses: MoneyDto;
  netProfit: MoneyDto;
  revenueBreakdown: AccountAmountDto[];
  expenseBreakdown: AccountAmountDto[];
  totalAssets: MoneyDto;
  totalLiabilities: MoneyDto;
  totalEquity: MoneyDto;
  totalLiabilitiesAndEquity: MoneyDto;
  balanced: boolean;
  assets: AccountAmountDto[];
  liabilities: AccountAmountDto[];
  equity: AccountAmountDto[];
  trialBalance: TrialRowDto[];
  trialTotalDebit: MoneyDto;
  trialTotalCredit: MoneyDto;
  trialBalanced: boolean;
  cashOnHand: MoneyDto;
  cashIn: MoneyDto;
  cashOut: MoneyDto;
  bankBalances: BankBalanceDto[];
  totalReceivable: MoneyDto;
  overdueInvoiceCount: number;
  aging: AgingBucketDto[];
  outputVat: MoneyDto;
  inputVat: MoneyDto;
  netVatPayable: MoneyDto;
};

export type StatementRowDto = {
  date: string;
  reference: string;
  description: string | null;
  debit: MoneyDto;
  credit: MoneyDto;
  balance: MoneyDto;
};

export type ClientStatementResponseDto = {
  clientId: string;
  clientName: string;
  from: string;
  to: string;
  openingBalance: MoneyDto;
  totalDebit: MoneyDto;
  totalCredit: MoneyDto;
  closingBalance: MoneyDto;
  rows: StatementRowDto[];
};

export type AccountLedgerRowDto = {
  date: string;
  entryNumber: string;
  referenceType: string | null;
  referenceId: string | null;
  description: string;
  debit: MoneyDto;
  credit: MoneyDto;
};

export type AccountLedgerResponseDto = {
  accountId: string;
  accountCode: string;
  accountName: string;
  accountType: AccountTypeDto;
  from: string;
  to: string;
  openingBalance: MoneyDto;
  periodDebit: MoneyDto;
  periodCredit: MoneyDto;
  closingBalance: MoneyDto;
  entries: PagedResponse<AccountLedgerRowDto>;
};

// Retainers
export type RetainerStatusDto = "ACTIVE" | "FULLY_APPLIED" | "REFUNDED" | "CANCELLED";
export type RetainerTargetTypeDto = "INVOICE" | "EXPENSE" | "BILL";

export type RetainerResponseDto = {
  id: string;
  companyId: string;
  clientId: string;
  clientNameAr: string;
  amount: MoneyDto;
  currency: string;
  status: RetainerStatusDto;
  receivedDate: string;
  receivedBy: string;
  receivedByName: string | null;
  reference: string | null;
  notes: string | null;
  appliedAmount: MoneyDto;
  remainingAmount: MoneyDto;
  createdAt: string;
};

export type RetainerCreateRequestDto = {
  clientId: string;
  amount: MoneyDto;
  currency?: string;
  receivedDate: string;
  reference?: string | null;
  notes?: string | null;
};

export type RetainerApplicationResponseDto = {
  id: string;
  companyId: string;
  retainerId: string;
  appliedAmount: MoneyDto;
  appliedDate: string;
  appliedBy: string;
  appliedByName: string | null;
  targetType: RetainerTargetTypeDto;
  targetId: string;
  targetReference: string | null;
  journalEntryId: string | null;
  notes: string | null;
  createdAt: string;
};

export type RetainerApplicationCreateRequestDto = {
  retainerId: string;
  appliedAmount: MoneyDto;
  appliedDate: string;
  targetType: RetainerTargetTypeDto;
  targetId: string;
  notes?: string | null;
};
