import React, { useEffect, useState } from "react";
import { Link2, Send } from "lucide-react";
import Modal from "@/components/ui/Modal";
import { ApiError } from "@/lib/apiClient";
import {
  addAttachment,
  addComment,
  AssignmentStatus,
  getTask,
  listAttachments,
  listComments,
  TaskAssignmentResponse,
  TaskAttachmentResponse,
  TaskCommentResponse,
  TaskResponse,
  updateAssignmentStatus
} from "../api";
import { ASSIGNMENT_ACTION_LABEL, ASSIGNMENT_STATUS_META, ASSIGNEE_TRANSITIONS } from "../statusMeta";
import { useToast } from "@/lib/useToast";
import Toast from "@/components/ui/Toast";

const PRIORITY_LABEL: Record<string, string> = { LOW: "Thấp", NORMAL: "Bình thường", HIGH: "Cao", URGENT: "Khẩn cấp" };

interface AssignmentDetailModalProps {
  assignment: TaskAssignmentResponse;
  onClose: () => void;
  onChanged: (updated: TaskAssignmentResponse) => void;
}

/** UC-07 Main Flow bước 2-4, A2: xem chi tiết + đổi trạng thái (đúng state machine assignee) + bình luận/đính kèm. */
export default function AssignmentDetailModal({ assignment, onClose, onChanged }: AssignmentDetailModalProps) {
  const [task, setTask] = useState<TaskResponse | null>(null);
  const [comments, setComments] = useState<TaskCommentResponse[]>([]);
  const [attachments, setAttachments] = useState<TaskAttachmentResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const [newComment, setNewComment] = useState("");
  const [declineTarget, setDeclineTarget] = useState<AssignmentStatus | null>(null);
  const [declineReason, setDeclineReason] = useState("");
  const [submittingStatus, setSubmittingStatus] = useState(false);

  const [attachUrl, setAttachUrl] = useState("");
  const [attachName, setAttachName] = useState("");
  const { message: toastMessage, showToast } = useToast();

  useEffect(() => {
    setLoading(true);
    Promise.all([getTask(assignment.taskId), listComments(assignment.taskId), listAttachments(assignment.taskId)])
      .then(([t, c, a]) => {
        setTask(t);
        setComments(c);
        setAttachments(a);
      })
      .catch((err) => setError(err instanceof ApiError ? err.message : "Không tải được chi tiết công việc."))
      .finally(() => setLoading(false));
  }, [assignment.taskId]);

  const allowedTargets = ASSIGNEE_TRANSITIONS[assignment.assignmentStatus];

  const handleChangeStatus = async (target: AssignmentStatus, comment?: string) => {
    setError(null);
    setSubmittingStatus(true);
    try {
      const updated = await updateAssignmentStatus(assignment.id, { status: target, comment });
      onChanged(updated);
      setDeclineTarget(null);
      setDeclineReason("");
      showToast(`Đã cập nhật trạng thái "${ASSIGNMENT_ACTION_LABEL[target]}" thành công!`);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Đổi trạng thái thất bại.");
    } finally {
      setSubmittingStatus(false);
    }
  };

  const handleConfirmDecline = () => {
    if (!declineReason.trim()) {
      setError("Cần nêu lý do khi từ chối nhận việc.");
      return;
    }
    handleChangeStatus("DECLINED", declineReason.trim());
  };

  const handleAddComment = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!newComment.trim()) return;
    try {
      const created = await addComment(assignment.taskId, { content: newComment.trim() });
      setComments((prev) => [...prev, created]);
      setNewComment("");
      showToast("Đã gửi bình luận thành công!");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Gửi bình luận thất bại.");
    }
  };

  const handleAddAttachment = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!attachUrl.trim() || !attachName.trim()) return;
    try {
      const created = await addAttachment(assignment.taskId, { fileUrl: attachUrl.trim(), fileName: attachName.trim() });
      setAttachments((prev) => [...prev, created]);
      setAttachUrl("");
      setAttachName("");
      showToast("Đã thêm tệp đính kèm thành công!");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Thêm đính kèm thất bại.");
    }
  };

  const meta = ASSIGNMENT_STATUS_META[assignment.assignmentStatus];

  return (
    <Modal open onClose={onClose} title={assignment.taskTitle} description={`Mã phân công: #${assignment.id} · Mã việc: ${task?.taskCode ?? "..."}`} size="lg">
      {loading ? (
        <p className="text-sm text-slate-500">Đang tải...</p>
      ) : (
        <div className="space-y-4">
          {error && <div className="text-sm text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg">{error}</div>}

          <div className="space-y-1">
            <span className="text-sm uppercase font-bold tracking-wider text-slate-400 block font-display">Mô tả chi tiết</span>
            <p className="text-sm text-slate-700 leading-relaxed bg-slate-50 p-3 rounded-lg border border-slate-100">
              {task?.description || "Không có mô tả."}
            </p>
          </div>

          <div className="grid grid-cols-3 gap-3 bg-slate-50 p-3.5 rounded-lg border border-slate-100 text-sm">
            <div>
              <span className="text-sm text-slate-400 font-medium block">Trạng thái</span>
              <span className={`inline-block mt-1 px-2 py-0.5 rounded-full text-sm font-bold ${meta.badge}`}>{meta.label}</span>
            </div>
            <div>
              <span className="text-sm text-slate-400 font-medium block">Người giao</span>
              <span className="font-bold text-slate-800 block mt-0.5">{task?.createdByFullName ?? "—"}</span>
            </div>
            <div>
              <span className="text-sm text-slate-400 font-medium block">Hạn hoàn thành</span>
              <span className="font-bold text-slate-800 block mt-0.5">{task?.dueAt ? new Date(task.dueAt).toLocaleString("vi-VN") : "Không đặt hạn"}</span>
            </div>
            <div>
              <span className="text-sm text-slate-400 font-medium block">Độ ưu tiên</span>
              <span className="font-bold text-slate-800 block mt-0.5">{task ? PRIORITY_LABEL[task.priority] ?? task.priority : "—"}</span>
            </div>
            {assignment.declineReason && (
              <div className="col-span-2">
                <span className="text-sm text-rose-400 font-medium block">Lý do từ chối</span>
                <span className="font-semibold text-rose-600 block mt-0.5">{assignment.declineReason}</span>
              </div>
            )}
          </div>

          {(allowedTargets.length > 0 || declineTarget) && (
            <div className="space-y-2 border border-slate-100 rounded-lg p-3">
              <span className="text-sm uppercase font-bold tracking-wider text-slate-400 block font-display">Cập nhật tiến độ</span>
              {declineTarget === "DECLINED" ? (
                <div className="space-y-2">
                  <textarea
                    value={declineReason}
                    onChange={(e) => setDeclineReason(e.target.value)}
                    placeholder="Nêu lý do từ chối nhận việc (bắt buộc)..."
                    rows={2}
                    className="w-full bg-white border border-rose-200 text-sm p-2.5 rounded-lg focus:outline-none"
                  />
                  <div className="flex gap-2">
                    <button
                      type="button"
                      onClick={() => setDeclineTarget(null)}
                      className="px-3 py-1.5 text-slate-500 hover:bg-slate-100 text-sm font-semibold rounded-lg"
                    >
                      Hủy
                    </button>
                    <button
                      type="button"
                      disabled={submittingStatus}
                      onClick={handleConfirmDecline}
                      className="bg-rose-600 hover:bg-rose-700 text-white font-semibold text-sm px-3.5 py-1.5 rounded-lg"
                    >
                      Xác nhận từ chối
                    </button>
                  </div>
                </div>
              ) : (
                <div className="flex flex-wrap gap-2">
                  {allowedTargets.map((target) => (
                    <button
                      key={target}
                      type="button"
                      disabled={submittingStatus}
                      onClick={() => (target === "DECLINED" ? setDeclineTarget("DECLINED") : handleChangeStatus(target))}
                      className={`text-sm font-semibold px-3.5 py-1.5 rounded-lg ${
                        target === "DECLINED"
                          ? "bg-rose-50 text-rose-600 hover:bg-rose-100"
                          : "bg-brand-gradient hover:opacity-95 text-white"
                      }`}
                    >
                      {ASSIGNMENT_ACTION_LABEL[target]}
                    </button>
                  ))}
                </div>
              )}
            </div>
          )}

          <div className="space-y-2">
            <span className="text-sm uppercase font-bold tracking-wider text-slate-400 block font-display">Tệp đính kèm</span>
            <div className="space-y-1.5">
              {attachments.map((a) => (
                <a
                  key={a.id}
                  href={a.fileUrl}
                  target="_blank"
                  rel="noreferrer"
                  className="flex items-center gap-1.5 text-sm text-brand-orange hover:underline"
                >
                  <Link2 className="w-3.5 h-3.5" />
                  {a.fileName}
                </a>
              ))}
              {attachments.length === 0 && <p className="text-sm text-slate-400 italic">Chưa có tệp đính kèm.</p>}
            </div>
            <form onSubmit={handleAddAttachment} className="flex gap-2">
              <input
                value={attachName}
                onChange={(e) => setAttachName(e.target.value)}
                placeholder="Tên tệp"
                className="flex-1 bg-slate-50 border border-slate-200 text-sm px-3 py-2 rounded-lg focus:outline-none"
              />
              <input
                value={attachUrl}
                onChange={(e) => setAttachUrl(e.target.value)}
                placeholder="Đường dẫn tệp (URL)"
                className="flex-1 bg-slate-50 border border-slate-200 text-sm px-3 py-2 rounded-lg focus:outline-none"
              />
              <button type="submit" className="bg-slate-100 hover:bg-slate-200 text-slate-600 text-sm font-semibold px-3 py-2 rounded-lg shrink-0">
                Thêm
              </button>
            </form>
          </div>

          <div className="space-y-3">
            <span className="text-sm uppercase font-bold tracking-wider text-slate-400 block font-display">Lịch sử trao đổi</span>
            <div className="space-y-2">
              {comments.map((cmt) => (
                <div key={cmt.id} className="p-2.5 rounded-lg bg-slate-50 border border-slate-100 text-sm">
                  <div className="flex items-center justify-between font-semibold text-slate-700">
                    <span>{cmt.commenterFullName}</span>
                    <span className="text-[9px] text-slate-400 font-mono">{new Date(cmt.createdAt).toLocaleString("vi-VN")}</span>
                  </div>
                  <p className="text-slate-600 mt-1">{cmt.content}</p>
                </div>
              ))}
              {comments.length === 0 && <p className="text-sm text-slate-400 italic">Chưa có bình luận nào.</p>}
            </div>

            <form onSubmit={handleAddComment} className="flex gap-2">
              <input
                type="text"
                placeholder="Viết phản hồi tiến độ..."
                value={newComment}
                onChange={(e) => setNewComment(e.target.value)}
                className="flex-1 bg-slate-50 border border-slate-200 text-sm px-3 py-2 rounded-lg focus:outline-none"
              />
              <button type="submit" className="bg-brand-gradient hover:opacity-95 text-white p-2 rounded-lg shrink-0">
                <Send className="w-4 h-4 text-white" />
              </button>
            </form>
          </div>
        </div>
      )}

      <Toast message={toastMessage} />
    </Modal>
  );
}
