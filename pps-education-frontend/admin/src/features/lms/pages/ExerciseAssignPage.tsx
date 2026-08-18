import React, { useEffect, useState } from "react";
import { ChevronDown, ChevronRight, ClipboardList, Layers, Pencil, Plus, Trash2, Users, X } from "lucide-react";
import { ApiError } from "@/lib/apiClient";
import { useApp } from "@/context/AppContext";
import { useEligibleClasses } from "@/features/academic/hooks/useEligibleClasses";
import { CurriculumResponse, listCurriculums } from "@/features/academic/api";
import {
  ExamResponse,
  ExamTeacherType,
  ExamType,
  ExerciseQuestionResponse,
  ExerciseResponse,
  QuestionResponse,
  UpdateExamRequest,
  UpdateExerciseRequest,
  assignExamToClass,
  createExam,
  deleteExam,
  deleteExercise,
  getExamQuestion,
  listExamAssignedClasses,
  listExercisesByExam,
  listExerciseQuestions,
  listExams,
  publishExercise,
  removeExerciseQuestion,
  unassignExamFromClass,
  updateExam,
  updateExercise,
  updateExerciseQuestionPoints
} from "../api";
import CreateAndAssignExerciseModal, { ExerciseQuestionsStep } from "../components/CreateAndAssignExerciseModal";
import ExercisePreviewModal from "../components/ExercisePreviewModal";
import QuestionEditorForm from "../components/QuestionEditorForm";
import Button from "@/components/ui/Button";
import Badge from "@/components/ui/Badge";
import Modal from "@/components/ui/Modal";
import Select from "@/components/ui/Select";
import { useToast } from "@/lib/useToast";
import Toast from "@/components/ui/Toast";
import Pagination from "@/components/ui/Pagination";
import { useDialog } from "@/components/ui/DialogProvider";

const inputClass = "w-full bg-slate-50 border border-slate-200 text-xs p-2.5 rounded-lg focus:outline-none";
const labelClass = "text-[10px] uppercase font-bold text-slate-500 block mb-1";

const statusLabels: Record<ExerciseResponse["status"], string> = { DRAFT: "Nháp", PUBLISHED: "Đã publish", ARCHIVED: "Đã gỡ" };
const statusVariants: Record<ExerciseResponse["status"], "neutral" | "success" | "danger"> = {
  DRAFT: "neutral",
  PUBLISHED: "success",
  ARCHIVED: "danger"
};

/** V74, đã xác nhận với người dùng 2026-08-04: bắt buộc chọn khi tạo/sửa Đề, dùng để lọc khi giao bài. */
const teacherTypeLabels: Record<ExamTeacherType, string> = { VIETNAMESE: "Giáo viên Việt Nam", FOREIGN: "Giáo viên nước ngoài" };
const examTypeLabels: Record<ExamType, string> = { REVIEW: "Ôn tập", HOMEWORK: "Bài tập về nhà" };

/**
 * Kho đề (UC-40, bổ sung ngoài SDD gốc, đã xác nhận với người dùng
 * 2026-07-30) — tái cấu trúc "Soạn & Giao đề" thành 2 cấp: "Đề" (Exam, VD
 * IELTS Grade 6 — gán 1 khung chương trình CHỈ để lọc/tìm kiếm, gán được
 * NHIỀU lớp) chứa nhiều "Bài" (Exercise, VD Unit 1 — giữ nguyên hạ tầng
 * câu hỏi/soạn đề nhanh/chấm bài). Đề gán lớp là điều kiện hiển thị DUY
 * NHẤT cho học sinh — giao bài thật (tự động cho cả lớp, hạn nộp = buổi
 * kế tiếp) chỉ xảy ra khi Giáo viên chọn 1 Bài đã Publish làm "BTVN buổi
 * sau" ở Nhận xét học viên (UC-21).
 */
