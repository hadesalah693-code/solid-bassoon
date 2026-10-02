import type { SystemRole } from "./staff";

export type CurrentUser = {
  id: string;
  fullName: string;
  email: string;
  systemRole: SystemRole;
};
