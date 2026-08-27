import { useEffect, useMemo, useState } from "react";
import { CalendarClock, Clock, Search, Users } from "lucide-react";
import { useTranslation } from "react-i18next";
import { ApiError } from "@/lib/apiClient";
import { cn } from "@/lib/cn";
import { getMonthGridDates, getWeekDates, toISODate } from "@/lib/calendarDates";
import { matchesShiftPattern } from "@/lib/shiftPattern";
import { toLocaleTag, formatTimeHm } from "@/lib/i18nFormat";
import { useApp } from "@/context/AppContext";
import { Badge, Modal, TableContainer, Th, Td } from "@/components/ui";
import Select from "@/components/ui/Select";
import DatePicker from "@/components/ui/DatePicker";
import { checkInStatusLabel, checkInStatusVariants, sessionStatusVariants } from "@/features/academic/components/ClassDetailPanel";
import ClassPeriodGrid from "@/features/academic/components/ClassPeriodGrid";
import { listClasses, ClassSessionCheckInStatusResponse, ClassSessionResponse } from "@/features/academic/api";
import {
  DepartmentResponse,
  EmployeeResponse,
  EmployeeScheduleOverviewResponse,
  EmployeeShiftResponse,
  ShiftResponse,
  WorkCalendarResponse,
  getEmployeeScheduleOverview,
  listDepartments
} from "../api";

function dayTypeLabel(t: (key: string) => string, dayType: WorkCalendarResponse["dayType"]): string {
  return t(`employeeSchedulePage.dayType.${dayType}`);
}

const dayTypeVariants: Record<WorkCalendarResponse["dayType"], "success" | "warning" | "danger" | "info"> = {
  WORKING: "success",
  OFF: "warning",
  HOLIDAY: "danger",
  COMPENSATORY: "info"
};

function employeeTypeLabel(t: (key: string) => string, employeeType: EmployeeResponse["employeeType"]): string {
  return t(`employeeSchedulePage.employeeType.${employeeType}`);
}

/** Quá dài để hiện từng cột ngày (VD chọn "Năm") — chuyển sang bảng tổng hợp số liệu. */
const MAX_DAILY_COLUMNS = 31;
/** Chế độ xem "Theo lớp học" (ClassPeriodGrid) chỉ hợp lý trong 1 tuần — quá dài thì lưới quá rộng, không đọc được. */
const MAX_CLASS_GRID_DAYS = 7;

type QuickRange = "day" | "week" | "month" | "year";
/** Bổ sung ngoài SDD gốc, xác nhận với người dùng 2026-08-20 — thử gộp "Thời khóa biểu" vào đây dưới dạng 1 chế độ xem khác, để so sánh UX trước khi quyết định có bỏ hẳn trang riêng hay không. */
type ViewMode = "employee" | "classGrid";

function datesBetween(from: string, to: string): Date[] {
  const start = new Date(`${from}T00:00:00`);
  const end = new Date(`${to}T00:00:00`);
  const dates: Date[] = [];
  for (let d = new Date(start); d <= end; d.setDate(d.getDate() + 1)) {
    dates.push(new Date(d));
  }
  return dates;
}

function workCalendarAppliesToEmployee(
  override: WorkCalendarResponse,
  employee: EmployeeResponse,
  employeeShifts: EmployeeShiftResponse[]
): boolean {
  if (override.appliesToScope === "ALL") return true;
  if (override.appliesToScope === "EMPLOYEE") return override.employeeId === employee.id;
  return employeeShifts.some((es) => es.employeeId === employee.id && es.shiftId === override.shiftId);
}

// ===================== Dòng thời gian theo giờ (UC-71, yêu cầu người dùng 2026-08-19) =====================

const TIMELINE_PIXELS_PER_HOUR = 64;
const TIMELINE_MIN_BLOCK_HEIGHT = 30;

/** Màu khối trên timeline theo trạng thái nhận lớp TÍNH RA — cùng bảng màu với Badge (checkInStatusVariants) để nhất quán. */
const timelineBlockClasses: Record<string, string> = {
  NOT_YET_OPEN: "bg-slate-50 border-slate-300 text-slate-600",
  PENDING: "bg-amber-50 border-amber-300 text-amber-700",
  ON_TIME: "bg-emerald-50 border-emerald-300 text-emerald-700",
  LATE: "bg-amber-50 border-amber-300 text-amber-700",
  ABSENT: "bg-rose-50 border-rose-300 text-rose-700"
};

function timeToMinutes(hhmm: string): number {
  const [h, m] = hhmm.split(":").map(Number);
  return h * 60 + (m || 0);
}

