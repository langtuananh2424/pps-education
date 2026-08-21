import { AttendanceRecordResponse } from "./api";
import { formatTimeHm } from "@/lib/i18nFormat";

export type AttendanceStatus = Exclude<AttendanceRecordResponse["status"], null>;

export const attendanceStatusVariant: Record<AttendanceStatus, "success" | "danger" | "warning" | "neutral"> = {
  NORMAL: "success",
  LATE: "warning",
  EARLY_LEAVE: "warning",
  MISSING: "danger"
};

/** Nhãn dịch qua i18next namespace "hrm-attendance" — xem src/i18n/locales/{vi,en}/hrm-attendance.json. */
export function attendanceStatusLabel(t: (key: string) => string, status: AttendanceStatus): string {
  return t(`attendanceStatus.${status}`);
}

export function attendanceMethodLabel(t: (key: string) => string, method: string): string {
  return t(`attendanceMethod.${method}`);
}

export function formatAttendanceTime(value: string | null, language: string): string {
  return formatTimeHm(value, language);
}
