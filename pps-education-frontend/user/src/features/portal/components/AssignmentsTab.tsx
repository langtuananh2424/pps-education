import React, { useEffect, useState } from "react";
import { ChevronDown, ChevronRight, ClipboardList, Clock } from "lucide-react";
import { ApiError } from "@/lib/apiClient";
import { AssignedExerciseResponse, ExerciseQuestionResponse, listExerciseQuestions, listMyAssignedExercises } from "../api";

interface AssignmentsTabProps {
  classId: number;
}

const attemptStatusLabels: Record<string, { label: string; className: string }> = {
  IN_PROGRESS: { label: "Đang làm dở", className: "bg-gold/10 text-gold" },
  AUTO_GRADED: { label: "Đã nộp — chờ chấm tự luận/nói", className: "bg-sky text-teal-deep" },
  FULLY_GRADED: { label: "Đã có điểm", className: "bg-teal/10 text-teal-deep" }
};

/** UC-40 (phía học viên): xem bài tập được giao cho lớp mình — chỉ xem, chưa làm bài (nộp bài thuộc UC-24/27, đang chờ BE bổ sung field choices an toàn cho câu trắc nghiệm trước khi làm được ở đây). */
export default function AssignmentsTab({ classId }: AssignmentsTabProps) {
  const [items, setItems] = useState<AssignedExerciseResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    setLoading(true);
    setError(null);
    listMyAssignedExercises(classId)
      .then(setItems)
      .catch((err) => setError(err instanceof ApiError ? err.message : "Không tải được danh sách bài tập."))
      .finally(() => setLoading(false));
  }, [classId]);

  if (loading) return <p className="text-sm text-muted font-bold">Đang tải...</p>;

  return (
    <div className="bg-white border border-line/80 p-6 rounded-[20px] shadow-[0_8px_30px_rgba(30,42,69,0.03)] space-y-4">
      <h2 className="text-xl font-extrabold text-ink flex items-center gap-2">
        <ClipboardList className="text-teal" /> Bài tập được giao
      </h2>

      {error && <div className="text-xs font-bold text-rose-600 bg-rose-50 border border-rose-100 p-3 rounded-xl">{error}</div>}

      {items.length === 0 ? (
        <p className="text-xs text-muted font-bold italic">Chưa có bài tập nào được giao cho lớp này.</p>
      ) : (
        <div className="space-y-3">
          {items.map((item) => (
            <AssignmentCard key={item.assignmentId} item={item} />
          ))}
        </div>
      )}
    </div>
  );
}

function AssignmentCard({ item }: { item: AssignedExerciseResponse }) {
  const [expanded, setExpanded] = useState(false);
  const [questions, setQuestions] = useState<ExerciseQuestionResponse[] | null>(null);
  const [loadingQuestions, setLoadingQuestions] = useState(false);

  const isOverdue = item.dueAt != null && new Date(item.dueAt) < new Date();
  const attemptMeta = item.myLatestAttemptStatus ? attemptStatusLabels[item.myLatestAttemptStatus] : null;

  const toggle = () => {
    setExpanded((v) => !v);
    if (!questions && !loadingQuestions) {
      setLoadingQuestions(true);
      listExerciseQuestions(item.exerciseId)
        .then((res) => setQuestions([...res].sort((a, b) => a.displayOrder - b.displayOrder)))
        .catch(() => setQuestions([]))
        .finally(() => setLoadingQuestions(false));
    }
  };

  return (
    <div className="border border-line/80 rounded-[16px] overflow-hidden">
      <button onClick={toggle} className="w-full text-left p-4 bg-sky-2 hover:bg-sky flex items-center justify-between gap-3">
        <div className="flex items-center gap-2 flex-1 min-w-0">
          {expanded ? <ChevronDown size={16} className="text-muted shrink-0" /> : <ChevronRight size={16} className="text-muted shrink-0" />}
          <div className="min-w-0">
            <p className="font-extrabold text-ink text-sm truncate">{item.title}</p>
            <p className="text-[10px] text-muted font-bold flex items-center gap-1 mt-0.5">
              <Clock size={11} />
              Hạn nộp: {item.dueAt ? new Date(item.dueAt).toLocaleString("vi-VN") : "Không giới hạn"}
              {item.myLatestTotalScore != null && ` · Điểm: ${item.myLatestTotalScore}`}
            </p>
          </div>
        </div>
        <div className="flex items-center gap-1.5 shrink-0">
          {attemptMeta && <span className={`text-[10px] font-extrabold uppercase px-2.5 py-1 rounded-full ${attemptMeta.className}`}>{attemptMeta.label}</span>}
          {!attemptMeta && (
            <span className={`text-[10px] font-extrabold uppercase px-2.5 py-1 rounded-full ${isOverdue ? "bg-coral/10 text-coral" : "bg-teal/10 text-teal-deep"}`}>
              {isOverdue ? "Đã quá hạn — chưa làm" : "Chưa làm"}
            </span>
          )}
        </div>
      </button>

      {expanded && (
        <div className="p-4 bg-white space-y-2">
          {loadingQuestions ? (
            <p className="text-xs text-muted font-bold">Đang tải câu hỏi...</p>
          ) : !questions || questions.length === 0 ? (
            <p className="text-xs text-muted font-bold italic">Đề này chưa có câu hỏi nào.</p>
          ) : (
            <div className="space-y-1.5">
              {questions.map((q) => (
                <div key={q.id} className="flex items-center justify-between gap-3 text-xs border-b border-line/50 pb-1.5">
                  <span className="text-ink font-bold truncate">
                    {q.displayOrder}. {q.questionContent}
                  </span>
                  <span className="text-muted font-bold shrink-0">{q.points} đ</span>
                </div>
              ))}
            </div>
          )}
        </div>
      )}
    </div>
  );
}
