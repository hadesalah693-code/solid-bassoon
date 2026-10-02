export type OfficeSettingsDto = {
  id: string;
  name: string;
  legalName: string | null;
  commercialRegister: string | null;
  taxNumber: string | null;
  phone: string | null;
  email: string | null;
  address: string | null;
  website: string | null;
  city: string | null;
  countryCode: string;
  active: boolean;
  createdAt: string;
  updatedAt: string;
};

export type OfficeSettingsUpdateInput = {
  name: string;
  legalName?: string | null;
  commercialRegister?: string | null;
  taxNumber?: string | null;
  phone?: string | null;
  email?: string | null;
  address?: string | null;
  website?: string | null;
  city?: string | null;
  countryCode?: string;
};
