import React, { useState } from "react";
import { Plus } from "lucide-react";
import { Task } from "@/types";
import { mockEmployees, mockTasks } from "@/data/mockData";
import TaskFormPanel from "../components/TaskFormPanel";
import KanbanBoard from "../components/KanbanBoard";
import TaskDetailModal from "../components/TaskDetailModal";

export default function TaskWorkflowPage() {
  const [tasks, setTasks] = useState<Task[]>(mockTasks);
  const [showTaskForm, setShowTaskForm] = useState(false);
  const [selectedTask, setSelectedTask] = useState<Task | null>(null);

  const handleAssignTask = (input: { title: string; description: string; assignedToId: string; deadline: string }) => {
    const assignee = mockEmployees.find((emp) => emp.id === input.assignedToId);
    if (!assignee) return;

    const newTask: Task = {
      id: `TSK-00${tasks.length + 1}`,
      title: input.title,
      description: input.description,
      assignedTo: assignee.fullName,
      assignedBy: "Nguyễn Văn Hùng (OPS_MANAGER)",
      departmentId: "DEPT-OPS",
      deadline: input.deadline,
      status: "TODO",
      createdAt: new Date().toISOString().substring(0, 10),
      comments: []
    };

    setTasks((prev) => [...prev, newTask]);
    setShowTaskForm(false);
  };

  const handleMoveTask = (taskId: string, newStatus: Task["status"]) => {
    setTasks((prev) => prev.map((t) => (t.id === taskId ? { ...t, status: newStatus } : t)));
    if (selectedTask?.id === taskId) {
      setSelectedTask((prev) => (prev ? { ...prev, status: newStatus } : prev));
    }
  };

  const handleAddComment = (content: string) => {
    if (!selectedTask) return;
    const updatedComments = [
      ...(selectedTask.comments || []),
      { id: `C-${Date.now()}`, authorName: "Nguyễn Văn Hùng (OPS_MANAGER)", content, createdAt: "Vừa xong" }
    ];

    setTasks((prev) => prev.map((t) => (t.id === selectedTask.id ? { ...t, comments: updatedComments } : t)));
    setSelectedTask({ ...selectedTask, comments: updatedComments });
  };

  return (
    <div className="space-y-6">
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 border-b border-slate-200 pb-4">
        <div>
          <h1 className="text-xl font-bold font-display tracking-tight text-slate-900">Điều Hành & Luồng Giao Việc Phân Cấp (Kanban)</h1>
          <p className="text-xs text-slate-500 mt-1">
            Giao đầu việc, đính kèm tài liệu và cập nhật tiến độ công việc nội bộ liên phòng ban (UC-06/07).
          </p>
        </div>

        <button
          onClick={() => setShowTaskForm(!showTaskForm)}
          className="bg-brand-gradient text-white hover:opacity-95 font-semibold text-xs px-4 py-2 rounded-lg flex items-center gap-1.5 shadow-soft self-start"
        >
          <Plus className="w-4 h-4" />
          <span>Giao việc mới (UC-06)</span>
        </button>
      </div>

      {showTaskForm && <TaskFormPanel employees={mockEmployees} onSubmit={handleAssignTask} onCancel={() => setShowTaskForm(false)} />}

      <KanbanBoard tasks={tasks} onSelectTask={setSelectedTask} />

      {selectedTask && (
        <TaskDetailModal
          task={selectedTask}
          onClose={() => setSelectedTask(null)}
          onChangeStatus={(status) => handleMoveTask(selectedTask.id, status)}
          onAddComment={handleAddComment}
        />
      )}
    </div>
  );
}
