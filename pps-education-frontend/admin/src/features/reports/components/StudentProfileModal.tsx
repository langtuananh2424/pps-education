import React, { useMemo, useState } from "react";
import { Award, BookOpen, Calendar, CheckCircle, ChevronDown, ChevronUp, Clock, ClipboardList, GraduationCap, Users, XCircle } from "lucide-react";
import Modal from "@/components/ui/Modal";
import DatePicker from "@/components/ui/DatePicker";
import Select from "@/components/ui/Select";
import { SkillTrendSeries, useStudentProfileData } from "../hooks/useStudentProfileData";
import HomeworkAttemptDetail from "./HomeworkAttemptDetail";

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

const RESULT_STATUS_LABELS: Record<string, string> = {
  DRAFT: "Nháp",
  SUBMITTED: "Đã nộp",
  OFFICIAL: "Chính thức",
  REJECTED: "Từ chối",
  PENDING: "Chờ duyệt",
  APPROVED: "Đã duyệt",
};

/** Thang thái độ chốt lại 2026-08-12 (StudentComment.Attitude) — trước đây in thẳng giá trị enum thô (VD "GOOD") không qua nhãn. */
const ATTITUDE_LABELS: Record<string, string> = {
  WEAK: "Yếu",
  AVERAGE: "Trung bình",
  FAIR: "Khá",
  GOOD: "Tốt",
  EXCELLENT: "Xuất sắc",
};

type TabKey = "overview" | "grades" | "comments" | "attendance" | "homework";

/** Cùng 1 chiều cao cho cả 5 tab (cuộn riêng bên trong) — đổi tab không làm popup co giãn theo nội dung. */
const TAB_BODY_CLASS = "h-[620px] overflow-y-auto pr-1";

const TABS: { key: TabKey; label: string; icon: React.ReactNode }[] = [
  { key: "overview", label: "Tổng quan", icon: <GraduationCap className="w-3.5 h-3.5" /> },
  { key: "grades", label: "Điểm số", icon: <Award className="w-3.5 h-3.5" /> },
  { key: "comments", label: "Nhận xét", icon: <BookOpen className="w-3.5 h-3.5" /> },
  { key: "attendance", label: "Điểm danh", icon: <Calendar className="w-3.5 h-3.5" /> },
  { key: "homework", label: "Bài tập về nhà", icon: <ClipboardList className="w-3.5 h-3.5" /> },
];

const HOMEWORK_STATUS_LABELS: Record<string, string> = {
  IN_PROGRESS: "Đang làm",
  SUBMITTED: "Đã nộp",
  AUTO_GRADED: "Đã chấm tự động",
  FULLY_GRADED: "Đã chấm xong",
  EXPIRED: "Hết hạn",
};

/** Chuỗi màu cố định theo THỨ TỰ (không đổi theo dữ liệu) — đủ dùng cho tối đa 6 kỹ năng phổ biến (Nghe/Nói/Đọc/Viết/Ngữ pháp/Dự án). */
const SKILL_COLORS = ["#6366f1", "#f59e0b", "#10b981", "#ec4899", "#0ea5e9", "#a855f7"];

/** Biểu đồ đường 1 kỹ năng qua các kỳ — trục X là nhãn kỳ (VD "HK1 GK"), trục Y là điểm. */
function SkillTrendChart({ series, color }: { series: SkillTrendSeries; color: string }) {
  const width = 1000;
  const height = 220;
  const padding = { top: 28, right: 20, bottom: 34, left: 36 };
  const innerW = width - padding.left - padding.right;
  const innerH = height - padding.top - padding.bottom;

  const points = series.points;
  const maxScore = Math.max(...points.map((p) => p.maxScore ?? p.score), 10);

  const x = (i: number) => padding.left + (points.length <= 1 ? innerW / 2 : (i / (points.length - 1)) * innerW);
  const y = (score: number) => padding.top + innerH - (score / maxScore) * innerH;

  const path = points.map((p, i) => `${i === 0 ? "M" : "L"} ${x(i)} ${y(p.score)}`).join(" ");

  const yTicks = 4;
  const yTickValues = Array.from({ length: yTicks + 1 }, (_, i) => Math.round((maxScore / yTicks) * i * 10) / 10);

  return (
    <div className="border border-slate-200/60 rounded-lg p-4">
      <div className="flex items-center gap-2 mb-2">
        <span className="w-3 h-3 rounded-full" style={{ backgroundColor: color }} />
        <span className="text-sm font-semibold text-slate-700">{series.skillName}</span>
        <span className="text-xs text-slate-400 ml-auto">
          Gần nhất: <span className="font-bold text-slate-600">{points[points.length - 1]?.score ?? "—"}</span>
        </span>
      </div>
      <svg viewBox={`0 0 ${width} ${height}`} className="w-full h-48" role="img" aria-label={`Biểu đồ điểm ${series.skillName} qua các kỳ`}>
        {yTickValues.map((v) => (
          <g key={v}>
            <line x1={padding.left} x2={width - padding.right} y1={y(v)} y2={y(v)} stroke="#e2e8f0" strokeWidth={1} />
            <text x={padding.left - 8} y={y(v)} textAnchor="end" dominantBaseline="middle" className="fill-slate-400" fontSize={13}>
              {v}
            </text>
          </g>
        ))}
        <path d={path} fill="none" stroke={color} strokeWidth={2.5} strokeLinecap="round" strokeLinejoin="round" />
        {points.map((p, i) => (
          <text key={i} x={x(i)} y={y(p.score) - 12} textAnchor="middle" className="fill-slate-700 font-semibold" fontSize={13}>
            {p.score}
          </text>
        ))}
        {points.map((p, i) => (
          <circle key={i} cx={x(i)} cy={y(p.score)} r={4.5} fill={color} stroke="white" strokeWidth={1.5}>
            <title>{`${p.termLabel}: ${p.score}${p.maxScore ? `/${p.maxScore}` : ""}`}</title>
          </circle>
        ))}
        {points.map((p, i) => (
          <text key={i} x={x(i)} y={height - 12} textAnchor="middle" className="fill-slate-500" fontSize={13}>
            {p.termLabel}
          </text>
        ))}
      </svg>
    </div>
  );
}

