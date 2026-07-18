/** Chuyển tên tiếng Việt có dấu thành mã SCREAMING_SNAKE_CASE (VD "Trưởng nhóm Marketing" -> "TRUONG_NHOM_MARKETING"). */
export function toCodeSlug(text: string): string {
  return text
    .normalize("NFD")
    .replace(/[̀-ͯ]/g, "") // bỏ dấu (combining diacritical marks)
    .replace(/đ/gi, "d") // "đ" không tách dấu qua NFD, phải thay tay
    .toUpperCase()
    .replace(/[^A-Z0-9]+/g, "_")
    .replace(/^_+|_+$/g, "");
}
