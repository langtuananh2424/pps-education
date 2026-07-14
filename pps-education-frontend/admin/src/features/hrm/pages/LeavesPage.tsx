import React, { useEffect, useState } from "react";
import { LeaveRequest, UserRole } from "@/types";
import { readStoredEmployees, readStoredLeaveRequests, writeStoredLeaveRequests } from "../storage";
import { useApp } from "@/context/AppContext";
import { roleLabels } from "@/constants/roles";
import { useToast } from "@/lib/useToast";
import Toast from "@/components/ui/Toast";
import LeaveRequestForm from "../components/LeaveRequestForm";
import LeaveApprovalQueue from "../components/LeaveApprovalQueue";

export default function LeavesPage() {
  const { currentUser, currentRole } = useApp();
  const [leaveRequests, setLeaveRequests] = useState<LeaveRequest[]>(readStoredLeaveRequests);
  const { message, showToast } = useToast();

  useEffect(() => {
    writeStoredLeaveRequests(leaveRequests);
  }, [leaveRequests]);

  const handleApplyLeave = (input: { type: "LEAVE" | "LATE" | "EARLY"; startDate: string; endDate: string; reason: string }) => {
    const employees = readStoredEmployees();
    const applicant = employees.find((emp) => emp.email === currentUser?.email) || employees.find((emp) => emp.id === "EMP-008");
    if (!applicant) return;

    const newReq: LeaveRequest = {
      id: `LEV-${Date.now().toString().substring(8)}`,
      employeeId: applicant.id,
      employeeName: applicant.fullName,
      departmentName: applicant.role === UserRole.TEACHER ? "Tổ chuyên môn Sư phạm" : "Phòng Hành chính - Vận hành",
      type: input.type,
      startDate: input.startDate,
      endDate: input.endDate,
      reason: input.reason,
      steps: [
        { role: UserRole.SITE_MANAGER, status: "PENDING" },
        { role: UserRole.OPS_MANAGER, status: "PENDING" }
      ],
      currentStepIndex: 0,
      status: "PENDING",
      createdAt: new Date().toISOString().substring(0, 10)
    };

    setLeaveRequests((prev) => [newReq, ...prev]);
    showToast("Đã nộp đơn xin nghỉ phép thành công!");
  };

  const handleVoteLeaveStep = (reqId: string, vote: "APPROVED" | "REJECTED", notes: string) => {
    const approverName = currentUser ? `${currentUser.fullName} (${roleLabels[currentRole]})` : "Lăng Tuấn Anh (SYS_ADMIN/BGĐ)";

    setLeaveRequests((prev) =>
      prev.map((req) => {
        if (req.id !== reqId) return req;

        const updatedSteps = req.steps.map((st, idx) =>
          idx === req.currentStepIndex ? { ...st, status: vote, notes, approverName, updatedAt: new Date().toISOString().replace("T", " ").substring(0, 16) } : st
        );

        let nextStepIndex = req.currentStepIndex;
        let finalStatus = req.status;

        if (vote === "REJECTED") {
          finalStatus = "REJECTED";
        } else if (req.currentStepIndex < req.steps.length - 1) {
          nextStepIndex += 1;
          finalStatus = "PENDING";
        } else {
          finalStatus = "APPROVED";
        }

        return { ...req, steps: updatedSteps, currentStepIndex: nextStepIndex, status: finalStatus };
      })
    );

    showToast(vote === "APPROVED" ? "Đã phê duyệt đơn thành công!" : `Đã từ chối đơn với lý do: "${notes}"`);
  };

  return (
    <div className="space-y-6">
      <div className="border-b border-slate-200 pb-4">
        <h1 className="text-xl font-bold font-display tracking-tight text-slate-900">Phân hệ Quản lý Nhân sự & Tiền lương (HRM)</h1>
        <p className="text-xs text-slate-500 mt-1">Nộp đơn nghỉ phép/đi muộn và xét duyệt theo luồng phân cấp (UC-10/11).</p>
      </div>

      <Toast message={message} />

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        <LeaveRequestForm onSubmit={handleApplyLeave} />
        <LeaveApprovalQueue leaveRequests={leaveRequests} onVote={handleVoteLeaveStep} />
      </div>
    </div>
  );
}
