import React, { useEffect, useLayoutEffect, useMemo, useRef, useState } from "react";
import { createPortal } from "react-dom";
import { useTranslation } from "react-i18next";
import { CheckCircle2, HelpCircle, Loader2, Lock, PartyPopper, RotateCcw, ShieldAlert, X, XCircle } from "lucide-react";
import { friendlyApiErrorMessage } from "@/lib/apiClient";
import {
  AssignedExerciseResponse,
  ExerciseAttemptResponse,
  ExerciseMetaResponse,
  ExerciseQuestionChoiceResponse,
  ExerciseQuestionResponse,
  ListeningHintResponse,
  ListeningPlayProgressResponse,
  StudentAnswerResponse,
  getAttempt,
  getExercise,
  getListeningHint,
  listExerciseQuestions,
  listAnswers,
  recordIntegrityEvents,
  recordListeningPlay,
  saveAnswer,
  startAttempt,
  submitAttempt,
  uploadMedia
} from "../api";
import { useIntegrityMonitor } from "../hooks/useIntegrityMonitor";
import MonitoringBadge from "./MonitoringBadge";
import { useCountdown, formatRemaining } from "@/components/ui/useCountdown";

interface TakeExerciseModalProps {
  item: AssignedExerciseResponse;
  onClose: () => void;
  onFinished: () => void;
}

const CHOICE_TYPES = new Set(["MULTIPLE_CHOICE", "MULTIPLE_ANSWER", "TRUE_FALSE"]);

/** Khớp ListeningHintService#listeningKeyOf (BE) — nhóm "1 audio nhiều câu" dùng chung groupKey, câu đơn dùng key riêng theo chính nó. */
function listeningKeyOf(q: ExerciseQuestionResponse): string {
  return q.groupKey ?? `Q${q.questionId}`;
}

/**
 * V78 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-04) — dạng "Đọc hiểu — lưới": nhiều
 * câu MULTIPLE_CHOICE liên tiếp cùng groupKey gộp hiển thị chung 1 referencePassage + 1 bảng câu hỏi,
 * thay vì lặp lại đoạn văn ở mỗi câu. Chỉ gộp các câu LIÊN TIẾP nhau (đúng thứ tự displayOrder).
 */
type RenderBlock =
  | { type: "single"; question: ExerciseQuestionResponse }
  | { type: "grid"; groupKey: string; referencePassage: string | null; audioUrl: string | null; questions: ExerciseQuestionResponse[] };

