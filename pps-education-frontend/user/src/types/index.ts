/** Khớp shape phân trang thật của Spring Data `Page<T>` — dùng chung cho mọi API GET có phân trang. */
export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number; // Trang hiện tại, 0-based
  size: number;
}
