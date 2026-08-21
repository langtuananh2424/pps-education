import React, { useEffect, useMemo, useState } from "react";
import {
  GraduationCap,
  Search,
  Download,
  ChevronRight,
  Award,
  BookOpen,
  Users,
  Calendar,
  CheckCircle,
  XCircle,
  Clock,
} from "lucide-react";
import { useTranslation } from "react-i18next";
import Button from "@/components/ui/Button";
import { useApp } from "@/context/AppContext";
import {
  listReportTemplates,
  generateReport,
  ReportTemplateResponse,
  AcademicTermResponse,
  listAcademicTerms,
  ReportPeriodSelector,
} from "@/features/academic/api";
import { StudentResponse, listStudents } from "@/features/student/api";
import { useToast } from "@/lib/useToast";
import Toast from "@/components/ui/Toast";
import { ApiError, downloadReport } from "@/lib/apiClient";
import SelectReportTemplateModal from "../components/SelectReportTemplateModal";
import { useStudentProfileData } from "../hooks/useStudentProfileData";

/** Nhãn trạng thái học sinh dịch qua i18next namespace "reports-progress". */
function studentStatusLabel(t: (key: string) => string, status: string): string {
  return t(`studentProgressPage.studentStatus.${status}`);
}

/** Nhãn trạng thái điểm/nhận xét (DRAFT/SUBMITTED/OFFICIAL/REJECTED/PENDING/APPROVED). */
function commentGradeStatusLabel(t: (key: string) => string, status: string): string {
  return t(`studentProgressPage.commentGradeStatus.${status}`);
}

const STATUS_COLORS: Record<string, string> = {
  ACTIVE: "bg-emerald-100 text-emerald-700",
  SUSPENDED: "bg-amber-100 text-amber-700",
  EXPELLED: "bg-rose-100 text-rose-700",
  GRADUATED: "bg-blue-100 text-blue-700",
  WITHDRAWN: "bg-slate-100 text-slate-600",
  DEFERRAL: "bg-purple-100 text-purple-700",
};

type TabKey = "overview" | "grades" | "comments" | "attendance";

