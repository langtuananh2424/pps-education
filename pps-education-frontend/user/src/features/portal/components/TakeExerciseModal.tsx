import React, { useEffect, useRef, useState } from "react";
import { CheckCircle2, Loader2, XCircle } from "lucide-react";
import { friendlyApiErrorMessage } from "@/lib/apiClient";
import {
  AssignedExerciseResponse,
  ExerciseAttemptResponse,
  ExerciseQuestionResponse,
  StudentAnswerResponse,
  getAttempt,
  listExerciseQuestions,
  listAnswers,
  saveAnswer,
  startAttempt,
  submitAttempt
} from "../api";

interface TakeExerciseModalProps {
  item: AssignedExerciseResponse;
  onClose: () => void;
  onFinished: () => void;
}

const CHOICE_TYPES = new Set(["MULTIPLE_CHOICE", "MULTIPLE_ANSWER", "TRUE_FALSE"]);

/**
 * UC-24/UC-27: màn "Làm bài" thật — mở/tiếp tục lượt làm, trả lời từng câu, nộp bài.
 * Luôn ưu tiên tiếp tục/xem lại attempt đã có (item.myLatestAttemptId) qua getAttempt —
 * chỉ startAttempt khi CHƯA có attempt nào, tránh vô tình tạo thêm lượt làm mới lúc đang
 * còn 1 lượt IN_PROGRESS (backend không tự resume, startAttempt luôn tạo attempt mới).
 */
