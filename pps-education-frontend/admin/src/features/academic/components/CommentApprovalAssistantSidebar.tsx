import React, { useEffect, useRef, useState } from "react";
import { AlertTriangle, BellRing, Bot, Check, CheckCheck, Info, Loader2, Mic, Paperclip, RefreshCw, Sparkles, X } from "lucide-react";
import { useTranslation } from "react-i18next";
import { ApiError } from "@/lib/apiClient";
import Select from "@/components/ui/Select";
import AiChatComposer, { AiChatComposerHandle, AiComposerAudio, formatSeconds } from "@/components/ai/AiChatComposer";
import {
  CommentAiInstructionResult,
  CommentAiReview,
  CommentAiReviewResult,
  CommentAiReviewSummary,
  CommentAiSuggestionResult,
  CommentAttitudeAlert,
  StudentCommentResponse,
  commentAiInstructionJobPath,
  startCommentAiInstruction,
  waitForAiJob
} from "../api";

export interface PendingClassOption {
  classId: number;
  label: string;
  items: StudentCommentResponse[];
}

type ChatMessageInput =
  | { role: "manager"; text: string; audioUrl?: string }
  | {
      role: "assistant";
      text: string;
      error?: boolean;
      reviewClassId?: number;
      summary?: CommentAiReviewSummary;
      instruction?: CommentAiInstructionResult;
    };
type ChatMessage = ChatMessageInput & { id: number };

interface Props {
  open: boolean;
  onClose: () => void;
  classes: PendingClassOption[];
  reviewByCommentId: Record<number, CommentAiReview>;
  suggestionByCommentId: Record<number, CommentAiSuggestionResult>;
  suggestingId: number | null;
  applyingId: number | null;
  decidingClassId: number | null;
  /** Soát cả lớp (UC-75 bước 1-5) — trả kết quả để hiện tóm tắt trong khung chat, null nếu lỗi. */
  onReview: (classId: number) => Promise<CommentAiReviewResult | null>;
  /** Duyệt các dòng đã soát và không có cảnh báo — Quản lý bấm, trợ lý không tự duyệt. Trả số dòng đã duyệt. */
  onApproveClean: (classId: number) => Promise<number>;
  onSuggest: (comment: StudentCommentResponse) => Promise<void>;
  /** AI soạn sẵn lý do từ chối rồi mở hộp thoại Từ chối điền sẵn — Quản lý sửa và tự bấm xác nhận. */
  onRejectWithAi: (comment: StudentCommentResponse) => Promise<void>;
  draftingReasonId: number | null;
  /** Dòng Yếu/Trung bình: duyệt sẽ báo phụ huynh (bổ sung 2026-09-29). */
  attitudeAlertById: Record<number, CommentAttitudeAlert>;
  onApplySuggestion: (comment: StudentCommentResponse) => Promise<boolean>;
  onDismissSuggestion: (commentId: number) => void;
  /** Lưu 1 bản sửa qua đúng chức năng sửa nội dung Chờ duyệt của UC-22. */
  onApplyContent: (commentId: number, content: string) => Promise<boolean>;
  onBusyChange?: (busy: boolean) => void;
}

/**
 * UC-75: Trợ lý AI duyệt nhận xét (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-29) — sidebar
 * giống trợ lý soạn nháp của Giáo viên (UC-74): chọn lớp đang chờ duyệt, bấm soát, ra yêu cầu sửa bằng giọng
 * nói/chữ, và các nút gợi ý thao tác (Duyệt dòng không có cảnh báo / AI đề xuất bản sửa / Áp dụng). Trợ lý chỉ
 * gợi ý: mọi thay đổi đều do Quản lý bấm, lưu qua đúng chức năng duyệt/sửa nội dung của UC-22.
 */
