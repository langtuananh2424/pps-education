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
