import React, { useState } from "react";
import { X } from "lucide-react";
import Modal from "@/components/ui/Modal";
import Button from "@/components/ui/Button";
import { ApiError } from "@/lib/apiClient";
import { searchUsers, UserListItemResponse } from "@/features/system-admin/api";
import { CreateTaskRequest, TaskPriority, TaskResponse, TaskType, createTask } from "../api";
import Select from "@/components/ui/Select";

const inputClass = "w-full bg-slate-50 border border-slate-200 text-xs p-2.5 rounded-lg focus:outline-none";
const labelClass = "text-[10px] uppercase font-bold text-slate-500 block mb-1";

const TASK_TYPE_LABEL: Record<TaskType, string> = { GENERAL: "Thông thường", URGENT: "Khẩn cấp", RECURRING: "Lặp lại", PROJECT: "Dự án" };
const TASK_PRIORITY_LABEL: Record<TaskPriority, string> = { LOW: "Thấp", NORMAL: "Bình thường", HIGH: "Cao", URGENT: "Khẩn cấp" };

/**
 * UC-06 Tác nhân nhận việc = "nhân sự" — loại STUDENT/PARENT khỏi kết quả tìm kiếm. Không dùng
 * GET /api/employees (yêu cầu quyền hrm.manage, chỉ HR_MANAGER có — SITE_MANAGER/HEAD_ACADEMIC
 * tạo việc được nhưng KHÔNG có quyền đó, gọi sẽ 403) — lọc ngay trên kết quả /api/users đã có sẵn
 * (roles trả kèm theo mỗi user, đủ để loại 2 vai trò không phải nhân sự).
 */
function isPersonnel(u: UserListItemResponse): boolean {
  return !u.roles.some((r) => r.code === "STUDENT" || r.code === "PARENT");
}

interface CreateTaskModalProps {
  onClose: () => void;
  onCreated: (task: TaskResponse) => void;
}

/**
 * UC-06 Main Flow bước 1-4: tạo công việc + giao cho 1-nhiều nhân sự. Phạm vi thật (V47, chốt
 * 2026-07-23): company-wide (task.manage/EXECUTIVE) giao cho bất kỳ ai; còn lại chỉ giao được cho
 * nhân sự thuộc phòng ban mà CHÍNH actor đang là Trưởng phòng (departments.head_user_id = actor) —
 * không phải "cùng phòng ban" nói chung. Không tự lọc người nhận ở FE (không có API tra "phòng mình
 * đang làm trưởng" phía client) — cứ để chọn tự do qua tìm kiếm, ngoài phạm vi BE tự chặn 403
 * (AssigneeOutsideDepartmentException), hiện rõ message qua ApiError.
 */