/** Xếp buổi dạy trùng giờ (khác GV) vào các "làn" song song, kiểu Google Calendar — thuật toán greedy: buổi nào bắt đầu sau khi 1 làn đã trống thì dùng lại làn đó, không thì mở làn mới. */
function assignTimelineLanes(sessions: ClassSessionResponse[]): { session: ClassSessionResponse; lane: number; laneCount: number }[] {
  const sorted = [...sessions].sort((a, b) => timeToMinutes(a.startTime) - timeToMinutes(b.startTime));
  const laneEndMinutes: number[] = [];
  const placed: { session: ClassSessionResponse; lane: number }[] = [];
  sorted.forEach((s) => {
    const start = timeToMinutes(s.startTime);
    const end = timeToMinutes(s.endTime);
    let lane = laneEndMinutes.findIndex((endMin) => endMin <= start);
    if (lane === -1) {
      lane = laneEndMinutes.length;
      laneEndMinutes.push(end);
    } else {
      laneEndMinutes[lane] = end;
    }
    placed.push({ session: s, lane });
  });
  const laneCount = Math.max(1, laneEndMinutes.length);
  return placed.map((p) => ({ ...p, laneCount }));
}

interface DailyTimelineProps {
  sessions: ClassSessionResponse[];
  checkInStatusBySessionId: Map<number, ClassSessionCheckInStatusResponse>;
  siteNameByClassId: Record<number, string>;
  loading: boolean;
}

/** Dòng thời gian trực quan (kiểu lịch theo ngày) cho khoảng lọc đúng 1 ngày — thay bảng phẳng để "xem rõ theo từng giờ". */
function DailyTimeline({ sessions, checkInStatusBySessionId, siteNameByClassId, loading }: DailyTimelineProps) {
  const { t } = useTranslation("hrm-shifts");
  const { t: tc } = useTranslation("common");
  const { startMin, endMin } = useMemo(() => {
    if (sessions.length === 0) return { startMin: 7 * 60, endMin: 21 * 60 };
    const starts = sessions.map((s) => timeToMinutes(s.startTime));
    const ends = sessions.map((s) => timeToMinutes(s.endTime));
    return {
      startMin: Math.max(0, Math.floor(Math.min(...starts) / 60) * 60 - 60),
      endMin: Math.min(24 * 60, Math.ceil(Math.max(...ends) / 60) * 60 + 60)
    };
  }, [sessions]);

  const hourMarks = useMemo(() => {
    const marks: number[] = [];
    for (let m = startMin; m <= endMin; m += 60) marks.push(m);
    return marks;
  }, [startMin, endMin]);

  const laid = useMemo(() => assignTimelineLanes(sessions), [sessions]);
  const containerHeight = ((endMin - startMin) / 60) * TIMELINE_PIXELS_PER_HOUR;

  if (loading) {
    return <p className="text-xs text-slate-400 text-center py-10">{t("employeeSchedulePage.timeline.loading")}</p>;
  }
  if (sessions.length === 0) {
    return <p className="text-xs text-slate-400 text-center py-10">{t("employeeSchedulePage.timeline.empty")}</p>;
  }

  return (
    <div className="p-4 overflow-x-auto">
      <div className="flex" style={{ height: containerHeight }}>
        <div className="relative w-14 shrink-0">
          {hourMarks.map((m) => (
            <div
              key={m}
              className="absolute left-0 right-2 text-right text-[10px] font-mono text-slate-400 -translate-y-1/2"
              style={{ top: ((m - startMin) / 60) * TIMELINE_PIXELS_PER_HOUR }}
            >
              {String(Math.floor(m / 60)).padStart(2, "0")}:00
            </div>
          ))}
        </div>
        <div className="relative flex-1 border-l border-slate-200 min-w-[420px]">
          {hourMarks.map((m) => (
            <div
              key={m}
              className="absolute left-0 right-0 border-t border-slate-100"
              style={{ top: ((m - startMin) / 60) * TIMELINE_PIXELS_PER_HOUR }}
            />
          ))}
          {laid.map(({ session: s, lane, laneCount }) => {
            const checkInStatus = checkInStatusBySessionId.get(s.id);
            const isCancelled = s.status === "CANCELLED" || s.status === "RESCHEDULED";
            const top = ((timeToMinutes(s.startTime) - startMin) / 60) * TIMELINE_PIXELS_PER_HOUR;
            const height = Math.max(
              TIMELINE_MIN_BLOCK_HEIGHT,
              ((timeToMinutes(s.endTime) - timeToMinutes(s.startTime)) / 60) * TIMELINE_PIXELS_PER_HOUR
            );
            const statusLabel = isCancelled
              ? s.status
              : checkInStatus
              ? checkInStatusLabel(tc, checkInStatus.effectiveStatus)
              : "—";
            const siteName = siteNameByClassId[s.classId] ?? "";
            return (
              <div
                key={s.id}
                title={`${s.startTime}–${s.endTime} · ${s.primaryTeacherName} · ${s.className}${siteName ? ` · ${siteName}` : ""} · ${statusLabel}`}
                className={cn(
                  "absolute rounded-md border-l-4 px-2 py-1 overflow-hidden shadow-xs",
                  isCancelled ? "bg-slate-50 border-slate-300 text-slate-400 border-dashed" : timelineBlockClasses[checkInStatus?.effectiveStatus ?? "NOT_YET_OPEN"]
                )}
                style={{
                  top,
                  height,
                  left: `calc(${(lane / laneCount) * 100}% + 2px)`,
                  width: `calc(${100 / laneCount}% - 4px)`
                }}
              >
                <p className="text-[10px] font-mono font-bold leading-tight">{s.startTime}–{s.endTime}</p>
                <p className="text-[10px] font-bold truncate leading-tight">{s.primaryTeacherName}</p>
                <p className="text-[10px] truncate leading-tight">{s.className}{siteName && ` · ${siteName}`}</p>
                {isCancelled ? (
                  <p className="text-[9px] italic truncate leading-tight">{statusLabel}</p>
                ) : (
                  <p className="text-[9px] font-semibold truncate leading-tight">{statusLabel}</p>
                )}
              </div>
            );
          })}
        </div>
      </div>
    </div>
  );
}

