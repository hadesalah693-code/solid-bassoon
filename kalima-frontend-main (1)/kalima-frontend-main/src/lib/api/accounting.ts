import { request } from "./client";
import type { PagedResponse } from "@/types/common";
import type {
  AccountCreateRequestDto,
  AccountResponseDto,
  AccountLedgerResponseDto,
  AccountTypeDto,
  AccountUpdateRequestDto,
  AccountingReportResponseDto,
  BankAccountCreateRequestDto,
  BankAccountResponseDto,
  BankTransferRequestDto,
  BankTransferResponseDto,
  ClientAccountRequestDto,
  ClientAccountResponseDto,
  ClientStatementResponseDto,
  ExpenseCategoryDto,
  ExpenseCreateRequestDto,
  ExpenseResponseDto,
  FinancialPeriodRequestDto,
  FinancialPeriodResponseDto,
  JournalEntryCreateRequestDto,
  JournalEntryResponseDto,
  JournalSourceDto,
  OpeningBalanceRequestDto,
  OpeningBalanceResponseDto,
  ReceiptCreateRequestDto,
  ReceiptResponseDto,
  RetainerApplicationCreateRequestDto,
  RetainerApplicationResponseDto,
  RetainerCreateRequestDto,
  RetainerResponseDto,
  TaxRateRequestDto,
  TaxRateResponseDto,
} from "@/types/accounting";

type PageQuery = { page?: number; size?: number };

const range = (from?: string | null, to?: string | null) => ({
  ...(from ? { from } : {}),
  ...(to ? { to } : {}),
});

export const accountsApi = {
  list: (type?: AccountTypeDto) =>
    request<AccountResponseDto[]>("/accounting/accounts", {
      query: type ? { type } : undefined,
    }),

  create: (body: AccountCreateRequestDto) =>
    request<AccountResponseDto>("/accounting/accounts", { method: "POST", body }),

  update: (id: string, body: AccountUpdateRequestDto) =>
    request<AccountResponseDto>(`/accounting/accounts/${id}`, { method: "PUT", body }),

  /** Archives rather than deletes — posted movements must stay auditable. */
  archive: (id: string) => request<void>(`/accounting/accounts/${id}`, { method: "DELETE" }),

  /**
   * Installs the system chart of accounts for the current company. Idempotent; the
   * backend also self-heals before the first posting, so this is only needed to
   * back-fill a company that predates the seeder.
   */
  seed: () =>
    request<{ inserted: number; message: string }>("/accounting/accounts/seed", { method: "POST" }),
};

export const openingBalancesApi = {
  /** Whether the single opening-balance entry has already been posted. */
  status: () => request<{ posted: boolean }>("/accounting/opening-balances"),

  /**
   * Posts opening balances as one balanced journal entry. Amounts are signed:
   * positive debits the account, negative credits it. Assets take positive amounts,
   * liabilities and equity negative ones.
   */
  post: (body: OpeningBalanceRequestDto) =>
    request<OpeningBalanceResponseDto>("/accounting/opening-balances", { method: "POST", body }),
};

export const journalApi = {
  list: (
    q: PageQuery & {
      from?: string | null;
      to?: string | null;
      source?: JournalSourceDto;
      search?: string;
    } = {},
  ) =>
    request<PagedResponse<JournalEntryResponseDto>>("/accounting/journal", {
      query: {
        page: q.page ?? 0,
        size: q.size ?? 20,
        ...range(q.from, q.to),
        ...(q.source ? { source: q.source } : {}),
        ...(q.search?.trim() ? { search: q.search.trim() } : {}),
      },
    }),

  get: (id: string) => request<JournalEntryResponseDto>(`/accounting/journal/${id}`),

  create: (body: JournalEntryCreateRequestDto) =>
    request<JournalEntryResponseDto>("/accounting/journal", { method: "POST", body }),

  reverse: (id: string) =>
    request<JournalEntryResponseDto>(`/accounting/journal/${id}/reverse`, { method: "POST" }),
};

export const expensesApi = {
  list: (
    q: PageQuery & {
      category?: ExpenseCategoryDto;
      from?: string | null;
      to?: string | null;
      search?: string;
    } = {},
  ) =>
    request<PagedResponse<ExpenseResponseDto>>("/accounting/expenses", {
      query: {
        page: q.page ?? 0,
        size: q.size ?? 20,
        ...range(q.from, q.to),
        ...(q.category ? { category: q.category } : {}),
        ...(q.search?.trim() ? { search: q.search.trim() } : {}),
      },
    }),

  get: (id: string) => request<ExpenseResponseDto>(`/accounting/expenses/${id}`),

  create: (body: ExpenseCreateRequestDto) =>
    request<ExpenseResponseDto>("/accounting/expenses", { method: "POST", body }),

  /** Reverses the journal entry and removes the record. */
  remove: (id: string) => request<void>(`/accounting/expenses/${id}`, { method: "DELETE" }),
};

