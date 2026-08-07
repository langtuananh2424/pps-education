import React, { useState } from "react";
import { Check, Clock } from "lucide-react";
import Badge from "@/components/ui/Badge";
import { cn } from "@/lib/cn";
import { useDialog } from "@/components/ui/DialogProvider";
import { ApiError } from "@/lib/apiClient";
import { decideLeaveRequest, LeaveRequestResponse } from "../api";
import { useLeaveTypeLabel } from "../hooks/useLeaveTypeLabel";

interface LeaveApprovalQueueProps {
  leaveRequests: LeaveRequestResponse[];
  loading: boolean;
  onDecided: () => void;
}

const statusLabels: Record<LeaveRequestResponse["status"], string> = {
  PENDING: "Chờ duyệt",
  APPROVED: "Đã duyệt",
  REJECTED: "Từ chối",
  CANCELLED: "Đã huỷ"
};

/** UC-11: Duyệt đơn từ — hàng chờ duyệt thuộc thẩm quyền người gọi (GET /api/leave-requests/pending-for-me). */
export default function LeaveApprovalQueue({ leaveRequests, loading, onDecided }: LeaveApprovalQueueProps) {
  const getLeaveTypeLabel = useLeaveTypeLabel();
  const [opinionNotes, setOpinionNotes] = useState<Record<number, string>>({});
  const [decidingId, setDecidingId] = useState<number | null>(null);
  const { alertDialog } = useDialog();

  const decide = async (req: LeaveRequestResponse, decision: "APPROVED" | "REJECTED", comment?: string) => {
    setDecidingId(req.id);
    try {
      await decideLeaveRequest(req.id, { decision, comment });
      onDecided();
    } catch (err) {
      await alertDialog(err instanceof ApiError ? err.message : "Không thực hiện được quyết định, vui lòng thử lại.");
    } finally {
      setDecidingId(null);
    }
  };

  return (
    <div className="lg:col-span-2 bg-white rounded-xl border border-slate-200 shadow-soft overflow-hidden">
      <div className="px-5 py-3 border-b border-slate-100 bg-slate-50 flex items-center justify-between gap-3">
        <div>
          <span className="text-xs font-bold text-slate-700 font-display block">Hàng chờ xét duyệt</span>
          <p className="text-[10px] text-slate-400">Các đơn xin nghỉ chờ xét duyệt thuộc thẩm quyền của bạn.</p>
        </div>
        <span className="px-2 py-1 rounded-full text-[10px] font-bold bg-brand-orange text-white shrink-0">{leaveRequests.length}</span>
      </div>

      <div className="divide-y divide-slate-100 max-h-[500px] overflow-y-auto">
        {loading ? (
          <div className="p-8 text-center text-xs text-slate-400">Đang tải...</div>
        ) : leaveRequests.length === 0 ? (
          <div className="p-12 text-center space-y-3">
            <div className="w-12 h-12 bg-slate-50 rounded-full flex items-center justify-center mx-auto border border-slate-100 text-slate-400">
              <Clock className="w-6 h-6" />
            </div>
            <div className="space-y-1">
              <h4 className="text-xs font-bold text-slate-700">Không có đơn nào chờ duyệt</h4>
              <p className="text-[11px] text-slate-400 max-w-xs mx-auto">Hiện tại không có yêu cầu nghỉ phép nào thuộc thẩm quyền của bạn.</p>
            </div>
          </div>
        ) : (
          leaveRequests.map((req) => (
            <div key={req.id} className="p-4 space-y-3">
              <div className="flex items-start justify-between gap-2">
                <div>
                  <div className="flex items-center gap-2 flex-wrap">
                    <h4 className="text-xs font-bold text-slate-900">{req.employeeFullName}</h4>
                    <span className="bg-slate-100 text-slate-600 text-[9px] font-bold px-2 py-0.5 rounded">{getLeaveTypeLabel(req.leaveType)}</span>
                    {req.departmentName && <span className="text-[10px] text-slate-400">{req.departmentName}</span>}
                  </div>
                  <p className="text-[11px] text-slate-500 mt-1">
                    Lý do: <span className="italic">"{req.reason}"</span>
                  </p>
                  <p className="text-[10px] text-slate-400 mt-0.5">
                    Thời gian: {req.startDate} đến {req.endDate}
                    {req.startTime && req.endTime && ` (${req.startTime.slice(0, 5)}-${req.endTime.slice(0, 5)})`} · {req.totalDays} ngày
                  </p>
                </div>

                <Badge variant="warning">{statusLabels[req.status]}</Badge>
              </div>

              <div className="space-y-2 pt-1 border-t border-slate-100">
                <div className="space-y-1">
                  <label className="text-[10px] uppercase font-bold tracking-wider text-slate-400 block font-mono">
                    Ý kiến phản hồi / Lý do từ chối (bắt buộc khi từ chối):
                  </label>
                  <input
                    type="text"
                    placeholder="Ví dụ: Đồng ý duyệt / Không duyệt vì không có người dạy thay thế..."
                    value={opinionNotes[req.id] || ""}
                    onChange={(e) => setOpinionNotes((prev) => ({ ...prev, [req.id]: e.target.value }))}
                    className="w-full bg-slate-50 border border-slate-200 rounded px-2 py-1 text-xs focus:outline-none"
                  />
                </div>

                <div className="flex gap-2 justify-end">
                  <button
                    disabled={decidingId === req.id}
                    onClick={async () => {
                      const notes = opinionNotes[req.id]?.trim();
                      if (!notes) {
                        await alertDialog("Bạn phải điền lý do từ chối vào ô ý kiến phản hồi!");
                        return;
                      }
                      await decide(req, "REJECTED", notes);
                    }}
                    className={cn(
                      "px-2.5 py-1 hover:bg-rose-50 text-rose-600 border border-rose-200 text-[10px] font-bold rounded",
                      decidingId === req.id && "opacity-50"
                    )}
                  >
                    Từ chối đơn
                  </button>
                  <button
                    disabled={decidingId === req.id}
                    onClick={() => decide(req, "APPROVED", opinionNotes[req.id]?.trim() || "Đã rà soát, đồng ý duyệt.")}
                    className={cn(
                      "px-3 py-1 bg-brand-gradient hover:opacity-95 text-white text-[10px] font-bold rounded flex items-center gap-1",
                      decidingId === req.id && "opacity-50"
                    )}
                  >
                    <Check className="w-3.5 h-3.5 text-white" />
                    Phê duyệt cấp hiện tại
                  </button>
                </div>
              </div>
            </div>
          ))
        )}
      </div>
    </div>
  );
}