/**
 * Bổ sung ngoài SDD gốc, xác nhận 2026-08-17: trang "Lịch làm việc" (roster
 * toàn công ty) cho HR/Điều hành — thay thế tab đơn-nhân-viên cũ, gộp ca
 * làm cố định + lịch dạy + lịch nghỉ lễ của TOÀN BỘ nhân viên, lọc theo
 * phòng ban/điểm trường/lớp/nhân viên. Gate bằng quyền
 * hrm.employee-schedule.view. Xem docs/uc/phan-he-04-nhan-su.md (UC-70).
 */
export default function EmployeeSchedulePage() {
  const { t, i18n } = useTranslation("hrm-shifts");
  const language = i18n.language;
  const { hasPermission, selectedCampusId, selectedClassId } = useApp();
  const canView = hasPermission("hrm.employee-schedule.view");

  const today = toISODate(new Date());
  // Mặc định "Theo lớp học" (xác nhận với người dùng 2026-08-20) — đây là nhu cầu dùng thường xuyên hơn (xếp/xem lịch dạy) so với roster nhân viên.
  const [viewMode, setViewMode] = useState<ViewMode>("classGrid");
  const [quickRange, setQuickRange] = useState<QuickRange>("week");
  const [from, setFrom] = useState(() => toISODate(getWeekDates(new Date())[0]));
  const [to, setTo] = useState(() => toISODate(getWeekDates(new Date())[6]));
  const [departmentId, setDepartmentId] = useState<number | "">("");
  const [employeeId, setEmployeeId] = useState<number | "">("");

  const [departments, setDepartments] = useState<DepartmentResponse[]>([]);
  const [allEmployees, setAllEmployees] = useState<EmployeeResponse[]>([]);
  /** UC-71 — map classId -> tên điểm trường, tải KHÔNG lọc theo site/lớp đang chọn ở Header. */
  const [siteNameByClassId, setSiteNameByClassId] = useState<Record<number, string>>({});

  const [overview, setOverview] = useState<EmployeeScheduleOverviewResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [detail, setDetail] = useState<{ employee: EmployeeResponse; date: string } | null>(null);

  useEffect(() => {
    listDepartments().then(setDepartments).catch(() => setDepartments([]));
    listClasses()
      .then((allClasses) => {
        const map: Record<number, string> = {};
        allClasses.forEach((c) => {
          map[c.id] = c.siteName;
        });
        setSiteNameByClassId(map);
      })
      .catch(() => undefined);
  }, []);

  const dateRange = useMemo(() => datesBetween(from, to), [from, to]);
  // Bỏ bộ lọc "Trường"/"Lớp" riêng của trang này (xác nhận với người dùng 2026-08-21) — dùng thẳng
  // điểm trường/lớp đang chọn ở dropdown Header (AppContext.selectedCampusId/selectedClassId), đỡ
  // phải chọn 2 lần cùng 1 thứ.
  const classGridSiteId = selectedCampusId !== "ALL" ? Number(selectedCampusId) : null;

  const applyQuickRange = (range: QuickRange, refDate = new Date()) => {
    setQuickRange(range);
    if (range === "day") {
      const d = toISODate(refDate);
      setFrom(d);
      setTo(d);
    } else if (range === "week") {
      const week = getWeekDates(refDate);
      setFrom(toISODate(week[0]));
      setTo(toISODate(week[6]));
    } else if (range === "month") {
      setFrom(toISODate(new Date(refDate.getFullYear(), refDate.getMonth(), 1)));
      setTo(toISODate(new Date(refDate.getFullYear(), refDate.getMonth() + 1, 0)));
    } else {
      setFrom(toISODate(new Date(refDate.getFullYear(), 0, 1)));
      setTo(toISODate(new Date(refDate.getFullYear(), 11, 31)));
    }
  };

  const load = () => {
    if (!canView) return;
    setLoading(true);
    setError(null);
    getEmployeeScheduleOverview({
      from,
      to,
      departmentId: departmentId === "" ? undefined : departmentId,
      siteId: classGridSiteId ?? undefined,
      classId: selectedClassId ?? undefined,
      employeeId: employeeId === "" ? undefined : employeeId
    })
      .then((res) => {
        setOverview(res);
        setAllEmployees((prev) => (prev.length === 0 ? res.employees : prev));
      })
      .catch((err) => {
        // Quyền có thể đổi runtime -- bỏ qua 403 âm thầm thay vì báo lỗi gây hoang mang.
        if (err instanceof ApiError && err.status === 403) {
          setOverview(null);
          return;
        }
        setError(err instanceof ApiError ? err.message : t("employeeSchedulePage.loadError"));
      })
      .finally(() => setLoading(false));
  };

  useEffect(load, [from, to, departmentId, classGridSiteId, selectedClassId, employeeId, canView]);

  // Danh sách nhân viên cho dropdown filter -- tải riêng 1 lần, KHÔNG lọc theo site/class
  // (khác overview.employees vốn đã bị thu hẹp theo filter hiện tại) để không tự xóa lựa
  // chọn của người dùng khi họ đổi site/class sau khi đã chọn 1 nhân viên.
  useEffect(() => {
    getEmployeeScheduleOverview({ from: today, to: today }).then((res) => setAllEmployees(res.employees)).catch(() => undefined);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const shiftById = useMemo(() => new Map((overview?.shiftDefinitions ?? []).map((s) => [s.id, s])), [overview]);
  /** UC-71 (bổ sung ngoài SDD gốc, xác nhận 2026-08-18) — trạng thái nhận lớp TÍNH RA theo classSessionId. */
  const checkInStatusBySessionId = useMemo(
    () => new Map((overview?.classSessionCheckIns ?? []).map((c) => [c.classSessionId, c])),
    [overview]
  );
  const showDailyColumns = dateRange.length <= MAX_DAILY_COLUMNS;
  // Yêu cầu người dùng 2026-08-19 -- lọc đúng 1 ngày cụ thể thì xem rõ theo TỪNG GIỜ (bảng phẳng
  // sắp theo giờ bắt đầu, không gộp theo nhân viên) thay vì ô tổng hợp 1 cột như trước.
  const showHourlyTable = dateRange.length === 1;
  const hourlyRows = useMemo(
    () =>
      [...(overview?.sessions ?? [])].sort(
        (a, b) => a.startTime.localeCompare(b.startTime) || a.className.localeCompare(b.className)
      ),
    [overview]
  );

  function shiftsForEmployeeOnDate(employee: EmployeeResponse, date: Date): { shift: ShiftResponse; es: EmployeeShiftResponse }[] {
    const dateStr = toISODate(date);
    return (overview?.employeeShifts ?? [])
      .filter((es) => es.employeeId === employee.id)
      .filter((es) => es.effectiveFrom <= dateStr && (es.effectiveTo === null || es.effectiveTo >= dateStr))
      .map((es) => ({ shift: shiftById.get(es.shiftId), es }))
      .filter((x): x is { shift: ShiftResponse; es: EmployeeShiftResponse } => !!x.shift && matchesShiftPattern(x.shift, date));
  }

  function overrideForEmployeeOnDate(employee: EmployeeResponse, date: Date): WorkCalendarResponse | undefined {
    const dateStr = toISODate(date);
    return (overview?.workCalendarOverrides ?? []).find(
      (w) => w.calendarDate === dateStr && workCalendarAppliesToEmployee(w, employee, overview?.employeeShifts ?? [])
    );
  }

  function sessionsForEmployeeOnDate(employee: EmployeeResponse, date: Date): ClassSessionResponse[] {
    const dateStr = toISODate(date);
    return (overview?.sessions ?? []).filter((s) => s.primaryTeacherId === employee.userId && s.sessionDate === dateStr);
  }

  /** UC-71 — số buổi dạy của nhân viên trong khoảng ngày đã thực sự nhận lớp (ON_TIME/LATE). */
  function checkedInSessionCount(employee: EmployeeResponse): number {
    return dateRange.reduce((acc, d) => {
      const daySessions = sessionsForEmployeeOnDate(employee, d);
      const checkedIn = daySessions.filter((s) => {
        const status = checkInStatusBySessionId.get(s.id)?.effectiveStatus;
        return status === "ON_TIME" || status === "LATE";
      });
      return acc + checkedIn.length;
    }, 0);
  }

  const employees = overview?.employees ?? [];

  return (
    <div className="space-y-6">
      <div className="border-b border-slate-200 pb-4 flex items-start justify-between flex-wrap gap-3">
        <div>
          <h1 className="text-xl font-bold font-display tracking-tight text-slate-900">{t("employeeSchedulePage.title")}</h1>
          <p className="text-xs text-slate-500 mt-1">{t("employeeSchedulePage.description")}</p>
        </div>
      </div>

      {!canView ? (
        <div className="text-xs text-slate-500 bg-slate-50 border border-slate-200 p-4 rounded-lg">
          {t("employeeSchedulePage.noPermission")}
        </div>
      ) : (
        <div className="bg-white rounded-xl border border-slate-200 shadow-soft overflow-hidden">
          <div className="px-5 py-3 border-b border-slate-100 bg-slate-50 flex flex-wrap items-center gap-2">
            <div className="flex items-center gap-1 bg-white border border-slate-200 rounded-xl p-1 mr-2">
              {(
                [
                  ["day", t("employeeSchedulePage.quickRange.day")],
                  ["week", t("employeeSchedulePage.quickRange.week")],
                  ["month", t("employeeSchedulePage.quickRange.month")],
                  ["year", t("employeeSchedulePage.quickRange.year")]
                ] as const
              ).map(([key, label]) => {
                // "Theo lớp học" chỉ hợp lý trong khoảng ngắn (lưới quá rộng nếu Tháng/Năm) — bổ
                // sung ngoài SDD gốc, xác nhận với người dùng 2026-08-20.
                const disabledInClassGrid = viewMode === "classGrid" && (key === "month" || key === "year");
                return (
                  <button
                    key={key}
                    onClick={() => !disabledInClassGrid && applyQuickRange(key)}
                    disabled={disabledInClassGrid}
                    title={disabledInClassGrid ? t("employeeSchedulePage.quickRangeDisabledInClassGrid") : undefined}
                    className={cn(
                      "px-2.5 py-1.5 rounded-lg text-[11px] font-bold transition-colors",
                      disabledInClassGrid
                        ? "text-slate-300 cursor-not-allowed"
                        : quickRange === key
                        ? "bg-brand-gradient text-white shadow-xs"
                        : "text-slate-500 hover:text-slate-700"
                    )}
                  >
                    {label}
                  </button>
                );
              })}
            </div>
            <div className="w-36">
              <DatePicker value={from} onChange={setFrom} max={to || undefined} />
            </div>
            <span className="text-[10px] text-slate-400">{t("employeeSchedulePage.rangeSeparator")}</span>
            <div className="w-36">
              <DatePicker value={to} onChange={setTo} min={from || undefined} />
            </div>

            {/* Phòng ban không lọc được gì ở "Theo lớp học" (lưới chỉ theo điểm trường/lớp) — ẩn thay vì để vô tác dụng, xác nhận với người dùng 2026-08-20. */}
            {viewMode === "employee" && (
              <Select value={departmentId} onChange={(e) => setDepartmentId(e.target.value === "" ? "" : Number(e.target.value))} className="bg-white border border-slate-200 text-xs p-2 rounded-lg focus:outline-none max-w-[160px]">
                <option value="">{t("employeeSchedulePage.filters.allDepartments")}</option>
                {departments.map((d) => (
                  <option key={d.id} value={d.id}>{d.name}</option>
                ))}
              </Select>
            )}
            {/* Bỏ bộ lọc "Trường"/"Lớp" riêng ở đây (xác nhận với người dùng 2026-08-21) — dùng thẳng
                dropdown "Điểm trường"/"Lớp" đã có sẵn ở Header, đỡ chọn trùng 2 chỗ. */}
            {/* Nhân viên không lọc được gì ở "Theo lớp học" (lưới không lọc theo GV) — ẩn thay vì để vô tác dụng. */}
            {viewMode === "employee" && (
              <Select value={employeeId} onChange={(e) => setEmployeeId(e.target.value === "" ? "" : Number(e.target.value))} className="bg-white border border-slate-200 text-xs p-2 rounded-lg focus:outline-none max-w-[160px]">
                <option value="">{t("employeeSchedulePage.filters.allEmployees")}</option>
                {allEmployees.map((e) => (
                  <option key={e.id} value={e.id}>{e.fullName}</option>
                ))}
              </Select>
            )}

            {/* Chế độ xem — chuyển xuống cùng hàng bộ lọc, ngoài cùng bên phải (xác nhận với người dùng 2026-08-20). */}
            <div className="flex items-center gap-1 bg-white border border-slate-200 rounded-xl p-1 ml-auto">
              <button
                onClick={() => setViewMode("employee")}
                className={cn(
                  "flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-[11px] font-bold transition-colors",
                  viewMode === "employee" ? "bg-brand-gradient text-white shadow-xs" : "text-slate-500 hover:text-slate-700"
                )}
              >
                <Users className="w-3.5 h-3.5" />
                {t("employeeSchedulePage.viewMode.employee")}
              </button>
              <button
                onClick={() => {
                  setViewMode("classGrid");
                  // Tháng/Năm không dùng được ở chế độ này (lưới quá rộng) — tự thu hẹp về Tuần cho đỡ phải bấm lại.
                  if (quickRange === "month" || quickRange === "year") applyQuickRange("week");
                }}
                className={cn(
                  "flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-[11px] font-bold transition-colors",
                  viewMode === "classGrid" ? "bg-brand-gradient text-white shadow-xs" : "text-slate-500 hover:text-slate-700"
                )}
              >
                <CalendarClock className="w-3.5 h-3.5" />
                {t("employeeSchedulePage.viewMode.classGrid")}
              </button>
            </div>
          </div>

          {error && <div className="m-4 text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg">{error}</div>}

          {viewMode === "classGrid" ? (
            classGridSiteId == null ? (
              <p className="text-xs text-slate-400 italic text-center py-12">{t("employeeSchedulePage.classGrid.selectSiteHint")}</p>
            ) : dateRange.length > MAX_CLASS_GRID_DAYS ? (
              <p className="text-xs text-slate-400 italic text-center py-12">
                {t("employeeSchedulePage.classGrid.rangeTooWideHint")}
              </p>
            ) : (
              <ClassPeriodGrid siteId={classGridSiteId} dates={dateRange} classId={selectedClassId ?? undefined} />
            )
          ) : showHourlyTable ? (
            <DailyTimeline
              sessions={hourlyRows}
              checkInStatusBySessionId={checkInStatusBySessionId}
              siteNameByClassId={siteNameByClassId}
              loading={loading}
            />
          ) : !showDailyColumns ? (
            <TableContainer className="rounded-none border-0">
              <thead>
                <tr>
                  <Th>{t("employeeSchedulePage.summaryTable.columns.employee")}</Th>
                  <Th>{t("employeeSchedulePage.summaryTable.columns.type")}</Th>
                  <Th>{t("employeeSchedulePage.summaryTable.columns.shiftDays")}</Th>
                  <Th>{t("employeeSchedulePage.summaryTable.columns.sessions")}</Th>
                  <Th>{t("employeeSchedulePage.summaryTable.columns.checkedIn")}</Th>
                  <Th>{t("employeeSchedulePage.summaryTable.columns.overrides")}</Th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {loading ? (
                  <tr><Td colSpan={6} className="text-center text-slate-400">{t("employeeSchedulePage.summaryTable.loading")}</Td></tr>
                ) : employees.length === 0 ? (
                  <tr><Td colSpan={6} className="text-center text-slate-400">{t("employeeSchedulePage.summaryTable.empty")}</Td></tr>
                ) : (
                  employees.map((emp) => {
                    const shiftDayCount = dateRange.filter((d) => shiftsForEmployeeOnDate(emp, d).length > 0).length;
                    const sessionCount = dateRange.reduce((acc, d) => acc + sessionsForEmployeeOnDate(emp, d).length, 0);
                    const overrideCount = dateRange.filter((d) => overrideForEmployeeOnDate(emp, d)).length;
                    return (
                      <tr key={emp.id} className="hover:bg-slate-50/50 transition-colors">
                        <Td className="font-bold text-slate-800">
                          {emp.fullName}
                          <div className="text-[10px] text-slate-400 font-normal">{emp.employeeCode}</div>
                        </Td>
                        <Td>{employeeTypeLabel(t, emp.employeeType)}</Td>
                        <Td>{shiftDayCount}</Td>
                        <Td>{sessionCount}</Td>
                        <Td>{sessionCount > 0 ? `${checkedInSessionCount(emp)}/${sessionCount}` : "—"}</Td>
                        <Td>{overrideCount}</Td>
                      </tr>
                    );
                  })
                )}
              </tbody>
            </TableContainer>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-xs border-collapse">
                <thead>
                  <tr className="bg-slate-50 border-b border-slate-200">
                    <th className="sticky left-0 bg-slate-50 text-left px-3 py-2 font-bold text-slate-600 z-10 min-w-[180px]">{t("employeeSchedulePage.gridTable.employeeColumn")}</th>
                    {dateRange.map((d) => (
                      <th key={toISODate(d)} className="px-2 py-2 font-bold text-slate-500 text-center min-w-[84px]">
                        <div>{new Intl.DateTimeFormat(toLocaleTag(language), { weekday: "short" }).format(d)}</div>
                        <div className="font-mono text-[10px]">{d.getDate()}/{d.getMonth() + 1}</div>
                      </th>
                    ))}
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {loading ? (
                    <tr><td colSpan={dateRange.length + 1} className="text-center text-slate-400 py-6">{t("employeeSchedulePage.gridTable.loading")}</td></tr>
                  ) : employees.length === 0 ? (
                    <tr>
                      <td colSpan={dateRange.length + 1} className="text-center text-slate-400 py-6">
                        <div className="flex flex-col items-center gap-1.5">
                          <Search className="w-5 h-5 text-slate-300" />
                          {t("employeeSchedulePage.gridTable.empty")}
                        </div>
                      </td>
                    </tr>
                  ) : (
                    employees.map((emp) => (
                      <tr key={emp.id} className="hover:bg-slate-50/40 transition-colors">
                        <td className="sticky left-0 bg-white text-left px-3 py-2 font-bold text-slate-800 z-10 border-r border-slate-100">
                          {emp.fullName}
                          <div className="text-[10px] text-slate-400 font-normal">{emp.employeeCode} · {employeeTypeLabel(t, emp.employeeType)}</div>
                        </td>
                        {dateRange.map((d) => {
                          const shifts = shiftsForEmployeeOnDate(emp, d);
                          const override = overrideForEmployeeOnDate(emp, d);
                          const sessions = sessionsForEmployeeOnDate(emp, d);
                          const hasContent = shifts.length > 0 || !!override || sessions.length > 0;
                          return (
                            <td key={toISODate(d)} className="px-1.5 py-1.5 align-top">
                              <button
                                type="button"
                                disabled={!hasContent}
                                onClick={() => setDetail({ employee: emp, date: toISODate(d) })}
                                className={cn(
                                  "w-full min-h-[46px] rounded-lg p-1 flex flex-col items-center justify-center gap-0.5 text-center transition-colors",
                                  hasContent ? "hover:bg-orange-50 cursor-pointer" : "cursor-default"
                                )}
                              >
                                {override && <Badge variant={dayTypeVariants[override.dayType]}>{dayTypeLabel(t, override.dayType)}</Badge>}
                                {shifts.length > 0 && (
                                  <span className="text-[9px] text-slate-500 flex items-center gap-0.5">
                                    <Clock className="w-2.5 h-2.5" /> {shifts.length}
                                  </span>
                                )}
                                {sessions.length > 0 && (
                                  <span className="text-[9px] bg-brand-red/10 text-brand-red px-1 py-0.5 rounded-md font-bold">
                                    {t("employeeSchedulePage.gridTable.sessionsCount", { count: sessions.length })}
                                  </span>
                                )}
                                {sessions.length > 0 && (
                                  <span className="text-[9px] text-slate-500 font-semibold">
                                    {t("employeeSchedulePage.gridTable.checkedInCount", {
                                      checkedIn: sessions.filter((s) => {
                                        const st = checkInStatusBySessionId.get(s.id)?.effectiveStatus;
                                        return st === "ON_TIME" || st === "LATE";
                                      }).length,
                                      total: sessions.length
                                    })}
                                  </span>
                                )}
                              </button>
                            </td>
                          );
                        })}
                      </tr>
                    ))
                  )}
                </tbody>
              </table>
            </div>
          )}
        </div>
      )}

      <Modal
        open={!!detail}
        onClose={() => setDetail(null)}
        title={
          detail
            ? `${detail.employee.fullName} — ${new Intl.DateTimeFormat(toLocaleTag(language), {
                weekday: "long",
                day: "2-digit",
                month: "2-digit",
                year: "numeric"
              }).format(new Date(`${detail.date}T00:00:00`))}`
            : ""
        }
        size="lg"
      >
        {detail && (
          <ScheduleDayDetail
            employee={detail.employee}
            date={detail.date}
            shifts={shiftsForEmployeeOnDate(detail.employee, new Date(`${detail.date}T00:00:00`))}
            override={overrideForEmployeeOnDate(detail.employee, new Date(`${detail.date}T00:00:00`))}
            sessions={sessionsForEmployeeOnDate(detail.employee, new Date(`${detail.date}T00:00:00`))}
            checkInStatusBySessionId={checkInStatusBySessionId}
          />
        )}
      </Modal>
    </div>
  );
}

function ScheduleDayDetail({
  shifts,
  override,
  sessions,
  checkInStatusBySessionId
}: {
  employee: EmployeeResponse;
  date: string;
  shifts: { shift: ShiftResponse; es: EmployeeShiftResponse }[];
  override: WorkCalendarResponse | undefined;
  sessions: ClassSessionResponse[];
  checkInStatusBySessionId: Map<number, ClassSessionCheckInStatusResponse>;
}) {
  const { t, i18n } = useTranslation("hrm-shifts");
  const { t: tc } = useTranslation("common");
  const language = i18n.language;
  return (
    <div className="space-y-3">
      {override && (
        <div className="flex items-center gap-2">
          <span className="text-[10px] font-bold uppercase text-slate-500">{t("employeeSchedulePage.detail.overrideLabel")}</span>
          <Badge variant={dayTypeVariants[override.dayType]}>{dayTypeLabel(t, override.dayType)}</Badge>
          {override.description && <span className="text-[11px] text-slate-500">{override.description}</span>}
        </div>
      )}

      <div>
        <span className="text-[10px] font-bold uppercase text-slate-500 block mb-1.5">
          {t("employeeSchedulePage.detail.fixedShiftsTitle", { count: shifts.length })}
        </span>
        {shifts.length === 0 ? (
          <p className="text-xs text-slate-400 italic">{t("employeeSchedulePage.detail.noFixedShifts")}</p>
        ) : (
          <div className="space-y-1.5">
            {shifts.map(({ shift, es }) => (
              <div key={es.id} className="border border-slate-150 rounded-lg p-2 text-xs flex items-center gap-2">
                <Clock className="w-3.5 h-3.5 text-slate-400 shrink-0" />
                <span className="font-bold text-slate-700">{shift.name}</span>
                <span className="text-slate-400 font-mono ml-auto">{shift.checkInTime}–{shift.checkOutTime}</span>
              </div>
            ))}
          </div>
        )}
      </div>

      <div>
        <span className="text-[10px] font-bold uppercase text-slate-500 block mb-1.5">
          {t("employeeSchedulePage.detail.sessionsTitle", { count: sessions.length })}
        </span>
        {sessions.length === 0 ? (
          <p className="text-xs text-slate-400 italic">{t("employeeSchedulePage.detail.noSessions")}</p>
        ) : (
          <div className="space-y-1.5">
            {sessions.map((s) => {
              const checkInStatus = checkInStatusBySessionId.get(s.id);
              return (
                <div key={s.id} className="border border-slate-150 rounded-lg p-2 text-xs space-y-1">
                  <div className="flex items-center justify-between gap-2 flex-wrap">
                    <span className="font-mono text-slate-500">{s.startTime}–{s.endTime}</span>
                    <div className="flex items-center gap-1.5 flex-wrap justify-end">
                      <Badge variant={sessionStatusVariants[s.status] ?? "neutral"}>{s.status}</Badge>
                      {checkInStatus && s.status !== "CANCELLED" && s.status !== "RESCHEDULED" && (
                        <Badge variant={checkInStatusVariants[checkInStatus.effectiveStatus] ?? "neutral"}>
                          {checkInStatusLabel(tc, checkInStatus.effectiveStatus)}
                          {checkInStatus.checkInTime && ` (${formatTimeHm(checkInStatus.checkInTime, language)})`}
                        </Badge>
                      )}
                    </div>
                  </div>
                  <p className="font-bold text-slate-800">{s.className}</p>
                </div>
              );
            })}
          </div>
        )}
      </div>
    </div>
  );
}
