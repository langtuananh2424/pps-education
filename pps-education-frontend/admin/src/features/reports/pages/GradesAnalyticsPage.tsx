import React, { useCallback, useEffect, useMemo, useState } from "react";
import {
  Award,
  BarChart2,
  Download,
  Filter,
  TrendingUp,
  Users,
  ChevronUp,
  ChevronDown,
} from "lucide-react";
import Button from "@/components/ui/Button";
import { useApp } from "@/context/AppContext";
import {
  ClassResponse,
  ClassEnrollmentResponse,
  GradeComponentSetupResponse,
  GradeEvaluationResultResponse,
  listClasses,
  listClassEnrollments,
  listGradeComponentSetups,
  listEvaluationResults,
  listReportTemplates,
  generateReport,
  ReportTemplateResponse,
  AcademicTermResponse,
  listAcademicTerms,
  ReportPeriodSelector,
} from "@/features/academic/api";
import { useToast } from "@/lib/useToast";
import Toast from "@/components/ui/Toast";
import { ApiError, downloadReport } from "@/lib/apiClient";

import SelectReportTemplateModal from "../components/SelectReportTemplateModal";

const EVAL_TYPE_LABELS: Record<string, string> = {
  MID_TERM: "Giữa kỳ",
  END_TERM: "Cuối kỳ",
};

const SCALE_TYPE_LABELS: Record<string, string> = {
  POINT_10: "Thang 10",
  PERCENT: "Phần trăm",
  IELTS: "IELTS band",
};

const STATUS_COLORS: Record<string, string> = {
  DRAFT: "bg-slate-100 text-slate-600",
  SUBMITTED: "bg-blue-100 text-blue-700",
  OFFICIAL: "bg-emerald-100 text-emerald-700",
  REJECTED: "bg-rose-100 text-rose-600",
};
const STATUS_LABELS: Record<string, string> = {
  DRAFT: "Nháp",
  SUBMITTED: "Đã nộp",
  OFFICIAL: "Chính thức",
  REJECTED: "Bị từ chối",
};

type SortKey = "name" | "score" | "status";

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

// Biểu đồ cột đơn giản
function MiniBarChart({ data }: { data: { label: string; count: number; color: string }[] }) {
  const max = Math.max(...data.map((d) => d.count), 1);
  return (
    <div className="flex items-end gap-3 h-24">
      {data.map((d) => (
        <div key={d.label} className="flex flex-col items-center gap-1 flex-1">
          <span className="text-xs font-semibold text-slate-600">{d.count}</span>
          <div
            className={`w-full rounded-t-md ${d.color} transition-all duration-500`}
            style={{ height: `${Math.max((d.count / max) * 72, d.count > 0 ? 4 : 0)}px` }}
          />
          <span className="text-[10px] text-slate-500 text-center leading-tight">{d.label}</span>
        </div>
      ))}
    </div>
  );
}

