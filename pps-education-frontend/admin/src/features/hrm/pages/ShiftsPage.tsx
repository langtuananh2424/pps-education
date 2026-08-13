import React from "react";
import ShiftsTab from "../components/ShiftsTab";

/** Bổ sung 2026-08-13 — quản lý danh mục ca & gán ca cho nhân sự (dưới UC-09/FR-HRM-02). */
export default function ShiftsPage() {
  return (
    <div className="space-y-6">
      <div className="border-b border-slate-200 pb-4">
        <h1 className="text-xl font-bold font-display tracking-tight text-slate-900">Ca làm việc</h1>
        <p className="text-xs text-slate-500 mt-1">
          Tạo danh mục ca chuẩn và gán ca cho nhân viên (đơn lẻ hoặc hàng loạt) — dữ liệu này quyết định cửa sổ chấm công hợp lệ (UC-09).
        </p>
      </div>

      <div className="bg-white rounded-xl border border-slate-200 shadow-soft p-5">
        <ShiftsTab />
      </div>
    </div>
  );
}
