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

/** Thời lượng dạng đồng hồ, không phụ thuộc ngôn ngữ: "12:30" (phút:giây) hoặc "1:05:09" (giờ:phút:giây). */
export function formatActiveDuration(totalSeconds: number): string {
  const s = Math.max(0, Math.round(totalSeconds));
  const h = Math.floor(s / 3600);
  const m = Math.floor((s % 3600) / 60);
  const sec = s % 60;
  const pad = (n: number) => String(n).padStart(2, "0");
  return h > 0 ? `${h}:${pad(m)}:${pad(sec)}` : `${pad(m)}:${pad(sec)}`;
}
