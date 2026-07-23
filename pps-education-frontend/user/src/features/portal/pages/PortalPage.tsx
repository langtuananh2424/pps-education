import React, { useEffect, useState } from "react";
import { BookOpen, Calendar, CreditCard, Home, LogOut, Award, School, Users, Menu, X } from "lucide-react";
import { ApiError } from "@/lib/apiClient";
import { useApp } from "@/context/AppContext";
import { ChildResponse, listClassOptions, listMyChildren, PortalClassOptionResponse } from "../api";
import HomeTab from "../components/HomeTab";
import PortalDropdown from "../components/PortalDropdown";
import ScheduleTab from "../components/ScheduleTab";
import StudentScheduleTab from "../components/StudentScheduleTab";
import AssignmentsTab from "../components/AssignmentsTab";
import GradesTab from "../components/GradesTab";
import BillingTab from "../components/BillingTab";
import LmsTab from "../components/LmsTab";
import ComingSoon from "../components/ComingSoon";
import ProfileModal from "../components/ProfileModal";

type Tab = "home" | "schedule" | "lms" | "grades" | "billing";

const TABS: { key: Tab; label: string; icon: React.ComponentType<{ size?: number }> }[] = [
  { key: "home", label: "Trang chủ & Bảng tin", icon: Home },
  { key: "schedule", label: "Lịch học & Chuyên cần", icon: Calendar },
  { key: "lms", label: "E-Learning & LMS", icon: BookOpen },
  { key: "grades", label: "Khảo thí & Điểm số", icon: Award },
  { key: "billing", label: "Học phí & Dịch vụ", icon: CreditCard }
];

/**
 * UC-42 mở self-access cho học sinh ở /portal/students/{id}/class-options,
 * /auth/me (studentId), và giờ có thêm /students/me/sessions (UC-59, lịch học
 * — xem StudentScheduleTab). 4 API còn lại (grades/attendance/comments/
 * invoices/my, thuộc ParentPortalService/InvoiceService) vẫn chỉ chấp nhận
 * Phụ huynh (requireLinkedParent/parentOrThrow), gọi bằng tài khoản Học sinh
 * sẽ 403 — các tab đó vẫn hiện ComingSoon, chờ BE áp dụng cùng pattern
 * requireOwnerOrLinkedParent cho các Service đó.
 */
