import React from "react";
import { Clock } from "lucide-react";
import { Task } from "@/types";

interface TaskCardProps {
  task: Task;
  onClick: () => void;
}

export default function TaskCard({ task, onClick }: TaskCardProps) {
  return (
    <div
      onClick={onClick}
      className="bg-white p-4 rounded-lg border border-slate-200 shadow-xs hover:border-brand-orange/40 transition-all cursor-pointer group"
    >
      <div className="flex items-start justify-between gap-2">
        <span className="text-sm font-bold text-slate-900 group-hover:text-brand-orange transition-colors">{task.title}</span>
      </div>

      <p className="text-sm text-slate-500 mt-1 line-clamp-2 leading-relaxed">{task.description}</p>

      <div className="border-t border-slate-100 pt-2.5 mt-3 flex items-center justify-between">
        <div className="flex items-center gap-1.5">
          <div className="w-5 h-5 rounded-full bg-brand-gradient text-white text-[9px] font-bold flex items-center justify-center shrink-0">
            {task.assignedTo.charAt(0)}
          </div>
          <span className="text-sm font-semibold text-slate-600 truncate max-w-[80px]">{task.assignedTo}</span>
        </div>

        <div className="flex items-center gap-1 text-sm text-slate-400 font-mono">
          <Clock className="w-3 h-3 text-slate-300" />
          <span>{task.deadline}</span>
        </div>
      </div>
    </div>
  );
}
