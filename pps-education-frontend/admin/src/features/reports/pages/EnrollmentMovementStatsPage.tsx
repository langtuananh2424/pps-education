import React, { useEffect, useMemo, useState } from "react";
import { ArrowLeftRight, Download, Filter, LogOut, TrendingDown, TrendingUp, UserPlus, Users } from "lucide-react";
import Button from "@/components/ui/Button";
import Select from "@/components/ui/Select";
import MonthPicker from "@/components/ui/MonthPicker";
import { useApp } from "@/context/AppContext";
import {
  AcademicTermResponse,
  EnrollmentMovementGridResponse,
  EnrollmentMovementPeriodType,
  EnrollmentMovementStatsResponse,
  EnrollmentMovementTrendResponse,
  exportEnrollmentMovementStats,
  exportEnrollmentMovementStatsForRange,
  getEnrollmentMovementGrid,
  getEnrollmentMovementStats,
  getEnrollmentMovementStatsForRange,
  getEnrollmentMovementTrend,
  getEnrollmentMovementTrendForRange,
  listAcademicTerms,
} from "@/features/academic/api";
import { useToast } from "@/lib/useToast";
import Toast from "@/components/ui/Toast";
import { ApiError } from "@/lib/apiClient";
import { downloadBlob } from "@/lib/xlsxTemplate";
import { cn } from "@/lib/cn";

function StatCard({ icon, label, value, sub, color = "text-brand-orange" }: {
  icon: React.ReactNode; label: string; value: string | number; sub?: string; color?: string;
}) {
  return (
    <div className="bg-white border border-slate-200/60 rounded-xl p-4 flex items-center gap-4 shadow-sm">
      <div className="p-2.5 bg-orange-50 rounded-lg">{icon}</div>
      <div>
        <p className={`text-2xl font-bold ${color}`}>{value}</p>
        <p className="text-xs text-slate-500">{label}</p>
        {sub && <p className="text-xs text-slate-400">{sub}</p>}
      </div>
    </div>
  );
}

interface TrendSeries {
  label: string;
  color: string; // Tailwind stroke/fill class gốc, VD "indigo"
  points: { monthIndex: number; headcount: number; periodStart: string; periodEnd: string }[];
}

const SERIES_COLORS: Record<string, { stroke: string; fill: string; dot: string }> = {
  indigo: { stroke: "#6366f1", fill: "rgba(99,102,241,0.12)", dot: "bg-indigo-500" },
  amber: { stroke: "#f59e0b", fill: "rgba(245,158,11,0.12)", dot: "bg-amber-500" },
};

/**
 * Biểu đồ đường: xu hướng sĩ số theo THÁNG THỨ MẤY CỦA KỲ (monthIndex, không phải tháng lịch
 * tuyệt đối) — cho phép chồng 2 kỳ có độ dài/thời điểm lịch khác nhau lên cùng 1 trục X để so
 * sánh công bằng. 1 chuỗi thì không cần chú giải (tên đã ở tiêu đề); ≥2 chuỗi luôn kèm chú giải
 * màu (không chỉ dựa vào màu để phân biệt).
 */
