import React, { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { CheckCircle2 } from "lucide-react";
import { ApiError } from "@/lib/apiClient";
import Modal from "@/components/ui/Modal";
import Button from "@/components/ui/Button";
import {
  ExamTeacherType,
  ExerciseResponse,
  QuestionImportedRow,
  QuestionResponse,
  addExerciseQuestion,
  createExercise,
  listExerciseQuestions,
  publishExercise
} from "../api";
import QuestionEditorForm from "./QuestionEditorForm";
import QuestionImportPanel from "./QuestionImportPanel";
import GridQuestionBuilder from "./GridQuestionBuilder";
import ListeningGroupBuilder from "./ListeningGroupBuilder";

const inputClass = "w-full bg-slate-50 border border-slate-200 text-sm p-2.5 rounded-lg focus:outline-none";
const labelClass = "text-sm uppercase font-bold text-slate-500 block mb-1";

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
  const [step, setStep] = useState<Step>("info");
  const [exercise, setExercise] = useState<ExerciseResponse | null>(null);
  const [error, setError] = useState<string | null>(null);

  return (
    <Modal open onClose={onClose} title="Soạn Bài mới" size="lg">
      {error && <div className="text-sm text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg mb-3">{error}</div>}

      {step === "info" && (
        <ExerciseInfoStep
          examId={examId}
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
  onCreated,
  onError
}: {
  examId: number;
  onCreated: (exercise: ExerciseResponse) => void;
  onError: (message: string | null) => void;
}) {
  const [code, setCode] = useState("");
  const [title, setTitle] = useState("");
  const [totalPoints, setTotalPoints] = useState("10");
  const [allowRetake, setAllowRetake] = useState(false);
  const [maxAttempts, setMaxAttempts] = useState("");
  const [showCorrectAnswers, setShowCorrectAnswers] = useState(true);
  const [passThresholdPercent, setPassThresholdPercent] = useState("");
  const [submitting, setSubmitting] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    onError(null);
    if (!code.trim() || !title.trim() || !totalPoints) {
      onError("Vui lòng điền Mã Bài, Tên Bài và Tổng điểm.");
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
        passThresholdPercent: passThresholdPercent ? Number(passThresholdPercent) : undefined
      });
      onCreated(created);
    } catch (err) {
      onError(err instanceof ApiError ? err.message : "Tạo Bài thất bại.");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <form onSubmit={handleSubmit} className="space-y-3">
      <div className="grid grid-cols-2 gap-3">
        <div>
          <label className={labelClass}>Mã Bài *</label>
          <input value={code} onChange={(e) => setCode(e.target.value)} className={`${inputClass} font-mono`} />
        </div>
        <div>
          <label className={labelClass}>Tổng điểm *</label>
          <input type="number" min={0} value={totalPoints} onChange={(e) => setTotalPoints(e.target.value)} className={inputClass} />
        </div>
        <div className="col-span-2">
          <label className={labelClass}>Tên Bài *</label>
          <input value={title} onChange={(e) => setTitle(e.target.value)} className={inputClass} />
        </div>
        <div className="col-span-2">
          <label className={labelClass}>Ngưỡng đạt (%)</label>
          <input
            type="number"
            min={0}
            max={100}
            placeholder="Mặc định 70"
            value={passThresholdPercent}
            onChange={(e) => setPassThresholdPercent(e.target.value)}
            className={inputClass}
          />
        </div>
        <div>
          <label className="flex items-center gap-1.5 text-sm font-bold text-slate-600">
            <input type="checkbox" checked={showCorrectAnswers} onChange={(e) => setShowCorrectAnswers(e.target.checked)} />
            Hiện đáp án đúng sau khi nộp (phần trắc nghiệm)
          </label>
        </div>
        <div>
          <label className="flex items-center gap-1.5 text-sm font-bold text-slate-600">
            <input type="checkbox" checked={allowRetake} onChange={(e) => setAllowRetake(e.target.checked)} />
            Cho phép làm lại
          </label>
        </div>
        {allowRetake && (
          <div className="col-span-2">
            <label className={labelClass}>Số lần làm tối đa</label>
            <input type="number" min={1} value={maxAttempts} onChange={(e) => setMaxAttempts(e.target.value)} className={inputClass} />
          </div>
        )}
      </div>

      <div className="flex justify-end gap-2 pt-2">
        <Button type="submit" variant="primary" size="sm" disabled={submitting}>
          {submitting ? "Đang tạo..." : "Tạo Bài — tiếp tục gắn câu hỏi"}
        </Button>
      </div>
    </form>
  );
}

type QuestionSourceMode = "compose" | "import";

