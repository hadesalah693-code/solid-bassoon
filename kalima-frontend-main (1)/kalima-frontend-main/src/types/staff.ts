export type SystemRole =
  | "ADMIN"
  | "SENIOR_LAWYER"
  | "LAWYER"
  | "SECRETARY"
  | "ACCOUNTANT"
  | "CLIENT"
  | "CLIENT_USER";

export type Staff = {
  id: string;
  companyId: string;
  fullName: string;
  email: string | null;
  phone: string | null;
  systemRole: SystemRole;
  nationalId: string | null;
  address: string | null;
  notes: string | null;
  isActive: boolean;
  lastLoginAt: string | null;
  createdAt: string;
  updatedAt: string;
};

export type StaffSystemRole = Exclude<SystemRole, "CLIENT">;

/** Request body for POST /api/staff. */
export type StaffCreateRequest = {
  systemRole: StaffSystemRole;
  fullName: string;
  email: string;
  password: string;
  nationalId?: string | null;
  phone?: string | null;
  address?: string | null;
  notes?: string | null;
};

/** Request body for PUT /api/staff/{id} — email and role are not editable via API. */
export type StaffUpdateRequest = {
  fullName: string;
  nationalId?: string | null;
  phone?: string | null;
  address?: string | null;
  notes?: string | null;
};
