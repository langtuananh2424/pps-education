/**
 * Helper định vị GPS dùng chung — trích từ SelfAttendanceCard.tsx (UC-09
 * Chấm công) để tái dùng cho "Nhận Lớp" (UC-71, bổ sung ngoài SDD gốc, đã
 * xác nhận với người dùng 2026-08-18). Không đổi hành vi UC-09.
 */
/**
 * `t` dịch qua i18next namespace "common" (key `geolocation.*`) — xem src/i18n/locales/{vi,en}/common.json.
 * Tham số `t` để tùy chọn (không phải mọi nơi gọi đã được đồng bộ song ngữ) — bỏ trống thì giữ
 * nguyên câu tiếng Việt gốc như trước, không phá hành vi các màn hình chưa dịch.
 */
export function getCurrentPosition(t?: (key: string) => string): Promise<GeolocationPosition> {
  return new Promise((resolve, reject) => {
    if (!navigator.geolocation) {
      reject(new Error(t ? t("geolocation.unsupported") : "Trình duyệt không hỗ trợ định vị GPS."));
      return;
    }
    navigator.geolocation.getCurrentPosition(resolve, reject, {
      enableHighAccuracy: true,
      timeout: 15000,
      maximumAge: 30000
    });
  });
}

/**
 * GeolocationPositionError.code không phải lúc nào cũng là "chưa cấp quyền" — trước đây hiện
 * chung 1 câu "vui lòng cho phép quyền định vị" cho MỌI lỗi GPS, gây hiểu lầm khi người dùng đã
 * cấp quyền trình duyệt rồi mà vẫn lỗi (thường do POSITION_UNAVAILABLE: tắt Location Services ở
 * hệ điều hành, hoặc TIMEOUT: máy không có GPS/Wi-Fi định vị được trong thời gian chờ).
 */
export function describeGeolocationError(err: { code: number; message?: string }, t?: (key: string, options?: { detail?: string }) => string): string {
  // Dùng literal số (1/2/3) thay vì err.PERMISSION_DENIED/... — các hằng số này lúc có lúc
  // không truy cập được tuỳ trình duyệt/cách object lỗi được tạo ra, literal luôn đáng tin cậy.
  switch (err.code) {
    case 1: // PERMISSION_DENIED
      return t
        ? t("geolocation.permissionDenied")
        : "Trình duyệt đang CHẶN quyền định vị cho trang này — bấm vào biểu tượng khoá/vị trí trên thanh địa chỉ, chọn Vị trí = Cho phép, rồi tải lại trang và thử lại.";
    case 2: // POSITION_UNAVAILABLE
      return t
        ? t("geolocation.positionUnavailable")
        : "Không xác định được vị trí hiện tại — kiểm tra dịch vụ định vị (Location Services) của máy/hệ điều hành đã bật chưa, rồi thử lại.";
    case 3: // TIMEOUT
      return t ? t("geolocation.timeout") : "Định vị GPS quá thời gian chờ — thử lại ở nơi có tín hiệu GPS/Wi-Fi tốt hơn.";
    default:
      if (t) return err.message ? t("geolocation.unknownWithDetail", { detail: err.message }) : t("geolocation.unknown");
      return `Không lấy được vị trí GPS${err.message ? ` (${err.message})` : ""} — vui lòng thử lại.`;
  }
}
