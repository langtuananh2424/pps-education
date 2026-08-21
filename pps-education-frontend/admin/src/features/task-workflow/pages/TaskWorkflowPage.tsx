import React, { useEffect, useMemo, useState } from "react";
import { LayoutGrid, Plus, Table2 } from "lucide-react";
import { useTranslation } from "react-i18next";
import { ApiError } from "@/lib/apiClient";
import { useApp } from "@/context/AppContext";
import { formatDateTime } from "@/lib/i18nFormat";
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

const TASK_STATUS_BADGE: Record<TaskStatus, string> = {
  OPEN: "bg-slate-100 text-slate-600",
  IN_PROGRESS: "bg-amber-50 text-amber-600",
  COMPLETED: "bg-emerald-50 text-emerald-600",
  CANCELLED: "bg-slate-100 text-slate-400",
  OVERDUE: "bg-rose-50 text-rose-600"
};

export default function TaskWorkflowPage() {
  const { t, i18n } = useTranslation("task-workflow");
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
      .catch((err) => setError(err instanceof ApiError ? err.message : t("page.loadError")))
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
      .catch((err) => setCreatedError(err instanceof ApiError ? err.message : t("page.loadCreatedError")))
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
        <h1 className="text-xl font-bold font-display tracking-tight text-slate-900">{t("page.title")}</h1>
        <p className="text-xs text-slate-500 mt-1">{t("page.subtitle")}</p>
      </div>

      <div className="flex items-center justify-between gap-4">
        <div className="flex border-b border-slate-200 gap-5 flex-1">
          {(
            [
              ["assigned-to-me", t("page.tabAssignedToMe")],
              ...(canCreateTask ? ([["assigned-by-me", t("page.tabAssignedByMe")]] as const) : [])
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

        {tab === "assigned-to-me" ? (
          <div className="flex items-center gap-1 bg-slate-100 rounded-lg p-1 shrink-0">
            <button
              onClick={() => setView("kanban")}
              className={`p-1.5 rounded-md ${view === "kanban" ? "bg-white shadow-xs text-brand-red" : "text-slate-400 hover:text-slate-600"}`}
              title={t("page.kanbanView")}
            >
              <LayoutGrid className="w-4 h-4" />
            </button>
            <button
              onClick={() => setView("sheet")}
              className={`p-1.5 rounded-md ${view === "sheet" ? "bg-white shadow-xs text-brand-red" : "text-slate-400 hover:text-slate-600"}`}
              title={t("page.sheetView")}
            >
              <Table2 className="w-4 h-4" />
            </button>
          </div>
        ) : (
          <button
            onClick={() => setShowCreateModal(true)}
            className="flex items-center gap-1.5 bg-brand-red hover:bg-brand-red/90 text-white text-xs font-bold px-3.5 py-2 rounded-lg shrink-0"
          >
            <Plus className="w-4 h-4" /> {t("page.newTaskButton")}
          </button>
        )}
      </div>

      {tab === "assigned-to-me" ? (
        <>
          {error && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg">{error}</div>}

          <div className="flex items-center gap-2 flex-wrap">
            <span className="text-[10px] uppercase font-bold text-slate-400">{t("page.filterByDate")}</span>
            <div className="w-36">
              <DatePicker value={dateFrom} onChange={setDateFrom} max={dateTo || undefined} />
            </div>
            <span className="text-xs text-slate-400">{t("page.to")}</span>
            <div className="w-36">
              <DatePicker value={dateTo} onChange={setDateTo} min={dateFrom || undefined} />
            </div>
            {(dateFrom || dateTo) && (
              <button
                onClick={() => {
                  setDateFrom("");
                  setDateTo("");
                }}
                className="text-[11px] font-semibold text-brand-red hover:underline"
              >
                {t("page.clearFilter")}
              </button>
            )}
            <span className="text-[11px] text-slate-400 ml-auto">
              {t("page.filteredCount", { filtered: filteredAssignments.length, total: assignments.length })}
            </span>
          </div>

          {loading ? (
            <p className="text-xs text-slate-500">{t("page.loading")}</p>
          ) : view === "kanban" ? (
            <AssignmentKanbanBoard assignments={filteredAssignments} onSelect={setSelected} />
          ) : (
            <AssignmentSheetView assignments={filteredAssignments} onSelect={setSelected} />
          )}
        </>
      ) : (
        <>
          {createdError && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg">{createdError}</div>}

          {!createdLoading && (
            <p className="text-[11px] text-slate-400 italic">
              {isOverviewScope ? t("page.overviewScopeNote") : t("page.ownScopeNote")}
            </p>
          )}

          {createdLoading ? (
            <p className="text-xs text-slate-500">{t("page.loading")}</p>
          ) : createdTasks.length === 0 ? (
            <div className="bg-white border border-slate-200 rounded-xl p-10 text-center text-xs text-slate-400 italic">
              {t("page.emptyCreated")}
            </div>
          ) : (
            <>
              <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
                {pageCreatedTasks.map((task) => {
                  const badgeClass = TASK_STATUS_BADGE[task.status];
                  return (
                    <button
                      key={task.id}
                      onClick={() => setSelectedTask(task)}
                      className="text-left bg-white border border-slate-200 hover:border-brand-red/40 rounded-xl p-4 space-y-1.5 transition-colors"
                    >
                      <div className="flex items-center justify-between gap-2">
                        <span className="font-bold text-slate-800 text-sm">{task.title}</span>
                        <span className={`text-[10px] font-bold px-2 py-0.5 rounded-full shrink-0 ${badgeClass}`}>{t(`taskStatus.${task.status}`)}</span>
                      </div>
                      <p className="text-[11px] text-slate-400 font-mono">{task.taskCode}</p>
                      {task.dueAt && <p className="text-[11px] text-slate-500">{t("page.dueLabel", { date: formatDateTime(task.dueAt, i18n.language) })}</p>}
                    </button>
                  );
                })}
              </div>
              <div className="bg-white border border-slate-200 rounded-xl overflow-hidden">
                <Pagination
                  page={createdPage}
                  pageSize={createdPageSize}
                  totalElements={createdTasks.length}
                  itemLabel={t("page.itemLabel")}
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
            showToast(t("page.taskCreatedToast"));
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
