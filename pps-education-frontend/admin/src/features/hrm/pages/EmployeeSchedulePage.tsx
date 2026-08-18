import { useEffect, useMemo, useState } from "react";
import { Clock, Search } from "lucide-react";
import { ApiError } from "@/lib/apiClient";
import { cn } from "@/lib/cn";
import { getMonthGridDates, getWeekDates, toISODate } from "@/lib/calendarDates";
import { matchesShiftPattern } from "@/lib/shiftPattern";
import { useApp } from "@/context/AppContext";
import { Badge, Modal, TableContainer, Th, Td } from "@/components/ui";
import Select from "@/components/ui/Select";
import { checkInStatusLabels, checkInStatusVariants, sessionStatusVariants } from "@/features/academic/components/ClassDetailPanel";
import { listSites, SiteResponse } from "@/features/facility/api";
import { listClasses, ClassResponse, ClassSessionCheckInStatusResponse, ClassSessionResponse } from "@/features/academic/api";
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

const dayTypeLabels: Record<WorkCalendarResponse["dayType"], string> = {
  WORKING: "Đi làm",
  OFF: "Nghỉ",
  HOLIDAY: "Nghỉ lễ",
  COMPENSATORY: "Đi bù"
};

const dayTypeVariants: Record<WorkCalendarResponse["dayType"], "success" | "warning" | "danger" | "info"> = {
  WORKING: "success",
  OFF: "warning",
  HOLIDAY: "danger",
  COMPENSATORY: "info"
};

const employeeTypeLabels: Record<EmployeeResponse["employeeType"], string> = {
  TEACHER: "Giáo viên",
  STAFF: "Nhân viên",
  MANAGER: "Quản lý"
};

/** Quá dài để hiện từng cột ngày (VD chọn "Năm") — chuyển sang bảng tổng hợp số liệu. */
const MAX_DAILY_COLUMNS = 31;

type QuickRange = "day" | "week" | "month" | "year";

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

/**
 * Bổ sung ngoài SDD gốc, xác nhận 2026-08-17: trang "Lịch làm việc" (roster
 * toàn công ty) cho HR/Điều hành — thay thế tab đơn-nhân-viên cũ, gộp ca
 * làm cố định + lịch dạy + lịch nghỉ lễ của TOÀN BỘ nhân viên, lọc theo
 * phòng ban/điểm trường/lớp/nhân viên. Gate bằng quyền
 * hrm.employee-schedule.view. Xem docs/uc/phan-he-04-nhan-su.md (UC-70).
 */
