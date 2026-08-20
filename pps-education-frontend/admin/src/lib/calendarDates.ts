/** Hàm thuần túy tính ngày cho các view calendar tuần/tháng — dùng chung giữa nhiều feature. */

export function toISODate(date: Date): string {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")}`;
}

export function getWeekDates(refDate: Date): Date[] {
  const day = refDate.getDay();
  const diff = refDate.getDate() - day + (day === 0 ? -6 : 1);
  const monday = new Date(refDate);
  monday.setDate(diff);
  return Array.from({ length: 7 }, (_, i) => {
    const d = new Date(monday);
    d.setDate(monday.getDate() + i);
    return d;
  });
}

/** Lưới 6 tuần x 7 ngày (bắt đầu Thứ 2) phủ trọn tháng chứa refDate — kể cả ngày tháng trước/sau để lấp đầy hàng. */
export function getMonthGridDates(refDate: Date): Date[] {
  const year = refDate.getFullYear();
  const month = refDate.getMonth();
  const firstOfMonth = new Date(year, month, 1);
  const firstDay = firstOfMonth.getDay();
  const startOffset = firstDay === 0 ? 6 : firstDay - 1;
  const gridStart = new Date(year, month, 1 - startOffset);
  return Array.from({ length: 42 }, (_, i) => new Date(gridStart.getFullYear(), gridStart.getMonth(), gridStart.getDate() + i));
}
