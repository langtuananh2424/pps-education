import React from "react";
import FloatingBanner, { FloatingBannerVariant } from "@/components/ui/FloatingBanner";

interface NotificationBannerProps {
  message: string | null;
  onClose: () => void;
  /** Bỏ trống thì tự suy theo emoji đầu câu (quy ước sẵn có trong file i18n): ⚠️ → cảnh báo, 📅 → thông tin,
   *  còn lại (✅/💾/🔔/không emoji) → thành công. */
  variant?: FloatingBannerVariant;
}

const LEADING_EMOJI = /^\s*(?:\p{Extended_Pictographic}️?\s*)+/u;

function inferVariant(message: string): FloatingBannerVariant {
  const text = message.trimStart();
  if (text.startsWith("⚠")) return "warning";
  if (text.startsWith("📅")) return "info";
  return "success";
}

/**
 * Thông báo sau thao tác (Nhận xét hằng ngày, Duyệt nhận xét, Duyệt nghỉ phép...) — từ 2026-10-01 là banner
 * nổi ở giữa phía trên (FloatingBanner) thay cho dải cam nằm trong trang.
 */
export default function NotificationBanner({ message, onClose, variant }: NotificationBannerProps) {
  if (!message) return null;
  const resolved = variant ?? inferVariant(message);
  // Emoji đầu câu (✅/⚠️/📅...) chỉ để phân loại — banner đã có icon + màu riêng nên bỏ đi, tránh 2 biểu tượng cạnh nhau.
  const text = message.replace(LEADING_EMOJI, "");
  // Câu thông báo ở đây thường dài hơn toast (VD "Đã gửi nhận xét N học sinh lên...") — thành công giữ 6s thay vì 4s.
  return <FloatingBanner message={text} onClose={onClose} variant={resolved} autoHideMs={resolved === "success" ? 6000 : undefined} />;
}
