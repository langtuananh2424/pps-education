import React from "react";

export interface GradePublishGroup {
  classId: number;
  classCode: string;
  className: string;
  teacherName: string | null;
  pendingCount: number;
}

interface GradePublishGroupListProps {
  groups: GradePublishGroup[];
  loading: boolean;
  selectedClassId: number | null;
  onSelect: (classId: number) => void;
}

/** UC-20 bước 1 (V44): danh sách lớp (+ giáo viên) có bản ghi điểm đang chờ duyệt (SUBMITTED) — bấm vào 1 lớp mới xem chi tiết. */
export default function GradePublishGroupList({ groups, loading, selectedClassId, onSelect }: GradePublishGroupListProps) {
  if (loading) return <p className="text-sm text-slate-500 p-4">Đang tải...</p>;
  if (groups.length === 0) return <p className="text-sm text-slate-400 italic text-center py-6">Chưa có lớp nào gửi điểm chờ duyệt.</p>;

  return (
    <div className="divide-y divide-slate-100">
      {groups.map((g) => (
        <button
          key={g.classId}
          onClick={() => onSelect(g.classId)}
          className={`w-full text-left px-4 py-3 flex items-center justify-between gap-3 transition-all ${
            selectedClassId === g.classId ? "bg-orange-50" : "hover:bg-slate-50"
          }`}
        >
          <div className="min-w-0">
            <span className="text-sm font-bold text-slate-800 truncate block">
              {g.classCode} — {g.className}
            </span>
            <span className="text-sm text-slate-400 block mt-0.5">GV: {g.teacherName ?? "Chưa rõ"}</span>
          </div>
          <span className="text-sm font-bold text-brand-orange bg-orange-50 border border-orange-100 px-2 py-1 rounded-full shrink-0">
            {g.pendingCount} điểm chờ
          </span>
        </button>
      ))}
    </div>
  );
}
