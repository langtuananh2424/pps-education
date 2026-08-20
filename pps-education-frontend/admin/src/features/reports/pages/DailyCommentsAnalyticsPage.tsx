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
import { useTranslation } from "react-i18next";
import Button from "@/components/ui/Button";
import Select from "@/components/ui/Select";
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


const SEVERITY_COLOR: Record<string, string> = {
  POSITIVE: "text-emerald-600 bg-emerald-50",
  NORMAL: "text-slate-500 bg-slate-50",
  CONCERN: "text-amber-600 bg-amber-50",
  WARNING: "text-rose-600 bg-rose-50",
};

/** Nhãn thái độ dịch qua i18next namespace "reports-operations" — xem src/i18n/locales/{vi,en}/reports-operations.json.
 * Thang thái độ chốt lại 2026-08-12 (StudentComment.Attitude) — trước đây map này bị lệch 1 bậc
 * (GOOD nhầm thành "Xuất sắc", FAIR nhầm thành "Tốt"...), đã sửa khớp đúng 5 file admin còn lại. */
function attitudeLabel(t: (key: string) => string, attitude: string): string {
  return t(`attitudeStatus.${attitude}`);
}

/** Nhãn mức độ nhận xét dịch qua i18next. */
function commentSeverityLabel(t: (key: string) => string, severity: string): string {
  return t(`commentSeverity.${severity}`);
}

