import React, { useEffect, useState } from "react";
import { ChevronDown, ChevronRight, ClipboardList, Plus } from "lucide-react";
import { ApiError } from "@/lib/apiClient";
import { useApp } from "@/context/AppContext";
import { useEligibleClasses } from "@/features/academic/hooks/useEligibleClasses";
import { ExerciseAssignmentResponse, ExerciseQuestionResponse, ExerciseResponse, getExercise, listAssignmentsForClass, listExerciseQuestions } from "../api";
import CreateAndAssignExerciseModal from "../components/CreateAndAssignExerciseModal";
import ExercisePreviewModal from "../components/ExercisePreviewModal";
import Button from "@/components/ui/Button";
import { useToast } from "@/lib/useToast";
import Toast from "@/components/ui/Toast";

interface AssignmentRow {
  assignment: ExerciseAssignmentResponse;
  exercise: ExerciseResponse | null;
}

const assignmentStatusLabels: Record<ExerciseAssignmentResponse["status"], string> = {
  ACTIVE: "Đang hiệu lực",
  CANCELLED: "Đã hủy",
  COMPLETED: "Đã kết thúc"
};

/** UC-40: Soạn & giao đề kiểm tra — chọn 1 lớp trước, xem lịch sử đề đã giao, giao đề mới. */
export default function ExerciseAssignPage() {
  const { hasPermission, selectedClassId } = useApp();
  // lms.exercise.manage đã bị tách nhỏ (hạt nhân hóa V62) — trang này chỉ giao bài tập mới (create).
  const canManage = hasPermission("lms.exercise.create");

  const { classes } = useEligibleClasses();
  const [rows, setRows] = useState<AssignmentRow[]>([]);
  const [loadingRows, setLoadingRows] = useState(false);
  const [createOpen, setCreateOpen] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const { message: toastMessage, showToast } = useToast();

  const loadAssignments = () => {
    if (!selectedClassId) {
      setRows([]);
      return;
    }
    setLoadingRows(true);
    listAssignmentsForClass(selectedClassId)
      .then(async (assignments) => {
        const exercises = await Promise.all(assignments.map((a) => getExercise(a.exerciseId).catch(() => null)));
        setRows(assignments.map((assignment, i) => ({ assignment, exercise: exercises[i] })));
      })
      .catch((err) => setError(err instanceof ApiError ? err.message : "Không tải được danh sách đề đã giao."))
      .finally(() => setLoadingRows(false));
  };

  useEffect(loadAssignments, [selectedClassId]);

  const selectedClass = classes.find((c) => c.id === selectedClassId) ?? null;

  return (
    <div className="space-y-6">
      <div className="border-b border-slate-200 pb-4">
        <h1 className="text-xl font-bold font-display tracking-tight text-slate-900">Soạn & giao đề</h1>
        <p className="text-xs text-slate-500 mt-1">Chọn 1 lớp để xem lịch sử đề đã giao, hoặc giao đề bài tập mới cho lớp đó.</p>
      </div>

      {error && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg">{error}</div>}

      <div className="bg-white rounded-xl border border-slate-200 shadow-soft overflow-hidden">
        <div className="px-5 py-4 border-b border-slate-100 bg-slate-50 flex items-center justify-between flex-wrap gap-3">
          <span className="text-xs font-bold text-slate-700">
            {selectedClass ? `Lớp: ${selectedClass.classCode} — ${selectedClass.name}` : "Chưa chọn lớp — chọn ở góc trên bên phải (Header)"}
          </span>

          {canManage && selectedClassId && (
            <Button variant="primary" size="sm" onClick={() => setCreateOpen(true)}>
              <Plus className="w-3.5 h-3.5" />
              Giao bài tập mới
            </Button>
          )}
        </div>

        {!selectedClassId ? (
          <div className="flex flex-col items-center justify-center p-12 text-center text-slate-400 space-y-3">
            <ClipboardList className="w-12 h-12 text-slate-300" />
            <div>
              <h3 className="text-sm font-bold text-slate-700">Chưa chọn lớp nào</h3>
              <p className="text-xs text-slate-400 mt-1">Chọn 1 lớp ở Header (góc trên bên phải) để xem lịch sử đề đã giao.</p>
            </div>
          </div>
        ) : loadingRows ? (
          <p className="text-xs text-slate-500 p-6 text-center">Đang tải...</p>
        ) : rows.length === 0 ? (
          <p className="text-xs text-slate-400 italic p-6 text-center">Lớp này chưa được giao đề nào.</p>
        ) : (
          <div className="divide-y divide-slate-100">
            {rows.map(({ assignment, exercise }) => (
              <AssignmentRow key={assignment.id} assignment={assignment} exercise={exercise} />
            ))}
          </div>
        )}
      </div>

      {createOpen && selectedClassId && (
        <CreateAndAssignExerciseModal
          classId={selectedClassId}
          onClose={() => setCreateOpen(false)}
          onAssigned={() => {
            loadAssignments();
            showToast("Đã giao đề bài tập thành công!");
          }}
        />
      )}

      {!selectedClass && classes.length === 0 && (
        <p className="text-xs text-slate-400 italic">Không có lớp nào để chọn — kiểm tra lại phân công giảng dạy nếu đây là tài khoản Giáo viên.</p>
      )}

      <Toast message={toastMessage} />
    </div>
  );
}

/** Click dòng để mở rộng xem nhanh danh sách câu hỏi; nút riêng để xem trước đề đầy đủ kèm đáp án. */
function AssignmentRow({ assignment, exercise }: AssignmentRow) {
  const [expanded, setExpanded] = useState(false);
  const [questions, setQuestions] = useState<ExerciseQuestionResponse[] | null>(null);
  const [loading, setLoading] = useState(false);
  const [previewOpen, setPreviewOpen] = useState(false);

  const toggle = () => {
    setExpanded((v) => !v);
    if (!questions && !loading) {
      setLoading(true);
      listExerciseQuestions(assignment.exerciseId)
        .then(setQuestions)
        .catch(() => setQuestions([]))
        .finally(() => setLoading(false));
    }
  };

  return (
    <div>
      <div className="w-full px-5 py-3.5 flex items-center justify-between gap-3 flex-wrap hover:bg-slate-50/60">
        <button onClick={toggle} className="flex items-center gap-2 text-left flex-1 min-w-0">
          {expanded ? <ChevronDown className="w-3.5 h-3.5 text-slate-400 shrink-0" /> : <ChevronRight className="w-3.5 h-3.5 text-slate-400 shrink-0" />}
          <div className="min-w-0">
            <p className="text-xs font-bold text-slate-800">{exercise?.title ?? `Đề #${assignment.exerciseId}`}</p>
            <p className="text-[10px] text-slate-400 mt-0.5">
              Hạn nộp: {assignment.dueAt ? new Date(assignment.dueAt).toLocaleString("vi-VN") : "Không giới hạn"}
              {exercise?.totalPoints != null && ` · Tổng điểm: ${exercise.totalPoints}`}
              {assignment.targetStudentIds ? ` · Giao riêng ${assignment.targetStudentIds.length} học sinh` : " · Giao cả lớp"}
            </p>
          </div>
        </button>
        <div className="flex items-center gap-2 shrink-0">
          {exercise && (
            <button
              onClick={() => setPreviewOpen(true)}
              className="text-[10px] font-bold text-brand-red hover:underline whitespace-nowrap"
            >
              Xem trước đề (có đáp án)
            </button>
          )}
          <span
            className={`text-[10px] font-bold uppercase px-2 py-0.5 rounded-full ${
              assignment.status === "ACTIVE" ? "bg-emerald-50 text-emerald-600" : "bg-slate-100 text-slate-500"
            }`}
          >
            {assignmentStatusLabels[assignment.status]}
          </span>
        </div>
      </div>

      {previewOpen && exercise && <ExercisePreviewModal exercise={exercise} onClose={() => setPreviewOpen(false)} />}

      {expanded && (
        <div className="px-5 pb-3.5 pl-11">
          {loading ? (
            <p className="text-[11px] text-slate-400">Đang tải câu hỏi...</p>
          ) : !questions || questions.length === 0 ? (
            <p className="text-[11px] text-slate-400 italic">Đề này chưa gắn câu hỏi nào.</p>
          ) : (
            <div className="space-y-1.5">
              {questions
                .sort((a, b) => a.displayOrder - b.displayOrder)
                .map((q) => (
                  <div key={q.id} className="text-[11px] text-slate-600 flex items-center justify-between gap-3 border-b border-slate-50 pb-1">
                    <span className="truncate">
                      {q.displayOrder}. {q.questionContent}
                    </span>
                    <span className="text-slate-400 shrink-0">
                      {q.questionType} · {q.points} đ
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
