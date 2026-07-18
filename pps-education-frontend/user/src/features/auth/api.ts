import { apiRequest } from "@/lib/apiClient";
import { clearTokens, getRefreshToken, setTokens } from "@/lib/tokenStorage";

/** Khớp LoginResponse thật của backend — xem API.md > Xác thực (UC-01). */
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
  /** UC-42: null trừ khi tài khoản có hồ sơ Student liên kết — dùng để Học sinh tự tra ra studentId của chính mình. */
  studentId?: number | null;
}

export async function login(usernameOrEmail: string, password: string): Promise<void> {
  const response = await apiRequest<LoginResponse>("/auth/login", {
    method: "POST",
    skipAuth: true,
    body: JSON.stringify({ usernameOrEmail, password })
  });
  setTokens(response.accessToken, response.refreshToken);
}

/**
 * UC-01 Main Flow bước 4 — idToken lấy từ Google Identity Services. A4: nếu email
 * Google chưa khớp tài khoản nào trong hệ thống, backend trả 403 kèm message "Tài
 * khoản chưa được cấp phát... Vui lòng liên hệ Quản trị viên." (GoogleAccountNotProvisionedException) —
 * hiện thẳng message đó ra UI, không tự viết lại.
 */
export async function loginWithGoogle(idToken: string): Promise<void> {
  const response = await apiRequest<LoginResponse>("/auth/login/google", {
    method: "POST",
    skipAuth: true,
    body: JSON.stringify({ idToken })
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
