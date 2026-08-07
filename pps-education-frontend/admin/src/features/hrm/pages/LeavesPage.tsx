import React, { useEffect, useState } from "react";
import { History, Repeat } from "lucide-react";
import Badge from "@/components/ui/Badge";
import { ApiError } from "@/lib/apiClient";
import { useToast } from "@/lib/useToast";
import Toast from "@/components/ui/Toast";
import LeaveRequestForm from "../components/LeaveRequestForm";
import LeaveApprovalQueue from "../components/LeaveApprovalQueue";
import {
  LeaveRequestResponse,
  LeaveSubstitutionResponse,
  listLeaveSubstitutionHistory,
  listMyLeaveRequests,
  listPendingLeaveRequestsForApprover
} from "../api";

const leaveTypeLabels: Record<LeaveRequestResponse["leaveType"], string> = {
  ANNUAL: "Nghỉ phép năm",
  SICK: "Nghỉ ốm",
  UNPAID: "Nghỉ không lương",
  PERSONAL: "Nghỉ việc riêng",
  LATE: "Đi trễ",
  EARLY_LEAVE: "Về sớm"
};

const statusVariant: Record<LeaveRequestResponse["status"], "success" | "danger" | "warning" | "neutral"> = {
  APPROVED: "success",
  REJECTED: "danger",
  PENDING: "warning",
  CANCELLED: "neutral"
};

const statusLabels: Record<LeaveRequestResponse["status"], string> = {
  PENDING: "Chờ duyệt",
  APPROVED: "Đã duyệt",
  REJECTED: "Từ chối",
  CANCELLED: "Đã huỷ"
};

export default function LeavesPage() {
  const [pending, setPending] = useState<LeaveRequestResponse[]>([]);
  const [pendingLoading, setPendingLoading] = useState(false);
  const [mine, setMine] = useState<LeaveRequestResponse[]>([]);
  const [mineLoading, setMineLoading] = useState(false);
  const [substitutions, setSubstitutions] = useState<LeaveSubstitutionResponse[]>([]);
  const { message: toastMessage, showToast } = useToast();

  const loadPending = () => {
    setPendingLoading(true);
    listPendingLeaveRequestsForApprover()
      .then(setPending)
      .catch(() => setPending([]))
      .finally(() => setPendingLoading(false));
  };

  const loadMine = () => {
    setMineLoading(true);
    listMyLeaveRequests()
      .then(setMine)
      .catch(() => setMine([]))
      .finally(() => setMineLoading(false));
  };

  const loadSubstitutions = () => {
    listLeaveSubstitutionHistory()
      .then(setSubstitutions)
      .catch((err) => {
        // Không phải ai cũng cần thấy mục này — bỏ qua lỗi 403 âm thầm, chỉ log các lỗi khác.
        if (!(err instanceof ApiError) || err.status !== 403) setSubstitutions([]);
      });
  };

  useEffect(() => {
    loadPending();
    loadMine();
    loadSubstitutions();
  }, []);

  return (
    <div className="space-y-6">
      <div className="border-b border-slate-200 pb-4">
        <h1 className="text-xl font-bold font-display tracking-tight text-slate-900">Xin Nghỉ Phép / Đi Muộn</h1>
        <p className="text-xs text-slate-500 mt-1">Nộp đơn xin nghỉ phép, đi muộn, về sớm và theo dõi quy trình xét duyệt.</p>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        <LeaveRequestForm
          onSubmitted={() => {
            showToast("Đã gửi đơn từ thành công!");
            loadMine();
            loadSubstitutions();
          }}
        />
        <LeaveApprovalQueue
          leaveRequests={pending}
          loading={pendingLoading}
          onDecided={() => {
            loadPending();
            loadSubstitutions();
          }}
        />
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <div className="bg-white rounded-xl border border-slate-200 shadow-soft overflow-hidden">
          <div className="px-5 py-3 border-b border-slate-100 bg-slate-50">
            <span className="text-xs font-bold text-slate-700 font-display block">Lịch sử đơn của tôi</span>
            <p className="text-[10px] text-slate-400">Các đơn xin nghỉ bạn đã nộp và trạng thái duyệt.</p>
          </div>
          <div className="divide-y divide-slate-100 max-h-96 overflow-y-auto">
            {mineLoading ? (
              <div className="p-6 text-center text-xs text-slate-400">Đang tải...</div>
            ) : mine.length === 0 ? (
              <div className="p-6 text-center text-xs text-slate-400">Bạn chưa nộp đơn nào.</div>
            ) : (
              mine.map((req) => (
                <div key={req.id} className="p-3 space-y-1">
                  <div className="flex items-center justify-between gap-2">
                    <span className="text-xs font-bold text-slate-800">{leaveTypeLabels[req.leaveType]}</span>
                    <Badge variant={statusVariant[req.status]}>{statusLabels[req.status]}</Badge>
                  </div>
                  <p className="text-[11px] text-slate-500 italic">"{req.reason}"</p>
                  <p className="text-[10px] text-slate-400">
                    {req.startDate} đến {req.endDate} · {req.totalDays} ngày
                  </p>
                </div>
              ))
            )}
          </div>
        </div>

        <div className="bg-white rounded-xl border border-slate-200 shadow-soft overflow-hidden">
          <div className="px-5 py-3 border-b border-slate-100 bg-slate-50 flex items-center gap-2">
            <History className="w-3.5 h-3.5 text-slate-400 shrink-0" />
            <div>
              <span className="text-xs font-bold text-slate-700 font-display block">Lịch sử giáo viên dạy thay</span>
              <p className="text-[10px] text-slate-400">Giáo viên dạy thay được gán theo đơn xin nghỉ của bạn.</p>
            </div>
          </div>
          <div className="divide-y divide-slate-100 max-h-96 overflow-y-auto">
            {substitutions.length === 0 ? (
              <div className="p-6 text-center text-xs text-slate-400">Chưa có lượt dạy thay nào.</div>
            ) : (
              substitutions.map((s) => (
                <div key={s.id} className="p-3 space-y-1">
                  <div className="flex items-center justify-between gap-2">
                    <span className="text-xs font-bold text-slate-800 flex items-center gap-1.5">
                      <Repeat className="w-3 h-3 text-slate-400" />
                      {s.className}
                    </span>
                    <Badge variant={s.revokedAt ? "neutral" : "success"}>{s.revokedAt ? "Đã thu hồi" : "Đang dạy thay"}</Badge>
                  </div>
                  <p className="text-[11px] text-slate-500">
                    {s.substituteTeacherName} <span className="text-slate-400">dạy thay cho</span> {s.originalTeacherName}
                  </p>
                  <p className="text-[10px] text-slate-400">Buổi ngày {s.sessionDate}</p>
                </div>
              ))
            )}
          </div>
        </div>
      </div>

      <Toast message={toastMessage} />
    </div>
  );
}
