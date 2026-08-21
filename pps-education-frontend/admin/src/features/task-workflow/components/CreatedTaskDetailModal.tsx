import React, { useEffect, useState } from "react";
import { Ban, Link2, RefreshCw, Send } from "lucide-react";
import { useTranslation } from "react-i18next";
import Modal from "@/components/ui/Modal";
import { ApiError } from "@/lib/apiClient";
import { formatDateTime } from "@/lib/i18nFormat";
import { searchUsers, UserListItemResponse } from "@/features/system-admin/api";
import {
  addAttachment,
  addComment,
  AssignmentStatus,
  cancelTask,
  listAssignments,
  listAttachments,
  listComments,
  reassignTask,
  TaskAssignmentResponse,
  TaskAttachmentResponse,
  TaskCommentResponse,
  TaskResponse,
  updateAssignmentStatus
} from "../api";
import { ASSIGNER_TRANSITIONS, ASSIGNMENT_STATUS_META, assignmentStatusLabel } from "../statusMeta";
import { useToast } from "@/lib/useToast";
import Toast from "@/components/ui/Toast";
import { useDialog } from "@/components/ui/DialogProvider";

const inputClass = "w-full bg-slate-50 border border-slate-200 text-xs p-2.5 rounded-lg focus:outline-none";

/** UC-06/07 A3: người nhận lại phải là nhân sự — loại STUDENT/PARENT (xem CreateTaskModal.tsx). */
function isPersonnel(u: UserListItemResponse): boolean {
  return !u.roles.some((r) => r.code === "STUDENT" || r.code === "PARENT");
}

interface CreatedTaskDetailModalProps {
  task: TaskResponse;
  onClose: () => void;
  onTaskChanged: () => void;
}

