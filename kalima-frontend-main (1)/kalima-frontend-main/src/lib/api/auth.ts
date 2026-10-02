import {
  loginRequest,
  logoutRequest,
  refreshRequest,
} from "@/lib/api/auth-http";

export type { LoginRequest, LoginResponse } from "@/lib/api/auth-types";

export const authApi = {
  login: loginRequest,
  refresh: refreshRequest,
  logout: logoutRequest,
};
