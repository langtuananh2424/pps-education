import React, { useEffect, useMemo, useState } from "react";
import { LayoutGrid, Plus, Table2 } from "lucide-react";
import { ApiError } from "@/lib/apiClient";
import { useApp } from "@/context/AppContext";
import { listMyAssignments, listOverview, listTasksCreatedByMe, TaskAssignmentResponse, TaskResponse, TaskStatus } from "../api";
import AssignmentKanbanBoard from "../components/AssignmentKanbanBoard";
import AssignmentSheetView from "../components/AssignmentSheetView";
import AssignmentDetailModal from "../components/AssignmentDetailModal";
import CreateTaskModal from "../components/CreateTaskModal";
import CreatedTaskDetailModal from "../components/CreatedTaskDetailModal";
import { useToast } from "@/lib/useToast";
import Toast from "@/components/ui/Toast";
import DatePicker from "@/components/ui/DatePicker";
import Pagination from "@/components/ui/Pagination";

type Tab = "assigned-to-me" | "assigned-by-me";
type ViewMode = "kanban" | "sheet";

const TASK_STATUS_META: Record<TaskStatus, { label: string; badge: string }> = {
  OPEN: { label: "Đang mở", badge: "bg-slate-100 text-slate-600" },
  IN_PROGRESS: { label: "Đang làm", badge: "bg-amber-50 text-amber-600" },
  COMPLETED: { label: "Hoàn thành", badge: "bg-emerald-50 text-emerald-600" },
  CANCELLED: { label: "Đã hủy", badge: "bg-slate-100 text-slate-400" },
  OVERDUE: { label: "Quá hạn", badge: "bg-rose-50 text-rose-600" }
};

