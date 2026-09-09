const ACCESS_TOKEN_KEY = "pps_access_token";
const REFRESH_TOKEN_KEY = "pps_refresh_token";

/**
 * localStorage (đổi từ sessionStorage — bổ sung ngoài SDD gốc, đã xác nhận với người dùng
 * 2026-09-05): Portal chủ yếu được cài qua shortcut (Add to Home Screen) trên điện thoại
 * Phụ huynh/Học sinh — thoát hẳn app (không chỉ đóng tab) trước đây luôn xoá sessionStorage,
 * buộc đăng nhập lại mỗi lần mở shortcut. localStorage sống qua việc thoát/mở lại app, tự
 * "nhớ đăng nhập" tới hết hạn refresh token (14 ngày, tự gia hạn mỗi lần dùng — xem
 * AuthService.issueRefreshToken/application.yml app.jwt.refresh-token-ttl-days).
 */
export function getAccessToken(): string | null {
  return localStorage.getItem(ACCESS_TOKEN_KEY);
}

export function getRefreshToken(): string | null {
  return localStorage.getItem(REFRESH_TOKEN_KEY);
}

export function setTokens(accessToken: string, refreshToken: string): void {
  localStorage.setItem(ACCESS_TOKEN_KEY, accessToken);
  localStorage.setItem(REFRESH_TOKEN_KEY, refreshToken);
}

export function clearTokens(): void {
  localStorage.removeItem(ACCESS_TOKEN_KEY);
  localStorage.removeItem(REFRESH_TOKEN_KEY);
}
