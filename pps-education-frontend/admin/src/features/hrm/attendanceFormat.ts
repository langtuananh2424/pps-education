import { AttendanceRecordResponse } from "./api";

export type AttendanceStatus = Exclude<AttendanceRecordResponse["status"], null>;

export const attendanceStatusVariant: Record<AttendanceStatus, "success" | "danger" | "warning" | "neutral"> = {
  NORMAL: "success",
  LATE: "warning",
  EARLY_LEAVE: "warning",
  MISSING: "danger"
};

export const attendanceStatusLabels: Record<AttendanceStatus, string> = {
  NORMAL: "Đúng giờ",
  LATE: "Đi muộn",
  EARLY_LEAVE: "Về sớm",
  MISSING: "Thiếu chấm công"
};

export const attendanceMethodLabels: Record<string, string> = {
  GPS: "GPS",
  FINGERPRINT: "Vân tay",
  FACE: "Khuôn mặt",
  MANUAL: "Thủ công"
};

export function formatAttendanceTime(value: string | null): string {
  if (!value) return "—";
  return new Date(value).toLocaleTimeString("vi-VN", { hour: "2-digit", minute: "2-digit" });
}
