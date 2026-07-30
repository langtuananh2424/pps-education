import React, { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { Search } from "lucide-react";
import { ApiError } from "@/lib/apiClient";
import Modal from "@/components/ui/Modal";
import Button from "@/components/ui/Button";
import { ClassEnrollmentResponse, ClassResponse, getClass, listClassEnrollments } from "@/features/academic/api";
import {
  AssignExerciseRequest,
  ExerciseResponse,
  QuestionBankResponse,
  QuestionImportedRow,
  QuestionResponse,
  addExerciseQuestion,
  assignExercise,
  createExercise,
  listQuestionBanksByCurriculum,
  listQuestions
} from "../api";
import Select from "@/components/ui/Select";
import QuestionEditorForm from "./QuestionEditorForm";
import QuestionImportPanel from "./QuestionImportPanel";

const inputClass = "w-full bg-slate-50 border border-slate-200 text-xs p-2.5 rounded-lg focus:outline-none";
const labelClass = "text-[10px] uppercase font-bold text-slate-500 block mb-1";

type Step = "info" | "questions" | "assign";

interface SelectedQuestion {
  question: QuestionResponse;
  points: number;
}

interface CreateAndAssignExerciseModalProps {
  classId: number;
  onClose: () => void;
  onAssigned: () => void;
}

/** UC-40 Main Flow bước 1-4: tạo đề mới → gắn câu hỏi → giao ngay cho 1 lớp cố định (classId truyền vào từ trang cha). */
export default function CreateAndAssignExerciseModal({ classId, onClose, onAssigned }: CreateAndAssignExerciseModalProps) {
  const [step, setStep] = useState<Step>("info");
  const [schoolClass, setSchoolClass] = useState<ClassResponse | null>(null);
  const [exercise, setExercise] = useState<ExerciseResponse | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    getClass(classId).then(setSchoolClass).catch(() => undefined);
  }, [classId]);

  return (
    <Modal
      open
      onClose={onClose}
      title="Giao bài tập mới (UC-40)"
      description={schoolClass ? `Lớp: ${schoolClass.name} (${schoolClass.classCode})` : undefined}
      size="lg"
    >
      {error && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg mb-3">{error}</div>}

      {step === "info" && (
        <ExerciseInfoStep
          curriculumId={schoolClass?.curriculumId ?? null}
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
          onDone={() => setStep("assign")}
          onError={setError}
        />
      )}

      {step === "assign" && exercise && (
        <ExerciseAssignStep
          exerciseId={exercise.id}
          classId={classId}
          onAssigned={() => {
            onAssigned();
            onClose();
          }}
          onError={setError}
        />
      )}
    </Modal>
  );
}

