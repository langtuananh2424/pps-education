const ACCESS_TOKEN_KEY = "pps_access_token";
const REFRESH_TOKEN_KEY = "pps_refresh_token";

/**
 * Nơi lưu token phụ thuộc ô "Ghi nhớ đăng nhập" ở trang login (bổ sung ngoài SDD gốc, sửa lỗi
 * 2026-09-24 — trước đây ô này chỉ là UI, token luôn nằm sessionStorage nên tick hay không đều bị
 * đăng xuất khi đóng tab/trình duyệt, đặc biệt rõ trên iOS vì Safari/PWA xoá sessionStorage mỗi khi
 * app bị thoát hẳn hoặc hệ điều hành thu hồi tab chạy nền):
 * - Không tick → sessionStorage: đóng hẳn trình duyệt/tab phải đăng nhập lại (hành vi mặc định cũ).
 * - Có tick → localStorage: sống qua việc đóng/mở lại trình duyệt/app, tới khi refresh token hết hạn
 *   (14 ngày, tự gia hạn mỗi lần dùng — xem application.yml app.jwt.refresh-token-ttl-days).
 * Mọi dữ liệu gắn với phiên đăng nhập (VD cache hồ sơ ở AppContext) phải dùng {@link getAuthStorage}
 * để nằm cùng chỗ với token.
 */
function hasTokens(storage: Storage): boolean {
  return !!storage.getItem(REFRESH_TOKEN_KEY) || !!storage.getItem(ACCESS_TOKEN_KEY);
}

/** Storage đang giữ phiên hiện tại — ưu tiên sessionStorage (phiên mới nhất của tab này), rồi tới localStorage. */
export function getAuthStorage(): Storage {
  if (hasTokens(sessionStorage)) return sessionStorage;
  if (hasTokens(localStorage)) return localStorage;
  return sessionStorage;
}

export function getAccessToken(): string | null {
  return getAuthStorage().getItem(ACCESS_TOKEN_KEY);
}

export function getRefreshToken(): string | null {
  return getAuthStorage().getItem(REFRESH_TOKEN_KEY);
}

/**
 * rememberMe truyền khi đăng nhập mới để chọn nơi lưu; bỏ trống (VD xoay vòng token ở /auth/refresh)
 * thì ghi đè đúng storage đang giữ phiên, không đổi lựa chọn người dùng đã chọn lúc đăng nhập.
 */
export function setTokens(accessToken: string, refreshToken: string, rememberMe?: boolean): void {
  let target: Storage;
  if (rememberMe === undefined) {
    target = getAuthStorage();
  } else {
    // Đăng nhập mới — dọn phiên cũ ở cả 2 nơi để không còn token lẫn lộn giữa 2 lựa chọn.
    clearTokens();
    target = rememberMe ? localStorage : sessionStorage;
  }
  target.setItem(ACCESS_TOKEN_KEY, accessToken);
  target.setItem(REFRESH_TOKEN_KEY, refreshToken);
}

export function clearTokens(): void {
  for (const storage of [sessionStorage, localStorage]) {
    storage.removeItem(ACCESS_TOKEN_KEY);
    storage.removeItem(REFRESH_TOKEN_KEY);
  }
}
