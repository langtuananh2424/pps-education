/** Backend trả giờ dạng "HH:mm:ss" (LocalTime) — Portal chỉ cần hiện "HH:mm", bỏ phần giây thừa. */
export function formatHm(time: string): string {
  return time.slice(0, 5);
}

/** ISO datetime (VD hạn nộp) — hiện "dd/MM/yyyy HH:mm", bỏ phần giây thừa mà toLocaleString mặc định thêm vào. */
export function formatDate(isoDate: string): string {
  return new Date(isoDate).toLocaleDateString("vi-VN", {
    day: "2-digit",
    month: "2-digit",
    year: "numeric"
  });
}

export function formatDateTimeHm(isoDateTime: string): string {
  return new Date(isoDateTime).toLocaleString("vi-VN", {
    day: "2-digit",
    month: "2-digit",
    year: "numeric",
    hour: "2-digit",
    minute: "2-digit"
  });
}
