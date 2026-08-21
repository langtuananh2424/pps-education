import React, { useEffect, useState } from "react";
import { Link2, Send } from "lucide-react";
import { useTranslation } from "react-i18next";
import Modal from "@/components/ui/Modal";
import { ApiError } from "@/lib/apiClient";
import { formatDateTime } from "@/lib/i18nFormat";
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
import { assignmentActionLabel, ASSIGNMENT_STATUS_META, assignmentStatusLabel, ASSIGNEE_TRANSITIONS } from "../statusMeta";
import { useToast } from "@/lib/useToast";
import Toast from "@/components/ui/Toast";

interface AssignmentDetailModalProps {
  assignment: TaskAssignmentResponse;
  onClose: () => void;
  onChanged: (updated: TaskAssignmentResponse) => void;
}

/** UC-07 Main Flow bước 2-4, A2: xem chi tiết + đổi trạng thái (đúng state machine assignee) + bình luận/đính kèm. */
export default function AssignmentDetailModal({ assignment, onClose, onChanged }: AssignmentDetailModalProps) {
  const { t, i18n } = useTranslation("task-workflow");
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
      .catch((err) => setError(err instanceof ApiError ? err.message : t("assignmentDetail.loadError")))
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
      showToast(t("assignmentDetail.statusChangedToast", { action: assignmentActionLabel(t, target) }));
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t("assignmentDetail.statusChangeError"));
    } finally {
      setSubmittingStatus(false);
    }
  };

  const handleConfirmDecline = () => {
    if (!declineReason.trim()) {
      setError(t("assignmentDetail.declineReasonRequired"));
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
      showToast(t("assignmentDetail.commentAddedToast"));
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t("assignmentDetail.commentAddError"));
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
      showToast(t("assignmentDetail.attachmentAddedToast"));
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t("assignmentDetail.attachmentAddError"));
    }
  };

  const meta = ASSIGNMENT_STATUS_META[assignment.assignmentStatus];

  return (
    <Modal
      open
      onClose={onClose}
      title={assignment.taskTitle}
      description={t("assignmentDetail.assignmentCodePrefix", { id: assignment.id, taskCode: task?.taskCode ?? "..." })}
      size="lg"
    >
      {loading ? (
        <p className="text-xs text-slate-500">{t("assignmentDetail.loading")}</p>
      ) : (
        <div className="space-y-4">
          {error && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg">{error}</div>}

          <div className="space-y-1">
            <span className="text-[10px] uppercase font-bold tracking-wider text-slate-400 block font-display">{t("assignmentDetail.descriptionLabel")}</span>
            <p className="text-xs text-slate-700 leading-relaxed bg-slate-50 p-3 rounded-lg border border-slate-100">
              {task?.description || t("assignmentDetail.noDescription")}
            </p>
          </div>

          <div className="grid grid-cols-3 gap-3 bg-slate-50 p-3.5 rounded-lg border border-slate-100 text-xs">
            <div>
              <span className="text-[10px] text-slate-400 font-medium block">{t("assignmentDetail.statusLabel")}</span>
              <span className={`inline-block mt-1 px-2 py-0.5 rounded-full text-[10px] font-bold ${meta.badge}`}>
                {assignmentStatusLabel(t, assignment.assignmentStatus)}
              </span>
            </div>
            <div>
              <span className="text-[10px] text-slate-400 font-medium block">{t("assignmentDetail.assignerLabel")}</span>
              <span className="font-bold text-slate-800 block mt-0.5">{task?.createdByFullName ?? "—"}</span>
            </div>
            <div>
              <span className="text-[10px] text-slate-400 font-medium block">{t("assignmentDetail.dueLabel")}</span>
              <span className="font-bold text-slate-800 block mt-0.5">
                {task?.dueAt ? formatDateTime(task.dueAt, i18n.language) : t("assignmentDetail.noDueDate")}
              </span>
            </div>
            <div>
              <span className="text-[10px] text-slate-400 font-medium block">{t("assignmentDetail.priorityLabel")}</span>
              <span className="font-bold text-slate-800 block mt-0.5">{task ? t(`priority.${task.priority}`) : "—"}</span>
            </div>
            {assignment.declineReason && (
              <div className="col-span-2">
                <span className="text-[10px] text-rose-400 font-medium block">{t("assignmentDetail.declineReasonLabel")}</span>
                <span className="font-semibold text-rose-600 block mt-0.5">{assignment.declineReason}</span>
              </div>
            )}
          </div>

          {(allowedTargets.length > 0 || declineTarget) && (
            <div className="space-y-2 border border-slate-100 rounded-lg p-3">
              <span className="text-[10px] uppercase font-bold tracking-wider text-slate-400 block font-display">{t("assignmentDetail.updateProgressLabel")}</span>
              {declineTarget === "DECLINED" ? (
                <div className="space-y-2">
                  <textarea
                    value={declineReason}
                    onChange={(e) => setDeclineReason(e.target.value)}
                    placeholder={t("assignmentDetail.declineReasonPlaceholder")}
                    rows={2}
                    className="w-full bg-white border border-rose-200 text-xs p-2.5 rounded-lg focus:outline-none"
                  />
                  <div className="flex gap-2">
                    <button
                      type="button"
                      onClick={() => setDeclineTarget(null)}
                      className="px-3 py-1.5 text-slate-500 hover:bg-slate-100 text-xs font-semibold rounded-lg"
                    >
                      {t("assignmentDetail.cancel")}
                    </button>
                    <button
                      type="button"
                      disabled={submittingStatus}
                      onClick={handleConfirmDecline}
                      className="bg-rose-600 hover:bg-rose-700 text-white font-semibold text-xs px-3.5 py-1.5 rounded-lg"
                    >
                      {t("assignmentDetail.confirmDecline")}
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
                      className={`text-xs font-semibold px-3.5 py-1.5 rounded-lg ${
                        target === "DECLINED"
                          ? "bg-rose-50 text-rose-600 hover:bg-rose-100"
                          : "bg-brand-gradient hover:opacity-95 text-white"
                      }`}
                    >
                      {assignmentActionLabel(t, target)}
                    </button>
                  ))}
                </div>
              )}
            </div>
          )}

          <div className="space-y-2">
            <span className="text-[10px] uppercase font-bold tracking-wider text-slate-400 block font-display">{t("assignmentDetail.attachmentsLabel")}</span>
            <div className="space-y-1.5">
              {attachments.map((a) => (
                <a
                  key={a.id}
                  href={a.fileUrl}
                  target="_blank"
                  rel="noreferrer"
                  className="flex items-center gap-1.5 text-xs text-brand-orange hover:underline"
                >
                  <Link2 className="w-3.5 h-3.5" />
                  {a.fileName}
                </a>
              ))}
              {attachments.length === 0 && <p className="text-xs text-slate-400 italic">{t("assignmentDetail.noAttachments")}</p>}
            </div>
            <form onSubmit={handleAddAttachment} className="flex gap-2">
              <input
                value={attachName}
                onChange={(e) => setAttachName(e.target.value)}
                placeholder={t("assignmentDetail.attachmentNamePlaceholder")}
                className="flex-1 bg-slate-50 border border-slate-200 text-xs px-3 py-2 rounded-lg focus:outline-none"
              />
              <input
                value={attachUrl}
                onChange={(e) => setAttachUrl(e.target.value)}
                placeholder={t("assignmentDetail.attachmentUrlPlaceholder")}
                className="flex-1 bg-slate-50 border border-slate-200 text-xs px-3 py-2 rounded-lg focus:outline-none"
              />
              <button type="submit" className="bg-slate-100 hover:bg-slate-200 text-slate-600 text-xs font-semibold px-3 py-2 rounded-lg shrink-0">
                {t("assignmentDetail.addButton")}
              </button>
            </form>
          </div>

          <div className="space-y-3">
            <span className="text-[10px] uppercase font-bold tracking-wider text-slate-400 block font-display">{t("assignmentDetail.historyLabel")}</span>
            <div className="space-y-2">
              {comments.map((cmt) => (
                <div key={cmt.id} className="p-2.5 rounded-lg bg-slate-50 border border-slate-100 text-xs">
                  <div className="flex items-center justify-between font-semibold text-slate-700">
                    <span>{cmt.commenterFullName}</span>
                    <span className="text-[9px] text-slate-400 font-mono">{formatDateTime(cmt.createdAt, i18n.language)}</span>
                  </div>
                  <p className="text-slate-600 mt-1">{cmt.content}</p>
                </div>
              ))}
              {comments.length === 0 && <p className="text-xs text-slate-400 italic">{t("assignmentDetail.noComments")}</p>}
            </div>

            <form onSubmit={handleAddComment} className="flex gap-2">
              <input
                type="text"
                placeholder={t("assignmentDetail.commentPlaceholder")}
                value={newComment}
                onChange={(e) => setNewComment(e.target.value)}
                className="flex-1 bg-slate-50 border border-slate-200 text-xs px-3 py-2 rounded-lg focus:outline-none"
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