function HeadcountTrendChart({ series }: { series: TrendSeries[] }) {
  const width = 640;
  const height = 220;
  const padding = { top: 16, right: 16, bottom: 28, left: 36 };
  const innerW = width - padding.left - padding.right;
  const innerH = height - padding.top - padding.bottom;

  const maxMonth = Math.max(...series.flatMap((s) => s.points.map((p) => p.monthIndex)), 1);
  const maxHeadcount = Math.max(...series.flatMap((s) => s.points.map((p) => p.headcount)), 1);

  const x = (monthIndex: number) => padding.left + (maxMonth <= 1 ? 0 : ((monthIndex - 1) / (maxMonth - 1)) * innerW);
  const y = (headcount: number) => padding.top + innerH - (headcount / maxHeadcount) * innerH;

  const yTicks = 4;
  const yTickValues = Array.from({ length: yTicks + 1 }, (_, i) => Math.round((maxHeadcount / yTicks) * i));

  return (
    <div className="bg-white border border-slate-200/60 rounded-xl p-5 shadow-sm">
      <div className="flex items-center justify-between mb-2 flex-wrap gap-2">
        <h3 className="text-sm font-semibold text-slate-700">Xu hướng sĩ số theo tháng</h3>
        {series.length > 1 && (
          <div className="flex items-center gap-4 text-xs text-slate-500">
            {series.map((s) => (
              <span key={s.label} className="flex items-center gap-1.5">
                <span className={`w-2.5 h-2.5 rounded-full ${SERIES_COLORS[s.color].dot}`} /> {s.label}
              </span>
            ))}
          </div>
        )}
      </div>
      <svg viewBox={`0 0 ${width} ${height}`} className="w-full h-56" role="img" aria-label="Biểu đồ đường xu hướng sĩ số theo tháng">
        {/* Gridline ngang + nhãn trục Y */}
        {yTickValues.map((v) => (
          <g key={v}>
            <line x1={padding.left} x2={width - padding.right} y1={y(v)} y2={y(v)} stroke="#e2e8f0" strokeWidth={1} />
            <text x={padding.left - 8} y={y(v)} textAnchor="end" dominantBaseline="middle" className="fill-slate-400" fontSize={10}>
              {v}
            </text>
          </g>
        ))}
        {/* Nhãn trục X: Tháng 1..N của kỳ */}
        {Array.from({ length: maxMonth }, (_, i) => i + 1).map((m) => (
          <text key={m} x={x(m)} y={height - 8} textAnchor="middle" className="fill-slate-400" fontSize={10}>
            T{m}
          </text>
        ))}
        {series.map((s) => {
          const c = SERIES_COLORS[s.color];
          const path = s.points.map((p, i) => `${i === 0 ? "M" : "L"} ${x(p.monthIndex)} ${y(p.headcount)}`).join(" ");
          const areaPath = `${path} L ${x(s.points[s.points.length - 1]?.monthIndex ?? 1)} ${y(0)} L ${x(s.points[0]?.monthIndex ?? 1)} ${y(0)} Z`;
          return (
            <g key={s.label}>
              <path d={areaPath} fill={c.fill} stroke="none" />
              <path d={path} fill="none" stroke={c.stroke} strokeWidth={2} strokeLinecap="round" strokeLinejoin="round" />
              {s.points.map((p) => (
                <circle key={p.monthIndex} cx={x(p.monthIndex)} cy={y(p.headcount)} r={4} fill={c.stroke} stroke="white" strokeWidth={1.5}>
                  <title>{`${s.label} — Tháng ${p.monthIndex} (${p.periodStart} → ${p.periodEnd}): ${p.headcount} học sinh`}</title>
                </circle>
              ))}
            </g>
          );
        })}
      </svg>
    </div>
  );
}

function toISODate(y: number, m: number, d: number): string {
  return `${String(y).padStart(4, "0")}-${String(m).padStart(2, "0")}-${String(d).padStart(2, "0")}`;
}