export default function ExerciseAssignPage() {
  const { hasPermission } = useApp();
  const canManage = hasPermission("lms.exam.create");

  const [curriculums, setCurriculums] = useState<CurriculumResponse[]>([]);
  const [curriculumFilter, setCurriculumFilter] = useState<number | null>(null);
  const [teacherTypeFilter, setTeacherTypeFilter] = useState<ExamTeacherType | null>(null);
  const [exams, setExams] = useState<ExamResponse[]>([]);
  const [loadingExams, setLoadingExams] = useState(false);
  const [selectedExamId, setSelectedExamId] = useState<number | null>(null);
  const [createExamOpen, setCreateExamOpen] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const { message: toastMessage, showToast } = useToast();

  useEffect(() => {
    listCurriculums().then(setCurriculums).catch(() => undefined);
  }, []);

  const loadExams = () => {
    setLoadingExams(true);
    listExams(curriculumFilter ?? undefined, teacherTypeFilter ?? undefined)
      .then((res) => {
        setExams(res);
        if (!res.some((e) => e.id === selectedExamId)) setSelectedExamId(res[0]?.id ?? null);
      })
      .catch((err) => setError(err instanceof ApiError ? err.message : "Không tải được danh sách Đề."))
      .finally(() => setLoadingExams(false));
  };

  // eslint-disable-next-line react-hooks/exhaustive-deps
  useEffect(loadExams, [curriculumFilter, teacherTypeFilter]);

  const selectedExam = exams.find((e) => e.id === selectedExamId) ?? null;

  // "Kho đề" toàn khung chương trình có thể tăng lên hàng trăm Đề theo thời gian — backend GET
  // /exams chưa hỗ trợ phân trang, phân trang phía client. Reset về trang 1 mỗi khi đổi bộ lọc/tải lại.
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(20);
  useEffect(() => setPage(0), [exams]);
  const pageExams = exams.slice(page * pageSize, (page + 1) * pageSize);

  return (
    <div className="space-y-6">
      <div className="border-b border-slate-200 pb-4 flex items-start justify-between flex-wrap gap-3">
        <div>
          <h1 className="text-xl font-bold font-display tracking-tight text-slate-900">Kho đề</h1>
          <p className="text-xs text-slate-500 mt-1">
            Soạn "Đề" (VD IELTS Grade 6) chứa nhiều "Bài" (VD Unit 1), gán Đề cho lớp — Giáo viên chọn Bài đã Publish
            làm "BTVN buổi sau" ở Nhận xét học viên mới thật sự giao cho lớp.
          </p>
        </div>
        {canManage && (
          <Button variant="primary" size="sm" onClick={() => setCreateExamOpen(true)}>
            <Plus className="w-3.5 h-3.5" />
            Tạo Đề mới
          </Button>
        )}
      </div>

      {error && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg">{error}</div>}

      <div className="grid grid-cols-1 lg:grid-cols-12 gap-4">
        <div className="lg:col-span-5 bg-white rounded-xl border border-slate-200 shadow-soft overflow-hidden flex flex-col">
          <div className="px-4 py-3 border-b border-slate-100 bg-slate-50 space-y-2">
            <Select
              value={curriculumFilter ?? ""}
              onChange={(e) => setCurriculumFilter(e.target.value ? Number(e.target.value) : null)}
              className={inputClass}
            >
              <option value="">Tất cả khung chương trình</option>
              {curriculums.map((c) => (
                <option key={c.id} value={c.id}>
                  {c.code} — {c.name}
                </option>
              ))}
            </Select>
            <Select
              value={teacherTypeFilter ?? ""}
              onChange={(e) => setTeacherTypeFilter(e.target.value ? (e.target.value as ExamTeacherType) : null)}
              className={inputClass}
            >
              <option value="">Tất cả loại giáo viên</option>
              {(Object.keys(teacherTypeLabels) as ExamTeacherType[]).map((t) => (
                <option key={t} value={t}>
                  {teacherTypeLabels[t]}
                </option>
              ))}
            </Select>
          </div>

          {loadingExams ? (
            <p className="text-xs text-slate-500 p-6 text-center">Đang tải...</p>
          ) : exams.length === 0 ? (
            <div className="flex flex-col items-center justify-center p-12 text-center text-slate-400 space-y-3">
              <Layers className="w-12 h-12 text-slate-300" />
              <p className="text-xs text-slate-400">Chưa có Đề nào{curriculumFilter ? " trong khung chương trình này" : ""}.</p>
            </div>
          ) : (
            <>
              <div className="divide-y divide-slate-100 overflow-y-auto">
                {pageExams.map((exam) => (
                  <button
                    key={exam.id}
                    onClick={() => setSelectedExamId(exam.id)}
                    className={`w-full text-left px-4 py-3 hover:bg-slate-50/60 ${selectedExamId === exam.id ? "bg-brand-red/5 border-l-2 border-brand-red" : ""}`}
                  >
                    <p className="text-xs font-bold text-slate-800">{exam.title}</p>
                    <p className="text-[10px] text-slate-400 mt-0.5 font-mono">{exam.code} · {exam.curriculumCode}</p>
                    <p className="text-[10px] text-slate-400 mt-0.5">
                      {teacherTypeLabels[exam.teacherType]} · {examTypeLabels[exam.examType]}
                    </p>
                  </button>
                ))}
              </div>
              <Pagination
                page={page}
                pageSize={pageSize}
                totalElements={exams.length}
                itemLabel="Đề"
                onPageChange={setPage}
                onPageSizeChange={(size) => {
                  setPageSize(size);
                  setPage(0);
                }}
              />
            </>
          )}
        </div>

        <div className="lg:col-span-7">
          {!selectedExam ? (
            <div className="bg-white rounded-xl border border-slate-200 shadow-soft flex flex-col items-center justify-center p-12 text-center text-slate-400 space-y-3">
              <ClipboardList className="w-12 h-12 text-slate-300" />
              <p className="text-xs text-slate-400">Chọn 1 Đề bên trái để xem chi tiết, hoặc tạo Đề mới.</p>
            </div>
          ) : (
            <ExamDetailPanel
              exam={selectedExam}
              canManage={canManage}
              showToast={showToast}
              onUpdated={(updated) => setExams((prev) => prev.map((e) => (e.id === updated.id ? updated : e)))}
              onDeleted={loadExams}
            />
          )}
        </div>
      </div>

      {createExamOpen && (
        <CreateExamModal
          curriculums={curriculums}
          onClose={() => setCreateExamOpen(false)}
          onCreated={(exam) => {
            loadExams();
            setSelectedExamId(exam.id);
            showToast("Đã tạo Đề thành công!");
          }}
        />
      )}

      <Toast message={toastMessage} />
    </div>
  );
}

function CreateExamModal({
  curriculums,
  onClose,
  onCreated
}: {
  curriculums: CurriculumResponse[];
  onClose: () => void;
  onCreated: (exam: ExamResponse) => void;
}) {
  const [code, setCode] = useState("");
  const [title, setTitle] = useState("");
  const [curriculumId, setCurriculumId] = useState<number | null>(curriculums[0]?.id ?? null);
  const [teacherType, setTeacherType] = useState<ExamTeacherType | "">("");
  const [examType, setExamType] = useState<ExamType | "">("");
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    if (!code.trim() || !title.trim() || !curriculumId || !teacherType || !examType) {
      setError("Vui lòng điền Mã Đề, Tên Đề, chọn Khung chương trình, Loại giáo viên và Loại đề.");
      return;
    }
    setSubmitting(true);
    try {
      const created = await createExam({ code: code.trim(), title: title.trim(), curriculumId, teacherType, examType });
      onCreated(created);
      onClose();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Tạo Đề thất bại.");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Modal open onClose={onClose} title="Tạo Đề mới" size="md">
      {error && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg mb-3">{error}</div>}
      <form onSubmit={handleSubmit} className="space-y-3">
        <div>
          <label className={labelClass}>Mã Đề *</label>
          <input value={code} onChange={(e) => setCode(e.target.value)} className={`${inputClass} font-mono`} placeholder="VD: IELTS6" />
        </div>
        <div>
          <label className={labelClass}>Tên Đề *</label>
          <input value={title} onChange={(e) => setTitle(e.target.value)} className={inputClass} placeholder="VD: IELTS Grade 6" />
        </div>
        <div>
          <label className={labelClass}>Khung chương trình * (chỉ dùng lọc/tìm kiếm)</label>
          <Select value={curriculumId ?? ""} onChange={(e) => setCurriculumId(e.target.value ? Number(e.target.value) : null)} className={inputClass}>
            <option value="">-- Chọn khung chương trình --</option>
            {curriculums.map((c) => (
              <option key={c.id} value={c.id}>
                {c.code} — {c.name}
              </option>
            ))}
          </Select>
        </div>
        <div>
          <label className={labelClass}>Loại giáo viên * (dùng lọc khi giao bài)</label>
          <Select value={teacherType} onChange={(e) => setTeacherType(e.target.value as ExamTeacherType | "")} className={inputClass}>
            <option value="">-- Chọn loại giáo viên --</option>
            {(Object.keys(teacherTypeLabels) as ExamTeacherType[]).map((t) => (
              <option key={t} value={t}>
                {teacherTypeLabels[t]}
              </option>
            ))}
          </Select>
        </div>
        <div>
          <label className={labelClass}>Loại đề *</label>
          <Select value={examType} onChange={(e) => setExamType(e.target.value as ExamType | "")} className={inputClass}>
            <option value="">-- Chọn loại đề --</option>
            {(Object.keys(examTypeLabels) as ExamType[]).map((t) => (
              <option key={t} value={t}>
                {examTypeLabels[t]}
              </option>
            ))}
          </Select>
        </div>
        <div className="flex justify-end gap-2 pt-2">
          <Button type="submit" variant="primary" size="sm" disabled={submitting}>
            {submitting ? "Đang tạo..." : "Tạo Đề"}
          </Button>
        </div>
      </form>
    </Modal>
  );
}

function EditExamModal({
  exam,
  onClose,
  onUpdated
}: {
  exam: ExamResponse;
  onClose: () => void;
  onUpdated: (exam: ExamResponse) => void;
}) {
  const [title, setTitle] = useState(exam.title);
  const [teacherType, setTeacherType] = useState<ExamTeacherType>(exam.teacherType);
  const [examType, setExamType] = useState<ExamType>(exam.examType);
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!title.trim()) {
      setError("Vui lòng điền Tên Đề.");
      return;
    }
    setSubmitting(true);
    setError(null);
    try {
      const request: UpdateExamRequest = { title: title.trim(), teacherType, examType };
      const updated = await updateExam(exam.id, request);
      onUpdated(updated);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Cập nhật Đề thất bại.");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Modal open onClose={onClose} title={`Sửa Đề — ${exam.code}`} size="md">
      {error && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg mb-3">{error}</div>}
      <form onSubmit={handleSubmit} className="space-y-3">
        <div>
          <label className={labelClass}>Tên Đề *</label>
          <input value={title} onChange={(e) => setTitle(e.target.value)} className={inputClass} placeholder="VD: IELTS Grade 6" />
        </div>
        <div>
          <label className={labelClass}>Loại giáo viên * (dùng lọc khi giao bài)</label>
          <Select value={teacherType} onChange={(e) => setTeacherType(e.target.value as ExamTeacherType)} className={inputClass}>
            {(Object.keys(teacherTypeLabels) as ExamTeacherType[]).map((t) => (
              <option key={t} value={t}>
                {teacherTypeLabels[t]}
              </option>
            ))}
          </Select>
        </div>
        <div>
          <label className={labelClass}>Loại đề *</label>
          <Select value={examType} onChange={(e) => setExamType(e.target.value as ExamType)} className={inputClass}>
            {(Object.keys(examTypeLabels) as ExamType[]).map((t) => (
              <option key={t} value={t}>
                {examTypeLabels[t]}
              </option>
            ))}
          </Select>
        </div>
        <p className="text-[10px] text-slate-400 italic">
          Mã Đề ({exam.code}) và Khung chương trình ({exam.curriculumCode}) không sửa được sau khi tạo.
        </p>
        <div className="flex justify-end gap-2 pt-2">
          <Button type="button" variant="secondary" size="sm" onClick={onClose}>
            Hủy
          </Button>
          <Button type="submit" variant="primary" size="sm" disabled={submitting}>
            {submitting ? "Đang lưu..." : "Lưu thay đổi"}
          </Button>
        </div>
      </form>
    </Modal>
  );
}

/** Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-04 — sửa lại thông tin 1 Bài đã soạn (trước đây chỉ tạo được, không sửa được). */
function EditExerciseModal({
  exercise,
  onClose,
  onUpdated
}: {
  exercise: ExerciseResponse;
  onClose: () => void;
  onUpdated: (exercise: ExerciseResponse) => void;
}) {
  const [title, setTitle] = useState(exercise.title);
  const [totalPoints, setTotalPoints] = useState(String(exercise.totalPoints));
  const [allowRetake, setAllowRetake] = useState(exercise.allowRetake);
  const [maxAttempts, setMaxAttempts] = useState(exercise.maxAttempts != null ? String(exercise.maxAttempts) : "");
  const [showCorrectAnswers, setShowCorrectAnswers] = useState(exercise.showCorrectAnswers);
  const [passThresholdPercent, setPassThresholdPercent] = useState(String(exercise.passThresholdPercent));
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!title.trim() || !totalPoints) {
      setError("Vui lòng điền Tên Bài và Tổng điểm.");
      return;
    }
    setSubmitting(true);
    setError(null);
    try {
      const request: UpdateExerciseRequest = {
        title: title.trim(),
        subjectId: exercise.subjectId ?? undefined,
        totalPoints: Number(totalPoints),
        allowRetake,
        maxAttempts: allowRetake && maxAttempts ? Number(maxAttempts) : undefined,
        showCorrectAnswers,
        passThresholdPercent: passThresholdPercent ? Number(passThresholdPercent) : undefined
      };
      const updated = await updateExercise(exercise.id, request);
      onUpdated(updated);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Cập nhật Bài thất bại.");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Modal open onClose={onClose} title={`Sửa Bài — ${exercise.code}`} size="md">
      {error && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg mb-3">{error}</div>}
      <form onSubmit={handleSubmit} className="space-y-3">
        <div>
          <label className={labelClass}>Tên Bài *</label>
          <input value={title} onChange={(e) => setTitle(e.target.value)} className={inputClass} />
        </div>
        <div>
          <label className={labelClass}>Tổng điểm *</label>
          <input type="number" min={0} value={totalPoints} onChange={(e) => setTotalPoints(e.target.value)} className={inputClass} />
        </div>
        <div>
          <label className={labelClass}>Ngưỡng đạt (%)</label>
          <input
            type="number"
            min={0}
            max={100}
            value={passThresholdPercent}
            onChange={(e) => setPassThresholdPercent(e.target.value)}
            className={inputClass}
          />
        </div>
        <div>
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
        <p className="text-[10px] text-slate-400 italic">Mã Bài ({exercise.code}) không sửa được sau khi tạo.</p>
        <div className="flex justify-end gap-2 pt-2">
          <Button type="button" variant="secondary" size="sm" onClick={onClose}>
            Hủy
          </Button>
          <Button type="submit" variant="primary" size="sm" disabled={submitting}>
            {submitting ? "Đang lưu..." : "Lưu thay đổi"}
          </Button>
        </div>
      </form>
    </Modal>
  );
}

/**
 * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-04 — vá gap: trước đây soạn xong 1 Bài
 * (hoặc đóng modal giữa chừng, tạo Bài "trống" như ảnh chụp người dùng gửi) thì không còn cách nào
 * gắn thêm câu hỏi. Tái dùng nguyên ExerciseQuestionsStep đã có (soạn câu hỏi mới/nhập Excel-Word/
 * lưới đọc hiểu) — V83: không còn cần suy luận/hỏi lại loại câu hỏi FOREIGN ở đây nữa, kind-picker
 * của QuestionEditorForm bên trong ExerciseQuestionsStep đã tự giới hạn đúng 2 kind cho FOREIGN.
 */
function AddQuestionsModal({
  exercise,
  teacherType,
  onClose,
  onDone
}: {
  exercise: ExerciseResponse;
  teacherType: ExamTeacherType;
  onClose: () => void;
  onDone: () => void;
}) {
  const [error, setError] = useState<string | null>(null);

  return (
    <Modal open onClose={onClose} title={`Thêm câu hỏi — ${exercise.code}`} size="lg">
      {error && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg mb-3">{error}</div>}
      <ExerciseQuestionsStep exercise={exercise} teacherType={teacherType} onDone={onDone} onError={setError} onClose={onClose} />
    </Modal>
  );
}

function ExamDetailPanel({
  exam,
  canManage,
  showToast,
  onUpdated,
  onDeleted
}: {
  exam: ExamResponse;
  canManage: boolean;
  showToast: (msg: string) => void;
  onUpdated: (exam: ExamResponse) => void;
  onDeleted: () => void;
}) {
  const [exercises, setExercises] = useState<ExerciseResponse[]>([]);
  const [loadingExercises, setLoadingExercises] = useState(false);
  const [createExerciseOpen, setCreateExerciseOpen] = useState(false);
  const [assignClassOpen, setAssignClassOpen] = useState(false);
  const [editExamOpen, setEditExamOpen] = useState(false);
  const [assignedClassCount, setAssignedClassCount] = useState<number | null>(null);
  const [deletingExam, setDeletingExam] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const { confirmDialog } = useDialog();

  const loadExercises = () => {
    setLoadingExercises(true);
    listExercisesByExam(exam.id)
      .then(setExercises)
      .catch((err) => setError(err instanceof ApiError ? err.message : "Không tải được danh sách Bài."))
      .finally(() => setLoadingExercises(false));
  };

  const loadAssignedClassCount = () => {
    listExamAssignedClasses(exam.id).then((cls) => setAssignedClassCount(cls.length)).catch(() => undefined);
  };

  useEffect(() => {
    loadExercises();
    loadAssignedClassCount();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [exam.id]);

  /** Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-04 — "Xóa Đề" (soft-delete), backend tự chặn nếu còn Bài chưa lưu trữ. */
  const handleDeleteExam = async () => {
    if (!(await confirmDialog(`Xóa Đề "${exam.title}" (${exam.code})? Chỉ xóa được khi mọi Bài thuộc Đề đã được lưu trữ.`, { danger: true }))) {
      return;
    }
    setDeletingExam(true);
    setError(null);
    try {
      await deleteExam(exam.id);
      showToast("Đã xóa Đề thành công!");
      onDeleted();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Xóa Đề thất bại.");
    } finally {
      setDeletingExam(false);
    }
  };

  return (
    <div className="bg-white rounded-xl border border-slate-200 shadow-soft overflow-hidden">
      <div className="px-5 py-4 border-b border-slate-100 bg-slate-50 space-y-3">
        <div className="flex items-start justify-between gap-2">
          <div>
            <p className="text-sm font-bold text-slate-800">{exam.title}</p>
            <p className="text-[10px] text-slate-400 font-mono mt-0.5">{exam.code} · {exam.curriculumCode}</p>
          </div>
          {canManage && (
            <div className="flex items-center gap-2 shrink-0">
              <button onClick={() => setEditExamOpen(true)} title="Sửa tên Đề" className="text-slate-400 hover:text-brand-red">
                <Pencil className="w-3.5 h-3.5" />
              </button>
              <button onClick={handleDeleteExam} disabled={deletingExam} title="Xóa Đề" className="text-slate-400 hover:text-rose-600 disabled:opacity-50">
                <Trash2 className="w-3.5 h-3.5" />
              </button>
            </div>
          )}
        </div>
        <div className="flex items-center justify-between flex-wrap gap-2">
          <button
            onClick={() => setAssignClassOpen(true)}
            className="flex items-center gap-1.5 text-[11px] font-bold text-brand-red hover:underline"
          >
            <Users className="w-3.5 h-3.5" />
            {assignedClassCount == null ? "Đã gán ... lớp" : `Đã gán ${assignedClassCount} lớp`} — quản lý
          </button>
          {canManage && (
            <Button variant="primary" size="sm" onClick={() => setCreateExerciseOpen(true)}>
              <Plus className="w-3.5 h-3.5" />
              Soạn Bài mới
            </Button>
          )}
        </div>
      </div>

      {editExamOpen && (
        <EditExamModal
          exam={exam}
          onClose={() => setEditExamOpen(false)}
          onUpdated={(updated) => {
            onUpdated(updated);
            setEditExamOpen(false);
            showToast("Đã cập nhật Đề thành công!");
          }}
        />
      )}

      {error && <p className="px-5 pt-3 text-[11px] text-rose-600">{error}</p>}

      {loadingExercises ? (
        <p className="text-xs text-slate-500 p-6 text-center">Đang tải...</p>
      ) : exercises.length === 0 ? (
        <p className="text-xs text-slate-400 italic p-6 text-center">Đề này chưa có Bài nào.</p>
      ) : (
        <div className="divide-y divide-slate-100">
          {exercises.map((exercise) => (
            <ExerciseRow
              key={exercise.id}
              exercise={exercise}
              teacherType={exam.teacherType}
              canManage={canManage}
              onChanged={loadExercises}
              showToast={showToast}
            />
          ))}
        </div>
      )}

      {createExerciseOpen && (
        <CreateAndAssignExerciseModal
          examId={exam.id}
          teacherType={exam.teacherType}
          onClose={() => setCreateExerciseOpen(false)}
          onDone={() => {
            loadExercises();
            showToast("Đã lưu Bài thành công!");
          }}
        />
      )}

      {assignClassOpen && (
        <AssignClassModal
          examId={exam.id}
          onClose={() => {
            setAssignClassOpen(false);
            loadAssignedClassCount();
          }}
        />
      )}
    </div>
  );
}

/** Click dòng để mở rộng xem nhanh danh sách câu hỏi; nút riêng để xem trước Bài đầy đủ kèm đáp án. */
function ExerciseRow({
  exercise,
  teacherType,
  canManage,
  onChanged,
  showToast
}: {
  exercise: ExerciseResponse;
  teacherType: ExamTeacherType;
  canManage: boolean;
  onChanged: () => void;
  showToast: (msg: string) => void;
}) {
  const [expanded, setExpanded] = useState(false);
  const [questions, setQuestions] = useState<ExerciseQuestionResponse[] | null>(null);
  const [loading, setLoading] = useState(false);
  const [previewOpen, setPreviewOpen] = useState(false);
  const [editExerciseOpen, setEditExerciseOpen] = useState(false);
  const [addQuestionsOpen, setAddQuestionsOpen] = useState(false);
  const [publishing, setPublishing] = useState(false);
  const [deletingExercise, setDeletingExercise] = useState(false);
  const [removingId, setRemovingId] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);
  const { confirmDialog } = useDialog();
  // Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-03 — cho sửa câu hỏi ngay tại đây (trước
  // đây phải rời màn Soạn & giao đề, qua trang Ngân hàng câu hỏi mới sửa được). Không giới hạn theo
  // exercise.status=DRAFT như nút "Gỡ" — updateQuestion ở BE đã tự chặn khi câu hỏi có student_answers
  // (QuestionLockedException), không cần lặp lại điều kiện ở FE.
  const [editingQuestion, setEditingQuestion] = useState<QuestionResponse | null>(null);
  const [loadingEditQuestionId, setLoadingEditQuestionId] = useState<number | null>(null);

  const handleEditQuestion = async (q: ExerciseQuestionResponse) => {
    setLoadingEditQuestionId(q.id);
    setError(null);
    try {
      const full = await getExamQuestion(exercise.examId, q.questionId);
      setEditingQuestion(full);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Không tải được câu hỏi để sửa.");
    } finally {
      setLoadingEditQuestionId(null);
    }
  };

  const toggle = () => {
    setExpanded((v) => !v);
    if (!questions && !loading) {
      setLoading(true);
      listExerciseQuestions(exercise.id)
        .then(setQuestions)
        .catch(() => setQuestions([]))
        .finally(() => setLoading(false));
    }
  };

  /** Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-07-31 — chỉ hiện nút này khi Bài còn DRAFT (xem điều kiện render bên dưới). */
  /** Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-18 — sửa điểm 1 câu, chỉ khi Bài còn DRAFT (backend tự chặn nếu tổng điểm vượt exercise.totalPoints). */
  const [savingPointsId, setSavingPointsId] = useState<number | null>(null);
  const handlePointsChange = async (q: ExerciseQuestionResponse, newPoints: number) => {
    if (Number.isNaN(newPoints) || newPoints === q.points) return;
    setSavingPointsId(q.id);
    setError(null);
    try {
      const updated = await updateExerciseQuestionPoints(exercise.id, q.id, newPoints);
      setQuestions((prev) => (prev ? prev.map((x) => (x.id === q.id ? { ...x, points: updated.points } : x)) : prev));
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Sửa điểm câu hỏi thất bại.");
    } finally {
      setSavingPointsId(null);
    }
  };

  const handleRemoveQuestion = async (q: ExerciseQuestionResponse) => {
    if (!(await confirmDialog(`Gỡ câu hỏi "${q.questionContent}" khỏi Bài này?`, { danger: true }))) return;
    setRemovingId(q.id);
    setError(null);
    try {
      await removeExerciseQuestion(exercise.id, q.id);
      setQuestions((prev) => (prev ? prev.filter((x) => x.id !== q.id) : prev));
      showToast("Đã gỡ câu hỏi khỏi Bài!");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Gỡ câu hỏi thất bại.");
    } finally {
      setRemovingId(null);
    }
  };

  const handlePublish = async (e: React.MouseEvent) => {
    e.stopPropagation();
    setPublishing(true);
    setError(null);
    try {
      await publishExercise(exercise.id);
      onChanged();
      showToast("Đã Publish Bài thành công!");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Publish Bài thất bại.");
    } finally {
      setPublishing(false);
    }
  };

  /** Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-04 — "Xóa Bài" (lưu trữ, không xóa cứng — xem Javadoc ExerciseService#deleteExercise). */
  const handleDeleteExercise = async (e: React.MouseEvent) => {
    e.stopPropagation();
    if (!(await confirmDialog(`Xóa Bài "${exercise.title}" (${exercise.code})?`, { danger: true }))) return;
    setDeletingExercise(true);
    setError(null);
    try {
      await deleteExercise(exercise.id);
      onChanged();
      showToast("Đã xóa Bài thành công!");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Xóa Bài thất bại.");
    } finally {
      setDeletingExercise(false);
    }
  };

  return (
    <div>
      <div className="w-full px-5 py-3.5 flex items-center justify-between gap-3 flex-wrap hover:bg-slate-50/60">
        <button onClick={toggle} className="flex items-center gap-2 text-left flex-1 min-w-0">
          {expanded ? <ChevronDown className="w-3.5 h-3.5 text-slate-400 shrink-0" /> : <ChevronRight className="w-3.5 h-3.5 text-slate-400 shrink-0" />}
          <div className="min-w-0">
            <p className="text-xs font-bold text-slate-800">
              {exercise.title} <span className="font-mono text-slate-400 font-normal">({exercise.code})</span>
            </p>
            <p className="text-[10px] text-slate-400 mt-0.5">
              Tổng điểm: {exercise.totalPoints}
              {questions && (
                <span className={questions.reduce((s, q) => s + q.points, 0) > exercise.totalPoints ? "text-rose-500 font-bold" : undefined}>
                  {" "}
                  (đã gắn {questions.reduce((s, q) => s + q.points, 0)})
                </span>
              )}
            </p>
          </div>
        </button>
        <div className="flex items-center gap-2 shrink-0">
          {canManage && (
            <button
              onClick={(e) => {
                e.stopPropagation();
                setEditExerciseOpen(true);
              }}
              title="Sửa Bài"
              className="text-slate-400 hover:text-brand-red shrink-0"
            >
              <Pencil className="w-3.5 h-3.5" />
            </button>
          )}
          {canManage && (
            <button onClick={handleDeleteExercise} disabled={deletingExercise} title="Xóa Bài" className="text-slate-400 hover:text-rose-600 shrink-0 disabled:opacity-50">
              <Trash2 className="w-3.5 h-3.5" />
            </button>
          )}
          {canManage && (
            <button
              onClick={(e) => {
                e.stopPropagation();
                setAddQuestionsOpen(true);
              }}
              className="text-[10px] font-bold text-brand-red hover:underline whitespace-nowrap"
            >
              + Thêm câu hỏi
            </button>
          )}
          <button
            onClick={(e) => {
              e.stopPropagation();
              setPreviewOpen(true);
            }}
            className="text-[10px] font-bold text-brand-red hover:underline whitespace-nowrap"
          >
            Xem trước (có đáp án)
          </button>
          <Badge variant={statusVariants[exercise.status]}>{statusLabels[exercise.status]}</Badge>
          {canManage && exercise.status === "DRAFT" && (
            <button
              onClick={handlePublish}
              disabled={publishing}
              className="text-[10px] font-bold text-white bg-emerald-600 hover:bg-emerald-700 px-2.5 py-1 rounded-lg disabled:opacity-50 whitespace-nowrap"
            >
              {publishing ? "Đang publish..." : "Publish"}
            </button>
          )}
        </div>
      </div>

      {error && <p className="px-5 pb-2 text-[11px] text-rose-600">{error}</p>}

      {previewOpen && <ExercisePreviewModal exercise={exercise} onClose={() => setPreviewOpen(false)} />}

      {editExerciseOpen && (
        <EditExerciseModal
          exercise={exercise}
          onClose={() => setEditExerciseOpen(false)}
          onUpdated={() => {
            setEditExerciseOpen(false);
            onChanged();
            showToast("Đã cập nhật Bài thành công!");
          }}
        />
      )}

      {addQuestionsOpen && (
        <AddQuestionsModal
          exercise={exercise}
          teacherType={teacherType}
          onClose={() => setAddQuestionsOpen(false)}
          onDone={() => {
            setAddQuestionsOpen(false);
            if (expanded) {
              setLoading(true);
              listExerciseQuestions(exercise.id)
                .then(setQuestions)
                .catch(() => setQuestions([]))
                .finally(() => setLoading(false));
            } else {
              setQuestions(null);
            }
            onChanged();
            showToast("Đã thêm câu hỏi vào Bài!");
          }}
        />
      )}

      {editingQuestion && (
        <Modal open onClose={() => setEditingQuestion(null)} title={`Sửa câu hỏi Q-${editingQuestion.id}`} size="lg">
          <QuestionEditorForm
            examId={exercise.examId}
            existingQuestion={editingQuestion}
            onCreated={(updated) => {
              setQuestions((prev) =>
                prev
                  ? prev.map((x) => (x.questionId === updated.id ? { ...x, questionContent: updated.content, questionType: updated.questionType } : x))
                  : prev
              );
              setEditingQuestion(null);
            }}
            onCancel={() => setEditingQuestion(null)}
          />
        </Modal>
      )}

      {expanded && (
        <div className="px-5 pb-3.5 pl-11">
          {loading ? (
            <p className="text-[11px] text-slate-400">Đang tải câu hỏi...</p>
          ) : !questions || questions.length === 0 ? (
            <p className="text-[11px] text-slate-400 italic">Bài này chưa gắn câu hỏi nào.</p>
          ) : (
            <div className="space-y-1.5">
              {questions
                .sort((a, b) => a.displayOrder - b.displayOrder)
                .map((q) => (
                  <div key={q.id} className="text-[11px] text-slate-600 flex items-center justify-between gap-3 border-b border-slate-50 pb-1">
                    <span className="truncate">
                      {q.displayOrder}. {q.questionContent}
                    </span>
                    <span className="flex items-center gap-2 shrink-0">
                      <span className="text-slate-400">{q.questionType} ·</span>
                      {canManage && exercise.status === "DRAFT" ? (
                        <input
                          key={`${q.id}-${q.points}`}
                          type="number"
                          min={0}
                          step="0.5"
                          defaultValue={q.points}
                          disabled={savingPointsId === q.id}
                          onBlur={(e) => handlePointsChange(q, Number(e.target.value))}
                          title="Điểm câu hỏi này trong Bài (chỉ sửa được khi còn Nháp)"
                          className="w-14 text-[11px] text-right text-slate-500 border border-slate-200 rounded px-1 py-0.5 disabled:opacity-50 focus:outline-none"
                        />
                      ) : (
                        <span className="text-slate-400">{q.points}</span>
                      )}
                      <span className="text-slate-400">đ</span>
                      {canManage && (
                        <button
                          onClick={() => handleEditQuestion(q)}
                          disabled={loadingEditQuestionId === q.id}
                          className="p-0.5 text-slate-300 hover:text-brand-red disabled:opacity-50"
                          title="Sửa câu hỏi"
                        >
                          <Pencil className="w-3 h-3" />
                        </button>
                      )}
                      {canManage && exercise.status === "DRAFT" && (
                        <button
                          onClick={() => handleRemoveQuestion(q)}
                          disabled={removingId === q.id}
                          className="p-0.5 text-slate-300 hover:text-rose-600 disabled:opacity-50"
                          title="Gỡ câu hỏi (chỉ khi còn Nháp)"
                        >
                          <X className="w-3 h-3" />
                        </button>
                      )}
                    </span>
                  </div>
                ))}
            </div>
          )}
        </div>
      )}
    </div>
  );
}

/** "Một đề sẽ có thể gán được cho nhiều lớp" (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-07-30) — toggle gán/gỡ tức thì từng lớp. */
function AssignClassModal({ examId, onClose }: { examId: number; onClose: () => void }) {
  const { classes } = useEligibleClasses();
  const [assignedIds, setAssignedIds] = useState<Set<number>>(new Set());
  const [loading, setLoading] = useState(true);
  const [pendingId, setPendingId] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    listExamAssignedClasses(examId)
      .then((cls) => setAssignedIds(new Set(cls.map((c) => c.id))))
      .catch((err) => setError(err instanceof ApiError ? err.message : "Không tải được danh sách lớp đã gán."))
      .finally(() => setLoading(false));
  }, [examId]);

  const toggle = async (classId: number) => {
    setError(null);
    setPendingId(classId);
    try {
      if (assignedIds.has(classId)) {
        await unassignExamFromClass(examId, classId);
        setAssignedIds((prev) => {
          const next = new Set(prev);
          next.delete(classId);
          return next;
        });
      } else {
        await assignExamToClass(examId, classId);
        setAssignedIds((prev) => new Set(prev).add(classId));
      }
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Cập nhật gán lớp thất bại.");
    } finally {
      setPendingId(null);
    }
  };

  return (
    <Modal open onClose={onClose} title="Gán Đề cho lớp" size="md">
      <p className="text-[11px] text-slate-500 mb-3">
        Đề chỉ hiển thị cho học sinh của các lớp đã gán ở đây — Bài trong Đề cần được Giáo viên chọn làm "BTVN buổi sau"
        ở Nhận xét học viên mới thật sự giao cho học sinh làm.
      </p>
      {error && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg mb-3">{error}</div>}
      {loading ? (
        <p className="text-xs text-slate-500 p-3 text-center">Đang tải...</p>
      ) : classes.length === 0 ? (
        <p className="text-xs text-slate-400 italic p-3 text-center">Không có lớp nào để gán.</p>
      ) : (
        <div className="border border-slate-200 rounded-lg divide-y divide-slate-100 max-h-72 overflow-y-auto">
          {classes.map((c) => (
            <label key={c.id} className="flex items-center gap-2 px-3 py-2 text-xs cursor-pointer hover:bg-slate-50">
              <input
                type="checkbox"
                checked={assignedIds.has(c.id)}
                disabled={pendingId === c.id}
                onChange={() => toggle(c.id)}
              />
              <span className="flex-1">{c.classCode} — {c.name}</span>
              {pendingId === c.id && <span className="text-[10px] text-slate-400">Đang lưu...</span>}
            </label>
          ))}
        </div>
      )}
      <div className="flex justify-end pt-3">
        <Button type="button" variant="secondary" size="sm" onClick={onClose}>
          <X className="w-3.5 h-3.5" />
          Đóng
        </Button>
      </div>
    </Modal>
  );
}
