import { AssignmentStatus } from "./api";

export const ASSIGNMENT_STATUS_META: Record<AssignmentStatus, { label: string; dot: string; badge: string }> = {
  PENDING: { label: "Chờ xác nhận", dot: "bg-slate-400", badge: "bg-slate-100 text-slate-600" },
  ACCEPTED: { label: "Đã nhận việc", dot: "bg-blue-400", badge: "bg-blue-50 text-blue-600" },
  IN_PROGRESS: { label: "Đang làm", dot: "bg-amber-400", badge: "bg-amber-50 text-amber-600" },
  PENDING_REVIEW: { label: "Chờ duyệt", dot: "bg-sky-400", badge: "bg-sky-50 text-sky-600" },
  COMPLETED: { label: "Hoàn thành", dot: "bg-emerald-400", badge: "bg-emerald-50 text-emerald-600" },
  DECLINED: { label: "Đã từ chối", dot: "bg-rose-400", badge: "bg-rose-50 text-rose-600" }
};

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

export const ASSIGNMENT_ACTION_LABEL: Record<AssignmentStatus, string> = {
  PENDING: "",
  ACCEPTED: "Nhận việc",
  IN_PROGRESS: "Bắt đầu làm",
  PENDING_REVIEW: "Nộp kết quả",
  COMPLETED: "",
  DECLINED: "Từ chối nhận việc"
};

/** Khớp ĐÚNG ASSIGNER_TRANSITIONS ở TaskService.java — hành động của người GIAO việc (tab "Việc tôi giao"). */
export const ASSIGNER_TRANSITIONS: Record<AssignmentStatus, AssignmentStatus[]> = {
  PENDING: [],
  ACCEPTED: [],
  IN_PROGRESS: [],
  PENDING_REVIEW: ["COMPLETED", "IN_PROGRESS"],
  COMPLETED: [],
  DECLINED: []
};