export default function PortalPage() {
  const { currentUser, isParent, isStudent, logout } = useApp();
  const [activeTab, setActiveTab] = useState<Tab>(() => (isParent ? "home" : "lms"));

  const [children, setChildren] = useState<ChildResponse[]>([]);
  const [selectedChildId, setSelectedChildId] = useState<number | null>(null);
  const [classOptions, setClassOptions] = useState<PortalClassOptionResponse[]>([]);
  const [selectedClassId, setSelectedClassId] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [profileOpen, setProfileOpen] = useState(false);
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);

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
      <nav className="bg-white border-b border-line sticky top-0 z-50 shadow-sm w-full py-2.5">
        <div className="w-full max-w-[1560px] mx-auto px-4 md:px-8 xl:px-12 flex items-center justify-between">
          <div className="flex items-center gap-3">
            {!noViewerData && !loading && (
              <button
                onClick={() => setMobileMenuOpen(true)}
                className="lg:hidden shrink-0 w-9 h-9 rounded-xl bg-sky-2 border border-line flex items-center justify-center text-ink hover:bg-sky transition-colors"
                aria-label="Mở menu"
              >
                <Menu size={18} />
              </button>
            )}
            <div className="w-10 h-10 rounded-xl bg-teal border-2 border-teal-deep flex items-center justify-center shadow-[0_3px_0_var(--teal-deep)]">
              <span className="font-display font-extrabold text-white text-xl">P</span>
            </div>
            <div className="leading-tight">
              <div className="font-extrabold text-[15.5px] text-ink">PPS Education</div>
              <div className="text-[11px] tracking-[0.14em] text-teal-deep font-extrabold">
                {isParent ? "PORTAL PHỤ HUYNH" : "PORTAL HỌC SINH"}
              </div>
            </div>
          </div>
          <div className="flex items-center gap-4">
            {selectedChildId && (
              <button
                onClick={() => setProfileOpen(true)}
                className="flex items-center gap-2.5 pl-2 pr-3.5 py-1.5 bg-sky-2 hover:bg-sky border border-line rounded-[16px] transition-colors"
              >
                <div className="w-7 h-7 rounded-full bg-teal/15 border border-teal/30 flex items-center justify-center text-teal-deep font-extrabold text-xs shrink-0">
                  {(viewerName || "?").charAt(0).toUpperCase()}
                </div>
                <div className="text-left leading-tight hidden sm:block">
                  <div className="text-[9px] text-muted font-extrabold uppercase tracking-wide">
                    {isParent ? "Học viên" : "Học sinh"}
                  </div>
                  <div className="text-xs font-extrabold text-ink">{viewerName}</div>
                </div>
              </button>
            )}
            <button
              onClick={() => logout()}
              className="flex items-center gap-1.5 px-4 py-1.5 bg-white border-2 border-coral text-coral font-bold text-xs rounded-[16px]"
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
                <span className="text-xs font-extrabold uppercase tracking-wide text-muted">Menu</span>
                <button
                  onClick={() => setMobileMenuOpen(false)}
                  className="w-8 h-8 rounded-full bg-sky-2 border border-line flex items-center justify-center text-ink hover:bg-sky transition-colors"
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
                    options={classOptions.map((c) => ({ value: c.classId, label: c.className }))}
                  />
                )}

                <div className="space-y-3">
                  {/* Học phí & Dịch vụ (invoices/thanh toán) chỉ dành Phụ huynh — Học sinh không cần/không nên xem thông tin tài chính của gia đình. */}
                  {TABS.filter((tab) => tab.key !== "billing" || isParent).map(({ key, label, icon: Icon }) => (
                    <button
                      key={key}
                      onClick={() => {
                        setActiveTab(key);
                        setMobileMenuOpen(false);
                      }}
                      className={`w-full flex items-center gap-3 px-4 py-3 rounded-[16px] font-bold text-sm transition-all border ${
                        activeTab === key
                          ? "bg-teal text-white border-teal-deep shadow-[0_4px_12px_rgba(23,166,160,0.2)]"
                          : "bg-slate-50/50 hover:bg-slate-50 text-muted border-line/60"
                      }`}
                    >
                      <Icon size={18} /> {label}
                    </button>
                  ))}
                </div>
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
                      <HomeTab
                        studentName={selectedChild.studentFullName}
                        commentsSource={{ studentId: selectedChild.studentId, classId: selectedClassId }}
                      />
                    ) : isStudent ? (
                      // Thông báo (GET /notifications) đã self-service thật cho mọi vai trò — hiện được ngay.
                      // Nhận xét/cảnh báo giáo viên vẫn chưa có API tự xem (commentsSource=null, xem HomeTab).
                      <HomeTab studentName={viewerName} commentsSource={null} />
                    ) : (
                      <ComingSoon title="Trang chủ & Bảng tin" description="Không có hồ sơ Học sinh hoặc Phụ huynh liên kết với tài khoản này." />
                    ))}
                  {activeTab === "schedule" &&
                    (isParent && selectedChild ? (
                      <ScheduleTab studentId={selectedChild.studentId} classId={selectedClassId} />
                    ) : isStudent ? (
                      <StudentScheduleTab />
                    ) : (
                      <ComingSoon
                        title="Lịch học & Chuyên cần"
                        description="Đang chờ Backend mở API cho Học sinh tự xem lịch học/chuyên cần của chính mình (hiện chỉ Phụ huynh xem được)."
                      />
                    ))}
                  {activeTab === "lms" && (
                    <div className="space-y-6">
                      {/* GET /students/me/exercises tra theo userId của chính người gọi — chỉ hoạt động cho Học sinh tự đăng nhập, Phụ huynh gọi sẽ 404 "không có hồ sơ học sinh" nên chỉ hiện với isStudent. */}
                      {isStudent && <AssignmentsTab classId={selectedClassId} />}
                      <LmsTab classId={selectedClassId} />
                    </div>
                  )}
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
