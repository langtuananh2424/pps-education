import { NotificationResponse } from "./api";

/**
 * Đích điều hướng khi bấm 1 thông báo ở quả chuông Header (Plan link hoá thông báo, 2026-09-22).
 * - `route`: navigate(url) React Router — app admin dùng route URL thật, không phải tab-state như Portal.
 * - `studentProfileId`: mở modal Hồ sơ học sinh dùng chung (useStudentProfileModal) — không có trang riêng.
 */
export type AdminNotificationTarget = { kind: "route"; url: string } | { kind: "studentProfile"; studentId: number };

/**
 * Map entityType/notificationType → đích. Chỉ map những loại đã có màn đích thật trong admin app; loại
 * chưa có màn (CLASS_CHECKIN_*_ALERT, SYSTEM_ANNOUNCEMENT hợp đồng đối tác...) cố tình trả null — bấm
 * vào chỉ đánh dấu đã đọc như trước. Đợt 2 (GRADE_REJECTED, COMMENT_REJECTED) chưa đủ dữ liệu BE, để sau.
 *
 * Trang đích đọc query param (`?feedbackId=`, `?userId=`, `?taskId=`, `?classId=`, `?leaveRequestId=`)
 * lúc mount để mở/cuộn sẵn đúng bản ghi — xem từng page tương ứng.
 */
export function resolveAdminNotificationTarget(n: NotificationResponse): AdminNotificationTarget | null {
  const id = n.entityId;
  switch (n.notificationType) {
    case "HOMEWORK_DEADLINE_SUMMARY":
      if (id == null) return { kind: "route", url: "/academic/homework-stats" };
      if (n.entityType === "REVIEW_VIDEO_ASSIGNMENT") return { kind: "route", url: `/academic/homework-stats/review-video/${id}` };
      if (n.entityType === "EXERCISE_ASSIGNMENT") return { kind: "route", url: `/academic/homework-stats/${id}` };
      return { kind: "route", url: "/academic/homework-stats" };
    case "PARTNER_FEEDBACK":
      return { kind: "route", url: id != null ? `/facility/feedback?feedbackId=${id}` : "/facility/feedback" };
    case "OTHER":
      // Tài khoản bị khoá do đăng nhập sai nhiều lần (AuthService) — entityType USER, gửi SysAdmin.
      if (n.entityType === "USER" && id != null) return { kind: "route", url: `/system-admin/users?userId=${id}` };
      return null;
    case "EXAM_INTEGRITY_VIOLATION":
      return n.entityType === "STUDENT" && id != null ? { kind: "studentProfile", studentId: id } : null;
    case "HOMEWORK_MEETING_INVITE_PENDING_APPROVAL":
      return { kind: "route", url: "/notifications/meeting-invites" };
    case "STUDENT_ATTITUDE_ESCALATION_PENDING_APPROVAL":
      return { kind: "route", url: "/notifications/attitude-escalations" };
    case "TASK_ASSIGNED":
    case "TASK_COMMENT":
      return { kind: "route", url: id != null ? `/task-workflow?taskId=${id}` : "/task-workflow" };
    case "COMMENT_PENDING_APPROVAL":
      return { kind: "route", url: n.entityType === "SCHOOL_CLASS" && id != null ? `/academic/comments?classId=${id}` : "/academic/comments" };
    case "LEAVE_REQUEST_STATUS":
      return { kind: "route", url: id != null ? `/hrm/leaves?leaveRequestId=${id}` : "/hrm/leaves" };
    default:
      return null;
  }
}
