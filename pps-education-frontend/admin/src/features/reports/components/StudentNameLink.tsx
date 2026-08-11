import React from "react";
import { useStudentProfileModal } from "../context/StudentProfileModalContext";

/**
 * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng — thay mọi chỗ hiển thị
 * tên học sinh dạng text thuần bằng link mở popup "Hồ sơ học tập" (xem
 * StudentProfileModal). stopPropagation để không kích hoạt onClick của
 * hàng cha (VD DailyCommentPanel có onClick riêng trên <tr> khi đã khoá).
 */
export default function StudentNameLink({
  studentId,
  name,
  className,
}: {
  studentId: number;
  name: string;
  className?: string;
}) {
  const { openStudentProfile } = useStudentProfileModal();
  return (
    <button
      type="button"
      onClick={(e) => {
        e.stopPropagation();
        openStudentProfile(studentId);
      }}
      className={className ?? "font-bold text-slate-900 hover:text-brand-red hover:underline text-left"}
    >
      {name}
    </button>
  );
}
