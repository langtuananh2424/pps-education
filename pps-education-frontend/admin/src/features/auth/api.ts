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
  /** Effective permissions (hợp nhất role + override) của chính tài khoản đang gọi — dùng cho AppContext.hasPermission thay bảng mock tĩnh. */
  permissions: string[];
}

/**
 * Bổ sung ngoài SDD gốc (đã xác nhận với người dùng 2026-09-05) — metadata thiết bị gửi kèm mỗi lần
 * đăng nhập, phục vụ lịch sử đăng nhập ở Quản lý người dùng → Xem/Sửa (LoginAttempt/UC-44).
 */
function deviceMetadata() {
  return {
    screenResolution: `${window.screen.width}x${window.screen.height}`,
    browserLanguage: navigator.language,
    timezone: Intl.DateTimeFormat().resolvedOptions().timeZone
  };
}

export async function login(usernameOrEmail: string, password: string): Promise<void> {
  const response = await apiRequest<LoginResponse>("/auth/login", {
    method: "POST",
    skipAuth: true,
    body: JSON.stringify({ usernameOrEmail, password, ...deviceMetadata() })
  });
  setTokens(response.accessToken, response.refreshToken);
}

/** UC-01 Main Flow bước 4 — idToken lấy từ Google Identity Services (credential trả về của nút Sign in with Google). */
export async function loginWithGoogle(idToken: string): Promise<void> {
  const response = await apiRequest<LoginResponse>("/auth/login/google", {
    method: "POST",
    skipAuth: true,
    body: JSON.stringify({ idToken, ...deviceMetadata() })
  });
  setTokens(response.accessToken, response.refreshToken);
}

export function fetchCurrentUser(): Promise<CurrentUserResponse> {
  return apiRequest<CurrentUserResponse>("/auth/me");
}

/**
 * UC-45 Main Flow: tự đổi mật khẩu của chính tài khoản đang đăng nhập.
 * currentPassword để trống chỉ hợp lệ với tài khoản chưa từng có mật khẩu (chỉ đăng nhập Google — UC-45 A3).
 */
export function changeOwnPassword(currentPassword: string, newPassword: string): Promise<void> {
  return apiRequest<void>("/auth/me/password", {
    method: "PUT",
    body: JSON.stringify({ currentPassword: currentPassword || undefined, newPassword })
  });
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