function ExerciseInfoStep({
  curriculumId,
  onCreated,
  onError
}: {
  curriculumId: number | null;
  onCreated: (exercise: ExerciseResponse) => void;
  onError: (message: string | null) => void;
}) {
  const [code, setCode] = useState("");
  const [title, setTitle] = useState("");
  const [totalPoints, setTotalPoints] = useState("10");
  const [timeLimitMinutes, setTimeLimitMinutes] = useState("");
  const [allowRetake, setAllowRetake] = useState(false);
  const [maxAttempts, setMaxAttempts] = useState("");
  const [showCorrectAnswers, setShowCorrectAnswers] = useState(true);
  const [submitting, setSubmitting] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    onError(null);
    if (!code.trim() || !title.trim() || !totalPoints) {
      onError("Vui lòng điền Mã đề, Tên đề và Tổng điểm.");
      return;
    }
    setSubmitting(true);
    try {
      const created = await createExercise({
        code: code.trim(),
        title: title.trim(),
        curriculumId: curriculumId ?? undefined,
        exerciseType: "ASSIGNED",
        totalPoints: Number(totalPoints),
        timeLimitMinutes: timeLimitMinutes ? Number(timeLimitMinutes) : undefined,
        allowRetake,
        maxAttempts: allowRetake && maxAttempts ? Number(maxAttempts) : undefined,
        showCorrectAnswers
      });
      onCreated(created);
    } catch (err) {
      onError(err instanceof ApiError ? err.message : "Tạo đề thất bại.");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <form onSubmit={handleSubmit} className="space-y-3">
      <div className="grid grid-cols-2 gap-3">
        <div>
          <label className={labelClass}>Mã đề *</label>
          <input value={code} onChange={(e) => setCode(e.target.value)} className={`${inputClass} font-mono`} />
        </div>
        <div>
          <label className={labelClass}>Tổng điểm *</label>
          <input type="number" min={0} value={totalPoints} onChange={(e) => setTotalPoints(e.target.value)} className={inputClass} />
        </div>
        <div className="col-span-2">
          <label className={labelClass}>Tên đề *</label>
          <input value={title} onChange={(e) => setTitle(e.target.value)} className={inputClass} />
        </div>
        <div>
          <label className={labelClass}>Thời gian làm bài (phút)</label>
          <input type="number" min={0} value={timeLimitMinutes} onChange={(e) => setTimeLimitMinutes(e.target.value)} className={inputClass} />
        </div>
        <div className="flex items-end">
          <label className="flex items-center gap-1.5 text-[11px] font-bold text-slate-600">
            <input type="checkbox" checked={showCorrectAnswers} onChange={(e) => setShowCorrectAnswers(e.target.checked)} />
            Hiện đáp án đúng sau khi nộp (phần trắc nghiệm)
          </label>
        </div>
        <div>
          <label className="flex items-center gap-1.5 text-[11px] font-bold text-slate-600">
            <input type="checkbox" checked={allowRetake} onChange={(e) => setAllowRetake(e.target.checked)} />
            Cho phép làm lại
          </label>
        </div>
        {allowRetake && (
          <div>
            <label className={labelClass}>Số lần làm tối đa</label>
            <input type="number" min={1} value={maxAttempts} onChange={(e) => setMaxAttempts(e.target.value)} className={inputClass} />
          </div>
        )}
      </div>

      <div className="flex justify-end gap-2 pt-2">
        <Button type="submit" variant="primary" size="sm" disabled={submitting}>
          {submitting ? "Đang tạo..." : "Tạo đề — tiếp tục gắn câu hỏi"}
        </Button>
      </div>
    </form>
  );
}

type QuestionSourceMode = "existing" | "compose" | "import";

interface AttachedQuestion {
  id: number;
  content: string;
  points: number;
}

