import React, { useState } from "react";
import { ArrowLeft, Check, FileText } from "lucide-react";
import { UserRole } from "@/types";
import { mockEmployees, mockLeaveRequests } from "@/data/mockData";
import DatePicker from "@/components/ui/DatePicker";
import Select from "@/components/ui/Select";
import { useDialog } from "@/components/ui/DialogProvider";

interface PublicLeaveRequestFormProps {
  onClose: () => void;
}

export default function PublicLeaveRequestForm({ onClose }: PublicLeaveRequestFormProps) {
  const [empId, setEmpId] = useState(mockEmployees[4]?.id || mockEmployees[0]?.id || "");
  const [leaveType, setLeaveType] = useState<"LEAVE" | "LATE" | "EARLY">("LEAVE");
  const [startDate, setStartDate] = useState("2026-07-15");
  const [endDate, setEndDate] = useState("2026-07-16");
  const [reason, setReason] = useState("");
  const [success, setSuccess] = useState(false);
  const { alertDialog } = useDialog();

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!reason.trim()) {
      await alertDialog("Vui lòng điền đầy đủ lý do nghỉ phép.");
      return;
    }

    const matchedEmp = mockEmployees.find((emp) => emp.id === empId);
    if (!matchedEmp) return;

    const savedRequestsRaw = localStorage.getItem("pps_leave_requests");
    let currentRequests: unknown[] = mockLeaveRequests;
    if (savedRequestsRaw) {
      try {
        currentRequests = JSON.parse(savedRequestsRaw);
      } catch {
        currentRequests = [];
      }
    }

    const newRequest = {
      id: `LEV-PUB-${Date.now().toString().substring(8)}`,
      employeeId: matchedEmp.id,
      employeeName: matchedEmp.fullName,
      departmentName: matchedEmp.role === UserRole.TEACHER ? "Tổ chuyên môn Sư phạm" : "Phòng Hành chính - Vận hành",
      type: leaveType,
      startDate,
      endDate,
      reason,
      steps: [
        { role: UserRole.SITE_MANAGER, status: "PENDING" },
        { role: UserRole.OPS_MANAGER, status: "PENDING" }
      ],
      currentStepIndex: 0,
      status: "PENDING",
      createdAt: new Date().toISOString().substring(0, 10),
      isPublicSubmitted: true
    };

    localStorage.setItem("pps_leave_requests", JSON.stringify([newRequest, ...currentRequests]));
    setSuccess(true);
  };

  return (
    <div className="space-y-4 animate-in fade-in duration-200">
      <div className="flex items-center gap-2.5 mb-2">
        <button
          type="button"
          onClick={onClose}
          className="p-1.5 rounded-full text-slate-400 hover:bg-slate-100 hover:text-slate-800 transition-colors"
        >
          <ArrowLeft className="w-4 h-4" />
        </button>
        <div>
          <h3 className="text-sm font-bold text-slate-850 uppercase tracking-wide">Nộp đơn nghỉ phép nhanh</h3>
          <p className="text-sm text-slate-400 font-medium">Bypass đăng nhập • Đồng bộ ERP</p>
        </div>
      </div>

      {success ? (
        <div className="space-y-4 text-center py-2 animate-in zoom-in-95 duration-200">
          <div className="w-12 h-12 bg-emerald-50 text-emerald-600 rounded-full flex items-center justify-center mx-auto border border-emerald-100 shadow-sm">
            <Check className="w-5 h-5 stroke-[3]" />
          </div>
          <div className="space-y-1">
            <h4 className="text-sm font-bold text-slate-900 uppercase">Gửi đơn thành công!</h4>
            <p className="text-sm text-slate-500 leading-relaxed font-medium">Đơn đã được đồng bộ tới quản lý trực tiếp phê duyệt.</p>
          </div>

          <div className="pt-2 flex flex-col gap-2">
            <button
              type="button"
              onClick={() => {
                setSuccess(false);
                setReason("");
              }}
              className="w-full bg-[#EA580C] hover:bg-[#D94E07] text-white font-bold text-sm py-2.5 rounded-full transition-all cursor-pointer text-center shadow-sm"
            >
              Nộp thêm đơn khác
            </button>
            <button
              type="button"
              onClick={onClose}
              className="w-full bg-slate-100 hover:bg-slate-200 text-slate-600 font-bold text-sm py-2.5 rounded-full transition-all cursor-pointer text-center"
            >
              Quay lại Trang Đăng nhập
            </button>
          </div>
        </div>
      ) : (
        <form onSubmit={handleSubmit} className="space-y-3">
          <div className="space-y-1">
            <label className="text-sm uppercase font-bold tracking-wider text-slate-500 block">Chọn Cán bộ / Giảng viên nộp đơn</label>
            <Select
              value={empId}
              onChange={(e) => setEmpId(e.target.value)}
              className="w-full bg-slate-50/50 border border-slate-200 text-sm px-4 py-2.5 rounded-xl focus:outline-none focus:ring-1 focus:ring-[#EA580C]"
            >
              {mockEmployees.map((emp) => (
                <option key={emp.id} value={emp.id}>
                  {emp.fullName} ({emp.role === UserRole.TEACHER ? "Giáo viên" : "Hành chính"})
                </option>
              ))}
            </Select>
          </div>

          <div className="space-y-1">
            <label className="text-sm uppercase font-bold tracking-wider text-slate-500 block">Hình thức xin nghỉ</label>
            <Select
              value={leaveType}
              onChange={(e) => setLeaveType(e.target.value as "LEAVE" | "LATE" | "EARLY")}
              className="w-full bg-slate-50/50 border border-slate-200 text-sm px-4 py-2.5 rounded-xl focus:outline-none focus:ring-1 focus:ring-[#EA580C]"
            >
              <option value="LEAVE">Nghỉ phép thường niên (Full-day)</option>
              <option value="LATE">Đi trễ có lý do (Late morning)</option>
              <option value="EARLY">Về sớm cá nhân (Early leave)</option>
            </Select>
          </div>

          <div className="grid grid-cols-2 gap-2">
            <div className="space-y-1">
              <label className="text-sm uppercase font-bold tracking-wider text-slate-500 block">Từ ngày</label>
              <DatePicker value={startDate} onChange={setStartDate} max={endDate || undefined} />
            </div>
            <div className="space-y-1">
              <label className="text-sm uppercase font-bold tracking-wider text-slate-500 block">Đến ngày</label>
              <DatePicker value={endDate} onChange={setEndDate} min={startDate || undefined} />
            </div>
          </div>

          <div className="space-y-1">
            <label className="text-sm uppercase font-bold tracking-wider text-slate-500 block">Lý do xin nghỉ</label>
            <textarea
              required
              rows={2}
              placeholder="Nêu lý do chi tiết..."
              value={reason}
              onChange={(e) => setReason(e.target.value)}
              className="w-full bg-slate-50/50 border border-slate-200 text-sm px-4 py-2 rounded-xl focus:outline-none focus:ring-1 focus:ring-[#EA580C] text-slate-850"
            />
          </div>

          <button
            type="submit"
            className="w-full bg-[#EA580C] hover:bg-[#D94E07] text-white font-bold text-sm py-3 rounded-full flex items-center justify-center gap-1.5 shadow-md mt-4 transition-all"
          >
            <FileText className="w-4 h-4 text-white" />
            <span>Gửi đơn nghỉ phép nhanh</span>
          </button>
        </form>
      )}
    </div>
  );
}
