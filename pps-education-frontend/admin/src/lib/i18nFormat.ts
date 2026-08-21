/** Chuyển i18next language ("vi"/"en") sang locale tag chuẩn cho Intl/toLocaleString — dùng chung
 *  thay vì hard-code "vi-VN" ở từng nơi gọi (xem Phase 0, kế hoạch đồng bộ song ngữ). */
export function toLocaleTag(language: string): string {
  return language === "en" ? "en-US" : "vi-VN";
}

export function formatDateLong(date: Date, language: string): string {
  return date.toLocaleDateString(toLocaleTag(language), { year: "numeric", month: "long", day: "numeric" });
}

export function formatTimeHm(value: string | null | undefined, language: string): string {
  if (!value) return "—";
  return new Date(value).toLocaleTimeString(toLocaleTag(language), { hour: "2-digit", minute: "2-digit" });
}

export function formatDateTime(value: string, language: string): string {
  return new Date(value).toLocaleString(toLocaleTag(language));
}
