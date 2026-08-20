import React, { useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { ArrowLeftRight, Download, Filter, LogOut, TrendingDown, TrendingUp, UserPlus, Users } from "lucide-react";
import Button from "@/components/ui/Button";
import Select from "@/components/ui/Select";
import { useApp } from "@/context/AppContext";
import {
  AcademicTermResponse,
  EnrollmentMovementStatsResponse,
  EnrollmentMovementTrendResponse,
  exportEnrollmentMovementStats,
  getEnrollmentMovementStats,
  getEnrollmentMovementTrend,
  listAcademicTerms,
} from "@/features/academic/api";
import { useToast } from "@/lib/useToast";
import Toast from "@/components/ui/Toast";
import { ApiError } from "@/lib/apiClient";
import { downloadBlob } from "@/lib/xlsxTemplate";

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
  const { t } = useTranslation("reports-operations");
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
        <h3 className="text-sm font-semibold text-slate-700">{t("enrollmentMovementStatsPage.trendChart.title")}</h3>
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
      <svg viewBox={`0 0 ${width} ${height}`} className="w-full h-56" role="img" aria-label={t("enrollmentMovementStatsPage.trendChart.ariaLabel")}>
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
            {t("enrollmentMovementStatsPage.trendChart.monthAxis", { month: m })}
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
                  <title>{t("enrollmentMovementStatsPage.trendChart.tooltip", { label: s.label, month: p.monthIndex, start: p.periodStart, end: p.periodEnd, count: p.headcount })}</title>
                </circle>
              ))}
            </g>
          );
        })}
      </svg>
    </div>
  );
}

