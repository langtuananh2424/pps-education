import React, { useEffect, useState } from "react";
import { useLocation } from "react-router-dom";
import { AlertTriangle, Bell, CheckCircle2, ChevronDown, Clock, GraduationCap, KeyRound, Lock, LogOut, Menu, MapPin, Settings, User } from "lucide-react";
import { useApp } from "@/context/AppContext";
import { getMyPartnerSite, listSites, listSiteTeachers, SiteResponse, SiteTeacherResponse } from "@/features/facility/api";
import { useEligibleClasses } from "@/features/academic/hooks/useEligibleClasses";
import { listMyNotifications, markNotificationRead, NotificationResponse } from "@/features/notifications/api";
import { AttendanceRecordResponse, getMyTodayAttendance } from "@/features/hrm/api";
import SelfAttendanceCard from "@/features/hrm/components/SelfAttendanceCard";
import { UserRole } from "@/types";
import Avatar from "@/components/ui/Avatar";
import Dropdown from "@/components/ui/Dropdown";
import Modal from "@/components/ui/Modal";
import ProfileModal from "@/features/auth/components/ProfileModal";
import ChangePasswordModal from "@/features/auth/components/ChangePasswordModal";
import { useDialog } from "@/components/ui/DialogProvider";

const NOTIFICATION_PAGE_SIZE = 15;

// Yêu cầu người dùng 2026-08-17 — pill "Lớp" trước đây hiện ở MỌI route một khi tài khoản đứng lớp
// thật (myAssignedClassCount > 0), gây rối mắt ở các màn không lọc theo lớp (Lịch của tôi, Soạn &
// giao đề, Kho Video Ôn tập, Kho tài liệu...). Với Giáo viên thuần, giới hạn chỉ hiện đúng 4 màn
// thật sự dùng lớp đang chọn ở Header: Quản lý lớp học (UC-18, gồm cả tab Sổ điểm/Nhận xét dạng
// panel), Điểm danh (UC-15), Nhận xét học viên (UC-21/22), Thống kê BTVN theo lớp. KHÔNG gồm "Hàng
// chờ chấm bài" (/lms/exams) — trang đó đã có bộ chọn Bộ Video + Lớp riêng ngay trong trang, không
// đọc selectedClassId từ Header. KHÔNG áp cho nhánh Site Manager/admin academic.class.view-all — 2
// nhóm đó vẫn cần "Lớp" ở các trang báo cáo như trước, người dùng chưa yêu cầu đổi.
const TEACHER_CLASS_SELECTOR_PATHS = ["/academic/classes", "/student/attendance", "/academic/comments", "/academic/homework-stats"];

function formatTimeHm(value: string | null): string {
  if (!value) return "—";
  return new Date(value).toLocaleTimeString("vi-VN", { hour: "2-digit", minute: "2-digit" });
}

