import type { CSSProperties } from "react";

/**
 * Giới hạn số dòng hiển thị bằng CSS thuần (-webkit-line-clamp) thay vì class Tailwind
 * `line-clamp-*` — xác nhận qua kiểm tra thực tế: class Tailwind không cắt dòng như kỳ vọng
 * (chưa rõ do cache build hay thiếu hỗ trợ ở Tailwind v4 cấu hình hiện tại), dùng inline style
 * đảm bảo chạy đúng bất kể cấu hình Tailwind.
 */
export function clampLines(lines: number): CSSProperties {
  return {
    display: "-webkit-box",
    WebkitLineClamp: lines,
    WebkitBoxOrient: "vertical",
    overflow: "hidden"
  };
}
