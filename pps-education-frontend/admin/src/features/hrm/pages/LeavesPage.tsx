import React from "react";
import UnderDevelopment from "@/components/ui/UnderDevelopment";
// Dữ liệu đơn từ/nhân sự đọc từ localStorage seed sẵn mock (mockLeaveRequests/mockEmployees qua
// ../storage), không phải API thật — tạm ẩn theo yêu cầu người dùng (2026-07-27), sẽ phát triển
// tiếp ở giai đoạn sau, không xoá component cũ (LeaveRequestForm/LeaveApprovalQueue).
// import { useEffect, useState } from "react";
// import { LeaveRequest, UserRole } from "@/types";
// import { readStoredEmployees, readStoredLeaveRequests, writeStoredLeaveRequests } from "../storage";
// import { useApp } from "@/context/AppContext";
// import { roleLabels } from "@/constants/roles";
// import { useToast } from "@/lib/useToast";
// import Toast from "@/components/ui/Toast";
// import LeaveRequestForm from "../components/LeaveRequestForm";
// import LeaveApprovalQueue from "../components/LeaveApprovalQueue";

export default function LeavesPage() {
  return (
    <div className="space-y-6">
      <div className="border-b border-slate-200 pb-4">
        <h1 className="text-xl font-bold font-display tracking-tight text-slate-900">Phân hệ Quản lý Nhân sự & Tiền lương (HRM)</h1>
        <p className="text-xs text-slate-500 mt-1">Nộp đơn nghỉ phép/đi muộn và xét duyệt theo luồng phân cấp (UC-10/11).</p>
      </div>

      <UnderDevelopment title="Phê duyệt đơn từ (UC-11)" />
    </div>
  );
}
