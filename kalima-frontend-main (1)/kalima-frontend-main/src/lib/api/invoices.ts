import { request } from "./client";
import type { PagedResponse } from "@/types/common";
import type {
  InvoiceCreateRequestDto,
  InvoiceListItemDto,
  InvoicePaymentRequestDto,
  InvoiceResponseDto,
  InvoiceStatisticsDto,
  InvoiceStatusDto,
  InvoiceStatusUpdateRequestDto,
} from "@/types/invoice";

export type InvoicesListQuery = {
  page?: number;
  size?: number;
  search?: string;
  status?: InvoiceStatusDto;
  clientId?: string;
};

export const invoicesApi = {
  /** GET /api/invoices — tenant-scoped, server derives companyId from the JWT. */
  list: (q: InvoicesListQuery = {}) =>
    request<PagedResponse<InvoiceListItemDto>>("/invoices", {
      query: {
        page: q.page ?? 0,
        size: q.size ?? 20,
        ...(q.search?.trim() ? { search: q.search.trim() } : {}),
        ...(q.status ? { status: q.status } : {}),
        ...(q.clientId ? { clientId: q.clientId } : {}),
      },
    }),

  /** GET /api/invoices/statistics — must precede `/{id}` route matching. */
  statistics: () => request<InvoiceStatisticsDto>("/invoices/statistics"),

  get: (id: string) => request<InvoiceResponseDto>(`/invoices/${id}`),

  create: (body: InvoiceCreateRequestDto) =>
    request<InvoiceResponseDto>("/invoices", { method: "POST", body }),

  updateStatus: (id: string, status: InvoiceStatusDto) =>
    request<InvoiceResponseDto>(`/invoices/${id}/status`, {
      method: "PATCH",
      body: { status } satisfies InvoiceStatusUpdateRequestDto,
    }),

  recordPayment: (id: string, body: InvoicePaymentRequestDto) =>
    request<InvoiceResponseDto>(`/invoices/${id}/payments`, {
      method: "POST",
      body,
    }),
};