/** UC-40 Main Flow bước 1: 3 nguồn câu hỏi — chọn có sẵn / soạn câu hỏi mới / nhập hàng loạt Excel-Word (bổ sung 2026-07-30, đã xác nhận với người dùng). */
function ExerciseQuestionsStep({
  exercise,
  onDone,
  onError
}: {
  exercise: ExerciseResponse;
  onDone: () => void;
  onError: (message: string | null) => void;
}) {
  const [mode, setMode] = useState<QuestionSourceMode>("existing");
  const [banks, setBanks] = useState<QuestionBankResponse[]>([]);
  const [selectedBankId, setSelectedBankId] = useState<number | null>(null);
  const [bankQuestions, setBankQuestions] = useState<QuestionResponse[]>([]);
  const [searchTerm, setSearchTerm] = useState("");
  const [selected, setSelected] = useState<SelectedQuestion[]>([]);
  const [submitting, setSubmitting] = useState(false);
  // Câu hỏi soạn mới/import hàng loạt được gắn vào đề NGAY khi tạo xong (đã ghi
  // thật vào ngân hàng câu hỏi) — khác "selected" (tab chọn có sẵn) vẫn chỉ là
  // staged, chờ bấm "Tiếp tục" mới thật sự gắn — nên không thể "bỏ chọn" được nữa.
  const [attached, setAttached] = useState<AttachedQuestion[]>([]);
  const [composeFormKey, setComposeFormKey] = useState(0);

  useEffect(() => {
    if (!exercise.curriculumId) return;
    listQuestionBanksByCurriculum(exercise.curriculumId)
      .then((res) => {
        setBanks(res);
        if (res.length > 0) setSelectedBankId(res[0].id);
      })
      .catch(() => undefined);
  }, [exercise.curriculumId]);

  useEffect(() => {
    setSearchTerm("");
    if (!selectedBankId) {
      setBankQuestions([]);
      return;
    }
    listQuestions(selectedBankId).then(setBankQuestions).catch(() => undefined);
  }, [selectedBankId]);

  const filteredQuestions = bankQuestions.filter((q) => {
    const term = searchTerm.trim().toLowerCase();
    return !term || q.content.toLowerCase().includes(term) || String(q.id).includes(term);
  });

  const isSelected = (questionId: number) => selected.some((s) => s.question.id === questionId);

  const toggleQuestion = (question: QuestionResponse) => {
    setSelected((prev) =>
      prev.some((s) => s.question.id === question.id)
        ? prev.filter((s) => s.question.id !== question.id)
        : [...prev, { question, points: question.defaultPoints ?? 0 }]
    );
  };

  const updatePoints = (questionId: number, points: number) => {
    setSelected((prev) => prev.map((s) => (s.question.id === questionId ? { ...s, points } : s)));
  };

  const handleComposeCreated = async (question: QuestionResponse) => {
    onError(null);
    try {
      const points = question.defaultPoints ?? 0;
      await addExerciseQuestion(exercise.id, { questionId: question.id, displayOrder: attached.length + 1, points });
      setAttached((prev) => [...prev, { id: question.id, content: question.content, points }]);
      setComposeFormKey((k) => k + 1); // remount QuestionEditorForm rỗng để soạn tiếp câu khác
    } catch (err) {
      onError(err instanceof ApiError ? err.message : "Gắn câu hỏi vừa soạn vào đề thất bại.");
    }
  };

  const handleImportCompleted = async (createdQuestions: QuestionImportedRow[]) => {
    onError(null);
    try {
      let order = attached.length;
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

  const handleContinue = async () => {
    onError(null);
    if (selected.length === 0 && attached.length === 0) {
      onError("Cần gắn tối thiểu 1 câu hỏi vào đề.");
      return;
    }
    if (selected.length === 0) {
      onDone();
      return;
    }
    setSubmitting(true);
    try {
      for (let i = 0; i < selected.length; i++) {
        await addExerciseQuestion(exercise.id, {
          questionId: selected[i].question.id,
          displayOrder: attached.length + i + 1,
          points: selected[i].points
        });
      }
      onDone();
    } catch (err) {
      onError(err instanceof ApiError ? err.message : "Gắn câu hỏi vào đề thất bại.");
    } finally {
      setSubmitting(false);
    }
  };

  const modeLabels: Record<QuestionSourceMode, string> = {
    existing: "Chọn có sẵn",
    compose: "Soạn câu hỏi mới",
    import: "Nhập Excel/Word"
  };

  return (
    <div className="space-y-3">
      {exercise.hasEssayOrSpeaking && (
        <div className="text-[11px] text-amber-700 bg-amber-50 border border-amber-100 p-2.5 rounded-lg">
          Đề có câu Tự luận/Nói — sau khi học sinh nộp bài sẽ cần chấm tay ở màn "Hàng chờ chấm bài (UC-41)".
        </div>
      )}

      {banks.length === 0 ? (
        <div className="text-xs text-slate-500 bg-slate-50 border border-slate-200 p-3 rounded-lg">
          Khung chương trình này chưa có ngân hàng câu hỏi nào.{" "}
          <Link to="/lms/question-banks" className="text-brand-red font-bold hover:underline">
            Soạn ngân hàng câu hỏi trước
          </Link>{" "}
          rồi quay lại đây để chọn.
        </div>
      ) : (
        <>
          <Select
            value={selectedBankId ?? ""}
            onChange={(e) => setSelectedBankId(e.target.value ? Number(e.target.value) : null)}
            className={inputClass}
          >
            <option value="">-- Chọn ngân hàng câu hỏi --</option>
            {banks.map((b) => (
              <option key={b.id} value={b.id}>
                {b.code} — {b.name}
              </option>
            ))}
          </Select>

          <div className="flex items-center gap-1.5 bg-slate-100 p-1 rounded-lg w-fit">
            {(Object.keys(modeLabels) as QuestionSourceMode[]).map((m) => (
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

          {mode === "existing" && (
            <div className="space-y-3">
              {bankQuestions.length > 0 && (
                <div className="relative">
                  <Search className="absolute left-3 top-2.5 w-3.5 h-3.5 text-slate-400" />
                  <input
                    value={searchTerm}
                    onChange={(e) => setSearchTerm(e.target.value)}
                    placeholder="Tìm theo nội dung câu hỏi, mã ID..."
                    className={`${inputClass} pl-8`}
                  />
                </div>
              )}

              <div className="border border-slate-200 rounded-lg divide-y divide-slate-100 max-h-56 overflow-y-auto">
                {!selectedBankId ? (
                  <p className="text-xs text-slate-400 italic p-3 text-center">Chọn 1 ngân hàng câu hỏi ở trên.</p>
                ) : bankQuestions.length === 0 ? (
                  <p className="text-xs text-slate-500 p-3">
                    Ngân hàng này chưa có câu hỏi nào.{" "}
                    <Link to="/lms/question-banks" className="text-brand-red font-bold hover:underline">
                      Soạn câu hỏi trước
                    </Link>{" "}
                    rồi quay lại đây, hoặc chuyển sang tab "Soạn câu hỏi mới"/"Nhập Excel/Word" ở trên.
                  </p>
                ) : filteredQuestions.length === 0 ? (
                  <p className="text-xs text-slate-400 italic p-3 text-center">Không tìm thấy câu hỏi nào khớp tìm kiếm.</p>
                ) : (
                  filteredQuestions.map((q) => (
                    <label key={q.id} className="flex items-center gap-2 px-3 py-2 text-xs cursor-pointer hover:bg-slate-50">
                      <input type="checkbox" checked={isSelected(q.id)} onChange={() => toggleQuestion(q)} />
                      <span className="flex-1 truncate">{q.content}</span>
                      <span className="text-[10px] text-slate-400 uppercase">{q.questionType}</span>
                      {isSelected(q.id) && (
                        <input
                          type="number"
                          min={0}
                          value={selected.find((s) => s.question.id === q.id)?.points ?? 0}
                          onChange={(e) => updatePoints(q.id, Number(e.target.value))}
                          onClick={(e) => e.stopPropagation()}
                          className="w-16 bg-white border border-slate-200 text-xs p-1 rounded"
                        />
                      )}
                    </label>
                  ))
                )}
              </div>
            </div>
          )}

          {mode === "compose" &&
            (selectedBankId ? (
              <QuestionEditorForm
                key={composeFormKey}
                questionBankId={selectedBankId}
                onCreated={handleComposeCreated}
                onCancel={() => setMode("existing")}
              />
            ) : (
              <p className="text-xs text-slate-400 italic p-3 text-center">Chọn 1 ngân hàng câu hỏi ở trên để soạn câu hỏi mới.</p>
            ))}

          {mode === "import" &&
            (selectedBankId ? (
              <QuestionImportPanel bankId={selectedBankId} onImported={handleImportCompleted} />
            ) : (
              <p className="text-xs text-slate-400 italic p-3 text-center">Chọn 1 ngân hàng câu hỏi ở trên để nhập hàng loạt.</p>
            ))}
        </>
      )}

      {attached.length > 0 && (
        <div className="border border-emerald-100 bg-emerald-50/50 rounded-lg divide-y divide-emerald-100 max-h-40 overflow-y-auto">
          <div className="px-3 py-1.5 text-[10px] font-bold text-emerald-700 uppercase">Đã gắn vào đề (soạn mới/nhập file)</div>
          {attached.map((a) => (
            <div key={a.id} className="px-3 py-1.5 text-xs flex items-center justify-between gap-2">
              <span className="flex-1 truncate">{a.content}</span>
              <span className="text-[10px] text-slate-500 shrink-0">{a.points} đ</span>
            </div>
          ))}
        </div>
      )}

      <div className="flex justify-between items-center pt-2">
        <span className="text-[11px] text-slate-500">
          Đã gắn vào đề: {selected.length + attached.length} câu
        </span>
        <Button type="button" variant="primary" size="sm" onClick={handleContinue} disabled={submitting || selected.length + attached.length === 0}>
          {submitting ? "Đang lưu..." : "Tiếp tục — chọn hạn nộp"}
        </Button>
      </div>
    </div>
  );
}

function ExerciseAssignStep({
  exerciseId,
  classId,
  onAssigned,
  onError
}: {
  exerciseId: number;
  classId: number;
  onAssigned: () => void;
  onError: (message: string | null) => void;
}) {
  const [dueAt, setDueAt] = useState("");
  const [availableFrom, setAvailableFrom] = useState("");
  const [lateSubmissionAllowed, setLateSubmissionAllowed] = useState(false);
  const [latePenaltyPercent, setLatePenaltyPercent] = useState("");
  const [restrictStudents, setRestrictStudents] = useState(false);
  const [enrollments, setEnrollments] = useState<ClassEnrollmentResponse[]>([]);
  const [targetStudentIds, setTargetStudentIds] = useState<Set<number>>(new Set());
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    if (!restrictStudents) return;
    listClassEnrollments(classId).then(setEnrollments).catch(() => undefined);
  }, [restrictStudents, classId]);

  const toggleStudent = (studentId: number) => {
    setTargetStudentIds((prev) => {
      const next = new Set(prev);
      if (next.has(studentId)) next.delete(studentId);
      else next.add(studentId);
      return next;
    });
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    onError(null);
    if (!dueAt) {
      onError("Vui lòng chọn Hạn nộp.");
      return;
    }
    if (restrictStudents && targetStudentIds.size === 0) {
      onError("Đã chọn 'chỉ giao 1 số học sinh' — vui lòng tích chọn tối thiểu 1 học sinh.");
      return;
    }
    setSubmitting(true);
    try {
      const request: AssignExerciseRequest = {
        classId,
        dueAt: new Date(dueAt).toISOString(),
        availableFrom: availableFrom ? new Date(availableFrom).toISOString() : undefined,
        lateSubmissionAllowed,
        latePenaltyPercent: lateSubmissionAllowed && latePenaltyPercent ? Number(latePenaltyPercent) : undefined,
        targetStudentIds: restrictStudents ? Array.from(targetStudentIds) : undefined
      };
      await assignExercise(exerciseId, request);
      onAssigned();
    } catch (err) {
      onError(err instanceof ApiError ? err.message : "Giao bài tập thất bại.");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <form onSubmit={handleSubmit} className="space-y-3">
      <div className="grid grid-cols-2 gap-3">
        <div>
          <label className={labelClass}>Hạn nộp *</label>
          <input type="datetime-local" value={dueAt} onChange={(e) => setDueAt(e.target.value)} className={inputClass} />
        </div>
        <div>
          <label className={labelClass}>Mở bài từ (không bắt buộc, mặc định ngay)</label>
          <input type="datetime-local" value={availableFrom} onChange={(e) => setAvailableFrom(e.target.value)} className={inputClass} />
        </div>
      </div>

      <label className="flex items-center gap-1.5 text-[11px] font-bold text-slate-600">
        <input type="checkbox" checked={lateSubmissionAllowed} onChange={(e) => setLateSubmissionAllowed(e.target.checked)} />
        Cho phép nộp muộn
      </label>
      {lateSubmissionAllowed && (
        <div className="w-1/2">
          <label className={labelClass}>Phần trăm trừ điểm khi nộp muộn</label>
          <input type="number" min={0} max={100} value={latePenaltyPercent} onChange={(e) => setLatePenaltyPercent(e.target.value)} className={inputClass} />
        </div>
      )}

      <label className="flex items-center gap-1.5 text-[11px] font-bold text-slate-600">
        <input type="checkbox" checked={restrictStudents} onChange={(e) => setRestrictStudents(e.target.checked)} />
        Chỉ giao cho 1 số học sinh (bỏ trống = giao cả lớp)
      </label>
      {restrictStudents && (
        <div className="border border-slate-200 rounded-lg divide-y divide-slate-100 max-h-48 overflow-y-auto">
          {enrollments
            .filter((en) => en.status === "ACTIVE")
            .map((en) => (
              <label key={en.studentId} className="flex items-center gap-2 px-3 py-2 text-xs cursor-pointer hover:bg-slate-50">
                <input type="checkbox" checked={targetStudentIds.has(en.studentId)} onChange={() => toggleStudent(en.studentId)} />
                <span>{en.studentFullName}</span>
                <span className="text-slate-400 font-mono">{en.studentCode}</span>
              </label>
            ))}
        </div>
      )}

      <div className="flex justify-end pt-2">
        <Button type="submit" variant="primary" size="sm" disabled={submitting}>
          {submitting ? "Đang giao..." : "Giao bài tập"}
        </Button>
      </div>
    </form>
  );
}
