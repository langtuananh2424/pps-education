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

const STATUS_LABELS: Record<string, string> = {
  ACTIVE: "Đang học",
  SUSPENDED: "Đình chỉ",
  EXPELLED: "Bị đuổi",
  GRADUATED: "Tốt nghiệp",
  WITHDRAWN: "Rút khỏi",
  DEFERRAL: "Tạm hoãn",
};

const STATUS_COLORS: Record<string, string> = {
  ACTIVE: "bg-emerald-100 text-emerald-700",
  SUSPENDED: "bg-amber-100 text-amber-700",
  EXPELLED: "bg-rose-100 text-rose-700",
  GRADUATED: "bg-blue-100 text-blue-700",
  WITHDRAWN: "bg-slate-100 text-slate-600",
  DEFERRAL: "bg-purple-100 text-purple-700",
};

const COMMENT_GRADE_LABELS: Record<string, string> = {
  DRAFT: "Nháp",
  SUBMITTED: "Đã nộp",
  OFFICIAL: "Chính thức",
  REJECTED: "Từ chối",
  PENDING: "Chờ duyệt",
  APPROVED: "Đã duyệt",
};

type TabKey = "overview" | "grades" | "comments" | "attendance";

const TABS: { key: TabKey; label: string; icon: React.ReactNode }[] = [
  { key: "overview", label: "Tổng quan", icon: <GraduationCap className="w-3.5 h-3.5" /> },
  { key: "grades", label: "Điểm số", icon: <Award className="w-3.5 h-3.5" /> },
  { key: "comments", label: "Nhận xét", icon: <BookOpen className="w-3.5 h-3.5" /> },
  { key: "attendance", label: "Điểm danh", icon: <Calendar className="w-3.5 h-3.5" /> },
];

