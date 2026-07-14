import { apiRequest } from "@/lib/apiClient";
import { clearTokens, getRefreshToken, setTokens } from "@/lib/tokenStorage";

/** Khớp LoginResponse/RefreshTokenResponse thật của backend — xem API.md > Xác thực (UC-01). */
interface LoginResponse {
  accessToken: string;
  refreshToken: string;
  accessTokenExpiresInSeconds: number;
}

/** Khớp CurrentUserResponse thật của backend (GET /api/auth/me). */
export interface CurrentUserResponse {
  id: number;
  username: string;
  fullName: string;
  email: string;
  phone?: string;
  departmentName?: string;
  roleCodes: string[];
}

export async function login(usernameOrEmail: string, password: string): Promise<void> {
  const response = await apiRequest<LoginResponse>("/auth/login", {
    method: "POST",
    skipAuth: true,
    body: JSON.stringify({ usernameOrEmail, password })
  });
  setTokens(response.accessToken, response.refreshToken);
}

export function fetchCurrentUser(): Promise<CurrentUserResponse> {
  return apiRequest<CurrentUserResponse>("/auth/me");
}

export async function logout(): Promise<void> {
  const refreshToken = getRefreshToken();
  if (refreshToken) {
    try {
      await apiRequest<void>("/auth/logout", {
        method: "POST",
        skipAuth: true,
        body: JSON.stringify({ refreshToken })
      });
    } catch {
      // Best-effort — vẫn xoá session cục bộ dù gọi logout backend thất bại.
    }
  }
  clearTokens();
}