export default function GradesAnalyticsPage() {
  const { selectedCampusId, selectedClassId: globalClassId, hasPermission } = useApp();
  const canExport = hasPermission("report.generate");

  const [classes, setClasses] = useState<ClassResponse[]>([]);
  const [setups, setSetups] = useState<GradeComponentSetupResponse[]>([]);
  const [results, setResults] = useState<GradeEvaluationResultResponse[]>([]);
  const [templates, setTemplates] = useState<ReportTemplateResponse[]>([]);
  const [academicTerms, setAcademicTerms] = useState<AcademicTermResponse[]>([]);

  const selectedClassId = globalClassId;
  const [selectedSetupId, setSelectedSetupId] = useState<number | "">("");

  const [loadingClasses, setLoadingClasses] = useState(false);
  const [loadingSetups, setLoadingSetups] = useState(false);
  const [loadingResults, setLoadingResults] = useState(false);
  const [exporting, setExporting] = useState(false);
  const [exportTarget, setExportTarget] = useState<{ mode: "BULK" } | { mode: "SINGLE"; studentId: number } | null>(null);

  const [sortKey, setSortKey] = useState<SortKey>("name");
  const [sortAsc, setSortAsc] = useState(true);
  const [search, setSearch] = useState("");

  const { message: toastMsg, showToast } = useToast();

  useEffect(() => {
    setLoadingClasses(true);
    listClasses({ siteId: selectedCampusId !== "ALL" ? Number(selectedCampusId) : undefined })
      .then(setClasses)
      .finally(() => setLoadingClasses(false));
    listReportTemplates("GRADE_REPORT").then(setTemplates);
  }, [selectedCampusId]);

  useEffect(() => {
    if (!selectedClassId) { setSetups([]); setResults([]); setSelectedSetupId(""); return; }
    setLoadingSetups(true);
    setSelectedSetupId("");
    listGradeComponentSetups(Number(selectedClassId))
      .then(setSetups)
      .finally(() => setLoadingSetups(false));
  }, [selectedClassId]);

  useEffect(() => {
    if (!selectedClassId || !selectedSetupId) { setResults([]); return; }
    setLoadingResults(true);
    listEvaluationResults(Number(selectedClassId), Number(selectedSetupId))
      .then(setResults)
      .finally(() => setLoadingResults(false));
  }, [selectedClassId, selectedSetupId]);

  const selectedClass = useMemo(() => classes.find((c) => c.id === Number(selectedClassId)), [classes, selectedClassId]);
  const selectedSetup = useMemo(() => setups.find((s) => s.id === Number(selectedSetupId)), [setups, selectedSetupId]);

  // GRADE_REPORT cần period selector (UC-68 bước 2) — tải kỳ đánh giá theo điểm trường của lớp đang chọn.
  useEffect(() => {
    if (!selectedClass) { setAcademicTerms([]); return; }
    listAcademicTerms(selectedClass.siteId).then(setAcademicTerms).catch(() => setAcademicTerms([]));
  }, [selectedClass]);

  const gradeTemplates = useMemo(() => templates.filter((t) => t.active), [templates]);

  // --- Thống kê ---
  const stats = useMemo(() => {
    const numericResults = results.filter((r) => r.overallScore !== null);
    const scores = numericResults.map((r) => r.overallScore as number);
    const avg = scores.length > 0 ? (scores.reduce((a, b) => a + b, 0) / scores.length).toFixed(1) : null;
    const max = scores.length > 0 ? Math.max(...scores) : null;
    const min = scores.length > 0 ? Math.min(...scores) : null;
    const official = results.filter((r) => r.status === "OFFICIAL").length;
    return { total: results.length, avg, max, min, official };
  }, [results]);

  // Phân phối điểm thành 5 nhóm
  const scoreDistribution = useMemo(() => {
    if (!selectedSetup) return [];
    const isIelts = selectedSetup.scaleType === "IELTS";
    const isPct = selectedSetup.scaleType === "PERCENT";
    const numeric = results.filter((r) => r.overallScore !== null);
    const bands = isIelts
      ? [
          { label: "< 4.0", min: 0, max: 4, color: "bg-rose-400" },
          { label: "4.0–5.0", min: 4, max: 5, color: "bg-amber-400" },
          { label: "5.0–6.0", min: 5, max: 6, color: "bg-yellow-400" },
          { label: "6.0–7.0", min: 6, max: 7, color: "bg-lime-400" },
          { label: "≥ 7.0", min: 7, max: 10, color: "bg-emerald-500" },
        ]
      : isPct
      ? [
          { label: "< 50%", min: 0, max: 50, color: "bg-rose-400" },
          { label: "50–64%", min: 50, max: 65, color: "bg-amber-400" },
          { label: "65–79%", min: 65, max: 80, color: "bg-yellow-400" },
          { label: "80–89%", min: 80, max: 90, color: "bg-lime-400" },
          { label: "≥ 90%", min: 90, max: 101, color: "bg-emerald-500" },
        ]
      : [
          { label: "< 5.0", min: 0, max: 5, color: "bg-rose-400" },
          { label: "5–6.4", min: 5, max: 6.5, color: "bg-amber-400" },
          { label: "6.5–7.9", min: 6.5, max: 8, color: "bg-yellow-400" },
          { label: "8–8.9", min: 8, max: 9, color: "bg-lime-400" },
          { label: "≥ 9.0", min: 9, max: 11, color: "bg-emerald-500" },
        ];
    return bands.map((b) => ({
      ...b,
      count: numeric.filter((r) => (r.overallScore as number) >= b.min && (r.overallScore as number) < b.max).length,
    }));
  }, [results, selectedSetup]);

  // Sort & filter
  const displayResults = useMemo(() => {
    let filtered = results.filter((r) =>
      r.studentFullName.toLowerCase().includes(search.toLowerCase()) ||
      r.studentCode.toLowerCase().includes(search.toLowerCase())
    );
    filtered.sort((a, b) => {
      if (sortKey === "name") return sortAsc ? a.studentFullName.localeCompare(b.studentFullName) : b.studentFullName.localeCompare(a.studentFullName);
      if (sortKey === "score") {
        const sa = a.overallScore ?? -1;
        const sb = b.overallScore ?? -1;
        return sortAsc ? sa - sb : sb - sa;
      }
      return sortAsc ? a.status.localeCompare(b.status) : b.status.localeCompare(a.status);
    });
    return filtered;
  }, [results, sortKey, sortAsc, search]);

  // Xuất báo cáo điểm của lớp
  const handleExportClass = async (templateId: number, outputFormat: "DOCX" | "PDF", periods: ReportPeriodSelector[]) => {
    if (!selectedClassId || !selectedSetupId) return;
    setExporting(true);
    try {
      const res = await generateReport({
        templateId,
        scope: "BULK_CLASS",
        classId: Number(selectedClassId),
        periods,
        outputFormat,
      });
      if (res?.id) {
        await downloadReport(res.id);
      } else if (res?.fileUrl) {
        window.open(res.fileUrl, "_blank");
      }
      showToast("Xuất báo cáo điểm cả lớp thành công! Đang tải file...");
      setExportTarget(null);
    } catch (err) {
      showToast(err instanceof ApiError ? err.message : "Xuất báo cáo thất bại");
    } finally {
      setExporting(false);
    }
  };

  const handleExportStudent = async (templateId: number, studentId: number, outputFormat: "DOCX" | "PDF", periods: ReportPeriodSelector[]) => {
    if (!selectedClassId) return;
    setExporting(true);
    try {
      const res = await generateReport({
        templateId,
        scope: "SINGLE",
        studentId,
        classId: Number(selectedClassId),
        periods,
        outputFormat,
      });
      if (res?.id) {
        await downloadReport(res.id);
      } else if (res?.fileUrl) {
        window.open(res.fileUrl, "_blank");
      }
      showToast(`Xuất báo cáo điểm cá nhân thành công! Đang tải file...`);
      setExportTarget(null);
    } catch (err) {
      showToast(err instanceof ApiError ? err.message : "Xuất thất bại");
    } finally {
      setExporting(false);
    }
  };

  const toggleSort = (key: SortKey) => {
    if (sortKey === key) setSortAsc((v) => !v);
    else { setSortKey(key); setSortAsc(false); }
  };

  const SortIcon = ({ k }: { k: SortKey }) =>
    sortKey === k ? (sortAsc ? <ChevronUp className="w-3 h-3" /> : <ChevronDown className="w-3 h-3" />) : null;

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="border-b border-slate-200 pb-4 flex items-start justify-between gap-3 flex-wrap">
        <div>
          <h1 className="text-xl font-bold font-display tracking-tight text-slate-900">Thống kê điểm số</h1>
          <p className="text-xs text-slate-500 mt-1">Xem phân phối điểm, điểm trung bình và xuất báo cáo theo lớp & kỳ học.</p>
        </div>
        {canExport && (
          <Button
            variant="primary"
            disabled={!selectedClassId || !selectedSetupId || exporting}
            onClick={() => setExportTarget({ mode: "BULK" })}
            className="flex items-center gap-1.5 shadow-sm"
          >
            <Download className="w-4 h-4" />
            Xuất báo cáo điểm của lớp
          </Button>
        )}
      </div>

      {/* Bộ lọc */}
      {selectedClassId ? (
        <div className="bg-white rounded-xl border border-slate-200/60 shadow-sm p-4">
          <div className="flex items-center justify-between gap-3 flex-wrap mb-3">
            <div className="flex items-center gap-2 text-xs font-semibold text-slate-600">
              <Filter className="w-3.5 h-3.5" /> Chọn sổ điểm
            </div>
            {selectedClass && (
              <div className="text-xs text-brand-orange font-medium bg-orange-50 px-2.5 py-1 rounded-md border border-orange-100">
                Lớp đang chọn: <span className="font-bold">{selectedClass.name} ({selectedClass.classCode})</span>
              </div>
            )}
          </div>
          <div className="flex flex-wrap gap-3">
            <div className="flex-1 min-w-[240px]">
              <label className="block text-xs text-slate-500 mb-1">Sổ điểm (kỳ & loại)</label>
              <select
                value={selectedSetupId}
                onChange={(e) => setSelectedSetupId(e.target.value ? Number(e.target.value) : "")}
                disabled={loadingSetups}
                className="w-full border border-slate-300 rounded-lg text-sm p-2 focus:outline-none focus:ring-2 focus:ring-brand-orange/40"
              >
                <option value="">-- Chọn sổ điểm --</option>
                {setups.map((s) => (
                  <option key={s.id} value={s.id}>
                    {s.academicTermName} — {EVAL_TYPE_LABELS[s.evaluationType]} ({SCALE_TYPE_LABELS[s.scaleType]})
                  </option>
                ))}
              </select>
            </div>
          </div>
        </div>
      ) : (
        <div className="bg-amber-50 border border-amber-200/80 rounded-xl p-4 text-amber-800 text-sm flex items-center gap-3">
          <Filter className="w-5 h-5 text-amber-600 shrink-0" />
          <span>Vui lòng chọn 1 lớp học trên thanh Header (góc trên bên phía bên phải) để xem thống kê điểm số.</span>
        </div>
      )}

      {selectedSetup && results.length >= 0 && !loadingResults && (
        <>
          {/* Thẻ thông tin kỳ */}
          <div className="bg-gradient-to-r from-indigo-500 to-purple-600 rounded-xl p-5 text-white flex flex-wrap items-start justify-between gap-4">
            <div>
              <p className="text-xs opacity-75">
                {selectedClass?.name} — {selectedSetup.academicTermName}
              </p>
              <h2 className="text-lg font-bold mt-1">
                {EVAL_TYPE_LABELS[selectedSetup.evaluationType]} · {SCALE_TYPE_LABELS[selectedSetup.scaleType]}
              </h2>
              <p className="text-xs opacity-70 mt-1">Tính theo danh sách tại: {selectedSetup.rosterAsOfDate}</p>
            </div>
          </div>

          {/* Thống kê */}
          <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
            <StatCard icon={<Users className="w-5 h-5 text-brand-orange" />} label="Tổng học sinh" value={stats.total} />
            <StatCard
              icon={<BarChart2 className="w-5 h-5 text-indigo-500" />}
              label="Điểm trung bình"
              value={stats.avg !== null ? stats.avg : "—"}
              color="text-indigo-600"
            />
            <StatCard
              icon={<TrendingUp className="w-5 h-5 text-emerald-500" />}
              label="Điểm cao nhất"
              value={stats.max !== null ? stats.max : "—"}
              sub={stats.min !== null ? `Thấp nhất: ${stats.min}` : undefined}
              color="text-emerald-600"
            />
            <StatCard
              icon={<Award className="w-5 h-5 text-amber-500" />}
              label="Đã chính thức"
              value={`${stats.official}/${stats.total}`}
              color="text-amber-600"
            />
          </div>

          {/* Biểu đồ phân phối */}
          {scoreDistribution.length > 0 && (
            <div className="bg-white border border-slate-200/60 rounded-xl p-5 shadow-sm">
              <h3 className="text-sm font-semibold text-slate-700 mb-4">Phân phối điểm</h3>
              <MiniBarChart data={scoreDistribution} />
            </div>
          )}

          {/* Bảng điểm */}
          <div className="bg-white rounded-xl border border-slate-200/60 shadow-sm overflow-hidden">
            <div className="px-4 py-3 border-b border-slate-100 flex items-center justify-between gap-3 flex-wrap">
              <h3 className="text-sm font-semibold text-slate-700">Bảng điểm học sinh</h3>
              <input
                type="text"
                value={search}
                onChange={(e) => setSearch(e.target.value)}
                placeholder="Tìm theo tên / mã học sinh..."
                className="text-xs border border-slate-300 rounded-lg px-3 py-1.5 focus:outline-none focus:ring-1 focus:ring-brand-orange w-56"
              />
            </div>
            {loadingResults ? (
              <div className="px-4 py-8 text-center text-sm text-slate-400">Đang tải điểm...</div>
            ) : displayResults.length === 0 ? (
              <div className="px-4 py-10 text-center">
                <Award className="w-10 h-10 mx-auto text-slate-200 mb-2" />
                <p className="text-sm text-slate-400">Chưa có điểm nào trong sổ điểm này</p>
              </div>
            ) : (
              <table className="w-full text-sm text-left">
                <thead className="bg-slate-50 border-b border-slate-100 text-xs text-slate-500 font-medium">
                  <tr>
                    <th className="px-4 py-2.5">#</th>
                    <th
                      className="px-4 py-2.5 cursor-pointer hover:text-slate-700 select-none"
                      onClick={() => toggleSort("name")}
                    >
                      <span className="flex items-center gap-1">Học sinh <SortIcon k="name" /></span>
                    </th>
                    <th
                      className="px-4 py-2.5 cursor-pointer hover:text-slate-700 select-none"
                      onClick={() => toggleSort("score")}
                    >
                      <span className="flex items-center gap-1">Điểm <SortIcon k="score" /></span>
                    </th>
                    <th className="px-4 py-2.5">Xếp hạng</th>
                    <th
                      className="px-4 py-2.5 cursor-pointer hover:text-slate-700 select-none"
                      onClick={() => toggleSort("status")}
                    >
                      <span className="flex items-center gap-1">Trạng thái <SortIcon k="status" /></span>
                    </th>
                    {canExport && templates.length > 0 && <th className="px-4 py-2.5 text-right">Xuất</th>}
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {displayResults.map((r, idx) => (
                    <tr key={r.id} className="hover:bg-slate-50/50 transition-colors">
                      <td className="px-4 py-3 text-slate-400 text-xs">{idx + 1}</td>
                      <td className="px-4 py-3">
                        <div>
                          <span className="font-medium text-slate-800">{r.studentFullName}</span>
                          <span className="text-xs text-slate-400 ml-2">{r.studentCode}</span>
                        </div>
                      </td>
                      <td className="px-4 py-3">
                        <span className="text-lg font-bold text-slate-800">
                          {r.overallScore !== null ? r.overallScore : "—"}
                        </span>
                      </td>
                      <td className="px-4 py-3 text-xs text-slate-500">{r.level ?? "—"}</td>
                      <td className="px-4 py-3">
                        <span className={`text-xs px-2 py-0.5 rounded-full font-semibold ${STATUS_COLORS[r.status] ?? ""}`}>
                          {STATUS_LABELS[r.status] ?? r.status}
                        </span>
                      </td>
                      {canExport && templates.length > 0 && (
                        <td className="px-4 py-3 text-right">
                          <Button
                            size="sm"
                            variant="secondary"
                            disabled={exporting}
                            onClick={() => setExportTarget({ mode: "SINGLE", studentId: r.studentId })}
                            title="Xuất bảng điểm cá nhân"
                          >
                            <Download className="w-3.5 h-3.5" />
                          </Button>
                        </td>
                      )}
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </div>
        </>
      )}

      {!selectedClassId && (
        <div className="text-center py-16 text-slate-400">
          <Award className="w-14 h-14 mx-auto text-slate-200 mb-3" />
          <p className="text-sm font-medium">Chọn lớp học và sổ điểm để xem thống kê</p>
        </div>
      )}

      <SelectReportTemplateModal
        open={exportTarget !== null}
        onClose={() => setExportTarget(null)}
        title={exportTarget?.mode === "SINGLE" ? "Xuất bảng điểm cá nhân" : "Xuất báo cáo điểm của lớp"}
        description={selectedSetup ? `${selectedClass?.name} — ${selectedSetup.academicTermName}` : "Chọn mẫu báo cáo phù hợp để xuất file."}
        templates={gradeTemplates}
        academicTerms={academicTerms}
        exporting={exporting}
        onExport={async (templateId, outputFormat, periods) => {
          if (exportTarget?.mode === "SINGLE") {
            await handleExportStudent(templateId, exportTarget.studentId, outputFormat, periods);
          } else {
            await handleExportClass(templateId, outputFormat, periods);
          }
        }}
      />

      <Toast message={toastMsg} />
    </div>
  );
}