export default function Header() {
  const {
    currentRoleLabel,
    currentUser,
    selectedCampusId,
    setSelectedCampusId,
    selectedClassId,
    setSelectedClassId,
    sidebarOpen,
    setSidebarOpen,
    logout,
    hasPermission
  } = useApp();
  const location = useLocation();
  const [sites, setSites] = useState<SiteResponse[]>([]);
  const [profileOpen, setProfileOpen] = useState(false);
  const [changePasswordOpen, setChangePasswordOpen] = useState(false);
  const [attendanceModalOpen, setAttendanceModalOpen] = useState(false);
  const { alertDialog } = useDialog();

  useEffect(() => {
    listSites().then(setSites).catch(() => undefined);
  }, []);

  // Bổ sung ngoài UC-09 gốc, đã xác nhận với người dùng 2026-08-12 — trạng thái chấm công hôm
  // nay của chính người dùng, hiển thị pill ở Header (giống pattern "Điểm trường"/"Lớp" bên
  // trái). undefined = không áp dụng (thuộc diện miễn trừ is_management hoặc không có hồ sơ
  // nhân sự, BE trả 204) -- ẩn hẳn pill, không phân biệt được 2 trường hợp này ở FE và cũng
  // không cần thiết (cả 2 đều "không chấm công qua hệ thống").
  const [myAttendance, setMyAttendance] = useState<AttendanceRecordResponse | undefined>(undefined);
  useEffect(() => {
    getMyTodayAttendance().then(setMyAttendance).catch(() => setMyAttendance(undefined));
  }, []);

  // Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-06 — nối API thật thay cho mảng
  // hardcode trước đây, mirror đúng NotificationBell.tsx bên Portal (GET /notifications +
  // POST /notifications/{id}/read, đánh dấu đã đọc khi bấm vào từng mục).
  const [notifications, setNotifications] = useState<NotificationResponse[]>([]);
  useEffect(() => {
    listMyNotifications(0, NOTIFICATION_PAGE_SIZE)
      .then((res) => setNotifications(res.content))
      .catch(() => undefined);
  }, []);
  const unreadNotificationCount = notifications.filter((n) => !n.readAt).length;
  const handleOpenNotification = (n: NotificationResponse) => {
    if (n.readAt) return;
    markNotificationRead(n.id)
      .then((updated) => setNotifications((prev) => prev.map((x) => (x.id === updated.id ? updated : x))))
      .catch(() => undefined);
  };

  // Bất kỳ vai trò nào gắn với đúng 1 (vài) điểm trường cụ thể — Quản lý điểm trường (site_managers),
  // Đại diện trường liên kết (site_managers role_type=PARTNER_REP, tự resolve qua UC-29), Giáo viên phụ trách
  // (site_teachers) — chỉ biết đúng điểm trường của mình, không thấy/chọn được điểm trường khác hay "Tất cả".
  const [managedSites, setManagedSites] = useState<SiteResponse[]>([]);
  // true trong lúc chưa xác định xong managedSites -- tránh chớp nhoáng hiện cảnh báo
  // "chưa gán điểm trường" trước khi các API site_managers/site_teachers trả về.
  const [managedSitesLoading, setManagedSitesLoading] = useState(true);

  // Vai trò bắt buộc gắn với (các) điểm trường cụ thể -- nếu tài khoản có 1 trong các
  // vai trò này mà managedSites rỗng, đó là dấu hiệu CHƯA ĐƯỢC GÁN điểm trường (thiếu
  // site_managers/site_teachers), không phải "không giới hạn site" như SYS_ADMIN/STAFF.
  //
  // Loại trừ tài khoản có academic.class.manage (đúng quyền BE dùng để bỏ giới hạn site
  // ở ClassService.resolveAllowedSiteIds) — tài khoản demo "Super Admin" cố tình được gán
  // ĐỦ mọi roleCodes (kể cả TEACHER/SITE_MANAGER) để test mọi màn hình, nhưng không thật
  // sự được gán site_teachers/site_managers nào — nếu không loại trừ, tài khoản này bị
  // hiểu lầm thành "chưa gán điểm trường" dù thực ra xem được hết mọi điểm trường.
  const siteScopedRoles: string[] = [UserRole.SITE_MANAGER, UserRole.PARTNER_REP, UserRole.TEACHER];
  const seesAllSites = hasPermission("academic.class.manage");
  const isSiteScopedRole = !seesAllSites && (currentUser?.roleCodes ?? []).some((r) => siteScopedRoles.includes(r));

  useEffect(() => {
    if (!currentUser || sites.length === 0) {
      return;
    }
    const roleCodes = currentUser.roleCodes ?? [];
    const tasks: Promise<SiteResponse[]>[] = [];

    if (roleCodes.includes(UserRole.SITE_MANAGER)) {
      tasks.push(Promise.resolve(sites.filter((site) => site.currentManagerUserId === currentUser.id)));
    }
    if (roleCodes.includes(UserRole.PARTNER_REP)) {
      tasks.push(
        getMyPartnerSite()
          .then((p) => sites.filter((site) => site.id === p.siteId))
          .catch(() => [] as SiteResponse[])
      );
    }
    if (roleCodes.includes(UserRole.TEACHER)) {
      tasks.push(
        Promise.allSettled(sites.map((site) => listSiteTeachers(site.id).then((list) => ({ site, list }))))
          .then((results) =>
            results
              .filter(
                (r): r is PromiseFulfilledResult<{ site: SiteResponse; list: SiteTeacherResponse[] }> => r.status === "fulfilled"
              )
              .filter((r) => r.value.list.some((t) => t.teacherUserId === currentUser.id))
              .map((r) => r.value.site)
          )
      );
    }

    if (tasks.length === 0) {
      setManagedSites([]);
      setManagedSitesLoading(false);
      return;
    }

    let cancelled = false;
    setManagedSitesLoading(true);
    Promise.all(tasks).then((results) => {
      if (cancelled) return;
      const merged = new Map<number, SiteResponse>();
      results.flat().forEach((site) => merged.set(site.id, site));
      setManagedSites(Array.from(merged.values()));
      setManagedSitesLoading(false);
    });
    return () => {
      cancelled = true;
    };
  }, [sites, currentUser]);

  useEffect(() => {
    if (selectedCampusId !== "ALL" || managedSites.length === 0) return;
    setSelectedCampusId(String(managedSites[0].id));
  }, [managedSites, selectedCampusId, setSelectedCampusId]);

  const lockToManagedSites = managedSites.length > 0;
  const showUnassignedWarning = !managedSitesLoading && isSiteScopedRole && managedSites.length === 0;
  const currentCampusLabel =
    !lockToManagedSites && selectedCampusId === "ALL"
      ? "Tất cả cơ sở & Trường liên kết"
      : (lockToManagedSites ? managedSites : sites).find((s) => String(s.id) === selectedCampusId)?.name ?? "-- Chọn điểm trường --";

  // Chỉ hiện "Lớp" cho vai trò thật sự cần lọc theo lớp ở 1 trong các màn: Sổ điểm (UC-19/20,
  // SITE_MANAGER không có permission điểm nhưng vẫn cần lọc lớp để "xem lại sổ điểm"), Điểm danh
  // (UC-15), Soạn & giao đề (UC-40), Nhận xét (UC-21/22), Kho bài giảng (UC-23) — ẩn với vai trò
  // không liên quan (Tài chính/HRM/CRM...) để đỡ rối mắt, cùng tinh thần với "Điểm trường".
  //
  // isGenuineSiteManager dùng managedSites (site_managers THẬT, đã tính ở trên cho phần Điểm
  // trường) — KHÔNG dùng roleCodes.includes(SITE_MANAGER) trực tiếp, vì tài khoản demo "Super
  // Admin" cố tình được gán roleCode SITE_MANAGER để test màn hình nhưng không thật sự quản lý
  // site nào (managedSites rỗng) — dùng roleCodes suông sẽ lại hiện nhầm pill cho tài khoản đó.
  const isGenuineSiteManager = (currentUser?.roleCodes?.includes(UserRole.SITE_MANAGER) ?? false) && managedSites.length > 0;
  const { classes: eligibleClasses, myAssignedClassCount, loading: loadingEligibleClasses } = useEligibleClasses();
  // academic.class.view-all (V64, bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-07-30):
  // permission RIÊNG cho "được xem/chọn mọi lớp", dành cho Trưởng phòng đào tạo/Quản trị viên —
  // trước đây nhóm tài khoản này (HEAD_ACADEMIC/SYS_ADMIN thuần, không đứng lớp/site nào thật)
  // hoàn toàn không thấy pill "Lớp" (xem lịch sử quyết định cũ ngay dưới), khiến các trang phụ
  // thuộc selectedClassId dùng chung ở Header (Sổ điểm UC-19/20, Điểm danh UC-15, Nhận xét
  // UC-21/22, Soạn & giao đề UC-40 — khác Kho Video Ôn tập UC-23 đã có bộ chọn lớp riêng trong
  // trang) không dùng được với tài khoản quản trị dù đã cấp quyền, vì quyền cũ dùng để loại trừ
  // (academic.class.manage) không phản ánh đúng nhu cầu "được xem mọi lớp" — permission mới tách
  // riêng để không phải cấp nhầm quyền UC-18 "xếp lớp" chỉ để xem danh sách lớp.
  const canViewAllClasses = hasPermission("academic.class.view-all");
  // Dùng myAssignedClassCount (đứng tên thật trong class_teachers) để quyết định hiện pill — KHÔNG
  // dùng quyền academic.class.manage để loại trừ (đã dính bug: 1 tài khoản Giáo viên demo vừa có
  // quyền quản trị vừa thật sự đứng lớp bị ẩn nhầm pill, vì quyền đó không phản ánh có được phân
  // công dạy lớp nào hay không). SITE_MANAGER thật không đứng lớp nào (myAssignedClassCount luôn 0)
  // nhưng vẫn cần thấy pill để "xem lại sổ điểm" (UC-20) — xét riêng qua eligibleClasses.
  //
  // Tài khoản chỉ có quyền quản trị rộng (academic.class.manage) như HEAD_ACADEMIC/SYS_ADMIN/"Super
  // Admin" demo KHÔNG hiện pill này ở Header theo quyết định 2026-07-27 — nhưng tài khoản được cấp
  // RIÊNG academic.class.view-all (2026-07-30) thì có, xem eligibleClasses (đã unrestricted qua
  // ClassService.resolveAllowedSiteIds khi có quyền này).
  // Chỉ Giáo viên thuần (đứng lớp thật, không phải Site Manager/admin view-all) mới bị giới hạn theo
  // route — xem TEACHER_CLASS_SELECTOR_PATHS ở đầu file.
  const isTeacherRouteAllowed = TEACHER_CLASS_SELECTOR_PATHS.includes(location.pathname);
  const showClassSelector =
    loadingEligibleClasses || (myAssignedClassCount > 0 && isTeacherRouteAllowed) ||
    (isGenuineSiteManager && eligibleClasses.length > 0) ||
    (canViewAllClasses && eligibleClasses.length > 0);
  const selectedEligibleClass = eligibleClasses.find((cls) => cls.id === selectedClassId) ?? null;

  return (
    <header className="sticky top-0 h-16 bg-brand-bg/85 backdrop-blur-md px-2 md:px-0 flex items-center justify-between z-30 mb-4 shrink-0">
      <div className="flex items-center gap-4">
        <button
          onClick={() => setSidebarOpen(!sidebarOpen)}
          className="p-2.5 rounded-xl text-slate-500 hover:text-slate-800 bg-white hover:bg-slate-50 border border-slate-200/50 shadow-soft lg:hidden transition-all"
        >
          <Menu className="w-5 h-5" />
        </button>

        {showUnassignedWarning ? (
          <div className="hidden sm:flex items-center gap-2 text-xs font-medium px-4 py-2 rounded-full shadow-soft border bg-amber-50 border-amber-200 text-amber-700">
            <AlertTriangle className="w-3.5 h-3.5 text-amber-500 shrink-0" />
            <span className="font-semibold text-amber-700">Điểm trường:</span>
            <span className="text-amber-700 font-semibold">Chưa được gán điểm trường — liên hệ quản trị viên</span>
          </div>
        ) : lockToManagedSites && managedSites.length === 1 ? (
          <div className="hidden sm:flex items-center gap-2 text-xs font-medium px-4 py-2 rounded-full shadow-soft border bg-white border-slate-200/50 text-slate-500">
            <MapPin className="w-3.5 h-3.5 text-brand-orange shrink-0" />
            <span className="font-semibold text-slate-700">Điểm trường:</span>
            <span className="flex items-center gap-1.5 text-slate-800 font-semibold">
              {managedSites[0].name}
              <Lock className="w-3 h-3 text-slate-400" />
            </span>
          </div>
        ) : (
          <div className="hidden sm:block">
            <Dropdown
              align="left"
              panelClassName="w-64 py-1.5 max-h-80 overflow-y-auto"
              trigger={
                <button className="flex items-center gap-2 text-xs font-medium px-4 py-2 rounded-full shadow-soft border bg-white border-slate-200/50 hover:bg-slate-50 hover:border-brand-orange/30 text-slate-500 transition-all cursor-pointer">
                  <MapPin className="w-3.5 h-3.5 text-brand-orange shrink-0" />
                  <span className="font-semibold text-slate-700">Điểm trường:</span>
                  <span className="font-semibold text-slate-800 max-w-[200px] truncate">{currentCampusLabel}</span>
                  <ChevronDown className="w-3.5 h-3.5 text-slate-400 shrink-0" />
                </button>
              }
            >
              <div className="p-1.5">
                {!lockToManagedSites && (
                  <button
                    onClick={() => setSelectedCampusId("ALL")}
                    className={`w-full px-3 py-2.5 text-left text-xs font-semibold rounded-lg transition-colors cursor-pointer ${
                      selectedCampusId === "ALL" ? "bg-brand-orange/10 text-brand-orange" : "text-slate-700 hover:bg-slate-50"
                    }`}
                  >
                    Tất cả cơ sở & Trường liên kết
                  </button>
                )}
                {(lockToManagedSites ? managedSites : sites).map((site) => (
                  <button
                    key={site.id}
                    onClick={() => setSelectedCampusId(String(site.id))}
                    className={`w-full px-3 py-2.5 text-left text-xs font-semibold rounded-lg transition-colors cursor-pointer ${
                      selectedCampusId === String(site.id) ? "bg-brand-orange/10 text-brand-orange" : "text-slate-700 hover:bg-slate-50"
                    }`}
                  >
                    {site.name}
                  </button>
                ))}
              </div>
            </Dropdown>
          </div>
        )}

        {showClassSelector && (
          <div className="hidden sm:block">
            <Dropdown
              align="left"
              panelClassName="w-64 py-1.5 max-h-80 overflow-y-auto"
              trigger={
                <button className="flex items-center gap-2 text-xs font-medium px-4 py-2 rounded-full shadow-soft border bg-white border-slate-200/50 hover:bg-slate-50 hover:border-brand-orange/30 text-slate-500 transition-all cursor-pointer">
                  <GraduationCap className="w-3.5 h-3.5 text-brand-orange shrink-0" />
                  <span className="font-semibold text-slate-700">Lớp:</span>
                  <span className="font-semibold text-slate-800 max-w-[160px] truncate">
                    {selectedEligibleClass ? `${selectedEligibleClass.classCode} — ${selectedEligibleClass.name}` : "-- Chọn lớp --"}
                  </span>
                  <ChevronDown className="w-3.5 h-3.5 text-slate-400 shrink-0" />
                </button>
              }
            >
              <div className="p-1.5">
                <button
                  onClick={() => setSelectedClassId(null)}
                  className={`w-full px-3 py-2.5 text-left text-xs font-semibold rounded-lg transition-colors cursor-pointer ${
                    !selectedClassId ? "bg-brand-orange/10 text-brand-orange" : "text-slate-700 hover:bg-slate-50"
                  }`}
                >
                  -- Chọn lớp --
                </button>
                {eligibleClasses.map((cls) => (
                  <button
                    key={cls.id}
                    onClick={() => setSelectedClassId(cls.id)}
                    className={`w-full px-3 py-2.5 text-left text-xs font-semibold rounded-lg transition-colors cursor-pointer ${
                      selectedClassId === cls.id ? "bg-brand-orange/10 text-brand-orange" : "text-slate-700 hover:bg-slate-50"
                    }`}
                  >
                    {cls.classCode} — {cls.name}
                  </button>
                ))}
              </div>
            </Dropdown>
          </div>
        )}
      </div>

      <div className="flex items-center gap-3 md:gap-5">
        {myAttendance && (
          <button
            onClick={() => setAttendanceModalOpen(true)}
            className={`hidden sm:flex items-center gap-1.5 text-xs font-medium px-3.5 py-2 rounded-full shadow-soft border transition-all cursor-pointer ${
              myAttendance.id == null
                ? "bg-amber-50 border-amber-200 text-amber-700 hover:bg-amber-100"
                : "bg-emerald-50 border-emerald-200 text-emerald-700 hover:bg-emerald-100"
            }`}
          >
            {myAttendance.id == null ? (
              <span className="relative flex w-2.5 h-2.5 shrink-0">
                <span className="animate-ping absolute inline-flex w-full h-full rounded-full bg-amber-400 opacity-75" />
                <span className="relative inline-flex w-2.5 h-2.5 rounded-full bg-amber-500" />
              </span>
            ) : (
              <CheckCircle2 className="w-3.5 h-3.5 shrink-0" />
            )}
            {myAttendance.id == null ? (
              <span className="font-semibold">Chấm công</span>
            ) : myAttendance.checkOutAt ? (
              <span className="font-semibold">
                Đã chấm công {formatTimeHm(myAttendance.checkInAt)} – {formatTimeHm(myAttendance.checkOutAt)}
              </span>
            ) : (
              <span className="font-semibold">Đã chấm công vào lúc {formatTimeHm(myAttendance.checkInAt)}</span>
            )}
          </button>
        )}

        <div className="hidden lg:flex items-center gap-1.5 text-slate-600 bg-white border border-slate-200/50 shadow-soft px-3.5 py-2 rounded-full font-mono text-[11px]">
          <Clock className="w-3.5 h-3.5 text-slate-400" />
          <span>
            {new Date().toLocaleDateString("vi-VN", { year: "numeric", month: "long", day: "numeric" })}
          </span>
        </div>

        <Dropdown
          panelClassName="w-80 max-h-[420px] overflow-y-auto"
          trigger={
            <button className="w-9 h-9 flex items-center justify-center rounded-full text-slate-500 hover:text-slate-800 bg-white border border-slate-200/50 hover:bg-slate-50 transition-colors relative shadow-soft">
              <Bell className="w-4 h-4" />
              {unreadNotificationCount > 0 && (
                <>
                  <span className="absolute top-1.5 right-1.5 w-2 h-2 rounded-full bg-brand-red animate-ping" />
                  <span className="absolute top-1.5 right-1.5 w-2 h-2 rounded-full bg-brand-red" />
                </>
              )}
            </button>
          }
        >
          <div className="px-4 py-3 bg-slate-50 border-b border-slate-100 flex items-center justify-between sticky top-0 z-10">
            <span className="text-xs font-semibold text-slate-700">Thông báo vận hành</span>
            {unreadNotificationCount > 0 && (
              <span className="text-[10px] bg-brand-gradient text-white px-2 py-0.5 rounded-full font-bold">{unreadNotificationCount} Mới</span>
            )}
          </div>
          {notifications.length === 0 ? (
            <p className="text-xs text-slate-400 italic p-4">Chưa có thông báo nào.</p>
          ) : (
            <div className="divide-y divide-slate-100">
              {notifications.map((notif) => (
                <button
                  key={notif.id}
                  type="button"
                  onClick={() => handleOpenNotification(notif)}
                  className={`w-full text-left p-3.5 hover:bg-slate-50/60 transition-colors ${!notif.readAt ? "bg-brand-orange/5" : ""}`}
                >
                  <div className="flex items-start gap-2.5">
                    <div
                      className={`w-2 h-2 rounded-full mt-1.5 shrink-0 ${
                        !notif.readAt
                          ? notif.priority === "URGENT" || notif.priority === "HIGH"
                            ? "bg-brand-red"
                            : "bg-brand-orange"
                          : "bg-transparent"
                      }`}
                    />
                    <div className="min-w-0">
                      <p className={`text-xs leading-normal ${!notif.readAt ? "font-bold text-slate-800" : "font-medium text-slate-500"}`}>{notif.title}</p>
                      <p className="text-[11px] text-slate-500 mt-0.5 line-clamp-2">{notif.content}</p>
                      <span className="text-[10px] text-slate-400 block mt-1 font-mono">
                        {new Date(notif.createdAt).toLocaleString("vi-VN")}
                      </span>
                    </div>
                  </div>
                </button>
              ))}
            </div>
          )}
        </Dropdown>

        <Dropdown
          panelClassName="w-56 py-1.5"
          trigger={
            <button className="flex items-center gap-3 pl-4 pr-2.5 py-2 bg-white border border-slate-200/50 hover:bg-slate-50 rounded-2xl transition-all shadow-soft">
              <div className="hidden md:block text-left leading-tight">
                <p className="text-xs font-bold text-slate-800 truncate max-w-[130px]">{currentUser?.fullName || "Cán bộ PPS"}</p>
                <p className="text-[10px] text-slate-500 truncate max-w-[130px]">{currentRoleLabel}</p>
              </div>
              <Avatar name={currentUser?.fullName || "U"} size="sm" />
              <ChevronDown className="w-3.5 h-3.5 text-slate-400 shrink-0" />
            </button>
          }
        >
          <div className="p-1.5">
            <button
              onClick={() => setProfileOpen(true)}
              className="w-full px-3 py-2.5 flex items-center gap-2.5 text-left text-xs font-semibold text-slate-700 hover:bg-slate-50 rounded-lg transition-colors cursor-pointer"
            >
              <User className="w-4 h-4 text-slate-400 shrink-0" />
              <span>Hồ sơ cá nhân</span>
            </button>
            <button
              onClick={() => setChangePasswordOpen(true)}
              className="w-full px-3 py-2.5 flex items-center gap-2.5 text-left text-xs font-semibold text-slate-700 hover:bg-slate-50 rounded-lg transition-colors cursor-pointer"
            >
              <KeyRound className="w-4 h-4 text-slate-400 shrink-0" />
              <span>Đổi mật khẩu</span>
            </button>
            <button
              onClick={() => alertDialog("Tính năng Cài đặt đang được phát triển.")}
              className="w-full px-3 py-2.5 flex items-center gap-2.5 text-left text-xs font-semibold text-slate-700 hover:bg-slate-50 rounded-lg transition-colors cursor-pointer"
            >
              <Settings className="w-4 h-4 text-slate-400 shrink-0" />
              <span>Cài đặt</span>
            </button>
          </div>
          <div className="p-1.5 border-t border-slate-100">
            <button
              onClick={logout}
              className="w-full px-3 py-2.5 flex items-center gap-2.5 text-left text-xs font-semibold text-rose-600 hover:bg-rose-50 rounded-lg transition-colors cursor-pointer"
            >
              <LogOut className="w-4 h-4 shrink-0" />
              <span>Đăng xuất</span>
            </button>
          </div>
        </Dropdown>
      </div>

      {profileOpen && <ProfileModal onClose={() => setProfileOpen(false)} />}
      {changePasswordOpen && <ChangePasswordModal onClose={() => setChangePasswordOpen(false)} />}
      {attendanceModalOpen && (
        <Modal
          open
          onClose={() => setAttendanceModalOpen(false)}
          title="Chấm công của tôi"
          description="Chọn điểm trường và bấm chấm công vào/ra — hệ thống dùng vị trí GPS hiện tại."
          size="lg"
        >
          <SelfAttendanceCard sites={sites} onChecked={setMyAttendance} />
        </Modal>
      )}
    </header>
  );
}
