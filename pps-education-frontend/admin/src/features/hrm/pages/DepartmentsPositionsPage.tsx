import React, { useState } from "react";
import { Building2, Briefcase } from "lucide-react";
import DepartmentsTab from "../components/DepartmentsTab";
import PositionsTab from "../components/PositionsTab";

type Tab = "departments" | "positions";

/** Màn hình cấu hình riêng (thường ẩn trong Admin) — không phải màn hình tạo nhân viên hàng ngày. */
export default function DepartmentsPositionsPage() {
  const [tab, setTab] = useState<Tab>("departments");

  return (
    <div className="space-y-6">
      <div className="border-b border-slate-200 pb-4">
        <h1 className="text-xl font-bold font-display tracking-tight text-slate-900">Phòng ban & Chức vụ</h1>
        <p className="text-xs text-slate-500 mt-1">
          Cấu hình danh mục Phòng ban, Chức vụ và role mặc định theo chức vụ — cần cấu hình ở đây trước khi chọn được trong hồ sơ nhân sự.
        </p>
      </div>

      <div className="flex border-b border-slate-200 gap-5">
        {(
          [
            ["departments", "Phòng ban", Building2],
            ["positions", "Chức vụ", Briefcase]
          ] as const
        ).map(([key, label, Icon]) => (
          <button
            key={key}
            onClick={() => setTab(key)}
            className={`pb-2.5 text-xs font-bold border-b-2 flex items-center gap-1.5 transition-all ${
              tab === key ? "border-brand-red text-brand-red" : "border-transparent text-slate-500 hover:text-slate-700"
            }`}
          >
            <Icon className="w-3.5 h-3.5" />
            {label}
          </button>
        ))}
      </div>

      <div className="bg-white rounded-xl border border-slate-200 shadow-soft p-5">
        {tab === "departments" ? <DepartmentsTab /> : <PositionsTab />}
      </div>
    </div>
  );
}
