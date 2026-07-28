import React, { useEffect, useState } from "react";
import { Check, X } from "lucide-react";
import { ApiError } from "@/lib/apiClient";
import { CurriculumApprovalResponse, decideCurriculumApproval, listPendingCurriculumApprovals } from "../api";
import { useToast } from "@/lib/useToast";
import Toast from "@/components/ui/Toast";

/** UC-17: Trưởng phòng đào tạo duyệt/từ chối bản tùy biến khung chương trình. */
export default function CurriculumApprovalPanel() {
  const [approvals, setApprovals] = useState<CurriculumApprovalResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [decidingId, setDecidingId] = useState<number | null>(null);
  const { message: toastMessage, showToast } = useToast();

  const load = () => {
    setLoading(true);
    listPendingCurriculumApprovals()
      .then(setApprovals)
      .catch((err) => setError(err instanceof ApiError ? err.message : "Không tải được hàng chờ duyệt."))
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
      await decideCurriculumApproval(id, decision, comment?.trim() || undefined);
      load();
      showToast(decision === "APPROVED" ? "Đã duyệt tùy biến thành công!" : "Đã từ chối tùy biến thành công!");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Duyệt thất bại.");
    } finally {
      setDecidingId(null);
    }
  };

  if (loading) return <p className="text-xs text-slate-500">Đang tải...</p>;

  return (
    <div className="space-y-3.5 max-h-[380px] overflow-y-auto">
      {error && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg">{error}</div>}
      {approvals.map((a) => (
        <div key={a.id} className="p-3.5 bg-slate-50 rounded-lg border border-slate-100 space-y-2">
          <div>
            <span className="text-xs font-bold text-slate-800 block">{a.curriculumName}</span>
            <span className="text-[10px] text-slate-400 font-mono font-bold block mt-0.5">{a.curriculumCode}</span>
          </div>
          <div className="flex gap-2 justify-end pt-1">
            <button
              onClick={() => handleDecide(a.id, "REJECTED")}
              disabled={decidingId === a.id}
              className="px-2 py-1 text-rose-600 hover:bg-rose-50 border border-rose-200 text-[10px] font-bold rounded disabled:opacity-50"
            >
              <X className="w-3 h-3 inline mr-0.5" />
              Từ chối
            </button>
            <button
              onClick={() => handleDecide(a.id, "APPROVED")}
              disabled={decidingId === a.id}
              className="px-2.5 py-1 bg-slate-900 hover:bg-slate-800 text-white text-[10px] font-bold rounded flex items-center gap-0.5 disabled:opacity-50"
            >
              <Check className="w-3.5 h-3.5 text-brand-yellow" />
              Duyệt tùy biến
            </button>
          </div>
        </div>
      ))}

      {approvals.length === 0 && <p className="text-xs text-slate-400 italic text-center py-6">Chưa có bản tùy biến nào chờ duyệt.</p>}

      <Toast message={toastMessage} />
    </div>
  );
}