export default function TakeExerciseModal({ item, onClose, onFinished }: TakeExerciseModalProps) {
  const [attempt, setAttempt] = useState<ExerciseAttemptResponse | null>(null);
  const [questions, setQuestions] = useState<ExerciseQuestionResponse[]>([]);
  const [answersByQuestion, setAnswersByQuestion] = useState<Map<number, StudentAnswerResponse>>(new Map());
  const [textDraft, setTextDraft] = useState<Record<number, string>>({});
  const [savingQuestionId, setSavingQuestionId] = useState<number | null>(null);
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  // Thay window.confirm() bằng popup nội tuyến khớp giao diện app — không có Modal dùng chung ở
  // app này (chỉ 1 chỗ dùng), nên làm bước xác nhận ngay trong modal đang mở thay vì Modal lồng Modal.
  const [confirmingSubmit, setConfirmingSubmit] = useState(false);

  const readOnly = attempt != null && attempt.status !== "IN_PROGRESS";

  const loadAnswers = (attemptId: number) => {
    listAnswers(attemptId)
      .then((res) => setAnswersByQuestion(new Map(res.map((a) => [a.questionId, a]))))
      .catch(() => undefined);
  };

  /**
   * Guard bằng ref (không phải chỉ dựa vào dependency array) — startAttempt là POST tạo
   * bản ghi thật (không idempotent), React 18 StrictMode tự double-invoke useEffect ở môi
   * trường dev sẽ gọi 2 lần gần như cùng lúc nếu không chặn, tạo 2 attempt trùng cho 1 lần
   * mở đề (đã tự bắt được lỗi này khi verify — xem DB exercise_attempts trùng attempt_number).
   */
  const openedRef = useRef(false);

  useEffect(() => {
    if (openedRef.current) return;
    openedRef.current = true;
    setLoading(true);
    setError(null);
    const openAttempt = item.myLatestAttemptId != null ? getAttempt(item.myLatestAttemptId) : startAttempt(item.exerciseId);
    Promise.all([openAttempt, listExerciseQuestions(item.exerciseId)])
      .then(([attemptRes, questionRes]) => {
        setAttempt(attemptRes);
        setQuestions([...questionRes].sort((a, b) => a.displayOrder - b.displayOrder));
        loadAnswers(attemptRes.id);
      })
      .catch((err) => setError(friendlyApiErrorMessage(err, "Không mở được đề để làm bài.")))
      .finally(() => setLoading(false));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [item.exerciseId, item.myLatestAttemptId]);

  const handleChoiceAnswer = async (questionId: number, choiceIds: number[]) => {
    if (!attempt || readOnly) return;
    setSavingQuestionId(questionId);
    setError(null);
    try {
      const res = await saveAnswer(attempt.id, { questionId, selectedChoiceIds: choiceIds });
      setAnswersByQuestion((prev) => new Map(prev).set(questionId, res));
    } catch (err) {
      setError(friendlyApiErrorMessage(err, "Lưu câu trả lời thất bại."));
    } finally {
      setSavingQuestionId(null);
    }
  };

  const handleTextBlur = async (questionId: number) => {
    if (!attempt || readOnly) return;
    const text = textDraft[questionId];
    if (text === undefined) return;
    setSavingQuestionId(questionId);
    setError(null);
    try {
      const res = await saveAnswer(attempt.id, { questionId, answerText: text });
      setAnswersByQuestion((prev) => new Map(prev).set(questionId, res));
    } catch (err) {
      setError(friendlyApiErrorMessage(err, "Lưu câu trả lời thất bại."));
    } finally {
      setSavingQuestionId(null);
    }
  };

  const handleSubmit = async () => {
    if (!attempt) return;
    setSubmitting(true);
    setError(null);
    try {
      const updated = await submitAttempt(attempt.id);
      setAttempt(updated);
      loadAnswers(updated.id);
      onFinished();
    } catch (err) {
      setError(friendlyApiErrorMessage(err, "Nộp bài thất bại."));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="fixed inset-0 bg-black/40 flex items-center justify-center p-4 z-50">
      <div className="bg-white rounded-[20px] w-full max-w-2xl max-h-[85vh] flex flex-col shadow-xl">
        <div className="p-6 border-b border-line/60 flex items-center justify-between gap-3 shrink-0">
          <div>
            <h3 className="text-lg font-extrabold text-ink">{item.title}</h3>
            {attempt && (
              <p className="text-[10px] text-muted font-bold mt-0.5">
                Lượt làm #{attempt.attemptNumber} ·{" "}
                {attempt.status === "IN_PROGRESS" ? "Đang làm" : attempt.status === "FULLY_GRADED" ? "Đã có điểm" : "Đã nộp — chờ chấm tự luận/nói"}
                {attempt.totalScore != null && ` · Điểm: ${attempt.totalScore}`}
              </p>
            )}
          </div>
          <button onClick={onClose} className="text-xs font-extrabold text-muted hover:text-ink px-3 py-1.5 rounded-lg hover:bg-sky-2">
            Đóng
          </button>
        </div>

        <div className="p-6 overflow-y-auto flex-1 space-y-5">
          {error && <div className="text-xs font-bold text-rose-600 bg-rose-50 border border-rose-100 p-3 rounded-xl">{error}</div>}

          {loading ? (
            <p className="text-xs text-muted font-bold flex items-center gap-2">
              <Loader2 size={14} className="animate-spin" /> Đang tải đề...
            </p>
          ) : questions.length === 0 ? (
            <p className="text-xs text-muted font-bold italic">Đề này chưa có câu hỏi nào.</p>
          ) : (
            questions.map((q) => (
              <QuestionBlock
                key={q.id}
                question={q}
                answer={answersByQuestion.get(q.questionId)}
                readOnly={readOnly}
                saving={savingQuestionId === q.questionId}
                textValue={textDraft[q.questionId]}
                onTextChange={(v) => setTextDraft((prev) => ({ ...prev, [q.questionId]: v }))}
                onTextBlur={() => handleTextBlur(q.questionId)}
                onChoiceToggle={(choiceIds) => handleChoiceAnswer(q.questionId, choiceIds)}
              />
            ))
          )}
        </div>

        {!readOnly && confirmingSubmit && (
          <div className="px-4 py-3 border-t border-line/60 bg-amber-50 flex flex-wrap items-center justify-between gap-2 shrink-0">
            <span className="text-xs font-bold text-amber-800">Nộp bài ngay? Sau khi nộp sẽ không sửa được câu trả lời nữa.</span>
            <div className="flex gap-2 shrink-0">
              <button
                onClick={() => setConfirmingSubmit(false)}
                className="text-xs font-extrabold text-slate-600 bg-white border border-slate-200 px-4 py-2 rounded-xl"
              >
                Hủy
              </button>
              <button
                onClick={() => {
                  setConfirmingSubmit(false);
                  handleSubmit();
                }}
                disabled={submitting}
                className="text-xs font-extrabold text-white bg-teal px-4 py-2 rounded-xl disabled:opacity-50"
              >
                {submitting ? "Đang nộp..." : "Xác nhận nộp"}
              </button>
            </div>
          </div>
        )}

        {!readOnly && !confirmingSubmit && (
          <div className="p-4 border-t border-line/60 flex justify-end shrink-0">
            <button
              onClick={() => setConfirmingSubmit(true)}
              disabled={submitting || loading}
              className="text-xs font-extrabold text-white bg-teal px-5 py-2.5 rounded-xl disabled:opacity-50"
            >
              {submitting ? "Đang nộp..." : "Nộp bài"}
            </button>
          </div>
        )}
      </div>
    </div>
  );
}

function QuestionBlock({
  question,
  answer,
  readOnly,
  saving,
  textValue,
  onTextChange,
  onTextBlur,
  onChoiceToggle
}: {
  question: ExerciseQuestionResponse;
  answer: StudentAnswerResponse | undefined;
  readOnly: boolean;
  saving: boolean;
  textValue: string | undefined;
  onTextChange: (v: string) => void;
  onTextBlur: () => void;
  onChoiceToggle: (choiceIds: number[]) => void;
}) {
  const isChoiceQuestion = CHOICE_TYPES.has(question.questionType) && question.choices.length > 0;
  const isFillInBlank = question.questionType === "FILL_IN_BLANK";
  const isMultiSelect = question.questionType === "MULTIPLE_ANSWER";
  const selected = new Set(answer?.selectedChoiceIds ?? []);
  const correctIds = new Set(answer?.correctChoiceIds ?? []);
  /**
   * "Đã nộp bài + showCorrectAnswers=true" — BE chỉ điền 1 trong các field này khi điều kiện đó
   * đúng (xem Javadoc StudentAnswerResponse), nên chỉ cần kiểm tra correctChoiceIds cho câu trắc
   * nghiệm là KHÔNG đủ: câu Điền từ không có choices (correctChoiceIds luôn null) nên trước đây
   * không bao giờ hiện đáp án/giải thích dù đã tự chấm xong — sửa lại dùng chung 1 điều kiện cho
   * mọi loại câu hỏi.
   */
  const showFeedback = answer != null && (answer.correctChoiceIds != null || answer.correctAnswerText != null || answer.isCorrect != null || answer.explanation != null);

  const toggleChoice = (choiceId: number) => {
    if (readOnly || saving) return;
    if (isMultiSelect) {
      const next = new Set(selected);
      if (next.has(choiceId)) next.delete(choiceId);
      else next.add(choiceId);
      onChoiceToggle([...next]);
    } else {
      onChoiceToggle([choiceId]);
    }
  };

  return (
    <div className="border border-line/60 rounded-[16px] p-4 space-y-3">
      <div className="flex items-start justify-between gap-3">
        <p className="text-sm font-bold text-ink">
          {question.displayOrder}. {question.questionContent}
        </p>
        <span className="text-[10px] text-muted font-bold shrink-0">{question.points} đ</span>
      </div>

      {isChoiceQuestion ? (
        <div className="space-y-2">
          {question.choices.map((c) => {
            const isSelected = selected.has(c.id);
            const isCorrectChoice = correctIds.has(c.id);
            let stateClass = "border-line/70 bg-sky-2 hover:bg-sky";
            if (showFeedback) {
              if (isCorrectChoice) stateClass = "border-teal bg-teal/10";
              else if (isSelected) stateClass = "border-coral bg-coral/10";
            } else if (isSelected) {
              stateClass = "border-teal bg-teal/10";
            }
            return (
              <button
                key={c.id}
                type="button"
                disabled={readOnly || saving}
                onClick={() => toggleChoice(c.id)}
                className={`w-full text-left text-xs font-bold px-3 py-2.5 rounded-xl border transition-colors flex items-center justify-between gap-2 ${stateClass} disabled:cursor-default`}
              >
                <span>
                  <span className="text-muted mr-1.5">{c.choiceLabel}.</span>
                  {c.content}
                </span>
                {showFeedback && isCorrectChoice && <CheckCircle2 size={14} className="text-teal-deep shrink-0" />}
                {showFeedback && !isCorrectChoice && isSelected && <XCircle size={14} className="text-coral shrink-0" />}
              </button>
            );
          })}
        </div>
      ) : question.questionType === "SPEAKING" ? (
        <p className="text-xs text-muted font-bold italic">Câu hỏi dạng Nói chưa hỗ trợ ghi âm trực tiếp trên Portal — giáo viên sẽ chấm theo cách khác.</p>
      ) : (
        <div className="space-y-2">
          <textarea
            value={textValue ?? answer?.answerText ?? ""}
            onChange={(e) => onTextChange(e.target.value)}
            onBlur={onTextBlur}
            disabled={readOnly || saving}
            rows={isFillInBlank ? 1 : 3}
            placeholder="Nhập câu trả lời..."
            className="w-full bg-sky-2 border border-line/70 text-xs p-3 rounded-xl focus:outline-none disabled:opacity-70"
          />
          {isFillInBlank && showFeedback && (
            <div className={`flex items-center gap-1.5 text-xs font-bold ${answer?.isCorrect ? "text-teal-deep" : "text-coral"}`}>
              {answer?.isCorrect ? <CheckCircle2 size={14} /> : <XCircle size={14} />}
              {answer?.isCorrect ? "Chính xác" : `Đáp án đúng: ${answer?.correctAnswerText ?? "—"}`}
            </div>
          )}
        </div>
      )}

      {answer?.explanation && showFeedback && <p className="text-[11px] text-muted font-bold italic border-t border-line/50 pt-2">Giải thích: {answer.explanation}</p>}
    </div>
  );
}
