import React, { useEffect, useState } from "react";
import { Calendar, ClipboardList, CreditCard, FolderOpen, Home, LogOut, Award, NotebookPen, School, Users, Menu, X } from "lucide-react";
import { ApiError } from "@/lib/apiClient";
import { useApp } from "@/context/AppContext";
import { ChildResponse, getMyStudentProfile, listClassOptions, listMyChildren, PortalClassOptionResponse } from "../api";
import HomeTab from "../components/HomeTab";
import PortalDropdown from "../components/PortalDropdown";
import ScheduleTab from "../components/ScheduleTab";
import StudentScheduleTab from "../components/StudentScheduleTab";
import AssignmentsTab from "../components/AssignmentsTab";
import ParentHomeworkProgressTab from "../components/ParentHomeworkProgressTab";
import NotificationBell from "../components/NotificationBell";
import GradesTab from "../components/GradesTab";
import BillingTab from "../components/BillingTab";
import DailyLearningProgressTab from "../components/DailyLearningProgressTab";
import DocumentLibraryTab from "../components/DocumentLibraryTab";
import ComingSoon from "../components/ComingSoon";
import ProfileModal from "../components/ProfileModal";

type Tab = "home" | "schedule" | "learning-progress" | "homework" | "documents" | "grades" | "billing";

const TABS: { key: Tab; label: string; icon: React.ComponentType<{ size?: number }> }[] = [
  { key: "home", label: "Trang chủ & Bảng tin", icon: Home },
  { key: "schedule", label: "Lịch học & Chuyên cần", icon: Calendar },
  { key: "learning-progress", label: "Quá trình học tập", icon: NotebookPen },
  { key: "homework", label: "Bài tập về nhà (BTVN)", icon: ClipboardList },
  { key: "documents", label: "Kho dữ liệu (Sách, TLTK)", icon: FolderOpen },
  { key: "grades", label: "Khảo thí & Điểm số", icon: Award },
  { key: "billing", label: "Học phí & Dịch vụ", icon: CreditCard }
];

/**
 * UC-42 mở self-access cho học sinh ở /portal/students/{id}/class-options, /auth/me (studentId),
 * /students/me/sessions (UC-59, lịch học), và từ UC-64 (2026-07-29, PR #112) thêm
 * /students/me/classes/{classId}/attendance + /comments (điểm danh + nhận xét đã duyệt của chính
 * mình — xem StudentScheduleTab/DailyLearningProgressTab/HomeTab). Riêng "Học phí & Dịch vụ"
 * (InvoiceService) vẫn chỉ chấp nhận Phụ huynh — tab đó vẫn hiện ComingSoon cho Học sinh.
 */
