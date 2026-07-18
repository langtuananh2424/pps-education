import React, { useEffect, useState } from "react";
import { BookOpen, Calendar, CreditCard, Home, LogOut, Award, School } from "lucide-react";
import { ApiError } from "@/lib/apiClient";
import { useApp } from "@/context/AppContext";
import { ChildResponse, listClassOptions, listMyChildren, PortalClassOptionResponse } from "../api";
import HomeTab from "../components/HomeTab";
import ScheduleTab from "../components/ScheduleTab";
import GradesTab from "../components/GradesTab";
import BillingTab from "../components/BillingTab";
import LmsTab from "../components/LmsTab";

type Tab = "home" | "schedule" | "lms" | "grades" | "billing";

const TABS: { key: Tab; label: string; icon: React.ComponentType<{ size?: number }> }[] = [
  { key: "home", label: "Trang chủ & Bảng tin", icon: Home },
  { key: "schedule", label: "Lịch học & Chuyên cần", icon: Calendar },
  { key: "lms", label: "E-Learning & LMS", icon: BookOpen },
  { key: "grades", label: "Khảo thí & Điểm số", icon: Award },
  { key: "billing", label: "Học phí & Dịch vụ", icon: CreditCard }
];

export default function PortalPage() {
  const { currentUser, isParent, logout } = useApp();
  const [activeTab, setActiveTab] = useState<Tab>("home");

  const [children, setChildren] = useState<ChildResponse[]>([]);
  const [selectedChildId, setSelectedChildId] = useState<number | null>(null);
  const [classOptions, setClassOptions] = useState<PortalClassOptionResponse[]>([]);
  const [selectedClassId, setSelectedClassId] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (!isParent) {
      setLoading(false);
      return;
    }
    listMyChildren()
      .then((kids) => {
        setChildren(kids);
        if (kids.length > 0) setSelectedChildId(kids[0].studentId);
      })
      .catch((err) => setError(err instanceof ApiError ? err.message : "Không tải được danh sách con."))
      .finally(() => setLoading(false));
  }, [isParent]);

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

  if (!isParent) {
    return (
      <div className="min-h-screen flex items-center justify-center p-8 text-center">
        <div className="bg-white border border-line/80 rounded-[24px] p-10 max-w-md space-y-3">
          <h2 className="text-lg font-extrabold text-ink">Tài khoản chưa hỗ trợ xem Portal</h2>
          <p className="text-xs text-muted font-bold">
            Portal hiện chỉ phục vụ tài khoản Phụ huynh. Học sinh tự đăng nhập xem hồ sơ của chính mình đang chờ Backend bổ sung API — sẽ mở sau.
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
            <div className="w-10 h-10 rounded-xl bg-teal border-2 border-teal-deep flex items-center justify-center shadow-[0_3px_0_var(--teal-deep)]">
              <span className="font-display font-extrabold text-white text-xl">P</span>
            </div>
            <div className="leading-tight">
              <div className="font-extrabold text-[15.5px] text-ink">PPS Education</div>
              <div className="text-[11px] tracking-[0.14em] text-teal-deep font-extrabold">PORTAL PHỤ HUYNH</div>
            </div>
          </div>
          <div className="flex items-center gap-4">
            <span className="text-xs font-bold text-ink hidden sm:inline">{currentUser?.fullName}</span>
            <button
              onClick={() => logout()}
              className="flex items-center gap-1.5 px-4 py-1.5 bg-white border-2 border-coral text-coral font-bold text-xs rounded-[16px]"
            >
              <LogOut size={14} /> Thoát
            </button>
          </div>
        </div>
      </nav>

      <div className="flex-1 w-full max-w-[1560px] mx-auto px-4 md:px-8 xl:px-12 py-8">
        {error && <div className="text-xs font-bold text-rose-600 bg-rose-50 border border-rose-100 p-3 rounded-xl mb-4">{error}</div>}

        {loading ? (
          <p className="text-sm text-muted font-bold">Đang tải...</p>
        ) : children.length === 0 ? (
          <div className="bg-white border border-line/80 rounded-[24px] p-10 text-center text-muted font-bold">
            Chưa có học sinh nào được liên kết với tài khoản của bạn.
          </div>
        ) : (
          <div className="grid grid-cols-1 lg:grid-cols-12 gap-8 items-start">
            <div className="lg:col-span-3 bg-white border border-line/80 rounded-[24px] p-6 shadow-[0_8px_30px_rgba(30,42,69,0.03)] space-y-6">
              {children.length > 1 && (
                <select
                  value={selectedChildId ?? ""}
                  onChange={(e) => setSelectedChildId(Number(e.target.value))}
                  className="w-full text-xs font-extrabold text-ink bg-sky-2 border border-line px-3 py-2.5 rounded-[16px]"
                >
                  {children.map((c) => (
                    <option key={c.studentId} value={c.studentId}>
                      {c.studentFullName} ({c.studentCode})
                    </option>
                  ))}
                </select>
              )}

              {classOptions.length > 1 && (
                <div className="flex items-center gap-2 bg-sky-2 border border-line px-3 py-2 rounded-[16px]">
                  <School size={14} className="text-teal shrink-0" />
                  <select
                    value={selectedClassId ?? ""}
                    onChange={(e) => setSelectedClassId(Number(e.target.value))}
                    className="w-full text-xs font-extrabold text-ink bg-transparent focus:outline-none"
                  >
                    {classOptions.map((c) => (
                      <option key={c.classEnrollmentId} value={c.classId}>
                        {c.className}
                      </option>
                    ))}
                  </select>
                </div>
              )}

              <div className="space-y-3">
                {TABS.map(({ key, label, icon: Icon }) => (
                  <button
                    key={key}
                    onClick={() => setActiveTab(key)}
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

            <div className="lg:col-span-9">
              {!selectedClassId ? (
                <div className="bg-white border border-line/80 rounded-[24px] p-10 text-center text-muted font-bold">
                  Học sinh chưa được xếp vào lớp nào.
                </div>
              ) : (
                <>
                  {activeTab === "home" && selectedChild && (
                    <HomeTab studentId={selectedChild.studentId} classId={selectedClassId} studentName={selectedChild.studentFullName} />
                  )}
                  {activeTab === "schedule" && selectedChild && <ScheduleTab studentId={selectedChild.studentId} classId={selectedClassId} />}
                  {activeTab === "lms" && <LmsTab classId={selectedClassId} />}
                  {activeTab === "grades" && selectedChild && <GradesTab studentId={selectedChild.studentId} classId={selectedClassId} />}
                  {activeTab === "billing" && <BillingTab />}
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
