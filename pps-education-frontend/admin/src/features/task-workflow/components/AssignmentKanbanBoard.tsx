import React from "react";
import { Clock } from "lucide-react";
import { TaskAssignmentResponse } from "../api";
import { ASSIGNMENT_STATUS_META, ASSIGNMENT_STATUS_ORDER } from "../statusMeta";

interface AssignmentKanbanBoardProps {
  assignments: TaskAssignmentResponse[];
  onSelect: (assignment: TaskAssignmentResponse) => void;
}

/** UC-07 Main Flow bước 1: Kanban theo ĐÚNG 6 giá trị TaskAssignment.Status (không phải 4 cột tự đặt). */
export default function AssignmentKanbanBoard({ assignments, onSelect }: AssignmentKanbanBoardProps) {
  return (
    <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-6 gap-4 overflow-x-auto pb-4">
      {ASSIGNMENT_STATUS_ORDER.map((status) => {
        const meta = ASSIGNMENT_STATUS_META[status];
        const colItems = assignments.filter((a) => a.assignmentStatus === status);
        return (
          <div key={status} className="bg-slate-100 rounded-xl p-3.5 flex flex-col min-h-[420px]">
            <div className="flex items-center justify-between mb-3 pb-2 border-b border-slate-200">
              <span className="text-[11px] font-bold text-slate-800 font-display flex items-center gap-1.5">
                <span className={`w-2 h-2 rounded-full ${meta.dot}`} />
                {meta.label}
              </span>
              <span className="text-[10px] font-mono font-bold bg-slate-200 text-slate-600 px-1.5 py-0.5 rounded-full">{colItems.length}</span>
            </div>

            <div className="space-y-2.5 flex-1 overflow-y-auto max-h-[460px] pr-0.5">
              {colItems.map((a) => (
                <div
                  key={a.id}
                  onClick={() => onSelect(a)}
                  className="bg-white p-3 rounded-lg border border-slate-200 shadow-xs hover:border-brand-orange/40 transition-all cursor-pointer group"
                >
                  <span className="text-xs font-bold text-slate-900 group-hover:text-brand-orange transition-colors line-clamp-2">{a.taskTitle}</span>
                  <div className="border-t border-slate-100 pt-2 mt-2.5 flex items-center justify-between">
                    <span className="text-[10px] font-mono text-slate-400">#{a.id}</span>
                    {a.startedAt && (
                      <div className="flex items-center gap-1 text-[10px] text-slate-400 font-mono">
                        <Clock className="w-3 h-3 text-slate-300" />
                        <span>{new Date(a.startedAt).toLocaleDateString("vi-VN")}</span>
                      </div>
                    )}
                  </div>
                </div>
              ))}

              {colItems.length === 0 && (
                <div className="h-24 border-2 border-dashed border-slate-200 rounded-lg flex items-center justify-center text-[10px] text-slate-400 italic">
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
