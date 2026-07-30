import React, { useEffect, useState } from "react";
import { ChevronDown, ChevronRight, ClipboardList, Plus } from "lucide-react";
import { ApiError } from "@/lib/apiClient";
import { useApp } from "@/context/AppContext";
import { useEligibleClasses } from "@/features/academic/hooks/useEligibleClasses";
import { ExerciseQuestionResponse, ExerciseResponse, listExerciseQuestions, listForCurriculum, publishExercise } from "../api";
import CreateAndAssignExerciseModal from "../components/CreateAndAssignExerciseModal";
import ExercisePreviewModal from "../components/ExercisePreviewModal";
import Button from "@/components/ui/Button";
import Badge from "@/components/ui/Badge";
import { useToast } from "@/lib/useToast";
import Toast from "@/components/ui/Toast";

const statusLabels: Record<ExerciseResponse["status"], string> = { DRAFT: "Nháp", PUBLISHED: "Đã publish", ARCHIVED: "Đã gỡ" };
const statusVariants: Record<ExerciseResponse["status"], "neutral" | "success" | "danger"> = {
  DRAFT: "neutral",
  PUBLISHED: "success",
  ARCHIVED: "danger"
};

/**
 * UC-40: Soạn & giao đề — soạn đề Ngữ pháp (ASSIGNED) theo khung chương trình của LỚP đang chọn ở
 * Header (đúng quy ước dùng chung selectedClassId/useEligibleClasses của mọi màn lọc theo lớp, không
 * tự chế 1 dropdown khung chương trình riêng — đã xác nhận với người dùng 2026-07-30), rồi Publish
 * để đủ điều kiện dùng làm nguồn. V65 (bổ sung ngoài SDD gốc): không còn thao tác "giao lớp" ở đây —
 * Publish không còn gắn với 1 lớp cụ thể nào, đề soạn ra dùng chung cho MỌI lớp cùng khung chương
 * trình. Giao bài thật (tự động cho cả lớp, hạn nộp = buổi kế tiếp) chỉ xảy ra khi Giáo viên chọn đề
 * đã Publish làm "BTVN Ngữ pháp buổi sau" ở Nhận xét học viên (UC-21).
 */
export default function ExerciseAssignPage() {
  const { hasPermission, selectedClassId } = useApp();
  const canManage = hasPermission("lms.exercise.create");
  const { classes } = useEligibleClasses();

  const [exercises, setExercises] = useState<ExerciseResponse[]>([]);
  const [loadingRows, setLoadingRows] = useState(false);
  const [createOpen, setCreateOpen] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const { message: toastMessage, showToast } = useToast();

  const selectedClass = classes.find((c) => c.id === selectedClassId) ?? null;
  const curriculumId = selectedClass?.curriculumId ?? null;

  const loadExercises = () => {
    if (!curriculumId) {
      setExercises([]);
      return;
    }
    setLoadingRows(true);
    listForCurriculum(curriculumId)
      .then(setExercises)
      .catch((err) => setError(err instanceof ApiError ? err.message : "Không tải được danh sách đề."))
      .finally(() => setLoadingRows(false));
  };

  useEffect(loadExercises, [curriculumId]);

  return (
    <div className="space-y-6">
      <div className="border-b border-slate-200 pb-4">
        <h1 className="text-xl font-bold font-display tracking-tight text-slate-900">Soạn & giao đề</h1>
        <p className="text-xs text-slate-500 mt-1">
          Soạn đề Ngữ pháp theo khung chương trình của lớp đang chọn rồi Publish — Giáo viên chọn đề đã Publish làm "BTVN Ngữ
          pháp buổi sau" ở Nhận xét học viên mới thật sự giao cho lớp.
        </p>
      </div>

      {error && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg">{error}</div>}

      <div className="bg-white rounded-xl border border-slate-200 shadow-soft overflow-hidden">
        <div className="px-5 py-4 border-b border-slate-100 bg-slate-50 flex items-center justify-between flex-wrap gap-3">
          <span className="text-xs font-bold text-slate-700">
            {selectedClass
              ? `Khung chương trình: ${selectedClass.curriculumCode} (lớp ${selectedClass.classCode})`
              : "Chưa chọn lớp — chọn ở góc trên bên phải (Header) để bắt đầu."}
          </span>

          {canManage && curriculumId && (
            <Button variant="primary" size="sm" onClick={() => setCreateOpen(true)}>
              <Plus className="w-3.5 h-3.5" />
              Soạn đề mới
            </Button>
          )}
        </div>

        {!curriculumId ? (
          <div className="flex flex-col items-center justify-center p-12 text-center text-slate-400 space-y-3">
            <ClipboardList className="w-12 h-12 text-slate-300" />
            <div>
              <h3 className="text-sm font-bold text-slate-700">Chưa chọn lớp nào</h3>
              <p className="text-xs text-slate-400 mt-1">Chọn 1 lớp ở Header (góc trên bên phải) để xem/soạn đề Ngữ pháp theo đúng khung chương trình của lớp đó.</p>
            </div>
          </div>
        ) : loadingRows ? (
          <p className="text-xs text-slate-500 p-6 text-center">Đang tải...</p>
        ) : exercises.length === 0 ? (
          <p className="text-xs text-slate-400 italic p-6 text-center">Khung chương trình này chưa soạn đề Ngữ pháp nào.</p>
        ) : (
          <div className="divide-y divide-slate-100">
            {exercises.map((exercise) => (
              <ExerciseRow key={exercise.id} exercise={exercise} canManage={canManage} onChanged={loadExercises} showToast={showToast} />
            ))}
          </div>
        )}
      </div>

      {createOpen && curriculumId && (
        <CreateAndAssignExerciseModal
          curriculumId={curriculumId}
          onClose={() => setCreateOpen(false)}
          onDone={() => {
            loadExercises();
            showToast("Đã lưu đề thành công!");
          }}
        />
      )}

      <Toast message={toastMessage} />
    </div>
  );
}

/** Click dòng để mở rộng xem nhanh danh sách câu hỏi; nút riêng để xem trước đề đầy đủ kèm đáp án. */
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
  const [error, setError] = useState<string | null>(null);

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

  const handlePublish = async (e: React.MouseEvent) => {
    e.stopPropagation();
    setPublishing(true);
    setError(null);
    try {
      await publishExercise(exercise.id);
      onChanged();
      showToast("Đã Publish đề thành công!");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Publish đề thất bại.");
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
            Xem trước đề (có đáp án)
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
