import React, { useState } from "react";
import { NavLink, useNavigate } from "react-router-dom";
import { ChevronDown, ChevronRight, LogOut, X } from "lucide-react";
import { useTranslation } from "react-i18next";
import { useApp } from "@/context/AppContext";
import { isNavItemAllowed, navSections } from "@/constants/navigation";
import { usePendingGradingCount } from "@/features/lms/hooks/usePendingGradingCount";
import Avatar from "@/components/ui/Avatar";
import CountBadge from "@/components/ui/CountBadge";
import Modal from "@/components/ui/Modal";
import { cn } from "@/lib/cn";

export default function Sidebar() {
  const { t } = useTranslation("layout");
  const {
    currentRoleLabel,
    currentUser,
    sidebarOpen,
    setSidebarOpen,
    sidebarCollapsed,
    setSidebarCollapsed,
    hasPermission,
    logout,
    hasUnsavedChanges,
    saveUnsavedChanges
  } = useApp();
  const navigate = useNavigate();
  // Bổ sung ngoài SDD gốc, xác nhận 2026-08-17 — số lớp đang có bài Video phản xạ chưa chấm, hiện cạnh
  // mục "Hàng chờ chấm bài" (lms-exams) để GV biết ngay có việc cần làm, không phải tự mò từng Bộ+Lớp.
  const pendingGradingCount = usePendingGradingCount();
  const [expandedGroups, setExpandedGroups] = useState<Record<string, boolean>>(
    Object.fromEntries(navSections.map((section) => [section.id, true]))
  );
  // "Rời trang khi chưa lưu" (2026-08-15, bổ sung ngoài SDD gốc, theo yêu cầu người dùng) — trang đang
  // dở (VD Nhận xét học viên) đăng ký hasUnsavedChanges=true qua AppContext; bấm mục Sidebar khác thì
  // chặn điều hướng (preventDefault ở onClick NavLink), giữ lại đường dẫn đích để hỏi xác nhận, chỉ
  // navigate() thật khi người dùng chọn 1 trong 2: lưu tạm rồi đi, hoặc bỏ qua rồi đi.
  const [pendingPath, setPendingPath] = useState<string | null>(null);
  const [savingBeforeLeave, setSavingBeforeLeave] = useState(false);
  // Bổ sung 2026-08-17 — lý do KHÔNG lưu được (nếu có), hiện ngay trong popup xác nhận (luôn nổi giữa
  // màn hình qua Modal/portal, không phụ thuộc vị trí cuộn trang — khác banner lỗi tĩnh trên từng trang
  // dễ bị bỏ lỡ khi đang cuộn xuống làm việc). Reset mỗi lần mở popup mới / đóng popup.
  const [saveFailureMessage, setSaveFailureMessage] = useState<string | null>(null);

  const toggleGroup = (group: string) => setExpandedGroups((prev) => ({ ...prev, [group]: !prev[group] }));

  const closeOnMobile = () => {
    if (window.innerWidth < 1024) setSidebarOpen(false);
  };

  const handleNavClick = (e: React.MouseEvent, path: string) => {
    if (hasUnsavedChanges) {
      e.preventDefault();
      setSaveFailureMessage(null);
      setPendingPath(path);
      return;
    }
    closeOnMobile();
  };

  const closePendingPathModal = () => {
    setPendingPath(null);
    setSaveFailureMessage(null);
  };

  const leaveToPendingPath = () => {
    if (!pendingPath) return;
    const path = pendingPath;
    closePendingPathModal();
    closeOnMobile();
    navigate(path);
  };

  const handleSaveAndLeave = async () => {
    setSavingBeforeLeave(true);
    setSaveFailureMessage(null);
    try {
      const result = await saveUnsavedChanges();
      if (result.ok) {
        leaveToPendingPath();
      } else {
        // Bug đã xác nhận 2026-08-17 — trước đây điều hướng đi bất kể kết quả, làm mất âm thầm dữ liệu
        // không lưu được (VD chỉ điền Thái độ/BTVN mà chưa gõ Nhận xét). Giờ GIỮ NGUYÊN popup, hiện lý
        // do ngay trong đó (luôn nổi giữa màn hình, không cần cuộn trang mới thấy như banner tĩnh cũ).
        setSaveFailureMessage(result.message ?? t("sidebar.unsavedModal.saveFailureFallback"));
      }
    } finally {
      setSavingBeforeLeave(false);
    }
  };

  return (
    <>
      {sidebarOpen && (
        <div className="fixed inset-0 bg-slate-900/40 z-30 lg:hidden backdrop-blur-xs" onClick={() => setSidebarOpen(false)} />
      )}

      {/* Desktop dùng scale (không phải translate) neo tại lg:origin-top-left — đúng góc nút logo mở
          lại trong Header (bổ sung ngoài SDD gốc, xác nhận 2026-08-20), tạo hiệu ứng Sidebar "mọc ra"
          từ đúng vị trí logo thay vì trượt ngang từ mép trái màn hình. Mobile vẫn dùng translate-x (kéo
          drawer từ mép trái) — không đổi, khác hẳn ngữ cảnh sử dụng trên desktop. */}
      <aside
        className={cn(
          "fixed top-0 bottom-0 left-0 lg:top-4 lg:bottom-4 lg:left-4 lg:h-[calc(100vh-2rem)] z-40 w-64 bg-white text-slate-700 border-r lg:border border-slate-200/90 lg:rounded-3xl lg:shadow-soft flex flex-col transition-[transform,opacity] duration-500 ease-out lg:translate-x-0 lg:origin-top-left transform-gpu will-change-transform",
          sidebarOpen ? "translate-x-0" : "-translate-x-full",
          sidebarCollapsed ? "lg:scale-0 lg:opacity-0 lg:pointer-events-none" : "lg:scale-100 lg:opacity-100"
        )}
      >
        <div className="h-16 px-6 border-b border-slate-200/90 flex items-center justify-between bg-white lg:rounded-t-3xl shrink-0">
          {/* Bấm logo để thu gọn hẳn Sidebar (bổ sung ngoài SDD gốc, xác nhận 2026-08-20) — trên
              desktop, Sidebar trượt hẳn ra ngoài màn hình (không còn dạng dải icon/hộp logo thu nhỏ
              nổi ngoài lề như thử trước đó) nên trang không còn phải chừa khoảng trống cố định nào
              cho nó nữa. Mở lại qua nút logo nhỏ đặt trong Header (xem Header.tsx). */}
          <button
            type="button"
            onClick={() => setSidebarCollapsed(true)}
            className="flex items-center gap-2.5 cursor-pointer lg:hover:opacity-80 transition-opacity"
            title={t("sidebar.collapseButtonTitle")}
          >
            <div className="w-8 h-8 rounded-full bg-white p-[2px] flex items-center justify-center shadow-glow relative shrink-0">
              <div className="w-full h-full rounded-full bg-gradient-to-tr from-[#EA580C] via-[#F68B1F] to-[#FB923C] p-[1px] flex items-center justify-center">
                <div className="w-full h-full rounded-full bg-white flex items-center justify-center text-[#EA580C] font-display font-black text-xs">
                  P
                </div>
              </div>
            </div>
            <div className="text-left">
              <span className="font-display font-bold text-slate-900 text-sm tracking-tight block">
                PPS <span className="text-brand-orange">VIETNAM</span>
              </span>
              <span className="text-[8px] text-slate-500 font-extrabold tracking-wider block uppercase -mt-0.5">
                CARING INDIVIDUALS
              </span>
            </div>
          </button>
          <button
            onClick={() => setSidebarOpen(false)}
            className="p-1 rounded-md text-slate-400 hover:text-slate-700 hover:bg-slate-200 lg:hidden"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        <div className="flex-1 overflow-y-auto scrollbar-hide px-4 py-6 space-y-4">
          {navSections.map((section) => {
            const visibleItems = section.items.filter((item) => isNavItemAllowed(item, currentUser?.roleCodes ?? [], hasPermission));
            if (visibleItems.length === 0) return null;

            const isExpanded = expandedGroups[section.id];
            return (
              <div key={section.id} className="space-y-1">
                <button
                  onClick={() => toggleGroup(section.id)}
                  className="w-full px-2 py-1.5 flex items-center justify-between text-[11px] font-bold font-display tracking-widest text-slate-400 hover:text-slate-600"
                >
                  <span>{t(`nav.sections.${section.id}`, section.title)}</span>
                  {isExpanded ? <ChevronDown className="w-3 h-3" /> : <ChevronRight className="w-3 h-3" />}
                </button>

                {isExpanded && (
                  <div className="space-y-[2px] pl-1">
                    {visibleItems.map((item) => {
                      const Icon = item.icon;
                      return (
                        <NavLink
                          key={item.id}
                          to={item.path}
                          onClick={(e) => handleNavClick(e, item.path)}
                          className={({ isActive }) =>
                            cn(
                              "w-full px-3 py-2 flex items-center gap-2.5 rounded-md text-xs font-medium transition-all duration-150",
                              isActive
                                ? "bg-brand-gradient text-white shadow-soft font-semibold"
                                : "text-slate-600 hover:text-slate-900 hover:bg-slate-200/80"
                            )
                          }
                        >
                          {({ isActive }) => (
                            <>
                              <Icon className={cn("w-4 h-4 shrink-0", isActive ? "text-white" : "text-slate-400")} />
                              <span className="truncate">{t(`nav.items.${item.id}`, item.label)}</span>
                              {item.id === "lms-exams" && <CountBadge count={pendingGradingCount} />}
                            </>
                          )}
                        </NavLink>
                      );
                    })}
                  </div>
                )}
              </div>
            );
          })}
        </div>

        <div className="p-4 border-t border-slate-200/90 bg-white flex flex-col gap-3 lg:rounded-b-3xl shrink-0">
          <div className="flex items-center gap-3">
            <Avatar name={currentUser?.fullName || "U"} />
            <div className="flex-1 min-w-0">
              <span className="text-xs font-bold text-slate-900 block truncate leading-tight">
                {currentUser?.fullName || t("sidebar.notLoggedIn")}
              </span>
              <span className="text-[10px] text-slate-500 block truncate mt-0.5 font-medium">{currentRoleLabel}</span>
            </div>
          </div>

          <button
            onClick={logout}
            className="w-full bg-white border border-slate-200 hover:bg-slate-50 hover:border-slate-300 text-rose-500 hover:text-rose-600 font-bold text-[11px] py-2 rounded-lg flex items-center justify-center gap-2 transition-all cursor-pointer shadow-xs"
          >
            <LogOut className="w-3.5 h-3.5 shrink-0" />
            <span>{t("sidebar.logout")}</span>
          </button>
        </div>
      </aside>

      <Modal
        open={!!pendingPath}
        onClose={closePendingPathModal}
        title={t("sidebar.unsavedModal.title")}
        description={t("sidebar.unsavedModal.description")}
        size="md"
        footer={
          <>
            <button
              type="button"
              onClick={closePendingPathModal}
              className="px-3 py-2 text-[11px] font-semibold text-slate-600 hover:bg-slate-100 rounded-lg"
            >
              {t("sidebar.unsavedModal.stay")}
            </button>
            <button
              type="button"
              onClick={leaveToPendingPath}
              className="px-3 py-2 text-[11px] font-semibold text-rose-600 hover:bg-rose-50 rounded-lg"
            >
              {t("sidebar.unsavedModal.leaveWithoutSaving")}
            </button>
            <button
              type="button"
              onClick={handleSaveAndLeave}
              disabled={savingBeforeLeave}
              className="px-3 py-2 bg-brand-orange hover:bg-brand-orange/90 text-white text-[11px] font-bold rounded-lg disabled:opacity-50"
            >
              {savingBeforeLeave ? t("sidebar.unsavedModal.saving") : t("sidebar.unsavedModal.saveAndLeave")}
            </button>
          </>
        }
      >
        <div className="space-y-2.5">
          <p className="text-[11px] text-slate-500">{t("sidebar.unsavedModal.warning")}</p>
          {saveFailureMessage && (
            <div className="text-[11px] font-semibold text-rose-700 bg-rose-50 border border-rose-100 rounded-lg p-2.5">
              {saveFailureMessage}
            </div>
          )}
        </div>
      </Modal>
    </>
  );
}
