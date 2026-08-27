import { useEffect, useState } from "react";
import { getStudentAttendanceGracePeriodMinutes } from "../api";

/**
 * UC-15 (V144): số phút nới thêm sau end_time buổi học để GV vẫn điểm
 * danh/sửa được — dùng chung cho AttendancePage.tsx/ClassDetailPanel.tsx để
 * tính khung giờ khoá nút [startTime, endTime + gracePeriodMinutes], đồng
 * bộ với StudentAttendanceService.isWithinSessionWindow (trước đây 2 nơi
 * này hardcode [startTime, endTime] không cộng grace, khoá nút sớm hơn
 * backend cho phép — bổ sung ngoài SDD gốc, đã xác nhận với người dùng
 * 2026-08-27). Mặc định 0 khi chưa tải xong — giữ hành vi chặt cũ trong lúc
 * chờ, không mở khoá nhầm.
 */
export function useAttendanceGracePeriodMinutes(): number {
  const [minutes, setMinutes] = useState(0);

  useEffect(() => {
    let cancelled = false;
    getStudentAttendanceGracePeriodMinutes()
      .then((res) => {
        if (!cancelled) setMinutes(res.gracePeriodMinutes);
      })
      .catch(() => undefined);
    return () => {
      cancelled = true;
    };
  }, []);

  return minutes;
}
