const ACCESS_TOKEN_KEY = "pps_access_token";
const REFRESH_TOKEN_KEY = "pps_refresh_token";

/**
 * sessionStorage (không phải localStorage) — cố ý: đóng hẳn trình duyệt/tab phải buộc đăng
 * nhập lại. Trong lúc tab còn mở (refresh trang, điều hướng SPA) vẫn giữ đăng nhập bình
 * thường vì sessionStorage sống hết vòng đời tab, chỉ mất khi tab/trình duyệt đóng.
 */
export function getAccessToken(): string | null {
  return sessionStorage.getItem(ACCESS_TOKEN_KEY);
}

export function getRefreshToken(): string | null {
  return sessionStorage.getItem(REFRESH_TOKEN_KEY);
}

export function setTokens(accessToken: string, refreshToken: string): void {
  sessionStorage.setItem(ACCESS_TOKEN_KEY, accessToken);
  sessionStorage.setItem(REFRESH_TOKEN_KEY, refreshToken);
}

export function clearTokens(): void {
  sessionStorage.removeItem(ACCESS_TOKEN_KEY);
  sessionStorage.removeItem(REFRESH_TOKEN_KEY);
}