export default function CreateTaskModal({ onClose, onCreated }: CreateTaskModalProps) {
  const [title, setTitle] = useState("");
  const [description, setDescription] = useState("");
  const [taskType, setTaskType] = useState<TaskType>("GENERAL");
  const [priority, setPriority] = useState<TaskPriority>("NORMAL");
  const [dueAt, setDueAt] = useState("");
  const [tagsInput, setTagsInput] = useState("");

  const [query, setQuery] = useState("");
  const [results, setResults] = useState<UserListItemResponse[]>([]);
  const [searching, setSearching] = useState(false);
  const [assignees, setAssignees] = useState<UserListItemResponse[]>([]);

  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleSearch = (q: string) => {
    setQuery(q);
    if (!q.trim()) {
      setResults([]);
      return;
    }
    setSearching(true);
    searchUsers({ keyword: q.trim() }, 0, 8)
      .then((res) => setResults(res.content.filter((u) => isPersonnel(u) && !assignees.some((a) => a.id === u.id))))
      .finally(() => setSearching(false));
  };

  const addAssignee = (u: UserListItemResponse) => {
    setAssignees((prev) => [...prev, u]);
    setQuery("");
    setResults([]);
  };

  const removeAssignee = (userId: number) => setAssignees((prev) => prev.filter((a) => a.id !== userId));

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!title.trim() || assignees.length === 0) {
      setError("Vui lòng nhập tiêu đề và chọn ít nhất 1 người nhận.");
      return;
    }
    setSubmitting(true);
    setError(null);
    try {
      const request: CreateTaskRequest = {
        title: title.trim(),
        description: description.trim() || undefined,
        assigneeUserIds: assignees.map((a) => a.id),
        taskType,
        priority,
        dueAt: dueAt ? new Date(dueAt).toISOString() : undefined,
        tags: tagsInput.trim() ? tagsInput.split(",").map((t) => t.trim()).filter(Boolean) : undefined
      };
      const created = await createTask(request);
      onCreated(created);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Giao việc thất bại.");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Modal open onClose={onClose} title="Giao việc mới" description="Chỉ giao được cho nhân sự trong phạm vi phòng ban mình phụ trách." size="lg">
      <form onSubmit={handleSubmit} className="space-y-4">
        {error && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg">{error}</div>}

        <div>
          <label className={labelClass}>Tiêu đề *</label>
          <input value={title} onChange={(e) => setTitle(e.target.value)} className={inputClass} required />
        </div>

        <div>
          <label className={labelClass}>Mô tả công việc</label>
          <textarea value={description} onChange={(e) => setDescription(e.target.value)} rows={3} className={inputClass} />
        </div>

        <div>
          <label className={labelClass}>Người nhận việc *</label>
          {assignees.length > 0 && (
            <div className="flex flex-wrap gap-1.5 mb-2">
              {assignees.map((a) => (
                <span key={a.id} className="flex items-center gap-1 bg-orange-50 border border-orange-200 text-brand-red text-[11px] font-semibold px-2 py-1 rounded-lg">
                  {a.fullName}
                  <button type="button" onClick={() => removeAssignee(a.id)} className="hover:text-rose-600">
                    <X className="w-3 h-3" />
                  </button>
                </span>
              ))}
            </div>
          )}
          <div className="relative">
            <input
              value={query}
              onChange={(e) => handleSearch(e.target.value)}
              placeholder="Tìm nhân sự theo username / họ tên..."
              name="task-assignee-lookup"
              autoComplete="off"
              autoCorrect="off"
              autoCapitalize="off"
              spellCheck={false}
              className={inputClass}
            />
            {searching && <p className="text-[10px] text-slate-400 mt-1">Đang tìm...</p>}
            {query.trim() && !searching && (
              <div className="absolute z-10 mt-1 w-full bg-white border border-slate-200 rounded-lg shadow-lg divide-y divide-slate-100 max-h-56 overflow-y-auto">
                {results.length === 0 ? (
                  <p className="px-3 py-2 text-xs text-slate-400 italic">Không tìm thấy nhân sự phù hợp.</p>
                ) : (
                  results.map((u) => (
                    <button key={u.id} type="button" onClick={() => addAssignee(u)} className="w-full text-left px-3 py-2 hover:bg-slate-50 text-xs flex items-center justify-between gap-2">
                      <span>
                        {u.fullName} <span className="text-slate-400">({u.username})</span>
                      </span>
                      <span className="text-[9px] font-bold text-teal-700 bg-teal-50 px-1.5 py-0.5 rounded-full shrink-0">
                        {u.roles.map((r) => r.name).join(", ") || "Nhân sự"}
                      </span>
                    </button>
                  ))
                )}
              </div>
            )}
          </div>
        </div>

        <div className="grid grid-cols-2 gap-3">
          <div>
            <label className={labelClass}>Loại việc</label>
            <Select value={taskType} onChange={(e) => setTaskType(e.target.value as TaskType)} className={inputClass}>
              {Object.entries(TASK_TYPE_LABEL).map(([value, label]) => (
                <option key={value} value={value}>
                  {label}
                </option>
              ))}
            </Select>
          </div>
          <div>
            <label className={labelClass}>Độ ưu tiên</label>
            <Select value={priority} onChange={(e) => setPriority(e.target.value as TaskPriority)} className={inputClass}>
              {Object.entries(TASK_PRIORITY_LABEL).map(([value, label]) => (
                <option key={value} value={value}>
                  {label}
                </option>
              ))}
            </Select>
          </div>
        </div>

        <div className="grid grid-cols-2 gap-3">
          <div>
            <label className={labelClass}>Hạn hoàn thành</label>
            <input type="datetime-local" value={dueAt} onChange={(e) => setDueAt(e.target.value)} className={inputClass} />
          </div>
          <div>
            <label className={labelClass}>Tags (phân cách bằng dấu phẩy)</label>
            <input value={tagsInput} onChange={(e) => setTagsInput(e.target.value)} placeholder="VD: gấp, báo cáo" className={inputClass} />
          </div>
        </div>

        <div className="flex justify-end gap-2 pt-2 border-t border-slate-100">
          <Button type="button" variant="secondary" onClick={onClose}>
            Hủy
          </Button>
          <Button type="submit" variant="primary" disabled={submitting}>
            {submitting ? "Đang giao..." : "Giao việc"}
          </Button>
        </div>
      </form>
    </Modal>
  );
}
