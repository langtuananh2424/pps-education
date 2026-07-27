import React from "react";
import UnderDevelopment from "@/components/ui/UnderDevelopment";
// Nhân sự đọc từ localStorage seed sẵn mockEmployees (../storage), lương tính tay bằng công thức
// giả lập tại chỗ, không phải API thật — tạm ẩn theo yêu cầu người dùng (2026-07-27), sẽ phát
// triển tiếp ở giai đoạn sau.
// import { useState } from "react";
// import { UserRole } from "@/types";
// import { readStoredEmployees } from "../storage";
// import TableContainer, { Td, Th } from "@/components/ui/TableContainer";

export default function PayrollPage() {
  return (
    <div className="space-y-6">
      <div className="border-b border-slate-200 pb-4">
        <h1 className="text-xl font-bold font-display tracking-tight text-slate-900">Phân hệ Quản lý Nhân sự & Tiền lương (HRM)</h1>
        <p className="text-xs text-slate-500 mt-1">Tính toán bảng lương tự động dựa trên hợp đồng và ngày công (UC-12).</p>
      </div>

      <UnderDevelopment title="Tính toán bảng lương (UC-12)" />
    </div>
  );
}
