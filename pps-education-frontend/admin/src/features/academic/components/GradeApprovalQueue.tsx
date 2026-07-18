import React, { useEffect, useState } from "react";
import { Check, X } from "lucide-react";
import { ApiError } from "@/lib/apiClient";
import { GradeEntryResponse, decideGrades, listPendingGrades } from "../api";

export default function GradeApprovalQueue() {
  const [entries, setEntries] = useState<GradeEntryResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [decidingId, setDecidingId] = useState<number | null>(null);

  const load = () => {
    setLoading(true);
    listPendingGrades()
      .then(setEntries)
      .catch((err) => setError(err instanceof ApiError ? err.message : "Không tải được hàng chờ duyệt điểm."))
      .finally(() => setLoading(false));
  };
  useEffect(load, []);

  const handleDecide = async (id: number, decision: "APPROVED" | "REJECTED") => {
    let comment: string | undefined;
    if (decision === "REJECTED") {
      comment = window.prompt("Lý do từ chối (không bắt buộc):") ?? undefined;
    }
    setDecidingId(id);
    setError(null);
    try {
      await decideGrades([id], decision, comment?.trim() || undefined);
      load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Duyệt điểm thất bại.");
    } finally {
      setDecidingId(null);
    }
  };

  if (loading) return <p className="text-xs text-slate-500">Đang tải...</p>;

  return (
    <div className="space-y-3.5 max-h-[380px] overflow-y-auto">
      {error && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg">{error}</div>}
      {entries.map((gr) => (
        <div key={gr.id} className="p-3.5 bg-slate-50 rounded-lg border border-slate-100 space-y-2">
          <div className="flex items-start justify-between">
            <div>
              <span className="text-xs font-bold text-slate-800 block">{gr.studentFullName}</span>
              <span className="text-[10px] text-slate-400 font-bold uppercase block mt-0.5">{gr.studentCode}</span>
            </div>
            <span className="text-xs font-mono font-bold bg-brand-yellow/20 text-brand-orange px-2 py-0.5 rounded">{gr.score}</span>
          </div>
          {gr.teacherNote && <p className="text-[10px] text-slate-500 italic">{gr.teacherNote}</p>}

          <div className="flex gap-2 justify-end pt-1">
            <button
              onClick={() => handleDecide(gr.id, "REJECTED")}
              disabled={decidingId === gr.id}
              className="px-2 py-1 text-rose-600 hover:bg-rose-50 border border-rose-200 text-[10px] font-bold rounded disabled:opacity-50"
            >
              <X className="w-3 h-3 inline mr-0.5" />
              Từ chối
            </button>
            <button
              onClick={() => handleDecide(gr.id, "APPROVED")}
              disabled={decidingId === gr.id}
              className="px-2.5 py-1 bg-slate-900 hover:bg-slate-800 text-white text-[10px] font-bold rounded flex items-center gap-0.5 disabled:opacity-50"
            >
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
