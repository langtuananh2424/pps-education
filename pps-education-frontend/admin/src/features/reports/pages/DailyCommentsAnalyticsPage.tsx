import React, { useCallback, useEffect, useMemo, useState } from "react";
import {
  BarChart3,
  Download,
  Filter,
  Users,
  ThumbsUp,
  Meh,
  AlertTriangle,
} from "lucide-react";
import Button from "@/components/ui/Button";
import { useApp } from "@/context/AppContext";
import {
  ClassResponse,
  ClassSessionResponse,
  ClassEnrollmentResponse,
  StudentCommentResponse,
  listClasses,
  listClassSessions,
  listClassEnrollments,
  listComments,
  listReportTemplates,
  generateReport,
  ReportTemplateResponse,
} from "@/features/academic/api";
import { useToast } from "@/lib/useToast";
import Toast from "@/components/ui/Toast";
import { ApiError, downloadReport } from "@/lib/apiClient";
import SelectReportTemplateModal from "../components/SelectReportTemplateModal";


/** Thang thái độ chốt lại 2026-08-12 (StudentComment.Attitude) — trước đây map này bị lệch 1 bậc
 * (GOOD nhầm thành "Xuất sắc", FAIR nhầm thành "Tốt"...), đã sửa khớp đúng 5 file admin còn lại. */
const ATTITUDE_LABEL: Record<string, string> = {
  WEAK: "Yếu",
  AVERAGE: "Trung bình",
  FAIR: "Khá",
  GOOD: "Tốt",
  EXCELLENT: "Xuất sắc",
};

const SEVERITY_COLOR: Record<string, string> = {
  POSITIVE: "text-emerald-600 bg-emerald-50",
  NORMAL: "text-slate-500 bg-slate-50",
  CONCERN: "text-amber-600 bg-amber-50",
  WARNING: "text-rose-600 bg-rose-50",
};

function StatCard({ icon, label, value, sub }: { icon: React.ReactNode; label: string; value: string | number; sub?: string }) {
  return (
    <div className="bg-white border border-slate-200/60 rounded-xl p-4 flex items-center gap-4 shadow-sm">
      <div className="p-2.5 bg-orange-50 rounded-lg">{icon}</div>
      <div>
        <p className="text-2xl font-bold text-slate-800">{value}</p>
        <p className="text-xs text-slate-500">{label}</p>
        {sub && <p className="text-xs text-slate-400">{sub}</p>}
      </div>
    </div>
  );
}

