import { request } from "./client";
import type {
  OfficeSettingsDto,
  OfficeSettingsUpdateInput,
} from "@/types/office-settings";

export const officeSettingsApi = {
  get: () => request<OfficeSettingsDto>("/office/settings"),

  update: (body: OfficeSettingsUpdateInput) =>
    request<OfficeSettingsDto>("/office/settings", {
      method: "PUT",
      body,
    }),
};