interface AttachedQuestion {
  id: number;
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
  const availableModes: QuestionSourceMode[] = teacherType === "FOREIGN" ? ["compose"] : ["import", "compose"];
  const [mode, setMode] = useState<QuestionSourceMode>(availableModes[0]);
  // V78: VIETNAMESE trong tab "Soạn câu hỏi mới" có thêm lựa chọn "Bài đọc hiểu — Lưới" (composite
  // nhiều câu hỏi/1 lần, xem GridQuestionBuilder) bên cạnh soạn từng câu đơn (QuestionEditorForm).
  // Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-06 — FOREIGN có thêm "listeningGroup"
  // (ListeningGroupBuilder): 1 audio dùng chung cho nhiều câu hỏi (Trắc nghiệm Voice/Nghe & nộp
  // audio/Nghe điền từ), song song với soạn 1 câu/1 audio như cũ.
  const [composeSubMode, setComposeSubMode] = useState<"single" | "grid" | "listeningGroup">("single");
  // Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-04 — Bài có thể ĐÃ có sẵn câu hỏi (mở
  // lại để thêm tiếp, không chỉ lúc soạn mới) — cần biết số câu đã có để displayOrder câu mới không
  // trùng câu cũ (trước đây luôn giả định Bài trống, bắt đầu từ 1).
  const [existingCount, setExistingCount] = useState(0);