export default function DailyCommentsAnalyticsPage() {
  const { selectedCampusId, selectedClassId: globalClassId, hasPermission } = useApp();
  const canExport = hasPermission("report.generate");

  // --- State lựa chọn ---
  const [classes, setClasses] = useState<ClassResponse[]>([]);
  const [sessions, setSessions] = useState<ClassSessionResponse[]>([]);
  const [enrollments, setEnrollments] = useState<ClassEnrollmentResponse[]>([]);
  const [comments, setComments] = useState<StudentCommentResponse[]>([]);
  const [templates, setTemplates] = useState<ReportTemplateResponse[]>([]);

  const selectedClassId = globalClassId;
  const [selectedSessionId, setSelectedSessionId] = useState<number | "">("");

  const [loadingClasses, setLoadingClasses] = useState(false);
  const [loadingSessions, setLoadingSessions] = useState(false);
  const [loadingComments, setLoadingComments] = useState(false);
  const [exporting, setExporting] = useState(false);
  const [showExportModal, setShowExportModal] = useState(false);

  const { message: toastMsg, showToast } = useToast();

  // --- Tải danh sách lớp & Mẫu báo cáo ---
  useEffect(() => {
    setLoadingClasses(true);
    listClasses({ siteId: selectedCampusId !== "ALL" ? Number(selectedCampusId) : undefined })
      .then(setClasses)
      .finally(() => setLoadingClasses(false));
    listReportTemplates("DAILY_REPORT").then((tpls) => setTemplates((prev) => [...prev.filter((t) => t.templateType !== "DAILY_REPORT"), ...tpls]));
    listReportTemplates("STUDENT_COMMENT").then((tpls) => setTemplates((prev) => [...prev.filter((t) => t.templateType !== "STUDENT_COMMENT"), ...tpls]));
  }, [selectedCampusId]);

  // --- Tải buổi học khi chọn lớp ---
  useEffect(() => {
    if (!selectedClassId) { setSessions([]); setEnrollments([]); setSelectedSessionId(""); return; }
    setLoadingSessions(true);
    setSelectedSessionId("");
    Promise.all([
      listClassSessions(Number(selectedClassId)),
      listClassEnrollments(Number(selectedClassId)),
    ]).then(([sess, enr]) => {
      // Sắp xếp mới nhất lên trước
      setSessions([...sess].sort((a, b) => b.sessionDate.localeCompare(a.sessionDate)));
      setEnrollments(enr.filter((e) => e.status === "ACTIVE" || (e.status as string) === "ENROLLED"));
    }).finally(() => setLoadingSessions(false));
  }, [selectedClassId]);

  // --- Tải nhận xét khi chọn buổi ---
  useEffect(() => {
    if (!selectedClassId || !selectedSessionId) { setComments([]); return; }
    setLoadingComments(true);
    // Lấy nhận xét của tất cả học sinh trong lớp cho buổi này
    const classId = Number(selectedClassId);
    if (!enrollments.length) { setLoadingComments(false); return; }
    Promise.all(
      enrollments.map((e) =>
        listComments(classId, e.studentId).catch(() => [] as StudentCommentResponse[])
      )
    ).then((allCommentArrays) => {
      const flat = allCommentArrays.flat().filter(
        (c) => c.classSessionId === Number(selectedSessionId) && c.commentType === "DAILY"
      );
      setComments(flat);
    }).finally(() => setLoadingComments(false));
  }, [selectedClassId, selectedSessionId, enrollments]);

  const selectedClass = useMemo(
    () => classes.find((c) => c.id === Number(selectedClassId)),
    [classes, selectedClassId]
  );
  const selectedSession = useMemo(
    () => sessions.find((s) => s.id === Number(selectedSessionId)),
    [sessions, selectedSessionId]
  );

  const dailyTemplates = useMemo(
    () => templates.filter((t) => t.templateType === "DAILY_REPORT" && t.active),
    [templates]
  );
  const commentTemplates = useMemo(
    () => templates.filter((t) => t.templateType === "STUDENT_COMMENT" && t.active),
    [templates]
  );

  // Thống kê nhanh
  const stats = useMemo(() => {
    const total = comments.length;
    const approved = comments.filter((c) => c.status === "APPROVED").length;
    const positive = comments.filter((c) => c.severity === "POSITIVE").length;
    const concern = comments.filter((c) => c.severity === "CONCERN" || c.severity === "WARNING").length;
    return { total, approved, positive, concern };
  }, [comments]);

  // Xuất báo cáo ngày cho cả lớp
  const handleExportClassDay = async (templateId: number, outputFormat?: "DOCX" | "PDF") => {
    if (!selectedClassId || !selectedSessionId) return;
    setExporting(true);
    try {
      const res = await generateReport({
        templateId,
        scope: "CLASS_SESSION",
        classSessionId: Number(selectedSessionId),
        outputFormat,
      });
      if (res?.id) {
        await downloadReport(res.id);
      } else if (res?.fileUrl) {
        window.open(res.fileUrl, "_blank");
      }
      showToast("Xuất báo cáo ngày của lớp thành công! Đang tải file...");
    } catch (err) {
      showToast(err instanceof ApiError ? err.message : "Xuất báo cáo thất bại");
    } finally {
      setExporting(false);
    }
  };

  // Xuất nhận xét từng học sinh
  const handleExportStudentComment = async (studentId: number) => {
    const tplId = commentTemplates[0]?.id;
    if (!tplId) {
      showToast("Chưa có mẫu nhận xét học sinh (STUDENT_COMMENT)");
      return;
    }
    setExporting(true);
    try {
      const res = await generateReport({
        templateId: tplId,
        scope: "SINGLE",
        studentId,
        classSessionId: selectedSessionId ? Number(selectedSessionId) : undefined,
      });
      if (res?.id) {
        await downloadReport(res.id);
      } else if (res?.fileUrl) {
        window.open(res.fileUrl, "_blank");
      }
      showToast("Xuất nhận xét thành công! Đang tải file...");
    } catch (err) {
      showToast(err instanceof ApiError ? err.message : "Xuất báo cáo thất bại");
    } finally {
      setExporting(false);
    }
  };

  // Xuất hàng loạt nhận xét toàn bộ học sinh trong buổi học đang chọn (gộp ZIP)
  const handleExportAllStudentComments = async () => {
    const tplId = commentTemplates[0]?.id;
    if (!tplId) {
      showToast("Chưa có mẫu nhận xét học sinh (STUDENT_COMMENT)");
      return;
    }
    if (!selectedClassId || !selectedSessionId) return;
    setExporting(true);
    try {
      const res = await generateReport({
        templateId: tplId,
        scope: "BULK_CLASS",
        classId: Number(selectedClassId),
        classSessionId: Number(selectedSessionId),
      });
      if (res?.id) {
        await downloadReport(res.id);
      } else if (res?.fileUrl) {
        window.open(res.fileUrl, "_blank");
      }
      showToast("Xuất hàng loạt nhận xét thành công! Đang tải file ZIP...");
    } catch (err) {
      showToast(err instanceof ApiError ? err.message : "Xuất báo cáo thất bại");
    } finally {
      setExporting(false);
    }
  };

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="border-b border-slate-200 pb-4 flex items-start justify-between gap-3 flex-wrap">
        <div>
          <h1 className="text-xl font-bold font-display tracking-tight text-slate-900">Thống kê nhận xét ngày</h1>
          <p className="text-xs text-slate-500 mt-1">Xem và xuất báo cáo nhận xét theo lớp, buổi học.</p>
        </div>
        {canExport && (
          <Button
            variant="primary"
            disabled={!selectedClassId || !selectedSessionId || exporting}
            onClick={() => setShowExportModal(true)}
            className="flex items-center gap-1.5 shadow-sm"
          >
            <Download className="w-4 h-4" />
            Xuất báo cáo ngày của lớp
          </Button>
        )}
      </div>

      {/* Bộ lọc */}
      {selectedClassId ? (
        <div className="bg-white rounded-xl border border-slate-200/60 shadow-sm p-4">
          <div className="flex items-center justify-between gap-3 flex-wrap mb-3">
            <div className="flex items-center gap-2 text-xs font-semibold text-slate-600">
              <Filter className="w-3.5 h-3.5" /> Chọn buổi học
            </div>
            {selectedClass && (
              <div className="text-xs text-brand-orange font-medium bg-orange-50 px-2.5 py-1 rounded-md border border-orange-100">
                Lớp đang chọn: <span className="font-bold">{selectedClass.name} ({selectedClass.classCode})</span>
              </div>
            )}
          </div>
          <div className="flex flex-wrap gap-3">
            <div className="flex-1 min-w-[240px]">
              <label className="block text-xs text-slate-500 mb-1">Buổi học</label>
              <select
                value={selectedSessionId}
                onChange={(e) => setSelectedSessionId(e.target.value ? Number(e.target.value) : "")}
                disabled={loadingSessions}
                className="w-full border border-slate-300 rounded-lg text-sm p-2 focus:outline-none focus:ring-2 focus:ring-brand-orange/40"
              >
                <option value="">-- Chọn buổi học --</option>
                {sessions.map((s) => (
                  <option key={s.id} value={s.id}>
                    Buổi {s.sessionNumber} — {s.sessionDate} ({s.status})
                  </option>
                ))}
              </select>
            </div>
          </div>
        </div>
      ) : (
        <div className="bg-amber-50 border border-amber-200/80 rounded-xl p-4 text-amber-800 text-sm flex items-center gap-3">
          <Filter className="w-5 h-5 text-amber-600 shrink-0" />
          <span>Vui lòng chọn 1 lớp học trên thanh Header (góc trên bên phải) để xem thống kê nhận xét.</span>
        </div>
      )}

      {selectedSession && (
        <>
          {/* Thông tin buổi học */}
          <div className="bg-gradient-to-r from-orange-500 to-rose-500 rounded-xl p-5 text-white">
            <div className="flex flex-wrap items-start justify-between gap-3">
              <div>
                <p className="text-xs opacity-80">Buổi {selectedSession.sessionNumber} — {selectedClass?.name}</p>
                <h2 className="text-lg font-bold mt-1">{selectedSession.sessionDate}</h2>
                {selectedSession.lessonContent && (
                  <p className="text-sm opacity-90 mt-1">📚 {selectedSession.lessonContent}</p>
                )}
                <p className="text-xs opacity-75 mt-1">
                  GV: {selectedSession.actualTeacherName ?? selectedSession.primaryTeacherName}
                  {selectedSession.teacherType && ` (${selectedSession.teacherType === "VIETNAMESE" ? "VN" : "Nước ngoài"})`}
                </p>
              </div>
            </div>
          </div>

          {/* Thống kê tóm tắt */}
          <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
            <StatCard
              icon={<Users className="w-5 h-5 text-brand-orange" />}
              label="Sĩ số nhận xét"
              value={stats.total}
              sub={`/ ${enrollments.length} học sinh`}
            />
            <StatCard
              icon={<BarChart3 className="w-5 h-5 text-blue-500" />}
              label="Đã duyệt"
              value={stats.approved}
              sub={stats.total > 0 ? `${Math.round(stats.approved / stats.total * 100)}%` : "—"}
            />
            <StatCard
              icon={<ThumbsUp className="w-5 h-5 text-emerald-500" />}
              label="Tích cực"
              value={stats.positive}
            />
            <StatCard
              icon={<AlertTriangle className="w-5 h-5 text-amber-500" />}
              label="Cần chú ý"
              value={stats.concern}
            />
          </div>

          {/* Bảng nhận xét */}
          <div className="bg-white rounded-xl border border-slate-200/60 shadow-sm overflow-hidden">
            <div className="px-4 py-3 border-b border-slate-100 flex items-center justify-between">
              <div className="flex items-center gap-2">
                <h3 className="text-sm font-semibold text-slate-700">
                  Nhận xét từng học sinh
                </h3>
                <span className="text-xs text-slate-400">{comments.length} bản ghi</span>
              </div>
              {canExport && commentTemplates.length > 0 && comments.length > 0 && (
                <Button
                  size="sm"
                  variant="secondary"
                  disabled={exporting}
                  onClick={handleExportAllStudentComments}
                  className="flex items-center gap-1.5"
                  title="Xuất hàng loạt nhận xét toàn bộ học sinh trong buổi học này (file ZIP)"
                >
                  <Download className="w-3.5 h-3.5" />
                  Xuất hàng loạt
                </Button>
              )}
            </div>
            {loadingComments ? (
              <div className="px-4 py-8 text-center text-sm text-slate-400">Đang tải nhận xét...</div>
            ) : comments.length === 0 ? (
              <div className="px-4 py-10 text-center">
                <Meh className="w-10 h-10 mx-auto text-slate-200 mb-2" />
                <p className="text-sm text-slate-400">Chưa có nhận xét nào cho buổi học này</p>
              </div>
            ) : (
              <table className="w-full text-sm text-left">
                <thead className="bg-slate-50 border-b border-slate-100 text-xs text-slate-500 font-medium">
                  <tr>
                    <th className="px-4 py-2.5">Học sinh</th>
                    <th className="px-4 py-2.5">Thái độ</th>
                    <th className="px-4 py-2.5">Mức độ</th>
                    <th className="px-4 py-2.5">Nội dung nhận xét</th>
                    <th className="px-4 py-2.5">Trạng thái</th>
                    {canExport && <th className="px-4 py-2.5 text-right">Xuất</th>}
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {comments.map((c) => (
                    <tr key={c.id} className="hover:bg-slate-50/50 transition-colors">
                      <td className="px-4 py-3 font-medium text-slate-800">{c.studentFullName}</td>
                      <td className="px-4 py-3">
                        <span className="text-xs">{c.attitude ? ATTITUDE_LABEL[c.attitude] ?? c.attitude : "—"}</span>
                      </td>
                      <td className="px-4 py-3">
                        {c.severity ? (
                          <span className={`text-xs px-2 py-0.5 rounded-full font-medium ${SEVERITY_COLOR[c.severity] ?? ""}`}>
                            {c.severity === "POSITIVE" ? "Tích cực" : c.severity === "NORMAL" ? "Bình thường" : c.severity === "CONCERN" ? "Cần chú ý" : "Cảnh báo"}
                          </span>
                        ) : "—"}
                      </td>
                      <td className="px-4 py-3 max-w-xs">
                        <p className="text-xs text-slate-600 line-clamp-2">{c.content || "—"}</p>
                      </td>
                      <td className="px-4 py-3">
                        <span className={`text-xs px-2 py-0.5 rounded-full font-semibold ${
                          c.status === "APPROVED" ? "bg-emerald-100 text-emerald-700" :
                          c.status === "PENDING" ? "bg-amber-100 text-amber-700" :
                          c.status === "DRAFT" ? "bg-slate-100 text-slate-600" :
                          "bg-rose-100 text-rose-600"
                        }`}>
                          {c.status === "APPROVED" ? "Đã duyệt" : c.status === "PENDING" ? "Chờ duyệt" : c.status === "DRAFT" ? "Nháp" : "Bị từ chối"}
                        </span>
                      </td>
                      {canExport && (
                        <td className="px-4 py-3 text-right">
                          {commentTemplates.length > 0 ? (
                            <Button
                              size="sm"
                              variant="secondary"
                              disabled={exporting}
                              onClick={() => handleExportStudentComment(c.studentId)}
                              title="Xuất báo cáo nhận xét học sinh"
                            >
                              <Download className="w-3.5 h-3.5" />
                            </Button>
                          ) : (
                            <span className="text-xs text-slate-300">—</span>
                          )}
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
          <BarChart3 className="w-14 h-14 mx-auto text-slate-200 mb-3" />
          <p className="text-sm font-medium">Chọn lớp học và buổi học để xem thống kê nhận xét</p>
        </div>
      )}

      <SelectReportTemplateModal
        open={showExportModal}
        onClose={() => setShowExportModal(false)}
        title="Xuất báo cáo ngày của lớp"
        description={selectedSession ? `Buổi ${selectedSession.sessionNumber} — ${selectedSession.sessionDate} (${selectedClass?.name})` : "Chọn mẫu báo cáo phù hợp để xuất file."}
        templates={dailyTemplates}
        exporting={exporting}
        onExport={async (templateId, outputFormat) => {
          setShowExportModal(false);
          await handleExportClassDay(templateId, outputFormat);
        }}
      />

      <Toast message={toastMsg} />
    </div>
  );
}
