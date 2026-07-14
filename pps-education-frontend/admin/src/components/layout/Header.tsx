import React from "react";
import { Bell, ChevronDown, Clock, LogOut, Menu, MapPin, ShieldCheck } from "lucide-react";
import { useApp } from "@/context/AppContext";
import { mockCampuses } from "@/data/mockData";
import { roleLabels } from "@/constants/roles";
import Avatar from "@/components/ui/Avatar";
import Dropdown from "@/components/ui/Dropdown";

const notifications = [
  { id: "1", text: "Trường Tiểu học Nghĩa Tân gửi ý kiến đóng góp mới (Cô Hiệu Trưởng)", time: "10 phút trước", type: "urgent" },
  { id: "2", text: "Đơn nghỉ phép của Đỗ Gia Bảo đang chờ duyệt bước 2", time: "1 giờ trước", type: "pending" },
  { id: "3", text: "Điểm thi lớp Nghĩa Tân 3A1 vừa được giáo viên Lê Thu Hà submit", time: "2 giờ trước", type: "info" }
];

export default function Header() {
  const { currentRole, currentUser, selectedCampusId, setSelectedCampusId, sidebarOpen, setSidebarOpen, logout } = useApp();

  return (
    <header className="h-16 bg-transparent px-2 md:px-0 flex items-center justify-between z-30 mb-4 shrink-0">
      <div className="flex items-center gap-4">
        <button
          onClick={() => setSidebarOpen(!sidebarOpen)}
          className="p-2.5 rounded-xl text-slate-500 hover:text-slate-800 bg-white hover:bg-slate-50 border border-slate-200/50 shadow-soft lg:hidden transition-all"
        >
          <Menu className="w-5 h-5" />
        </button>

        <div className="hidden sm:flex items-center gap-2 text-xs font-medium text-slate-500 bg-white border border-slate-200/50 shadow-soft px-4 py-2 rounded-full">
          <MapPin className="w-3.5 h-3.5 text-brand-orange shrink-0" />
          <span className="font-semibold text-slate-700">Điểm trường:</span>
          <select
            value={selectedCampusId}
            onChange={(e) => setSelectedCampusId(e.target.value)}
            className="bg-transparent border-none text-slate-800 font-semibold focus:outline-none focus:ring-0 cursor-pointer pr-1"
          >
            <option value="ALL">Tất cả cơ sở & Trường liên kết</option>
            {mockCampuses.map((campus) => (
              <option key={campus.id} value={campus.id}>
                {campus.name}
              </option>
            ))}
          </select>
        </div>
      </div>

      <div className="flex items-center gap-3 md:gap-5">
        <div className="flex items-center gap-2 bg-slate-900 text-white px-3.5 py-1.5 rounded-md text-xs font-semibold shadow-glow tracking-tight font-display">
          <ShieldCheck className="w-3.5 h-3.5 text-brand-yellow shrink-0" />
          <span className="hidden md:inline">Vai trò:</span>
          <span className="text-brand-orange">{roleLabels[currentRole]}</span>
        </div>

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
          panelClassName="w-64 py-2"
          trigger={
            <button className="flex items-center gap-2 px-3.5 py-1.5 bg-white border border-slate-200/50 hover:bg-slate-50 rounded-full transition-all shadow-soft">
              <Avatar name={currentUser?.fullName || "U"} size="sm" />
              <span className="hidden md:block text-xs font-semibold text-slate-700 truncate max-w-[120px]">
                {currentUser?.fullName || "Cán bộ PPS"}
              </span>
              <ChevronDown className="w-3.5 h-3.5 text-slate-400" />
            </button>
          }
        >
          <div className="px-4 py-2 border-b border-slate-100 bg-slate-50">
            <span className="text-[9px] font-bold text-slate-400 block uppercase font-mono tracking-wider">Nhân sự đăng nhập</span>
            <p className="text-xs font-bold text-slate-800 truncate mt-0.5">{currentUser?.fullName || "Chưa đăng nhập"}</p>
            <p className="text-[10px] text-slate-500 truncate mt-0.5 font-mono">{currentUser?.email || "example@pps.edu.vn"}</p>
            <span className="inline-block mt-2 px-2 py-0.5 text-[9px] font-bold bg-slate-100 border border-slate-200 text-brand-orange rounded">
              {roleLabels[currentRole]}
            </span>
          </div>
          <div className="p-1">
            <button
              onClick={logout}
              className="w-full px-3 py-2 flex items-center gap-2 text-left text-xs font-semibold text-rose-600 hover:bg-rose-50 rounded-md transition-colors cursor-pointer"
            >
              <LogOut className="w-3.5 h-3.5" />
              <span>Đăng xuất hệ thống</span>
            </button>
          </div>
        </Dropdown>
      </div>
    </header>
  );
}
