import React, { useEffect, useState } from "react";
import { AlertTriangle } from "lucide-react";
import { AttendanceRecordResponse, getMyTodayAttendance } from "../api";

/**
 * Nhắc mềm (KHÔNG chặn thao tác) khi GV mở màn Điểm danh/Nhận xét mà chưa chấm công hôm nay — bổ
 * sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-13: chấm công là bước đầu buổi dạy nhưng
 * không bắt buộc phải chấm mới thao tác được, chỉ nhắc nếu quên (undefined = miễn trừ/không có hồ
 * sơ nhân sự, ẩn hẳn banner — cùng quy ước với pill ở Header, xem getMyTodayAttendance).
 */
export default function AttendanceReminderBanner() {
  const [myAttendance, setMyAttendance] = useState<AttendanceRecordResponse | undefined>(undefined);

  useEffect(() => {
    getMyTodayAttendance()
      .then(setMyAttendance)
      .catch(() => setMyAttendance(undefined));
  }, []);

  if (!myAttendance || myAttendance.id != null) return null;

  return (
    <div className="flex items-center gap-2 text-xs text-amber-700 bg-amber-50 border border-amber-100 p-2.5 rounded-lg">
      <AlertTriangle className="w-3.5 h-3.5 shrink-0" />
      <span>
        Bạn <strong>chưa chấm công hôm nay</strong> — nhớ chấm công (góc trên bên phải) để hệ thống ghi nhận đã đến dạy.
      </span>
    </div>
  );
}