export default function EnrollmentMovementStatsPage() {
  const { t } = useTranslation("reports-operations");
  const { selectedCampusId, selectedClassId, hasPermission } = useApp();
  const canExport = hasPermission("report.enrollment-stats.view");

  const [terms, setTerms] = useState<AcademicTermResponse[]>([]);
  const [selectedTermId, setSelectedTermId] = useState<number | "">("");
  const [loadingTerms, setLoadingTerms] = useState(false);

  const [stats, setStats] = useState<EnrollmentMovementStatsResponse | null>(null);
  const [loadingStats, setLoadingStats] = useState(false);
  const [exporting, setExporting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const [trend, setTrend] = useState<EnrollmentMovementTrendResponse | null>(null);
  const [comparisonTermId, setComparisonTermId] = useState<number | "">("");
  const [comparisonTrend, setComparisonTrend] = useState<EnrollmentMovementTrendResponse | null>(null);

  const { message: toastMsg, showToast } = useToast();

  const hasSite = selectedCampusId !== "ALL";

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

  // classId=null (chưa chọn lớp ở Header) -- xem toàn bộ lớp của điểm trường đang chọn.
  useEffect(() => {
    if (!selectedTermId) { setStats(null); return; }
    setLoadingStats(true);
    setError(null);
    getEnrollmentMovementStats(Number(selectedTermId), selectedClassId ?? undefined)
      .then(setStats)
      .catch((err) => {
        setStats(null);
        setError(err instanceof ApiError ? err.message : t("enrollmentMovementStatsPage.loadStatsError"));
      })
      .finally(() => setLoadingStats(false));
  }, [selectedTermId, selectedClassId]);

  // Xu hướng theo tháng (kỳ đang chọn) — dùng cho biểu đồ đường.
  useEffect(() => {
    if (!selectedTermId) { setTrend(null); return; }
    getEnrollmentMovementTrend(Number(selectedTermId), selectedClassId ?? undefined)
      .then(setTrend)
      .catch(() => setTrend(null));
  }, [selectedTermId, selectedClassId]);

  // Đổi kỳ đang xem thì bỏ lựa chọn so sánh cũ (tránh so sánh kỳ với chính nó / kỳ đã đổi).
  useEffect(() => {
    setComparisonTermId("");
    setComparisonTrend(null);
  }, [selectedTermId]);

  // Xu hướng theo tháng (kỳ so sánh, tuỳ chọn) — cùng classId để so sánh công bằng.
  useEffect(() => {
    if (!comparisonTermId) { setComparisonTrend(null); return; }
    getEnrollmentMovementTrend(Number(comparisonTermId), selectedClassId ?? undefined)
      .then(setComparisonTrend)
      .catch(() => setComparisonTrend(null));
  }, [comparisonTermId, selectedClassId]);

  const selectedTerm = useMemo(() => terms.find((t) => t.id === selectedTermId), [terms, selectedTermId]);
  const comparableTerms = useMemo(() => terms.filter((t) => t.id !== selectedTermId), [terms, selectedTermId]);

  const handleExport = async () => {
    if (!selectedTermId) return;
    setExporting(true);
    try {
      const blob = await exportEnrollmentMovementStats(Number(selectedTermId), selectedClassId ?? undefined);
      downloadBlob(blob, `bien-dong-hoc-sinh-ky-${selectedTermId}.xlsx`);
      showToast(t("enrollmentMovementStatsPage.toasts.exportSuccess"));
    } catch (err) {
      showToast(err instanceof ApiError ? err.message : t("enrollmentMovementStatsPage.toasts.exportFailed"));
    } finally {
      setExporting(false);
    }
  };

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="border-b border-slate-200 pb-4 flex items-start justify-between gap-3 flex-wrap">
        <div>
          <h1 className="text-xl font-bold font-display tracking-tight text-slate-900">{t("enrollmentMovementStatsPage.title")}</h1>
          <p className="text-xs text-slate-500 mt-1">
            {t("enrollmentMovementStatsPage.description")}
          </p>
        </div>
        {canExport && (
          <Button
            variant="primary"
            disabled={!selectedTermId || exporting || loadingStats}
            onClick={handleExport}
            className="flex items-center gap-1.5 shadow-sm"
          >
            <Download className="w-4 h-4" />
            {t("enrollmentMovementStatsPage.exportButton")}
          </Button>
        )}
      </div>

      {/* Bộ lọc */}
      {!hasSite ? (
        <div className="bg-amber-50 border border-amber-200/80 rounded-xl p-4 text-amber-800 text-sm flex items-center gap-3">
          <Filter className="w-5 h-5 text-amber-600 shrink-0" />
          <span>{t("enrollmentMovementStatsPage.filters.noSiteSelected")}</span>
        </div>
      ) : (
        <div className="bg-white rounded-xl border border-slate-200/60 shadow-sm p-4">
          <div className="flex items-center gap-2 text-xs font-semibold text-slate-600 mb-3">
            <Filter className="w-3.5 h-3.5" /> {t("enrollmentMovementStatsPage.filters.title")}
          </div>
          <div className="flex flex-wrap gap-3">
            <div className="flex-1 min-w-[260px]">
              <label className="block text-xs text-slate-500 mb-1">{t("enrollmentMovementStatsPage.filters.termLabel")}</label>
              <Select
                value={selectedTermId}
                onChange={(e) => setSelectedTermId(e.target.value ? Number(e.target.value) : "")}
                disabled={loadingTerms}
                className="w-full border border-slate-300 rounded-lg text-sm p-2 focus:outline-none focus:ring-2 focus:ring-brand-orange/40"
              >
                <option value="">{t("enrollmentMovementStatsPage.filters.termPlaceholder")}</option>
                {terms.map((term) => (
                  <option key={term.id} value={term.id}>
                    {t("enrollmentMovementStatsPage.filters.termOption", { name: term.name, startDate: term.startDate, endDate: term.endDate })}
                  </option>
                ))}
              </Select>
            </div>
            {selectedTermId && comparableTerms.length > 0 && (
              <div className="flex-1 min-w-[260px]">
                <label className="block text-xs text-slate-500 mb-1">{t("enrollmentMovementStatsPage.filters.comparisonLabel")}</label>
                <Select
                  value={comparisonTermId}
                  onChange={(e) => setComparisonTermId(e.target.value ? Number(e.target.value) : "")}
                  className="w-full border border-slate-300 rounded-lg text-sm p-2 focus:outline-none focus:ring-2 focus:ring-brand-orange/40"
                >
                  <option value="">{t("enrollmentMovementStatsPage.filters.comparisonPlaceholder")}</option>
                  {comparableTerms.map((term) => (
                    <option key={term.id} value={term.id}>
                      {t("enrollmentMovementStatsPage.filters.termOption", { name: term.name, startDate: term.startDate, endDate: term.endDate })}
                    </option>
                  ))}
                </Select>
              </div>
            )}
          </div>
          {!loadingTerms && terms.length === 0 && (
            <p className="text-xs text-slate-400 mt-2">{t("enrollmentMovementStatsPage.filters.noTerms")}</p>
          )}
        </div>
      )}

      {error && (
        <div className="bg-rose-50 border border-rose-200/80 rounded-xl p-4 text-rose-700 text-sm">{error}</div>
      )}

      {stats && !loadingStats && (
        <>
          {/* Thẻ thông tin kỳ */}
          <div className="bg-gradient-to-r from-indigo-500 to-purple-600 rounded-xl p-5 text-white flex flex-wrap items-start justify-between gap-4">
            <div>
              <p className="text-xs opacity-75">{stats.siteName}</p>
              <h2 className="text-lg font-bold mt-1">{stats.academicTermName}</h2>
              <p className="text-xs opacity-70 mt-1">{stats.startDate} — {stats.endDate}</p>
            </div>
          </div>

          {/* Thống kê tổng quan */}
          <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-6 gap-4">
            <StatCard icon={<Users className="w-5 h-5 text-brand-orange" />} label={t("enrollmentMovementStatsPage.stats.opening")} value={stats.totals.openingHeadcount} />
            <StatCard icon={<UserPlus className="w-5 h-5 text-emerald-500" />} label={t("enrollmentMovementStatsPage.stats.newEnrollments")} value={stats.totals.newEnrollments} color="text-emerald-600" />
            <StatCard icon={<LogOut className="w-5 h-5 text-rose-500" />} label={t("enrollmentMovementStatsPage.stats.withdrawn")} value={stats.totals.withdrawnCount} color="text-rose-600" />
            <StatCard icon={<ArrowLeftRight className="w-5 h-5 text-amber-500" />} label={t("enrollmentMovementStatsPage.stats.transferred")} value={stats.totals.transferredCount} color="text-amber-600" />
            <StatCard icon={<TrendingUp className="w-5 h-5 text-blue-500" />} label={t("enrollmentMovementStatsPage.stats.completed")} value={stats.totals.completedCount} color="text-blue-600" />
            <StatCard icon={<Users className="w-5 h-5 text-brand-orange" />} label={t("enrollmentMovementStatsPage.stats.closing")} value={stats.totals.closingHeadcount} />
          </div>

          {/* Biểu đồ xu hướng sĩ số theo tháng — so sánh giữa 2 kỳ nếu có chọn */}
          {trend && trend.points.length > 0 && (
            <HeadcountTrendChart
              series={[
                { label: trend.academicTermName, color: "indigo", points: trend.points },
                ...(comparisonTrend && comparisonTrend.points.length > 0
                  ? [{ label: comparisonTrend.academicTermName, color: "amber", points: comparisonTrend.points }]
                  : []),
              ]}
            />
          )}

          {/* Bảng biến động theo lớp */}
          <div className="bg-white rounded-xl border border-slate-200/60 shadow-sm overflow-hidden">
            <div className="px-4 py-3 border-b border-slate-100 flex items-center justify-between gap-3 flex-wrap">
              <h3 className="text-sm font-semibold text-slate-700">{t("enrollmentMovementStatsPage.table.title")}</h3>
              <span className="text-xs text-slate-400">{t("enrollmentMovementStatsPage.table.classCount", { count: stats.classes.length })}</span>
            </div>
            {stats.classes.length === 0 ? (
              <div className="px-4 py-10 text-center">
                <Users className="w-10 h-10 mx-auto text-slate-200 mb-2" />
                <p className="text-sm text-slate-400">{t("enrollmentMovementStatsPage.table.empty")}</p>
              </div>
            ) : (
              <table className="w-full text-sm text-left">
                <thead className="bg-slate-50 border-b border-slate-100 text-xs text-slate-500 font-medium">
                  <tr>
                    <th className="px-4 py-2.5">{t("enrollmentMovementStatsPage.table.columns.class")}</th>
                    <th className="px-4 py-2.5 text-right">{t("enrollmentMovementStatsPage.table.columns.opening")}</th>
                    <th className="px-4 py-2.5 text-right">{t("enrollmentMovementStatsPage.table.columns.newEnrollments")}</th>
                    <th className="px-4 py-2.5 text-right">{t("enrollmentMovementStatsPage.table.columns.withdrawn")}</th>
                    <th className="px-4 py-2.5 text-right">{t("enrollmentMovementStatsPage.table.columns.transferred")}</th>
                    <th className="px-4 py-2.5 text-right">{t("enrollmentMovementStatsPage.table.columns.completed")}</th>
                    <th className="px-4 py-2.5 text-right">{t("enrollmentMovementStatsPage.table.columns.closing")}</th>
                    <th className="px-4 py-2.5 text-right">{t("enrollmentMovementStatsPage.table.columns.netChange")}</th>
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
                    <td className="px-4 py-3">{t("enrollmentMovementStatsPage.table.totalRow")}</td>
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

      {loadingStats && (
        <div className="text-center py-16 text-slate-400">
          <ArrowLeftRight className="w-14 h-14 mx-auto text-slate-200 mb-3 animate-pulse" />
          <p className="text-sm font-medium">{t("enrollmentMovementStatsPage.loadingStats")}</p>
        </div>
      )}

      {hasSite && !selectedTermId && !loadingTerms && terms.length > 0 && (
        <div className="text-center py-16 text-slate-400">
          <ArrowLeftRight className="w-14 h-14 mx-auto text-slate-200 mb-3" />
          <p className="text-sm font-medium">{t("enrollmentMovementStatsPage.selectTermPrompt")}</p>
        </div>
      )}

      <Toast message={toastMsg} />
    </div>
  );
}
