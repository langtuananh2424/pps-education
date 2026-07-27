import React from "react";
import UnderDevelopment from "@/components/ui/UnderDevelopment";
// 100% mock data (mockCampuses/mockExpenses/mockInvoices) — tạm ẩn theo yêu cầu người dùng
// (2026-07-27), sẽ phát triển tiếp ở giai đoạn sau.
// import { mockCampuses, mockExpenses, mockInvoices } from "@/data/mockData";
// import Card from "@/components/ui/Card";

export default function ReportsPage() {
  return (
    <div className="space-y-6">
      <div className="border-b border-slate-200 pb-4">
        <h1 className="text-xl font-bold font-display tracking-tight text-slate-900">Quản Lý Tài Chính & Sổ Cái Kế Toán</h1>
        <p className="text-xs text-slate-500 mt-1">Báo cáo dòng tiền tổng hợp & phân cấp theo cơ sở (UC-32).</p>
      </div>

      <UnderDevelopment title="Báo cáo kế toán (UC-32)" />
    </div>
  );
}
