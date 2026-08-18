import type { ShiftResponse } from "@/features/hrm/api";

/** Mirror AttendanceService.matchesShiftPattern (backend) — chỉ dùng để HIỂN THỊ, không dùng để chấm công thật. */
export function getIsoWeekNumber(date: Date): number {
  const d = new Date(Date.UTC(date.getFullYear(), date.getMonth(), date.getDate()));
  const dayNum = d.getUTCDay() || 7;
  d.setUTCDate(d.getUTCDate() + 4 - dayNum);
  const yearStart = new Date(Date.UTC(d.getUTCFullYear(), 0, 1));
  return Math.ceil(((d.getTime() - yearStart.getTime()) / 86400000 + 1) / 7);
}

export function matchesShiftPattern(shift: ShiftResponse, date: Date): boolean {
  if (!shift.active) return false;
  const isoDay = String(date.getDay() === 0 ? 7 : date.getDay());
  if (!shift.appliesToWeekdays.split(",").includes(isoDay)) return false;
  if (shift.weekParity === "ALL") return true;
  const oddWeek = getIsoWeekNumber(date) % 2 !== 0;
  return shift.weekParity === "ODD" ? oddWeek : !oddWeek;
}
