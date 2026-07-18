import React, { useState } from "react";
import { Check, Clock } from "lucide-react";
import { LeaveRequest } from "@/types";
import Badge from "@/components/ui/Badge";
import { cn } from "@/lib/cn";

interface LeaveApprovalQueueProps {
  leaveRequests: LeaveRequest[];
  onVote: (reqId: string, vote: "APPROVED" | "REJECTED", notes: string) => void;
}

const typeLabels = { LEAVE: "Nghỉ phép", LATE: "Đi muộn", EARLY: "Về sớm" };
const statusLabels = { APPROVED: "Đã duyệt", REJECTED: "Từ chối", PENDING: "Chờ duyệt", DRAFT: "Bản nháp" };

export default function LeaveApprovalQueue({ leaveRequests, onVote }: LeaveApprovalQueueProps) {
  const [filter, setFilter] = useState<"pending" | "history">("pending");
  const [opinionNotes, setOpinionNotes] = useState<Record<string, string>>({});

  const filtered = leaveRequests.filter((req) => (filter === "pending" ? req.status === "PENDING" : req.status !== "PENDING"));

  return (
    <div className="lg:col-span-2 bg-white rounded-xl border border-slate-200 shadow-soft overflow-hidden">
      <div className="px-5 py-3 border-b border-slate-100 bg-slate-50 flex flex-col sm:flex-row items-start sm:items-center justify-between gap-3">
        <div>
          <span className="text-xs font-bold text-slate-700 font-display block">Hàng chờ duyệt đơn trực tuyến (PBAC)</span>
          <p className="text-[10px] text-slate-400">Xem xét và phê duyệt hoặc từ chối đơn xin nghỉ phép của cán bộ giảng viên</p>
        </div>

        <div className="flex bg-slate-200/60 p-0.5 rounded-lg border border-slate-200 shrink-0">
          <button
            onClick={() => setFilter("pending")}
            className={cn("px-3 py-1 text-[10px] font-bold rounded-md transition-all flex items-center gap-1.5", filter === "pending" ? "bg-white text-slate-900 shadow-sm" : "text-slate-500 hover:text-slate-800")}
          >
            <span>Chờ duyệt</span>
            <span className={cn("px-1.5 py-0.5 rounded-full text-[9px]", filter === "pending" ? "bg-brand-orange text-white" : "bg-slate-300 text-slate-600")}>
              {leaveRequests.filter((r) => r.status === "PENDING").length}
            </span>
          </button>
          <button
            onClick={() => setFilter("history")}
            className={cn("px-3 py-1 text-[10px] font-bold rounded-md transition-all flex items-center gap-1.5", filter === "history" ? "bg-white text-slate-900 shadow-sm" : "text-slate-500 hover:text-slate-800")}
          >
            <span>Lịch sử đơn</span>
            <span className="px-1.5 py-0.5 rounded-full bg-slate-300 text-slate-600 text-[9px]">{leaveRequests.filter((r) => r.status !== "PENDING").length}</span>
          </button>
        </div>
      </div>

      <div className="divide-y divide-slate-100 max-h-[500px] overflow-y-auto">
        {filtered.length === 0 ? (
          <div className="p-12 text-center space-y-3">
            <div className="w-12 h-12 bg-slate-50 rounded-full flex items-center justify-center mx-auto border border-slate-100 text-slate-400">
              <Clock className="w-6 h-6" />
            </div>
            <div className="space-y-1">
              <h4 className="text-xs font-bold text-slate-700">Không có đơn từ nào</h4>
              <p className="text-[11px] text-slate-400 max-w-xs mx-auto">Hiện tại không có yêu cầu nghỉ phép nào thuộc trạng thái này.</p>
            </div>
          </div>
        ) : (
          filtered.map((req) => (
            <div key={req.id} className={cn("p-4 space-y-3 transition-colors", req.isPublicSubmitted && "border-l-4 border-amber-400 bg-amber-50/10")}>
              <div className="flex items-start justify-between gap-2">
                <div>
                  <div className="flex items-center gap-2 flex-wrap">
                    <h4 className="text-xs font-bold text-slate-900">{req.employeeName}</h4>
                    <span className="bg-slate-100 text-slate-600 text-[9px] font-bold px-2 py-0.5 rounded">{typeLabels[req.type]}</span>
                    {req.isPublicSubmitted && (
                      <span className="bg-amber-100 text-amber-800 text-[9px] font-bold px-2 py-0.5 rounded-full flex items-center gap-1 border border-amber-200">🔗 Đơn nộp ngoài</span>
                    )}
                  </div>
                  <p className="text-[11px] text-slate-500 mt-1">
                    Lý do: <span className="italic">"{req.reason}"</span>
                  </p>
                  <p className="text-[10px] text-slate-400 mt-0.5">
                    Thời gian: {req.startDate} đến {req.endDate} • Tạo ngày {req.createdAt}
                  </p>
                </div>

                <Badge variant={req.status === "APPROVED" ? "success" : req.status === "REJECTED" ? "danger" : "warning"}>{statusLabels[req.status]}</Badge>
              </div>

              <div className="bg-slate-50 p-2.5 rounded border border-slate-100 grid grid-cols-1 sm:grid-cols-2 gap-4 text-[10px]">
                {req.steps.map((st, sIdx) => (
                  <div key={sIdx} className="border-r last:border-r-0 border-slate-200 pr-2">
                    <span className="font-bold text-slate-500 block uppercase font-display">
                      Cấp duyệt {sIdx + 1} ({st.role === "SITE_MANAGER" ? "Quản lý Điểm trường" : "Ban điều hành / Vận hành"})
                    </span>
                    <div className="flex items-center gap-1.5 mt-1">
                      <span className={cn("w-1.5 h-1.5 rounded-full", st.status === "APPROVED" ? "bg-emerald-500" : st.status === "REJECTED" ? "bg-red-500" : "bg-amber-500")} />
                      <span className="text-slate-700 font-semibold">
                        {st.status === "APPROVED" ? `Đã duyệt (${st.approverName || "Sếp"})` : st.status === "REJECTED" ? "Bị từ chối" : "Đang chờ"}
                      </span>
                    </div>
                    {st.notes && <p className="text-[10px] text-slate-400 italic mt-0.5">Ý kiến: "{st.notes}"</p>}
                  </div>
                ))}
              </div>

              {req.status === "PENDING" && (
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
                      onClick={() => {
                        const notes = opinionNotes[req.id]?.trim();
                        if (!notes) {
                          alert("Lỗi: Bạn phải điền lý do từ chối vào ô ý kiến phản hồi!");
                          return;
                        }
                        onVote(req.id, "REJECTED", notes);
                      }}
                      className="px-2.5 py-1 hover:bg-rose-50 text-rose-600 border border-rose-200 text-[10px] font-bold rounded"
                    >
                      Từ chối đơn
                    </button>
                    <button
                      onClick={() => onVote(req.id, "APPROVED", opinionNotes[req.id]?.trim() || "Đã rà soát, đồng ý duyệt.")}
                      className="px-3 py-1 bg-slate-900 hover:bg-slate-800 text-white text-[10px] font-bold rounded flex items-center gap-1"
                    >
                      <Check className="w-3.5 h-3.5 text-brand-yellow" />
                      Phê duyệt cấp hiện tại
                    </button>
                  </div>
                </div>
              )}
            </div>
          ))
        )}
      </div>
    </div>
  );
}