export default function StudentProgressPage() {
  const { selectedCampusId, hasPermission } = useApp();
  const canExport = hasPermission("report.generate");

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
        showToast("Học sinh chưa ghi danh lớp nào — không thể xuất Phiếu kết quả lộ trình.");
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
      showToast("Xuất báo cáo thành công! Đang tải file...");
      setShowExportModal(false);
    } catch (err) {
      showToast(err instanceof ApiError ? err.message : "Xuất báo cáo thất bại.");
    } finally {
      setExporting(false);
    }
  };

  const dailyComments = profile.allComments.filter((c) => c.commentType === "DAILY");

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="border-b border-slate-200 pb-4">
        <h1 className="text-xl font-bold font-display tracking-tight text-slate-900">Hồ sơ học tập</h1>
        <p className="text-xs text-slate-500 mt-1">Xem toàn bộ quá trình học tập của một học sinh cụ thể.</p>
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
              placeholder="Nhập tên hoặc mã học sinh..."
              className="w-full pl-10 pr-4 py-3 border border-slate-300 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-brand-orange/40 focus:border-brand-orange"
            />
          </div>

          {searching && (
            <p className="text-center text-sm text-slate-400 mt-4">Đang tìm kiếm...</p>
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
                      {STATUS_LABELS[s.status]}
                    </span>
                    <ChevronRight className="w-4 h-4 text-slate-400" />
                  </div>
                </button>
              ))}
            </div>
          )}

          {!searching && search.trim() && students.length === 0 && (
            <p className="text-center text-sm text-slate-400 mt-4">Không tìm thấy học sinh nào</p>
          )}

          {!search.trim() && (
            <div className="text-center mt-8">
              <GraduationCap className="w-14 h-14 mx-auto text-slate-200 mb-3" />
              <p className="text-sm text-slate-400 font-medium">Tìm kiếm học sinh để xem hồ sơ học tập</p>
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
                    {STATUS_LABELS[profile.student.status] ?? profile.student.status}
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
                Đổi học sinh
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
                  Xuất hồ sơ PDF
                </Button>
              )}
            </div>
          </div>

          {profile.loading ? (
            <div className="bg-white rounded-xl border border-slate-200 p-10 text-center text-slate-400 text-sm">
              Đang tải hồ sơ học tập...
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
                      <span className="text-xs font-semibold text-slate-600">Lớp đã học</span>
                    </div>
                    <p className="text-3xl font-bold text-indigo-600">{profile.enrollments.length}</p>
                    <p className="text-xs text-slate-400 mt-1">lớp</p>
                  </div>
                  <div className="bg-white border border-slate-200/60 rounded-xl p-4 shadow-sm">
                    <div className="flex items-center gap-2 mb-2">
                      <Award className="w-4 h-4 text-amber-500" />
                      <span className="text-xs font-semibold text-slate-600">Điểm TB</span>
                    </div>
                    <p className="text-3xl font-bold text-amber-600">{avgScore ?? "—"}</p>
                    <p className="text-xs text-slate-400 mt-1">qua {profile.allGrades.length} kỳ</p>
                  </div>
                  <div className="bg-white border border-slate-200/60 rounded-xl p-4 shadow-sm">
                    <div className="flex items-center gap-2 mb-2">
                      <BookOpen className="w-4 h-4 text-emerald-500" />
                      <span className="text-xs font-semibold text-slate-600">Nhận xét</span>
                    </div>
                    <p className="text-3xl font-bold text-emerald-600">{dailyComments.length}</p>
                    <p className="text-xs text-slate-400 mt-1">nhận xét ngày</p>
                  </div>
                  <div className="bg-white border border-slate-200/60 rounded-xl p-4 shadow-sm">
                    <div className="flex items-center gap-2 mb-2">
                      <Calendar className="w-4 h-4 text-rose-500" />
                      <span className="text-xs font-semibold text-slate-600">Điểm danh</span>
                    </div>
                    <p className="text-3xl font-bold text-rose-600">
                      {attendanceStats.total > 0
                        ? `${Math.round((attendanceStats.present / attendanceStats.total) * 100)}%`
                        : "—"}
                    </p>
                    <p className="text-xs text-slate-400 mt-1">có mặt ({attendanceStats.present}/{attendanceStats.total})</p>
                  </div>

                  {/* Danh sách lớp đã học */}
                  <div className="col-span-2 md:col-span-4 bg-white border border-slate-200/60 rounded-xl p-4 shadow-sm">
                    <h3 className="text-sm font-semibold text-slate-700 mb-3">Lịch sử ghi danh</h3>
                    {profile.enrollments.length === 0 ? (
                      <p className="text-sm text-slate-400">Chưa có lớp nào</p>
                    ) : (
                      <div className="divide-y divide-slate-100">
                        {profile.enrollments.map((e) => (
                          <div key={e.id} className="py-2 flex items-center justify-between gap-3">
                            <div>
                              <p className="text-sm font-medium text-slate-700">{e.className}</p>
                              <p className="text-xs text-slate-400">Ghi danh: {e.enrolledDate}{e.withdrawnDate ? ` · Rút: ${e.withdrawnDate}` : ""}</p>
                            </div>
                            <span className={`text-xs px-2 py-0.5 rounded-full font-semibold ${e.status === "ACTIVE" ? "bg-emerald-100 text-emerald-700" : "bg-slate-100 text-slate-500"}`}>
                              {e.status === "ACTIVE" ? "Đang học" : e.status}
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
                    <h3 className="text-sm font-semibold text-slate-700">Kết quả học kỳ</h3>
                  </div>
                  {profile.allGrades.length === 0 ? (
                    <div className="py-10 text-center text-sm text-slate-400">Chưa có điểm số nào</div>
                  ) : (
                    <table className="w-full text-sm text-left">
                      <thead className="bg-slate-50 border-b border-slate-100 text-xs text-slate-500 font-medium">
                        <tr>
                          <th className="px-4 py-2.5">Kỳ học</th>
                          <th className="px-4 py-2.5">Loại</th>
                          <th className="px-4 py-2.5">Điểm</th>
                          <th className="px-4 py-2.5">Xếp hạng</th>
                          <th className="px-4 py-2.5">Trạng thái</th>
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
                              {g.evaluationType === "MID_TERM" ? "Giữa kỳ" : "Cuối kỳ"}
                            </td>
                            <td className="px-4 py-3">
                              <span className="text-lg font-bold text-slate-800">{g.overallScore ?? "—"}</span>
                            </td>
                            <td className="px-4 py-3 text-slate-500">{g.level ?? "—"}</td>
                            <td className="px-4 py-3">
                              <span className={`text-xs px-2 py-0.5 rounded-full font-semibold ${g.status === "OFFICIAL" ? "bg-emerald-100 text-emerald-700" : "bg-slate-100 text-slate-600"}`}>
                                {COMMENT_GRADE_LABELS[g.status] ?? g.status}
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
                    <div className="bg-white rounded-xl border border-slate-200 py-10 text-center text-sm text-slate-400">Chưa có nhận xét nào</div>
                  ) : (
                    profile.allComments.slice(0, 30).map((c) => (
                      <div key={c.id} className="bg-white border border-slate-200/60 rounded-xl p-4 shadow-sm">
                        <div className="flex items-center justify-between gap-2 mb-2">
                          <div className="flex items-center gap-2">
                            <span className="text-xs text-slate-500">{c.commentDate}</span>
                            <span className={`text-xs px-2 py-0.5 rounded-full font-semibold ${c.commentType === "DAILY" ? "bg-blue-100 text-blue-700" : "bg-purple-100 text-purple-700"}`}>
                              {c.commentType === "DAILY" ? "Hàng ngày" : c.commentType === "MID_TERM" ? "Giữa kỳ" : "Cuối kỳ"}
                            </span>
                          </div>
                          <span className={`text-xs px-2 py-0.5 rounded-full font-semibold ${c.status === "APPROVED" ? "bg-emerald-100 text-emerald-700" : "bg-slate-100 text-slate-500"}`}>
                            {COMMENT_GRADE_LABELS[c.status] ?? c.status}
                          </span>
                        </div>
                        <p className="text-sm text-slate-700">{c.content || "(không có nội dung)"}</p>
                        {c.attitude && (
                          <p className="text-xs text-slate-400 mt-1">Thái độ: {c.attitude}</p>
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
                      <p className="text-xs text-emerald-600 mt-1">Có mặt</p>
                    </div>
                    <div className="bg-rose-50 border border-rose-200 rounded-xl p-4 text-center">
                      <p className="text-2xl font-bold text-rose-700">{attendanceStats.absent}</p>
                      <p className="text-xs text-rose-600 mt-1">Vắng không phép</p>
                    </div>
                    <div className="bg-amber-50 border border-amber-200 rounded-xl p-4 text-center">
                      <p className="text-2xl font-bold text-amber-700">{attendanceStats.excused}</p>
                      <p className="text-xs text-amber-600 mt-1">Vắng có phép</p>
                    </div>
                  </div>
                  <p className="text-xs text-slate-400 text-center">Hiển thị từ 150 buổi học gần nhất</p>

                  {profile.attendance.length === 0 && (
                    <div className="bg-white rounded-xl border border-slate-200 py-10 text-center text-sm text-slate-400">Chưa có dữ liệu điểm danh</div>
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
                              {entry.sessionNumber ? `Buổi ${entry.sessionNumber}` : `Buổi học #${entry.classSessionId}`} — {entry.sessionDate}
                            </span>
                          </div>
                          <span className={`text-xs px-2 py-0.5 rounded-full font-semibold ${
                            isPresent ? "bg-emerald-100 text-emerald-700" :
                            isAbsent ? "bg-rose-100 text-rose-700" :
                            "bg-amber-100 text-amber-700"
                          }`}>
                            {entry.status === "PRESENT" ? "Có mặt" :
                             entry.status === "LATE" ? "Đi muộn" :
                             entry.status === "EARLY_LEAVE" ? "Về sớm" :
                             entry.status === "ABSENT" ? "Vắng" : "Có phép"}
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
          title="Xuất hồ sơ học sinh"
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