function yearMonthOf(date: Date): string {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}`;
}

function currentYearMonth(): string {
  return yearMonthOf(new Date());
}

/** Tháng liền trước tháng hiện tại — mặc định "Từ tháng" (xác nhận với người dùng 2026-08-20: mặc định xem 2 tháng gần nhất, VD đang Tháng 8 thì mặc định Tháng 7 – Tháng 8). */
function previousYearMonth(): string {
  const now = new Date();
  return yearMonthOf(new Date(now.getFullYear(), now.getMonth() - 1, 1));
}

/** 1 lựa chọn khoảng thời gian đã "chốt" (bất kể loại Tháng/Kỳ/Năm) — dùng chung cho cả kỳ chính và kỳ so sánh. */
interface ResolvedPeriod {
  fromDate: string;
  toDate: string;
  label: string;
  academicTermId: number | null;
}

const PERIOD_TYPE_LABELS: Record<EnrollmentMovementPeriodType, string> = {
  MONTH: "Tháng",
  TERM: "Học kỳ",
  YEAR: "Năm"
};

/**
 * Gộp "Từ tháng"/"đến tháng" (bổ sung ngoài SDD gốc, xác nhận 2026-08-20 — xem biến động NHIỀU
 * tháng liền nhau thay vì chỉ 1 tháng) thành 1 ResolvedPeriod — tự hoán đổi nếu người dùng chọn
 * "đến tháng" sớm hơn "Từ tháng". fromYm === toYm thì nhãn hiện dạng 1 tháng như cũ, khác thì hiện
 * dạng khoảng "Tháng M/YYYY – Tháng M2/YYYY2".
 */
function resolveMonthRange(fromYm: string, toYm: string): ResolvedPeriod | null {
  if (!fromYm || !toYm) return null;
  const [fy, fm] = fromYm.split("-").map(Number);
  const [ty, tm] = toYm.split("-").map(Number);
  const [startY, startM, endY, endM] = fy * 12 + fm <= ty * 12 + tm ? [fy, fm, ty, tm] : [ty, tm, fy, fm];
  const lastDayOfEnd = new Date(endY, endM, 0).getDate();
  const label = startY === endY && startM === endM ? `Tháng ${startM}/${startY}` : `Tháng ${startM}/${startY} – Tháng ${endM}/${endY}`;
  return { fromDate: toISODate(startY, startM, 1), toDate: toISODate(endY, endM, lastDayOfEnd), label, academicTermId: null };
}

/**
 * UC-69 mở rộng (bổ sung ngoài SDD gốc, xác nhận với người dùng 2026-08-20): đổi tên "Biến động
 * học sinh theo kỳ" -> "Thống kê biến động học sinh" + thêm 2 chế độ xem "theo tháng"/"theo năm"
 * (ngoài "theo kỳ" cũ), mỗi chế độ đều so sánh được với 1 khoảng THỜI GIAN CÙNG LOẠI khác (2 tháng
 * với nhau, 2 kỳ với nhau, hoặc 2 năm với nhau — không so sánh chéo loại vì trục X biểu đồ
 * (monthIndex) không có ý nghĩa chung giữa các loại khác nhau).
 */
export default function EnrollmentMovementStatsPage() {
  const { selectedCampusId, selectedClassId, hasPermission } = useApp();
  const canExport = hasPermission("report.enrollment-stats.view");
  const hasSite = selectedCampusId !== "ALL";
  const siteId = hasSite ? Number(selectedCampusId) : null;

  // Mặc định xem "Tháng" (tháng gần nhất — xem currentYearMonth() ở selectedMonthFrom/To phía dưới),
  // xác nhận với người dùng 2026-08-20 — trước đây mặc định "Học kỳ", đổi vì tháng gần nhất là nhu
  // cầu xem thường xuyên hơn khi mới vào trang.
  const [periodType, setPeriodType] = useState<EnrollmentMovementPeriodType>("MONTH");

  // ----- Chọn kỳ (giữ nguyên hành vi cũ) -----
  const [terms, setTerms] = useState<AcademicTermResponse[]>([]);
  const [selectedTermId, setSelectedTermId] = useState<number | "">("");
  const [comparisonTermId, setComparisonTermId] = useState<number | "">("");
  const [loadingTerms, setLoadingTerms] = useState(false);

  // ----- Chọn tháng (bổ sung ngoài SDD gốc, xác nhận 2026-08-20: chọn được 1 KHOẢNG nhiều tháng liền
  // nhau — "Từ tháng" mặc định = "đến tháng" (khoảng 1 tháng, giữ đúng hành vi cũ) cho tới khi người
  // dùng đổi 1 trong 2 mốc) -----
  const [selectedMonthFrom, setSelectedMonthFrom] = useState<string>(previousYearMonth());
  const [selectedMonthTo, setSelectedMonthTo] = useState<string>(currentYearMonth());
  const [comparisonMonthFrom, setComparisonMonthFrom] = useState<string>("");
  const [comparisonMonthTo, setComparisonMonthTo] = useState<string>("");

  // ----- Chọn năm -----
  const [selectedYear, setSelectedYear] = useState<number>(new Date().getFullYear());
  const [comparisonYear, setComparisonYear] = useState<number | "">("");

  const [stats, setStats] = useState<EnrollmentMovementStatsResponse | null>(null);
  const [loadingStats, setLoadingStats] = useState(false);
  const [exporting, setExporting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const [trend, setTrend] = useState<EnrollmentMovementTrendResponse | null>(null);
  const [comparisonTrend, setComparisonTrend] = useState<EnrollmentMovementTrendResponse | null>(null);

  // ----- Chế độ hiển thị dạng LƯỚI (bổ sung ngoài SDD gốc, xác nhận 2026-08-20) — hàng đầu là các
  // tháng/kỳ/năm (theo periodType đang chọn), cột đầu là từng lớp, mỗi ô là sĩ số cuối đoạn. -----
  const [displayMode, setDisplayMode] = useState<"detail" | "grid">("detail");
  const [gridYear, setGridYear] = useState(new Date().getFullYear());
  const [grid, setGrid] = useState<EnrollmentMovementGridResponse | null>(null);
  const [loadingGrid, setLoadingGrid] = useState(false);

  const { message: toastMsg, showToast } = useToast();

  // Kỳ học luôn gắn 1 điểm trường cụ thể (academic_terms.site_id NOT NULL) — tải lại khi đổi điểm trường ở Header.
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
        // listAcademicTerms trả về sắp theo startDate giảm dần -- mặc định chọn kỳ gần nhất.
        if (list.length > 0) setSelectedTermId(list[0].id);
      })
      .finally(() => setLoadingTerms(false));
  }, [hasSite, selectedCampusId]);

  // Đổi chế độ xem hoặc đổi kỳ đang xem -- bỏ lựa chọn so sánh cũ (tránh so sánh 2 khoảng trùng nhau
  // hoặc so sánh lệch loại khi vừa đổi tab).
  useEffect(() => {
    setComparisonTermId("");
    setComparisonMonthFrom("");
    setComparisonMonthTo("");
    setComparisonYear("");
  }, [periodType, selectedTermId, selectedMonthFrom, selectedMonthTo, selectedYear]);

  const currentPeriod: ResolvedPeriod | null = useMemo(() => {
    if (periodType === "TERM") {
      const term = terms.find((t) => t.id === selectedTermId);
      if (!term) return null;
      return { fromDate: term.startDate, toDate: term.endDate, label: term.name, academicTermId: term.id };
    }
    if (periodType === "MONTH") {
      return resolveMonthRange(selectedMonthFrom, selectedMonthTo);
    }
    if (!selectedYear) return null;
    return { fromDate: toISODate(selectedYear, 1, 1), toDate: toISODate(selectedYear, 12, 31), label: `Năm ${selectedYear}`, academicTermId: null };
  }, [periodType, terms, selectedTermId, selectedMonthFrom, selectedMonthTo, selectedYear]);

  const comparisonPeriod: ResolvedPeriod | null = useMemo(() => {
    if (periodType === "TERM") {
      const term = terms.find((t) => t.id === comparisonTermId);
      if (!term) return null;
      return { fromDate: term.startDate, toDate: term.endDate, label: term.name, academicTermId: term.id };
    }
    if (periodType === "MONTH") {
      return resolveMonthRange(comparisonMonthFrom, comparisonMonthTo);
    }
    if (!comparisonYear) return null;
    return { fromDate: toISODate(comparisonYear, 1, 1), toDate: toISODate(comparisonYear, 12, 31), label: `Năm ${comparisonYear}`, academicTermId: null };
  }, [periodType, terms, comparisonTermId, comparisonMonthFrom, comparisonMonthTo, comparisonYear]);

  const fetchStatsFor = (period: ResolvedPeriod): Promise<EnrollmentMovementStatsResponse> =>
    period.academicTermId != null
      ? getEnrollmentMovementStats(period.academicTermId, selectedClassId ?? undefined)
      : getEnrollmentMovementStatsForRange({
          siteId: siteId as number,
          fromDate: period.fromDate,
          toDate: period.toDate,
          periodType,
          periodLabel: period.label,
          classId: selectedClassId ?? undefined
        });

  const fetchTrendFor = (period: ResolvedPeriod): Promise<EnrollmentMovementTrendResponse> =>
    period.academicTermId != null
      ? getEnrollmentMovementTrend(period.academicTermId, selectedClassId ?? undefined)
      : getEnrollmentMovementTrendForRange({
          siteId: siteId as number,
          fromDate: period.fromDate,
          toDate: period.toDate,
          periodType,
          periodLabel: period.label,
          classId: selectedClassId ?? undefined
        });

  // classId=null (chưa chọn lớp ở Header) -- xem toàn bộ lớp của điểm trường đang chọn.
  useEffect(() => {
    if (!currentPeriod || !siteId) { setStats(null); return; }
    setLoadingStats(true);
    setError(null);
    fetchStatsFor(currentPeriod)
      .then(setStats)
      .catch((err) => {
        setStats(null);
        setError(err instanceof ApiError ? err.message : "Không tải được thống kê biến động học sinh.");
      })
      .finally(() => setLoadingStats(false));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [currentPeriod, siteId, selectedClassId]);

  // Xu hướng theo tháng (khoảng đang chọn) — dùng cho biểu đồ đường.
  useEffect(() => {
    if (!currentPeriod || !siteId) { setTrend(null); return; }
    fetchTrendFor(currentPeriod)
      .then(setTrend)
      .catch(() => setTrend(null));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [currentPeriod, siteId, selectedClassId]);

  // Xu hướng theo tháng (khoảng so sánh, tuỳ chọn) — cùng classId để so sánh công bằng.
  useEffect(() => {
    if (!comparisonPeriod || !siteId) { setComparisonTrend(null); return; }
    fetchTrendFor(comparisonPeriod)
      .then(setComparisonTrend)
      .catch(() => setComparisonTrend(null));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [comparisonPeriod, siteId, selectedClassId]);

  // Lưới tổng quan chỉ tải khi đang ở chế độ "grid" -- tránh gọi API thừa (nhiều cột x nhiều lớp) lúc
  // người dùng chỉ xem chi tiết theo 1 khoảng như trước.
  useEffect(() => {
    if (displayMode !== "grid" || !siteId) { return; }
    setLoadingGrid(true);
    setError(null);
    getEnrollmentMovementGrid({
      siteId,
      periodType,
      year: periodType === "MONTH" ? gridYear : undefined,
      classId: selectedClassId ?? undefined
    })
      .then(setGrid)
      .catch((err) => {
        setGrid(null);
        setError(err instanceof ApiError ? err.message : "Không tải được lưới biến động học sinh.");
      })
      .finally(() => setLoadingGrid(false));
  }, [displayMode, siteId, periodType, gridYear, selectedClassId]);

  const comparableTerms = useMemo(() => terms.filter((t) => t.id !== selectedTermId), [terms, selectedTermId]);

  const handleExport = async () => {
    if (!currentPeriod || !siteId) return;
    setExporting(true);
    try {
      const blob = currentPeriod.academicTermId != null
        ? await exportEnrollmentMovementStats(currentPeriod.academicTermId, selectedClassId ?? undefined)
        : await exportEnrollmentMovementStatsForRange({
            siteId, fromDate: currentPeriod.fromDate, toDate: currentPeriod.toDate,
            periodType, periodLabel: currentPeriod.label, classId: selectedClassId ?? undefined
          });
      downloadBlob(blob, `bien-dong-hoc-sinh-${currentPeriod.fromDate}-${currentPeriod.toDate}.xlsx`);
      showToast("Xuất file Excel thành công!");
    } catch (err) {
      showToast(err instanceof ApiError ? err.message : "Xuất file thất bại.");
    } finally {
      setExporting(false);
    }
  };

  const yearOptions = useMemo(() => {
    const current = new Date().getFullYear();
    return Array.from({ length: 6 }, (_, i) => current - i);
  }, []);

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="border-b border-slate-200 pb-4 flex items-start justify-between gap-3 flex-wrap">
        <div>
          <h1 className="text-xl font-bold font-display tracking-tight text-slate-900">Thống kê biến động học sinh</h1>
          <p className="text-xs text-slate-500 mt-1">
            Sĩ số đầu kỳ/cuối kỳ và biến động (nhập học mới, nghỉ/rút, chuyển lớp, hoàn thành) của các lớp — xem theo tháng, học kỳ hoặc năm.
          </p>
        </div>
        {canExport && displayMode === "detail" && (
          <Button
            variant="primary"
            disabled={!currentPeriod || exporting || loadingStats}
            onClick={handleExport}
            className="flex items-center gap-1.5 shadow-sm"
          >
            <Download className="w-4 h-4" />
            Xuất Excel
          </Button>
        )}
      </div>

      {/* Chọn loại khoảng thời gian + chế độ hiển thị (Chi tiết 1 khoảng / Lưới tổng quan nhiều khoảng) */}
      <div className="flex items-center justify-between flex-wrap gap-3">
        <div className="flex items-center gap-1 bg-white border border-slate-200 rounded-xl p-1 w-fit">
          {(Object.keys(PERIOD_TYPE_LABELS) as EnrollmentMovementPeriodType[]).map((pt) => (
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
            title="Lưới tổng quan — hàng đầu là tháng/kỳ/năm, cột đầu là lớp"
            className={cn(
              "px-3.5 py-1.5 rounded-lg text-xs font-bold transition-colors",
              displayMode === "grid" ? "bg-brand-gradient text-white shadow-xs" : "text-slate-500 hover:text-slate-700"
            )}
          >
            Lưới tổng quan
          </button>
        </div>
      </div>

      {/* Bộ lọc */}
      {!hasSite ? (
        <div className="bg-amber-50 border border-amber-200/80 rounded-xl p-4 text-amber-800 text-sm flex items-center gap-3">
          <Filter className="w-5 h-5 text-amber-600 shrink-0" />
          <span>Vui lòng chọn 1 điểm trường cụ thể trên thanh Header (góc trên bên trái).</span>
        </div>
      ) : displayMode === "grid" ? (
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
      ) : (
        <div className="bg-white rounded-xl border border-slate-200/60 shadow-sm p-4">
          <div className="flex items-center gap-2 text-xs font-semibold text-slate-600 mb-3">
            <Filter className="w-3.5 h-3.5" /> Chọn {PERIOD_TYPE_LABELS[periodType].toLowerCase()}
          </div>
          <div className="flex flex-wrap gap-3">
            {periodType === "TERM" && (
              <>
                <div className="flex-1 min-w-[260px]">
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
                </div>
                {selectedTermId && comparableTerms.length > 0 && (
                  <div className="flex-1 min-w-[260px]">
                    <label className="block text-xs text-slate-500 mb-1">So sánh với kỳ khác (tuỳ chọn)</label>
                    <Select
                      value={comparisonTermId}
                      onChange={(e) => setComparisonTermId(e.target.value ? Number(e.target.value) : "")}
                      className="w-full border border-slate-300 rounded-lg text-sm p-2 focus:outline-none focus:ring-2 focus:ring-brand-orange/40"
                    >
                      <option value="">-- Không so sánh --</option>
                      {comparableTerms.map((t) => (
                        <option key={t.id} value={t.id}>
                          {t.name} ({t.startDate} - {t.endDate})
                        </option>
                      ))}
                    </Select>
                  </div>
                )}
              </>
            )}

            {periodType === "MONTH" && (
              <>
                <div className="flex-1 min-w-[220px] flex gap-2">
                  <div className="flex-1">
                    <label className="block text-xs text-slate-500 mb-1">Từ tháng</label>
                    <MonthPicker value={selectedMonthFrom} onChange={setSelectedMonthFrom} />
                  </div>
                  <div className="flex-1">
                    <label className="block text-xs text-slate-500 mb-1">Đến tháng</label>
                    <MonthPicker value={selectedMonthTo} onChange={setSelectedMonthTo} />
                  </div>
                </div>
                <div className="flex-1 min-w-[220px] flex gap-2">
                  <div className="flex-1">
                    <label className="block text-xs text-slate-500 mb-1">So sánh — từ tháng (tuỳ chọn)</label>
                    <MonthPicker
                      value={comparisonMonthFrom}
                      onChange={(v) => {
                        setComparisonMonthFrom(v);
                        // Chọn "Từ tháng" so sánh lần đầu -- tự điền "đến tháng" trùng luôn (so sánh 1
                        // tháng, giống hành vi đơn giản trước đây), người dùng vẫn đổi lại được ngay bên cạnh.
                        if (v && !comparisonMonthTo) setComparisonMonthTo(v);
                      }}
                      placeholder="-- Không so sánh --"
                    />
                  </div>
                  <div className="flex-1">
                    <label className="block text-xs text-slate-500 mb-1">đến tháng</label>
                    <MonthPicker
                      value={comparisonMonthTo}
                      onChange={setComparisonMonthTo}
                      placeholder="-- Không so sánh --"
                      disabled={!comparisonMonthFrom}
                    />
                  </div>
                </div>
              </>
            )}

            {periodType === "YEAR" && (
              <>
                <div className="flex-1 min-w-[220px]">
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
                </div>
                <div className="flex-1 min-w-[220px]">
                  <label className="block text-xs text-slate-500 mb-1">So sánh với năm khác (tuỳ chọn)</label>
                  <Select
                    value={comparisonYear}
                    onChange={(e) => setComparisonYear(e.target.value ? Number(e.target.value) : "")}
                    className="w-full border border-slate-300 rounded-lg text-sm p-2 focus:outline-none focus:ring-2 focus:ring-brand-orange/40"
                  >
                    <option value="">-- Không so sánh --</option>
                    {yearOptions.filter((y) => y !== selectedYear).map((y) => (
                      <option key={y} value={y}>{y}</option>
                    ))}
                  </Select>
                </div>
              </>
            )}
          </div>
          {periodType === "TERM" && !loadingTerms && terms.length === 0 && (
            <p className="text-xs text-slate-400 mt-2">Điểm trường này chưa có kỳ học nào — tạo ở màn "Quản lý Kỳ học".</p>
          )}
        </div>
      )}

      {error && (
        <div className="bg-rose-50 border border-rose-200/80 rounded-xl p-4 text-rose-700 text-sm">{error}</div>
      )}

      {displayMode === "detail" && stats && !loadingStats && (
        <>
          {/* Thẻ thông tin kỳ */}
          <div className="bg-gradient-to-r from-indigo-500 to-purple-600 rounded-xl p-5 text-white flex flex-wrap items-start justify-between gap-4">
            <div>
              <p className="text-xs opacity-75">{stats.siteName}</p>
              <h2 className="text-lg font-bold mt-1">{stats.periodLabel}</h2>
              <p className="text-xs opacity-70 mt-1">{stats.startDate} — {stats.endDate}</p>
            </div>
          </div>

          {/* Thống kê tổng quan */}
          <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-6 gap-4">
            <StatCard icon={<Users className="w-5 h-5 text-brand-orange" />} label="Đầu kỳ" value={stats.totals.openingHeadcount} />
            <StatCard icon={<UserPlus className="w-5 h-5 text-emerald-500" />} label="Nhập học mới" value={stats.totals.newEnrollments} color="text-emerald-600" />
            <StatCard icon={<LogOut className="w-5 h-5 text-rose-500" />} label="Nghỉ/rút" value={stats.totals.withdrawnCount} color="text-rose-600" />
            <StatCard icon={<ArrowLeftRight className="w-5 h-5 text-amber-500" />} label="Chuyển lớp" value={stats.totals.transferredCount} color="text-amber-600" />
            <StatCard icon={<TrendingUp className="w-5 h-5 text-blue-500" />} label="Hoàn thành" value={stats.totals.completedCount} color="text-blue-600" />
            <StatCard icon={<Users className="w-5 h-5 text-brand-orange" />} label="Cuối kỳ" value={stats.totals.closingHeadcount} />
          </div>

          {/* Biểu đồ xu hướng sĩ số theo tháng — so sánh giữa 2 khoảng nếu có chọn */}
          {trend && trend.points.length > 0 && (
            <HeadcountTrendChart
              series={[
                { label: trend.periodLabel, color: "indigo", points: trend.points },
                ...(comparisonTrend && comparisonTrend.points.length > 0
                  ? [{ label: comparisonTrend.periodLabel, color: "amber", points: comparisonTrend.points }]
                  : []),
              ]}
            />
          )}

          {/* Bảng biến động theo lớp */}
          <div className="bg-white rounded-xl border border-slate-200/60 shadow-sm overflow-hidden">
            <div className="px-4 py-3 border-b border-slate-100 flex items-center justify-between gap-3 flex-wrap">
              <h3 className="text-sm font-semibold text-slate-700">Biến động theo lớp</h3>
              <span className="text-xs text-slate-400">{stats.classes.length} lớp</span>
            </div>
            {stats.classes.length === 0 ? (
              <div className="px-4 py-10 text-center">
                <Users className="w-10 h-10 mx-auto text-slate-200 mb-2" />
                <p className="text-sm text-slate-400">Điểm trường này chưa có lớp nào.</p>
              </div>
            ) : (
              <table className="w-full text-sm text-left">
                <thead className="bg-slate-50 border-b border-slate-100 text-xs text-slate-500 font-medium">
                  <tr>
                    <th className="px-4 py-2.5">Lớp</th>
                    <th className="px-4 py-2.5 text-right">Đầu kỳ</th>
                    <th className="px-4 py-2.5 text-right">Nhập mới</th>
                    <th className="px-4 py-2.5 text-right">Nghỉ/rút</th>
                    <th className="px-4 py-2.5 text-right">Chuyển lớp</th>
                    <th className="px-4 py-2.5 text-right">Hoàn thành</th>
                    <th className="px-4 py-2.5 text-right">Cuối kỳ</th>
                    <th className="px-4 py-2.5 text-right">Biến động ròng</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {stats.classes.map((row) => {
                    const net = row.closingHeadcount - row.openingHeadcount;
                    return (
                      <tr key={row.classId} className="hover:bg-slate-50/50 transition-colors">
                        <td className="px-4 py-3">
                          <span className="font-medium text-slate-800">{row.className}</span>
                          <span className="text-xs text-slate-400 ml-2">{row.classCode}</span>
                        </td>
                        <td className="px-4 py-3 text-right">{row.openingHeadcount}</td>
                        <td className="px-4 py-3 text-right text-emerald-600 font-medium">{row.newEnrollments}</td>
                        <td className="px-4 py-3 text-right text-rose-600 font-medium">{row.withdrawnCount}</td>
                        <td className="px-4 py-3 text-right text-amber-600 font-medium">{row.transferredCount}</td>
                        <td className="px-4 py-3 text-right text-blue-600 font-medium">{row.completedCount}</td>
                        <td className="px-4 py-3 text-right font-semibold text-slate-800">{row.closingHeadcount}</td>
                        <td className="px-4 py-3 text-right">
                          <span className={`inline-flex items-center gap-1 font-semibold ${net > 0 ? "text-emerald-600" : net < 0 ? "text-rose-600" : "text-slate-400"}`}>
                            {net > 0 ? <TrendingUp className="w-3.5 h-3.5" /> : net < 0 ? <TrendingDown className="w-3.5 h-3.5" /> : null}
                            {net > 0 ? `+${net}` : net}
                          </span>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
                <tfoot className="bg-slate-50 border-t-2 border-slate-200 font-bold text-slate-800">
                  <tr>
                    <td className="px-4 py-3">Tổng cộng</td>
                    <td className="px-4 py-3 text-right">{stats.totals.openingHeadcount}</td>
                    <td className="px-4 py-3 text-right text-emerald-700">{stats.totals.newEnrollments}</td>
                    <td className="px-4 py-3 text-right text-rose-700">{stats.totals.withdrawnCount}</td>
                    <td className="px-4 py-3 text-right text-amber-700">{stats.totals.transferredCount}</td>
                    <td className="px-4 py-3 text-right text-blue-700">{stats.totals.completedCount}</td>
                    <td className="px-4 py-3 text-right">{stats.totals.closingHeadcount}</td>
                    <td className="px-4 py-3 text-right">
                      {stats.totals.closingHeadcount - stats.totals.openingHeadcount > 0 ? "+" : ""}
                      {stats.totals.closingHeadcount - stats.totals.openingHeadcount}
                    </td>
                  </tr>
                </tfoot>
              </table>
            )}
          </div>
        </>
      )}

      {displayMode === "detail" && loadingStats && (
        <div className="text-center py-16 text-slate-400">
          <ArrowLeftRight className="w-14 h-14 mx-auto text-slate-200 mb-3 animate-pulse" />
          <p className="text-sm font-medium">Đang tải thống kê...</p>
        </div>
      )}

      {displayMode === "detail" && hasSite && !currentPeriod && !loadingTerms && (
        <div className="text-center py-16 text-slate-400">
          <ArrowLeftRight className="w-14 h-14 mx-auto text-slate-200 mb-3" />
          <p className="text-sm font-medium">Chọn {PERIOD_TYPE_LABELS[periodType].toLowerCase()} để xem thống kê biến động</p>
        </div>
      )}

      {/* Lưới tổng quan (bổ sung ngoài SDD gốc, xác nhận 2026-08-20) — hàng đầu là tháng/kỳ/năm, cột
          đầu là lớp, mỗi ô là sĩ số cuối đoạn. sticky cột đầu để cuộn ngang vẫn biết đang xem lớp nào,
          cùng idiom với các bảng cuộn ngang khác trong repo. */}
      {displayMode === "grid" && loadingGrid && (
        <div className="text-center py-16 text-slate-400">
          <ArrowLeftRight className="w-14 h-14 mx-auto text-slate-200 mb-3 animate-pulse" />
          <p className="text-sm font-medium">Đang tải lưới thống kê...</p>
        </div>
      )}

      {displayMode === "grid" && grid && !loadingGrid && (
        <div className="bg-white rounded-xl border border-slate-200/60 shadow-sm overflow-hidden">
          <div className="px-4 py-3 border-b border-slate-100 flex items-center justify-between gap-3 flex-wrap">
            <h3 className="text-sm font-semibold text-slate-700">{grid.siteName}</h3>
          </div>
          {grid.rows.length === 0 ? (
            <div className="px-4 py-10 text-center">
              <Users className="w-10 h-10 mx-auto text-slate-200 mb-2" />
              <p className="text-sm text-slate-400">Điểm trường này chưa có lớp nào.</p>
            </div>
          ) : (
            <div className="overflow-x-auto">
              {/* table-fixed + colgroup (bổ sung ngoài SDD gốc, xác nhận 2026-08-20) — mặc định table
                  co theo nội dung (auto width), để trống nguyên mảng bên phải khi ít cột (VD 12 tháng
                  nhưng bảng chứa rộng hơn nhiều). w-full ép bảng giãn hết chiều rộng vùng chứa; cột
                  "Lớp" giữ width cố định, các cột tháng/kỳ/năm còn lại (không set width riêng) tự chia
                  đều phần còn lại nhờ table-layout: fixed. */}
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
                        <td key={c.key} className="px-3 py-2.5 text-right text-slate-700">{row.headcountByColumnKey[c.key] ?? 0}</td>
                      ))}
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      )}

      <Toast message={toastMsg} />
    </div>
  );
}
