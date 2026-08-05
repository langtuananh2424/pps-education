import React, { useEffect, useMemo, useRef, useState } from "react";
import { CheckCircle2, Loader2, Lock, PartyPopper, RotateCcw, ShieldAlert, XCircle } from "lucide-react";
import { friendlyApiErrorMessage } from "@/lib/apiClient";
import {
  AssignedExerciseResponse,
  ExerciseAttemptResponse,
  ExerciseMetaResponse,
  ExerciseQuestionResponse,
  StudentAnswerResponse,
  getAttempt,
  getExercise,
  listExerciseQuestions,
  listAnswers,
  recordIntegrityEvents,
  saveAnswer,
  startAttempt,
  submitAttempt,
  uploadMedia
} from "../api";
import { useIntegrityMonitor } from "../hooks/useIntegrityMonitor";

interface TakeExerciseModalProps {
  item: AssignedExerciseResponse;
  onClose: () => void;
  onFinished: () => void;
}

const CHOICE_TYPES = new Set(["MULTIPLE_CHOICE", "MULTIPLE_ANSWER", "TRUE_FALSE"]);

/**
 * V78 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-04) — dạng "Đọc hiểu — lưới": nhiều
 * câu MULTIPLE_CHOICE liên tiếp cùng groupKey gộp hiển thị chung 1 referencePassage + 1 bảng câu hỏi,
 * thay vì lặp lại đoạn văn ở mỗi câu. Chỉ gộp các câu LIÊN TIẾP nhau (đúng thứ tự displayOrder).
 */
type RenderBlock =
  | { type: "single"; question: ExerciseQuestionResponse }
  | { type: "grid"; groupKey: string; referencePassage: string | null; questions: ExerciseQuestionResponse[] };

function groupQuestionsByGroupKey(questions: ExerciseQuestionResponse[]): RenderBlock[] {
  const blocks: RenderBlock[] = [];
  for (const q of questions) {
    const last = blocks[blocks.length - 1];
    if (q.groupKey && last && last.type === "grid" && last.groupKey === q.groupKey) {
      last.questions.push(q);
      continue;
    }
    if (q.groupKey) {
      blocks.push({ type: "grid", groupKey: q.groupKey, referencePassage: q.referencePassage, questions: [q] });
    } else {
      blocks.push({ type: "single", question: q });
    }
  }
  return blocks;
}

/**
 * UC-24/A4, UC-27/A2: đáp án đúng (correctChoiceIds/correctAnswerText/correctStructuredContent) đã
 * thật sự lộ ra chưa — dùng chung cho mọi loại câu hỏi thay vì kiểm tra riêng lẻ isCorrect (field đó
 * luôn có giá trị ngay khi tự chấm xong, không phụ thuộc gate làm lại). Câu tự luận/Nói (ESSAY/
 * SPEAKING) không có 3 field trên, dựa vào explanation (luôn hiện khi revealAnswer=true, không bị
 * gate làm lại — xem ExerciseAttemptService.toResponse).
 */
