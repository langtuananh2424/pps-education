import React, { useEffect, useState } from "react";
import { History, X } from "lucide-react";
import { ApiError } from "@/lib/apiClient";
import { StudentCommentHistoryResponse, listStudentCommentHistory } from "../api";
import Badge from "@/components/ui/Badge";

const attitudeLabels: Record<NonNullable<StudentCommentHistoryResponse["details"]["attitude"]>, string> = {
  WEAK: "Yếu",
  AVERAGE: "Trung bình",
  FAIR: "Khá",
  GOOD: "Tốt",
  EXCELLENT: "Xuất sắc"
};
const statusLabels: Record<StudentCommentHistoryResponse["details"]["status"], string> = {
  DRAFT: "Nháp",
  PENDING: "Chờ duyệt",
  APPROVED: "Đã duyệt",
  REJECTED: "Bị từ chối"
};
const statusVariants: Record<StudentCommentHistoryResponse["details"]["status"], "success" | "warning" | "danger" | "neutral"> = {
  DRAFT: "neutral",
  PENDING: "warning",
  APPROVED: "success",
  REJECTED: "danger"
};
const actionLabels: Record<StudentCommentHistoryResponse["action"], string> = {
  CREATED: "Tạo mới",
  UPDATED: "Cập nhật"
};

interface CommentVersionHistoryModalProps {
  commentId: number;
  studentFullName: string;
  onClose: () => void;
}

/**
 * Bổ sung ngoài SDD gốc (đã xác nhận với người dùng 2026-08-19) — version
 * history kiểu Google Sheets cho 1 nhận xét: mỗi lần Lưu nháp/Gửi/Duyệt/Từ
 * chối là 1 mốc thời gian, click vào từng mốc xem lại được TOÀN BỘ nội
 * dung tại thời điểm đó (không phải chỉ biết "đã sửa lúc nào"). Mặc định
 * chỉ mở rộng mốc MỚI NHẤT — các mốc cũ hơn thu gọn, tránh cuộn dài với
 * nhận xét đã sửa nhiều lần.
 */