export default function StudentProfileModal({ studentId, onClose }: { studentId: number; onClose: () => void }) {
  const profile = useStudentProfileData(studentId);
  const [activeTab, setActiveTab] = useState<TabKey>("overview");

  // Lọc theo Năm học / Kỳ học ở tab Điểm số.
  const [yearFilter, setYearFilter] = useState<string>("ALL");
  const [termFilter, setTermFilter] = useState<string>("ALL");

  const academicYears = useMemo(
    () => Array.from(new Set(profile.allGrades.map((g) => g.academicYear).filter((y): y is string => !!y))).sort(),
    [profile.allGrades]
  );
  const termsInScope = useMemo(() => {
    const map = new Map<number, { id: number; name: string; year: string | null }>();
    profile.allGrades.forEach((g) => {
      if (!map.has(g.academicTermId)) map.set(g.academicTermId, { id: g.academicTermId, name: g.academicTermName, year: g.academicYear });
    });
    return Array.from(map.values());
  }, [profile.allGrades]);
  const termOptions = useMemo(
    () => termsInScope.filter((t) => yearFilter === "ALL" || t.year === yearFilter),
    [termsInScope, yearFilter]
  );

  const filteredGrades = useMemo(
    () =>
      profile.allGrades.filter(
        (g) => (yearFilter === "ALL" || g.academicYear === yearFilter) && (termFilter === "ALL" || String(g.academicTermId) === termFilter)
      ),
    [profile.allGrades, yearFilter, termFilter]
  );
  const filteredSkillTrends = useMemo(
    () =>
      profile.skillTrends
        .map((s) => ({
          ...s,
          points: s.points.filter(
            (p) => (yearFilter === "ALL" || p.academicYear === yearFilter) && (termFilter === "ALL" || String(p.academicTermId) === termFilter)
          ),
        }))
        .filter((s) => s.points.length > 0),
    [profile.skillTrends, yearFilter, termFilter]
  );

  // So sánh điểm từng kỹ năng giữa 2 kỳ cụ thể (độc lập với bộ lọc Năm học/Kỳ học ở trên -- luôn cho chọn từ TOÀN BỘ lịch sử).
  // BE đã trả skillScores theo đúng thứ tự thời gian (ORDER BY academicTerm.startDate) -- lấy theo thứ tự
  // xuất hiện đầu tiên là đủ, không cần tự sort lại.
  const comparableTerms = useMemo(() => {
    const seen = new Set<string>();
    profile.skillTrends.forEach((s) => s.points.forEach((p) => seen.add(p.termLabel)));
    return Array.from(seen);
  }, [profile.skillTrends]);
  const [compareA, setCompareA] = useState<string>("");
  const [compareB, setCompareB] = useState<string>("");
  const overallByLabel = useMemo(() => {
    const map = new Map<string, number | null>();
    profile.allGrades.forEach((g) => {
      map.set(`${g.academicTermName} ${g.evaluationType === "MID_TERM" ? "GK" : "CK"}`, g.overallScore);
    });
    return map;
  }, [profile.allGrades]);
  const comparisonRows = useMemo(() => {
    if (!compareA || !compareB) return [];
    const overallRow = {
      skillName: "Overall (Tổng kết)",
      a: overallByLabel.get(compareA) ?? null,
      b: overallByLabel.get(compareB) ?? null,
      isOverall: true,
    };
    const skillRows = profile.skillTrends
      .map((s) => ({
        skillName: s.skillName,
        a: s.points.find((p) => p.termLabel === compareA)?.score ?? null,
        b: s.points.find((p) => p.termLabel === compareB)?.score ?? null,
        isOverall: false,
      }))
      .filter((r) => r.a !== null || r.b !== null);
    return overallRow.a === null && overallRow.b === null ? skillRows : [overallRow, ...skillRows];
  }, [profile.skillTrends, overallByLabel, compareA, compareB]);

  const attendanceStats = useMemo(() => {
    const present = profile.attendance.filter((m) => m.status === "PRESENT" || m.status === "LATE" || m.status === "EARLY_LEAVE").length;
    const absent = profile.attendance.filter((m) => m.status === "ABSENT").length;
    const excused = profile.attendance.filter((m) => m.status === "EXCUSED").length;
    return { total: profile.attendance.length, present, absent, excused };
  }, [profile.attendance]);

  const avgScore = useMemo(() => {
    const numeric = profile.allGrades.filter((g) => g.overallScore !== null);
    if (!numeric.length) return null;
    return (numeric.reduce((s, g) => s + (g.overallScore as number), 0) / numeric.length).toFixed(2);
  }, [profile.allGrades]);

  const dailyComments = profile.allComments.filter((c) => c.commentType === "DAILY");

  // Lọc ở tab Nhận xét: năm học / kỳ / buổi / khoảng ngày.
  const [commentYearFilter, setCommentYearFilter] = useState<string>("ALL");
  const [commentTermFilter, setCommentTermFilter] = useState<string>("ALL");
  const [commentSessionFilter, setCommentSessionFilter] = useState<string>("ALL");
  const [commentDateFrom, setCommentDateFrom] = useState<string>("");
  const [commentDateTo, setCommentDateTo] = useState<string>("");

  const commentYears = useMemo(
    () => Array.from(new Set(profile.allComments.map((c) => c.academicYear).filter((y): y is string => !!y))).sort(),
    [profile.allComments]
  );
  const commentTermsInScope = useMemo(() => {
    const map = new Map<string, { name: string; year: string | null }>();
    profile.allComments.forEach((c) => {
      if (c.academicTermName && !map.has(c.academicTermName)) map.set(c.academicTermName, { name: c.academicTermName, year: c.academicYear });
    });
    return Array.from(map.values());
  }, [profile.allComments]);
  const commentTermOptions = useMemo(
    () => commentTermsInScope.filter((t) => commentYearFilter === "ALL" || t.year === commentYearFilter),
    [commentTermsInScope, commentYearFilter]
  );
  const commentSessionLabel = (c: { sessionNumber: number | null; sessionDate: string | null }): string | null =>
    c.sessionNumber ? `Buổi ${c.sessionNumber} — ${c.sessionDate}` : null;
  const commentSessionOptions = useMemo(
    () => Array.from(new Set(profile.allComments.map(commentSessionLabel).filter((s): s is string => !!s))),
    [profile.allComments]
  );
  const filteredComments = useMemo(
    () =>
      profile.allComments.filter(
        (c) =>
          (commentYearFilter === "ALL" || c.academicYear === commentYearFilter) &&
          (commentTermFilter === "ALL" || c.academicTermName === commentTermFilter) &&
          (commentSessionFilter === "ALL" || commentSessionLabel(c) === commentSessionFilter) &&
          (!commentDateFrom || c.commentDate >= commentDateFrom) &&
          (!commentDateTo || c.commentDate <= commentDateTo)
      ),
    [profile.allComments, commentYearFilter, commentTermFilter, commentSessionFilter, commentDateFrom, commentDateTo]
  );
  const hasCommentFilter =
    commentYearFilter !== "ALL" || commentTermFilter !== "ALL" || commentSessionFilter !== "ALL" || !!commentDateFrom || !!commentDateTo;
  const clearCommentFilters = () => {
    setCommentYearFilter("ALL");
    setCommentTermFilter("ALL");
    setCommentSessionFilter("ALL");
    setCommentDateFrom("");
    setCommentDateTo("");
  };

  // Lọc ở tab Điểm danh: năm học / kỳ / khoảng ngày.
  const [attYearFilter, setAttYearFilter] = useState<string>("ALL");
  const [attTermFilter, setAttTermFilter] = useState<string>("ALL");
  const [attDateFrom, setAttDateFrom] = useState<string>("");
  const [attDateTo, setAttDateTo] = useState<string>("");

  const attYears = useMemo(
    () => Array.from(new Set(profile.attendance.map((s) => s.academicYear).filter((y): y is string => !!y))).sort(),
    [profile.attendance]
  );
  const attTermsInScope = useMemo(() => {
    const map = new Map<string, { name: string; year: string | null }>();
    profile.attendance.forEach((s) => {
      if (s.academicTermName && !map.has(s.academicTermName)) map.set(s.academicTermName, { name: s.academicTermName, year: s.academicYear });
    });
    return Array.from(map.values());
  }, [profile.attendance]);
  const attTermOptions = useMemo(
    () => attTermsInScope.filter((t) => attYearFilter === "ALL" || t.year === attYearFilter),
    [attTermsInScope, attYearFilter]
  );
  const filteredAttendance = useMemo(
    () =>
      profile.attendance.filter(
        (s) =>
          (attYearFilter === "ALL" || s.academicYear === attYearFilter) &&
          (attTermFilter === "ALL" || s.academicTermName === attTermFilter) &&
          (!attDateFrom || s.sessionDate >= attDateFrom) &&
          (!attDateTo || s.sessionDate <= attDateTo)
      ),
    [profile.attendance, attYearFilter, attTermFilter, attDateFrom, attDateTo]
  );
  const filteredAttendanceStats = useMemo(() => {
    const present = filteredAttendance.filter((m) => m.status === "PRESENT" || m.status === "LATE" || m.status === "EARLY_LEAVE").length;
    const absent = filteredAttendance.filter((m) => m.status === "ABSENT").length;
    const excused = filteredAttendance.filter((m) => m.status === "EXCUSED").length;
    return { total: filteredAttendance.length, present, absent, excused };
  }, [filteredAttendance]);
  const hasAttFilter = attYearFilter !== "ALL" || attTermFilter !== "ALL" || !!attDateFrom || !!attDateTo;
  const clearAttFilters = () => {
    setAttYearFilter("ALL");
    setAttTermFilter("ALL");
    setAttDateFrom("");
    setAttDateTo("");
  };

  // Lọc ở tab Bài tập về nhà: lớp / kết quả đạt-chưa đạt.
  const [hwClassFilter, setHwClassFilter] = useState<string>("ALL");
  const [hwResultFilter, setHwResultFilter] = useState<string>("ALL");
  const [expandedHomeworkAttemptId, setExpandedHomeworkAttemptId] = useState<number | null>(null);

  const hwClasses = useMemo(
    () => Array.from(new Set(profile.homeworkResults.map((h) => h.className))).sort(),
    [profile.homeworkResults]
  );
  const filteredHomework = useMemo(
    () =>
      profile.homeworkResults.filter(
        (h) =>
          (hwClassFilter === "ALL" || h.className === hwClassFilter) &&
          (hwResultFilter === "ALL" ||
            (hwResultFilter === "PASSED" && h.passed === true) ||
            (hwResultFilter === "FAILED" && h.passed === false) ||
            (hwResultFilter === "UNGRADED" && h.passed === null))
      ),
    [profile.homeworkResults, hwClassFilter, hwResultFilter]
  );
  const hasHwFilter = hwClassFilter !== "ALL" || hwResultFilter !== "ALL";
  const clearHwFilters = () => {
    setHwClassFilter("ALL");
    setHwResultFilter("ALL");
  };

  const student = profile.student;

  return (
    <Modal
      open
      onClose={onClose}
      title="Hồ sơ học tập"
      description={student ? `${student.fullName} (${student.studentCode})` : undefined}
      size="xl"
    >
      {!student || profile.loading ? (
        <div className="py-16 text-center text-sm text-slate-400">Đang tải hồ sơ học tập...</div>
      ) : profile.error ? (
        <div className="py-16 text-center text-sm text-rose-500">{profile.error}</div>
      ) : (
        <div className="space-y-4">
          {/* Header học sinh */}
          <div className="bg-gradient-to-r from-violet-500 to-purple-700 rounded-xl p-5 text-white flex flex-wrap items-center gap-4">
            {student.portraitUrl ? (
              <img src={student.portraitUrl} alt={student.fullName} className="w-12 h-12 rounded-full object-cover ring-2 ring-white/50" />
            ) : (
              <div className="w-12 h-12 rounded-full bg-white/20 flex items-center justify-center text-xl font-bold">
                {student.fullName.charAt(0)}
              </div>
            )}
            <div>
              <h2 className="text-base font-bold">{student.fullName}</h2>
              <p className="text-xs opacity-75">{student.studentCode} · {student.primarySiteName ?? "—"}</p>
              <span className={`text-xs px-2 py-0.5 rounded-full font-semibold mt-1 inline-block ${STATUS_COLORS[student.status] ?? ""}`}>
                {STATUS_LABELS[student.status] ?? student.status}
              </span>
            </div>
          </div>

          {/* Tabs */}
          <div className="flex gap-1 bg-slate-100 rounded-xl p-1">
            {TABS.map((tab) => (
              <button
                key={tab.key}
                onClick={() => setActiveTab(tab.key)}
                className={`flex-1 flex items-center justify-center gap-1.5 py-2 px-3 rounded-lg text-xs font-semibold transition-all ${
                  activeTab === tab.key ? "bg-white text-slate-800 shadow-sm" : "text-slate-500 hover:text-slate-700"
                }`}
              >
                {tab.icon}
                {tab.label}
              </button>
            ))}
          </div>

          {/* Tổng quan */}
          {activeTab === "overview" && (
            <div className={`${TAB_BODY_CLASS} grid grid-cols-2 md:grid-cols-4 gap-4 content-start`}>
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
                  {attendanceStats.total > 0 ? `${Math.round((attendanceStats.present / attendanceStats.total) * 100)}%` : "—"}
                </p>
                <p className="text-xs text-slate-400 mt-1">có mặt ({attendanceStats.present}/{attendanceStats.total})</p>
              </div>

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
                          <p className="text-xs text-slate-400">
                            Ghi danh: {e.enrolledDate}{e.withdrawnDate ? ` · Rút: ${e.withdrawnDate}` : ""}
                          </p>
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

          {/* Điểm số -- lọc theo năm học/kỳ, kết quả tổng kết + nhận xét kỳ, biểu đồ đường từng kỹ năng, so sánh giữa 2 kỳ */}
          {activeTab === "grades" && (
            <div className={`${TAB_BODY_CLASS} space-y-4`}>
              {profile.allGrades.length > 0 && (
                <div className="bg-white border border-slate-200/60 rounded-xl p-3 shadow-sm flex flex-wrap items-center gap-3">
                  <span className="text-xs font-semibold text-slate-500">Lọc:</span>
                  <Select
                    value={yearFilter}
                    onChange={(e) => { setYearFilter(e.target.value); setTermFilter("ALL"); }}
                    className="border border-slate-300 rounded-lg text-xs px-2.5 py-1.5 focus:outline-none focus:ring-2 focus:ring-brand-orange/40"
                  >
                    <option value="ALL">Tất cả năm học</option>
                    {academicYears.map((y) => (
                      <option key={y} value={y}>{y}</option>
                    ))}
                  </Select>
                  <Select
                    value={termFilter}
                    onChange={(e) => setTermFilter(e.target.value)}
                    className="border border-slate-300 rounded-lg text-xs px-2.5 py-1.5 focus:outline-none focus:ring-2 focus:ring-brand-orange/40"
                  >
                    <option value="ALL">Tất cả kỳ học</option>
                    {termOptions.map((t) => (
                      <option key={t.id} value={t.id}>{t.name}</option>
                    ))}
                  </Select>
                  {(yearFilter !== "ALL" || termFilter !== "ALL") && (
                    <button
                      type="button"
                      onClick={() => { setYearFilter("ALL"); setTermFilter("ALL"); }}
                      className="text-xs text-brand-orange font-semibold hover:underline"
                    >
                      Xoá lọc
                    </button>
                  )}
                </div>
              )}

              <div className="bg-white border border-slate-200/60 rounded-xl overflow-hidden shadow-sm">
                <div className="px-4 py-3 border-b border-slate-100">
                  <h3 className="text-sm font-semibold text-slate-700">Kết quả học kỳ</h3>
                </div>
                {filteredGrades.length === 0 ? (
                  <div className="py-10 text-center text-sm text-slate-400">
                    {profile.allGrades.length === 0 ? "Chưa có điểm số nào" : "Không có kết quả khớp bộ lọc đang chọn"}
                  </div>
                ) : (
                  <table className="w-full text-sm text-left">
                    <thead className="bg-slate-50 border-b border-slate-100 text-xs text-slate-500 font-medium">
                      <tr>
                        <th className="px-4 py-2.5">Năm học</th>
                        <th className="px-4 py-2.5">Kỳ học</th>
                        <th className="px-4 py-2.5">Loại</th>
                        <th className="px-4 py-2.5">Điểm</th>
                        <th className="px-4 py-2.5">Xếp hạng</th>
                        <th className="px-4 py-2.5">Nhận xét</th>
                        <th className="px-4 py-2.5">Trạng thái</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-slate-100">
                      {filteredGrades.map((g) => (
                        <tr key={g.id} className="hover:bg-slate-50/50">
                          <td className="px-4 py-3 text-slate-500 text-xs">{g.academicYear ?? "—"}</td>
                          <td className="px-4 py-3 text-slate-700">
                            {g.academicTermName}
                            {g.className && <span className="block text-[11px] text-slate-400">{g.className}</span>}
                          </td>
                          <td className="px-4 py-3 text-slate-500 text-xs">{g.evaluationType === "MID_TERM" ? "Giữa kỳ" : "Cuối kỳ"}</td>
                          <td className="px-4 py-3"><span className="text-lg font-bold text-slate-800">{g.overallScore ?? "—"}</span></td>
                          <td className="px-4 py-3 text-slate-500">{g.level ?? "—"}</td>
                          <td className="px-4 py-3 text-slate-500 text-xs max-w-xs">{g.comment || "—"}</td>
                          <td className="px-4 py-3">
                            <span className={`text-xs px-2 py-0.5 rounded-full font-semibold ${g.status === "OFFICIAL" ? "bg-emerald-100 text-emerald-700" : "bg-slate-100 text-slate-600"}`}>
                              {RESULT_STATUS_LABELS[g.status] ?? g.status}
                            </span>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                )}
              </div>

              <div className="bg-white border border-slate-200/60 rounded-xl p-4 shadow-sm">
                <h3 className="text-sm font-semibold text-slate-700 mb-3">Biến động điểm theo từng kỹ năng</h3>
                {filteredSkillTrends.length === 0 ? (
                  <p className="text-sm text-slate-400 text-center py-6">
                    {profile.skillTrends.length === 0 ? "Chưa có điểm thành phần theo kỹ năng" : "Không có điểm kỹ năng khớp bộ lọc đang chọn"}
                  </p>
                ) : (
                  <div className="flex flex-col gap-4">
                    {filteredSkillTrends.map((series, i) => (
                      <SkillTrendChart key={series.skillCode} series={series} color={SKILL_COLORS[i % SKILL_COLORS.length]} />
                    ))}
                  </div>
                )}
              </div>

              {comparableTerms.length >= 2 && (
                <div className="bg-white border border-slate-200/60 rounded-xl p-4 shadow-sm">
                  <h3 className="text-sm font-semibold text-slate-700 mb-3">So sánh điểm giữa 2 kỳ</h3>
                  <div className="flex flex-wrap items-center gap-3 mb-3">
                    <Select
                      value={compareA}
                      onChange={(e) => setCompareA(e.target.value)}
                      className="border border-slate-300 rounded-lg text-xs px-2.5 py-1.5 focus:outline-none focus:ring-2 focus:ring-brand-orange/40"
                    >
                      <option value="">-- Chọn kỳ A --</option>
                      {comparableTerms.map((t) => (
                        <option key={t} value={t}>{t}</option>
                      ))}
                    </Select>
                    <span className="text-xs text-slate-400">so với</span>
                    <Select
                      value={compareB}
                      onChange={(e) => setCompareB(e.target.value)}
                      className="border border-slate-300 rounded-lg text-xs px-2.5 py-1.5 focus:outline-none focus:ring-2 focus:ring-brand-orange/40"
                    >
                      <option value="">-- Chọn kỳ B --</option>
                      {comparableTerms.map((t) => (
                        <option key={t} value={t}>{t}</option>
                      ))}
                    </Select>
                  </div>
                  {!compareA || !compareB ? (
                    <p className="text-xs text-slate-400">Chọn 2 kỳ để so sánh điểm từng kỹ năng.</p>
                  ) : comparisonRows.length === 0 ? (
                    <p className="text-xs text-slate-400">Không có điểm kỹ năng nào ở 1 trong 2 kỳ đã chọn.</p>
                  ) : (
                    <table className="w-full text-sm text-left">
                      <thead className="bg-slate-50 border-b border-slate-100 text-xs text-slate-500 font-medium">
                        <tr>
                          <th className="px-3 py-2">Kỹ năng</th>
                          <th className="px-3 py-2 text-right">{compareA}</th>
                          <th className="px-3 py-2 text-right">{compareB}</th>
                          <th className="px-3 py-2 text-right">Chênh lệch</th>
                        </tr>
                      </thead>
                      <tbody className="divide-y divide-slate-100">
                        {comparisonRows.map((r) => {
                          const delta = r.a !== null && r.b !== null ? r.b - r.a : null;
                          return (
                            <tr key={r.skillName} className={r.isOverall ? "bg-orange-50/60" : undefined}>
                              <td className={`px-3 py-2 ${r.isOverall ? "font-bold text-slate-800" : "font-medium text-slate-700"}`}>{r.skillName}</td>
                              <td className={`px-3 py-2 text-right ${r.isOverall ? "font-bold text-slate-800" : "text-slate-600"}`}>{r.a ?? "—"}</td>
                              <td className={`px-3 py-2 text-right ${r.isOverall ? "font-bold text-slate-800" : "text-slate-600"}`}>{r.b ?? "—"}</td>
                              <td className="px-3 py-2 text-right">
                                {delta === null ? (
                                  <span className="text-slate-400">—</span>
                                ) : (
                                  <span className={`font-semibold ${delta > 0 ? "text-emerald-600" : delta < 0 ? "text-rose-600" : "text-slate-400"}`}>
                                    {delta > 0 ? `+${delta.toFixed(1)}` : delta.toFixed(1)}
                                  </span>
                                )}
                              </td>
                            </tr>
                          );
                        })}
                      </tbody>
                    </table>
                  )}
                </div>
              )}
            </div>
          )}

          {/* Nhận xét -- lọc theo loại/buổi/kỳ/năm học/khoảng ngày */}
          {activeTab === "comments" && (
            <div className={`${TAB_BODY_CLASS} space-y-3`}>
              {profile.allComments.length > 0 && (
                <div className="bg-white border border-slate-200/60 rounded-xl p-3 shadow-sm flex flex-wrap items-center gap-3 sticky top-0 z-10">
                  <span className="text-xs font-semibold text-slate-500">Lọc:</span>
                  <Select
                    value={commentYearFilter}
                    onChange={(e) => { setCommentYearFilter(e.target.value); setCommentTermFilter("ALL"); }}
                    className="border border-slate-300 rounded-lg text-xs px-2.5 py-1.5 focus:outline-none focus:ring-2 focus:ring-brand-orange/40"
                  >
                    <option value="ALL">Tất cả</option>
                    {commentYears.map((y) => (
                      <option key={y} value={y}>{y}</option>
                    ))}
                  </Select>
                  <Select
                    value={commentTermFilter}
                    onChange={(e) => setCommentTermFilter(e.target.value)}
                    className="border border-slate-300 rounded-lg text-xs px-2.5 py-1.5 focus:outline-none focus:ring-2 focus:ring-brand-orange/40"
                  >
                    <option value="ALL">Tất cả</option>
                    {commentTermOptions.map((t) => (
                      <option key={t.name} value={t.name}>{t.name}</option>
                    ))}
                  </Select>
                  {commentSessionOptions.length > 0 && (
                    <Select
                      value={commentSessionFilter}
                      onChange={(e) => setCommentSessionFilter(e.target.value)}
                      className="border border-slate-300 rounded-lg text-xs px-2.5 py-1.5 focus:outline-none focus:ring-2 focus:ring-brand-orange/40"
                    >
                      <option value="ALL">Tất cả</option>
                      {commentSessionOptions.map((s) => (
                        <option key={s} value={s}>{s}</option>
                      ))}
                    </Select>
                  )}
                  <div className="flex items-center gap-1.5">
                    <div className="w-32">
                      <DatePicker value={commentDateFrom} onChange={setCommentDateFrom} max={commentDateTo || undefined} placeholder="Từ ngày" />
                    </div>
                    <span className="text-xs text-slate-400">—</span>
                    <div className="w-32">
                      <DatePicker value={commentDateTo} onChange={setCommentDateTo} min={commentDateFrom || undefined} placeholder="Đến ngày" />
                    </div>
                  </div>
                  {hasCommentFilter && (
                    <button type="button" onClick={clearCommentFilters} className="text-xs text-brand-orange font-semibold hover:underline">
                      Xoá lọc
                    </button>
                  )}
                  <span className="text-xs text-slate-400 ml-auto">{filteredComments.length} nhận xét</span>
                </div>
              )}

              {filteredComments.length === 0 ? (
                <div className="bg-white rounded-xl border border-slate-200 py-10 text-center text-sm text-slate-400">
                  {profile.allComments.length === 0 ? "Chưa có nhận xét nào" : "Không có nhận xét nào khớp bộ lọc đang chọn"}
                </div>
              ) : (
                filteredComments.slice(0, 50).map((c) => (
                  <div key={c.id} className="bg-white border border-slate-200/60 rounded-xl p-4 shadow-sm">
                    <div className="flex items-center justify-between gap-2 mb-2">
                      <div className="flex items-center gap-2 flex-wrap">
                        <span className="text-xs text-slate-500">{c.commentDate}</span>
                        <span className="text-xs px-2 py-0.5 rounded-full font-semibold bg-blue-100 text-blue-700">Hàng ngày</span>
                        {commentSessionLabel(c) && <span className="text-xs text-slate-400">{commentSessionLabel(c)}</span>}
                        {c.academicTermName && <span className="text-xs text-slate-400">{c.academicTermName}{c.academicYear ? ` · ${c.academicYear}` : ""}</span>}
                      </div>
                      <span className={`text-xs px-2 py-0.5 rounded-full font-semibold ${c.status === "APPROVED" ? "bg-emerald-100 text-emerald-700" : "bg-slate-100 text-slate-500"}`}>
                        {RESULT_STATUS_LABELS[c.status] ?? c.status}
                      </span>
                    </div>
                    <p className="text-sm text-slate-700">{c.content || "(không có nội dung)"}</p>
                    {c.attitude && <p className="text-xs text-slate-400 mt-1">Thái độ: {ATTITUDE_LABELS[c.attitude] ?? c.attitude}</p>}
                  </div>
                ))
              )}
            </div>
          )}

          {/* Điểm danh -- lọc theo năm học/kỳ/khoảng ngày */}
          {activeTab === "attendance" && (
            <div className={`${TAB_BODY_CLASS} space-y-4`}>
              {profile.attendance.length > 0 && (
                <div className="bg-white border border-slate-200/60 rounded-xl p-3 shadow-sm flex flex-wrap items-center gap-3 sticky top-0 z-10">
                  <span className="text-xs font-semibold text-slate-500">Lọc:</span>
                  <Select
                    value={attYearFilter}
                    onChange={(e) => { setAttYearFilter(e.target.value); setAttTermFilter("ALL"); }}
                    className="border border-slate-300 rounded-lg text-xs px-2.5 py-1.5 focus:outline-none focus:ring-2 focus:ring-brand-orange/40"
                  >
                    <option value="ALL">Tất cả</option>
                    {attYears.map((y) => (
                      <option key={y} value={y}>{y}</option>
                    ))}
                  </Select>
                  <Select
                    value={attTermFilter}
                    onChange={(e) => setAttTermFilter(e.target.value)}
                    className="border border-slate-300 rounded-lg text-xs px-2.5 py-1.5 focus:outline-none focus:ring-2 focus:ring-brand-orange/40"
                  >
                    <option value="ALL">Tất cả</option>
                    {attTermOptions.map((t) => (
                      <option key={t.name} value={t.name}>{t.name}</option>
                    ))}
                  </Select>
                  <div className="flex items-center gap-1.5">
                    <div className="w-32">
                      <DatePicker value={attDateFrom} onChange={setAttDateFrom} max={attDateTo || undefined} placeholder="Từ ngày" />
                    </div>
                    <span className="text-xs text-slate-400">—</span>
                    <div className="w-32">
                      <DatePicker value={attDateTo} onChange={setAttDateTo} min={attDateFrom || undefined} placeholder="Đến ngày" />
                    </div>
                  </div>
                  {hasAttFilter && (
                    <button type="button" onClick={clearAttFilters} className="text-xs text-brand-orange font-semibold hover:underline">
                      Xoá lọc
                    </button>
                  )}
                  <span className="text-xs text-slate-400 ml-auto">{filteredAttendance.length}/{profile.attendance.length} buổi (tối đa 150 buổi gần nhất)</span>
                </div>
              )}

              <div className="grid grid-cols-3 gap-4">
                <div className="bg-emerald-50 border border-emerald-200 rounded-xl p-4 text-center">
                  <p className="text-2xl font-bold text-emerald-700">{filteredAttendanceStats.present}</p>
                  <p className="text-xs text-emerald-600 mt-1">Có mặt</p>
                </div>
                <div className="bg-rose-50 border border-rose-200 rounded-xl p-4 text-center">
                  <p className="text-2xl font-bold text-rose-700">{filteredAttendanceStats.absent}</p>
                  <p className="text-xs text-rose-600 mt-1">Vắng không phép</p>
                </div>
                <div className="bg-amber-50 border border-amber-200 rounded-xl p-4 text-center">
                  <p className="text-2xl font-bold text-amber-700">{filteredAttendanceStats.excused}</p>
                  <p className="text-xs text-amber-600 mt-1">Vắng có phép</p>
                </div>
              </div>

              {filteredAttendance.length === 0 ? (
                <div className="bg-white rounded-xl border border-slate-200 py-10 text-center text-sm text-slate-400">
                  {profile.attendance.length === 0 ? "Chưa có dữ liệu điểm danh" : "Không có buổi học nào khớp bộ lọc đang chọn"}
                </div>
              ) : (
                <div className="space-y-2">
                  {filteredAttendance.map((entry) => {
                    const isPresent = entry.status === "PRESENT" || entry.status === "LATE" || entry.status === "EARLY_LEAVE";
                    const isAbsent = entry.status === "ABSENT";
                    return (
                      <div key={entry.id} className="bg-white border border-slate-200/60 rounded-lg px-4 py-2.5 flex items-center justify-between gap-3">
                        <div className="flex items-center gap-3">
                          {isPresent ? <CheckCircle className="w-4 h-4 text-emerald-500" /> : isAbsent ? <XCircle className="w-4 h-4 text-rose-500" /> : <Clock className="w-4 h-4 text-amber-500" />}
                          <div>
                            <span className="text-sm text-slate-700">
                              {entry.sessionNumber ? `Buổi ${entry.sessionNumber}` : `Buổi học #${entry.classSessionId}`}
                              {` — ${entry.sessionDate}`}
                            </span>
                            {(entry.className || entry.academicTermName) && (
                              <p className="text-[11px] text-slate-400">
                                {[entry.className, entry.academicTermName, entry.academicYear].filter(Boolean).join(" · ")}
                              </p>
                            )}
                          </div>
                        </div>
                        <span
                          className={`text-xs px-2 py-0.5 rounded-full font-semibold shrink-0 ${
                            isPresent ? "bg-emerald-100 text-emerald-700" : isAbsent ? "bg-rose-100 text-rose-700" : "bg-amber-100 text-amber-700"
                          }`}
                        >
                          {entry.status === "PRESENT" ? "Có mặt" : entry.status === "LATE" ? "Đi muộn" : entry.status === "EARLY_LEAVE" ? "Về sớm" : entry.status === "ABSENT" ? "Vắng" : "Có phép"}
                        </span>
                      </div>
                    );
                  })}
                </div>
              )}
            </div>
          )}

          {/* Bài tập về nhà -- lọc theo lớp/kết quả, xem điểm và chi tiết câu sai từng lượt làm */}
          {activeTab === "homework" && (
            <div className={`${TAB_BODY_CLASS} space-y-3`}>
              {profile.homeworkResults.length > 0 && (
                <div className="bg-white border border-slate-200/60 rounded-xl p-3 shadow-sm flex flex-wrap items-center gap-3 sticky top-0 z-10">
                  <span className="text-xs font-semibold text-slate-500">Lọc:</span>
                  <Select
                    value={hwClassFilter}
                    onChange={(e) => setHwClassFilter(e.target.value)}
                    className="border border-slate-300 rounded-lg text-xs px-2.5 py-1.5 focus:outline-none focus:ring-2 focus:ring-brand-orange/40"
                  >
                    <option value="ALL">Tất cả lớp</option>
                    {hwClasses.map((c) => (
                      <option key={c} value={c}>{c}</option>
                    ))}
                  </Select>
                  <Select
                    value={hwResultFilter}
                    onChange={(e) => setHwResultFilter(e.target.value)}
                    className="border border-slate-300 rounded-lg text-xs px-2.5 py-1.5 focus:outline-none focus:ring-2 focus:ring-brand-orange/40"
                  >
                    <option value="ALL">Tất cả kết quả</option>
                    <option value="PASSED">Đạt</option>
                    <option value="FAILED">Chưa đạt</option>
                    <option value="UNGRADED">Chưa chấm</option>
                  </Select>
                  {hasHwFilter && (
                    <button type="button" onClick={clearHwFilters} className="text-xs text-brand-orange font-semibold hover:underline">
                      Xoá lọc
                    </button>
                  )}
                  <span className="text-xs text-slate-400 ml-auto">{filteredHomework.length} bài</span>
                </div>
              )}

              {filteredHomework.length === 0 ? (
                <div className="bg-white rounded-xl border border-slate-200 py-10 text-center text-sm text-slate-400">
                  {profile.homeworkResults.length === 0 ? "Chưa có bài tập về nhà nào được giao" : "Không có bài nào khớp bộ lọc đang chọn"}
                </div>
              ) : (
                <div className="space-y-2">
                  {filteredHomework.map((h) => {
                    const isExpanded = expandedHomeworkAttemptId === h.attemptId;
                    return (
                      <div key={h.attemptId} className="bg-white border border-slate-200/60 rounded-xl shadow-sm overflow-hidden">
                        <button
                          type="button"
                          onClick={() => setExpandedHomeworkAttemptId(isExpanded ? null : h.attemptId)}
                          className="w-full text-left px-4 py-3 flex items-center justify-between gap-3 hover:bg-slate-50/50"
                        >
                          <div>
                            <p className="text-sm font-medium text-slate-700">{h.exerciseTitle}</p>
                            <p className="text-[11px] text-slate-400">
                              {h.className}
                              {h.attemptNumber > 1 ? ` · Lượt ${h.attemptNumber}` : ""}
                              {h.submittedAt ? ` · Nộp: ${new Date(h.submittedAt).toLocaleString("vi-VN")}` : " · Chưa nộp"}
                            </p>
                          </div>
                          <div className="flex items-center gap-2 shrink-0">
                            <span className="text-sm font-bold text-slate-800">
                              {h.totalScore ?? "—"}/{h.totalPoints}
                              {h.percentage !== null && <span className="text-xs text-slate-400 font-normal"> ({h.percentage}%)</span>}
                            </span>
                            <span
                              className={`text-xs px-2 py-0.5 rounded-full font-semibold ${
                                h.passed === true
                                  ? "bg-emerald-100 text-emerald-700"
                                  : h.passed === false
                                  ? "bg-rose-100 text-rose-700"
                                  : "bg-slate-100 text-slate-500"
                              }`}
                            >
                              {h.passed === true ? "Đạt" : h.passed === false ? "Chưa đạt" : HOMEWORK_STATUS_LABELS[h.status] ?? h.status}
                            </span>
                            {isExpanded ? <ChevronUp className="w-4 h-4 text-slate-400" /> : <ChevronDown className="w-4 h-4 text-slate-400" />}
                          </div>
                        </button>
                        {isExpanded && (
                          <div className="px-4 pb-4 border-t border-slate-100 pt-3">
                            <HomeworkAttemptDetail attemptId={h.attemptId} exerciseId={h.exerciseId} />
                          </div>
                        )}
                      </div>
                    );
                  })}
                </div>
              )}
            </div>
          )}
        </div>
      )}
    </Modal>
  );
}