export const receiptsApi = {
  list: (
    q: PageQuery & { clientId?: string | null; from?: string | null; to?: string | null; search?: string } = {},
  ) =>
    request<PagedResponse<ReceiptResponseDto>>("/accounting/receipts", {
      query: {
        page: q.page ?? 0,
        size: q.size ?? 20,
        ...range(q.from, q.to),
        ...(q.clientId ? { clientId: q.clientId } : {}),
        ...(q.search?.trim() ? { search: q.search.trim() } : {}),
      },
    }),

  get: (id: string) => request<ReceiptResponseDto>(`/accounting/receipts/${id}`),

  create: (body: ReceiptCreateRequestDto) =>
    request<ReceiptResponseDto>("/accounting/receipts", { method: "POST", body }),

  remove: (id: string) => request<void>(`/accounting/receipts/${id}`, { method: "DELETE" }),
};

export const bankAccountsApi = {
  list: () => request<BankAccountResponseDto[]>("/accounting/bank-accounts"),

  transfers: () => request<BankTransferResponseDto[]>("/accounting/bank-accounts/transfers"),

  transfer: (body: BankTransferRequestDto) =>
    request<BankTransferResponseDto>("/accounting/bank-accounts/transfers", { method: "POST", body }),

  get: (id: string) => request<BankAccountResponseDto>(`/accounting/bank-accounts/${id}`),

  create: (body: BankAccountCreateRequestDto) =>
    request<BankAccountResponseDto>("/accounting/bank-accounts", { method: "POST", body }),

  update: (id: string, body: BankAccountCreateRequestDto) =>
    request<BankAccountResponseDto>(`/accounting/bank-accounts/${id}`, { method: "PUT", body }),

  /** Backend reads `active` from the query string, not a JSON body. */
  setActive: (id: string, active: boolean) =>
    request<BankAccountResponseDto>(`/accounting/bank-accounts/${id}/active`, {
      method: "PATCH",
      query: { active: String(active) },
    }),
};

export const clientAccountsApi = {
  list: (search?: string) =>
    request<ClientAccountResponseDto[]>("/accounting/client-accounts", {
      query: search?.trim() ? { search: search.trim() } : undefined,
    }),

  get: (id: string) => request<ClientAccountResponseDto>(`/accounting/client-accounts/${id}`),

  /** Control accounts are also created lazily when an invoice is first issued. */
  create: (clientId: string, body: ClientAccountRequestDto) =>
    request<ClientAccountResponseDto>("/accounting/client-accounts", {
      method: "POST",
      query: { clientId },
      body,
    }),

  update: (id: string, body: ClientAccountRequestDto) =>
    request<ClientAccountResponseDto>(`/accounting/client-accounts/${id}`, { method: "PUT", body }),
};

export const periodsApi = {
  list: () => request<FinancialPeriodResponseDto[]>("/accounting/periods"),

  create: (body: FinancialPeriodRequestDto) =>
    request<FinancialPeriodResponseDto>("/accounting/periods", { method: "POST", body }),

  close: (id: string) =>
    request<FinancialPeriodResponseDto>(`/accounting/periods/${id}/close`, { method: "POST" }),

  reopen: (id: string) =>
    request<FinancialPeriodResponseDto>(`/accounting/periods/${id}/reopen`, { method: "POST" }),
};

export const taxRatesApi = {
  list: () => request<TaxRateResponseDto[]>("/accounting/tax-rates"),

  create: (body: TaxRateRequestDto) =>
    request<TaxRateResponseDto>("/accounting/tax-rates", { method: "POST", body }),

  update: (id: string, body: TaxRateRequestDto) =>
    request<TaxRateResponseDto>(`/accounting/tax-rates/${id}`, { method: "PUT", body }),

  remove: (id: string) => request<void>(`/accounting/tax-rates/${id}`, { method: "DELETE" }),
};

export const reportsApi = {
  /** P&L, balance sheet, trial balance, cash, receivables aging and VAT. */
  overview: (from?: string | null, to?: string | null) =>
    request<AccountingReportResponseDto>("/accounting/reports/overview", {
      query: range(from, to),
    }),

  clientStatement: (clientId: string, from?: string | null, to?: string | null) =>
    request<ClientStatementResponseDto>(
      `/accounting/reports/clients/${clientId}/statement`,
      { query: range(from, to) },
    ),

  accountLedger: (accountId: string, q: PageQuery & { from?: string; to?: string } = {}) =>
    request<AccountLedgerResponseDto>(`/accounting/reports/accounts/${accountId}/ledger`, {
      query: { page: q.page ?? 0, size: q.size ?? 50, ...range(q.from, q.to) },
    }),
};

export const retainersApi = {
  list: () => request<RetainerResponseDto[]>("/accounting/retainers"),

  create: (body: RetainerCreateRequestDto) =>
    request<RetainerResponseDto>("/accounting/retainers", { method: "POST", body }),

  apply: (retainerId: string, body: RetainerApplicationCreateRequestDto) =>
    request<RetainerApplicationResponseDto>(`/accounting/retainers/${retainerId}/apply`, {
      method: "POST",
      body,
    }),

  applications: (retainerId: string) =>
    request<RetainerApplicationResponseDto[]>(`/accounting/retainers/${retainerId}/applications`),
};