/** Nhãn trạng thái duyệt nhận xét dịch qua i18next. */
function commentStatusLabel(t: (key: string) => string, status: string): string {
  return t(`commentStatus.${status}`);
}

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
  const { t } = useTranslation("reports-operations");
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
      showToast(t("dailyCommentsAnalyticsPage.toasts.exportClassDaySuccess"));
    } catch (err) {
      showToast(err instanceof ApiError ? err.message : t("dailyCommentsAnalyticsPage.toasts.exportFailed"));
    } finally {
      setExporting(false);
    }
  };

  // Xuất nhận xét từng học sinh
  const handleExportStudentComment = async (studentId: number) => {
    const tplId = commentTemplates[0]?.id;
    if (!tplId) {
      showToast(t("dailyCommentsAnalyticsPage.toasts.noCommentTemplate"));
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
      showToast(t("dailyCommentsAnalyticsPage.toasts.exportStudentCommentSuccess"));
    } catch (err) {
      showToast(err instanceof ApiError ? err.message : t("dailyCommentsAnalyticsPage.toasts.exportFailed"));
    } finally {
      setExporting(false);
    }
  };

  // Xuất hàng loạt nhận xét toàn bộ học sinh trong buổi học đang chọn (gộp ZIP)
  const handleExportAllStudentComments = async () => {
    const tplId = commentTemplates[0]?.id;
    if (!tplId) {
      showToast(t("dailyCommentsAnalyticsPage.toasts.noCommentTemplate"));
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
      showToast(t("dailyCommentsAnalyticsPage.toasts.exportAllSuccess"));
    } catch (err) {
      showToast(err instanceof ApiError ? err.message : t("dailyCommentsAnalyticsPage.toasts.exportFailed"));
    } finally {
      setExporting(false);
    }
  };

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="border-b border-slate-200 pb-4 flex items-start justify-between gap-3 flex-wrap">
        <div>
          <h1 className="text-xl font-bold font-display tracking-tight text-slate-900">{t("dailyCommentsAnalyticsPage.title")}</h1>
          <p className="text-xs text-slate-500 mt-1">{t("dailyCommentsAnalyticsPage.description")}</p>
        </div>
        {canExport && (
          <Button
            variant="primary"
            disabled={!selectedClassId || !selectedSessionId || exporting}
            onClick={() => setShowExportModal(true)}
            className="flex items-center gap-1.5 shadow-sm"
          >
            <Download className="w-4 h-4" />
            {t("dailyCommentsAnalyticsPage.exportButton")}
          </Button>
        )}
      </div>

      {/* Bộ lọc */}
      {selectedClassId ? (
        <div className="bg-white rounded-xl border border-slate-200/60 shadow-sm p-4">
          <div className="flex items-center justify-between gap-3 flex-wrap mb-3">
            <div className="flex items-center gap-2 text-xs font-semibold text-slate-600">
              <Filter className="w-3.5 h-3.5" /> {t("dailyCommentsAnalyticsPage.filters.title")}
            </div>
            {selectedClass && (
              <div className="text-xs text-brand-orange font-medium bg-orange-50 px-2.5 py-1 rounded-md border border-orange-100">
                {t("dailyCommentsAnalyticsPage.filters.selectedClassPrefix")} <span className="font-bold">{selectedClass.name} ({selectedClass.classCode})</span>
              </div>
            )}
          </div>
          <div className="flex flex-wrap gap-3">
            <div className="flex-1 min-w-[240px]">
              <label className="block text-xs text-slate-500 mb-1">{t("dailyCommentsAnalyticsPage.filters.sessionLabel")}</label>
              <Select
                value={selectedSessionId}
                onChange={(e) => setSelectedSessionId(e.target.value ? Number(e.target.value) : "")}
                disabled={loadingSessions}
                className="w-full border border-slate-300 rounded-lg text-sm p-2 focus:outline-none focus:ring-2 focus:ring-brand-orange/40"
              >
                <option value="">{t("dailyCommentsAnalyticsPage.filters.sessionPlaceholder")}</option>
                {sessions.map((s) => (
                  <option key={s.id} value={s.id}>
                    {t("dailyCommentsAnalyticsPage.filters.sessionOption", { number: s.sessionNumber, date: s.sessionDate, status: s.status })}
                  </option>
                ))}
              </Select>
            </div>
          </div>
        </div>
      ) : (
        <div className="bg-amber-50 border border-amber-200/80 rounded-xl p-4 text-amber-800 text-sm flex items-center gap-3">
          <Filter className="w-5 h-5 text-amber-600 shrink-0" />
          <span>{t("dailyCommentsAnalyticsPage.filters.noClassSelected")}</span>
        </div>
      )}

      {selectedSession && (
        <>
          {/* Thông tin buổi học */}
          <div className="bg-gradient-to-r from-orange-500 to-rose-500 rounded-xl p-5 text-white">
            <div className="flex flex-wrap items-start justify-between gap-3">
              <div>
                <p className="text-xs opacity-80">{t("dailyCommentsAnalyticsPage.session.label", { number: selectedSession.sessionNumber, className: selectedClass?.name })}</p>
                <h2 className="text-lg font-bold mt-1">{selectedSession.sessionDate}</h2>
                {selectedSession.lessonContent && (
                  <p className="text-sm opacity-90 mt-1">📚 {selectedSession.lessonContent}</p>
                )}
                <p className="text-xs opacity-75 mt-1">
                  {t("dailyCommentsAnalyticsPage.session.teacherPrefix")} {selectedSession.actualTeacherName ?? selectedSession.primaryTeacherName}
                  {selectedSession.teacherType && ` (${selectedSession.teacherType === "VIETNAMESE" ? t("dailyCommentsAnalyticsPage.session.teacherTypeVN") : t("dailyCommentsAnalyticsPage.session.teacherTypeForeign")})`}
                </p>
              </div>
            </div>
          </div>

          {/* Thống kê tóm tắt */}
          <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
            <StatCard
              icon={<Users className="w-5 h-5 text-brand-orange" />}
              label={t("dailyCommentsAnalyticsPage.stats.commented")}
              value={stats.total}
              sub={t("dailyCommentsAnalyticsPage.stats.commentedSub", { count: enrollments.length })}
            />
            <StatCard
              icon={<BarChart3 className="w-5 h-5 text-blue-500" />}
              label={t("dailyCommentsAnalyticsPage.stats.approved")}
              value={stats.approved}
              sub={stats.total > 0 ? `${Math.round(stats.approved / stats.total * 100)}%` : "—"}
            />
            <StatCard
              icon={<ThumbsUp className="w-5 h-5 text-emerald-500" />}
              label={t("dailyCommentsAnalyticsPage.stats.positive")}
              value={stats.positive}
            />
            <StatCard
              icon={<AlertTriangle className="w-5 h-5 text-amber-500" />}
              label={t("dailyCommentsAnalyticsPage.stats.concern")}
              value={stats.concern}
            />
          </div>

          {/* Bảng nhận xét */}
          <div className="bg-white rounded-xl border border-slate-200/60 shadow-sm overflow-hidden">
            <div className="px-4 py-3 border-b border-slate-100 flex items-center justify-between">
              <div className="flex items-center gap-2">
                <h3 className="text-sm font-semibold text-slate-700">
                  {t("dailyCommentsAnalyticsPage.table.title")}
                </h3>
                <span className="text-xs text-slate-400">{t("dailyCommentsAnalyticsPage.table.recordCount", { count: comments.length })}</span>
              </div>
              {canExport && commentTemplates.length > 0 && comments.length > 0 && (
                <Button
                  size="sm"
                  variant="secondary"
                  disabled={exporting}
                  onClick={handleExportAllStudentComments}
                  className="flex items-center gap-1.5"
                  title={t("dailyCommentsAnalyticsPage.table.exportAllTitle")}
                >
                  <Download className="w-3.5 h-3.5" />
                  {t("dailyCommentsAnalyticsPage.table.exportAllButton")}
                </Button>
              )}
            </div>
            {loadingComments ? (
              <div className="px-4 py-8 text-center text-sm text-slate-400">{t("dailyCommentsAnalyticsPage.table.loading")}</div>
            ) : comments.length === 0 ? (
              <div className="px-4 py-10 text-center">
                <Meh className="w-10 h-10 mx-auto text-slate-200 mb-2" />
                <p className="text-sm text-slate-400">{t("dailyCommentsAnalyticsPage.table.empty")}</p>
              </div>
            ) : (
              <table className="w-full text-sm text-left">
                <thead className="bg-slate-50 border-b border-slate-100 text-xs text-slate-500 font-medium">
                  <tr>
                    <th className="px-4 py-2.5">{t("dailyCommentsAnalyticsPage.table.columns.student")}</th>
                    <th className="px-4 py-2.5">{t("dailyCommentsAnalyticsPage.table.columns.attitude")}</th>
                    <th className="px-4 py-2.5">{t("dailyCommentsAnalyticsPage.table.columns.severity")}</th>
                    <th className="px-4 py-2.5">{t("dailyCommentsAnalyticsPage.table.columns.content")}</th>
                    <th className="px-4 py-2.5">{t("dailyCommentsAnalyticsPage.table.columns.status")}</th>
                    {canExport && <th className="px-4 py-2.5 text-right">{t("dailyCommentsAnalyticsPage.table.columns.export")}</th>}
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {comments.map((c) => (
                    <tr key={c.id} className="hover:bg-slate-50/50 transition-colors">
                      <td className="px-4 py-3 font-medium text-slate-800">{c.studentFullName}</td>
                      <td className="px-4 py-3">
                        <span className="text-xs">{c.attitude ? attitudeLabel(t, c.attitude) : "—"}</span>
                      </td>
                      <td className="px-4 py-3">
                        {c.severity ? (
                          <span className={`text-xs px-2 py-0.5 rounded-full font-medium ${SEVERITY_COLOR[c.severity] ?? ""}`}>
                            {commentSeverityLabel(t, c.severity)}
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
                          {commentStatusLabel(t, c.status === "APPROVED" || c.status === "PENDING" || c.status === "DRAFT" ? c.status : "REJECTED")}
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
                              title={t("dailyCommentsAnalyticsPage.table.exportStudentTitle")}
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
          <p className="text-sm font-medium">{t("dailyCommentsAnalyticsPage.emptyState")}</p>
        </div>
      )}

      <SelectReportTemplateModal
        open={showExportModal}
        onClose={() => setShowExportModal(false)}
        title={t("dailyCommentsAnalyticsPage.exportModal.title")}
        description={selectedSession ? t("dailyCommentsAnalyticsPage.exportModal.descriptionWithSession", { number: selectedSession.sessionNumber, date: selectedSession.sessionDate, className: selectedClass?.name }) : t("dailyCommentsAnalyticsPage.exportModal.descriptionDefault")}
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