export default function EmployeeSchedulePage() {
  const { hasPermission } = useApp();
  const canView = hasPermission("hrm.employee-schedule.view");

  const today = toISODate(new Date());
  const [quickRange, setQuickRange] = useState<QuickRange>("week");
  const [from, setFrom] = useState(() => toISODate(getWeekDates(new Date())[0]));
  const [to, setTo] = useState(() => toISODate(getWeekDates(new Date())[6]));
  const [departmentId, setDepartmentId] = useState<number | "">("");
  const [siteId, setSiteId] = useState<number | "">("");
  const [classId, setClassId] = useState<number | "">("");
  const [employeeId, setEmployeeId] = useState<number | "">("");

  const [departments, setDepartments] = useState<DepartmentResponse[]>([]);
  const [sites, setSites] = useState<SiteResponse[]>([]);
  const [classes, setClasses] = useState<ClassResponse[]>([]);
  const [allEmployees, setAllEmployees] = useState<EmployeeResponse[]>([]);

  const [overview, setOverview] = useState<EmployeeScheduleOverviewResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [detail, setDetail] = useState<{ employee: EmployeeResponse; date: string } | null>(null);

  useEffect(() => {
    listDepartments().then(setDepartments).catch(() => setDepartments([]));
    listSites().then(setSites).catch(() => setSites([]));
  }, []);

  // classId phụ thuộc site đã chọn -- reset khi đổi site để tránh giữ lại lớp thuộc site cũ.
  useEffect(() => {
    if (siteId === "") {
      setClasses([]);
      setClassId("");
      return;
    }
    listClasses({ siteId }).then(setClasses).catch(() => setClasses([]));
    setClassId("");
  }, [siteId]);

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
      siteId: siteId === "" ? undefined : siteId,
      classId: classId === "" ? undefined : classId,
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
        setError(err instanceof ApiError ? err.message : "Không tải được lịch làm việc.");
      })
      .finally(() => setLoading(false));
  };

  useEffect(load, [from, to, departmentId, siteId, classId, employeeId, canView]);

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
  const dateRange = useMemo(() => datesBetween(from, to), [from, to]);
  const showDailyColumns = dateRange.length <= MAX_DAILY_COLUMNS;

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
      <div className="border-b border-slate-200 pb-4">
        <h1 className="text-xl font-bold font-display tracking-tight text-slate-900">Lịch làm việc</h1>
        <p className="text-xs text-slate-500 mt-1">Ca làm cố định và lịch dạy của toàn bộ nhân viên, lọc theo phòng ban/điểm trường/lớp/nhân viên.</p>
      </div>

      {!canView ? (
        <div className="text-xs text-slate-500 bg-slate-50 border border-slate-200 p-4 rounded-lg">
          Bạn không có quyền xem trang này.
        </div>
      ) : (
        <div className="bg-white rounded-xl border border-slate-200 shadow-soft overflow-hidden">
          <div className="px-5 py-3 border-b border-slate-100 bg-slate-50 flex flex-wrap items-center gap-2">
            <div className="flex items-center gap-1 bg-white border border-slate-200 rounded-xl p-1 mr-2">
              {(
                [
                  ["day", "Ngày"],
                  ["week", "Tuần"],
                  ["month", "Tháng"],
                  ["year", "Năm"]
                ] as const
              ).map(([key, label]) => (
                <button
                  key={key}
                  onClick={() => applyQuickRange(key)}
                  className={cn(
                    "px-2.5 py-1.5 rounded-lg text-[11px] font-bold transition-colors",
                    quickRange === key ? "bg-brand-gradient text-white shadow-xs" : "text-slate-500 hover:text-slate-700"
                  )}
                >
                  {label}
                </button>
              ))}
            </div>
            <input type="date" value={from} onChange={(e) => setFrom(e.target.value)} className="bg-white border border-slate-200 text-xs p-2 rounded-lg focus:outline-none" />
            <span className="text-[10px] text-slate-400">đến</span>
            <input type="date" value={to} onChange={(e) => setTo(e.target.value)} className="bg-white border border-slate-200 text-xs p-2 rounded-lg focus:outline-none" />

            <Select value={departmentId} onChange={(e) => setDepartmentId(e.target.value === "" ? "" : Number(e.target.value))} className="bg-white border border-slate-200 text-xs p-2 rounded-lg focus:outline-none max-w-[160px]">
              <option value="">Tất cả phòng ban</option>
              {departments.map((d) => (
                <option key={d.id} value={d.id}>{d.name}</option>
              ))}
            </Select>
            <Select value={siteId} onChange={(e) => setSiteId(e.target.value === "" ? "" : Number(e.target.value))} className="bg-white border border-slate-200 text-xs p-2 rounded-lg focus:outline-none max-w-[160px]">
              <option value="">Tất cả điểm trường</option>
              {sites.map((s) => (
                <option key={s.id} value={s.id}>{s.name}</option>
              ))}
            </Select>
            <Select value={classId} onChange={(e) => setClassId(e.target.value === "" ? "" : Number(e.target.value))} disabled={siteId === ""} className="bg-white border border-slate-200 text-xs p-2 rounded-lg focus:outline-none max-w-[160px] disabled:opacity-50">
              <option value="">Tất cả lớp</option>
              {classes.map((c) => (
                <option key={c.id} value={c.id}>{c.name}</option>
              ))}
            </Select>
            <Select value={employeeId} onChange={(e) => setEmployeeId(e.target.value === "" ? "" : Number(e.target.value))} className="bg-white border border-slate-200 text-xs p-2 rounded-lg focus:outline-none max-w-[160px]">
              <option value="">Tất cả nhân viên</option>
              {allEmployees.map((e) => (
                <option key={e.id} value={e.id}>{e.fullName}</option>
              ))}
            </Select>
          </div>

          {error && <div className="m-4 text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg">{error}</div>}

          {!showDailyColumns ? (
            <TableContainer className="rounded-none border-0">
              <thead>
                <tr>
                  <Th>Nhân viên</Th>
                  <Th>Loại</Th>
                  <Th>Số ca làm khớp lịch</Th>
                  <Th>Số buổi dạy</Th>
                  <Th>Số buổi đã nhận lớp</Th>
                  <Th>Số ngày nghỉ lễ/ghi đè</Th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {loading ? (
                  <tr><Td colSpan={6} className="text-center text-slate-400">Đang tải...</Td></tr>
                ) : employees.length === 0 ? (
                  <tr><Td colSpan={6} className="text-center text-slate-400">Không có nhân viên nào khớp bộ lọc.</Td></tr>
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
                        <Td>{employeeTypeLabels[emp.employeeType]}</Td>
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
                    <th className="sticky left-0 bg-slate-50 text-left px-3 py-2 font-bold text-slate-600 z-10 min-w-[180px]">Nhân viên</th>
                    {dateRange.map((d) => (
                      <th key={toISODate(d)} className="px-2 py-2 font-bold text-slate-500 text-center min-w-[84px]">
                        <div>{d.toLocaleDateString("vi-VN", { weekday: "short" })}</div>
                        <div className="font-mono text-[10px]">{d.getDate()}/{d.getMonth() + 1}</div>
                      </th>
                    ))}
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {loading ? (
                    <tr><td colSpan={dateRange.length + 1} className="text-center text-slate-400 py-6">Đang tải...</td></tr>
                  ) : employees.length === 0 ? (
                    <tr>
                      <td colSpan={dateRange.length + 1} className="text-center text-slate-400 py-6">
                        <div className="flex flex-col items-center gap-1.5">
                          <Search className="w-5 h-5 text-slate-300" />
                          Không có nhân viên nào khớp bộ lọc.
                        </div>
                      </td>
                    </tr>
                  ) : (
                    employees.map((emp) => (
                      <tr key={emp.id} className="hover:bg-slate-50/40 transition-colors">
                        <td className="sticky left-0 bg-white text-left px-3 py-2 font-bold text-slate-800 z-10 border-r border-slate-100">
                          {emp.fullName}
                          <div className="text-[10px] text-slate-400 font-normal">{emp.employeeCode} · {employeeTypeLabels[emp.employeeType]}</div>
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
                                {override && <Badge variant={dayTypeVariants[override.dayType]}>{dayTypeLabels[override.dayType]}</Badge>}
                                {shifts.length > 0 && (
                                  <span className="text-[9px] text-slate-500 flex items-center gap-0.5">
                                    <Clock className="w-2.5 h-2.5" /> {shifts.length}
                                  </span>
                                )}
                                {sessions.length > 0 && (
                                  <span className="text-[9px] bg-brand-red/10 text-brand-red px-1 py-0.5 rounded-md font-bold">
                                    {sessions.length} buổi dạy
                                  </span>
                                )}
                                {sessions.length > 0 && (
                                  <span className="text-[9px] text-slate-500 font-semibold">
                                    {sessions.filter((s) => {
                                      const st = checkInStatusBySessionId.get(s.id)?.effectiveStatus;
                                      return st === "ON_TIME" || st === "LATE";
                                    }).length}/{sessions.length} đã nhận lớp
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
        title={detail ? `${detail.employee.fullName} — ${new Date(`${detail.date}T00:00:00`).toLocaleDateString("vi-VN", { weekday: "long", day: "2-digit", month: "2-digit", year: "numeric" })}` : ""}
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
  return (
    <div className="space-y-3">
      {override && (
        <div className="flex items-center gap-2">
          <span className="text-[10px] font-bold uppercase text-slate-500">Lịch nghỉ lễ/ghi đè:</span>
          <Badge variant={dayTypeVariants[override.dayType]}>{dayTypeLabels[override.dayType]}</Badge>
          {override.description && <span className="text-[11px] text-slate-500">{override.description}</span>}
        </div>
      )}

      <div>
        <span className="text-[10px] font-bold uppercase text-slate-500 block mb-1.5">Ca làm cố định ({shifts.length})</span>
        {shifts.length === 0 ? (
          <p className="text-xs text-slate-400 italic">Không có ca làm cố định khớp ngày này.</p>
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
        <span className="text-[10px] font-bold uppercase text-slate-500 block mb-1.5">Buổi dạy ({sessions.length})</span>
        {sessions.length === 0 ? (
          <p className="text-xs text-slate-400 italic">Không có buổi dạy trong ngày này.</p>
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
                          {checkInStatusLabels[checkInStatus.effectiveStatus] ?? checkInStatus.effectiveStatus}
                          {checkInStatus.checkInTime &&
                            ` (${new Date(checkInStatus.checkInTime).toLocaleTimeString("vi-VN", { hour: "2-digit", minute: "2-digit" })})`}
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
