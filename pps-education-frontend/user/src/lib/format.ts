/** Chuyển i18next language ("vi"/"en") sang locale tag chuẩn cho Intl/toLocaleString — dùng chung
 *  thay vì hard-code "vi-VN" ở từng nơi gọi (xem Phase 0, kế hoạch đồng bộ song ngữ). */
export function toLocaleTag(language: string): string {
  return language === "en" ? "en-US" : "vi-VN";
}

/** Backend trả giờ dạng "HH:mm:ss" (LocalTime) — Portal chỉ cần hiện "HH:mm", bỏ phần giây thừa. */
export function formatHm(time: string): string {
  return time.slice(0, 5);
}

/** ISO datetime (VD hạn nộp) — hiện "dd/MM/yyyy" (mặc định vi), bỏ phần giây thừa mà toLocaleString mặc định thêm vào. */
export function formatDate(isoDate: string, language = "vi"): string {
  return new Date(isoDate).toLocaleDateString(toLocaleTag(language), {
    day: "2-digit",
    month: "2-digit",
    year: "numeric"
  });
}

export function formatDateTimeHm(isoDateTime: string, language = "vi"): string {
  return new Date(isoDateTime).toLocaleString(toLocaleTag(language), {
    day: "2-digit",
    month: "2-digit",
    year: "numeric",
    hour: "2-digit",
    minute: "2-digit"
  });
}
