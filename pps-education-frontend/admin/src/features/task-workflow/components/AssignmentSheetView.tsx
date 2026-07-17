import React, { useMemo, useState } from "react";
import { TaskAssignmentResponse } from "../api";
import { ASSIGNMENT_STATUS_META, ASSIGNMENT_STATUS_ORDER } from "../statusMeta";

interface AssignmentSheetViewProps {
  assignments: TaskAssignmentResponse[];
  onSelect: (assignment: TaskAssignmentResponse) => void;
}

/** Yêu cầu bổ sung: quản lý sâu hơn dạng bảng, song song Kanban — cùng nguồn dữ liệu `my-assignments`. */
export default function AssignmentSheetView({ assignments, onSelect }: AssignmentSheetViewProps) {
  const [statusFilter, setStatusFilter] = useState<string>("ALL");
  const [keyword, setKeyword] = useState("");

  const filtered = useMemo(() => {
    return assignments
      .filter((a) => statusFilter === "ALL" || a.assignmentStatus === statusFilter)
      .filter((a) => !keyword.trim() || a.taskTitle.toLowerCase().includes(keyword.trim().toLowerCase()))
      .sort((a, b) => b.id - a.id);
  }, [assignments, statusFilter, keyword]);

  return (
    <div className="space-y-3">
      <div className="flex items-center gap-2">
        <input
          value={keyword}
          onChange={(e) => setKeyword(e.target.value)}
          placeholder="Tìm theo tiêu đề công việc..."
          className="flex-1 bg-slate-50 border border-slate-200 text-xs p-2.5 rounded-lg focus:outline-none max-w-xs"
        />
        <select
          value={statusFilter}
          onChange={(e) => setStatusFilter(e.target.value)}
          className="bg-slate-50 border border-slate-200 text-xs p-2.5 rounded-lg focus:outline-none"
        >
          <option value="ALL">-- Mọi trạng thái --</option>
          {ASSIGNMENT_STATUS_ORDER.map((s) => (
            <option key={s} value={s}>
              {ASSIGNMENT_STATUS_META[s].label}
            </option>
          ))}
        </select>
        <span className="text-[11px] text-slate-400 ml-auto">Tổng {filtered.length} việc</span>
      </div>

      <div className="border border-slate-200 rounded-xl overflow-x-auto">
        <table className="w-full text-xs">
          <thead className="bg-slate-50 text-[10px] uppercase font-bold text-slate-500">
            <tr>
              <th className="text-left px-3 py-2.5">Mã PC</th>
              <th className="text-left px-3 py-2.5">Tiêu đề công việc</th>
              <th className="text-left px-3 py-2.5">Trạng thái</th>
              <th className="text-left px-3 py-2.5">Tiến độ</th>
              <th className="text-left px-3 py-2.5">Bắt đầu</th>
              <th className="text-left px-3 py-2.5">Hoàn thành</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {filtered.map((a) => {
              const meta = ASSIGNMENT_STATUS_META[a.assignmentStatus];
              return (
                <tr key={a.id} onClick={() => onSelect(a)} className="hover:bg-slate-50 cursor-pointer">
                  <td className="px-3 py-2.5 font-mono text-slate-400">#{a.id}</td>
                  <td className="px-3 py-2.5 font-semibold text-slate-800">{a.taskTitle}</td>
                  <td className="px-3 py-2.5">
                    <span className={`px-2 py-0.5 rounded-full text-[10px] font-bold ${meta.badge}`}>{meta.label}</span>
                  </td>
                  <td className="px-3 py-2.5 text-slate-500">{a.progressPercent != null ? `${a.progressPercent}%` : "—"}</td>
                  <td className="px-3 py-2.5 text-slate-500">{a.startedAt ? new Date(a.startedAt).toLocaleDateString("vi-VN") : "—"}</td>
                  <td className="px-3 py-2.5 text-slate-500">{a.completedAt ? new Date(a.completedAt).toLocaleDateString("vi-VN") : "—"}</td>
                </tr>
              );
            })}

            {filtered.length === 0 && (
              <tr>
                <td colSpan={6} className="px-3 py-8 text-center text-slate-400 italic">
                  Không có công việc nào khớp bộ lọc.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
}