/** UC-07 Main Flow bước 5, A2, A3: người giao xem toàn bộ phân công của 1 việc, duyệt/từ chối "Chờ duyệt", giao lại phân công DECLINED. */
export default function CreatedTaskDetailModal({ task, onClose, onTaskChanged }: CreatedTaskDetailModalProps) {
  const { t, i18n } = useTranslation("task-workflow");
  const [assignments, setAssignments] = useState<TaskAssignmentResponse[]>([]);
  const [comments, setComments] = useState<TaskCommentResponse[]>([]);
  const [attachments, setAttachments] = useState<TaskAttachmentResponse[]>([]);
  const [taskStatus, setTaskStatus] = useState(task.status);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const [rejectTarget, setRejectTarget] = useState<TaskAssignmentResponse | null>(null);
  const [rejectReason, setRejectReason] = useState("");
  const [reassignTarget, setReassignTarget] = useState<TaskAssignmentResponse | null>(null);
  const [reassignQuery, setReassignQuery] = useState("");
  const [reassignResults, setReassignResults] = useState<UserListItemResponse[]>([]);
  const [reassignComment, setReassignComment] = useState("");
  const [busy, setBusy] = useState(false);

  const [newComment, setNewComment] = useState("");
  const [attachUrl, setAttachUrl] = useState("");
  const [attachName, setAttachName] = useState("");
  const { message: toastMessage, showToast } = useToast();
  const { confirmDialog, promptDialog } = useDialog();

  const load = () => {
    setLoading(true);
    Promise.all([listAssignments(task.id), listComments(task.id), listAttachments(task.id)])
      .then(([a, c, f]) => {
        setAssignments(a);
        setComments(c);
        setAttachments(f);
      })
      .catch((err) => setError(err instanceof ApiError ? err.message : t("createdTaskDetail.loadError")))
      .finally(() => setLoading(false));
  };

  useEffect(load, [task.id]);

  const handleCancelTask = async () => {
    if (
      !(await confirmDialog(
        t("createdTaskDetail.cancelConfirm", { title: task.title }),
        { danger: true }
      ))
    )
      return;
    const reason = (await promptDialog(t("createdTaskDetail.cancelReasonPrompt"), { multiline: true })) ?? undefined;
    setBusy(true);
    setError(null);
    try {
      const updated = await cancelTask(task.id, { reason: reason?.trim() || undefined });
      setTaskStatus(updated.status);
      onTaskChanged();
      showToast(t("createdTaskDetail.taskCancelledToast"));
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t("createdTaskDetail.taskCancelError"));
    } finally {
      setBusy(false);
    }
  };

  const handleApprove = async (a: TaskAssignmentResponse) => {
    setBusy(true);
    setError(null);
    try {
      const updated = await updateAssignmentStatus(a.id, { status: "COMPLETED" });
      setAssignments((prev) => prev.map((x) => (x.id === updated.id ? updated : x)));
      onTaskChanged();
      showToast(t("createdTaskDetail.approvedToast"));
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t("createdTaskDetail.approveError"));
    } finally {
      setBusy(false);
    }
  };

  const handleConfirmReject = async () => {
    if (!rejectTarget || !rejectReason.trim()) {
      setError(t("createdTaskDetail.rejectReasonRequired"));
      return;
    }
    setBusy(true);
    setError(null);
    try {
      const updated = await updateAssignmentStatus(rejectTarget.id, { status: "IN_PROGRESS", comment: rejectReason.trim() });
      setAssignments((prev) => prev.map((x) => (x.id === updated.id ? updated : x)));
      setRejectTarget(null);
      setRejectReason("");
      onTaskChanged();
      showToast(t("createdTaskDetail.rejectedToast"));
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t("createdTaskDetail.rejectError"));
    } finally {
      setBusy(false);
    }
  };

  const handleReassignSearch = (q: string) => {
    setReassignQuery(q);
    if (!q.trim()) {
      setReassignResults([]);
      return;
    }
    searchUsers({ keyword: q.trim() }, 0, 8).then((res) => setReassignResults(res.content.filter(isPersonnel)));
  };

  const handleConfirmReassign = async (newAssignee: UserListItemResponse) => {
    if (!reassignTarget) return;
    setBusy(true);
    setError(null);
    try {
      const created = await reassignTask(task.id, {
        fromAssignmentId: reassignTarget.id,
        newAssigneeUserId: newAssignee.id,
        comment: reassignComment.trim() || undefined
      });
      setAssignments((prev) => [...prev, created]);
      setReassignTarget(null);
      setReassignQuery("");
      setReassignResults([]);
      setReassignComment("");
      onTaskChanged();
      showToast(t("createdTaskDetail.reassignedToast"));
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t("createdTaskDetail.reassignError"));
    } finally {
      setBusy(false);
    }
  };

  const handleAddComment = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!newComment.trim()) return;
    try {
      const created = await addComment(task.id, { content: newComment.trim() });
      setComments((prev) => [...prev, created]);
      setNewComment("");
      showToast(t("createdTaskDetail.commentAddedToast"));
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t("createdTaskDetail.commentAddError"));
    }
  };

  const handleAddAttachment = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!attachUrl.trim() || !attachName.trim()) return;
    try {
      const created = await addAttachment(task.id, { fileUrl: attachUrl.trim(), fileName: attachName.trim() });
      setAttachments((prev) => [...prev, created]);
      setAttachUrl("");
      setAttachName("");
      showToast(t("createdTaskDetail.attachmentAddedToast"));
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t("createdTaskDetail.attachmentAddError"));
    }
  };

  return (
    <Modal
      open
      onClose={onClose}
      title={task.title}
      description={t("createdTaskDetail.taskCodePrefix", { taskCode: task.taskCode, priority: t(`priority.${task.priority}`) })}
      size="lg"
    >
      {loading ? (
        <p className="text-xs text-slate-500">{t("createdTaskDetail.loading")}</p>
      ) : (
        <div className="space-y-4">
          {error && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg">{error}</div>}

          <div className="flex items-center justify-between gap-2">
            <span className="text-[10px] font-bold uppercase px-2 py-0.5 rounded-full bg-slate-100 text-slate-600">
              {t(`taskStatus.${taskStatus}`, taskStatus)}
            </span>
            {taskStatus !== "COMPLETED" && taskStatus !== "CANCELLED" && (
              <button
                type="button"
                disabled={busy}
                onClick={handleCancelTask}
                className="flex items-center gap-1.5 text-xs font-semibold px-3 py-1.5 rounded-lg bg-rose-50 hover:bg-rose-100 text-rose-600 disabled:opacity-50"
              >
                <Ban className="w-3.5 h-3.5" /> {t("createdTaskDetail.cancelTaskButton")}
              </button>
            )}
          </div>

          <div className="space-y-1">
            <span className="text-[10px] uppercase font-bold tracking-wider text-slate-400 block font-display">{t("createdTaskDetail.descriptionLabel")}</span>
            <p className="text-xs text-slate-700 leading-relaxed bg-slate-50 p-3 rounded-lg border border-slate-100">{task.description || t("createdTaskDetail.noDescription")}</p>
          </div>

          <div className="space-y-2">
            <span className="text-[10px] uppercase font-bold tracking-wider text-slate-400 block font-display">{t("createdTaskDetail.assigneesLabel")}</span>
            <div className="space-y-2">
              {assignments.map((a) => {
                const meta = ASSIGNMENT_STATUS_META[a.assignmentStatus];
                const canReject = ASSIGNER_TRANSITIONS[a.assignmentStatus].includes("IN_PROGRESS");
                const canApprove = ASSIGNER_TRANSITIONS[a.assignmentStatus].includes("COMPLETED");
                return (
                  <div key={a.id} className="border border-slate-200 rounded-lg p-3 space-y-2">
                    <div className="flex items-center justify-between gap-2">
                      <span className="font-bold text-slate-800 text-xs">{a.assigneeFullName}</span>
                      <span className={`text-[10px] font-bold px-2 py-0.5 rounded-full ${meta.badge}`}>{assignmentStatusLabel(t, a.assignmentStatus)}</span>
                    </div>
                    {a.declineReason && <p className="text-[11px] text-rose-600">{t("createdTaskDetail.declineReasonLabel", { reason: a.declineReason })}</p>}

                    {(canApprove || canReject) && taskStatus !== "CANCELLED" && (
                      <div className="flex gap-2">
                        {canApprove && (
                          <button
                            type="button"
                            disabled={busy}
                            onClick={() => handleApprove(a)}
                            className="text-xs font-semibold px-3 py-1.5 rounded-lg bg-emerald-600 hover:bg-emerald-700 text-white"
                          >
                            {t("createdTaskDetail.approveButton")}
                          </button>
                        )}
                        {canReject && (
                          <button
                            type="button"
                            disabled={busy}
                            onClick={() => setRejectTarget(a)}
                            className="text-xs font-semibold px-3 py-1.5 rounded-lg bg-rose-50 hover:bg-rose-100 text-rose-600"
                          >
                            {t("createdTaskDetail.rejectButton")}
                          </button>
                        )}
                      </div>
                    )}

                    {a.assignmentStatus === "DECLINED" && taskStatus !== "CANCELLED" && reassignTarget?.id !== a.id && (
                      <button
                        type="button"
                        onClick={() => setReassignTarget(a)}
                        className="flex items-center gap-1.5 text-xs font-semibold px-3 py-1.5 rounded-lg bg-brand-gradient hover:opacity-95 text-white"
                      >
                        <RefreshCw className="w-3.5 h-3.5" /> {t("createdTaskDetail.reassignButton")}
                      </button>
                    )}

                    {rejectTarget?.id === a.id && (
                      <div className="space-y-2 bg-rose-50/50 border border-rose-100 rounded-lg p-2.5">
                        <textarea
                          value={rejectReason}
                          onChange={(e) => setRejectReason(e.target.value)}
                          placeholder={t("createdTaskDetail.rejectReasonPlaceholder")}
                          rows={2}
                          className="w-full bg-white border border-rose-200 text-xs p-2 rounded-lg focus:outline-none"
                        />
                        <div className="flex gap-2">
                          <button type="button" onClick={() => setRejectTarget(null)} className="px-3 py-1.5 text-slate-500 hover:bg-slate-100 text-xs font-semibold rounded-lg">
                            {t("createdTaskDetail.cancel")}
                          </button>
                          <button
                            type="button"
                            disabled={busy}
                            onClick={handleConfirmReject}
                            className="bg-rose-600 hover:bg-rose-700 text-white font-semibold text-xs px-3.5 py-1.5 rounded-lg"
                          >
                            {t("createdTaskDetail.confirmReject")}
                          </button>
                        </div>
                      </div>
                    )}

                    {reassignTarget?.id === a.id && (
                      <div className="space-y-2 bg-slate-50 border border-slate-200 rounded-lg p-2.5 relative">
                        <input
                          value={reassignQuery}
                          onChange={(e) => handleReassignSearch(e.target.value)}
                          placeholder={t("createdTaskDetail.reassignSearchPlaceholder")}
                          name="task-reassign-lookup"
                          autoComplete="off"
                          autoCorrect="off"
                          autoCapitalize="off"
                          spellCheck={false}
                          className={inputClass}
                        />
                        {reassignResults.length > 0 && (
                          <div className="absolute z-10 left-2.5 right-2.5 bg-white border border-slate-200 rounded-lg shadow-lg divide-y divide-slate-100 max-h-40 overflow-y-auto">
                            {reassignResults.map((u) => (
                              <button
                                key={u.id}
                                type="button"
                                disabled={busy}
                                onClick={() => handleConfirmReassign(u)}
                                className="w-full text-left px-3 py-2 hover:bg-slate-50 text-xs"
                              >
                                {u.fullName} <span className="text-slate-400">({u.username})</span>
                              </button>
                            ))}
                          </div>
                        )}
                        <input
                          value={reassignComment}
                          onChange={(e) => setReassignComment(e.target.value)}
                          placeholder={t("createdTaskDetail.reassignReasonPlaceholder")}
                          className={inputClass}
                        />
                        <button
                          type="button"
                          onClick={() => {
                            setReassignTarget(null);
                            setReassignQuery("");
                            setReassignResults([]);
                          }}
                          className="px-3 py-1.5 text-slate-500 hover:bg-slate-100 text-xs font-semibold rounded-lg"
                        >
                          {t("createdTaskDetail.cancel")}
                        </button>
                      </div>
                    )}
                  </div>
                );
              })}
              {assignments.length === 0 && <p className="text-xs text-slate-400 italic">{t("createdTaskDetail.noAssignees")}</p>}
            </div>
          </div>

          <div className="space-y-2">
            <span className="text-[10px] uppercase font-bold tracking-wider text-slate-400 block font-display">{t("createdTaskDetail.attachmentsLabel")}</span>
            <div className="space-y-1.5">
              {attachments.map((a) => (
                <a key={a.id} href={a.fileUrl} target="_blank" rel="noreferrer" className="flex items-center gap-1.5 text-xs text-brand-orange hover:underline">
                  <Link2 className="w-3.5 h-3.5" />
                  {a.fileName}
                </a>
              ))}
              {attachments.length === 0 && <p className="text-xs text-slate-400 italic">{t("createdTaskDetail.noAttachments")}</p>}
            </div>
            <form onSubmit={handleAddAttachment} className="flex gap-2">
              <input value={attachName} onChange={(e) => setAttachName(e.target.value)} placeholder={t("createdTaskDetail.attachmentNamePlaceholder")} className="flex-1 bg-slate-50 border border-slate-200 text-xs px-3 py-2 rounded-lg focus:outline-none" />
              <input value={attachUrl} onChange={(e) => setAttachUrl(e.target.value)} placeholder={t("createdTaskDetail.attachmentUrlPlaceholder")} className="flex-1 bg-slate-50 border border-slate-200 text-xs px-3 py-2 rounded-lg focus:outline-none" />
              <button type="submit" className="bg-slate-100 hover:bg-slate-200 text-slate-600 text-xs font-semibold px-3 py-2 rounded-lg shrink-0">
                {t("createdTaskDetail.addButton")}
              </button>
            </form>
          </div>

          <div className="space-y-3">
            <span className="text-[10px] uppercase font-bold tracking-wider text-slate-400 block font-display">{t("createdTaskDetail.historyLabel")}</span>
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
              {comments.length === 0 && <p className="text-xs text-slate-400 italic">{t("createdTaskDetail.noComments")}</p>}
            </div>
            <form onSubmit={handleAddComment} className="flex gap-2">
              <input
                type="text"
                placeholder={t("createdTaskDetail.commentPlaceholder")}
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