export default function CommentApprovalAssistantSidebar({
  open,
  onClose,
  classes,
  reviewByCommentId,
  suggestionByCommentId,
  suggestingId,
  applyingId,
  decidingClassId,
  onReview,
  onApproveClean,
  onSuggest,
  onRejectWithAi,
  draftingReasonId,
  attitudeAlertById,
  onApplySuggestion,
  onDismissSuggestion,
  onApplyContent,
  onBusyChange
}: Props) {
  const { t } = useTranslation("academic-comments");
  const [selectedClassId, setSelectedClassId] = useState<number | null>(classes[0]?.classId ?? null);
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [busy, setBusy] = useState(false);
  const [appliedCommentIds, setAppliedCommentIds] = useState<Set<number>>(new Set());
  const nextId = useRef(1);
  const composerRef = useRef<AiChatComposerHandle>(null);
  const scrollRef = useRef<HTMLDivElement>(null);

  const push = (message: ChatMessageInput) => setMessages((prev) => [...prev, { ...message, id: nextId.current++ }]);

  // Lớp đang chọn đã duyệt hết (biến khỏi danh sách chờ) → tự chuyển sang lớp đầu tiên còn chờ duyệt.
  useEffect(() => {
    if (selectedClassId == null || !classes.some((c) => c.classId === selectedClassId)) {
      setSelectedClassId(classes[0]?.classId ?? null);
    }
  }, [classes, selectedClassId]);

  useEffect(() => {
    onBusyChange?.(busy);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [busy]);

  useEffect(() => {
    scrollRef.current?.scrollTo({ top: scrollRef.current.scrollHeight, behavior: "smooth" });
  }, [messages, busy]);

  const selectedClass = classes.find((c) => c.classId === selectedClassId) ?? null;
  const pendingById = new Map(classes.flatMap((c) => c.items).map((cm) => [cm.id, cm]));

  const handleReview = async () => {
    if (!selectedClass || busy) return;
    const classId = selectedClass.classId;
    push({ role: "manager", text: t("approvalByClass.assistant.reviewCommand", { className: selectedClass.label }) });
    setBusy(true);
    try {
      const result = await onReview(classId);
      push(
        result
          ? { role: "assistant", text: result.message, reviewClassId: classId, summary: result.summary }
          : { role: "assistant", text: t("approvalByClass.aiReview.errors.failed"), error: true }
      );
    } finally {
      setBusy(false);
    }
  };

  const handleApproveClean = async (classId: number) => {
    const count = await onApproveClean(classId);
    if (count > 0) push({ role: "assistant", text: t("approvalByClass.aiReview.approvedClean", { count }) });
  };

  const handleSend = (audio: AiComposerAudio | null, text: string): boolean => {
    if (!selectedClass || busy) return false;
    const commentIds = selectedClass.items.map((cm) => cm.id);
    push({
      role: "manager",
      text: text || t("approvalByClass.assistant.audioMessage", { duration: audio?.seconds ? formatSeconds(audio.seconds) : "?" }),
      audioUrl: audio?.url
    });
    setBusy(true);
    void (async () => {
      try {
        const job = await waitForAiJob(await startCommentAiInstruction(commentIds, audio?.blob ?? null, text), commentAiInstructionJobPath);
        if (job.status === "DONE" && job.result) {
          push({ role: "assistant", text: job.result.assistantMessage, instruction: job.result });
        } else {
          push({ role: "assistant", text: job.errorMessage ?? t("dailyCommentPanel.aiAssistant.errors.generic"), error: true });
        }
      } catch (err) {
        push({ role: "assistant", text: err instanceof ApiError ? err.message : t("dailyCommentPanel.aiAssistant.errors.generic"), error: true });
      } finally {
        setBusy(false);
      }
    })();
    return true;
  };

  const applyChange = async (commentId: number, content: string) => {
    if (await onApplyContent(commentId, content)) {
      setAppliedCommentIds((prev) => new Set(prev).add(commentId));
      return true;
    }
    return false;
  };

  const applyAllChanges = async (instruction: CommentAiInstructionResult) => {
    let applied = 0;
    for (const change of instruction.changes) {
      if (appliedCommentIds.has(change.commentId) || !pendingById.has(change.commentId)) continue;
      if (await applyChange(change.commentId, change.suggestedContent)) applied++;
    }
    push({ role: "assistant", text: t("approvalByClass.assistant.appliedAll", { count: applied }) });
  };

  if (!open) return null;

  const latestReviewMessageId = [...messages].reverse().find((m) => m.role === "assistant" && m.reviewClassId != null)?.id;

  return (
    <div className="fixed inset-y-0 right-0 z-40 w-full sm:w-[560px] xl:w-[640px] bg-white border-l border-slate-200 shadow-2xl flex flex-col">
      <div className="px-4 py-3 border-b border-slate-100 flex items-center justify-between gap-2 bg-slate-50">
        <div className="min-w-0 flex-1">
          <div className="flex items-center gap-1.5 text-base font-bold text-slate-800">
            <Bot className="w-5 h-5 text-violet-600" />
            {t("approvalByClass.assistant.title")}
          </div>
          {classes.length > 0 && (
            <Select
              value={selectedClassId ?? ""}
              onChange={(e) => setSelectedClassId(Number(e.target.value))}
              className="mt-1 bg-white border border-slate-200 text-xs font-semibold text-slate-700 px-2 py-1 rounded"
            >
              {classes.map((c) => (
                <option key={c.classId} value={c.classId}>
                  {t("approvalByClass.assistant.classOption", { className: c.label, count: c.items.length })}
                </option>
              ))}
            </Select>
          )}
        </div>
        <button type="button" onClick={onClose} className="p-1.5 rounded-lg hover:bg-slate-200 text-slate-500" aria-label={t("dailyCommentPanel.aiAssistant.close")}>
          <X className="w-4 h-4" />
        </button>
      </div>

      <div ref={scrollRef} className="flex-1 overflow-y-auto px-4 py-3 space-y-3">
        <div className="text-sm text-slate-600 bg-violet-50 border border-violet-100 rounded-lg p-3 space-y-1.5">
          <p>{t("approvalByClass.assistant.intro")}</p>
          <p className="text-xs text-violet-700">{t("approvalByClass.assistant.permissionNote")}</p>
        </div>

        {selectedClass && (
          <div className="flex flex-wrap gap-2">
            <button type="button" onClick={() => void handleReview()} disabled={busy} className="flex items-center gap-1.5 text-[13px] font-semibold border border-violet-200 text-violet-700 bg-white hover:bg-violet-50 rounded-full px-3 py-1.5 disabled:opacity-50">
              <Sparkles className="w-3.5 h-3.5" />
              {t("approvalByClass.assistant.reviewChip")}
            </button>
            <button type="button" onClick={() => composerRef.current?.startRecording()} disabled={busy} className="flex items-center gap-1.5 text-[13px] font-semibold border border-violet-200 text-violet-700 bg-white hover:bg-violet-50 rounded-full px-3 py-1.5 disabled:opacity-50">
              <Mic className="w-3.5 h-3.5" />
              {t("approvalByClass.assistant.recordChip")}
            </button>
            <button type="button" onClick={() => composerRef.current?.openFilePicker()} disabled={busy} className="flex items-center gap-1.5 text-[13px] font-semibold border border-violet-200 text-violet-700 bg-white hover:bg-violet-50 rounded-full px-3 py-1.5 disabled:opacity-50">
              <Paperclip className="w-3.5 h-3.5" />
              {t("dailyCommentPanel.aiAssistant.suggestions.upload")}
            </button>
          </div>
        )}

        {messages.map((m) =>
          m.role === "manager" ? (
            <div key={m.id} className="flex justify-end">
              <div className="max-w-[85%] bg-brand-orange/10 border border-brand-orange/20 text-sm text-slate-800 rounded-2xl rounded-br-sm px-3 py-2 space-y-1.5">
                <p className="whitespace-pre-wrap">{m.text}</p>
                {m.audioUrl && <audio controls src={m.audioUrl} className="w-full h-8" />}
              </div>
            </div>
          ) : (
            <div key={m.id} className="flex justify-start">
              <div className={`max-w-[95%] w-full text-sm rounded-2xl rounded-bl-sm px-3 py-2 space-y-2 border ${m.error ? "bg-rose-50 border-rose-100 text-rose-700" : "bg-slate-50 border-slate-200 text-slate-700"}`}>
                <p className="whitespace-pre-wrap">{m.text}</p>
                {m.summary && (
                  <div className="flex flex-wrap gap-1">
                    <span className="text-[11px] font-semibold rounded-full px-2 py-0.5 bg-emerald-50 text-emerald-700 border border-emerald-200">
                      {t("approvalByClass.aiReview.summary.clean", { count: m.summary.cleanCount })}
                    </span>
                    {m.summary.issueCounts.map((c) => (
                      <span key={c.type} className="text-[11px] font-semibold rounded-full px-2 py-0.5 bg-amber-50 text-amber-800 border border-amber-200">
                        {t("approvalByClass.aiReview.summary.issue", {
                          count: c.count,
                          label: t(`approvalByClass.aiReview.issueType.${c.type}`, { defaultValue: c.type })
                        })}
                      </span>
                    ))}
                    {m.summary.parentAlertCount > 0 && (
                      <span className="text-[11px] font-semibold rounded-full px-2 py-0.5 bg-orange-50 text-orange-700 border border-orange-200">
                        {t("approvalByClass.aiReview.summary.parentAlerts", { count: m.summary.parentAlertCount })}
                      </span>
                    )}
                    {m.summary.escalationCount > 0 && (
                      <span className="text-[11px] font-semibold rounded-full px-2 py-0.5 bg-rose-50 text-rose-700 border border-rose-200">
                        {t("approvalByClass.aiReview.summary.escalations", { count: m.summary.escalationCount })}
                      </span>
                    )}
                    {m.summary.repeatedPatternCount > 0 && (
                      <span className="text-[11px] font-semibold rounded-full px-2 py-0.5 bg-sky-50 text-sky-700 border border-sky-200">
                        {t("approvalByClass.aiReview.summary.repeatedPattern", { count: m.summary.repeatedPatternCount })}
                      </span>
                    )}
                    {m.summary.homeworkMismatchCount > 0 && (
                      <span className="text-[11px] font-semibold rounded-full px-2 py-0.5 bg-sky-50 text-sky-700 border border-sky-200">
                        {t("approvalByClass.aiReview.summary.homeworkMismatch", { count: m.summary.homeworkMismatchCount })}
                      </span>
                    )}
                  </div>
                )}
                {m.reviewClassId != null && m.id === latestReviewMessageId && (() => {
                  const cls = classes.find((c) => c.classId === m.reviewClassId);
                  if (!cls) return <p className="text-xs text-slate-400 italic">{t("approvalByClass.assistant.classDone")}</p>;
                  const noticesOf = (id: number) => (reviewByCommentId[id]?.notices ?? []).filter((n) => n.type !== "ATTITUDE_ALERT");
                  // Dòng có lỗi, hoặc chỉ có lưu ý của AI (VD BTVN ngược dữ liệu) — lưu ý không chặn "Duyệt dòng không có cảnh báo".
                  const flagged = cls.items.filter((cm) => (reviewByCommentId[cm.id]?.issues.length ?? 0) > 0 || noticesOf(cm.id).length > 0);
                  const cleanCount = cls.items.filter((cm) => reviewByCommentId[cm.id] && reviewByCommentId[cm.id].issues.length === 0).length;
                  return (
                    <div className="space-y-2">
                      <div className="flex flex-wrap gap-1.5">
                        {cleanCount > 0 && (
                          <button
                            type="button"
                            onClick={() => void handleApproveClean(cls.classId)}
                            disabled={decidingClassId === cls.classId}
                            className="flex items-center gap-1 text-[13px] font-semibold bg-emerald-600 text-white hover:bg-emerald-700 rounded-full px-3 py-1.5 disabled:opacity-50"
                          >
                            <CheckCheck className="w-3.5 h-3.5" />
                            {t("approvalByClass.aiReview.approveClean", { count: cleanCount })}
                          </button>
                        )}
                        <button type="button" onClick={() => void handleReview()} disabled={busy} className="flex items-center gap-1 text-[13px] font-semibold bg-white border border-violet-200 text-violet-700 hover:bg-violet-50 rounded-full px-3 py-1.5 disabled:opacity-50">
                          <RefreshCw className="w-3.5 h-3.5" />
                          {t("approvalByClass.assistant.reviewAgain")}
                        </button>
                      </div>
                      {flagged.length > 0 && (
                        <ul className="space-y-1.5 max-h-[28rem] overflow-y-auto pr-1">
                          {flagged.map((cm) => {
                            const suggestion = suggestionByCommentId[cm.id];
                            return (
                              <li key={cm.id} className="bg-white border border-amber-200 rounded-lg p-2 space-y-1">
                                <p className="font-bold text-slate-800">{cm.studentFullName}</p>
                                {attitudeAlertById[cm.id] && (
                                  <p className={`flex items-start gap-1 text-xs ${attitudeAlertById[cm.id].escalation ? "text-rose-700" : "text-orange-700"}`}>
                                    <BellRing className="w-3 h-3 mt-0.5 shrink-0" />
                                    {attitudeAlertById[cm.id].message}
                                  </p>
                                )}
                                {noticesOf(cm.id).map((notice, i) => (
                                  <p key={`n${i}`} className="flex items-start gap-1 text-xs text-sky-800">
                                    <Info className="w-3 h-3 mt-0.5 shrink-0" />
                                    <span>
                                      <span className="font-bold">{t("approvalByClass.aiReview.notice")}: </span>
                                      {notice.message}
                                    </span>
                                  </p>
                                ))}
                                {reviewByCommentId[cm.id].issues.map((issue, i) => (
                                  <p key={i} className="flex items-start gap-1 text-xs text-amber-800">
                                    <AlertTriangle className="w-3 h-3 mt-0.5 shrink-0" />
                                    <span>
                                      <span className="font-bold">{t(`approvalByClass.aiReview.source.${issue.source}`)}: </span>
                                      {issue.message}
                                    </span>
                                  </p>
                                ))}
                                {suggestion ? (
                                  <div className="border border-violet-200 bg-violet-50/70 rounded-lg p-2 space-y-1">
                                    <p className="text-xs font-bold uppercase text-violet-700">{t("approvalByClass.aiReview.suggestionTitle")}</p>
                                    <p className="whitespace-pre-wrap text-slate-800">{suggestion.suggestedContent}</p>
                                    {suggestion.explanation && <p className="text-xs text-slate-500 italic">{suggestion.explanation}</p>}
                                    {suggestion.warnings.map((w, i) => (
                                      <p key={i} className="flex items-start gap-1 text-xs text-amber-700">
                                        <AlertTriangle className="w-3 h-3 mt-0.5 shrink-0" />
                                        {w}
                                      </p>
                                    ))}
                                    <div className="flex justify-end gap-1.5">
                                      <button type="button" onClick={() => onDismissSuggestion(cm.id)} className="px-2 py-1 text-slate-500 hover:bg-slate-100 text-xs font-bold rounded-lg">
                                        {t("approvalByClass.aiReview.dismiss")}
                                      </button>
                                      <button
                                        type="button"
                                        onClick={() => void onApplySuggestion(cm)}
                                        disabled={applyingId === cm.id}
                                        className="px-2 py-1 bg-violet-600 hover:bg-violet-700 text-white text-xs font-bold rounded-lg disabled:opacity-50"
                                      >
                                        {applyingId === cm.id ? t("approvalByClass.savingEdit") : t("approvalByClass.aiReview.apply")}
                                      </button>
                                    </div>
                                  </div>
                                ) : (
                                  <div className="flex flex-wrap gap-3">
                                    <button
                                      type="button"
                                      onClick={() => void onSuggest(cm)}
                                      disabled={suggestingId !== null}
                                      className="flex items-center gap-1 text-xs font-bold text-violet-700 hover:underline disabled:opacity-50"
                                    >
                                      {suggestingId === cm.id ? <Loader2 className="w-3 h-3 animate-spin" /> : <Sparkles className="w-3 h-3" />}
                                      {suggestingId === cm.id ? t("approvalByClass.aiReview.suggesting") : t("approvalByClass.aiReview.suggestButton")}
                                    </button>
                                    <button
                                      type="button"
                                      onClick={() => void onRejectWithAi(cm)}
                                      disabled={draftingReasonId !== null}
                                      className="flex items-center gap-1 text-xs font-bold text-rose-700 hover:underline disabled:opacity-50"
                                    >
                                      {draftingReasonId === cm.id ? <Loader2 className="w-3 h-3 animate-spin" /> : <X className="w-3 h-3" />}
                                      {draftingReasonId === cm.id ? t("approvalByClass.aiReview.rejectReasonLoading") : t("approvalByClass.aiReview.rejectReasonButton")}
                                    </button>
                                  </div>
                                )}
                              </li>
                            );
                          })}
                        </ul>
                      )}
                    </div>
                  );
                })()}
                {m.instruction && m.instruction.changes.length > 0 && (
                  <div className="space-y-1.5">
                    {m.instruction.transcript && (
                      <p className="text-xs text-slate-500 italic">{t("approvalByClass.assistant.heard", { text: m.instruction.transcript })}</p>
                    )}
                    <ul className="space-y-1.5 max-h-[28rem] overflow-y-auto pr-1">
                      {m.instruction.changes.map((change) => {
                        const applied = appliedCommentIds.has(change.commentId);
                        const stillPending = pendingById.has(change.commentId);
                        return (
                          <li key={change.commentId} className="bg-white border border-slate-200 rounded-lg p-2 space-y-1">
                            <p className="font-bold text-slate-800">{change.studentFullName}</p>
                            <p className="text-xs text-slate-400 line-through whitespace-pre-wrap">{change.originalContent}</p>
                            <p className="text-slate-800 whitespace-pre-wrap">{change.suggestedContent}</p>
                            {change.warnings.map((w, i) => (
                              <p key={i} className="flex items-start gap-1 text-xs text-amber-700">
                                <AlertTriangle className="w-3 h-3 mt-0.5 shrink-0" />
                                {w}
                              </p>
                            ))}
                            <div className="flex justify-end">
                              {applied ? (
                                <span className="flex items-center gap-1 text-xs font-bold text-emerald-700">
                                  <Check className="w-3 h-3" />
                                  {t("approvalByClass.assistant.applied")}
                                </span>
                              ) : stillPending ? (
                                <button
                                  type="button"
                                  onClick={() => void applyChange(change.commentId, change.suggestedContent)}
                                  disabled={applyingId === change.commentId}
                                  className="px-2 py-1 bg-violet-600 hover:bg-violet-700 text-white text-xs font-bold rounded-lg disabled:opacity-50"
                                >
                                  {applyingId === change.commentId ? t("approvalByClass.savingEdit") : t("approvalByClass.aiReview.apply")}
                                </button>
                              ) : (
                                <span className="text-xs text-slate-400 italic">{t("approvalByClass.assistant.noLongerPending")}</span>
                              )}
                            </div>
                          </li>
                        );
                      })}
                    </ul>
                    {m.instruction.changes.filter((c) => !appliedCommentIds.has(c.commentId) && pendingById.has(c.commentId)).length > 1 && (
                      <button
                        type="button"
                        onClick={() => void applyAllChanges(m.instruction!)}
                        disabled={applyingId !== null}
                        className="flex items-center gap-1 text-[13px] font-semibold bg-violet-600 text-white hover:bg-violet-700 rounded-full px-3 py-1.5 disabled:opacity-50"
                      >
                        <CheckCheck className="w-3.5 h-3.5" />
                        {t("approvalByClass.assistant.applyAll")}
                      </button>
                    )}
                  </div>
                )}
              </div>
            </div>
          )
        )}

        {busy && (
          <div className="flex items-center gap-2 text-sm text-slate-500">
            <Loader2 className="w-3.5 h-3.5 animate-spin" />
            {t("approvalByClass.assistant.working")}
          </div>
        )}
      </div>

      <AiChatComposer
        ref={composerRef}
        disabled={!selectedClass}
        busy={busy}
        placeholder={t("approvalByClass.assistant.inputPlaceholder")}
        onSend={handleSend}
      />
    </div>
  );
}
