import React from "react";
import { Check } from "lucide-react";
import { GradeEntry } from "@/types";

interface GradeApprovalQueueProps {
  entries: GradeEntry[];
  onApprove: (gradeId: string, status: "APPROVED" | "REJECTED") => void;
}

export default function GradeApprovalQueue({ entries, onApprove }: GradeApprovalQueueProps) {
  return (
    <div className="space-y-3.5 max-h-[380px] overflow-y-auto">
      {entries.map((gr) => (
        <div key={gr.id} className="p-3.5 bg-slate-50 rounded-lg border border-slate-100 space-y-2">
          <div className="flex items-start justify-between">
            <div>
              <span className="text-xs font-bold text-slate-800 block">{gr.studentName}</span>
              <span className="text-[10px] text-slate-400 font-bold uppercase block mt-0.5">{gr.className}</span>
            </div>
            <span className="text-xs font-mono font-bold bg-brand-yellow/20 text-brand-orange px-2 py-0.5 rounded">Avg: {gr.averageScore?.toFixed(1)}</span>
          </div>

          <div className="grid grid-cols-2 text-[10px] text-slate-500 font-semibold gap-1 pt-1.5 border-t border-dashed">
            <span>Giữa kỳ: {gr.midtermScore}</span>
            <span>Cuối kỳ: {gr.finalScore}</span>
          </div>

          <div className="flex gap-2 justify-end pt-1">
            <button onClick={() => onApprove(gr.id, "REJECTED")} className="px-2 py-1 text-rose-600 hover:bg-rose-50 border border-rose-200 text-[10px] font-bold rounded">
              Từ chối
            </button>
            <button onClick={() => onApprove(gr.id, "APPROVED")} className="px-2.5 py-1 bg-slate-900 hover:bg-slate-800 text-white text-[10px] font-bold rounded flex items-center gap-0.5">
              <Check className="w-3.5 h-3.5 text-brand-yellow" />
              Duyệt điểm
            </button>
          </div>
        </div>
      ))}

      {entries.length === 0 && <p className="text-xs text-slate-400 italic text-center py-6">Chưa có cột điểm nào chờ duyệt.</p>}
    </div>
  );
}
