import React from "react";
import { Check, Flag } from "lucide-react";
import { CommentEntry } from "@/types";
import Badge from "@/components/ui/Badge";

interface CommentApprovalQueueProps {
  comments: CommentEntry[];
  onApprove: (id: string, status: "APPROVED" | "REJECTED") => void;
}

export default function CommentApprovalQueue({ comments, onApprove }: CommentApprovalQueueProps) {
  return (
    <div className="divide-y divide-slate-100 max-h-[460px] overflow-y-auto">
      {comments.map((cm) => (
        <div key={cm.id} className="p-4 space-y-3.5">
          <div className="flex items-start justify-between">
            <div>
              <div className="flex items-center gap-2">
                <h4 className="text-xs font-bold text-slate-900">{cm.studentName}</h4>
                <span className="bg-slate-100 text-slate-600 text-[9px] font-bold px-2 py-0.5 rounded">{cm.type}</span>
              </div>
              <span className="text-[10px] text-slate-400 font-bold block mt-1 uppercase">{cm.className}</span>
            </div>

            <Badge variant={cm.status === "APPROVED" ? "success" : cm.status === "REJECTED" ? "danger" : "warning"}>
              {cm.status === "APPROVED" ? "Đã duyệt" : cm.status === "REJECTED" ? "Từ chối" : "Chờ duyệt"}
            </Badge>
          </div>

          <p className="text-xs text-slate-700 leading-relaxed bg-slate-50/60 p-3 rounded-lg border border-slate-100">{cm.content}</p>

          {cm.isWarning && (
            <div className="flex items-center gap-1.5 text-[10px] font-bold text-rose-600">
              <Flag className="w-3.5 h-3.5 fill-rose-600 shrink-0" />
              <span>Có gắn cờ cảnh báo (Hiển thị hàng đầu)</span>
            </div>
          )}

          {cm.status === "PENDING" && (
            <div className="flex gap-2 justify-end">
              <button onClick={() => onApprove(cm.id, "REJECTED")} className="px-2 py-1 text-rose-600 hover:bg-rose-50 border border-rose-200 text-[10px] font-bold rounded">
                Từ chối
              </button>
              <button onClick={() => onApprove(cm.id, "APPROVED")} className="px-3 py-1 bg-slate-900 hover:bg-slate-800 text-white text-[10px] font-bold rounded flex items-center gap-1">
                <Check className="w-3.5 h-3.5 text-brand-yellow" />
                Duyệt nhận xét
              </button>
            </div>
          )}
        </div>
      ))}
    </div>
  );
}
