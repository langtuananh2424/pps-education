import React from "react";
import UnderDevelopment from "@/components/ui/UnderDevelopment";
// 100% mock data (mockAttendanceLogs, nhân sự đọc từ localStorage seed sẵn mockEmployees, tách
// biệt khỏi API EmployeeResponse thật) — tạm ẩn theo yêu cầu người dùng (2026-07-27), sẽ phát
// triển tiếp ở giai đoạn sau.
// import { useState } from "react";
// import { Fingerprint, Play } from "lucide-react";
// import { AttendanceLog } from "@/types";
// import { mockAttendanceLogs } from "@/data/mockData";
// import { readStoredEmployees } from "../storage";
// import TableContainer, { Td, Th } from "@/components/ui/TableContainer";
// import Badge from "@/components/ui/Badge";

export default function AttendancePage() {
  return (
    <div className="space-y-6">
      <div className="border-b border-slate-200 pb-4">
        <h1 className="text-xl font-bold font-display tracking-tight text-slate-900">Phân hệ Quản lý Nhân sự & Tiền lương (HRM)</h1>
        <p className="text-sm text-slate-500 mt-1">Dữ liệu chấm công thực tế đa phương thức (vân tay, khuôn mặt, GPS).</p>
      </div>

      <UnderDevelopment title="Dữ liệu chấm công" />
    </div>
  );
}