export default function TaskWorkflowPage() {
  // UC-06 (V47): task.create cũ đã tách thành task.assign (giao việc)/task.manage (quản trị cao nhất).
  // Nhân viên/Giáo viên thường (chỉ có task.receive) không có quyền này — tab "Việc tôi giao" + nút
  // "Giao việc mới" phải ẩn hẳn, không chỉ chặn lúc submit (BE đã chặn đúng ở createTask).
  const { hasPermission } = useApp();
  const canCreateTask = hasPermission("task.assign") || hasPermission("task.manage");
  // true nếu đang xem tổng quan công ty/phòng ban (GET /api/tasks/overview) — false nếu fallback về
  // "chỉ việc chính mình tự tạo" (actor có task.assign nhưng KHÔNG làm trưởng phòng nào, BE 403 overview).
  const [isOverviewScope, setIsOverviewScope] = useState(false);
  const [tab, setTab] = useState<Tab>("assigned-to-me");
  const [view, setView] = useState<ViewMode>("kanban");
  const [assignments, setAssignments] = useState<TaskAssignmentResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [selected, setSelected] = useState<TaskAssignmentResponse | null>(null);
  // Lọc lịch sử theo ngày được giao (assignedAt) — field duy nhất luôn có giá trị (không như startedAt/completedAt chỉ có khi đã xử lý).
  const [dateFrom, setDateFrom] = useState("");
  const [dateTo, setDateTo] = useState("");

  const [createdTasks, setCreatedTasks] = useState<TaskResponse[]>([]);
  const [createdLoading, setCreatedLoading] = useState(true);
  const [createdError, setCreatedError] = useState<string | null>(null);
  const [selectedTask, setSelectedTask] = useState<TaskResponse | null>(null);
  const [showCreateModal, setShowCreateModal] = useState(false);
  const { message: toastMessage, showToast } = useToast();

  const load = () => {
    setLoading(true);
    listMyAssignments()
      .then(setAssignments)
      .catch((err) => setError(err instanceof ApiError ? err.message : "Không tải được danh sách công việc."))
      .finally(() => setLoading(false));
  };
  useEffect(load, []);

  const loadCreated = () => {
    if (!canCreateTask) {
      setCreatedLoading(false);
      return;
    }
    setCreatedLoading(true);
    setCreatedError(null);
    listOverview()
      .then((tasks) => {
        setIsOverviewScope(true);
        setCreatedTasks(tasks);
      })
      .catch((err) => {
        if (err instanceof ApiError && err.status === 403) {
          // Có task.assign nhưng không phải trưởng phòng nào + không có task.overview.company —
          // BE chặn đúng chủ ý (xem TaskService.listOverview) — quay về "chỉ việc chính mình tự giao".
          setIsOverviewScope(false);
          return listTasksCreatedByMe().then(setCreatedTasks);
        }
        throw err;
      })
      .catch((err) => setCreatedError(err instanceof ApiError ? err.message : "Không tải được danh sách việc đã giao."))
      .finally(() => setCreatedLoading(false));
  };
  useEffect(loadCreated, [canCreateTask]);

  // "Việc tôi giao" khi isOverviewScope=true là tổng quan toàn phòng ban/công ty, tích lũy theo thời
  // gian — backend GET /tasks/overview chưa hỗ trợ phân trang, phân trang phía client. loadCreated()
  // luôn tải lại toàn bộ (không patch tại chỗ) nên reset trang theo identity createdTasks là an toàn.
  const [createdPage, setCreatedPage] = useState(0);
  const [createdPageSize, setCreatedPageSize] = useState(20);
  useEffect(() => setCreatedPage(0), [createdTasks]);
  const pageCreatedTasks = createdTasks.slice(createdPage * createdPageSize, (createdPage + 1) * createdPageSize);

  const filteredAssignments = useMemo(() => {
    if (!dateFrom && !dateTo) return assignments;
    return assignments.filter((a) => {
      const assignedDate = a.assignedAt.slice(0, 10);
      if (dateFrom && assignedDate < dateFrom) return false;
      if (dateTo && assignedDate > dateTo) return false;
      return true;
    });
  }, [assignments, dateFrom, dateTo]);

  return (
    <div className="space-y-6">
      <div className="border-b border-slate-200 pb-4">
        <h1 className="text-xl font-bold font-display tracking-tight text-slate-900">Điều Hành & Luồng Giao Việc</h1>
        <p className="text-sm text-slate-500 mt-1">Theo dõi tiến độ công việc được giao — xem dạng Kanban hoặc bảng chi tiết.</p>
      </div>

      <div className="flex items-center justify-between gap-4">
        <div className="flex border-b border-slate-200 gap-5 flex-1">
          {(
            [
              ["assigned-to-me", "Việc tôi được giao"],
              ...(canCreateTask ? ([["assigned-by-me", "Việc tôi giao"]] as const) : [])
            ] as const
          ).map(([key, label]) => (
            <button
              key={key}
              onClick={() => setTab(key)}
              className={`pb-2.5 text-sm font-bold border-b-2 transition-all ${
                tab === key ? "border-brand-red text-brand-red" : "border-transparent text-slate-500 hover:text-slate-700"
              }`}
            >
              {label}
            </button>
          ))}
        </div>

        {tab === "assigned-to-me" ? (
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
        ) : (
          <button
            onClick={() => setShowCreateModal(true)}
            className="flex items-center gap-1.5 bg-brand-red hover:bg-brand-red/90 text-white text-sm font-bold px-3.5 py-2 rounded-lg shrink-0"
          >
            <Plus className="w-4 h-4" /> Giao việc mới
          </button>
        )}
      </div>

      {tab === "assigned-to-me" ? (
        <>
          {error && <div className="text-sm text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg">{error}</div>}

          <div className="flex items-center gap-2 flex-wrap">
            <span className="text-sm uppercase font-bold text-slate-400">Lọc theo ngày được giao:</span>
            <div className="w-36">
              <DatePicker value={dateFrom} onChange={setDateFrom} max={dateTo || undefined} />
            </div>
            <span className="text-sm text-slate-400">đến</span>
            <div className="w-36">
              <DatePicker value={dateTo} onChange={setDateTo} min={dateFrom || undefined} />
            </div>
            {(dateFrom || dateTo) && (
              <button
                onClick={() => {
                  setDateFrom("");
                  setDateTo("");
                }}
                className="text-sm font-semibold text-brand-red hover:underline"
              >
                Xóa lọc
              </button>
            )}
            <span className="text-sm text-slate-400 ml-auto">{filteredAssignments.length}/{assignments.length} việc</span>
          </div>

          {loading ? (
            <p className="text-sm text-slate-500">Đang tải...</p>
          ) : view === "kanban" ? (
            <AssignmentKanbanBoard assignments={filteredAssignments} onSelect={setSelected} />
          ) : (
            <AssignmentSheetView assignments={filteredAssignments} onSelect={setSelected} />
          )}
        </>
      ) : (
        <>
          {createdError && <div className="text-sm text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg">{createdError}</div>}

          {!createdLoading && (
            <p className="text-sm text-slate-400 italic">
              {isOverviewScope
                ? "Đang xem tổng quan toàn bộ việc thuộc phạm vi của bạn (phòng ban mình làm trưởng, hoặc toàn công ty)."
                : "Chỉ đang xem việc do chính bạn tự giao (chưa được gán làm trưởng phòng nào)."}
            </p>
          )}

          {createdLoading ? (
            <p className="text-sm text-slate-500">Đang tải...</p>
          ) : createdTasks.length === 0 ? (
            <div className="bg-white border border-slate-200 rounded-xl p-10 text-center text-sm text-slate-400 italic">
              Chưa giao việc nào — bấm "Giao việc mới" để bắt đầu.
            </div>
          ) : (
            <>
              <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
                {pageCreatedTasks.map((t) => {
                  const meta = TASK_STATUS_META[t.status];
                  return (
                    <button
                      key={t.id}
                      onClick={() => setSelectedTask(t)}
                      className="text-left bg-white border border-slate-200 hover:border-brand-red/40 rounded-xl p-4 space-y-1.5 transition-colors"
                    >
                      <div className="flex items-center justify-between gap-2">
                        <span className="font-bold text-slate-800 text-sm">{t.title}</span>
                        <span className={`text-sm font-bold px-2 py-0.5 rounded-full shrink-0 ${meta.badge}`}>{meta.label}</span>
                      </div>
                      <p className="text-sm text-slate-400 font-mono">{t.taskCode}</p>
                      {t.dueAt && <p className="text-sm text-slate-500">Hạn: {new Date(t.dueAt).toLocaleString("vi-VN")}</p>}
                    </button>
                  );
                })}
              </div>
              <div className="bg-white border border-slate-200 rounded-xl overflow-hidden">
                <Pagination
                  page={createdPage}
                  pageSize={createdPageSize}
                  totalElements={createdTasks.length}
                  itemLabel="việc"
                  onPageChange={setCreatedPage}
                  onPageSizeChange={(size) => {
                    setCreatedPageSize(size);
                    setCreatedPage(0);
                  }}
                />
              </div>
            </>
          )}
        </>
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

      {showCreateModal && (
        <CreateTaskModal
          onClose={() => setShowCreateModal(false)}
          onCreated={() => {
            setShowCreateModal(false);
            loadCreated();
            showToast("Đã giao việc mới thành công!");
          }}
        />
      )}

      {selectedTask && (
        <CreatedTaskDetailModal task={selectedTask} onClose={() => setSelectedTask(null)} onTaskChanged={loadCreated} />
      )}

      <Toast message={toastMessage} />
    </div>
  );
}