function isAnswerRevealed(answer: StudentAnswerResponse): boolean {
  return (
    answer.correctChoiceIds != null ||
    answer.correctAnswerText != null ||
    answer.correctStructuredContent != null ||
    (!answer.isAutoGradable && answer.explanation != null)
  );
}

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
  // Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-06 — bài bị hệ thống dừng ép do vượt
  // ngưỡng vi phạm (khác banner "justViolated" nhỏ — đây là cảnh báo mạnh, chặn tương tác cho tới
  // khi học sinh bấm "Đã hiểu"). Kết quả đã được ghi nhận ở server ngay lúc dừng — chỉ cần tải lại
  // attempt để cập nhật trạng thái readOnly, học sinh làm lại qua nút "Làm lại" ở màn danh sách đề.
  const [stoppedByViolation, setStoppedByViolation] = useState(false);
  // Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-06 — popup kết quả hiện đúng 1 lần
  // ngay sau khi bấm "Nộp bài" (không hiện lại khi mở xem lại 1 lượt đã nộp từ trước — đó là lý do
  // tách riêng justSubmitted thay vì suy từ attempt.status). exerciseMeta cho biết còn bao nhiêu
  // lượt làm lại (allowRetake/maxAttempts) để hiện đúng câu "bạn còn N lần để làm lại".
  const [justSubmitted, setJustSubmitted] = useState(false);
  const [exerciseMeta, setExerciseMeta] = useState<ExerciseMetaResponse | null>(null);

  const readOnly = attempt != null && attempt.status !== "IN_PROGRESS";
  /**
   * UC-24/A4, UC-27/A2: đề có giới hạn số lần làm lại (exerciseMeta.maxAttempts khác NULL) — số lượt
   * CÒN LẠI trước khi đáp án được mở khóa (mirror công thức BE: revealAnswer khi attemptNumber >=
   * maxAttempts). null = đề không giới hạn số lần làm lại HOẶC exerciseMeta chưa tải xong — không
   * hiện thông báo khóa đáp án cho tới khi có dữ liệu chắc chắn.
   */
  const attemptsRemainingBeforeAnswer =
    exerciseMeta?.maxAttempts != null && attempt != null ? Math.max(0, exerciseMeta.maxAttempts - attempt.attemptNumber) : null;

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
    // Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-05 — lượt gần nhất đã chấm xong
    // (FULLY_GRADED) nhưng dưới ngưỡng đạt (myLatestPassed=false) thì mở LƯỢT MỚI (startAttempt,
    // attemptNumber+1) thay vì xem lại lượt cũ đã chấm — khác các trạng thái khác (IN_PROGRESS/
    // AUTO_GRADED/đã đạt) vẫn resume/xem lại lượt hiện có như cũ.
    const needsRetake = item.myLatestAttemptStatus === "FULLY_GRADED" && item.myLatestPassed === false;
    const openAttempt =
      item.myLatestAttemptId != null && !needsRetake ? getAttempt(item.myLatestAttemptId) : startAttempt(item.exerciseId);
    Promise.all([openAttempt, listExerciseQuestions(item.exerciseId)])
      .then(([attemptRes, questionRes]) => {
        setAttempt(attemptRes);
        setQuestions([...questionRes].sort((a, b) => a.displayOrder - b.displayOrder));
        loadAnswers(attemptRes.id);
      })
      .catch((err) => setError(friendlyApiErrorMessage(err, "Không mở được đề để làm bài.")))
      .finally(() => setLoading(false));
    // Không chặn màn làm bài nếu lỗi — dùng cho cả popup kết quả (allowRetake/passThresholdPercent)
    // LẪN khóa đáp án theo số lần làm lại (maxAttempts, UC-24/A4-UC-27/A2) — 1 API duy nhất, thay vì
    // gọi thêm getExerciseAttemptLimit() riêng (2 endpoint từng đọc cùng 1 field maxAttempts).
    getExercise(item.exerciseId).then(setExerciseMeta).catch(() => undefined);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [item.exerciseId, item.myLatestAttemptId]);

  // Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-07-31 — xem Javadoc useIntegrityMonitor.
  const attemptId = attempt?.id;
  const { violationCount, isMonitoringActive, justViolated } = useIntegrityMonitor({
    enabled: !readOnly && attemptId != null && !stoppedByViolation,
    autoFlushIntervalMs: 20000,
    onFlush: (events) => {
      if (attemptId == null) return;
      recordIntegrityEvents(attemptId, { events })
        .then((res) => {
          if (res.attemptStopped) {
            setStoppedByViolation(true);
            // Không gọi onFinished() ở đây — cùng lý do đã sửa ở handleSubmit (xem comment ở đó):
            // dời sang lúc học sinh bấm "Đã hiểu" đóng popup, tránh cha reload giữa chừng giật mất popup.
            getAttempt(attemptId).then((updated) => setAttempt(updated)).catch(() => undefined);
          }
        })
        .catch(() => undefined);
    }
  });

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

  /** V78 — WORD_BANK/SENTENCE_BUILDING: lưu ngay khi học sinh chọn đủ (không chờ blur như văn bản tự do). */
  const handleStructuredAnswer = async (questionId: number, values: string[]) => {
    if (!attempt || readOnly) return;
    setSavingQuestionId(questionId);
    setError(null);
    try {
      const res = await saveAnswer(attempt.id, { questionId, structuredAnswer: values });
      setAnswersByQuestion((prev) => new Map(prev).set(questionId, res));
    } catch (err) {
      setError(friendlyApiErrorMessage(err, "Lưu câu trả lời thất bại."));
    } finally {
      setSavingQuestionId(null);
    }
  };

  /**
   * V78 — SPEAKING (Speaking oral gốc lẫn "Nghe & nộp audio" mới): học sinh upload file audio ghi âm
   * câu trả lời, lưu URL qua saveAnswer.audioAnswerUrl — vá gap cũ (Portal trước đây không có UI nộp
   * audio thật cho SPEAKING dù backend đã hỗ trợ).
   */
  const handleAudioAnswer = async (questionId: number, file: File) => {
    if (!attempt || readOnly) return;
    setSavingQuestionId(questionId);
    setError(null);
    try {
      const { url } = await uploadMedia(file, "EXERCISE_ANSWER_SUBMISSION");
      const res = await saveAnswer(attempt.id, { questionId, audioAnswerUrl: url });
      setAnswersByQuestion((prev) => new Map(prev).set(questionId, res));
    } catch (err) {
      setError(friendlyApiErrorMessage(err, "Nộp audio thất bại."));
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
      // Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-06 — KHÔNG gọi onFinished() ngay ở
      // đây: AssignmentsTab.load() (cha) set loading=true, khiến toàn bộ tab (kể cả modal đang mở)
      // bị unmount ngay lúc render "Đang tải..." — popup kết quả vừa hiện bị giật mất trước khi học
      // sinh kịp đọc. Dời sang lúc bấm "Đã hiểu" đóng popup (xem onClose của SubmitResultPopup).
      setJustSubmitted(true);
    } catch (err) {
      setError(friendlyApiErrorMessage(err, "Nộp bài thất bại."));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="fixed inset-0 bg-black/40 flex items-center justify-center p-4 z-50">
      {/* Popup cảnh báo tức thời — hiện ngay lúc phát hiện vi phạm mới, tự mờ dần sau ~3.5s, khác banner
          tĩnh bên dưới (chỉ đổi số đếm, học sinh dễ không để ý). Neo "fixed" ở gốc modal để luôn nổi
          trên cùng bất kể đang cuộn tới đâu bên trong nội dung đề. */}
      {justViolated && !stoppedByViolation && (
        <div
          key={violationCount}
          role="alert"
          className="fixed top-6 left-1/2 z-[60] flex items-center gap-2 bg-rose-600 text-white pl-3 pr-4 py-2.5 rounded-2xl shadow-xl animate-alert-pop"
        >
          <ShieldAlert size={18} className="shrink-0" />
          <span className="text-xs font-black">Đã ghi nhận: bạn vừa thoát ra ngoài khi đang làm bài!</span>
        </div>
      )}

      {/* Cảnh báo mạnh — chặn tương tác, khác hẳn toast nhỏ ở trên — hiện đúng 1 lần khi vừa bị dừng ép. */}
      {stoppedByViolation && (
        <div className="fixed inset-0 bg-black/60 flex items-center justify-center p-4 z-[70]">
          <div className="bg-white rounded-[20px] w-full max-w-md p-6 space-y-4 text-center shadow-xl">
            <ShieldAlert size={40} className="text-rose-600 mx-auto" />
            <h3 className="text-base font-black text-ink">Bài làm đã bị dừng</h3>
            <p className="text-xs font-bold text-muted leading-relaxed">
              Bạn đã thoát ra ngoài quá số lần cho phép khi đang làm bài. Kết quả đã được ghi nhận theo phần đã trả lời.
              Bạn có thể làm lại từ màn danh sách đề.
            </p>
            <button
              onClick={() => {
                setStoppedByViolation(false);
                onClose();
              }}
              className="text-xs font-extrabold text-white bg-teal px-5 py-2.5 rounded-xl"
            >
              Đã hiểu
            </button>
          </div>
        </div>
      )}

      {/* Popup kết quả sau khi nộp bài — bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-06.
          Chỉ hiện đúng 1 lần ngay sau khi bấm "Nộp bài" (justSubmitted), không hiện lại khi mở xem
          lại 1 lượt đã nộp từ trước. */}
      {justSubmitted && attempt && (
        <SubmitResultPopup
          attempt={attempt}
          exerciseTitle={item.title}
          exerciseMeta={exerciseMeta}
          onClose={() => {
            setJustSubmitted(false);
            onFinished();
          }}
        />
      )}

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

        {isMonitoringActive && (
          <div className="px-6 py-2 bg-amber-50 border-b border-amber-100 flex items-center gap-1.5 shrink-0">
            <ShieldAlert size={13} className="text-amber-600 shrink-0" />
            <span className="text-[11px] font-bold text-amber-800">
              Đang giám sát quá trình làm bài — thoát ra ngoài (đổi tab/thu nhỏ) sẽ được ghi nhận.
              {violationCount > 0 && ` Đã ghi nhận ${violationCount} lần.`}
            </span>
          </div>
        )}

        <div className="p-6 overflow-y-auto flex-1 space-y-5">
          {error && <div className="text-xs font-bold text-rose-600 bg-rose-50 border border-rose-100 p-3 rounded-xl">{error}</div>}

          {loading ? (
            <p className="text-xs text-muted font-bold flex items-center gap-2">
              <Loader2 size={14} className="animate-spin" /> Đang tải đề...
            </p>
          ) : questions.length === 0 ? (
            <p className="text-xs text-muted font-bold italic">Đề này chưa có câu hỏi nào.</p>
          ) : (
            groupQuestionsByGroupKey(questions).map((block) =>
              block.type === "grid" ? (
                <GridQuestionGroup
                  key={block.groupKey}
                  block={block}
                  answersByQuestion={answersByQuestion}
                  readOnly={readOnly}
                  savingQuestionId={savingQuestionId}
                  attemptsRemainingBeforeAnswer={attemptsRemainingBeforeAnswer}
                  onChoiceToggle={handleChoiceAnswer}
                />
              ) : (
                <QuestionBlock
                  key={block.question.id}
                  question={block.question}
                  answer={answersByQuestion.get(block.question.questionId)}
                  readOnly={readOnly}
                  saving={savingQuestionId === block.question.questionId}
                  attemptsRemainingBeforeAnswer={attemptsRemainingBeforeAnswer}
                  textValue={textDraft[block.question.questionId]}
                  onTextChange={(v) => setTextDraft((prev) => ({ ...prev, [block.question.questionId]: v }))}
                  onTextBlur={() => handleTextBlur(block.question.questionId)}
                  onChoiceToggle={(choiceIds) => handleChoiceAnswer(block.question.questionId, choiceIds)}
                  onStructuredAnswer={(values) => handleStructuredAnswer(block.question.questionId, values)}
                  onAudioUpload={(file) => handleAudioAnswer(block.question.questionId, file)}
                />
              )
            )
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

/**
 * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-06 — popup kết quả ngay sau khi nộp bài
 * (mirror tinh thần popup "Bài làm đã bị dừng" ở trên). 3 nhánh nội dung:
 * - Chưa chấm xong hết (còn tự luận/nói chờ GV chấm, status AUTO_GRADED): chỉ báo đã nộp, chưa có
 *   kết luận đạt/không đạt.
 * - FULLY_GRADED + đạt (passed=true): chúc mừng.
 * - FULLY_GRADED + chưa đạt (passed=false): báo % + ngưỡng cần đạt + số lượt còn lại để làm lại
 *   (suy từ exerciseMeta.allowRetake/maxAttempts — null maxAttempts = không giới hạn lượt).
 */
function SubmitResultPopup({
  attempt,
  exerciseTitle,
  exerciseMeta,
  onClose
}: {
  attempt: ExerciseAttemptResponse;
  exerciseTitle: string;
  exerciseMeta: ExerciseMetaResponse | null;
  onClose: () => void;
}) {
  const fullyGraded = attempt.status === "FULLY_GRADED";
  const passed = fullyGraded ? attempt.passed : null;

  let remainingText: string | null = null;
  if (fullyGraded && passed === false && exerciseMeta) {
    if (!exerciseMeta.allowRetake) {
      remainingText = "Đề này không cho làm lại — đây là kết quả cuối cùng.";
    } else if (exerciseMeta.maxAttempts == null) {
      remainingText = "Bạn có thể làm lại bài tập này khi sẵn sàng.";
    } else {
      const remaining = Math.max(0, exerciseMeta.maxAttempts - attempt.attemptNumber);
      remainingText =
        remaining > 0
          ? `Bạn còn ${remaining} lần để làm lại bài tập này.`
          : "Bạn đã dùng hết số lần làm lại cho phép của bài tập này.";
    }
  }

  return (
    <div className="fixed inset-0 bg-black/60 flex items-center justify-center p-4 z-[70]">
      <div className="bg-white rounded-[20px] w-full max-w-md p-6 space-y-4 text-center shadow-xl">
        {!fullyGraded ? (
          <>
            <CheckCircle2 size={40} className="text-teal mx-auto" />
            <h3 className="text-base font-black text-ink">Đã nộp bài "{exerciseTitle}"!</h3>
            <p className="text-xs font-bold text-muted leading-relaxed">
              Phần trắc nghiệm đã được chấm tự động. Bài còn câu tự luận/nói cần giáo viên chấm tay — điểm cuối cùng sẽ có sau.
            </p>
          </>
        ) : passed ? (
          <>
            <PartyPopper size={40} className="text-teal mx-auto" />
            <h3 className="text-base font-black text-ink">Chúc mừng bạn đã hoàn thành bài "{exerciseTitle}"!</h3>
            <p className="text-xs font-bold text-teal-deep leading-relaxed">
              Kết quả: {attempt.percentage ?? "—"}% — Đạt yêu cầu.
            </p>
          </>
        ) : (
          <>
            <RotateCcw size={40} className="text-coral mx-auto" />
            <h3 className="text-base font-black text-ink">Bạn đã hoàn thành bài "{exerciseTitle}"</h3>
            <p className="text-xs font-bold text-coral leading-relaxed">
              Kết quả: {attempt.percentage ?? "—"}% — Chưa đạt yêu cầu
              {exerciseMeta ? ` (cần ≥${exerciseMeta.passThresholdPercent}%)` : ""}, cần làm lại bài tập.
            </p>
            {remainingText && <p className="text-xs font-bold text-muted leading-relaxed">{remainingText}</p>}
          </>
        )}
        <button onClick={onClose} className="text-xs font-extrabold text-white bg-teal px-5 py-2.5 rounded-xl">
          Đã hiểu
        </button>
      </div>
    </div>
  );
}

function QuestionBlock({
  question,
  answer,
  readOnly,
  saving,
  attemptsRemainingBeforeAnswer,
  textValue,
  onTextChange,
  onTextBlur,
  onChoiceToggle,
  onStructuredAnswer,
  onAudioUpload
}: {
  question: ExerciseQuestionResponse;
  answer: StudentAnswerResponse | undefined;
  readOnly: boolean;
  saving: boolean;
  attemptsRemainingBeforeAnswer: number | null;
  textValue: string | undefined;
  onTextChange: (v: string) => void;
  onTextBlur: () => void;
  onChoiceToggle: (choiceIds: number[]) => void;
  onStructuredAnswer: (values: string[]) => void;
  onAudioUpload: (file: File) => void;
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
  const showFeedback = answer != null && isAnswerRevealed(answer);
  // UC-24/A4, UC-27/A2: câu tự chấm đã có kết quả (isCorrect) nhưng đáp án chưa lộ — do đề còn
  // giới hạn số lần làm lại và đây chưa phải lượt cuối cùng. Không áp dụng cho ESSAY/SPEAKING.
  const answerLockedByRetake = answer != null && answer.isAutoGradable && answer.isCorrect != null && !showFeedback;

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
        <div className="space-y-2">
          {question.skill === "LISTENING" && question.audioUrl && (
            <div className="space-y-1">
              <p className="text-[10px] text-muted font-bold uppercase">Audio bài nghe</p>
              {/* eslint-disable-next-line jsx-a11y/media-has-caption */}
              <audio controls src={question.audioUrl} className="w-full" />
            </div>
          )}
          <div className="space-y-1">
            <p className="text-[10px] text-muted font-bold uppercase">Ghi âm câu trả lời của bạn</p>
            <input
              type="file"
              accept="audio/*"
              disabled={readOnly || saving}
              onChange={(e) => {
                const file = e.target.files?.[0];
                if (file) onAudioUpload(file);
                e.target.value = "";
              }}
              className="text-xs font-bold text-ink file:mr-2 file:px-3 file:py-1.5 file:rounded-lg file:border-0 file:bg-teal file:text-white file:text-xs file:font-extrabold disabled:opacity-70"
            />
            {answer?.audioAnswerUrl && (
              // eslint-disable-next-line jsx-a11y/media-has-caption
              <audio controls src={answer.audioAnswerUrl} className="w-full mt-1" />
            )}
          </div>
          <p className="text-[10px] text-muted italic">Câu này sẽ được giáo viên chấm tay sau khi nộp bài.</p>
        </div>
      ) : question.questionType === "WORD_BANK" && question.structuredContent?.blanks ? (
        <WordBankBlock
          content={question.questionContent}
          wordPool={question.structuredContent.blanks}
          initialAnswer={answer?.structuredAnswer ?? undefined}
          readOnly={readOnly}
          saving={saving}
          onChange={onStructuredAnswer}
        />
      ) : question.questionType === "SENTENCE_BUILDING" && question.structuredContent?.chunks ? (
        <SentenceBuildingBlock chunkPool={question.structuredContent.chunks} readOnly={readOnly} saving={saving} onChange={onStructuredAnswer} />
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

      {(question.questionType === "WORD_BANK" || question.questionType === "SENTENCE_BUILDING") && showFeedback && (
        <div className={`flex items-center gap-1.5 text-xs font-bold ${answer?.isCorrect ? "text-teal-deep" : "text-coral"}`}>
          {answer?.isCorrect ? <CheckCircle2 size={14} /> : <XCircle size={14} />}
          {answer?.isCorrect
            ? "Chính xác"
            : `Đáp án đúng: ${(question.questionType === "WORD_BANK" ? answer?.correctStructuredContent?.blanks : answer?.correctStructuredContent?.chunks)?.join(" — ") ?? "—"}`}
        </div>
      )}

      {answer?.explanation && showFeedback && <p className="text-[11px] text-muted font-bold italic border-t border-line/50 pt-2">Giải thích: {answer.explanation}</p>}

      {answerLockedByRetake && <LockedAnswerBanner attemptsRemainingBeforeAnswer={attemptsRemainingBeforeAnswer} />}
    </div>
  );
}

/** UC-24/A4, UC-27/A2: nút "Xem đáp án" luôn hiện — chỉ khóa (disabled) khi backend chưa lộ đáp án vì còn lượt làm lại. */
function LockedAnswerBanner({ attemptsRemainingBeforeAnswer }: { attemptsRemainingBeforeAnswer: number | null }) {
  return (
    <div className="flex items-center justify-between gap-2 text-xs font-bold text-amber-800 bg-amber-50 border border-amber-100 rounded-xl px-3 py-2">
      <span>
        {attemptsRemainingBeforeAnswer != null && attemptsRemainingBeforeAnswer > 0
          ? `Còn ${attemptsRemainingBeforeAnswer} lượt làm — hoàn thành hết lượt để xem đáp án.`
          : "Đáp án sẽ mở khóa ở lượt làm cuối cùng."}
      </span>
      <button type="button" disabled className="flex items-center gap-1 text-[11px] font-extrabold text-amber-700 bg-amber-100 px-2.5 py-1 rounded-lg opacity-70 cursor-not-allowed">
        <Lock size={11} /> Xem đáp án
      </button>
    </div>
  );
}

/** V78 — Điền từ - Hộp từ vựng: content chứa marker "___" theo đúng số chỗ trống, mỗi dropdown liệt kê từ CÒN LẠI (chưa chọn ở chỗ trống khác). */
function WordBankBlock({
  content,
  wordPool,
  initialAnswer,
  readOnly,
  saving,
  onChange
}: {
  content: string;
  wordPool: string[];
  initialAnswer: string[] | undefined;
  readOnly: boolean;
  saving: boolean;
  onChange: (values: string[]) => void;
}) {
  const parts = content.split("___");
  const blankCount = parts.length - 1;
  const [selections, setSelections] = useState<string[]>(
    initialAnswer && initialAnswer.length === blankCount ? initialAnswer : new Array(blankCount).fill("")
  );

  const handleSelect = (idx: number, value: string) => {
    const next = selections.map((s, i) => (i === idx ? value : s));
    setSelections(next);
    if (next.every((s) => s)) onChange(next);
  };

  return (
    <div className="flex flex-wrap items-center gap-x-1.5 gap-y-2 text-sm font-bold text-ink leading-8">
      {parts.map((part, idx) => (
        <React.Fragment key={idx}>
          {part && <span>{part}</span>}
          {idx < blankCount && (
            <select
              value={selections[idx]}
              disabled={readOnly || saving}
              onChange={(e) => handleSelect(idx, e.target.value)}
              className="bg-sky-2 border border-line/70 text-xs font-bold px-2 py-1.5 rounded-lg focus:outline-none disabled:opacity-70"
            >
              <option value="">— chọn —</option>
              {wordPool
                .filter((w) => w === selections[idx] || !selections.includes(w))
                .map((w, wIdx) => (
                  <option key={`${w}-${wIdx}`} value={w}>
                    {w}
                  </option>
                ))}
            </select>
          )}
        </React.Fragment>
      ))}
    </div>
  );
}

/**
 * V78 — Sắp xếp câu: học sinh CHẠM từng khối theo thứ tự muốn chọn (thay cho kéo-thả vật lý — ổn định
 * hơn trên thiết bị cảm ứng, không cần thư viện DnD, cùng kết quả tự động chấm chính xác thứ tự).
 * Dùng index vào mảng đã xáo trộn (không dùng giá trị chuỗi) để xử lý đúng cả khi có khối trùng nội dung.
 * Giới hạn đã biết: không khôi phục lại lựa chọn cũ khi mở lại đề (initialAnswer không dùng để seed lại
 * usedIndices vì không có cách map ngược đáng tin cậy khi có khối trùng nội dung) — học sinh chọn lại
 * từ đầu, submit sẽ ghi đè đúng answer mới.
 */
function SentenceBuildingBlock({
  chunkPool,
  readOnly,
  saving,
  onChange
}: {
  chunkPool: string[];
  readOnly: boolean;
  saving: boolean;
  onChange: (values: string[]) => void;
}) {
  const shuffled = useMemo(() => {
    const arr = chunkPool.map((text, idx) => ({ text, idx }));
    for (let i = arr.length - 1; i > 0; i--) {
      const j = Math.floor(Math.random() * (i + 1));
      [arr[i], arr[j]] = [arr[j], arr[i]];
    }
    return arr;
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);
  const [usedIndices, setUsedIndices] = useState<number[]>([]);

  const built = usedIndices.map((i) => shuffled.find((s) => s.idx === i)?.text ?? "");
  const available = shuffled.filter((s) => !usedIndices.includes(s.idx));

  const addChunk = (idx: number) => {
    if (readOnly || saving) return;
    const next = [...usedIndices, idx];
    setUsedIndices(next);
    if (next.length === chunkPool.length) {
      onChange(next.map((i) => shuffled.find((s) => s.idx === i)?.text ?? ""));
    }
  };
  const removeChunk = (position: number) => {
    if (readOnly || saving) return;
    setUsedIndices((prev) => prev.filter((_, i) => i !== position));
  };

  return (
    <div className="space-y-2">
      <div className="flex flex-wrap gap-1.5 min-h-[38px] p-2 bg-sky-2 rounded-xl border border-dashed border-line/70">
        {built.length === 0 && <span className="text-[11px] text-muted italic px-1">Chạm các khối bên dưới theo đúng thứ tự để dựng câu...</span>}
        {built.map((text, position) => (
          <button
            key={position}
            type="button"
            disabled={readOnly || saving}
            onClick={() => removeChunk(position)}
            className="px-2.5 py-1 rounded-lg bg-teal/10 border border-teal text-xs font-bold text-teal-deep disabled:opacity-70"
          >
            {text}
          </button>
        ))}
      </div>
      <div className="flex flex-wrap gap-1.5">
        {available.map((s) => (
          <button
            key={s.idx}
            type="button"
            disabled={readOnly || saving}
            onClick={() => addChunk(s.idx)}
            className="px-2.5 py-1 rounded-lg bg-white border border-line/70 text-xs font-bold text-ink hover:bg-sky disabled:opacity-70"
          >
            {s.text}
          </button>
        ))}
      </div>
    </div>
  );
}

/** V78 — "Đọc hiểu — lưới": 1 đoạn văn dùng chung + bảng N câu × 3 cột đáp án (radio từng dòng). */
function GridQuestionGroup({
  block,
  answersByQuestion,
  readOnly,
  savingQuestionId,
  attemptsRemainingBeforeAnswer,
  onChoiceToggle
}: {
  block: Extract<RenderBlock, { type: "grid" }>;
  answersByQuestion: Map<number, StudentAnswerResponse>;
  readOnly: boolean;
  savingQuestionId: number | null;
  attemptsRemainingBeforeAnswer: number | null;
  onChoiceToggle: (questionId: number, choiceIds: number[]) => void;
}) {
  // UC-24/A4, UC-27/A2: mọi câu trong 1 nhóm lưới đều thuộc cùng 1 lượt làm — chỉ cần 1 banner khóa chung.
  const anyLockedByRetake = block.questions.some((q) => {
    const a = answersByQuestion.get(q.questionId);
    return a != null && a.isAutoGradable && a.isCorrect != null && !isAnswerRevealed(a);
  });
  return (
    <div className="border border-line/60 rounded-[16px] p-4 space-y-3">
      {block.referencePassage && <p className="text-xs text-ink whitespace-pre-wrap bg-sky-2 rounded-xl p-3">{block.referencePassage}</p>}
      <div className="divide-y divide-line/50">
        {block.questions.map((q) => {
          const answer = answersByQuestion.get(q.questionId);
          const selected = new Set(answer?.selectedChoiceIds ?? []);
          const correctIds = new Set(answer?.correctChoiceIds ?? []);
          const showFeedback = answer != null && isAnswerRevealed(answer);
          const saving = savingQuestionId === q.questionId;
          return (
            <div key={q.id} className="py-2.5 flex items-center gap-2 flex-wrap">
              <span className="text-xs font-bold text-ink flex-1 min-w-[160px]">
                {q.displayOrder}. {q.questionContent}
              </span>
              <div className="flex gap-1.5 shrink-0">
                {q.choices.map((c) => {
                  const isSelected = selected.has(c.id);
                  const isCorrectChoice = correctIds.has(c.id);
                  let cls = "border-line/70 bg-sky-2";
                  if (showFeedback) {
                    if (isCorrectChoice) cls = "border-teal bg-teal/10 text-teal-deep";
                    else if (isSelected) cls = "border-coral bg-coral/10 text-coral";
                  } else if (isSelected) {
                    cls = "border-teal bg-teal/10 text-teal-deep";
                  }
                  return (
                    <button
                      key={c.id}
                      type="button"
                      disabled={readOnly || saving}
                      onClick={() => onChoiceToggle(q.questionId, [c.id])}
                      className={`w-8 h-8 rounded-lg border text-[11px] font-bold transition-colors disabled:cursor-default ${cls}`}
                    >
                      {c.choiceLabel}
                    </button>
                  );
                })}
              </div>
            </div>
          );
        })}
      </div>

      {anyLockedByRetake && <LockedAnswerBanner attemptsRemainingBeforeAnswer={attemptsRemainingBeforeAnswer} />}
    </div>
  );
}
