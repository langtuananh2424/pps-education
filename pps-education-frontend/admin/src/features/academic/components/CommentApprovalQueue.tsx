import React, { useEffect, useState } from "react";
import { Check, Flag, X } from "lucide-react";
import { ApiError } from "@/lib/apiClient";
import { StudentCommentResponse, decideComments, listPendingComments } from "../api";
import Badge from "@/components/ui/Badge";

const commentTypeLabels: Record<StudentCommentResponse["commentType"], string> = { DAILY: "Hàng ngày", MID_TERM: "Giữa kỳ", END_TERM: "Cuối kỳ" };

export default function CommentApprovalQueue() {
  const [comments, setComments] = useState<StudentCommentResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [decidingId, setDecidingId] = useState<number | null>(null);

  const load = () => {
    setLoading(true);
    listPendingComments()
      .then(setComments)
      .catch((err) => setError(err instanceof ApiError ? err.message : "Không tải được hàng chờ duyệt nhận xét."))
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
      await decideComments([id], decision, comment?.trim() || undefined);
      load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Duyệt nhận xét thất bại.");
    } finally {
      setDecidingId(null);
    }
  };

  if (loading) return <p className="text-xs text-slate-500 p-4">Đang tải...</p>;

  return (
    <div className="divide-y divide-slate-100 max-h-[460px] overflow-y-auto">
      {error && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 m-3 rounded-lg">{error}</div>}
      {comments.map((cm) => (
        <div key={cm.id} className="p-4 space-y-3.5">
          <div className="flex items-start justify-between">
            <div>
              <div className="flex items-center gap-2">
                <h4 className="text-xs font-bold text-slate-900">{cm.studentFullName}</h4>
                <span className="bg-slate-100 text-slate-600 text-[9px] font-bold px-2 py-0.5 rounded">{commentTypeLabels[cm.commentType]}</span>
              </div>
              <span className="text-[10px] text-slate-400 font-bold block mt-1 uppercase">{cm.commentDate}</span>
            </div>
            <Badge variant="warning">Chờ duyệt</Badge>
          </div>

          <p className="text-xs text-slate-700 leading-relaxed bg-slate-50/60 p-3 rounded-lg border border-slate-100">{cm.content}</p>

          {cm.isWarning && (
            <div className="flex items-center gap-1.5 text-[10px] font-bold text-rose-600">
              <Flag className="w-3.5 h-3.5 fill-rose-600 shrink-0" />
              <span>Có gắn cờ cảnh báo (Hiển thị hàng đầu)</span>
            </div>
          )}

          <div className="flex gap-2 justify-end">
            <button
              onClick={() => handleDecide(cm.id, "REJECTED")}
              disabled={decidingId === cm.id}
              className="px-2 py-1 text-rose-600 hover:bg-rose-50 border border-rose-200 text-[10px] font-bold rounded disabled:opacity-50"
            >
              <X className="w-3 h-3 inline mr-0.5" />
              Từ chối
            </button>
            <button
              onClick={() => handleDecide(cm.id, "APPROVED")}
              disabled={decidingId === cm.id}
              className="px-3 py-1 bg-slate-900 hover:bg-slate-800 text-white text-[10px] font-bold rounded flex items-center gap-1 disabled:opacity-50"
            >
              <Check className="w-3.5 h-3.5 text-brand-yellow" />
              Duyệt nhận xét
            </button>
          </div>
        </div>
      ))}

      {comments.length === 0 && <p className="text-xs text-slate-400 italic text-center py-6">Chưa có nhận xét nào chờ duyệt.</p>}
    </div>
  );
}
