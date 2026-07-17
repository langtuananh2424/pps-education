import React, { useEffect, useState } from "react";
import { LayoutGrid, Table2 } from "lucide-react";
import { ApiError } from "@/lib/apiClient";
import { listMyAssignments, TaskAssignmentResponse } from "../api";
import AssignmentKanbanBoard from "../components/AssignmentKanbanBoard";
import AssignmentSheetView from "../components/AssignmentSheetView";
import AssignmentDetailModal from "../components/AssignmentDetailModal";

type Tab = "assigned-to-me" | "assigned-by-me";
type ViewMode = "kanban" | "sheet";

export default function TaskWorkflowPage() {
  const [tab, setTab] = useState<Tab>("assigned-to-me");
  const [view, setView] = useState<ViewMode>("kanban");
  const [assignments, setAssignments] = useState<TaskAssignmentResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [selected, setSelected] = useState<TaskAssignmentResponse | null>(null);

  const load = () => {
    setLoading(true);
    listMyAssignments()
      .then(setAssignments)
      .catch((err) => setError(err instanceof ApiError ? err.message : "Không tải được danh sách công việc."))
      .finally(() => setLoading(false));
  };
  useEffect(load, []);

  return (
    <div className="space-y-6">
      <div className="border-b border-slate-200 pb-4">
        <h1 className="text-xl font-bold font-display tracking-tight text-slate-900">Điều Hành & Luồng Giao Việc (UC-06/07)</h1>
        <p className="text-xs text-slate-500 mt-1">Theo dõi tiến độ công việc được giao — xem dạng Kanban hoặc bảng chi tiết.</p>
      </div>

      <div className="flex items-center justify-between gap-4">
        <div className="flex border-b border-slate-200 gap-5 flex-1">
          {(
            [
              ["assigned-to-me", "Việc tôi được giao"],
              ["assigned-by-me", "Việc tôi giao"]
            ] as const
          ).map(([key, label]) => (
            <button
              key={key}
              onClick={() => setTab(key)}
              className={`pb-2.5 text-xs font-bold border-b-2 transition-all ${
                tab === key ? "border-brand-red text-brand-red" : "border-transparent text-slate-500 hover:text-slate-700"
              }`}
            >
              {label}
            </button>
          ))}
        </div>

        {tab === "assigned-to-me" && (
          <div className="flex items-center gap-1 bg-slate-100 rounded-lg p-1 shrink-0">
            <button
              onClick={() => setView("kanban")}
              className={`p-1.5 rounded-md ${view === "kanban" ? "bg-white shadow-xs text-brand-red" : "text-slate-400 hover:text-slate-600"}`}
              title="Xem Kanban"
            >
              <LayoutGrid className="w-4 h-4" />
            </button>
            <button
              onClick={() => setView("sheet")}
              className={`p-1.5 rounded-md ${view === "sheet" ? "bg-white shadow-xs text-brand-red" : "text-slate-400 hover:text-slate-600"}`}
              title="Xem dạng bảng"
            >
              <Table2 className="w-4 h-4" />
            </button>
          </div>
        )}
      </div>

      {tab === "assigned-to-me" ? (
        <>
          {error && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg">{error}</div>}

          {loading ? (
            <p className="text-xs text-slate-500">Đang tải...</p>
          ) : view === "kanban" ? (
            <AssignmentKanbanBoard assignments={assignments} onSelect={setSelected} />
          ) : (
            <AssignmentSheetView assignments={assignments} onSelect={setSelected} />
          )}
        </>
      ) : (
        <div className="bg-amber-50 border border-amber-100 rounded-xl p-5 text-xs text-amber-700">
          Tab "Việc tôi giao" (Kanban + bảng cho người quản lý theo dõi toàn bộ việc đã giao) đang chờ Backend bổ sung 1 endpoint
          để lấy được danh sách phân công theo từng người nhận (cần để duyệt/từ chối kết quả) — sẽ hoàn thiện ngay sau khi có.
        </div>
      )}

      {selected && (
        <AssignmentDetailModal
          assignment={selected}
          onClose={() => setSelected(null)}
          onChanged={(updated) => {
            setAssignments((prev) => prev.map((a) => (a.id === updated.id ? updated : a)));
            setSelected(updated);
          }}
        />
      )}
    </div>
  );
}
