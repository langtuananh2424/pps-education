import React from "react";
import { Task } from "@/types";
import TaskCard from "./TaskCard";

const columns: { id: Task["status"]; label: string; dot: string }[] = [
  { id: "TODO", label: "Cần Làm", dot: "bg-slate-400" },
  { id: "IN_PROGRESS", label: "Đang Thực Hiện", dot: "bg-amber-400" },
  { id: "WAITING_APPROVAL", label: "Chờ Duyệt Kết Quả", dot: "bg-sky-400" },
  { id: "COMPLETED", label: "Đã Hoàn Thành", dot: "bg-emerald-400" }
];

interface KanbanBoardProps {
  tasks: Task[];
  onSelectTask: (task: Task) => void;
}

export default function KanbanBoard({ tasks, onSelectTask }: KanbanBoardProps) {
  return (
    <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-5 overflow-x-auto pb-4">
      {columns.map((col) => {
        const colTasks = tasks.filter((t) => t.status === col.id);
        return (
          <div key={col.id} className="bg-slate-100 rounded-xl p-4 flex flex-col min-h-[500px] kanban-column">
            <div className="flex items-center justify-between mb-3.5 pb-2 border-b border-slate-200">
              <span className="text-xs font-bold text-slate-800 font-display flex items-center gap-1.5">
                <span className={`w-2 h-2 rounded-full ${col.dot}`} />
                {col.label}
              </span>
              <span className="text-[10px] font-mono font-bold bg-slate-200 text-slate-600 px-2 py-0.5 rounded-full">{colTasks.length}</span>
            </div>

            <div className="space-y-3 flex-1 overflow-y-auto max-h-[520px] pr-0.5">
              {colTasks.map((task) => (
                <TaskCard key={task.id} task={task} onClick={() => onSelectTask(task)} />
              ))}

              {colTasks.length === 0 && (
                <div className="h-28 border-2 border-dashed border-slate-200 rounded-lg flex items-center justify-center text-[11px] text-slate-400 italic">
                  Trống
                </div>
              )}
            </div>
          </div>
        );
      })}
    </div>
  );
}
