export type PagedResponse<T> = {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
  first: boolean;
  last: boolean;
};

export type ApiError = {
  timestamp: string;
  status: number;
  code: string;
  message: string;
  path: string;
  errors?: { field: string; message: string }[];
};

export type Party = {
  id: string;
  fullName: string | null;
  systemRole?: string | null;
};
