import React, { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { CheckCircle2 } from "lucide-react";
import { useTranslation } from "react-i18next";
import { ApiError } from "@/lib/apiClient";
import Modal from "@/components/ui/Modal";
import Button from "@/components/ui/Button";
import Select from "@/components/ui/Select";
import {
  ExamTeacherType,
  ExerciseResponse,
  ExerciseSkillCategory,
  QuestionImportedRow,
  QuestionResponse,
  addExerciseQuestion,
  createExercise,
  listExerciseQuestions,
  publishExercise,
  updateExerciseQuestionPoints
} from "../api";
import QuestionEditorForm from "./QuestionEditorForm";
import QuestionImportPanel from "./QuestionImportPanel";
import GridQuestionBuilder from "./GridQuestionBuilder";
import ListeningGroupBuilder from "./ListeningGroupBuilder";
import ClozeQuestionBuilder from "./ClozeQuestionBuilder";
import FillInBlankGroupBuilder from "./FillInBlankGroupBuilder";
import {
  ComposeSubMode,
  FOREIGN_LISTENING_KINDS,
  FOREIGN_LISTENING_MODES,
  VIETNAMESE_SKILL_KINDS,
  VIETNAMESE_SKILL_MODES,
  VietnameseSkillCategory
} from "../skillCategoryKinds";

const inputClass = "w-full bg-slate-50 border border-slate-200 text-xs p-2.5 rounded-lg focus:outline-none";
const labelClass = "text-[10px] uppercase font-bold text-slate-500 block mb-1";

type Step = "info" | "questions" | "publish";

interface CreateAndAssignExerciseModalProps {
  examId: number;
  /**
   * V77-V83 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-04): VIETNAMESE giữ luồng câu
   * hỏi thường (ưu tiên "Nhập Excel/Word", V78 thêm toggle "Bài đọc hiểu — Lưới"). FOREIGN (V83, sửa
   * lại lần cuối): không còn MÀN HÌNH riêng để chọn loại Bài ("Audio bài nghe"/"Nghe & nộp audio") —
   * kể cả gắn ở bước "gắn câu hỏi" như V82 người dùng vẫn thấy đó là "1 bước ngoài" thừa. Giờ FOREIGN
   * đi đúng 1 luồng DUY NHẤT như VIETNAMESE: soạn info Bài → sang thẳng ExerciseQuestionsStep → chọn
   * loại câu hỏi NGAY TRONG kind-picker của QuestionEditorForm (cùng chỗ VIETNAMESE chọn giữa 7 kind),
   * chỉ khác là danh sách kind cho phép bị giới hạn còn 2 (Trắc nghiệm Voice / Nghe & nộp audio).
   * "Video phản xạ" đã bị bỏ khỏi màn này từ trước (V79) — Video phản xạ (REFLEX) giao lớp trực tiếp
   * ở Kho Video Ôn tập, y hệt Video kết nối.
   */
  teacherType: ExamTeacherType;
  onClose: () => void;
  onDone: () => void;
}

/**
 * UC-40 Main Flow bước 1-2 + Publish (V65, bổ sung ngoài SDD gốc, đã xác nhận với người dùng
 * 2026-07-30): tạo Bài mới trong 1 Đề → gắn câu hỏi → Publish (đánh dấu "đủ điều kiện dùng làm nguồn")
 * hoặc để DRAFT publish sau. KHÔNG còn bước giao lớp/hạn nộp ở đây nữa — việc giao (tự động cho cả lớp,
 * hạn nộp = buổi kế tiếp) chuyển hẳn sang lúc Giáo viên chọn Bài này làm "BTVN buổi sau" ở Nhận xét học
 * viên (UC-21, xem DailyCommentPanel.tsx). Kho đề (2026-07-30): Bài giờ thuộc 1 Đề (examId) thay vì
 * gán khung chương trình trực tiếp — khung chương trình chỉ còn dùng để lọc/duyệt trong Kho đề.
 *
 * V82: info step giờ giống HỆT nhau cho VIETNAMESE/FOREIGN (ExerciseInfoStep không còn phân biệt
 * teacherType) — chỉ bước "gắn câu hỏi" (ExerciseQuestionsStep) mới khác nhau theo teacherType.
 * V75 (merge từ develop, 2026-08-04): câu hỏi giờ thuộc thẳng ngân hàng nội bộ của Đề (examId) — không
 * còn khái niệm chọn/tạo ngân hàng theo khung chương trình nữa.
 */
export default function CreateAndAssignExerciseModal({ examId, teacherType, onClose, onDone }: CreateAndAssignExerciseModalProps) {
  const { t } = useTranslation("lms-question-authoring");
  const [step, setStep] = useState<Step>("info");
  const [exercise, setExercise] = useState<ExerciseResponse | null>(null);
  const [error, setError] = useState<string | null>(null);

  return (
    <Modal open onClose={onClose} title={t("assignModal.modalTitle")} size="lg">
      {error && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg mb-3">{error}</div>}

      {step === "info" && (
        <ExerciseInfoStep
          examId={examId}
          teacherType={teacherType}
          onCreated={(created) => {
            setExercise(created);
            setStep("questions");
          }}
          onError={setError}
        />
      )}

      {step === "questions" && exercise && (
        <ExerciseQuestionsStep
          exercise={exercise}
          teacherType={teacherType}
          onDone={() => setStep("publish")}
          onError={setError}
          onClose={onClose}
        />
      )}

      {step === "publish" && exercise && (
        <ExercisePublishStep
          exercise={exercise}
          onDone={() => {
            onDone();
            onClose();
          }}
          onError={setError}
        />
      )}
    </Modal>
  );
}

function ExerciseInfoStep({
  examId,
  teacherType,
  onCreated,
  onError
}: {
  examId: number;
  teacherType: ExamTeacherType;
  onCreated: (exercise: ExerciseResponse) => void;
  onError: (message: string | null) => void;
}) {
  const { t } = useTranslation("lms-question-authoring");
  const [code, setCode] = useState("");
  const [title, setTitle] = useState("");
  const [totalPoints, setTotalPoints] = useState("10");
  /**
   * V136, bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-21 — nhóm kỹ năng của Bài
   * (Reading/Writing/Từ vựng&Ngữ pháp), cố định từ lúc tạo.
   *
   * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-26 — BẮT BUỘC chọn (bỏ "-- Chưa phân
   * loại --"), dùng để khóa/rút gọn kind-picker ở ExerciseQuestionsStep (xem skillCategoryKinds.ts).
   * FOREIGN chỉ có đúng 1 giá trị hợp lệ (LISTENING) nên mặc định chọn sẵn luôn. VIETNAMESE mặc định
   * chọn sẵn option ĐẦU TIÊN (READING) — bắt buộc khởi tạo state khớp đúng option đầu, KHÔNG để rỗng:
   * do bỏ hẳn placeholder "", nếu state khởi tạo "" (không khớp option nào) trình duyệt vẫn tự hiện
   * option đầu tiên trên UI (hành vi mặc định của <select> khi value không khớp) khiến người dùng
   * tưởng đã chọn Reading trong khi state React thực tế vẫn rỗng — validate submit vẫn báo lỗi dù
   * dropdown nhìn như đã chọn.
   */
  const [skillCategory, setSkillCategory] = useState<ExerciseSkillCategory | "">(
    teacherType === "FOREIGN" ? "LISTENING" : "READING"
  );
  const [allowRetake, setAllowRetake] = useState(false);
  const [maxAttempts, setMaxAttempts] = useState("");
  const [showCorrectAnswers, setShowCorrectAnswers] = useState(true);
  const [passThresholdPercent, setPassThresholdPercent] = useState("");
  /** Bổ sung ngoài SDD gốc (đã xác nhận với người dùng 2026-08-22) — thời gian làm bài tính từ lúc học
   * sinh mở bài (ExerciseAttempt.startedAt), khác hạn nộp (ExerciseAssignment.dueAt). Field
   * Exercise.timeLimitMinutes đã có sẵn từ trước nhưng chưa từng có ô nhập ở đây — học sinh xem đếm
   * ngược ở TakeExerciseModal.tsx (Cổng Học viên), tự nộp bài khi hết giờ. */
  const [timeLimitMinutes, setTimeLimitMinutes] = useState("");
  const [submitting, setSubmitting] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    onError(null);
    if (!code.trim() || !title.trim() || !totalPoints) {
      onError(t("assignModal.infoStep.errors.requiredFields"));
      return;
    }
    if (!skillCategory) {
      onError(t("assignModal.infoStep.errors.skillCategoryRequired"));
      return;
    }
    setSubmitting(true);
    try {
      const created = await createExercise({
        code: code.trim(),
        title: title.trim(),
        examId,
        exerciseType: "ASSIGNED",
        totalPoints: Number(totalPoints),
        allowRetake,
        maxAttempts: allowRetake && maxAttempts ? Number(maxAttempts) : undefined,
        showCorrectAnswers,
        passThresholdPercent: passThresholdPercent ? Number(passThresholdPercent) : undefined,
        timeLimitMinutes: timeLimitMinutes ? Number(timeLimitMinutes) : undefined,
        skillCategory: skillCategory || undefined
      });
      onCreated(created);
    } catch (err) {
      onError(err instanceof ApiError ? err.message : t("assignModal.infoStep.errors.createFailed"));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <form onSubmit={handleSubmit} className="space-y-3">
      <div className="grid grid-cols-2 gap-3">
        <div>
          <label className={labelClass}>{t("assignModal.infoStep.codeLabel")}</label>
          <input value={code} onChange={(e) => setCode(e.target.value)} className={`${inputClass} font-mono`} />
        </div>
        <div>
          <label className={labelClass}>{t("assignModal.infoStep.totalPointsLabel")}</label>
          <input type="number" min={0} value={totalPoints} onChange={(e) => setTotalPoints(e.target.value)} className={inputClass} />
        </div>
        <div className="col-span-2">
          <label className={labelClass}>{t("assignModal.infoStep.titleLabel")}</label>
          <input value={title} onChange={(e) => setTitle(e.target.value)} className={inputClass} />
        </div>
        <div className="col-span-2">
          <label className={labelClass}>{t("assignModal.infoStep.skillCategoryLabel")}</label>
          <Select
            value={skillCategory}
            onChange={(e) => setSkillCategory(e.target.value as ExerciseSkillCategory | "")}
            className={inputClass}
          >
            {teacherType === "FOREIGN" ? (
              <option value="LISTENING">{t("assignModal.infoStep.skillCategoryListening")}</option>
            ) : (
              <>
                <option value="READING">{t("assignModal.infoStep.skillCategoryReading")}</option>
                <option value="WRITING">{t("assignModal.infoStep.skillCategoryWriting")}</option>
                <option value="VOCAB_GRAMMAR">{t("assignModal.infoStep.skillCategoryVocabGrammar")}</option>
              </>
            )}
          </Select>
        </div>
        <div className="col-span-2">
          <label className={labelClass}>{t("assignModal.infoStep.passThresholdLabel")}</label>
          <input
            type="number"
            min={0}
            max={100}
            placeholder={t("assignModal.infoStep.passThresholdPlaceholder")}
            value={passThresholdPercent}
            onChange={(e) => setPassThresholdPercent(e.target.value)}
            className={inputClass}
          />
        </div>
        <div className="col-span-2">
          <label className={labelClass}>{t("assignModal.infoStep.timeLimitLabel")}</label>
          <input
            type="number"
            min={1}
            placeholder={t("assignModal.infoStep.timeLimitPlaceholder")}
            value={timeLimitMinutes}
            onChange={(e) => setTimeLimitMinutes(e.target.value)}
            className={inputClass}
          />
        </div>
        <div>
          <label className="flex items-center gap-1.5 text-[11px] font-bold text-slate-600">
            <input type="checkbox" checked={showCorrectAnswers} onChange={(e) => setShowCorrectAnswers(e.target.checked)} />
            {t("assignModal.infoStep.showCorrectAnswersCheckbox")}
          </label>
        </div>
        <div>
          <label className="flex items-center gap-1.5 text-[11px] font-bold text-slate-600">
            <input type="checkbox" checked={allowRetake} onChange={(e) => setAllowRetake(e.target.checked)} />
            {t("assignModal.infoStep.allowRetakeCheckbox")}
          </label>
        </div>
        {allowRetake && (
          <div className="col-span-2">
            <label className={labelClass}>{t("assignModal.infoStep.maxAttemptsLabel")}</label>
            <input type="number" min={1} value={maxAttempts} onChange={(e) => setMaxAttempts(e.target.value)} className={inputClass} />
          </div>
        )}
      </div>

      <div className="flex justify-end gap-2 pt-2">
        <Button type="submit" variant="primary" size="sm" disabled={submitting}>
          {submitting ? t("common.creating") : t("assignModal.infoStep.submit")}
        </Button>
      </div>
    </form>
  );
}

type QuestionSourceMode = "compose" | "import";

interface AttachedQuestion {
  /** id của ExerciseQuestion (dòng gắn câu hỏi vào đề) — dùng để sửa điểm qua updateExerciseQuestionPoints, KHÔNG phải id của Question trong ngân hàng. */
  exerciseQuestionId: number;
  content: string;
  points: number;
}

/**
 * UC-40 Main Flow bước 1: nguồn câu hỏi — soạn câu hỏi mới / nhập hàng loạt Excel-Word. V84 (bổ sung
 * ngoài SDD gốc, đã xác nhận với người dùng 2026-08-05): bỏ hẳn tab "Chọn có sẵn" — dù V75 (merge từ
 * develop) đổi ý nghĩa thành "câu hỏi đã có trong chính Đề này" (khác ý nghĩa V77 cũ là duyệt ngân
 * hàng theo khung chương trình), test thực tế cho thấy vẫn gây rối cho luồng soạn Bài ĐẦU TIÊN của 1
 * Đề (Đề trống → tab mặc định hiện "chưa có câu hỏi", phải tự chuyển tab). "Sửa câu hỏi" cho câu ĐÃ
 * gắn vào Bài vẫn làm được bình thường ở trang Soạn & giao đề (ExerciseAssignPage — click mở rộng 1
 * Bài, có nút bút chì riêng), không mất tính năng.
 */
/**
 * Xuất công khai để tái dùng ở ExerciseAssignPage.tsx — "+ Thêm câu hỏi" cho 1 Bài ĐÃ TỒN TẠI (bổ
 * sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-04, vá gap: trước đây soạn xong 1 Bài rồi
 * đóng modal thì không còn cách nào gắn thêm câu hỏi nữa).
 */
export function ExerciseQuestionsStep({
  exercise,
  teacherType,
  onDone,
  onError,
  onClose
}: {
  exercise: ExerciseResponse;
  teacherType: ExamTeacherType;
  onDone: () => void;
  onError: (message: string | null) => void;
  onClose: () => void;
}) {
  const { t } = useTranslation("lms-question-authoring");
  // V78: VIETNAMESE trong tab "Soạn câu hỏi mới" có thêm lựa chọn "Bài đọc hiểu — Lưới" (composite
  // nhiều câu hỏi/1 lần, xem GridQuestionBuilder) bên cạnh soạn từng câu đơn (QuestionEditorForm).
  // Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-06 — FOREIGN có thêm "listeningGroup"
  // (ListeningGroupBuilder): 1 audio dùng chung cho nhiều câu hỏi (Trắc nghiệm Voice/Nghe & nộp
  // audio/Nghe điền từ), song song với soạn 1 câu/1 audio như cũ.
  // Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-26 — VIETNAMESE có thêm "cloze"
  // (ClozeQuestionBuilder): "Đọc điền từ" — 1 đoạn văn dùng chung + N chỗ trống, MỖI chỗ trống có bộ
  // đáp án riêng (khác "grid" — ở đó mọi câu dùng CHUNG 1 bộ đáp án).
  //
  // Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-26 — danh sách mode/kind hợp lệ giờ tra
  // theo (teacherType, exercise.skillCategory) từ skillCategoryKinds.ts thay vì hard-code đủ 3/2 lựa
  // chọn như trước (VOCAB_GRAMMAR/WRITING chỉ còn "single", READING chỉ còn "cloze"/"grid").
  const composeModes: ComposeSubMode[] =
    teacherType === "FOREIGN" ? FOREIGN_LISTENING_MODES : VIETNAMESE_SKILL_MODES[exercise.skillCategory as VietnameseSkillCategory];
  const allowedKinds =
    teacherType === "FOREIGN"
      ? FOREIGN_LISTENING_KINDS
      : (VIETNAMESE_SKILL_KINDS[exercise.skillCategory as "VOCAB_GRAMMAR" | "WRITING"] ?? []);
  /**
   * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-26 — tab "Nhập Excel/Word" giờ bật cho
   * cả 2 loại GV (trước đây FOREIGN chỉ có "compose"), NHƯNG ẩn hẳn khi Nhóm kỹ năng không có loại
   * nào import được (READING — Cloze/Grid chỉ là composite, chưa từng import được, xem
   * QuestionImportService.java) để tránh hiện 1 tab rỗng vô nghĩa.
   */
  const availableModes: QuestionSourceMode[] = allowedKinds.length > 0 ? ["import", "compose"] : ["compose"];
  const [mode, setMode] = useState<QuestionSourceMode>(availableModes[0]);
  const [composeSubMode, setComposeSubMode] = useState<ComposeSubMode>(composeModes[0]);
  // Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-04 — Bài có thể ĐÃ có sẵn câu hỏi (mở
  // lại để thêm tiếp, không chỉ lúc soạn mới) — cần biết số câu đã có để displayOrder câu mới không
  // trùng câu cũ (trước đây luôn giả định Bài trống, bắt đầu từ 1).
  const [existingCount, setExistingCount] = useState(0);
  // Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-18 — tổng điểm câu hỏi ĐÃ có sẵn (mở
  // lại Bài để gắn thêm), dùng cùng existingCount để tính tổng điểm hiện tại so với exercise.totalPoints.
  const [existingPoints, setExistingPoints] = useState(0);

  useEffect(() => {
    listExerciseQuestions(exercise.id)
      .then((res) => {
        setExistingCount(res.length);
        setExistingPoints(res.reduce((sum, q) => sum + q.points, 0));
      })
      .catch(() => undefined);
  }, [exercise.id]);
  // Câu hỏi soạn mới/import hàng loạt được gắn vào đề NGAY khi tạo xong (đã ghi thật vào ngân hàng
  // câu hỏi) — không thể "bỏ chọn" được nữa, chỉ có thể gỡ lại ở trang Soạn & giao đề.
  const [attached, setAttached] = useState<AttachedQuestion[]>([]);
  const [composeFormKey, setComposeFormKey] = useState(0);

  const handleComposeCreated = async (question: QuestionResponse) => {
    onError(null);
    try {
      const points = question.defaultPoints ?? 0;
      const eq = await addExerciseQuestion(exercise.id, { questionId: question.id, displayOrder: existingCount + attached.length + 1, points });
      setAttached((prev) => [...prev, { exerciseQuestionId: eq.id, content: question.content, points: eq.points }]);
      setComposeFormKey((k) => k + 1); // remount QuestionEditorForm rỗng để soạn tiếp câu khác
    } catch (err) {
      onError(err instanceof ApiError ? err.message : t("assignModal.questionsStep.errors.attachComposedFailed"));
    }
  };

  /**
   * V78/V85 — dùng chung cho mọi composite builder tạo N Question/1 lần (GridQuestionBuilder "Đọc
   * hiểu — Lưới" lẫn ListeningGroupBuilder "1 audio nhiều câu"): gắn hết vào đề rồi quay lại soạn đơn.
   */
  const handleCompositeCreated = async (createdQuestions: QuestionResponse[]) => {
    onError(null);
    try {
      let order = existingCount + attached.length;
      const newlyAttached: AttachedQuestion[] = [];
      for (const q of createdQuestions) {
        order += 1;
        const points = q.defaultPoints ?? 0;
        const eq = await addExerciseQuestion(exercise.id, { questionId: q.id, displayOrder: order, points });
        newlyAttached.push({ exerciseQuestionId: eq.id, content: q.content, points: eq.points });
      }
      setAttached((prev) => [...prev, ...newlyAttached]);
      setComposeSubMode(composeModes[0]);
    } catch (err) {
      onError(err instanceof ApiError ? err.message : t("assignModal.questionsStep.errors.attachCompositeFailed"));
    }
  };

  const handleImportCompleted = async (createdQuestions: QuestionImportedRow[]) => {
    onError(null);
    try {
      let order = existingCount + attached.length;
      const newlyAttached: AttachedQuestion[] = [];
      for (const q of createdQuestions) {
        order += 1;
        const eq = await addExerciseQuestion(exercise.id, { questionId: q.id, displayOrder: order, points: q.defaultPoints });
        newlyAttached.push({ exerciseQuestionId: eq.id, content: q.content, points: eq.points });
      }
      setAttached((prev) => [...prev, ...newlyAttached]);
    } catch (err) {
      onError(err instanceof ApiError ? err.message : t("assignModal.questionsStep.errors.attachImportedFailed"));
    }
  };

  /** Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-18 — sửa điểm 1 câu đã gắn; backend tự chặn nếu tổng điểm vượt exercise.totalPoints. */
  const handlePointsChange = async (exerciseQuestionId: number, newPoints: number) => {
    onError(null);
    try {
      const eq = await updateExerciseQuestionPoints(exercise.id, exerciseQuestionId, newPoints);
      setAttached((prev) => prev.map((a) => (a.exerciseQuestionId === exerciseQuestionId ? { ...a, points: eq.points } : a)));
    } catch (err) {
      onError(err instanceof ApiError ? err.message : "Sửa điểm câu hỏi thất bại.");
    }
  };

  const totalAttachedPoints = existingPoints + attached.reduce((sum, a) => sum + a.points, 0);
  const overTotalPoints = totalAttachedPoints > exercise.totalPoints;

  const handleContinue = () => {
    onError(null);
    if (attached.length === 0 && existingCount === 0) {
      onError(t("assignModal.questionsStep.errors.attachAtLeastOne"));
      return;
    }
    onDone();
  };

  const modeLabels: Record<QuestionSourceMode, string> = {
    compose: t("assignModal.questionsStep.modeCompose"),
    import: t("assignModal.questionsStep.modeImport")
  };

  return (
    <div className="space-y-3">
      {exercise.hasEssayOrSpeaking && (
        <div className="text-[11px] text-amber-700 bg-amber-50 border border-amber-100 p-2.5 rounded-lg">
          {t("assignModal.questionsStep.essayOrSpeakingWarning")}
        </div>
      )}

      {/* {teacherType === "FOREIGN" && (
        <p className="text-[11px] text-slate-400 italic">
          {t("assignModal.questionsStep.foreignReflexPart1")}{" "}
          <Link to="/lms/lectures" target="_blank" rel="noreferrer" className="text-brand-red font-bold hover:underline">
            {t("assignModal.questionsStep.foreignReflexLinkLabel")}
          </Link>{" "}
          {t("assignModal.questionsStep.foreignReflexPart2")}
        </p>
      )} */}

      <div className="flex items-center gap-1.5 bg-slate-100 p-1 rounded-lg w-fit">
        {availableModes.map((m) => (
          <button
            key={m}
            type="button"
            onClick={() => setMode(m)}
            className={`text-[11px] font-bold px-3 py-1.5 rounded-md transition-all ${
              mode === m ? "bg-white text-brand-red shadow-xs" : "text-slate-500 hover:text-slate-700"
            }`}
          >
            {modeLabels[m]}
          </button>
        ))}
      </div>

      {/* Toggle chỉ hiện khi Nhóm kỹ năng có >1 mode hợp lệ (VOCAB_GRAMMAR/WRITING chỉ có "single" nên
          ẩn hẳn toggle; READING chọn giữa Cloze/Grid; FOREIGN/LISTENING chọn giữa 1 câu và 1 audio
          nhiều câu — y hệt hành vi cũ, chỉ đổi nguồn danh sách sang composeModes). */}
      {mode === "compose" && composeModes.length > 1 && (
        <div className="flex items-center gap-1.5 bg-slate-100 p-1 rounded-lg w-fit">
          {composeModes.map((m) => (
            <button
              key={m}
              type="button"
              onClick={() => setComposeSubMode(m)}
              className={`text-[11px] font-bold px-3 py-1.5 rounded-md transition-all ${
                composeSubMode === m ? "bg-white text-brand-red shadow-xs" : "text-slate-500 hover:text-slate-700"
              }`}
            >
              {m === "single"
                ? t("assignModal.questionsStep.subModeSingle")
                : m === "grid"
                  ? t("assignModal.questionsStep.subModeGrid")
                  : m === "cloze"
                    ? t("assignModal.questionsStep.subModeCloze")
                    : m === "fillInBlankGroup"
                      ? t("assignModal.questionsStep.subModeFillInBlankGroup")
                      : t("assignModal.questionsStep.subModeListeningGroup")}
            </button>
          ))}
        </div>
      )}

      {mode === "compose" &&
        (composeSubMode === "grid" && teacherType === "VIETNAMESE" ? (
          <GridQuestionBuilder examId={exercise.examId} onCreated={handleCompositeCreated} onCancel={() => setComposeSubMode(composeModes[0])} />
        ) : composeSubMode === "cloze" && teacherType === "VIETNAMESE" ? (
          <ClozeQuestionBuilder examId={exercise.examId} onCreated={handleCompositeCreated} onCancel={() => setComposeSubMode(composeModes[0])} />
        ) : composeSubMode === "listeningGroup" && teacherType === "FOREIGN" ? (
          <ListeningGroupBuilder examId={exercise.examId} onCreated={handleCompositeCreated} onCancel={() => setComposeSubMode(composeModes[0])} />
        ) : composeSubMode === "fillInBlankGroup" && teacherType === "VIETNAMESE" ? (
          <FillInBlankGroupBuilder examId={exercise.examId} onCreated={handleCompositeCreated} onCancel={() => setComposeSubMode(composeModes[0])} />
        ) : (
          <QuestionEditorForm
            key={composeFormKey}
            examId={exercise.examId}
            allowedKinds={allowedKinds}
            onCreated={handleComposeCreated}
            onCancel={() => setMode(availableModes[0])}
          />
        ))}

      {mode === "import" && (
        <QuestionImportPanel
          examId={exercise.examId}
          skillCategory={exercise.skillCategory ?? undefined}
          teacherType={teacherType}
          onImported={handleImportCompleted}
        />
      )}

      {attached.length > 0 && (
        <div className="border border-emerald-100 bg-emerald-50/50 rounded-lg divide-y divide-emerald-100 max-h-40 overflow-y-auto">
          <div className="px-3 py-1.5 text-[10px] font-bold text-emerald-700 uppercase">{t("assignModal.questionsStep.attachedSectionTitle")}</div>
          {attached.map((a) => (
            <div key={a.exerciseQuestionId} className="px-3 py-1.5 text-xs flex items-center justify-between gap-2">
              <span className="flex-1 truncate">{a.content}</span>
              <input
                key={`${a.exerciseQuestionId}-${a.points}`}
                type="number"
                min={0}
                step="0.5"
                defaultValue={a.points}
                onBlur={(e) => {
                  const value = Number(e.target.value);
                  if (!Number.isNaN(value) && value !== a.points) handlePointsChange(a.exerciseQuestionId, value);
                }}
                title={t("assignModal.questionsStep.pointsInputTitle")}
                className="w-16 text-[11px] text-right text-slate-600 border border-slate-200 rounded px-1 py-0.5 shrink-0 focus:outline-none"
              />
            </div>
          ))}
        </div>
      )}

      <div className="flex justify-between items-center pt-2">
        <span className="text-[11px] text-slate-500">{t("assignModal.questionsStep.attachedCount", { count: existingCount + attached.length })}</span>
        <span className={`text-[11px] font-bold ${overTotalPoints ? "text-red-600" : "text-slate-500"}`}>
          {t("assignModal.questionsStep.totalPoints", { total: totalAttachedPoints, max: exercise.totalPoints })}
        </span>
        <Button type="button" variant="primary" size="sm" onClick={handleContinue} disabled={attached.length === 0 && existingCount === 0}>
          {t("assignModal.questionsStep.continue")}
        </Button>
      </div>
    </div>
  );
}

/**
 * V65: bước cuối chỉ còn Publish (đánh dấu Bài "đủ điều kiện dùng làm nguồn") hoặc để DRAFT publish
 * sau — không còn chọn lớp/hạn nộp/target students ở đây. Giao bài thật (tự động cho cả lớp, hạn nộp
 * = buổi kế tiếp) chỉ xảy ra khi Giáo viên chọn Bài này ở "BTVN buổi sau" trong Nhận xét học viên.
 */
function ExercisePublishStep({
  exercise,
  onDone,
  onError
}: {
  exercise: ExerciseResponse;
  onDone: () => void;
  onError: (message: string | null) => void;
}) {
  const { t } = useTranslation("lms-question-authoring");
  const [submitting, setSubmitting] = useState(false);

  const handlePublish = async () => {
    onError(null);
    setSubmitting(true);
    try {
      await publishExercise(exercise.id);
      onDone();
    } catch (err) {
      onError(err instanceof ApiError ? err.message : t("assignModal.publishStep.errors.publishFailed"));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="space-y-4">
      <div className="bg-emerald-50 border border-emerald-100 rounded-xl p-4 flex items-start gap-3">
        <CheckCircle2 className="w-5 h-5 text-emerald-600 shrink-0 mt-0.5" />
        <div className="text-xs text-emerald-800">
          <p className="font-bold">{t("assignModal.publishStep.readyBanner", { title: exercise.title, code: exercise.code })}</p>
          <p className="mt-1 text-emerald-700">
            {t("assignModal.publishStep.readyDescriptionPart1")}{" "}
            <strong>{t("assignModal.publishStep.readyDescriptionStrong")}</strong>{" "}
            {t("assignModal.publishStep.readyDescriptionPart2")}
          </p>
        </div>
      </div>

      <div className="flex justify-end gap-2 pt-2">
        <Button type="button" variant="secondary" size="sm" onClick={onDone} disabled={submitting}>
          {t("assignModal.publishStep.draftLater")}
        </Button>
        <Button type="button" variant="primary" size="sm" onClick={handlePublish} disabled={submitting}>
          {submitting ? t("assignModal.publishStep.publishing") : t("assignModal.publishStep.publishNow")}
        </Button>
      </div>
    </div>
  );
}
