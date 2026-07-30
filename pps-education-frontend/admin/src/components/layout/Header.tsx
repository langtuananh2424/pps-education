import React, { useEffect, useState } from "react";
import { AlertTriangle, Bell, ChevronDown, Clock, GraduationCap, KeyRound, Lock, LogOut, Menu, MapPin, Settings, User } from "lucide-react";
import { useApp } from "@/context/AppContext";
import { getMyPartnerSite, listSites, listSiteTeachers, SiteResponse, SiteTeacherResponse } from "@/features/facility/api";
import { useEligibleClasses } from "@/features/academic/hooks/useEligibleClasses";
import { UserRole } from "@/types";
import Avatar from "@/components/ui/Avatar";
import Dropdown from "@/components/ui/Dropdown";
import ProfileModal from "@/features/auth/components/ProfileModal";
import ChangePasswordModal from "@/features/auth/components/ChangePasswordModal";

const notifications = [
  { id: "1", text: "Trường Tiểu học Nghĩa Tân gửi ý kiến đóng góp mới (Cô Hiệu Trưởng)", time: "10 phút trước", type: "urgent" },
  { id: "2", text: "Đơn nghỉ phép của Đỗ Gia Bảo đang chờ duyệt bước 2", time: "1 giờ trước", type: "pending" },
  { id: "3", text: "Điểm thi lớp Nghĩa Tân 3A1 vừa được giáo viên Lê Thu Hà submit", time: "2 giờ trước", type: "info" }
];

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
  const [sites, setSites] = useState<SiteResponse[]>([]);
  const [profileOpen, setProfileOpen] = useState(false);
  const [changePasswordOpen, setChangePasswordOpen] = useState(false);

  useEffect(() => {
    listSites().then(setSites).catch(() => undefined);
  }, []);

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
  const showClassSelector =
    loadingEligibleClasses || myAssignedClassCount > 0 || (isGenuineSiteManager && eligibleClasses.length > 0) ||
    (canViewAllClasses && eligibleClasses.length > 0);
  const selectedEligibleClass = eligibleClasses.find((cls) => cls.id === selectedClassId) ?? null;

  return (
    <header className="h-16 bg-transparent px-2 md:px-0 flex items-center justify-between z-30 mb-4 shrink-0">
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
        <div className="hidden lg:flex items-center gap-1.5 text-slate-600 bg-white border border-slate-200/50 shadow-soft px-3.5 py-2 rounded-full font-mono text-[11px]">
          <Clock className="w-3.5 h-3.5 text-slate-400" />
          <span>
            {new Date().toLocaleDateString("vi-VN", { year: "numeric", month: "long", day: "numeric" })}
          </span>
        </div>

        <Dropdown
          panelClassName="w-80 overflow-hidden"
          trigger={
            <button className="w-9 h-9 flex items-center justify-center rounded-full text-slate-500 hover:text-slate-800 bg-white border border-slate-200/50 hover:bg-slate-50 transition-colors relative shadow-soft">
              <Bell className="w-4 h-4" />
              <span className="absolute top-1.5 right-1.5 w-2 h-2 rounded-full bg-brand-red animate-ping" />
              <span className="absolute top-1.5 right-1.5 w-2 h-2 rounded-full bg-brand-red" />
            </button>
          }
        >
          <div className="px-4 py-3 bg-slate-50 border-b border-slate-100 flex items-center justify-between">
            <span className="text-xs font-semibold text-slate-700">Thông báo vận hành</span>
            <span className="text-[10px] bg-brand-gradient text-white px-2 py-0.5 rounded-full font-bold">3 Mới</span>
          </div>
          <div className="divide-y divide-slate-100">
            {notifications.map((notif) => (
              <div key={notif.id} className="p-3.5 hover:bg-slate-50/60 transition-colors">
                <div className="flex items-start gap-2.5">
                  <div
                    className={`w-2 h-2 rounded-full mt-1.5 shrink-0 ${
                      notif.type === "urgent" ? "bg-brand-red" : notif.type === "pending" ? "bg-brand-orange" : "bg-sky-500"
                    }`}
                  />
                  <div>
                    <p className="text-xs text-slate-700 leading-normal font-medium">{notif.text}</p>
                    <span className="text-[10px] text-slate-400 block mt-1 font-mono">{notif.time}</span>
                  </div>
                </div>
              </div>
            ))}
          </div>
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
              onClick={() => alert("Tính năng Cài đặt đang được phát triển.")}
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
    </header>
  );
}
