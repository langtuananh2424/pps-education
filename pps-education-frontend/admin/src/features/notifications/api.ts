import { apiRequest } from "@/lib/apiClient";
import { Page } from "@/types";

/** Khớp NotificationResponse thật của backend (NotificationController) — mirror user/src/features/portal/api.ts. */
export interface NotificationResponse {
  id: number;
  notificationType: string;
  title: string;
  content: string;
  entityType: string | null;
  entityId: number | null;
  priority: string;
  createdAt: string;
  readAt: string | null;
}

export function listMyNotifications(page = 0, size = 20): Promise<Page<NotificationResponse>> {
  return apiRequest<Page<NotificationResponse>>(`/notifications?page=${page}&size=${size}`);
}

export function markNotificationRead(id: number): Promise<NotificationResponse> {
  return apiRequest<NotificationResponse>(`/notifications/${id}/read`, { method: "POST" });
}

// ===================== Duyệt "Thư mời phụ huynh tới làm việc" =====================
// Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-12 — xem
// HomeworkParentMeetingInviteService (backend).

export type HomeworkParentMeetingInviteStatus = "PENDING" | "APPROVED" | "REJECTED";

/** Khớp HomeworkParentMeetingInviteResponse thật của backend. */
export interface HomeworkParentMeetingInviteResponse {
  id: number;
  studentId: number;
  studentName: string;
  schoolClassId: number;
  className: string;
  channelLabel: string;
  missCount: number;
  status: HomeworkParentMeetingInviteStatus;
  createdAt: string;
  decidedAt: string | null;
  rejectionReason: string | null;
}

/** Hàng chờ duyệt của (các) điểm trường mình phụ trách — BE tự chặn theo site_managers. */
export function listPendingMeetingInvites(): Promise<HomeworkParentMeetingInviteResponse[]> {
  return apiRequest<HomeworkParentMeetingInviteResponse[]>("/homework-meeting-invites/pending");
}

/** Duyệt/Từ chối theo lô — cùng 1 decision cho toàn bộ inviteIds truyền vào. */
export function decideMeetingInvites(
  inviteIds: number[],
  decision: "APPROVED" | "REJECTED",
  comment?: string
): Promise<HomeworkParentMeetingInviteResponse[]> {
  return apiRequest<HomeworkParentMeetingInviteResponse[]>("/homework-meeting-invites/decision", {
    method: "POST",
    body: JSON.stringify({ inviteIds, decision, comment })
  });
}
