import React, { useEffect, useState } from "react";
import { ChevronDown, ChevronRight, ClipboardList, Layers, Plus, Users, X } from "lucide-react";
import { ApiError } from "@/lib/apiClient";
import { useApp } from "@/context/AppContext";
import { useEligibleClasses } from "@/features/academic/hooks/useEligibleClasses";
import { CurriculumResponse, listCurriculums } from "@/features/academic/api";
import {
  ExamResponse,
  ExerciseQuestionResponse,
  ExerciseResponse,
  assignExamToClass,
  createExam,
  listExamAssignedClasses,
  listExercisesByExam,
  listExerciseQuestions,
  listExams,
  publishExercise,
  removeExerciseQuestion,
  unassignExamFromClass
} from "../api";
import CreateAndAssignExerciseModal from "../components/CreateAndAssignExerciseModal";
import ExercisePreviewModal from "../components/ExercisePreviewModal";
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
    listExams(curriculumFilter ?? undefined)
      .then((res) => {
        setExams(res);
        if (!res.some((e) => e.id === selectedExamId)) setSelectedExamId(res[0]?.id ?? null);
      })
      .catch((err) => setError(err instanceof ApiError ? err.message : "Không tải được danh sách Đề."))
      .finally(() => setLoadingExams(false));
  };

  // eslint-disable-next-line react-hooks/exhaustive-deps
  useEffect(loadExams, [curriculumFilter]);

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
          <div className="px-4 py-3 border-b border-slate-100 bg-slate-50">
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
            <ExamDetailPanel exam={selectedExam} canManage={canManage} showToast={showToast} />
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
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    if (!code.trim() || !title.trim() || !curriculumId) {
      setError("Vui lòng điền Mã Đề, Tên Đề và chọn Khung chương trình.");
      return;
    }
    setSubmitting(true);
    try {
      const created = await createExam({ code: code.trim(), title: title.trim(), curriculumId });
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
        <div className="flex justify-end gap-2 pt-2">
          <Button type="submit" variant="primary" size="sm" disabled={submitting}>
            {submitting ? "Đang tạo..." : "Tạo Đề"}
          </Button>
        </div>
      </form>
    </Modal>
  );
}

function ExamDetailPanel({
  exam,
  canManage,
  showToast
}: {
  exam: ExamResponse;
  canManage: boolean;
  showToast: (msg: string) => void;
}) {
  const [exercises, setExercises] = useState<ExerciseResponse[]>([]);
  const [loadingExercises, setLoadingExercises] = useState(false);
  const [createExerciseOpen, setCreateExerciseOpen] = useState(false);
  const [assignClassOpen, setAssignClassOpen] = useState(false);
  const [assignedClassCount, setAssignedClassCount] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);

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

  return (
    <div className="bg-white rounded-xl border border-slate-200 shadow-soft overflow-hidden">
      <div className="px-5 py-4 border-b border-slate-100 bg-slate-50 space-y-3">
        <div>
          <p className="text-sm font-bold text-slate-800">{exam.title}</p>
          <p className="text-[10px] text-slate-400 font-mono mt-0.5">{exam.code} · {exam.curriculumCode}</p>
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

      {error && <p className="px-5 pt-3 text-[11px] text-rose-600">{error}</p>}

      {loadingExercises ? (
        <p className="text-xs text-slate-500 p-6 text-center">Đang tải...</p>
      ) : exercises.length === 0 ? (
        <p className="text-xs text-slate-400 italic p-6 text-center">Đề này chưa có Bài nào.</p>
      ) : (
        <div className="divide-y divide-slate-100">
          {exercises.map((exercise) => (
            <ExerciseRow key={exercise.id} exercise={exercise} canManage={canManage} onChanged={loadExercises} showToast={showToast} />
          ))}
        </div>
      )}

      {createExerciseOpen && (
        <CreateAndAssignExerciseModal
          examId={exam.id}
          curriculumId={exam.curriculumId}
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
  canManage,
  onChanged,
  showToast
}: {
  exercise: ExerciseResponse;
  canManage: boolean;
  onChanged: () => void;
  showToast: (msg: string) => void;
}) {
  const [expanded, setExpanded] = useState(false);
  const [questions, setQuestions] = useState<ExerciseQuestionResponse[] | null>(null);
  const [loading, setLoading] = useState(false);
  const [previewOpen, setPreviewOpen] = useState(false);
  const [publishing, setPublishing] = useState(false);
  const [removingId, setRemovingId] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);
  const { confirmDialog } = useDialog();

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

  return (
    <div>
      <div className="w-full px-5 py-3.5 flex items-center justify-between gap-3 flex-wrap hover:bg-slate-50/60">
        <button onClick={toggle} className="flex items-center gap-2 text-left flex-1 min-w-0">
          {expanded ? <ChevronDown className="w-3.5 h-3.5 text-slate-400 shrink-0" /> : <ChevronRight className="w-3.5 h-3.5 text-slate-400 shrink-0" />}
          <div className="min-w-0">
            <p className="text-xs font-bold text-slate-800">
              {exercise.title} <span className="font-mono text-slate-400 font-normal">({exercise.code})</span>
            </p>
            <p className="text-[10px] text-slate-400 mt-0.5">Tổng điểm: {exercise.totalPoints}</p>
          </div>
        </button>
        <div className="flex items-center gap-2 shrink-0">
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
                      <span className="text-slate-400">
                        {q.questionType} · {q.points} đ
                      </span>
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