export default function CommentVersionHistoryModal({ commentId, studentFullName, onClose }: CommentVersionHistoryModalProps) {
  const [history, setHistory] = useState<StudentCommentHistoryResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [expandedId, setExpandedId] = useState<number | null>(null);

  useEffect(() => {
    setLoading(true);
    setError(null);
    listStudentCommentHistory(commentId)
      .then((res) => {
        setHistory(res);
        setExpandedId(res[0]?.id ?? null);
      })
      .catch((err) => setError(err instanceof ApiError ? err.message : "Không tải được lịch sử phiên bản."))
      .finally(() => setLoading(false));
  }, [commentId]);

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
      <div className="fixed inset-0 bg-slate-900/50 backdrop-blur-xs" onClick={onClose} />
      <div className="relative w-full max-w-2xl max-h-[85vh] overflow-y-auto bg-white rounded-2xl border border-slate-200 shadow-xl">
        <div className="sticky top-0 bg-white px-5 py-4 border-b border-slate-100 flex items-start justify-between gap-3">
          <div className="flex items-center gap-2">
            <History className="w-4 h-4 text-slate-500 shrink-0" />
            <div>
              <h3 className="text-sm font-bold font-display text-slate-900">Lịch sử phiên bản</h3>
              <p className="text-[11px] text-slate-500 mt-0.5">{studentFullName} — mỗi mốc là 1 lần Lưu nháp/Gửi/Duyệt/Từ chối.</p>
            </div>
          </div>
          <button type="button" onClick={onClose} className="text-slate-400 hover:text-slate-600 shrink-0">
            <X className="w-4 h-4" />
          </button>
        </div>

        <div className="p-5 space-y-2.5">
          {error && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg">{error}</div>}
          {loading ? (
            <p className="text-xs text-slate-400">Đang tải...</p>
          ) : history.length === 0 ? (
            <p className="text-xs text-slate-400 italic">Chưa có lịch sử nào.</p>
          ) : (
            history.map((h, index) => {
              const expanded = expandedId === h.id;
              const d = h.details;
              return (
                <div key={h.id} className="border border-slate-200 rounded-lg overflow-hidden">
                  <button
                    type="button"
                    onClick={() => setExpandedId(expanded ? null : h.id)}
                    className="w-full flex items-center justify-between gap-2 px-3 py-2.5 text-left hover:bg-slate-50/60"
                  >
                    <div className="flex items-center gap-2 flex-wrap min-w-0">
                      <span className="text-[11px] font-bold text-slate-800 whitespace-nowrap">
                        {new Date(h.createdAt).toLocaleString("vi-VN", { dateStyle: "short", timeStyle: "medium" })}
                      </span>
                      {index === 0 && (
                        <span className="px-1.5 py-0.5 rounded bg-teal/10 text-teal-deep text-[9px] font-black uppercase tracking-wide shrink-0">Mới nhất</span>
                      )}
                      <span className="text-[10px] text-slate-400 truncate">
                        {actionLabels[h.action]} bởi {h.changedByName}
                      </span>
                    </div>
                    <Badge variant={statusVariants[d.status]}>{statusLabels[d.status]}</Badge>
                  </button>
                  {expanded && (
                    <div className="px-3 pb-3 pt-1 border-t border-slate-100 space-y-1.5 text-[11px]">
                      <p className="text-slate-700 whitespace-pre-wrap">{d.content || "—"}</p>
                      {(d.attitude || d.homeworkPreviousScore || d.homeworkPreviousSpeakingScore) && (
                        <p className="text-slate-500">
                          {d.attitude && `Thái độ: ${attitudeLabels[d.attitude]}`}
                          {d.attitude && d.homeworkPreviousScore && " · "}
                          {d.homeworkPreviousScore && `BTVN Ngữ pháp buổi trước: ${d.homeworkPreviousScore}`}
                          {(d.attitude || d.homeworkPreviousScore) && d.homeworkPreviousSpeakingScore && " · "}
                          {d.homeworkPreviousSpeakingScore && `BTVN Nghe-nói buổi trước: ${d.homeworkPreviousSpeakingScore}`}
                        </p>
                      )}
                      {(d.homeworkNext || d.homeworkNextExerciseTitle || d.homeworkNextReviewVideoSetTitle) && (
                        <p className="text-slate-500">
                          {d.homeworkNext && `BTVN Ngữ pháp buổi sau (offline): ${d.homeworkNext}`}
                          {d.homeworkNextExerciseTitle && `BTVN Ngữ pháp buổi sau (online): ${d.homeworkNextExerciseTitle}`}
                          {(d.homeworkNext || d.homeworkNextExerciseTitle) && d.homeworkNextReviewVideoSetTitle && " · "}
                          {d.homeworkNextReviewVideoSetTitle && `Video ôn tập buổi sau: ${d.homeworkNextReviewVideoSetTitle}`}
                          {d.homeworkNextDueAt && ` (hạn ${new Date(d.homeworkNextDueAt).toLocaleString("vi-VN", { dateStyle: "short", timeStyle: "short" })})`}
                        </p>
                      )}
                      {(d.pendingHomeworkNextExerciseTitle || d.pendingHomeworkNextReviewVideoSetTitle) && (
                        <p className="text-amber-600">
                          Lựa chọn CHƯA gửi lúc này: {[d.pendingHomeworkNextExerciseTitle, d.pendingHomeworkNextReviewVideoSetTitle].filter(Boolean).join(" · ")}
                        </p>
                      )}
                      {d.note && <p className="text-slate-500">Ghi chú: {d.note}</p>}
                      {d.status === "REJECTED" && d.rejectionReason && <p className="text-rose-500">Lý do từ chối: {d.rejectionReason}</p>}
                    </div>
                  )}
                </div>
              );
            })
          )}
        </div>
      </div>
    </div>
  );
}
