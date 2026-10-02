export type PersonType = "INDIVIDUAL" | "COMPANY";

/** Mirrors `PersonResponse` from the backend. */
export type ClientPerson = {
  id: string;
  personType: PersonType;
  systemRole: string;
  fullName: string | null;
  companyName: string | null;
  nationalId: string | null;
  commercialRegister: string | null;
  phone: string | null;
  email: string | null;
  address: string | null;
  notes: string | null;
  active: boolean;
  lastLoginAt: string | null;
  archivedAt: string | null;
  createdAt: string;
  updatedAt: string;
};

export type PersonRequest = {
  personType: PersonType;
  systemRole?: "CLIENT" | null;
  fullName?: string;
  nationalId?: string;
  companyName?: string;
  commercialRegister?: string;
  phone?: string;
  email?: string;
  address?: string;
  notes?: string;
  password?: string;
};