  useEffect(() => {
    listExerciseQuestions(exercise.id)
      .then((res) => setExistingCount(res.length))
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
      await addExerciseQuestion(exercise.id, { questionId: question.id, displayOrder: existingCount + attached.length + 1, points });
      setAttached((prev) => [...prev, { id: question.id, content: question.content, points }]);
      setComposeFormKey((k) => k + 1); // remount QuestionEditorForm rỗng để soạn tiếp câu khác
    } catch (err) {
      onError(err instanceof ApiError ? err.message : "Gắn câu hỏi vừa soạn vào đề thất bại.");
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
        await addExerciseQuestion(exercise.id, { questionId: q.id, displayOrder: order, points });
        newlyAttached.push({ id: q.id, content: q.content, points });
      }
      setAttached((prev) => [...prev, ...newlyAttached]);
      setComposeSubMode("single");
    } catch (err) {
      onError(err instanceof ApiError ? err.message : "Gắn các câu hỏi vừa soạn vào đề thất bại.");
    }
  };

  const handleImportCompleted = async (createdQuestions: QuestionImportedRow[]) => {
    onError(null);
    try {
      let order = existingCount + attached.length;
      const newlyAttached: AttachedQuestion[] = [];
      for (const q of createdQuestions) {
        order += 1;
        await addExerciseQuestion(exercise.id, { questionId: q.id, displayOrder: order, points: q.defaultPoints });
        newlyAttached.push({ id: q.id, content: q.content, points: q.defaultPoints });
      }
      setAttached((prev) => [...prev, ...newlyAttached]);
    } catch (err) {
      onError(err instanceof ApiError ? err.message : "Gắn câu hỏi vừa nhập vào đề thất bại.");
    }
  };

  const handleContinue = () => {
    onError(null);
    if (attached.length === 0 && existingCount === 0) {
      onError("Cần gắn tối thiểu 1 câu hỏi vào đề.");
      return;
    }
    onDone();
  };

  const modeLabels: Record<QuestionSourceMode, string> = {
    compose: "Soạn câu hỏi mới",
    import: "Nhập Excel/Word"
  };

  return (
    <div className="space-y-3">
      {exercise.hasEssayOrSpeaking && (
        <div className="text-sm text-amber-700 bg-amber-50 border border-amber-100 p-2.5 rounded-lg">
          Đề có câu Tự luận/Nói — sau khi học sinh nộp bài sẽ cần chấm tay ở màn "Hàng chờ chấm bài".
        </div>
      )}

      {teacherType === "FOREIGN" && (
        <p className="text-sm text-slate-400 italic">
          Cần giao Video phản xạ? Vào{" "}
          <Link to="/lms/lectures" target="_blank" rel="noreferrer" className="text-brand-red font-bold hover:underline">
            Kho Video Ôn tập
          </Link>{" "}
          — tạo/giao lớp trực tiếp ở đó, giống Video kết nối.
        </p>
      )}

      <div className="flex items-center gap-1.5 bg-slate-100 p-1 rounded-lg w-fit">
        {availableModes.map((m) => (
          <button
            key={m}
            type="button"
            onClick={() => setMode(m)}
            className={`text-sm font-bold px-3 py-1.5 rounded-md transition-all ${
              mode === m ? "bg-white text-brand-red shadow-xs" : "text-slate-500 hover:text-slate-700"
            }`}
          >
            {modeLabels[m]}
          </button>
        ))}
      </div>

      {mode === "compose" && teacherType === "VIETNAMESE" && (
        <div className="flex items-center gap-1.5 bg-slate-100 p-1 rounded-lg w-fit">
          {(["single", "grid"] as const).map((m) => (
            <button
              key={m}
              type="button"
              onClick={() => setComposeSubMode(m)}
              className={`text-sm font-bold px-3 py-1.5 rounded-md transition-all ${
                composeSubMode === m ? "bg-white text-brand-red shadow-xs" : "text-slate-500 hover:text-slate-700"
              }`}
            >
              {m === "single" ? "Câu hỏi đơn" : "Bài đọc hiểu — Lưới"}
            </button>
          ))}
        </div>
      )}

      {/* Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-06 — FOREIGN chọn giữa soạn 1
          câu/1 audio (như cũ) và "nhiều câu/1 audio" (ListeningGroupBuilder), y hệt tinh thần toggle
          single/grid của VIETNAMESE ở trên. */}
      {mode === "compose" && teacherType === "FOREIGN" && (
        <div className="flex items-center gap-1.5 bg-slate-100 p-1 rounded-lg w-fit">
          {(["single", "listeningGroup"] as const).map((m) => (
            <button
              key={m}
              type="button"
              onClick={() => setComposeSubMode(m)}
              className={`text-sm font-bold px-3 py-1.5 rounded-md transition-all ${
                composeSubMode === m ? "bg-white text-brand-red shadow-xs" : "text-slate-500 hover:text-slate-700"
              }`}
            >
              {m === "single" ? "Câu hỏi đơn" : "Nhiều câu — 1 audio nghe"}
            </button>
          ))}
        </div>
      )}

      {mode === "compose" &&
        (composeSubMode === "grid" && teacherType === "VIETNAMESE" ? (
          <GridQuestionBuilder examId={exercise.examId} onCreated={handleCompositeCreated} onCancel={() => setComposeSubMode("single")} />
        ) : composeSubMode === "listeningGroup" && teacherType === "FOREIGN" ? (
          <ListeningGroupBuilder examId={exercise.examId} onCreated={handleCompositeCreated} onCancel={() => setComposeSubMode("single")} />
        ) : (
          <QuestionEditorForm
            key={composeFormKey}
            examId={exercise.examId}
            allowedKinds={
              // V83 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-04): FOREIGN chọn
              // trực tiếp giữa 2 kind ngay trong kind-picker này — không còn màn chọn riêng trước
              // đó (V78 GV Việt Nam không soạn Speaking oral/"Nghe & nộp audio", 2 kind này chỉ
              // dành cho FOREIGN).
              teacherType === "FOREIGN"
                ? ["VOICE_MULTIPLE_CHOICE", "LISTENING_AUDIO_SUBMISSION", "LISTENING_FILL_IN_BLANK"]
                : ["MULTIPLE_CHOICE", "VOICE_MULTIPLE_CHOICE", "INLINE_CHOICE", "FILL_IN_BLANK", "WORD_BANK", "SENTENCE_BUILDING", "ESSAY"]
            }
            onCreated={handleComposeCreated}
            onCancel={() => setMode(availableModes[0])}
          />
        ))}

      {mode === "import" && <QuestionImportPanel examId={exercise.examId} onImported={handleImportCompleted} />}

      {attached.length > 0 && (
        <div className="border border-emerald-100 bg-emerald-50/50 rounded-lg divide-y divide-emerald-100 max-h-40 overflow-y-auto">
          <div className="px-3 py-1.5 text-sm font-bold text-emerald-700 uppercase">Đã gắn vào đề (soạn mới/nhập file)</div>
          {attached.map((a) => (
            <div key={a.id} className="px-3 py-1.5 text-sm flex items-center justify-between gap-2">
              <span className="flex-1 truncate">{a.content}</span>
              <span className="text-sm text-slate-500 shrink-0">{a.points} đ</span>
            </div>
          ))}
        </div>
      )}

      <div className="flex justify-between items-center pt-2">
        <span className="text-sm text-slate-500">Đã gắn vào Bài: {existingCount + attached.length} câu</span>
        <Button type="button" variant="primary" size="sm" onClick={handleContinue} disabled={attached.length === 0 && existingCount === 0}>
          Tiếp tục
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
  const [submitting, setSubmitting] = useState(false);

  const handlePublish = async () => {
    onError(null);
    setSubmitting(true);
    try {
      await publishExercise(exercise.id);
      onDone();
    } catch (err) {
      onError(err instanceof ApiError ? err.message : "Publish Bài thất bại.");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="space-y-4">
      <div className="bg-emerald-50 border border-emerald-100 rounded-xl p-4 flex items-start gap-3">
        <CheckCircle2 className="w-5 h-5 text-emerald-600 shrink-0 mt-0.5" />
        <div className="text-sm text-emerald-800">
          <p className="font-bold">Bài "{exercise.title}" ({exercise.code}) đã soạn xong.</p>
          <p className="mt-1 text-emerald-700">
            Publish để đánh dấu Bài này <strong>đủ điều kiện dùng làm nguồn</strong> — sau đó Giáo viên chọn Bài này làm
            "BTVN buổi sau" ở Nhận xét học viên (UC-21) sẽ tự động giao cho cả lớp, hạn nộp = buổi học kế tiếp.
            Chưa Publish thì Bài vẫn ở dạng nháp, không chọn được ở Nhận xét.
          </p>
        </div>
      </div>

      <div className="flex justify-end gap-2 pt-2">
        <Button type="button" variant="secondary" size="sm" onClick={onDone} disabled={submitting}>
          Để nháp, publish sau
        </Button>
        <Button type="button" variant="primary" size="sm" onClick={handlePublish} disabled={submitting}>
          {submitting ? "Đang publish..." : "Publish ngay"}
        </Button>
      </div>
    </div>
  );
}