export default function PortalPage() {
  const { currentUser, isParent, isStudent, logout } = useApp();
  const [activeTab, setActiveTab] = useState<Tab>(() => (isParent ? "home" : "homework"));

  const [children, setChildren] = useState<ChildResponse[]>([]);
  const [selectedChildId, setSelectedChildId] = useState<number | null>(null);
  const [classOptions, setClassOptions] = useState<PortalClassOptionResponse[]>([]);
  const [selectedClassId, setSelectedClassId] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [profileOpen, setProfileOpen] = useState(false);
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);
  // Ảnh đại diện (UC-63) hiện ở avatar Header — trước đây chỉ ProfileModal tự fetch/tự giữ state
  // riêng, nên đổi ảnh xong đóng modal ra ngoài Header vẫn thấy chữ cái thay vì ảnh vừa tải lên (đã
  // báo lỗi 2026-08-03). Chỉ áp dụng khi Học sinh tự xem (isStudent) — Phụ huynh xem con thì
  // ChildResponse chưa có portraitUrl, giữ chữ cái như cũ.
  const [viewerPortraitUrl, setViewerPortraitUrl] = useState<string | null>(null);
  // Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-06 — bấm link "Bài ngữ pháp/nghe"/"Video
  // TKN/PX" ở tab Quá trình học tập nhảy sang tab BTVN + tự mở đúng bài (học sinh) hoặc cuộn/highlight
  // đúng dòng (phụ huynh). Set ở đây (cha chung của 2 tab) vì 2 tab là 2 component độc lập, không tự
  // gọi nhau được — PortalPage làm cầu nối, mỗi tab tự clear về null sau khi dùng xong (onAutoOpenHandled/
  // onHighlightHandled) để không lặp lại hành động mỗi khi re-render.
  const [pendingExerciseAssignmentId, setPendingExerciseAssignmentId] = useState<number | null>(null);
  const [pendingReviewVideoAssignmentId, setPendingReviewVideoAssignmentId] = useState<number | null>(null);
  const [pendingHighlightCommentId, setPendingHighlightCommentId] = useState<number | null>(null);
  // Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-06 — badge số BTVN "Cần hoàn thành" trên
  // mục sidebar, do AssignmentsTab báo lên (xem prop onPendingCountChange) — giữ nguyên giá trị lần
  // tính gần nhất kể cả khi rời tab "homework" (AssignmentsTab unmount thì không tính lại, không phải
  // là 0 lúc đó).
  const [pendingHomeworkCount, setPendingHomeworkCount] = useState<number | null>(null);

  useEffect(() => {
    if (isParent) {
      listMyChildren()
        .then((kids) => {
          setChildren(kids);
          if (kids.length > 0) setSelectedChildId(kids[0].studentId);
        })
        .catch((err) => setError(err instanceof ApiError ? err.message : "Không tải được danh sách con."))
        .finally(() => setLoading(false));
      return;
    }
    if (isStudent) {
      setSelectedChildId(currentUser?.studentId ?? null);
      setLoading(false);
      return;
    }
    setLoading(false);
  }, [isParent, isStudent, currentUser]);

  useEffect(() => {
    if (!isStudent) return;
    getMyStudentProfile()
      .then((p) => setViewerPortraitUrl(p.portraitUrl))
      .catch(() => undefined);
  }, [isStudent]);

  useEffect(() => {
    setSelectedClassId(null);
    setClassOptions([]);
    if (!selectedChildId) return;
    listClassOptions(selectedChildId)
      .then((options) => {
        setClassOptions(options);
        const recommended = options.find((o) => o.recommended) ?? options[0];
        if (recommended) setSelectedClassId(recommended.classId);
      })
      .catch((err) => setError(err instanceof ApiError ? err.message : "Không tải được danh sách lớp."));
  }, [selectedChildId]);

  const selectedChild = children.find((c) => c.studentId === selectedChildId) ?? null;
  const noViewerData = isParent ? children.length === 0 : !selectedChildId;
  const currentClass = classOptions.find((c) => c.classId === selectedClassId) ?? null;
  const viewerName = isParent ? selectedChild?.studentFullName ?? "" : currentUser?.fullName ?? "";
  // Sidebar mobile (chứa Menu) chỉ render khi có dữ liệu viewer — dùng chung điều kiện để quyết định
  // logo/nút Thoát ở top bar có nên "chuyển hẳn vào sidebar" (ẩn ở top bar) hay phải giữ tại chỗ.
  const hasMobileDrawer = !noViewerData && !loading;

  if (!isParent && !isStudent) {
    return (
      <div className="min-h-screen flex items-center justify-center p-8 text-center">
        <div className="bg-white border border-line/80 rounded-[24px] p-10 max-w-md space-y-3">
          <h2 className="text-lg font-extrabold text-ink">Tài khoản chưa hỗ trợ xem Portal</h2>
          <p className="text-xs text-muted font-bold">
            Portal hiện chỉ phục vụ tài khoản Phụ huynh và Học sinh.
          </p>
          <button onClick={() => logout()} className="text-xs font-extrabold text-teal hover:underline">
            Đăng xuất
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen flex flex-col font-sans antialiased text-ink bg-[--sky]">
      <nav className="bg-teal-deep lg:bg-white border-b border-white/10 lg:border-line sticky top-0 z-50 shadow-sm w-full py-2.5">
        <div className="w-full max-w-[1560px] mx-auto px-4 md:px-8 xl:px-12 flex items-center justify-between">
          <div className="flex items-center gap-3">
            {!noViewerData && !loading && (
              <button
                onClick={() => setMobileMenuOpen(true)}
                className="lg:hidden shrink-0 w-9 h-9 rounded-xl bg-white/15 border border-white/20 flex items-center justify-center text-white hover:bg-white/25 transition-colors"
                aria-label="Mở menu"
              >
                <Menu size={18} />
              </button>
            )}
            {/* Logo/brand — trên mobile chuyển hẳn vào sidebar (xem header "MENU" bên dưới) để đỡ chật
                thanh top bar, chỉ giữ nút hamburger ở đây; desktop vẫn hiện như cũ. Sidebar chỉ tồn tại
                khi có dữ liệu viewer (noViewerData=false) — không có sidebar thì phải hiện logo ở đây
                luôn, không được giấu hẳn (mất branding + không có chỗ khác thay thế). Nav chỉ nền
                teal-deep ở mobile (desktop vẫn nền trắng như cũ) — nên hộp logo cần 2 bộ màu viền/bóng/
                chữ tùy breakpoint (viền/chữ màu teal-deep gốc sẽ biến mất nếu nền cũng teal-deep). */}
            <div className={`items-center gap-3 ${hasMobileDrawer ? "hidden lg:flex" : "flex"}`}>
              <div className="w-10 h-10 rounded-xl bg-teal border-2 border-white/30 lg:border-teal-deep flex items-center justify-center shadow-[0_3px_0_rgba(0,0,0,0.15)] lg:shadow-[0_3px_0_var(--teal-deep)]">
                <span className="font-display font-extrabold text-white text-xl">P</span>
              </div>
              <div className="leading-tight">
                <div className="font-extrabold text-[15.5px] text-white lg:text-ink">PPS Education</div>
                <div className="text-[11px] tracking-[0.14em] text-white/80 lg:text-teal-deep font-extrabold">
                  {isParent ? "PORTAL PHỤ HUYNH" : "PORTAL HỌC SINH"}
                </div>
              </div>
            </div>

            {/* Chỗ trống bỏ lại sau khi ẩn logo trên mobile nhìn trống trải — thay bằng lời chào +
                avatar (bấm mở hồ sơ), giống mẫu tham khảo người dùng gửi, thay vì để trống hẳn. */}
            {hasMobileDrawer && selectedChildId && (
              <button onClick={() => setProfileOpen(true)} className="flex lg:hidden items-center gap-2.5 min-w-0">
                <div className="w-9 h-9 rounded-full bg-white/20 border border-white/30 flex items-center justify-center text-white font-extrabold text-xs shrink-0 overflow-hidden">
                  {viewerPortraitUrl ? (
                    <img src={viewerPortraitUrl} alt="" className="w-full h-full object-cover" />
                  ) : (
                    (viewerName || "?").charAt(0).toUpperCase()
                  )}
                </div>
                <div className="text-left leading-tight min-w-0">
                  <div className="text-[11px] text-white/75 font-bold">Chào mừng,</div>
                  <div className="text-sm font-extrabold text-white truncate max-w-[130px]">{viewerName || "Bạn"}</div>
                </div>
              </button>
            )}
          </div>
          <div className="flex items-center gap-4">
            {!noViewerData && !loading && <NotificationBell />}
            {/* Bản đầy đủ (avatar + nhãn vai trò + tên) chỉ còn ở desktop (nền trắng, giữ nguyên màu
                gốc) — mobile đã có bản gọn nền teal-deep ở bên trái (lời chào) để không lặp 2 avatar
                cùng mở chung 1 ProfileModal. */}
            {selectedChildId && (
              <button
                onClick={() => setProfileOpen(true)}
                className="hidden lg:flex items-center gap-2.5 pl-2 pr-3.5 py-1.5 bg-sky-2 hover:bg-sky border border-line rounded-[16px] transition-colors"
              >
                <div className="w-7 h-7 rounded-full bg-teal/15 border border-teal/30 flex items-center justify-center text-teal-deep font-extrabold text-xs shrink-0 overflow-hidden">
                  {viewerPortraitUrl ? (
                    <img src={viewerPortraitUrl} alt="" className="w-full h-full object-cover" />
                  ) : (
                    (viewerName || "?").charAt(0).toUpperCase()
                  )}
                </div>
                <div className="text-left leading-tight">
                  <div className="text-[9px] text-muted font-extrabold uppercase tracking-wide">
                    {isParent ? "Học viên" : "Học sinh"}
                  </div>
                  <div className="text-xs font-extrabold text-ink">{viewerName}</div>
                </div>
              </button>
            )}
            {/* Nút "Thoát" — trên mobile chuyển vào cuối sidebar (khi sidebar tồn tại), desktop vẫn giữ
                ở đây. Không có sidebar (noViewerData) thì phải giữ ở đây luôn, không thì mất lối thoát. */}
            <button
              onClick={() => logout()}
              className={`items-center gap-1.5 px-4 py-1.5 bg-white border-2 border-coral text-coral font-bold text-xs rounded-[16px] ${
                hasMobileDrawer ? "hidden lg:flex" : "flex"
              }`}
            >
              <LogOut size={14} /> Thoát
            </button>
          </div>
        </div>
      </nav>

      {profileOpen && (
        <ProfileModal
          fullName={viewerName || "—"}
          studentId={selectedChildId}
          className={currentClass?.className ?? null}
          classCode={currentClass?.classCode ?? null}
          enrollmentStatus={currentClass?.status ?? null}
          parentName={isParent ? currentUser?.fullName ?? null : null}
          parentPhone={isParent ? currentUser?.phone ?? null : null}
          isStudent={isStudent}
          isParent={isParent}
          onClose={() => setProfileOpen(false)}
          onPortraitUpdated={isStudent ? setViewerPortraitUrl : undefined}
        />
      )}

      <div className="flex-1 w-full max-w-[1560px] mx-auto px-4 md:px-8 xl:px-12 py-8">
        {error && <div className="text-xs font-bold text-rose-600 bg-rose-50 border border-rose-100 p-3 rounded-xl mb-4">{error}</div>}

        {loading ? (
          <p className="text-sm text-muted font-bold">Đang tải...</p>
        ) : noViewerData ? (
          <div className="bg-white border border-line/80 rounded-[24px] p-10 text-center text-muted font-bold">
            {isParent ? "Chưa có học sinh nào được liên kết với tài khoản của bạn." : "Không tìm thấy hồ sơ học sinh gắn với tài khoản này."}
          </div>
        ) : (
          <div className="grid grid-cols-1 lg:grid-cols-12 gap-8 items-start">
            {mobileMenuOpen && (
              <div
                className="fixed inset-0 z-[70] bg-ink/40 backdrop-blur-[1px] lg:hidden"
                onClick={() => setMobileMenuOpen(false)}
              />
            )}

            <div
              className={`fixed inset-y-0 left-0 z-[80] w-[82%] max-w-[320px] overflow-y-auto bg-white p-6 shadow-[0_8px_40px_rgba(30,42,69,0.18)] transition-transform duration-300 ease-in-out
                lg:sticky lg:top-[76px] lg:z-auto lg:col-span-3 lg:w-auto lg:max-w-none lg:translate-x-0 lg:shadow-[0_8px_30px_rgba(30,42,69,0.03)] lg:rounded-[24px] lg:border lg:border-line/80
                ${mobileMenuOpen ? "translate-x-0" : "-translate-x-full"}`}
            >
              <div className="flex items-center justify-between mb-5 lg:hidden">
                <div className="flex items-center gap-2.5">
                  <div className="w-9 h-9 rounded-xl bg-teal border-2 border-teal-deep flex items-center justify-center shadow-[0_2px_0_var(--teal-deep)] shrink-0">
                    <span className="font-display font-extrabold text-white text-lg">P</span>
                  </div>
                  <div className="leading-tight">
                    <div className="font-extrabold text-[13px] text-ink">PPS Education</div>
                    <div className="text-[9px] tracking-[0.12em] text-teal-deep font-extrabold">
                      {isParent ? "PORTAL PHỤ HUYNH" : "PORTAL HỌC SINH"}
                    </div>
                  </div>
                </div>
                <button
                  onClick={() => setMobileMenuOpen(false)}
                  className="w-8 h-8 rounded-full bg-sky-2 border border-line flex items-center justify-center text-ink hover:bg-sky transition-colors shrink-0"
                  aria-label="Đóng menu"
                >
                  <X size={16} />
                </button>
              </div>

              <div className="space-y-6">
                {children.length > 1 && (
                  <PortalDropdown
                    icon={Users}
                    label="Học viên"
                    value={selectedChildId ?? 0}
                    onChange={(v) => setSelectedChildId(v)}
                    options={children.map((c) => ({ value: c.studentId, label: `${c.studentFullName} (${c.studentCode})` }))}
                  />
                )}

                {classOptions.length > 1 && (
                  <PortalDropdown
                    icon={School}
                    label="Lớp đang học"
                    value={selectedClassId ?? 0}
                    onChange={(v) => setSelectedClassId(v)}
                    // UC-13 (2026-07-29): học sinh/phụ huynh xem được cả lớp cũ (đã chuyển đi) — gắn nhãn
                    // rõ để không nhầm là lớp đang học, dữ liệu hiển thị (điểm/nhận xét/điểm danh/BTVN) vẫn xem đủ.
                    options={classOptions.map((c) => ({ value: c.classId, label: c.status === "ACTIVE" ? c.className : `${c.className} (Lớp cũ)` }))}
                  />
                )}

                <div className="space-y-3">
                  {/* Học phí & Dịch vụ (invoices/thanh toán) chỉ dành Phụ huynh — Học sinh không cần/không nên xem thông tin tài chính của gia đình.
                      Kho dữ liệu (Sách, TLTK) ngược lại: GET /students/me/documents chỉ tự truy cập được cho chính Học sinh — Phụ huynh
                      gọi sẽ 404 "không có hồ sơ học sinh" (2026-07-30), nên ẩn hẳn tab này với Phụ huynh thay vì hiện rồi báo lỗi. */}
                  {TABS.filter((tab) => (tab.key !== "billing" || isParent) && (tab.key !== "documents" || isStudent)).map(({ key, label, icon: Icon }) => (
                    <button
                      key={key}
                      onClick={() => {
                        setActiveTab(key);
                        setMobileMenuOpen(false);
                      }}
                      className={`relative w-full flex items-center gap-3 px-4 py-3 rounded-[16px] font-bold text-sm transition-all border ${
                        activeTab === key
                          ? "bg-teal text-white border-teal-deep shadow-[0_4px_12px_rgba(23,166,160,0.2)]"
                          : "bg-slate-50/50 hover:bg-slate-50 text-muted border-line/60"
                      }`}
                    >
                      <Icon size={18} /> {label}
                      {/* Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-06 — badge số BTVN
                          "Cần hoàn thành", mirror style badge chưa đọc của NotificationBell. */}
                      {key === "homework" && isStudent && !!pendingHomeworkCount && (
                        <span className="ml-auto min-w-[20px] h-5 px-1.5 rounded-full bg-coral text-white text-[11px] font-extrabold flex items-center justify-center border-2 border-white shrink-0">
                          {pendingHomeworkCount}
                        </span>
                      )}
                    </button>
                  ))}
                </div>

                {/* "Thoát" chuyển vào đây trên mobile (top bar chỉ giữ hamburger để đỡ chật) — desktop
                    vẫn dùng nút ở top bar, không lặp lại 2 chỗ. */}
                <button
                  onClick={() => logout()}
                  className="lg:hidden w-full flex items-center justify-center gap-1.5 px-4 py-3 bg-white border-2 border-coral text-coral font-bold text-sm rounded-[16px]"
                >
                  <LogOut size={16} /> Thoát
                </button>
              </div>
            </div>

            <div className="lg:col-span-9">
              {!selectedClassId ? (
                <div className="bg-white border border-line/80 rounded-[24px] p-10 text-center text-muted font-bold">
                  Học sinh chưa được xếp vào lớp nào.
                </div>
              ) : (
                <>
                  {activeTab === "home" &&
                    (isParent && selectedChild ? (
                      <HomeTab studentName={selectedChild.studentFullName} classId={selectedClassId} parentStudentId={selectedChild.studentId} />
                    ) : isStudent ? (
                      <HomeTab studentName={viewerName} classId={selectedClassId} />
                    ) : (
                      <ComingSoon title="Trang chủ & Bảng tin" description="Không có hồ sơ Học sinh hoặc Phụ huynh liên kết với tài khoản này." />
                    ))}
                  {activeTab === "schedule" &&
                    (isParent && selectedChild ? (
                      <ScheduleTab studentId={selectedChild.studentId} classId={selectedClassId} />
                    ) : isStudent ? (
                      <StudentScheduleTab classId={selectedClassId} />
                    ) : (
                      <ComingSoon title="Lịch học & Chuyên cần" description="Không có hồ sơ Học sinh hoặc Phụ huynh liên kết với tài khoản này." />
                    ))}
                  {activeTab === "learning-progress" &&
                    (isParent && selectedChild ? (
                      <DailyLearningProgressTab
                        studentName={selectedChild.studentFullName}
                        studentCode={selectedChild.studentCode}
                        classId={selectedClassId}
                        parentStudentId={selectedChild.studentId}
                        onOpenGrammarHomework={(commentId) => {
                          setPendingHighlightCommentId(commentId);
                          setActiveTab("homework");
                        }}
                        onOpenVideoHomework={(commentId) => {
                          setPendingHighlightCommentId(commentId);
                          setActiveTab("homework");
                        }}
                      />
                    ) : isStudent ? (
                      <DailyLearningProgressTab
                        studentName={viewerName}
                        studentCode={currentUser?.studentId ? String(currentUser.studentId) : ""}
                        classId={selectedClassId}
                        onOpenGrammarHomework={(_commentId, exerciseAssignmentId) => {
                          setPendingExerciseAssignmentId(exerciseAssignmentId);
                          setActiveTab("homework");
                        }}
                        onOpenVideoHomework={(_commentId, reviewVideoAssignmentId) => {
                          setPendingReviewVideoAssignmentId(reviewVideoAssignmentId);
                          setActiveTab("homework");
                        }}
                      />
                    ) : (
                      <ComingSoon title="Quá trình học tập" description="Không có hồ sơ Học sinh hoặc Phụ huynh liên kết với tài khoản này." />
                    ))}
                  {activeTab === "homework" &&
                    (isStudent ? (
                      // GET /students/me/exercises tra theo userId của chính người gọi — chỉ hoạt động cho Học sinh tự
                      // đăng nhập, Phụ huynh gọi sẽ 404 "không có hồ sơ học sinh".
                      <AssignmentsTab
                        classId={selectedClassId}
                        autoOpenExerciseAssignmentId={pendingExerciseAssignmentId}
                        autoOpenReviewVideoAssignmentId={pendingReviewVideoAssignmentId}
                        onAutoOpenHandled={() => {
                          setPendingExerciseAssignmentId(null);
                          setPendingReviewVideoAssignmentId(null);
                        }}
                        onPendingCountChange={setPendingHomeworkCount}
                      />
                    ) : isParent && selectedChild ? (
                      // UC-64 (2026-07-29): Phụ huynh chỉ XEM tiến độ BTVN của con (không phải giao diện làm bài — con tự làm ở Portal Học sinh).
                      <ParentHomeworkProgressTab
                        studentId={selectedChild.studentId}
                        classId={selectedClassId}
                        highlightCommentId={pendingHighlightCommentId}
                        onHighlightHandled={() => setPendingHighlightCommentId(null)}
                      />
                    ) : (
                      <ComingSoon title="Bài tập về nhà (BTVN)" description="Không có hồ sơ Học sinh hoặc Phụ huynh liên kết với tài khoản này." />
                    ))}
                  {activeTab === "documents" && isStudent && <DocumentLibraryTab classId={selectedClassId} />}
                  {activeTab === "grades" &&
                    (isParent && selectedChild ? (
                      <GradesTab studentId={selectedChild.studentId} classId={selectedClassId} />
                    ) : isStudent ? (
                      <GradesTab classId={selectedClassId} />
                    ) : (
                      <ComingSoon title="Khảo thí & Điểm số" description="Không có hồ sơ Học sinh hoặc Phụ huynh liên kết với tài khoản này." />
                    ))}
                  {activeTab === "billing" &&
                    (isParent ? (
                      <BillingTab />
                    ) : (
                      <ComingSoon
                        title="Học phí & Dịch vụ"
                        description="Đang chờ Backend mở API cho Học sinh tự xem học phí của chính mình (hiện chỉ Phụ huynh xem được)."
                      />
                    ))}
                </>
              )}
            </div>
          </div>
        )}
      </div>

      <footer className="mt-auto py-6 border-t border-line/60 bg-white text-center text-xs text-muted font-semibold">
        © 2026 PPS Education. All rights reserved.
      </footer>
    </div>
  );
}
