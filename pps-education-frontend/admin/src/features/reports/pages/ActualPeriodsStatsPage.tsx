import React, { useEffect, useMemo, useState } from "react";
import { BookOpenCheck, Filter } from "lucide-react";
import Select from "@/components/ui/Select";
import DatePicker from "@/components/ui/DatePicker";
import MonthPicker from "@/components/ui/MonthPicker";
import { useApp } from "@/context/AppContext";
import {
  ActualPeriodsGridResponse,
  ActualPeriodsStatsResponse,
  AcademicTermResponse,
  EnrollmentMovementPeriodType,
  getActualPeriodsGrid,
  getActualPeriodsStats,
  listAcademicTerms
} from "@/features/academic/api";
import { ApiError } from "@/lib/apiClient";
import { getWeekDates, toISODate } from "@/lib/calendarDates";
import { cn } from "@/lib/cn";

type PeriodType = "WEEK" | "MONTH" | "TERM" | "YEAR";

const PERIOD_TYPE_LABELS: Record<PeriodType, string> = {
  WEEK: "Tuần",
  MONTH: "Tháng",
  TERM: "Học kỳ",
  YEAR: "Năm"
};

interface ResolvedPeriod {
  fromDate: string;
  toDate: string;
  label: string;
}

function currentYearMonth(): string {
  const now = new Date();
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, "0")}`;
}

function toISODateYmd(y: number, m: number, d: number): string {
  return `${String(y).padStart(4, "0")}-${String(m).padStart(2, "0")}-${String(d).padStart(2, "0")}`;
}

/**
 * Báo cáo "Số tiết thực tế theo lớp" (bổ sung ngoài SDD gốc, xác nhận với người dùng 2026-08-20) —
 * số tiết ĐÃ DẠY thực tế (không tính buổi huỷ/đã dời) của từng lớp trong 1 điểm trường, lọc theo
 * tuần/tháng/học kỳ/năm. Mặc định hiển thị dạng LƯỚI (hàng đầu = tháng/kỳ/năm, cột đầu = lớp — xác
 * nhận với người dùng 2026-08-20, thay cho dạng danh sách 1 khoảng/lần), "Chi tiết" (1 khoảng cụ
 * thể, dùng được cả cho "Tuần") vẫn giữ làm chế độ phụ.
 */
export default function ActualPeriodsStatsPage() {
  const { selectedCampusId, selectedClassId, hasPermission } = useApp();
  const canView = hasPermission("report.actual-periods.view");
  const hasSite = selectedCampusId !== "ALL";
  const siteId = hasSite ? Number(selectedCampusId) : null;

  const [periodType, setPeriodType] = useState<PeriodType>("MONTH");
  const [displayMode, setDisplayMode] = useState<"detail" | "grid">("grid");

  const [weekAnchor, setWeekAnchor] = useState<string>(toISODate(new Date()));
  const [selectedMonth, setSelectedMonth] = useState<string>(currentYearMonth());
  const [terms, setTerms] = useState<AcademicTermResponse[]>([]);
  const [selectedTermId, setSelectedTermId] = useState<number | "">("");
  const [loadingTerms, setLoadingTerms] = useState(false);
  const [selectedYear, setSelectedYear] = useState<number>(new Date().getFullYear());
  const [gridYear, setGridYear] = useState<number>(new Date().getFullYear());

  const [stats, setStats] = useState<ActualPeriodsStatsResponse | null>(null);
  const [loadingStats, setLoadingStats] = useState(false);
  const [grid, setGrid] = useState<ActualPeriodsGridResponse | null>(null);
  const [loadingGrid, setLoadingGrid] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // "Tuần" không có dạng lưới hợp lý -- chọn "Tuần" thì tự chuyển về Chi tiết.
  useEffect(() => {
    if (periodType === "WEEK") setDisplayMode("detail");
  }, [periodType]);

  useEffect(() => {
    if (!hasSite) {
      setTerms([]);
      setSelectedTermId("");
      return;
    }
    setLoadingTerms(true);
    setSelectedTermId("");
    listAcademicTerms(Number(selectedCampusId))
      .then((list) => {
        setTerms(list);
        if (list.length > 0) setSelectedTermId(list[0].id);
      })
      .finally(() => setLoadingTerms(false));
  }, [hasSite, selectedCampusId]);

  const yearOptions = useMemo(() => {
    const current = new Date().getFullYear();
    return Array.from({ length: 6 }, (_, i) => current - i);
  }, []);

  const currentPeriod: ResolvedPeriod | null = useMemo(() => {
    if (periodType === "WEEK") {
      if (!weekAnchor) return null;
      const week = getWeekDates(new Date(`${weekAnchor}T00:00:00`));
      const from = week[0];
      const to = week[6];
      const label = `Tuần ${from.toLocaleDateString("vi-VN", { day: "2-digit", month: "2-digit" })} – ${to.toLocaleDateString("vi-VN", { day: "2-digit", month: "2-digit", year: "numeric" })}`;
      return { fromDate: toISODate(from), toDate: toISODate(to), label };
    }
    if (periodType === "MONTH") {
      if (!selectedMonth) return null;
      const [y, m] = selectedMonth.split("-").map(Number);
      const lastDay = new Date(y, m, 0).getDate();
      return { fromDate: toISODateYmd(y, m, 1), toDate: toISODateYmd(y, m, lastDay), label: `Tháng ${m}/${y}` };
    }
    if (periodType === "TERM") {
      const term = terms.find((t) => t.id === selectedTermId);
      if (!term) return null;
      return { fromDate: term.startDate, toDate: term.endDate, label: term.name };
    }
    if (!selectedYear) return null;
    return { fromDate: toISODateYmd(selectedYear, 1, 1), toDate: toISODateYmd(selectedYear, 12, 31), label: `Năm ${selectedYear}` };
  }, [periodType, weekAnchor, selectedMonth, terms, selectedTermId, selectedYear]);

  useEffect(() => {
    if (displayMode !== "detail" || !currentPeriod || !siteId) { setStats(null); return; }
    setLoadingStats(true);
    setError(null);
    getActualPeriodsStats({
      siteId,
      fromDate: currentPeriod.fromDate,
      toDate: currentPeriod.toDate,
      periodType,
      periodLabel: currentPeriod.label,
      classId: selectedClassId ?? undefined
    })
      .then(setStats)
      .catch((err) => {
        setStats(null);
        setError(err instanceof ApiError ? err.message : "Không tải được thống kê số tiết thực tế.");
      })
      .finally(() => setLoadingStats(false));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [displayMode, currentPeriod, siteId, selectedClassId]);

  useEffect(() => {
    if (displayMode !== "grid" || periodType === "WEEK" || !siteId) return;
    setLoadingGrid(true);
    setError(null);
    getActualPeriodsGrid({
      siteId,
      periodType: periodType as EnrollmentMovementPeriodType,
      year: periodType === "MONTH" ? gridYear : undefined,
      classId: selectedClassId ?? undefined
    })
      .then(setGrid)
      .catch((err) => {
        setGrid(null);
        setError(err instanceof ApiError ? err.message : "Không tải được lưới số tiết thực tế.");
      })
      .finally(() => setLoadingGrid(false));
  }, [displayMode, periodType, siteId, gridYear, selectedClassId]);

  return (
    <div className="space-y-6">
      <div className="border-b border-slate-200 pb-4">
        <h1 className="text-xl font-bold font-display tracking-tight text-slate-900">Số tiết thực tế theo lớp</h1>
        <p className="text-xs text-slate-500 mt-1">
          Số tiết đã dạy thực tế (không tính buổi đã huỷ/đã dời) của từng lớp — xem theo tuần, tháng, học kỳ hoặc năm.
        </p>
      </div>

      <div className="flex items-center justify-between flex-wrap gap-3">
        <div className="flex items-center gap-1 bg-white border border-slate-200 rounded-xl p-1 w-fit">
          {(Object.keys(PERIOD_TYPE_LABELS) as PeriodType[]).map((pt) => (
            <button
              key={pt}
              onClick={() => setPeriodType(pt)}
              className={cn(
                "px-3.5 py-1.5 rounded-lg text-xs font-bold transition-colors",
                periodType === pt ? "bg-brand-gradient text-white shadow-xs" : "text-slate-500 hover:text-slate-700"
              )}
            >
              {PERIOD_TYPE_LABELS[pt]}
            </button>
          ))}
        </div>
        <div className="flex items-center gap-1 bg-white border border-slate-200 rounded-xl p-1 w-fit">
          <button
            onClick={() => setDisplayMode("detail")}
            className={cn(
              "px-3.5 py-1.5 rounded-lg text-xs font-bold transition-colors",
              displayMode === "detail" ? "bg-brand-gradient text-white shadow-xs" : "text-slate-500 hover:text-slate-700"
            )}
          >
            Chi tiết
          </button>
          <button
            onClick={() => setDisplayMode("grid")}
            disabled={periodType === "WEEK"}
            title={periodType === "WEEK" ? "Lưới tổng quan không áp dụng cho Tuần" : "Lưới tổng quan — hàng đầu là tháng/kỳ/năm, cột đầu là lớp"}
            className={cn(
              "px-3.5 py-1.5 rounded-lg text-xs font-bold transition-colors disabled:opacity-40 disabled:cursor-not-allowed",
              displayMode === "grid" ? "bg-brand-gradient text-white shadow-xs" : "text-slate-500 hover:text-slate-700"
            )}
          >
            Lưới tổng quan
          </button>
        </div>
      </div>

      {!canView ? (
        <div className="text-xs text-slate-500 bg-slate-50 border border-slate-200 p-4 rounded-lg">
          Bạn không có quyền xem trang này.
        </div>
      ) : !hasSite ? (
        <div className="bg-amber-50 border border-amber-200/80 rounded-xl p-4 text-amber-800 text-sm flex items-center gap-3">
          <Filter className="w-5 h-5 text-amber-600 shrink-0" />
          <span>Vui lòng chọn 1 điểm trường cụ thể trên thanh Header (góc trên bên trái).</span>
        </div>
      ) : displayMode === "grid" ? (
        <>
          <div className="bg-white rounded-xl border border-slate-200/60 shadow-sm p-4">
            <div className="flex items-center gap-2 text-xs font-semibold text-slate-600 mb-3">
              <Filter className="w-3.5 h-3.5" /> Lưới tổng quan — hàng đầu là {PERIOD_TYPE_LABELS[periodType].toLowerCase()}, cột đầu là lớp
            </div>
            {periodType === "MONTH" ? (
              <div className="max-w-[220px]">
                <label className="block text-xs text-slate-500 mb-1">Năm (hiển thị đủ 12 tháng)</label>
                <Select
                  value={gridYear}
                  onChange={(e) => setGridYear(Number(e.target.value))}
                  className="w-full border border-slate-300 rounded-lg text-sm p-2 focus:outline-none focus:ring-2 focus:ring-brand-orange/40"
                >
                  {yearOptions.map((y) => (
                    <option key={y} value={y}>{y}</option>
                  ))}
                </Select>
              </div>
            ) : periodType === "YEAR" ? (
              <p className="text-xs text-slate-400">Hiển thị 6 năm gần nhất ({new Date().getFullYear() - 5} — {new Date().getFullYear()}).</p>
            ) : (
              <p className="text-xs text-slate-400">Hiển thị toàn bộ kỳ học của điểm trường này, sắp theo thời gian.</p>
            )}
          </div>

          {error && <div className="bg-rose-50 border border-rose-200/80 rounded-xl p-4 text-rose-700 text-sm">{error}</div>}

          {loadingGrid ? (
            <div className="text-center py-16 text-slate-400">
              <BookOpenCheck className="w-14 h-14 mx-auto text-slate-200 mb-3 animate-pulse" />
              <p className="text-sm font-medium">Đang tải lưới thống kê...</p>
            </div>
          ) : grid ? (
            <div className="bg-white rounded-xl border border-slate-200/60 shadow-sm overflow-hidden">
              <div className="px-4 py-3 border-b border-slate-100 flex items-center justify-between gap-3 flex-wrap">
                <h3 className="text-sm font-semibold text-slate-700">{grid.siteName}</h3>
              </div>
              {grid.rows.length === 0 ? (
                <div className="px-4 py-10 text-center">
                  <BookOpenCheck className="w-10 h-10 mx-auto text-slate-200 mb-2" />
                  <p className="text-sm text-slate-400">Điểm trường này chưa có lớp nào.</p>
                </div>
              ) : (
                <div className="overflow-x-auto">
                  <table className="w-full text-sm text-left table-fixed">
                    <colgroup>
                      <col style={{ width: 200 }} />
                      {grid.columns.map((c) => (
                        <col key={c.key} />
                      ))}
                    </colgroup>
                    <thead className="bg-slate-50 border-b border-slate-100 text-xs text-slate-500 font-medium">
                      <tr>
                        <th className="sticky left-0 bg-slate-50 px-4 py-2.5 z-10">Lớp</th>
                        {grid.columns.map((c) => (
                          <th key={c.key} className="px-3 py-2.5 text-right">{c.label}</th>
                        ))}
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-slate-100">
                      {grid.rows.map((row) => (
                        <tr key={row.classId} className="hover:bg-slate-50/50 transition-colors">
                          <td
                            className="sticky left-0 bg-white px-4 py-2.5 font-medium text-slate-800 border-r border-slate-100 truncate"
                            title={`${row.className} (${row.classCode})`}
                          >
                            {row.className}
                            <span className="text-xs text-slate-400 ml-1.5">{row.classCode}</span>
                          </td>
                          {grid.columns.map((c) => (
                            <td key={c.key} className="px-3 py-2.5 text-right text-slate-700">{row.actualPeriodsByColumnKey[c.key] ?? 0}</td>
                          ))}
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </div>
          ) : null}
        </>
      ) : (
        <>
          <div className="bg-white rounded-xl border border-slate-200/60 shadow-sm p-4">
            <div className="flex items-center gap-2 text-xs font-semibold text-slate-600 mb-3">
              <Filter className="w-3.5 h-3.5" /> Chọn {PERIOD_TYPE_LABELS[periodType].toLowerCase()}
            </div>
            <div className="max-w-[260px]">
              {periodType === "WEEK" && (
                <>
                  <label className="block text-xs text-slate-500 mb-1">Chọn 1 ngày trong tuần</label>
                  <DatePicker value={weekAnchor} onChange={setWeekAnchor} />
                </>
              )}
              {periodType === "MONTH" && (
                <>
                  <label className="block text-xs text-slate-500 mb-1">Tháng</label>
                  <MonthPicker value={selectedMonth} onChange={setSelectedMonth} />
                </>
              )}
              {periodType === "TERM" && (
                <>
                  <label className="block text-xs text-slate-500 mb-1">Kỳ học</label>
                  <Select
                    value={selectedTermId}
                    onChange={(e) => setSelectedTermId(e.target.value ? Number(e.target.value) : "")}
                    disabled={loadingTerms}
                    className="w-full border border-slate-300 rounded-lg text-sm p-2 focus:outline-none focus:ring-2 focus:ring-brand-orange/40"
                  >
                    <option value="">-- Chọn kỳ học --</option>
                    {terms.map((t) => (
                      <option key={t.id} value={t.id}>
                        {t.name} ({t.startDate} - {t.endDate})
                      </option>
                    ))}
                  </Select>
                  {!loadingTerms && terms.length === 0 && (
                    <p className="text-xs text-slate-400 mt-2">Điểm trường này chưa có kỳ học nào — tạo ở màn "Quản lý Kỳ học".</p>
                  )}
                </>
              )}
              {periodType === "YEAR" && (
                <>
                  <label className="block text-xs text-slate-500 mb-1">Năm</label>
                  <Select
                    value={selectedYear}
                    onChange={(e) => setSelectedYear(Number(e.target.value))}
                    className="w-full border border-slate-300 rounded-lg text-sm p-2 focus:outline-none focus:ring-2 focus:ring-brand-orange/40"
                  >
                    {yearOptions.map((y) => (
                      <option key={y} value={y}>{y}</option>
                    ))}
                  </Select>
                </>
              )}
            </div>
          </div>

          {error && <div className="bg-rose-50 border border-rose-200/80 rounded-xl p-4 text-rose-700 text-sm">{error}</div>}

          {loadingStats ? (
            <div className="text-center py-16 text-slate-400">
              <BookOpenCheck className="w-14 h-14 mx-auto text-slate-200 mb-3 animate-pulse" />
              <p className="text-sm font-medium">Đang tải thống kê...</p>
            </div>
          ) : stats ? (
            <>
              <div className="bg-gradient-to-r from-indigo-500 to-purple-600 rounded-xl p-5 text-white flex flex-wrap items-start justify-between gap-4">
                <div>
                  <p className="text-xs opacity-75">{stats.siteName}</p>
                  <h2 className="text-lg font-bold mt-1">{stats.periodLabel}</h2>
                  <p className="text-xs opacity-70 mt-1">{stats.startDate} — {stats.endDate}</p>
                </div>
                <div className="text-right">
                  <p className="text-3xl font-bold">{stats.totalActualPeriods}</p>
                  <p className="text-xs opacity-75">Tổng số tiết thực tế</p>
                </div>
              </div>

              <div className="bg-white rounded-xl border border-slate-200/60 shadow-sm overflow-hidden">
                <div className="px-4 py-3 border-b border-slate-100 flex items-center justify-between gap-3 flex-wrap">
                  <h3 className="text-sm font-semibold text-slate-700">Số tiết thực tế theo lớp</h3>
                  <span className="text-xs text-slate-400">{stats.classes.length} lớp</span>
                </div>
                {stats.classes.length === 0 ? (
                  <div className="px-4 py-10 text-center">
                    <BookOpenCheck className="w-10 h-10 mx-auto text-slate-200 mb-2" />
                    <p className="text-sm text-slate-400">Điểm trường này chưa có lớp nào.</p>
                  </div>
                ) : (
                  <table className="w-full text-sm text-left">
                    <thead className="bg-slate-50 border-b border-slate-100 text-xs text-slate-500 font-medium">
                      <tr>
                        <th className="px-4 py-2.5">Lớp</th>
                        <th className="px-4 py-2.5 text-right">Số tiết thực tế</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-slate-100">
                      {stats.classes.map((row) => (
                        <tr key={row.classId} className="hover:bg-slate-50/50 transition-colors">
                          <td className="px-4 py-3">
                            <span className="font-medium text-slate-800">{row.className}</span>
                            <span className="text-xs text-slate-400 ml-2">{row.classCode}</span>
                          </td>
                          <td className="px-4 py-3 text-right font-semibold text-slate-800">{row.actualPeriods}</td>
                        </tr>
                      ))}
                    </tbody>
                    <tfoot className="bg-slate-50 border-t-2 border-slate-200 font-bold text-slate-800">
                      <tr>
                        <td className="px-4 py-3">Tổng cộng</td>
                        <td className="px-4 py-3 text-right">{stats.totalActualPeriods}</td>
                      </tr>
                    </tfoot>
                  </table>
                )}
              </div>
            </>
          ) : (
            <div className="text-center py-16 text-slate-400">
              <BookOpenCheck className="w-14 h-14 mx-auto text-slate-200 mb-3" />
              <p className="text-sm font-medium">Chọn {PERIOD_TYPE_LABELS[periodType].toLowerCase()} để xem thống kê.</p>
            </div>
          )}
        </>
      )}
    </div>
  );
}
