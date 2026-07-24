import React, { useEffect, useState } from "react";
import { ChevronRight, ClipboardList, Clock } from "lucide-react";
import { ApiError } from "@/lib/apiClient";
import { AssignedExerciseResponse, listMyAssignedExercises } from "../api";
import TakeExerciseModal from "./TakeExerciseModal";

interface AssignmentsTabProps {
  classId: number;
}

const attemptStatusLabels: Record<string, { label: string; className: string }> = {
  IN_PROGRESS: { label: "Đang làm dở", className: "bg-gold/10 text-gold" },
  AUTO_GRADED: { label: "Đã nộp — chờ chấm tự luận/nói", className: "bg-sky text-teal-deep" },
  FULLY_GRADED: { label: "Đã có điểm", className: "bg-teal/10 text-teal-deep" }
};

/** UC-40 (phía học viên): xem bài tập được giao cho lớp mình + làm bài thật (UC-24/27, đã có field choices an toàn cho câu trắc nghiệm). */
export default function AssignmentsTab({ classId }: AssignmentsTabProps) {
  const [items, setItems] = useState<AssignedExerciseResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = () => {
    setLoading(true);
    setError(null);
    listMyAssignedExercises(classId)
      .then(setItems)
      .catch((err) => setError(err instanceof ApiError ? err.message : "Không tải được danh sách bài tập."))
      .finally(() => setLoading(false));
  };

  useEffect(load, [classId]);

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
            <AssignmentCard key={item.assignmentId} item={item} onChanged={load} />
          ))}
        </div>
      )}
    </div>
  );
}

function AssignmentCard({ item, onChanged }: { item: AssignedExerciseResponse; onChanged: () => void }) {
  const [taking, setTaking] = useState(false);

  const isOverdue = item.dueAt != null && new Date(item.dueAt) < new Date();
  const attemptMeta = item.myLatestAttemptStatus ? attemptStatusLabels[item.myLatestAttemptStatus] : null;
  const isFullyGraded = item.myLatestAttemptStatus === "FULLY_GRADED";

  const actionLabel =
    item.myLatestAttemptStatus == null
      ? "Làm bài ngay"
      : item.myLatestAttemptStatus === "IN_PROGRESS"
        ? "Tiếp tục làm bài"
        : isFullyGraded
          ? "Xem lại bài đã làm"
          : "Xem lại bài đã nộp";

  return (
    <div className={`rounded-[16px] p-4 flex items-center justify-between gap-3 ${isOverdue && !attemptMeta ? "bg-coral/5 border border-coral/20" : "bg-sky-2 border border-line/80"}`}>
      <div className="min-w-0 flex-1">
        {attemptMeta ? (
          <span className={`text-[10px] font-extrabold uppercase px-2.5 py-1 rounded-full ${attemptMeta.className}`}>{attemptMeta.label}</span>
        ) : (
          <span className={`text-[10px] font-extrabold uppercase px-2.5 py-1 rounded-full ${isOverdue ? "bg-coral/10 text-coral" : "bg-teal/10 text-teal-deep"}`}>
            {isOverdue ? "Đã quá hạn — chưa làm" : "Chưa làm"}
          </span>
        )}
        <p className="font-extrabold text-ink text-sm mt-1.5 truncate">{item.title}</p>
        <p className="text-[10px] text-muted font-bold flex items-center gap-1 mt-0.5">
          <Clock size={11} />
          Hạn nộp: {item.dueAt ? new Date(item.dueAt).toLocaleString("vi-VN") : "Không giới hạn"}
          {item.myLatestTotalScore != null && ` · Điểm: ${item.myLatestTotalScore}`}
        </p>
      </div>

      <div className="shrink-0 flex items-center gap-3">
        {isFullyGraded && item.myLatestTotalScore != null && <p className="text-sm font-extrabold text-teal-deep">{item.myLatestTotalScore} đ</p>}
        <button
          onClick={() => setTaking(true)}
          className={`flex items-center gap-1 text-xs font-extrabold px-4 py-2.5 rounded-full transition-colors ${
            isFullyGraded ? "text-teal-deep bg-teal/10 hover:bg-teal/20" : "text-white bg-teal hover:bg-teal-deep"
          }`}
        >
          {actionLabel} <ChevronRight size={14} />
        </button>
      </div>

      {taking && (
        <TakeExerciseModal
          item={item}
          // Mở đề = BE đã tạo attempt ngay (started_at = NOW), kể cả khi đóng chưa nộp — luôn báo onChanged
          // để danh sách bên ngoài cập nhật đúng myLatestAttemptId/Status, tránh lần sau bấm "Làm bài" lại
          // tưởng chưa có lượt nào rồi gọi tạo lượt MỚI (đề không cho làm lại sẽ bị chặn ngay).
          onClose={() => {
            setTaking(false);
            onChanged();
          }}
          onFinished={onChanged}
        />
      )}
    </div>
  );
}
