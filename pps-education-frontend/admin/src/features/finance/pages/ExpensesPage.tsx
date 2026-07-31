import React from "react";
import UnderDevelopment from "@/components/ui/UnderDevelopment";
// 100% mock data (mockExpenses, mockCampuses, form ghi nhận chi phí chỉ set vào state cục bộ,
// không gọi API) — tạm ẩn theo yêu cầu người dùng (2026-07-23), sẽ phát triển tiếp ở giai đoạn
// sau, không xoá logic cũ.
// import { useState } from "react";
// import { Plus, Save } from "lucide-react";
// import { Expense } from "@/types";
// import { mockCampuses, mockExpenses } from "@/data/mockData";
// import Card from "@/components/ui/Card";
// import Button from "@/components/ui/Button";
// import TableContainer, { Td, Th } from "@/components/ui/TableContainer";

export default function ExpensesPage() {
  return (
    <div className="space-y-6">
      <div className="border-b border-slate-200 pb-4">
        <h1 className="text-xl font-bold font-display tracking-tight text-slate-900">Quản Lý Tài Chính & Sổ Cái Kế Toán</h1>
        <p className="text-xs text-slate-500 mt-1">Theo dõi thu chi phân cấp cơ sở.</p>
      </div>

      <UnderDevelopment title="Chi phí vận hành" />
    </div>
  );
}
