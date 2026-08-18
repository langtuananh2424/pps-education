import { AssignmentStatus } from "./api";

export const ASSIGNMENT_STATUS_META: Record<AssignmentStatus, { dot: string; badge: string }> = {
  PENDING: { dot: "bg-slate-400", badge: "bg-slate-100 text-slate-600" },
  ACCEPTED: { dot: "bg-blue-400", badge: "bg-blue-50 text-blue-600" },
  IN_PROGRESS: { dot: "bg-amber-400", badge: "bg-amber-50 text-amber-600" },
  PENDING_REVIEW: { dot: "bg-sky-400", badge: "bg-sky-50 text-sky-600" },
  COMPLETED: { dot: "bg-emerald-400", badge: "bg-emerald-50 text-emerald-600" },
  DECLINED: { dot: "bg-rose-400", badge: "bg-rose-50 text-rose-600" }
};

/** Nhãn trạng thái dịch qua i18next namespace "task-workflow" — xem src/i18n/locales/{vi,en}/task-workflow.json. */
export function assignmentStatusLabel(t: (key: string) => string, status: AssignmentStatus): string {
  return t(`assignmentStatus.${status}`);
}

export const ASSIGNMENT_STATUS_ORDER: AssignmentStatus[] = [
  "PENDING",
  "ACCEPTED",
  "IN_PROGRESS",
  "PENDING_REVIEW",
  "COMPLETED",
  "DECLINED"
];

/** Khớp ĐÚNG ASSIGNEE_TRANSITIONS ở TaskService.java — chỉ áp dụng cho hành động của người NHẬN việc (tab "Việc tôi được giao"). */
export const ASSIGNEE_TRANSITIONS: Record<AssignmentStatus, AssignmentStatus[]> = {
  PENDING: ["ACCEPTED", "DECLINED", "IN_PROGRESS"],
  ACCEPTED: ["IN_PROGRESS"],
  IN_PROGRESS: ["PENDING_REVIEW"],
  PENDING_REVIEW: [],
  COMPLETED: [],
  DECLINED: []
};

const ASSIGNMENT_ACTION_KEYS: Partial<Record<AssignmentStatus, string>> = {
  ACCEPTED: "ACCEPTED",
  IN_PROGRESS: "IN_PROGRESS",
  PENDING_REVIEW: "PENDING_REVIEW",
  DECLINED: "DECLINED"
};

/** Nhãn hành động chuyển trạng thái dịch qua i18next — PENDING/COMPLETED không có hành động chuyển (trả rỗng như cũ). */
export function assignmentActionLabel(t: (key: string) => string, status: AssignmentStatus): string {
  const key = ASSIGNMENT_ACTION_KEYS[status];
  return key ? t(`assignmentAction.${key}`) : "";
}

/** Khớp ĐÚNG ASSIGNER_TRANSITIONS ở TaskService.java — hành động của người GIAO việc (tab "Việc tôi giao"). */
export const ASSIGNER_TRANSITIONS: Record<AssignmentStatus, AssignmentStatus[]> = {
  PENDING: [],
  ACCEPTED: [],
  IN_PROGRESS: [],
  PENDING_REVIEW: ["COMPLETED", "IN_PROGRESS"],
  COMPLETED: [],
  DECLINED: []
};