export default function StudentProgressPage() {
  const { t } = useTranslation("reports-progress");
  const { selectedCampusId, hasPermission } = useApp();
  const canExport = hasPermission("report.generate");

  const TABS: { key: TabKey; label: string; icon: React.ReactNode }[] = [
    { key: "overview", label: t("studentProgressPage.tabs.overview"), icon: <GraduationCap className="w-3.5 h-3.5" /> },
    { key: "grades", label: t("studentProgressPage.tabs.grades"), icon: <Award className="w-3.5 h-3.5" /> },
    { key: "comments", label: t("studentProgressPage.tabs.comments"), icon: <BookOpen className="w-3.5 h-3.5" /> },
    { key: "attendance", label: t("studentProgressPage.tabs.attendance"), icon: <Calendar className="w-3.5 h-3.5" /> },
  ];

  const [students, setStudents] = useState<StudentResponse[]>([]);
  const [search, setSearch] = useState("");
  const [searching, setSearching] = useState(false);
  const [selectedStudentId, setSelectedStudentId] = useState<number | null>(null);

  const profile = useStudentProfileData(selectedStudentId);

  const [templates, setTemplates] = useState<ReportTemplateResponse[]>([]);
  const [academicTerms, setAcademicTerms] = useState<AcademicTermResponse[]>([]);
  const [exporting, setExporting] = useState(false);
  const [showExportModal, setShowExportModal] = useState(false);
  const [activeTab, setActiveTab] = useState<TabKey>("overview");

  const { message: toastMsg, showToast } = useToast();

  // Load templates (STUDENT_PROFILE & TRANSCRIPT)
  useEffect(() => {
    Promise.all([
      listReportTemplates("STUDENT_PROFILE"),
      listReportTemplates("TRANSCRIPT"),
    ]).then(([pTpls, tTpls]) => setTemplates([...pTpls, ...tTpls]));
  }, []);

  // Tìm kiếm học sinh
  useEffect(() => {
    const timer = setTimeout(() => {
      if (!search.trim()) { setStudents([]); return; }
      setSearching(true);
      listStudents(
        search.trim(),
        selectedCampusId !== "ALL" ? Number(selectedCampusId) : undefined
      ).then(setStudents).finally(() => setSearching(false));
    }, 400);
    return () => clearTimeout(timer);
  }, [search, selectedCampusId]);

  // TRANSCRIPT cần period selector (UC-68 bước 2) — tải kỳ đánh giá theo điểm trường chính của học sinh.
  useEffect(() => {
    if (!profile.student?.primarySiteId) { setAcademicTerms([]); return; }
    listAcademicTerms(profile.student.primarySiteId).then(setAcademicTerms).catch(() => setAcademicTerms([]));
  }, [profile.student]);

  const selectStudent = (student: StudentResponse) => {
    setSelectedStudentId(student.id);
    setActiveTab("overview");
    setStudents([]);
    setSearch("");
  };

  // Tính thống kê điểm danh
  const attendanceStats = useMemo(() => {
    const present = profile.attendance.filter((m) => m.status === "PRESENT" || m.status === "LATE" || m.status === "EARLY_LEAVE").length;
    const absent = profile.attendance.filter((m) => m.status === "ABSENT").length;
    const excused = profile.attendance.filter((m) => m.status === "EXCUSED").length;
    return { total: profile.attendance.length, present, absent, excused };
  }, [profile.attendance]);

  // Tính điểm trung bình
  const avgScore = useMemo(() => {
    const numericGrades = profile.allGrades.filter((g) => g.overallScore !== null);
    if (!numericGrades.length) return null;
    return (numericGrades.reduce((s, g) => s + (g.overallScore as number), 0) / numericGrades.length).toFixed(2);
  }, [profile.allGrades]);

  const handleExportReport = async (templateId: number, outputFormat: "DOCX" | "PDF", periods: ReportPeriodSelector[]) => {
    if (!profile.student) return;
    setExporting(true);
    try {
      // TRANSCRIPT cần classId (điểm số gắn theo lớp) — lấy từ lớp đang học (hoặc gần nhất) của học sinh.
      const template = templates.find((t) => t.id === templateId);
      const primaryEnrollment = profile.enrollments.find((e) => e.status === "ACTIVE") ?? profile.enrollments[0];
      const classId = template?.templateType === "TRANSCRIPT" ? primaryEnrollment?.classId : undefined;
      if (template?.templateType === "TRANSCRIPT" && !classId) {
        showToast(t("studentProgressPage.toast.noEnrollment"));
        setExporting(false);
        return;
      }
      const result = await generateReport({
        templateId,
        scope: "SINGLE",
        studentId: profile.student.id,
        classId,
        periods,
        outputFormat,
      });
      if (result?.id) {
        await downloadReport(result.id);
      } else if (result?.fileUrl) {
        window.open(result.fileUrl, "_blank");
      }
      showToast(t("studentProgressPage.toast.exportSuccess"));
      setShowExportModal(false);
    } catch (err) {
      showToast(err instanceof ApiError ? err.message : t("studentProgressPage.toast.exportFailed"));
    } finally {
      setExporting(false);
    }
  };

  const dailyComments = profile.allComments.filter((c) => c.commentType === "DAILY");

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="border-b border-slate-200 pb-4">
        <h1 className="text-xl font-bold font-display tracking-tight text-slate-900">{t("studentProgressPage.title")}</h1>
        <p className="text-xs text-slate-500 mt-1">{t("studentProgressPage.description")}</p>
      </div>

      {/* Tìm kiếm học sinh */}
      {!selectedStudentId && (
        <div className="bg-white border border-slate-200/60 rounded-xl shadow-sm p-6">
          <div className="relative max-w-lg mx-auto">
            <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-400" />
            <input
              type="text"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              placeholder={t("studentProgressPage.search.placeholder")}
              className="w-full pl-10 pr-4 py-3 border border-slate-300 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-brand-orange/40 focus:border-brand-orange"
            />
          </div>

          {searching && (
            <p className="text-center text-sm text-slate-400 mt-4">{t("studentProgressPage.search.searching")}</p>
          )}

          {!searching && students.length > 0 && (
            <div className="mt-4 space-y-2 max-w-lg mx-auto">
              {students.map((s) => (
                <button
                  key={s.id}
                  onClick={() => selectStudent(s)}
                  className="w-full flex items-center justify-between gap-3 px-4 py-3 rounded-xl border border-slate-200 hover:border-brand-orange hover:bg-orange-50 transition-all text-left"
                >
                  <div className="flex items-center gap-3">
                    {s.portraitUrl ? (
                      <img src={s.portraitUrl} alt={s.fullName} className="w-9 h-9 rounded-full object-cover" />
                    ) : (
                      <div className="w-9 h-9 rounded-full bg-orange-100 flex items-center justify-center text-brand-orange font-bold text-sm">
                        {s.fullName.charAt(0)}
                      </div>
                    )}
                    <div>
                      <p className="font-semibold text-slate-800 text-sm">{s.fullName}</p>
                      <p className="text-xs text-slate-500">{s.studentCode} · {s.primarySiteName ?? "—"}</p>
                    </div>
                  </div>
                  <div className="flex items-center gap-2">
                    <span className={`text-xs px-2 py-0.5 rounded-full font-semibold ${STATUS_COLORS[s.status] ?? ""}`}>
                      {studentStatusLabel(t, s.status)}
                    </span>
                    <ChevronRight className="w-4 h-4 text-slate-400" />
                  </div>
                </button>
              ))}
            </div>
          )}

          {!searching && search.trim() && students.length === 0 && (
            <p className="text-center text-sm text-slate-400 mt-4">{t("studentProgressPage.search.noResults")}</p>
          )}

          {!search.trim() && (
            <div className="text-center mt-8">
              <GraduationCap className="w-14 h-14 mx-auto text-slate-200 mb-3" />
              <p className="text-sm text-slate-400 font-medium">{t("studentProgressPage.search.emptyPrompt")}</p>
            </div>
          )}
        </div>
      )}

      {/* Hồ sơ học sinh đã chọn */}
      {selectedStudentId && (
        <div className="space-y-4">
          {/* Header học sinh */}
          <div className="bg-gradient-to-r from-violet-500 to-purple-700 rounded-xl p-5 text-white flex flex-wrap items-center justify-between gap-4">
            <div className="flex items-center gap-4">
              {profile.student?.portraitUrl ? (
                <img src={profile.student.portraitUrl} alt={profile.student.fullName} className="w-14 h-14 rounded-full object-cover ring-2 ring-white/50" />
              ) : (
                <div className="w-14 h-14 rounded-full bg-white/20 flex items-center justify-center text-2xl font-bold">
                  {profile.student?.fullName.charAt(0) ?? "?"}
                </div>
              )}
              <div>
                <h2 className="text-lg font-bold">{profile.student?.fullName ?? "..."}</h2>
                <p className="text-sm opacity-75">{profile.student?.studentCode} · {profile.student?.primarySiteName ?? "—"}</p>
                {profile.student && (
                  <span className={`text-xs px-2 py-0.5 rounded-full font-semibold mt-1 inline-block ${STATUS_COLORS[profile.student.status] ?? ""}`}>
                    {studentStatusLabel(t, profile.student.status) ?? profile.student.status}
                  </span>
                )}
              </div>
            </div>
            <div className="flex flex-wrap gap-2">
              <Button
                size="sm"
                variant="secondary"
                onClick={() => { setSelectedStudentId(null); setSearch(""); }}
                className="bg-white/20 text-white border-white/30 hover:bg-white/30"
              >
                {t("studentProgressPage.header.changeStudentButton")}
              </Button>
              {canExport && templates.length > 0 && (
                <Button
                  size="sm"
                  variant="secondary"
                  disabled={exporting || profile.loading}
                  onClick={() => setShowExportModal(true)}
                  className="bg-white/20 text-white border-white/30 hover:bg-white/30"
                >
                  <Download className="w-3.5 h-3.5" />
                  {t("studentProgressPage.header.exportButton")}
                </Button>
              )}
            </div>
          </div>

          {profile.loading ? (
            <div className="bg-white rounded-xl border border-slate-200 p-10 text-center text-slate-400 text-sm">
              {t("studentProgressPage.loadingProfile")}
            </div>
          ) : profile.error ? (
            <div className="bg-white rounded-xl border border-slate-200 p-10 text-center text-rose-500 text-sm">
              {profile.error}
            </div>
          ) : (
            <>
              {/* Tabs */}
              <div className="flex gap-1 bg-slate-100 rounded-xl p-1">
                {TABS.map((tab) => (
                  <button
                    key={tab.key}
                    onClick={() => setActiveTab(tab.key)}
                    className={`flex-1 flex items-center justify-center gap-1.5 py-2 px-3 rounded-lg text-xs font-semibold transition-all ${
                      activeTab === tab.key
                        ? "bg-white text-slate-800 shadow-sm"
                        : "text-slate-500 hover:text-slate-700"
                    }`}
                  >
                    {tab.icon}
                    {tab.label}
                  </button>
                ))}
              </div>

              {/* Tab: Tổng quan */}
              {activeTab === "overview" && (
                <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
                  <div className="bg-white border border-slate-200/60 rounded-xl p-4 shadow-sm">
                    <div className="flex items-center gap-2 mb-2">
                      <Users className="w-4 h-4 text-indigo-500" />
                      <span className="text-xs font-semibold text-slate-600">{t("studentProgressPage.overview.classesLabel")}</span>
                    </div>
                    <p className="text-3xl font-bold text-indigo-600">{profile.enrollments.length}</p>
                    <p className="text-xs text-slate-400 mt-1">{t("studentProgressPage.overview.classesUnit")}</p>
                  </div>
                  <div className="bg-white border border-slate-200/60 rounded-xl p-4 shadow-sm">
                    <div className="flex items-center gap-2 mb-2">
                      <Award className="w-4 h-4 text-amber-500" />
                      <span className="text-xs font-semibold text-slate-600">{t("studentProgressPage.overview.avgScoreLabel")}</span>
                    </div>
                    <p className="text-3xl font-bold text-amber-600">{avgScore ?? "—"}</p>
                    <p className="text-xs text-slate-400 mt-1">{t("studentProgressPage.overview.avgScoreSub", { count: profile.allGrades.length })}</p>
                  </div>
                  <div className="bg-white border border-slate-200/60 rounded-xl p-4 shadow-sm">
                    <div className="flex items-center gap-2 mb-2">
                      <BookOpen className="w-4 h-4 text-emerald-500" />
                      <span className="text-xs font-semibold text-slate-600">{t("studentProgressPage.overview.commentsLabel")}</span>
                    </div>
                    <p className="text-3xl font-bold text-emerald-600">{dailyComments.length}</p>
                    <p className="text-xs text-slate-400 mt-1">{t("studentProgressPage.overview.commentsUnit")}</p>
                  </div>
                  <div className="bg-white border border-slate-200/60 rounded-xl p-4 shadow-sm">
                    <div className="flex items-center gap-2 mb-2">
                      <Calendar className="w-4 h-4 text-rose-500" />
                      <span className="text-xs font-semibold text-slate-600">{t("studentProgressPage.overview.attendanceLabel")}</span>
                    </div>
                    <p className="text-3xl font-bold text-rose-600">
                      {attendanceStats.total > 0
                        ? `${Math.round((attendanceStats.present / attendanceStats.total) * 100)}%`
                        : "—"}
                    </p>
                    <p className="text-xs text-slate-400 mt-1">{t("studentProgressPage.overview.attendanceSub", { present: attendanceStats.present, total: attendanceStats.total })}</p>
                  </div>

                  {/* Danh sách lớp đã học */}
                  <div className="col-span-2 md:col-span-4 bg-white border border-slate-200/60 rounded-xl p-4 shadow-sm">
                    <h3 className="text-sm font-semibold text-slate-700 mb-3">{t("studentProgressPage.overview.enrollmentHistoryTitle")}</h3>
                    {profile.enrollments.length === 0 ? (
                      <p className="text-sm text-slate-400">{t("studentProgressPage.overview.noEnrollments")}</p>
                    ) : (
                      <div className="divide-y divide-slate-100">
                        {profile.enrollments.map((e) => (
                          <div key={e.id} className="py-2 flex items-center justify-between gap-3">
                            <div>
                              <p className="text-sm font-medium text-slate-700">{e.className}</p>
                              <p className="text-xs text-slate-400">
                                {t("studentProgressPage.overview.enrolledLabel", { date: e.enrolledDate })}
                                {e.withdrawnDate ? t("studentProgressPage.overview.withdrawnLabel", { date: e.withdrawnDate }) : ""}
                              </p>
                            </div>
                            <span className={`text-xs px-2 py-0.5 rounded-full font-semibold ${e.status === "ACTIVE" ? "bg-emerald-100 text-emerald-700" : "bg-slate-100 text-slate-500"}`}>
                              {e.status === "ACTIVE" ? t("studentProgressPage.overview.activeStatus") : e.status}
                            </span>
                          </div>
                        ))}
                      </div>
                    )}
                  </div>
                </div>
              )}

              {/* Tab: Điểm số */}
              {activeTab === "grades" && (
                <div className="bg-white border border-slate-200/60 rounded-xl overflow-hidden shadow-sm">
                  <div className="px-4 py-3 border-b border-slate-100">
                    <h3 className="text-sm font-semibold text-slate-700">{t("studentProgressPage.grades.sectionTitle")}</h3>
                  </div>
                  {profile.allGrades.length === 0 ? (
                    <div className="py-10 text-center text-sm text-slate-400">{t("studentProgressPage.grades.empty")}</div>
                  ) : (
                    <table className="w-full text-sm text-left">
                      <thead className="bg-slate-50 border-b border-slate-100 text-xs text-slate-500 font-medium">
                        <tr>
                          <th className="px-4 py-2.5">{t("studentProgressPage.grades.columns.term")}</th>
                          <th className="px-4 py-2.5">{t("studentProgressPage.grades.columns.type")}</th>
                          <th className="px-4 py-2.5">{t("studentProgressPage.grades.columns.score")}</th>
                          <th className="px-4 py-2.5">{t("studentProgressPage.grades.columns.rank")}</th>
                          <th className="px-4 py-2.5">{t("studentProgressPage.grades.columns.status")}</th>
                        </tr>
                      </thead>
                      <tbody className="divide-y divide-slate-100">
                        {profile.allGrades.map((g) => (
                          <tr key={g.id} className="hover:bg-slate-50/50">
                            <td className="px-4 py-3 text-slate-700">
                              {g.academicTermName}
                              <span className="block text-[11px] text-slate-400">{g.className}</span>
                            </td>
                            <td className="px-4 py-3 text-slate-500 text-xs">
                              {g.evaluationType === "MID_TERM" ? t("studentProgressPage.grades.midTerm") : t("studentProgressPage.grades.endTerm")}
                            </td>
                            <td className="px-4 py-3">
                              <span className="text-lg font-bold text-slate-800">{g.overallScore ?? "—"}</span>
                            </td>
                            <td className="px-4 py-3 text-slate-500">{g.level ?? "—"}</td>
                            <td className="px-4 py-3">
                              <span className={`text-xs px-2 py-0.5 rounded-full font-semibold ${g.status === "OFFICIAL" ? "bg-emerald-100 text-emerald-700" : "bg-slate-100 text-slate-600"}`}>
                                {commentGradeStatusLabel(t, g.status) ?? g.status}
                              </span>
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  )}
                </div>
              )}

              {/* Tab: Nhận xét */}
              {activeTab === "comments" && (
                <div className="space-y-3">
                  {profile.allComments.length === 0 ? (
                    <div className="bg-white rounded-xl border border-slate-200 py-10 text-center text-sm text-slate-400">{t("studentProgressPage.comments.empty")}</div>
                  ) : (
                    profile.allComments.slice(0, 30).map((c) => (
                      <div key={c.id} className="bg-white border border-slate-200/60 rounded-xl p-4 shadow-sm">
                        <div className="flex items-center justify-between gap-2 mb-2">
                          <div className="flex items-center gap-2">
                            <span className="text-xs text-slate-500">{c.commentDate}</span>
                            <span className={`text-xs px-2 py-0.5 rounded-full font-semibold ${c.commentType === "DAILY" ? "bg-blue-100 text-blue-700" : "bg-purple-100 text-purple-700"}`}>
                              {c.commentType === "DAILY" ? t("studentProgressPage.comments.typeDaily") : c.commentType === "MID_TERM" ? t("studentProgressPage.comments.typeMidTerm") : t("studentProgressPage.comments.typeEndTerm")}
                            </span>
                          </div>
                          <span className={`text-xs px-2 py-0.5 rounded-full font-semibold ${c.status === "APPROVED" ? "bg-emerald-100 text-emerald-700" : "bg-slate-100 text-slate-500"}`}>
                            {commentGradeStatusLabel(t, c.status) ?? c.status}
                          </span>
                        </div>
                        <p className="text-sm text-slate-700">{c.content || t("studentProgressPage.comments.noContent")}</p>
                        {c.attitude && (
                          <p className="text-xs text-slate-400 mt-1">{t("studentProgressPage.comments.attitudeLabel", { value: c.attitude })}</p>
                        )}
                        {c.note && (
                          <p className="text-xs text-slate-400 mt-1">{t("studentProgressPage.comments.noteLabel", { value: c.note })}</p>
                        )}
                      </div>
                    ))
                  )}
                </div>
              )}

              {/* Tab: Điểm danh */}
              {activeTab === "attendance" && (
                <div className="space-y-4">
                  <div className="grid grid-cols-3 gap-4">
                    <div className="bg-emerald-50 border border-emerald-200 rounded-xl p-4 text-center">
                      <p className="text-2xl font-bold text-emerald-700">{attendanceStats.present}</p>
                      <p className="text-xs text-emerald-600 mt-1">{t("studentProgressPage.attendance.present")}</p>
                    </div>
                    <div className="bg-rose-50 border border-rose-200 rounded-xl p-4 text-center">
                      <p className="text-2xl font-bold text-rose-700">{attendanceStats.absent}</p>
                      <p className="text-xs text-rose-600 mt-1">{t("studentProgressPage.attendance.absentUnexcused")}</p>
                    </div>
                    <div className="bg-amber-50 border border-amber-200 rounded-xl p-4 text-center">
                      <p className="text-2xl font-bold text-amber-700">{attendanceStats.excused}</p>
                      <p className="text-xs text-amber-600 mt-1">{t("studentProgressPage.attendance.excusedAbsent")}</p>
                    </div>
                  </div>
                  <p className="text-xs text-slate-400 text-center">{t("studentProgressPage.attendance.recentNote")}</p>

                  {profile.attendance.length === 0 && (
                    <div className="bg-white rounded-xl border border-slate-200 py-10 text-center text-sm text-slate-400">{t("studentProgressPage.attendance.empty")}</div>
                  )}
                  <div className="space-y-2">
                    {profile.attendance.map((entry) => {
                      const isPresent = entry.status === "PRESENT" || entry.status === "LATE" || entry.status === "EARLY_LEAVE";
                      const isAbsent = entry.status === "ABSENT";
                      return (
                        <div key={entry.id} className="bg-white border border-slate-200/60 rounded-lg px-4 py-2.5 flex items-center justify-between gap-3">
                          <div className="flex items-center gap-3">
                            {isPresent ? <CheckCircle className="w-4 h-4 text-emerald-500" /> :
                             isAbsent ? <XCircle className="w-4 h-4 text-rose-500" /> :
                             <Clock className="w-4 h-4 text-amber-500" />}
                            <span className="text-sm text-slate-700">
                              {entry.sessionNumber ? t("studentProgressPage.attendance.sessionNumberLabel", { number: entry.sessionNumber }) : t("studentProgressPage.attendance.sessionIdLabel", { id: entry.classSessionId })} — {entry.sessionDate}
                            </span>
                          </div>
                          <span className={`text-xs px-2 py-0.5 rounded-full font-semibold ${
                            isPresent ? "bg-emerald-100 text-emerald-700" :
                            isAbsent ? "bg-rose-100 text-rose-700" :
                            "bg-amber-100 text-amber-700"
                          }`}>
                            {entry.status === "PRESENT" ? t("studentProgressPage.attendance.statusPresent") :
                             entry.status === "LATE" ? t("studentProgressPage.attendance.statusLate") :
                             entry.status === "EARLY_LEAVE" ? t("studentProgressPage.attendance.statusEarlyLeave") :
                             entry.status === "ABSENT" ? t("studentProgressPage.attendance.statusAbsent") : t("studentProgressPage.attendance.statusExcused")}
                          </span>
                        </div>
                      );
                    })}
                  </div>
                </div>
              )}
            </>
          )}
        </div>
      )}

      {selectedStudentId && profile.student && (
        <SelectReportTemplateModal
          open={showExportModal}
          onClose={() => setShowExportModal(false)}
          title={t("studentProgressPage.exportModal.title")}
          description={`${profile.student.fullName} (${profile.student.studentCode})`}
          templates={templates}
          academicTerms={academicTerms}
          exporting={exporting}
          onExport={handleExportReport}
        />
      )}

      <Toast message={toastMsg} />
    </div>
  );
}
