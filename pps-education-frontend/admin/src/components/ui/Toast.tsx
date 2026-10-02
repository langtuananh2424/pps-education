import React from "react";
import FloatingBanner from "./FloatingBanner";

interface ToastProps {
  message: string | null;
  /** Giữ để không phải sửa nơi gọi cũ — từ 2026-10-01 mọi toast đều là banner xanh ở giữa phía trên. */
  position?: "bottom-right" | "top-center";
}

/**
 * Thông báo THÀNH CÔNG — từ 2026-10-01 hiển thị bằng banner nổi xanh ở giữa phía trên (chung ngăn xếp
 * với banner lỗi, xem FloatingBanner) thay cho toast đen góc dưới phải. Thời gian tự ẩn do useToast quản lý.
 */
export default function Toast({ message }: ToastProps) {
  return <FloatingBanner variant="success" message={message} autoHideMs={0} />;
}
