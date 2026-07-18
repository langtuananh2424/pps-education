import React, { useState } from "react";
import { Flag, Save, Send } from "lucide-react";
import { ApiError } from "@/lib/apiClient";
import { StudentCommentResponse, submitComments, updateComment } from "../api";
import Badge from "@/components/ui/Badge";
import Button from "@/components/ui/Button";

const inputClass = "w-full bg-slate-50 border border-slate-200 text-xs p-2 rounded-lg focus:outline-none";
const statusLabels: Record<StudentCommentResponse["status"], string> = { DRAFT: "Nháp", PENDING: "Chờ duyệt", APPROVED: "Đã duyệt", REJECTED: "Bị từ chối" };
const statusVariants: Record<StudentCommentResponse["status"], "success" | "warning" | "danger" | "neutral"> = {
  DRAFT: "neutral",
  PENDING: "warning",
  APPROVED: "success",
  REJECTED: "danger"
};

interface CommentHistoryListProps {
  classId: number;
  history: StudentCommentResponse[];
  onChanged: () => void;
  showStudentName?: boolean;
}

/** UC-21 A1: nhận xét bị Quản lý điểm trường từ chối (kèm lý do) — Giáo viên sửa lại nội dung rồi gửi lại thẳng lên hàng chờ duyệt. */
export default function CommentHistoryList({ classId, history, onChanged, showStudentName }: CommentHistoryListProps) {
  const [editingId, setEditingId] = useState<number | null>(null);
  const [editContent, setEditContent] = useState("");
  const [editSeverity, setEditSeverity] = useState<NonNullable<StudentCommentResponse["severity"]>>("NORMAL");
  const [editIsWarning, setEditIsWarning] = useState(false);
  const [saving, setSaving] = useState(false);
  const [submittingId, setSubmittingId] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);

  const startEdit = (h: StudentCommentResponse) => {
    setEditingId(h.id);
    setEditContent(h.content);
    setEditSeverity(h.severity ?? "NORMAL");
    setEditIsWarning(h.isWarning);
    setError(null);
  };

  const handleSubmitDraft = async (commentId: number) => {
    setSubmittingId(commentId);
    setError(null);
    try {
      await submitComments(classId, [commentId]);
      onChanged();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Nộp nhận xét thất bại.");
    } finally {
      setSubmittingId(null);
    }
  };

  const handleResend = async (commentId: number) => {
    if (!editContent.trim()) {
      setError("Vui lòng nhập nội dung nhận xét.");
      return;
    }
    setSaving(true);
    setError(null);
    try {
      await updateComment(commentId, { content: editContent.trim(), severity: editSeverity, isWarning: editIsWarning });
      await submitComments(classId, [commentId]);
      setEditingId(null);
      onChanged();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Gửi lại nhận xét thất bại.");
    } finally {
      setSaving(false);
    }
  };

  if (history.length === 0) {
    return <p className="text-xs text-slate-400 italic">Chưa có nhận xét nào.</p>;
  }

  return (
    <div className="space-y-2">
      {error && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg">{error}</div>}
      {history.map((h) => (
        <div key={h.id} className="border border-slate-200 rounded-lg p-2.5 text-[11px] space-y-1.5">
          <div className="flex items-center justify-between gap-2">
            {showStudentName && <span className="font-bold text-slate-800">{h.studentFullName}</span>}
            <span className="text-slate-400">{h.commentDate}</span>
            <Badge variant={statusVariants[h.status]}>{statusLabels[h.status]}</Badge>
          </div>

          {editingId === h.id ? (
            <div className="space-y-2 pt-1">
              <textarea value={editContent} onChange={(e) => setEditContent(e.target.value)} rows={3} className={inputClass} />
              <div className="grid grid-cols-2 gap-2">
                <select value={editSeverity} onChange={(e) => setEditSeverity(e.target.value as NonNullable<StudentCommentResponse["severity"]>)} className={inputClass}>
                  <option value="POSITIVE">Tích cực</option>
                  <option value="NORMAL">Bình thường</option>
                  <option value="CONCERN">Cần lưu ý</option>
                  <option value="WARNING">Cảnh báo</option>
                </select>
                <label className="flex items-center gap-1.5 text-[10px] font-semibold text-slate-600">
                  <input type="checkbox" checked={editIsWarning} onChange={(e) => setEditIsWarning(e.target.checked)} />
                  <Flag className="w-3 h-3 text-rose-500" />
                  Cảnh báo đặc biệt
                </label>
              </div>
              <div className="flex gap-2">
                <Button type="button" size="sm" variant="secondary" onClick={() => setEditingId(null)}>
                  Hủy
                </Button>
                <Button type="button" size="sm" variant="primary" onClick={() => handleResend(h.id)} disabled={saving}>
                  <Send className="w-3.5 h-3.5" />
                  {saving ? "Đang gửi..." : "Gửi lại"}
                </Button>
              </div>
            </div>
          ) : (
            <>
              <p className="text-slate-700">{h.content}</p>
              {h.status === "REJECTED" && h.rejectionReason && (
                <p className="text-rose-500">Lý do từ chối: {h.rejectionReason}</p>
              )}
              {h.status === "DRAFT" && (
                <Button size="sm" variant="secondary" onClick={() => handleSubmitDraft(h.id)} disabled={submittingId === h.id}>
                  <Save className="w-3.5 h-3.5" />
                  {submittingId === h.id ? "Đang nộp..." : "Nộp duyệt"}
                </Button>
              )}
              {h.status === "REJECTED" && (
                <Button size="sm" variant="secondary" onClick={() => startEdit(h)}>
                  Sửa & Gửi lại
                </Button>
              )}
            </>
          )}
        </div>
      ))}
    </div>
  );
}
