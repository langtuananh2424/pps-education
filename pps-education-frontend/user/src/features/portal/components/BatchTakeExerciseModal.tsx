import React, { useEffect, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { CheckCircle2, KeyRound, Loader2, PartyPopper, RotateCcw, X } from "lucide-react";
import { friendlyApiErrorMessage } from "@/lib/apiClient";
import {
  AssignedExerciseResponse,
  ExerciseAttemptResponse,
  ExerciseMetaResponse,
  ExerciseQuestionResponse,
  ListeningPlayProgressResponse,
  StudentAnswerResponse,
  getAttempt,
  getExercise,
  listAnswers,
  listExerciseQuestions,
  recordListeningPlay,
  revealAndCloseAttempt,
  saveAnswer,
  startAttempt,
  submitAttempt,
  uploadMedia
} from "../api";
import { GridQuestionGroup, QuestionBlock, groupQuestionsByGroupKey } from "./TakeExerciseModal";

const SKILL_LABEL_KEY: Record<string, string> = {
  VOCAB_GRAMMAR: "assignments.batch.skillLabel.VOCAB_GRAMMAR",
  READING: "assignments.batch.skillLabel.READING",
  WRITING: "assignments.batch.skillLabel.WRITING",
  LISTENING: "assignments.batch.skillLabel.LISTENING"
};

/** "Ngữ pháp — Lesson 1" — dùng chung cho tiêu đề thẻ gộp ở AssignmentsTab lẫn tiêu đề modal. */
export function batchGroupTitle(t: (key: string) => string, items: AssignedExerciseResponse[]): string {
  const first = items[0];
  const skillKey = first.skillCategory ? SKILL_LABEL_KEY[first.skillCategory] : undefined;
  const skillLabel = skillKey ? t(skillKey) : "";
  return skillLabel && first.examTitle ? `${skillLabel} — ${first.examTitle}` : first.examTitle || first.title;
}

interface SubExercise {
  item: AssignedExerciseResponse;
  meta: ExerciseMetaResponse;
  attempt: ExerciseAttemptResponse;
  questions: ExerciseQuestionResponse[];
}

/**
 * V152 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-25) — mirror TakeExerciseModal:
 * lượt làm CÒN IN_PROGRESS (chưa nộp) nhưng đã quá hạn nộp và bản giao không cho nộp muộn — khoá HẲN
 * thành chỉ xem (BE saveAnswer đã chặn tương ứng, đây là lớp chặn ở FE để không hiện ô nhập được nữa).
 */
function isSubOverdueLocked(sub: SubExercise): boolean {
  return sub.attempt.status === "IN_PROGRESS" && sub.item.dueAt != null && !sub.item.lateSubmissionAllowed && new Date(sub.item.dueAt).getTime() < Date.now();
}

interface BatchTakeExerciseModalProps {
  /** N thẻ BTVN cùng homeworkBatchId — xem AssignmentsTab.tsx (đã sort ổn định theo exerciseId). */
  items: AssignedExerciseResponse[];
  onClose: () => void;
}

/**
 * V150 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-25 — thay bản "tuần tự từng Bài"
 * ban đầu theo đúng yêu cầu người dùng: học sinh cần thấy TẤT CẢ câu hỏi của cả Lô liền 1 trang cuộn
 * duy nhất, giống hệt 1 trang bài tập giấy có nhiều Ex., không phải lần lượt mở từng Bài) — "Lô giao
 * BTVN theo kỹ năng": mở CÙNG LÚC N lượt làm thật (1 attempt/Bài, tái dùng nguyên startAttempt/
 * saveAnswer/submitAttempt — KHÔNG có API "gộp" nào ở BE), gộp câu hỏi cả N Bài vào 1 danh sách cuộn
 * liên tục (mỗi Bài 1 tiêu đề + đánh số RESET về 1, tái dùng QuestionBlock/GridQuestionGroup nguyên
 * vẹn từ TakeExerciseModal — chỉ khác đầu vào đến từ N Exercise thật, không phải 1 Exercise đã clone).
 * 1 nút "Nộp bài" DUY NHẤT ở cuối trang nộp CẢ N attempt cùng lúc, ra 1 kết quả gộp.
 *
 * ĐƠN GIẢN HOÁ CÓ CHỦ Ý so với TakeExerciseModal (đã xác nhận phù hợp cho BTVN, không phải giờ kiểm
 * tra proctor chặt): KHÔNG có đồng hồ đếm ngược thời gian làm bài / giám sát chống gian lận / nút "Làm
 * lại" ngay trong màn gộp — nếu 1 Bài có timeLimitMinutes, BE vẫn tự chốt khi hết giờ lúc gọi
 * saveAnswer/submitAttempt như bình thường, chỉ là không có UI đếm ngược hiển thị ở đây.
 */
export default function BatchTakeExerciseModal({ items, onClose }: BatchTakeExerciseModalProps) {
  const { t } = useTranslation("portal-exercises");
  const [subs, setSubs] = useState<SubExercise[] | null>(null);
  const [answersByQuestion, setAnswersByQuestion] = useState<Map<number, StudentAnswerResponse>>(new Map());
  const [textDraft, setTextDraft] = useState<Record<number, string>>({});
  const [listeningProgress, setListeningProgress] = useState<Map<string, ListeningPlayProgressResponse>>(new Map());
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [savingQuestionId, setSavingQuestionId] = useState<number | null>(null);
  const [confirmingSubmit, setConfirmingSubmit] = useState(false);
  const [justSubmitted, setJustSubmitted] = useState(false);
  // V152 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-25) — mirror TakeExerciseModal,
  // xem Javadoc canRevealAndClose/handleRevealAndClose bên dưới.
  const [confirmingRevealClose, setConfirmingRevealClose] = useState(false);
  const [revealClosing, setRevealClosing] = useState(false);
  const [justClosedEarly, setJustClosedEarly] = useState(false);

  const groupTitle = batchGroupTitle(t, items);

  // Guard bằng ref — mirror TakeExerciseModal (startAttempt không idempotent, React StrictMode dev
  // double-invoke effect sẽ tạo 2 attempt trùng cho 1 lần mở nếu không chặn).
  const openedRef = useRef(false);
  useEffect(() => {
    if (openedRef.current) return;
    openedRef.current = true;
    setLoading(true);
    setError(null);

    const load = async () => {
      const loaded = await Promise.all(
        items.map(async (item): Promise<SubExercise> => {
          const meta = await getExercise(item.exerciseId);
          const attempt =
            item.myLatestAttemptId == null ? await startAttempt(item.exerciseId, item.assignmentId) : await getAttempt(item.myLatestAttemptId);
          const questions = (await listExerciseQuestions(item.exerciseId)).slice().sort((a, b) => a.displayOrder - b.displayOrder);
          return { item, meta, attempt, questions };
        })
      );
      setSubs(loaded);
      const answerLists = await Promise.all(loaded.map((s) => listAnswers(s.attempt.id)));
      setAnswersByQuestion(new Map(answerLists.flat().map((a) => [a.questionId, a])));
    };

    load()
      .catch((err) => setError(friendlyApiErrorMessage(err, t("takeExercise.loadError"))))
      .finally(() => setLoading(false));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const findSub = (exerciseId: number) => subs?.find((s) => s.item.exerciseId === exerciseId);

  const handleChoiceAnswer = async (exerciseId: number, questionId: number, choiceIds: number[]) => {
    const sub = findSub(exerciseId);
    if (!sub || sub.attempt.status !== "IN_PROGRESS" || isSubOverdueLocked(sub)) return;
    setSavingQuestionId(questionId);
    setError(null);
    try {
      const res = await saveAnswer(sub.attempt.id, { questionId, selectedChoiceIds: choiceIds });
      setAnswersByQuestion((prev) => new Map(prev).set(questionId, res));
    } catch (err) {
      setError(friendlyApiErrorMessage(err, t("takeExercise.saveAnswerError")));
    } finally {
      setSavingQuestionId(null);
    }
  };

  const handleStructuredAnswer = async (exerciseId: number, questionId: number, values: string[]) => {
    const sub = findSub(exerciseId);
    if (!sub || sub.attempt.status !== "IN_PROGRESS" || isSubOverdueLocked(sub)) return;
    setSavingQuestionId(questionId);
    setError(null);
    try {
      const res = await saveAnswer(sub.attempt.id, { questionId, structuredAnswer: values });
      setAnswersByQuestion((prev) => new Map(prev).set(questionId, res));
    } catch (err) {
      setError(friendlyApiErrorMessage(err, t("takeExercise.saveAnswerError")));
    } finally {
      setSavingQuestionId(null);
    }
  };

  const handleAudioAnswer = async (exerciseId: number, questionId: number, file: File) => {
    const sub = findSub(exerciseId);
    if (!sub || sub.attempt.status !== "IN_PROGRESS" || isSubOverdueLocked(sub)) return;
    setSavingQuestionId(questionId);
    setError(null);
    try {
      const { url } = await uploadMedia(file, "EXERCISE_ANSWER_SUBMISSION");
      const res = await saveAnswer(sub.attempt.id, { questionId, audioAnswerUrl: url });
      setAnswersByQuestion((prev) => new Map(prev).set(questionId, res));
    } catch (err) {
      setError(friendlyApiErrorMessage(err, t("takeExercise.submitAudioError")));
    } finally {
      setSavingQuestionId(null);
    }
  };

  const handleTextBlur = async (exerciseId: number, questionId: number) => {
    const sub = findSub(exerciseId);
    if (!sub || sub.attempt.status !== "IN_PROGRESS" || isSubOverdueLocked(sub)) return;
    const text = textDraft[questionId];
    if (text === undefined) return;
    setSavingQuestionId(questionId);
    setError(null);
    try {
      const res = await saveAnswer(sub.attempt.id, { questionId, answerText: text });
      setAnswersByQuestion((prev) => new Map(prev).set(questionId, res));
    } catch (err) {
      setError(friendlyApiErrorMessage(err, t("takeExercise.saveAnswerError")));
    } finally {
      setSavingQuestionId(null);
    }
  };

  const handleListeningEnded = async (exerciseId: number, q: ExerciseQuestionResponse) => {
    const sub = findSub(exerciseId);
    if (!sub || sub.attempt.status !== "IN_PROGRESS" || isSubOverdueLocked(sub)) return;
    try {
      const res = await recordListeningPlay(sub.attempt.id, q.questionId);
      setListeningProgress((prev) => new Map(prev).set(q.groupKey ?? `Q${q.questionId}`, res));
    } catch {
      // Không hiện lỗi — mirror TakeExerciseModal, nghe lại vẫn hoạt động bình thường.
    }
  };

  const handleSubmit = async () => {
    if (!subs) return;
    setSubmitting(true);
    setError(null);
    try {
      const toSubmit = subs.filter((s) => s.attempt.status === "IN_PROGRESS" && !isSubOverdueLocked(s));
      const updatedAttempts = await Promise.all(toSubmit.map((s) => submitAttempt(s.attempt.id)));
      setSubs((prev) =>
        prev
          ? prev.map((s) => {
              const updated = updatedAttempts.find((u) => u.id === s.attempt.id);
              return updated ? { ...s, attempt: updated } : s;
            })
          : prev
      );
      const answerLists = await Promise.all(toSubmit.map((s) => listAnswers(s.attempt.id)));
      setAnswersByQuestion((prev) => {
        const merged = new Map(prev);
        answerLists.flat().forEach((a) => merged.set(a.questionId, a));
        return merged;
      });
      setJustSubmitted(true);
    } catch (err) {
      setError(friendlyApiErrorMessage(err, t("takeExercise.submitError")));
    } finally {
      setSubmitting(false);
    }
  };

  /**
   * V152 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-25) — mirror TakeExerciseModal:
   * cả Lô ĐÃ ĐẠT (từng Bài trong Lô đều tự đạt ngưỡng riêng, không chỉ đạt % gộp) nhưng còn lượt làm
   * lại — học sinh tự nguyện dừng NGAY cả Lô để xem đáp án, đổi lại mất quyền làm lại. Đóng ĐỒNG THỜI
   * mọi lượt trong Lô (không cho đóng lẻ từng Bài — tránh trạng thái nửa đóng nửa mở khó hiểu).
   */
  const handleRevealAndClose = async () => {
    if (!subs) return;
    setRevealClosing(true);
    setError(null);
    try {
      await Promise.all(subs.map((s) => revealAndCloseAttempt(s.attempt.id)));
      setJustClosedEarly(true);
      const answerLists = await Promise.all(subs.map((s) => listAnswers(s.attempt.id)));
      setAnswersByQuestion((prev) => {
        const merged = new Map(prev);
        answerLists.flat().forEach((a) => merged.set(a.questionId, a));
        return merged;
      });
    } catch (err) {
      setError(friendlyApiErrorMessage(err, t("takeExercise.loadError")));
    } finally {
      setRevealClosing(false);
    }
  };

  if (loading) {
    return (
      <div className="fixed inset-0 bg-white z-[100] flex items-center justify-center">
        <Loader2 size={32} className="text-teal animate-spin" />
      </div>
    );
  }

  if (!subs) {
    return (
      <div className="fixed inset-0 bg-white z-[100] flex flex-col items-center justify-center gap-4 p-6">
        <p className="text-xs font-bold text-rose-600 text-center">{error ?? t("takeExercise.loadError")}</p>
        <button onClick={onClose} className="text-xs font-extrabold text-white bg-slate-600 hover:bg-slate-700 px-5 py-2.5 rounded-xl">
          {t("takeExercise.exitButton")}
        </button>
      </div>
    );
  }

  const hasActiveAttempt = subs.some((s) => s.attempt.status === "IN_PROGRESS" && !isSubOverdueLocked(s));
  const anyOverdueLocked = subs.some(isSubOverdueLocked);
  /** V152 — xem Javadoc handleRevealAndClose. */
  const canRevealAndClose =
    !justClosedEarly &&
    subs.every((s) => s.attempt.status === "FULLY_GRADED" && s.attempt.passed === true && s.meta.maxAttempts != null && s.item.canStartNewAttempt);

  return (
    <div className="fixed inset-0 bg-white z-[100] flex flex-col">
      {submitting && (
        <div className="fixed inset-0 bg-white/90 backdrop-blur-sm flex items-center justify-center z-[125]">
          <div className="flex flex-col items-center gap-3 text-center px-6">
            <Loader2 size={36} className="text-teal animate-spin" />
            <p className="text-sm font-extrabold text-ink">{t("takeExercise.gradingOverlay.title")}</p>
            <p className="text-xs font-bold text-muted max-w-xs">{t("takeExercise.gradingOverlay.description")}</p>
          </div>
        </div>
      )}

      {justSubmitted && <BatchResultPopup subs={subs} groupTitle={groupTitle} onClose={() => setJustSubmitted(false)} />}

      {/* V152 — xác nhận trước khi TỰ NGUYỆN đóng cả Lô sớm để xem đáp án (không hoàn tác được). */}
      {confirmingRevealClose && (
        <div className="fixed inset-0 bg-black/60 flex items-center justify-center p-4 z-[120]">
          <div className="bg-white rounded-[20px] w-full max-w-sm p-6 space-y-4 text-center shadow-xl">
            <KeyRound size={36} className="text-amber-600 mx-auto" />
            <h3 className="text-base font-black text-ink">{t("takeExercise.revealAndClose.confirmTitle")}</h3>
            <p className="text-xs font-bold text-muted leading-relaxed">{t("takeExercise.revealAndClose.confirmDescription")}</p>
            <div className="flex flex-col sm:flex-row gap-2">
              <button
                onClick={() => setConfirmingRevealClose(false)}
                className="flex-1 px-4 py-2.5 bg-white hover:bg-slate-100 border border-line rounded-xl text-xs font-extrabold text-ink"
              >
                {t("takeExercise.revealAndClose.cancel")}
              </button>
              <button
                onClick={() => {
                  setConfirmingRevealClose(false);
                  handleRevealAndClose();
                }}
                disabled={revealClosing}
                className="flex-1 px-4 py-2.5 bg-amber-600 hover:bg-amber-700 text-white rounded-xl text-xs font-extrabold disabled:opacity-60"
              >
                {revealClosing ? t("takeExercise.revealAndClose.closing") : t("takeExercise.revealAndClose.confirmButton")}
              </button>
            </div>
          </div>
        </div>
      )}

      <div className="border-b border-line/60 shrink-0">
        <div className="max-w-2xl lg:max-w-3xl w-full mx-auto px-4 sm:px-6 py-3 sm:py-4 flex items-center justify-between gap-3">
          <div className="min-w-0">
            <span className="inline-block mb-1 px-2 py-0.5 rounded-full bg-teal/10 text-teal-deep text-[10px] sm:text-[11px] font-black uppercase tracking-wide">
              {t("assignments.batch.countSuffix", { count: subs.length })}
            </span>
            <h3 className="text-lg sm:text-xl lg:text-2xl font-extrabold text-ink truncate">{groupTitle}</h3>
          </div>
          <div className="flex items-center gap-2 shrink-0">
            {/* V152 — nút "Xem đáp án & đóng lượt" tường minh, chỉ hiện khi CẢ LÔ đã đạt (từng Bài tự
                đạt ngưỡng riêng) nhưng còn lượt làm lại — xem canRevealAndClose/handleRevealAndClose. */}
            {canRevealAndClose && (
              <button
                onClick={() => setConfirmingRevealClose(true)}
                disabled={revealClosing}
                className="shrink-0 flex items-center gap-1.5 px-3.5 py-2 rounded-xl bg-amber-50 hover:bg-amber-100 text-amber-800 border border-amber-200 text-xs font-extrabold transition-colors disabled:opacity-60"
              >
                <KeyRound size={14} /> {t("takeExercise.revealAndClose.button")}
              </button>
            )}
            <button
              onClick={onClose}
              aria-label={t("takeExercise.closeAriaLabel")}
              className="shrink-0 flex items-center justify-center w-9 h-9 sm:w-10 sm:h-10 rounded-full bg-rose-50 hover:bg-rose-100 text-rose-600 border border-rose-200 transition-colors"
            >
              <X size={18} className="sm:w-5 sm:h-5" />
            </button>
          </div>
        </div>
      </div>

      {/* V152 — giải thích vì sao (các) Bài đang dở trong Lô bỗng thành chỉ-xem — xem isSubOverdueLocked. */}
      {anyOverdueLocked && (
        <div className="shrink-0 px-4 sm:px-6 py-2 text-center text-xs font-extrabold bg-coral/10 text-coral border-b border-coral/20">
          {t("takeExercise.overdueLocked.banner")}
        </div>
      )}

      {error && (
        <div className="max-w-2xl lg:max-w-3xl w-full mx-auto px-4 sm:px-6 pt-3">
          <div className="text-xs font-bold text-rose-600 bg-rose-50 border border-rose-100 p-3 rounded-xl">{error}</div>
        </div>
      )}

      <div className="flex-1 overflow-y-auto">
        <div className="max-w-2xl lg:max-w-3xl w-full mx-auto px-4 sm:px-6 py-4 sm:py-6 space-y-6">
          {subs.map((sub) => {
            const sectionReadOnly = sub.attempt.status !== "IN_PROGRESS" || isSubOverdueLocked(sub);
            const attemptsRemainingBeforeAnswer =
              sub.meta.maxAttempts != null ? Math.max(0, sub.meta.maxAttempts - sub.attempt.attemptNumber) : null;
            const blocks = groupQuestionsByGroupKey(sub.questions);
            let counter = 0;
            return (
              <div key={sub.item.exerciseId} className="space-y-3 lg:space-y-4">
                <div className="flex items-center gap-2.5 pt-2 first:pt-0">
                  <span className="shrink-0 text-[11px] sm:text-xs font-black uppercase tracking-wide text-white bg-coral rounded-full px-3 py-1">
                    {sub.item.title}
                  </span>
                  <span className="h-px flex-1 bg-line" />
                </div>
                {blocks.map((block) => {
                  const startNumber = counter + 1;
                  counter += block.type === "grid" ? block.questions.length : 1;
                  return block.type === "grid" ? (
                    <GridQuestionGroup
                      key={block.groupKey}
                      block={block}
                      startNumber={startNumber}
                      answersByQuestion={answersByQuestion}
                      readOnly={sectionReadOnly}
                      savingQuestionId={savingQuestionId}
                      attemptsRemainingBeforeAnswer={attemptsRemainingBeforeAnswer}
                      onChoiceToggle={(qId, ids) => handleChoiceAnswer(sub.item.exerciseId, qId, ids)}
                      textDraft={textDraft}
                      onTextChange={(qId, v) => setTextDraft((prev) => ({ ...prev, [qId]: v }))}
                      onTextBlur={(qId) => handleTextBlur(sub.item.exerciseId, qId)}
                      onAudioUpload={(qId, file) => handleAudioAnswer(sub.item.exerciseId, qId, file)}
                      onFilePickerOpen={() => undefined}
                      attemptId={sub.attempt.id}
                      listeningProgress={listeningProgress}
                      onListeningEnded={(q) => handleListeningEnded(sub.item.exerciseId, q)}
                    />
                  ) : (
                    <QuestionBlock
                      key={block.question.id}
                      question={block.question}
                      displayNumber={startNumber}
                      answer={answersByQuestion.get(block.question.questionId)}
                      readOnly={sectionReadOnly}
                      saving={savingQuestionId === block.question.questionId}
                      attemptsRemainingBeforeAnswer={attemptsRemainingBeforeAnswer}
                      textValue={textDraft[block.question.questionId]}
                      onTextChange={(v) => setTextDraft((prev) => ({ ...prev, [block.question.questionId]: v }))}
                      onTextBlur={() => handleTextBlur(sub.item.exerciseId, block.question.questionId)}
                      onChoiceToggle={(ids) => handleChoiceAnswer(sub.item.exerciseId, block.question.questionId, ids)}
                      onStructuredAnswer={(vals) => handleStructuredAnswer(sub.item.exerciseId, block.question.questionId, vals)}
                      onAudioUpload={(file) => handleAudioAnswer(sub.item.exerciseId, block.question.questionId, file)}
                      onFilePickerOpen={() => undefined}
                      attemptId={sub.attempt.id}
                      listeningProgress={listeningProgress}
                      onListeningEnded={(q) => handleListeningEnded(sub.item.exerciseId, q)}
                    />
                  );
                })}
              </div>
            );
          })}
        </div>
      </div>

      {confirmingSubmit && (
        <div className="border-t border-line/60 shrink-0 bg-amber-50">
          <div className="max-w-2xl lg:max-w-3xl w-full mx-auto px-4 sm:px-6 py-3 flex flex-wrap items-center justify-between gap-2">
            <span className="text-xs font-bold text-amber-800">{t("takeExercise.confirmSubmit.message")}</span>
            <div className="flex gap-2 shrink-0">
              <button
                onClick={() => setConfirmingSubmit(false)}
                className="text-xs font-extrabold text-slate-600 bg-white border border-slate-200 px-4 py-2 rounded-xl"
              >
                {t("takeExercise.confirmSubmit.cancel")}
              </button>
              <button
                onClick={() => {
                  setConfirmingSubmit(false);
                  handleSubmit();
                }}
                disabled={submitting}
                className="text-xs font-extrabold text-white bg-teal px-4 py-2 rounded-xl disabled:opacity-50"
              >
                {submitting ? t("takeExercise.submitting") : t("takeExercise.confirmSubmit.confirmButton")}
              </button>
            </div>
          </div>
        </div>
      )}

      {hasActiveAttempt && !confirmingSubmit && (
        <div className="border-t border-line/60 shrink-0">
          <div className="max-w-2xl lg:max-w-3xl w-full mx-auto px-4 sm:px-6 py-3 sm:py-4 flex justify-end">
            <button
              onClick={() => setConfirmingSubmit(true)}
              disabled={submitting}
              className="text-sm sm:text-base font-extrabold text-white bg-teal px-5 sm:px-6 py-2.5 sm:py-3 rounded-xl disabled:opacity-50"
            >
              {submitting ? t("takeExercise.submitting") : t("takeExercise.submitButton")}
            </button>
          </div>
        </div>
      )}

      {!hasActiveAttempt && (
        <div className="border-t border-line/60 shrink-0">
          <div className="max-w-2xl lg:max-w-3xl w-full mx-auto px-4 sm:px-6 py-3 sm:py-4 flex justify-end">
            <button onClick={onClose} className="text-xs font-extrabold text-white bg-slate-600 hover:bg-slate-700 px-5 py-2.5 rounded-xl">
              {t("takeExercise.exitButton")}
            </button>
          </div>
        </div>
      )}
    </div>
  );
}

/** Mirror SubmitResultPopup (TakeExerciseModal.tsx) nhưng cộng dồn cả N Bài — công thức % giống hệt HomeworkProgressService#grammarProgressLabel(List, Long) ở BE (tổng điểm/tổng điểm tối đa, ngưỡng 70%). */
function BatchResultPopup({ subs, groupTitle, onClose }: { subs: SubExercise[]; groupTitle: string; onClose: () => void }) {
  const { t } = useTranslation("portal-exercises");
  const totalScore = subs.reduce((sum, s) => sum + (s.attempt.totalScore ?? 0), 0);
  const totalPoints = subs.reduce((sum, s) => sum + (s.item.exerciseTotalPoints ?? 0), 0);
  const allGraded = subs.every((s) => s.attempt.status === "FULLY_GRADED");
  const percentage = totalPoints > 0 ? Math.round((totalScore / totalPoints) * 10000) / 100 : null;
  const passed = allGraded && percentage != null ? percentage >= 70 : null;

  return (
    <div className="fixed inset-0 bg-black/60 flex items-center justify-center p-4 z-[110]">
      <div className="bg-white rounded-[20px] w-full max-w-md p-6 space-y-4 text-center shadow-xl">
        {!allGraded ? (
          <>
            <CheckCircle2 size={40} className="text-teal mx-auto" />
            <h3 className="text-base font-black text-ink">{t("assignments.batch.summary.pendingTitle", { title: groupTitle })}</h3>
            <p className="text-xs font-bold text-muted leading-relaxed">{t("takeExercise.resultPopup.submittedDescription")}</p>
          </>
        ) : passed ? (
          <>
            <PartyPopper size={40} className="text-teal mx-auto" />
            <h3 className="text-base font-black text-ink">{t("assignments.batch.summary.passedTitle", { title: groupTitle })}</h3>
            <p className="text-xs font-bold text-teal-deep leading-relaxed">
              {t("assignments.batch.summary.combinedScoreLabel", { score: totalScore, total: totalPoints, percentage: percentage ?? "—" })}
            </p>
          </>
        ) : (
          <>
            <RotateCcw size={40} className="text-coral mx-auto" />
            <h3 className="text-base font-black text-ink">{t("assignments.batch.summary.failedTitle", { title: groupTitle })}</h3>
            <p className="text-xs font-bold text-coral leading-relaxed">
              {t("assignments.batch.summary.combinedScoreLabel", { score: totalScore, total: totalPoints, percentage: percentage ?? "—" })}
            </p>
          </>
        )}

        <div className="space-y-1.5 text-left bg-slate-50 rounded-xl p-3">
          {subs.map((s) => (
            <div key={s.item.exerciseId} className="flex items-center justify-between gap-2 text-xs font-bold text-ink">
              <span className="truncate">{s.item.title}</span>
              <span
                className={`shrink-0 ${
                  s.attempt.passed === true ? "text-teal-deep" : s.attempt.passed === false ? "text-coral" : "text-muted"
                }`}
              >
                {s.attempt.percentage != null ? `${s.attempt.percentage}%` : t("assignments.attemptStatus.inProgress")}
              </span>
            </div>
          ))}
        </div>

        <button onClick={onClose} className="text-xs font-extrabold text-white bg-teal px-5 py-2.5 rounded-xl">
          {t("takeExercise.resultPopup.understood")}
        </button>
      </div>
    </div>
  );
}