function groupQuestionsByGroupKey(questions: ExerciseQuestionResponse[]): RenderBlock[] {
  const blocks: RenderBlock[] = [];
  for (const q of questions) {
    const last = blocks[blocks.length - 1];
    if (q.groupKey && last && last.type === "grid" && last.groupKey === q.groupKey) {
      last.questions.push(q);
      continue;
    }
    if (q.groupKey) {
      // Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-06 — nhóm "1 audio nhiều câu" (GV
      // nước ngoài, xem ListeningGroupBuilder) cùng dùng chung audioUrl như referencePassage: chỉ cần
      // lấy từ câu hỏi đầu tiên của nhóm.
      blocks.push({ type: "grid", groupKey: q.groupKey, referencePassage: q.referencePassage, audioUrl: q.audioUrl, questions: [q] });
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
  const { t } = useTranslation("portal-exercises");
  const [attempt, setAttempt] = useState<ExerciseAttemptResponse | null>(null);
  const [questions, setQuestions] = useState<ExerciseQuestionResponse[]>([]);
  const [answersByQuestion, setAnswersByQuestion] = useState<Map<number, StudentAnswerResponse>>(new Map());
  const [textDraft, setTextDraft] = useState<Record<number, string>>({});
  const [savingQuestionId, setSavingQuestionId] = useState<number | null>(null);
  // Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-06 — gợi ý tapescript câu hỏi Nghe, mở
  // khóa sau khi nghe HẾT audio đủ số lần cấu hình. Key theo listeningKeyOf (groupKey nếu có, không
  // thì "Q"+questionId) — khớp cách backend gộp bộ đếm cho nhóm "1 audio nhiều câu" (xem
  // ListeningHintService, ListeningGroupBuilder ở Admin).
  const [listeningProgress, setListeningProgress] = useState<Map<string, ListeningPlayProgressResponse>>(new Map());
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
  // Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-12 — bấm "Đóng" khi đang có lượt làm
  // IN_PROGRESS phải hỏi lại (dễ đóng nhầm khi đang giám sát chống gian lận) — không hỏi khi chỉ đang
  // xem lại 1 lượt đã chấm (không có gì để "thoát dở dang").
  const [confirmingClose, setConfirmingClose] = useState(false);

  const readOnly = attempt != null && attempt.status !== "IN_PROGRESS";
  // Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-12 — tách riêng khỏi readOnly (readOnly
  // vẫn false khi attempt == null, vốn đúng cho việc khoá ô nhập vì chưa render câu hỏi nào cả, nhưng
  // KHÔNG được dùng để quyết định hiện nút "Nộp bài": trước đây !readOnly cũng đúng khi attempt == null
  // (VD load lỗi/hết lượt) nên vẫn hiện nhầm nút "Nộp bài" dù chẳng có lượt IN_PROGRESS nào để nộp.
  const hasActiveAttempt = attempt != null && attempt.status === "IN_PROGRESS";
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

    const load = async () => {
      // Cần exerciseMeta (maxAttempts) TRƯỚC khi quyết định có mở lượt mới hay không (đảo thứ tự so
      // với trước — trước đây gọi song song, không chờ được).
      const meta = await getExercise(item.exerciseId);
      setExerciseMeta(meta);

      let attemptRes: ExerciseAttemptResponse;
      if (item.myLatestAttemptId == null) {
        attemptRes = await startAttempt(item.exerciseId, item.assignmentId);
      } else {
        const latest = await getAttempt(item.myLatestAttemptId);
        // Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-05 — lượt gần nhất đã chấm xong
        // (FULLY_GRADED) nhưng dưới ngưỡng đạt (myLatestPassed=false) thì mở LƯỢT MỚI (startAttempt,
        // attemptNumber+1) thay vì xem lại lượt cũ đã chấm — khác các trạng thái khác (IN_PROGRESS/
        // AUTO_GRADED/đã đạt) vẫn resume/xem lại lượt hiện có như cũ. SỬA 2026-08-12 (đã xác nhận với
        // người dùng, fix bug thật): CHỈ mở lượt mới khi CÒN lượt (maxAttempts null hoặc attemptNumber
        // < maxAttempts) — trước đây bỏ qua điều kiện còn lượt nên hết lượt vẫn cố startAttempt(),
        // backend chặn 422 (RetakeNotAllowedException) khiến modal hiện lỗi + không có attempt nào
        // đang IN_PROGRESS nhưng vẫn hiện nhầm nút "Nộp bài", đồng thời học sinh không bao giờ xem lại
        // được lượt cuối cùng (lượt duy nhất đã lộ đáp án theo rào maxAttempts ở BE).
        const stillHasRetake = meta.allowRetake && (meta.maxAttempts == null || latest.attemptNumber < meta.maxAttempts);
        const failedNeedsRetake = latest.status === "FULLY_GRADED" && latest.passed === false && stillHasRetake;
        // Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-22 — fix bug thật: lượt trước đang
        // AUTO_GRADED (chờ chấm câu tự luận/nói, VD AI chấm lỗi thoáng qua) cũng cho mở lượt MỚI nếu còn
        // lượt, thay vì chỉ resume/xem lại lượt cũ đang kẹt chờ chấm — backend startAttempt() vốn không
        // bắt buộc lượt trước phải FULLY_GRADED, chỉ giới hạn theo allowRetake/maxAttempts.
        const pendingCanRetry = latest.status === "AUTO_GRADED" && stillHasRetake;
        const needsRetake = failedNeedsRetake || pendingCanRetry;
        attemptRes = needsRetake ? await startAttempt(item.exerciseId, item.assignmentId) : latest;
      }

      const questionRes = await listExerciseQuestions(item.exerciseId);
      setAttempt(attemptRes);
      setQuestions([...questionRes].sort((a, b) => a.displayOrder - b.displayOrder));
      loadAnswers(attemptRes.id);
    };

    load()
      .catch((err) => setError(friendlyApiErrorMessage(err, t("takeExercise.loadError"))))
      .finally(() => setLoading(false));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [item.exerciseId, item.myLatestAttemptId]);

  // Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-07-31 — xem Javadoc useIntegrityMonitor.
  const attemptId = attempt?.id;
  const { violationCount, isMonitoringActive, justViolated, suppressForFilePicker } = useIntegrityMonitor({
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
      setError(friendlyApiErrorMessage(err, t("takeExercise.saveAnswerError")));
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
      setError(friendlyApiErrorMessage(err, t("takeExercise.saveAnswerError")));
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
      setError(friendlyApiErrorMessage(err, t("takeExercise.submitAudioError")));
    } finally {
      setSavingQuestionId(null);
    }
  };

  /** Gọi khi audio của 1 câu hỏi Nghe phát tới cuối (sự kiện `ended`) — im lặng bỏ qua lỗi mạng, không chặn học sinh nghe/làm bài tiếp. */
  const handleListeningEnded = async (q: ExerciseQuestionResponse) => {
    if (!attempt || readOnly) return;
    try {
      const res = await recordListeningPlay(attempt.id, q.questionId);
      setListeningProgress((prev) => new Map(prev).set(listeningKeyOf(q), res));
    } catch {
      // Không hiện lỗi — nghe lại vẫn hoạt động bình thường, chỉ là chưa ghi được lượt này.
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
      setError(friendlyApiErrorMessage(err, t("takeExercise.saveAnswerError")));
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
      setError(friendlyApiErrorMessage(err, t("takeExercise.submitError")));
    } finally {
      setSubmitting(false);
    }
  };

  /**
   * Bổ sung ngoài SDD gốc (đã xác nhận với người dùng 2026-08-22) — thời gian làm bài tính từ lúc mở
   * bài (attempt.startedAt), KHÁC hạn nộp (dueAt, xem AssignmentsTab.tsx — đã bỏ đếm ngược ở đó theo
   * yêu cầu người dùng). exerciseMeta.timeLimitMinutes NULL = không giới hạn, giữ nguyên hành vi cũ.
   * Chỉ tính/đếm khi còn lượt IN_PROGRESS — xem lại 1 lượt đã nộp thì không cần đếm ngược nữa.
   */
  const timeLimitDeadlineIso = useMemo(() => {
    if (!hasActiveAttempt || !attempt || exerciseMeta?.timeLimitMinutes == null) return null;
    return new Date(new Date(attempt.startedAt).getTime() + exerciseMeta.timeLimitMinutes * 60_000).toISOString();
  }, [hasActiveAttempt, attempt, exerciseMeta?.timeLimitMinutes]);
  const { remainingMs: timeLimitRemainingMs } = useCountdown(timeLimitDeadlineIso);
  const timeLimitTotalMs = exerciseMeta?.timeLimitMinutes != null ? exerciseMeta.timeLimitMinutes * 60_000 : null;
  // Cảnh báo ở 10% thời gian cuối, tối thiểu 1 phút (đề rất ngắn vẫn có đủ thời gian đọc cảnh báo).
  const timeLimitWarning =
    timeLimitRemainingMs != null && timeLimitTotalMs != null && timeLimitRemainingMs <= Math.max(60_000, timeLimitTotalMs * 0.1);

  /** Hết giờ — đã xác nhận với người dùng 2026-08-22: tự động nộp bài (dùng phần đã làm dở). Guard bằng
   * ref tránh gọi handleSubmit() lặp lại khi remainingMs dao động quanh 0 do tick không chính xác tuyệt đối. */
  const autoSubmitRef = useRef(false);
  useEffect(() => {
    if (hasActiveAttempt && timeLimitRemainingMs != null && timeLimitRemainingMs <= 0 && !autoSubmitRef.current) {
      autoSubmitRef.current = true;
      handleSubmit();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [hasActiveAttempt, timeLimitRemainingMs]);

  return (
    // Lớp phủ toàn màn hình (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-12) — thay cho
    // popup căn giữa cũ, mirror đúng pattern ReviewVideoTaskModal (fixed inset-0 bg-white z-[100]) để
    // đồng nhất mọi dạng bài tập (trắc nghiệm/điền từ/nghe/nói/sắp xếp câu) và tận dụng hết chiều cao
    // màn hình trên mobile lẫn desktop, không bị bó hẹp trong khung max-h-[85vh] như trước.
    <div className="fixed inset-0 bg-white z-[100] flex flex-col">
      {/* Popup cảnh báo tức thời — hiện ngay lúc phát hiện vi phạm mới, tự mờ dần sau ~3.5s, khác banner
          tĩnh bên dưới (chỉ đổi số đếm, học sinh dễ không để ý). Neo "fixed" ở gốc màn hình để luôn nổi
          trên cùng bất kể đang cuộn tới đâu bên trong nội dung đề. */}
      {justViolated && !stoppedByViolation && (
        <div
          key={violationCount}
          role="alert"
          className="fixed top-4 sm:top-6 left-1/2 -translate-x-1/2 z-[110] flex items-center gap-2 bg-rose-600 text-white pl-3 pr-4 py-2.5 rounded-2xl shadow-xl animate-alert-pop max-w-[92vw]"
        >
          <ShieldAlert size={18} className="shrink-0" />
          <span className="text-xs font-black">{t("monitoring.violationToast")}</span>
        </div>
      )}

      {/* Cảnh báo mạnh — chặn tương tác, khác hẳn toast nhỏ ở trên — hiện đúng 1 lần khi vừa bị dừng ép. */}
      {stoppedByViolation && (
        <div className="fixed inset-0 bg-black/60 flex items-center justify-center p-4 z-[120]">
          <div className="bg-white rounded-[20px] w-full max-w-md p-6 space-y-4 text-center shadow-xl">
            <ShieldAlert size={40} className="text-rose-600 mx-auto" />
            <h3 className="text-base font-black text-ink">{t("takeExercise.stoppedByViolation.title")}</h3>
            <p className="text-xs font-bold text-muted leading-relaxed">{t("takeExercise.stoppedByViolation.description")}</p>
            <button
              onClick={() => {
                setStoppedByViolation(false);
                onClose();
              }}
              className="text-xs font-extrabold text-white bg-teal px-5 py-2.5 rounded-xl"
            >
              {t("takeExercise.stoppedByViolation.understood")}
            </button>
          </div>
        </div>
      )}

      {/*
       * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-22 — submitAttempt() có thể gọi AI
       * chấm bài Writing đồng bộ (Gemini, thực đo ~10-20s) NGAY trong request nộp bài — trước đây chỉ
       * disable nút "Nộp bài" đổi chữ "Đang nộp...", học sinh không biết có đang chờ AI chấm hay hệ
       * thống bị treo. Lớp phủ toàn màn hình rõ ràng hơn, đặt tên đúng việc đang chờ.
       */}
      {submitting && (
        <div className="fixed inset-0 bg-white/90 backdrop-blur-sm flex items-center justify-center z-[125]">
          <div className="flex flex-col items-center gap-3 text-center px-6">
            <Loader2 size={36} className="text-teal animate-spin" />
            <p className="text-sm font-extrabold text-ink">{t("takeExercise.gradingOverlay.title")}</p>
            <p className="text-xs font-bold text-muted max-w-xs">{t("takeExercise.gradingOverlay.description")}</p>
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
          hasFeedback={[...answersByQuestion.values()].some((a) => !!a.gradingFeedback)}
          onClose={() => {
            setJustSubmitted(false);
            onFinished();
          }}
        />
      )}

      <div className="border-b border-line/60 shrink-0">
        <div className="max-w-2xl lg:max-w-3xl w-full mx-auto px-4 sm:px-6 py-3 sm:py-4 flex items-center justify-between gap-3">
          <div className="min-w-0">
            <h3 className="text-lg sm:text-xl lg:text-2xl font-extrabold text-ink truncate">{item.title}</h3>
            {attempt && (
              <p className="text-[10px] sm:text-xs text-muted font-bold mt-0.5">
                {t("takeExercise.attemptNumber", { number: attempt.attemptNumber })} ·{" "}
                {attempt.status === "IN_PROGRESS"
                  ? t("takeExercise.status.inProgress")
                  : attempt.status === "FULLY_GRADED"
                    ? t("takeExercise.status.fullyGraded")
                    : t("takeExercise.status.submittedPendingGrading")}
                {attempt.totalScore != null && t("takeExercise.scoreSuffix", { score: attempt.totalScore })}
              </p>
            )}
          </div>
          <div className="flex items-center gap-2 shrink-0">
            {/* Chip ghim góc (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-12) — thay cho
                banner amber căng hết chiều rộng trước đây, gọn lại thành 1 badge nhỏ ngay cạnh nút Đóng,
                bấm/hover mới hiện đủ dòng cảnh báo. */}
            {isMonitoringActive && <MonitoringBadge violationCount={violationCount} />}
            <button
              onClick={() => (hasActiveAttempt ? setConfirmingClose(true) : onClose())}
              aria-label={t("takeExercise.closeAriaLabel")}
              // Làm nổi bật (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-12) — trước đây
              // chỉ là link chữ mờ dễ bỏ sót, giờ là nút tròn viền đỏ nhạt, dễ nhận biết hành động thoát.
              className="shrink-0 flex items-center justify-center w-9 h-9 sm:w-10 sm:h-10 rounded-full bg-rose-50 hover:bg-rose-100 text-rose-600 border border-rose-200 transition-colors"
            >
              <X size={18} className="sm:w-5 sm:h-5" />
            </button>
          </div>
        </div>
      </div>

      {timeLimitDeadlineIso && timeLimitRemainingMs != null && (
        <div
          className={`shrink-0 px-4 sm:px-6 py-2 text-center text-xs font-extrabold tabular-nums ${
            timeLimitWarning ? "bg-rose-50 text-rose-700 border-b border-rose-100" : "bg-amber-50 text-amber-800 border-b border-amber-100"
          }`}
        >
          {t("takeExercise.timeLimit.remainingPrefix")}
          {formatRemaining(timeLimitRemainingMs, t)}
          {timeLimitWarning && ` — ${t("takeExercise.timeLimit.warning")}`}
        </div>
      )}

      {/* Cảnh báo trước khi đóng (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-12) — chỉ
          hỏi khi đang có lượt IN_PROGRESS (dễ đóng nhầm lúc đang bị giám sát chống gian lận); xem lại
          1 lượt đã chấm thì đóng thẳng, không cần hỏi. */}
      {confirmingClose && (
        <div className="fixed inset-0 bg-black/60 flex items-center justify-center p-4 z-[120]">
          <div className="bg-white rounded-[20px] w-full max-w-sm p-6 space-y-4 text-center shadow-xl">
            <ShieldAlert size={36} className="text-amber-600 mx-auto" />
            <h3 className="text-base font-black text-ink">{t("takeExercise.confirmClose.title")}</h3>
            <p className="text-xs font-bold text-muted leading-relaxed">{t("takeExercise.confirmClose.description")}</p>
            <div className="flex flex-col sm:flex-row gap-2">
              <button
                onClick={() => setConfirmingClose(false)}
                className="flex-1 px-4 py-2.5 bg-white hover:bg-slate-100 border border-line rounded-xl text-xs font-extrabold text-ink"
              >
                {t("takeExercise.confirmClose.stay")}
              </button>
              <button
                onClick={() => {
                  setConfirmingClose(false);
                  onClose();
                }}
                className="flex-1 px-4 py-2.5 bg-rose-600 hover:bg-rose-700 text-white rounded-xl text-xs font-extrabold"
              >
                {t("takeExercise.confirmClose.stillClose")}
              </button>
            </div>
          </div>
        </div>
      )}

      <div className="flex-1 overflow-y-auto">
        <div className="max-w-2xl lg:max-w-3xl w-full mx-auto p-4 sm:p-6 space-y-5">
          {error && <div className="text-xs font-bold text-rose-600 bg-rose-50 border border-rose-100 p-3 rounded-xl">{error}</div>}

          {loading ? (
            <p className="text-xs text-muted font-bold flex items-center gap-2">
              <Loader2 size={14} className="animate-spin" /> {t("takeExercise.loadingExam")}
            </p>
          ) : questions.length === 0 ? (
            <p className="text-xs text-muted font-bold italic">{t("takeExercise.noQuestions")}</p>
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
                  textDraft={textDraft}
                  onTextChange={(questionId, v) => setTextDraft((prev) => ({ ...prev, [questionId]: v }))}
                  onTextBlur={handleTextBlur}
                  onAudioUpload={handleAudioAnswer}
                  onFilePickerOpen={suppressForFilePicker}
                  attemptId={attempt?.id}
                  listeningProgress={listeningProgress}
                  onListeningEnded={handleListeningEnded}
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
                  onFilePickerOpen={suppressForFilePicker}
                  attemptId={attempt?.id}
                  listeningProgress={listeningProgress}
                  onListeningEnded={handleListeningEnded}
                />
              )
            )
          )}
        </div>
      </div>

      {hasActiveAttempt && confirmingSubmit && (
        <div className="border-t border-line/60 bg-amber-50 shrink-0">
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
              disabled={submitting || loading}
              className="text-sm sm:text-base font-extrabold text-white bg-teal px-5 sm:px-6 py-2.5 sm:py-3 rounded-xl disabled:opacity-50"
            >
              {submitting ? t("takeExercise.submitting") : t("takeExercise.submitButton")}
            </button>
          </div>
        </div>
      )}

      {/* Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-12 — không còn lượt IN_PROGRESS nào
          để nộp (đang xem lại lượt đã chấm, hoặc hết lượt làm lại) thì thanh dưới cùng phải là "Thoát",
          không phải "Nộp bài" — trước đây thiếu nhánh này nên khi hết lượt vẫn hiện nhầm "Nộp bài". */}
      {!hasActiveAttempt && !loading && (
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
  hasFeedback,
  onClose
}: {
  attempt: ExerciseAttemptResponse;
  exerciseTitle: string;
  exerciseMeta: ExerciseMetaResponse | null;
  /** Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-22 — có ít nhất 1 câu tự luận/nói đã có nhận xét chấm (tay/AI), gợi ý học sinh cuộn xuống xem chi tiết thay vì chỉ thấy % tổng. */
  hasFeedback: boolean;
  onClose: () => void;
}) {
  const { t } = useTranslation("portal-exercises");
  const fullyGraded = attempt.status === "FULLY_GRADED";
  const passed = fullyGraded ? attempt.passed : null;

  let remainingText: string | null = null;
  if (fullyGraded && passed === false && exerciseMeta) {
    if (!exerciseMeta.allowRetake) {
      remainingText = t("takeExercise.resultPopup.retakeNotAllowed");
    } else if (exerciseMeta.maxAttempts == null) {
      remainingText = t("takeExercise.resultPopup.retakeAnytime");
    } else {
      const remaining = Math.max(0, exerciseMeta.maxAttempts - attempt.attemptNumber);
      remainingText =
        remaining > 0
          ? t("takeExercise.resultPopup.retakeRemaining", { count: remaining })
          : t("takeExercise.resultPopup.retakeExhausted");
    }
  }

  return (
    <div className="fixed inset-0 bg-black/60 flex items-center justify-center p-4 z-[110]">
      <div className="bg-white rounded-[20px] w-full max-w-md p-6 space-y-4 text-center shadow-xl">
        {!fullyGraded ? (
          <>
            <CheckCircle2 size={40} className="text-teal mx-auto" />
            <h3 className="text-base font-black text-ink">{t("takeExercise.resultPopup.submittedTitle", { title: exerciseTitle })}</h3>
            <p className="text-xs font-bold text-muted leading-relaxed">{t("takeExercise.resultPopup.submittedDescription")}</p>
          </>
        ) : passed ? (
          <>
            <PartyPopper size={40} className="text-teal mx-auto" />
            <h3 className="text-base font-black text-ink">{t("takeExercise.resultPopup.passedTitle", { title: exerciseTitle })}</h3>
            <p className="text-xs font-bold text-teal-deep leading-relaxed">
              {t("takeExercise.resultPopup.passedDescription", { percentage: attempt.percentage ?? "—" })}
            </p>
          </>
        ) : (
          <>
            <RotateCcw size={40} className="text-coral mx-auto" />
            <h3 className="text-base font-black text-ink">{t("takeExercise.resultPopup.failedTitle", { title: exerciseTitle })}</h3>
            <p className="text-xs font-bold text-coral leading-relaxed">
              {t("takeExercise.resultPopup.failedDescription", {
                percentage: attempt.percentage ?? "—",
                threshold: exerciseMeta ? t("takeExercise.resultPopup.thresholdSuffix", { percent: exerciseMeta.passThresholdPercent }) : ""
              })}
            </p>
            {remainingText && <p className="text-xs font-bold text-muted leading-relaxed">{remainingText}</p>}
          </>
        )}
        {hasFeedback && <p className="text-[11px] text-muted font-bold italic">{t("takeExercise.resultPopup.seeFeedbackHint")}</p>}
        <button onClick={onClose} className="text-xs font-extrabold text-white bg-teal px-5 py-2.5 rounded-xl">
          {t("takeExercise.resultPopup.understood")}
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
  onAudioUpload,
  onFilePickerOpen,
  attemptId,
  listeningProgress,
  onListeningEnded
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
  onFilePickerOpen: () => void;
  attemptId: number | undefined;
  listeningProgress: Map<string, ListeningPlayProgressResponse>;
  onListeningEnded: (q: ExerciseQuestionResponse) => void;
}) {
  const { t } = useTranslation("portal-exercises");
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
    <div className="border border-line/60 rounded-[16px] p-4 sm:p-5 lg:p-6 space-y-3 lg:space-y-4">
      <div className="flex items-start justify-between gap-3">
        <p className="text-sm sm:text-base lg:text-lg font-bold text-ink">
          {question.displayOrder}. {question.questionContent}
        </p>
        <div className="flex items-center gap-2 shrink-0">
          {question.skill === "LISTENING" && question.audioUrl && attemptId != null && (
            <ListeningHintButton
              attemptId={attemptId}
              questionId={question.questionId}
              choices={question.choices}
              progress={listeningProgress.get(listeningKeyOf(question))}
              readOnly={readOnly}
            />
          )}
          <span className="text-[10px] sm:text-xs text-muted font-bold">{t("takeExercise.question.pointsSuffix", { points: question.points })}</span>
        </div>
      </div>

      <ListeningAudioBlock question={question} onEnded={() => onListeningEnded(question)} />

      {isChoiceQuestion ? (
        <div className="space-y-2 lg:space-y-2.5">
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
                className={`w-full text-left text-xs sm:text-sm lg:text-base font-bold px-3 py-2.5 sm:px-4 sm:py-3 rounded-xl border transition-colors flex items-center justify-between gap-2 ${stateClass} disabled:cursor-default`}
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
          <div className="space-y-1">
            <p className="text-[10px] text-muted font-bold uppercase">{t("takeExercise.question.recordAnswerLabel")}</p>
            <input
              type="file"
              accept="audio/*"
              disabled={readOnly || saving}
              onClick={onFilePickerOpen}
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
          <p className="text-[10px] text-muted italic">{t("takeExercise.question.manualGradingNote")}</p>
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
            placeholder={t("takeExercise.question.answerPlaceholder")}
            className="w-full bg-sky-2 border border-line/70 text-xs sm:text-sm lg:text-base p-3 sm:p-4 rounded-xl focus:outline-none disabled:opacity-70"
          />
          {isFillInBlank && showFeedback && (
            <div className={`flex items-center gap-1.5 text-xs font-bold ${answer?.isCorrect ? "text-teal-deep" : "text-coral"}`}>
              {answer?.isCorrect ? <CheckCircle2 size={14} /> : <XCircle size={14} />}
              {answer?.isCorrect
                ? t("takeExercise.question.correct")
                : t("takeExercise.question.correctAnswerPrefix", { answer: answer?.correctAnswerText ?? "—" })}
            </div>
          )}
        </div>
      )}

      {(question.questionType === "WORD_BANK" || question.questionType === "SENTENCE_BUILDING") && showFeedback && (
        <div className={`flex items-center gap-1.5 text-xs font-bold ${answer?.isCorrect ? "text-teal-deep" : "text-coral"}`}>
          {answer?.isCorrect ? <CheckCircle2 size={14} /> : <XCircle size={14} />}
          {answer?.isCorrect
            ? t("takeExercise.question.correct")
            : t("takeExercise.question.correctAnswerPrefix", {
                answer:
                  (question.questionType === "WORD_BANK" ? answer?.correctStructuredContent?.blanks : answer?.correctStructuredContent?.chunks)?.join(
                    " — "
                  ) ?? "—"
              })}
        </div>
      )}

      {answer?.explanation && showFeedback && (
        <p className="text-[11px] text-muted font-bold italic border-t border-line/50 pt-2">
          {t("takeExercise.question.explanationPrefix", { text: answer.explanation })}
        </p>
      )}

      {/*
       * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-22 — điểm/nhận xét câu tự luận/nói
       * (ESSAY/SPEAKING) đã chấm (tay hoặc AI). KHÔNG phụ thuộc showFeedback (đó là cấu hình riêng cho
       * việc lộ ĐÁP ÁN ĐÚNG) — nhận xét bài của chính học sinh luôn hiện ngay khi có, để trả lời "vì
       * sao đạt/không đạt" thay vì chỉ thấy % tổng ở popup kết quả.
       */}
      {answer?.gradingFeedback && (
        <div className="text-xs font-bold p-3 rounded-xl border bg-sky-2 border-teal/20 space-y-1.5">
          <div className="flex items-center justify-between gap-2 flex-wrap">
            <span className="text-teal-deep uppercase text-[10px] tracking-wide">{t("takeExercise.question.gradingFeedbackTitle")}</span>
            <span className="text-[10px] text-muted font-black uppercase">
              {answer.gradingSource === "AI" ? t("takeExercise.question.gradedByAi") : t("takeExercise.question.gradedByTeacher")}
              {answer.gradingScore != null && answer.gradingMaxScore != null
                ? ` · ${t("takeExercise.question.gradingScoreSuffix", { score: answer.gradingScore, max: answer.gradingMaxScore })}`
                : ""}
            </span>
          </div>
          <p className="font-medium text-ink normal-case whitespace-pre-line">{answer.gradingFeedback}</p>
        </div>
      )}

      {answerLockedByRetake && <LockedAnswerBanner attemptsRemainingBeforeAnswer={attemptsRemainingBeforeAnswer} />}
    </div>
  );
}

/** UC-24/A4, UC-27/A2: nút "Xem đáp án" luôn hiện — chỉ khóa (disabled) khi backend chưa lộ đáp án vì còn lượt làm lại. */
function LockedAnswerBanner({ attemptsRemainingBeforeAnswer }: { attemptsRemainingBeforeAnswer: number | null }) {
  const { t } = useTranslation("portal-exercises");
  return (
    <div className="flex items-center justify-between gap-2 text-xs font-bold text-amber-800 bg-amber-50 border border-amber-100 rounded-xl px-3 py-2">
      <span>
        {attemptsRemainingBeforeAnswer != null && attemptsRemainingBeforeAnswer > 0
          ? t("takeExercise.locked.remaining", { count: attemptsRemainingBeforeAnswer })
          : t("takeExercise.locked.lastAttemptOnly")}
      </span>
      <button type="button" disabled className="flex items-center gap-1 text-[11px] font-extrabold text-amber-700 bg-amber-100 px-2.5 py-1 rounded-lg opacity-70 cursor-not-allowed">
        <Lock size={11} /> {t("takeExercise.locked.viewAnswer")}
      </button>
    </div>
  );
}

/**
 * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-06 — audio dùng chung cho MỌI câu hỏi
 * Nghe (skill=LISTENING, không riêng SPEAKING), tách khỏi nút "?" gợi ý (nút gợi ý giờ đặt ở hàng
 * tiêu đề câu hỏi, xem ListeningHintButton). Trước đây "Trắc nghiệm Voice"/"Nghe điền từ" đơn lẻ
 * (không nhóm) hoàn toàn không phát audio cho học sinh (audio chỉ được render trong nhánh SPEAKING)
 * — sửa cùng đợt vì cùng nằm trong luồng "Nghe" đang kiểm tra/xử lý, audio giờ luôn hiện 1 lần ở đây
 * bất kể questionType, KHÔNG lặp lại trong nhánh SPEAKING nữa.
 */
function ListeningAudioBlock({ question, onEnded }: { question: ExerciseQuestionResponse; onEnded: () => void }) {
  const { t } = useTranslation("portal-exercises");
  if (question.skill !== "LISTENING" || !question.audioUrl) return null;
  return (
    <div className="space-y-1.5">
      <p className="text-[10px] text-muted font-bold uppercase">{t("takeExercise.listening.audioLabel")}</p>
      {/* eslint-disable-next-line jsx-a11y/media-has-caption */}
      <audio controls src={question.audioUrl} className="w-full" onEnded={onEnded} />
    </div>
  );
}

/**
 * Icon "?" gợi ý tapescript — khóa cho tới khi nghe hết audio đủ số lần cấu hình
 * (progress.hintUnlocked). Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-06: đặt ở góc
 * phải hàng tiêu đề câu hỏi (song song với câu hỏi, không nằm dưới audio nữa), hiện dạng tooltip khi
 * di chuột vào thay vì nút bấm có nhãn chữ.
 */
function ListeningHintButton({
  attemptId,
  questionId,
  choices,
  progress,
  readOnly
}: {
  attemptId: number;
  questionId: number;
  choices: ExerciseQuestionChoiceResponse[];
  progress: ListeningPlayProgressResponse | undefined;
  readOnly: boolean;
}) {
  const [open, setOpen] = useState(false);
  const [hint, setHint] = useState<ListeningHintResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  // Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-06 — panel gợi ý trước đây `position:
  // absolute` nằm bên trong vùng `overflow-y-auto` của modal làm bài (bao toàn bộ danh sách câu hỏi)
  // nên câu hỏi ở gần cuối bị cắt mất phần tooltip tràn ra ngoài đáy vùng cuộn, không cách nào xem
  // được. Render qua Portal ra document.body (mirror admin/src/components/ui/Select.tsx — cùng vấn đề
  // dropdown bị overflow cha cắt), tự tính toạ độ `position: fixed` theo bounding rect của nút "?",
  // tự lật lên trên khi không đủ chỗ bên dưới viewport.
  const [placement, setPlacement] = useState<{ top?: number; bottom?: number; right: number; flipped: boolean } | null>(null);
  const triggerRef = useRef<HTMLDivElement>(null);
  const { t } = useTranslation("portal-exercises");

  const threshold = progress?.hintUnlockThreshold ?? 3;
  const unlocked = progress?.hintUnlocked ?? false;
  // Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-06 — hiện thêm số lượt nghe hết CÒN
  // THIẾU (không chỉ báo cố định "cần đủ N lần") để học sinh biết mình đã nghe được bao nhiêu, còn
  // thiếu bao nhiêu lần nữa mới mở khóa.
  const playCount = progress?.playCount ?? 0;
  const remaining = Math.max(0, threshold - playCount);

  const updatePlacement = () => {
    const el = triggerRef.current;
    if (!el) return;
    const r = el.getBoundingClientRect();
    // Chỉ ước lượng chiều cao để QUYẾT ĐỊNH có lật hướng hay không — vị trí thật dùng CSS `bottom`
    // khi lật (neo theo mép trên của nút) nên không cần đúng tuyệt đối chiều cao nội dung thật.
    const estimatedHeight = unlocked ? 240 : 70;
    const spaceBelow = window.innerHeight - r.bottom;
    const flipped = spaceBelow < estimatedHeight && r.top > spaceBelow;
    const right = Math.max(8, window.innerWidth - r.right);
    setPlacement(flipped ? { bottom: window.innerHeight - r.top + 6, right, flipped } : { top: r.bottom + 6, right, flipped });
  };

  useLayoutEffect(() => {
    if (open) updatePlacement();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open, unlocked]);

  useEffect(() => {
    if (!open) return;
    const reposition = () => updatePlacement();
    window.addEventListener("scroll", reposition, true);
    window.addEventListener("resize", reposition);
    return () => {
      window.removeEventListener("scroll", reposition, true);
      window.removeEventListener("resize", reposition);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open]);

  const ensureLoaded = async () => {
    if (!unlocked || hint || loading) return;
    setLoading(true);
    setError(null);
    try {
      setHint(await getListeningHint(attemptId, questionId));
    } catch (err) {
      setError(friendlyApiErrorMessage(err, t("takeExercise.hintError")));
    } finally {
      setLoading(false);
    }
  };

  const handleOpen = () => {
    if (readOnly) return;
    setOpen(true);
    ensureLoaded();
  };

  const correctChoiceContents = hint?.correctChoiceIds.length
    ? choices.filter((c) => hint.correctChoiceIds.includes(c.id)).map((c) => `${c.choiceLabel}. ${c.content}`)
    : [];

  return (
    <div ref={triggerRef} className="relative shrink-0" onMouseEnter={handleOpen} onMouseLeave={() => setOpen(false)}>
      <button
        type="button"
        disabled={readOnly}
        onClick={handleOpen}
        aria-label={t("takeExercise.listening.hintAriaLabel")}
        className="w-5 h-5 rounded-full border border-teal/50 bg-teal/10 text-teal-deep flex items-center justify-center disabled:opacity-60"
      >
        <HelpCircle size={12} />
      </button>
      {open &&
        placement &&
        createPortal(
          !unlocked ? (
            // Tooltip dạng bong bóng thoại — mũi nhọn trỏ về phía nút "?" (lên nếu tooltip nằm dưới,
            // xuống nếu bị lật lên trên), màu theo đúng bảng màu hệ thống (teal-deep).
            <div style={{ position: "fixed", top: placement.top, bottom: placement.bottom, right: placement.right }} className="z-[200]">
              <div className={`absolute right-3 w-3 h-3 bg-teal-deep rotate-45 ${placement.flipped ? "-bottom-1.5" : "-top-1.5"}`} />
              <div className="relative bg-teal-deep text-white text-[11px] font-bold rounded-lg px-3 py-2 max-w-[220px] shadow-lg">
                {t("takeExercise.listening.locked", { playCount, threshold, remaining })}
              </div>
            </div>
          ) : (
            <div
              style={{ position: "fixed", top: placement.top, bottom: placement.bottom, right: placement.right }}
              className="z-[200] w-72 max-w-[80vw] text-left text-xs bg-white border border-line/60 rounded-xl shadow-lg p-3 space-y-1.5"
            >
              {loading ? (
                <p className="font-bold text-muted flex items-center gap-1.5">
                  <Loader2 size={12} className="animate-spin" /> {t("takeExercise.listening.loadingHint")}
                </p>
              ) : error ? (
                <p className="font-bold text-coral">{error}</p>
              ) : hint ? (
                <>
                  {hint.transcript && (
                    <p>
                      <span className="font-bold">{t("takeExercise.listening.transcriptLabel")}</span>
                      {hint.transcript}
                    </p>
                  )}
                  {hint.correctAnswerText && (
                    <p>
                      <span className="font-bold">{t("takeExercise.listening.correctAnswerLabel")}</span>
                      {hint.correctAnswerText}
                    </p>
                  )}
                  {correctChoiceContents.length > 0 && (
                    <p>
                      <span className="font-bold">{t("takeExercise.listening.correctAnswerLabel")}</span>
                      {correctChoiceContents.join(", ")}
                    </p>
                  )}
                  {hint.explanation && (
                    <p>
                      <span className="font-bold">{t("takeExercise.listening.explanationLabel")}</span>
                      {hint.explanation}
                    </p>
                  )}
                </>
              ) : null}
            </div>
          ),
          document.body
        )}
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
  const { t } = useTranslation("portal-exercises");
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
    <div className="flex flex-wrap items-center gap-x-1.5 gap-y-2 text-sm sm:text-base lg:text-lg font-bold text-ink leading-8 lg:leading-10">
      {parts.map((part, idx) => (
        <React.Fragment key={idx}>
          {part && <span>{part}</span>}
          {idx < blankCount && (
            <select
              value={selections[idx]}
              disabled={readOnly || saving}
              onChange={(e) => handleSelect(idx, e.target.value)}
              className="bg-sky-2 border border-line/70 text-xs sm:text-sm lg:text-base font-bold px-2 py-1.5 sm:px-3 sm:py-2 rounded-lg focus:outline-none disabled:opacity-70"
            >
              <option value="">{t("takeExercise.wordBank.choosePlaceholder")}</option>
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
  const { t } = useTranslation("portal-exercises");
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
        {built.length === 0 && <span className="text-[11px] text-muted italic px-1">{t("takeExercise.sentenceBuilding.instructions")}</span>}
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

/**
 * V78 — "Đọc hiểu — lưới": 1 đoạn văn dùng chung + bảng N câu × 3 cột đáp án (radio từng dòng).
 * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-06 — cũng dùng cho nhóm "1 audio nhiều
 * câu" của GV nước ngoài (ListeningGroupBuilder): phát audio dùng chung ở đầu khối, mỗi câu con rẽ
 * nhánh theo questionType — MULTIPLE_CHOICE giữ nguyên dãy nút chọn đáp án như cũ, FILL_IN_BLANK
 * thêm ô nhập text tự chấm, SPEAKING thêm control nộp audio (chấm tay).
 */
function GridQuestionGroup({
  block,
  answersByQuestion,
  readOnly,
  savingQuestionId,
  attemptsRemainingBeforeAnswer,
  onChoiceToggle,
  textDraft,
  onTextChange,
  onTextBlur,
  onAudioUpload,
  onFilePickerOpen,
  attemptId,
  listeningProgress,
  onListeningEnded
}: {
  block: Extract<RenderBlock, { type: "grid" }>;
  answersByQuestion: Map<number, StudentAnswerResponse>;
  readOnly: boolean;
  savingQuestionId: number | null;
  attemptsRemainingBeforeAnswer: number | null;
  onChoiceToggle: (questionId: number, choiceIds: number[]) => void;
  textDraft: Record<number, string>;
  onTextChange: (questionId: number, value: string) => void;
  onTextBlur: (questionId: number) => void;
  onAudioUpload: (questionId: number, file: File) => void;
  onFilePickerOpen: () => void;
  attemptId: number | undefined;
  listeningProgress: Map<string, ListeningPlayProgressResponse>;
  onListeningEnded: (q: ExerciseQuestionResponse) => void;
}) {
  const { t } = useTranslation("portal-exercises");
  // UC-24/A4, UC-27/A2: mọi câu trong 1 nhóm lưới đều thuộc cùng 1 lượt làm — chỉ cần 1 banner khóa chung.
  const anyLockedByRetake = block.questions.some((q) => {
    const a = answersByQuestion.get(q.questionId);
    return a != null && a.isAutoGradable && a.isCorrect != null && !isAnswerRevealed(a);
  });
  return (
    <div className="border border-line/60 rounded-[16px] p-4 sm:p-5 lg:p-6 space-y-3 lg:space-y-4">
      {block.referencePassage && (
        <p className="text-xs sm:text-sm lg:text-base text-ink whitespace-pre-wrap bg-sky-2 rounded-xl p-3 sm:p-4">{block.referencePassage}</p>
      )}
      {block.audioUrl && (
        // eslint-disable-next-line jsx-a11y/media-has-caption
        <audio controls src={block.audioUrl} className="w-full" onEnded={() => onListeningEnded(block.questions[0])} />
      )}
      <div className="divide-y divide-line/50">
        {block.questions.map((q) => {
          const answer = answersByQuestion.get(q.questionId);
          const selected = new Set(answer?.selectedChoiceIds ?? []);
          const correctIds = new Set(answer?.correctChoiceIds ?? []);
          const showFeedback = answer != null && isAnswerRevealed(answer);
          const saving = savingQuestionId === q.questionId;
          const isChoiceRow = CHOICE_TYPES.has(q.questionType) && q.choices.length > 0;
          const isFillInBlankRow = q.questionType === "FILL_IN_BLANK";
          const isSpeakingRow = q.questionType === "SPEAKING";
          return (
            <div key={q.id} className="py-2.5 lg:py-3.5 space-y-2">
              <div className="flex items-center gap-2 flex-wrap">
                <span className="text-xs sm:text-sm lg:text-base font-bold text-ink flex-1 min-w-[160px]">
                  {q.displayOrder}. {q.questionContent}
                </span>
                {q.skill === "LISTENING" && attemptId != null && (
                  <ListeningHintButton
                    attemptId={attemptId}
                    questionId={q.questionId}
                    choices={q.choices}
                    progress={listeningProgress.get(listeningKeyOf(q))}
                    readOnly={readOnly || saving}
                  />
                )}
                {isChoiceRow && (
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
                          className={`w-8 h-8 sm:w-9 sm:h-9 lg:w-10 lg:h-10 rounded-lg border text-[11px] sm:text-xs lg:text-sm font-bold transition-colors disabled:cursor-default ${cls}`}
                        >
                          {c.choiceLabel}
                        </button>
                      );
                    })}
                  </div>
                )}
              </div>

              {isFillInBlankRow && (
                <div className="space-y-1">
                  <input
                    value={textDraft[q.questionId] ?? answer?.answerText ?? ""}
                    onChange={(e) => onTextChange(q.questionId, e.target.value)}
                    onBlur={() => onTextBlur(q.questionId)}
                    disabled={readOnly || saving}
                    placeholder={t("takeExercise.question.answerPlaceholder")}
                    className="w-full bg-sky-2 border border-line/70 text-xs sm:text-sm lg:text-base p-2.5 sm:p-3 rounded-xl focus:outline-none disabled:opacity-70"
                  />
                  {showFeedback && (
                    <div className={`flex items-center gap-1.5 text-xs font-bold ${answer?.isCorrect ? "text-teal-deep" : "text-coral"}`}>
                      {answer?.isCorrect ? <CheckCircle2 size={14} /> : <XCircle size={14} />}
                      {answer?.isCorrect
                        ? t("takeExercise.question.correct")
                        : t("takeExercise.question.correctAnswerPrefix", { answer: answer?.correctAnswerText ?? "—" })}
                    </div>
                  )}
                </div>
              )}

              {isSpeakingRow && (
                <div className="space-y-1">
                  <input
                    type="file"
                    accept="audio/*"
                    disabled={readOnly || saving}
                    onClick={onFilePickerOpen}
                    onChange={(e) => {
                      const file = e.target.files?.[0];
                      if (file) onAudioUpload(q.questionId, file);
                      e.target.value = "";
                    }}
                    className="text-xs font-bold text-ink file:mr-2 file:px-3 file:py-1.5 file:rounded-lg file:border-0 file:bg-teal file:text-white file:text-xs file:font-extrabold disabled:opacity-70"
                  />
                  {answer?.audioAnswerUrl && (
                    // eslint-disable-next-line jsx-a11y/media-has-caption
                    <audio controls src={answer.audioAnswerUrl} className="w-full mt-1" />
                  )}
                  <p className="text-[10px] text-muted italic">{t("takeExercise.question.manualGradingNote")}</p>
                </div>
              )}
            </div>
          );
        })}
      </div>

      {anyLockedByRetake && <LockedAnswerBanner attemptsRemainingBeforeAnswer={attemptsRemainingBeforeAnswer} />}
    </div>
  );
}
