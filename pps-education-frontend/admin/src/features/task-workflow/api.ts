import { apiRequest } from "@/lib/apiClient";

/** UC-06/UC-07 — khớp TaskAssignment.Status thật (TaskAssignment.java), KHÔNG phải 4 cột tự đặt trước đây. */
export type AssignmentStatus = "PENDING" | "ACCEPTED" | "IN_PROGRESS" | "PENDING_REVIEW" | "COMPLETED" | "DECLINED";

/** Khớp Task.Status thật (Task.java) — dùng cho tab "Việc tôi giao" (chưa build ở lần này). */
export type TaskStatus = "OPEN" | "IN_PROGRESS" | "COMPLETED" | "CANCELLED" | "OVERDUE";

export type TaskType = "GENERAL" | "URGENT" | "RECURRING" | "PROJECT";
export type TaskPriority = "LOW" | "NORMAL" | "HIGH" | "URGENT";

/** Khớp TaskResponse thật — GET /api/tasks/{id}, GET /api/tasks/created-by-me, GET /api/tasks/overview. */
export interface TaskResponse {
  id: number;
  taskCode: string;
  title: string;
  description: string | null;
  createdBy: number;
  createdByFullName: string;
  departmentId: number | null;
  taskType: TaskType;
  priority: TaskPriority;
  status: TaskStatus;
  dueAt: string | null;
  completedAt: string | null;
  parentTaskId: number | null;
  tags: string[] | null;
}

/** Khớp TaskAssignmentResponse thật — GET /api/tasks/my-assignments, PUT .../status. */
export interface TaskAssignmentResponse {
  id: number;
  taskId: number;
  taskTitle: string;
  assigneeUserId: number;
  assigneeFullName: string;
  assignedAt: string;
  assignmentStatus: AssignmentStatus;
  progressPercent: number | null;
  startedAt: string | null;
  completedAt: string | null;
  declineReason: string | null;
}

export interface CreateTaskRequest {
  title: string;
  description?: string;
  assigneeUserIds: number[];
  taskType?: TaskType;
  priority?: TaskPriority;
  dueAt?: string;
  tags?: string[];
}

export interface UpdateAssignmentStatusRequest {
  status: AssignmentStatus;
  comment?: string;
}

export interface TaskCommentResponse {
  id: number;
  taskId: number;
  commenterUserId: number;
  commenterFullName: string;
  content: string;
  attachmentUrl: string | null;
  createdAt: string;
}

export interface AddTaskCommentRequest {
  content: string;
  attachmentUrl?: string;
}

export interface TaskAttachmentResponse {
  id: number;
  taskId: number;
  fileUrl: string;
  fileName: string;
  uploadedBy: number | null;
}

export interface AddTaskAttachmentRequest {
  fileUrl: string;
  fileName: string;
}

/** UC-07 Main Flow bước 1: không gian làm việc Kanban của người nhận việc — "Việc tôi được giao". */
export function listMyAssignments(): Promise<TaskAssignmentResponse[]> {
  return apiRequest<TaskAssignmentResponse[]>("/tasks/my-assignments");
}

/** UC-06 Main Flow bước 5 — chỉ đúng việc CHÍNH actor tự tạo (dùng làm fallback khi listOverview() 403). */
export function listTasksCreatedByMe(): Promise<TaskResponse[]> {
  return apiRequest<TaskResponse[]>("/tasks/created-by-me");
}

/**
 * UC-06/07 (bổ sung 2026-07-23) — "Việc tôi giao" tầng đầy đủ: company-wide (task.overview.company,
 * OPS_MANAGER/HR_MANAGER/EXECUTIVE/SUPER_ADMIN) thấy toàn công ty; Trưởng phòng
 * (departments.head_user_id = actor) thấy toàn bộ việc phòng mình bất kể ai giao. 403 nếu không thuộc
 * 2 tầng trên (không có quyền overview, không làm trưởng phòng nào) — khi đó dùng listTasksCreatedByMe().
 */
export function listOverview(): Promise<TaskResponse[]> {
  return apiRequest<TaskResponse[]>("/tasks/overview");
}

export interface CancelTaskRequest {
  reason?: string;
}

/** UC-06/07 (bổ sung): hủy việc (CANCELLED, giữ lịch sử, không xóa cứng) — người giao hoặc task.manage. */
export function cancelTask(taskId: number, request?: CancelTaskRequest): Promise<TaskResponse> {
  return apiRequest<TaskResponse>(`/tasks/${taskId}/cancel`, { method: "POST", body: JSON.stringify(request ?? {}) });
}

export function getTask(taskId: number): Promise<TaskResponse> {
  return apiRequest<TaskResponse>(`/tasks/${taskId}`);
}

export function createTask(request: CreateTaskRequest): Promise<TaskResponse> {
  return apiRequest<TaskResponse>("/tasks", { method: "POST", body: JSON.stringify(request) });
}

/** UC-07 Main Flow bước 2-4, A2: chuyển trạng thái 1 task_assignment (đúng state machine BE). */
export function updateAssignmentStatus(assignmentId: number, request: UpdateAssignmentStatusRequest): Promise<TaskAssignmentResponse> {
  return apiRequest<TaskAssignmentResponse>(`/task-assignments/${assignmentId}/status`, {
    method: "PUT",
    body: JSON.stringify(request)
  });
}

export function listComments(taskId: number): Promise<TaskCommentResponse[]> {
  return apiRequest<TaskCommentResponse[]>(`/tasks/${taskId}/comments`);
}

export function addComment(taskId: number, request: AddTaskCommentRequest): Promise<TaskCommentResponse> {
  return apiRequest<TaskCommentResponse>(`/tasks/${taskId}/comments`, { method: "POST", body: JSON.stringify(request) });
}

export function listAttachments(taskId: number): Promise<TaskAttachmentResponse[]> {
  return apiRequest<TaskAttachmentResponse[]>(`/tasks/${taskId}/attachments`);
}

export function addAttachment(taskId: number, request: AddTaskAttachmentRequest): Promise<TaskAttachmentResponse> {
  return apiRequest<TaskAttachmentResponse>(`/tasks/${taskId}/attachments`, { method: "POST", body: JSON.stringify(request) });
}

/** UC-07 A2: người giao xem toàn bộ phân công của 1 việc (mọi người nhận + trạng thái) — chỉ createdBy gọi được (BE tự chặn). */
export function listAssignments(taskId: number): Promise<TaskAssignmentResponse[]> {
  return apiRequest<TaskAssignmentResponse[]>(`/tasks/${taskId}/assignments`);
}

export interface ReassignTaskRequest {
  fromAssignmentId: number;
  newAssigneeUserId: number;
  comment?: string;
}

/** UC-06/07 A3: người giao giao lại 1 phân công đã DECLINED cho nhân sự khác trong phạm vi phòng ban. */
export function reassignTask(taskId: number, request: ReassignTaskRequest): Promise<TaskAssignmentResponse> {
  return apiRequest<TaskAssignmentResponse>(`/tasks/${taskId}/reassign`, { method: "POST", body: JSON.stringify(request) });
}
