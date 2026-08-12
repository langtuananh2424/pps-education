import React, { useEffect, useRef, useState } from "react";
import { Bell, CheckCheck } from "lucide-react";
import { markNotificationRead, listMyNotifications, NotificationResponse } from "../api";
import { clampLines } from "@/lib/textClamp";

const PAGE_SIZE = 15;

export type NotificationTab = "homework" | "schedule" | "grades" | "billing";

/** Đích chuyển trang khi bấm 1 thông báo — PortalPage nhận rồi tự set activeTab + các state "pending" liên quan. */
export interface NotificationNavTarget {
  tab: NotificationTab;
  exerciseAssignmentId?: number;
  reviewVideoAssignmentId?: number;
  /** entityId khi entityType=STUDENT — PortalPage dùng để chuyển đúng con (Phụ huynh nhiều con). */
  studentId?: number;
}

/**
 * Map entityType/entityId (Notification.java: "Loại object liên quan để click chuyển trang") sang
 * đúng tab Portal. Chỉ map những entityType đã có backend gán thật (xem các nơi gọi
 * NotificationService.notify() với entityType) VÀ có trang tương ứng ở Portal — entityType "STUDENT"
 * của EXAM_INTEGRITY_VIOLATION_PARENT chưa có trang xem cho Phụ huynh (chỉ Giáo viên xem qua
 * integrity-summary, quyền lms.grading.manage) nên cố tình không map, trả null.
 */
function resolveNotificationTarget(n: NotificationResponse): NotificationNavTarget | null {
  switch (n.entityType) {
    case "EXERCISE_ASSIGNMENT":
      return n.entityId != null ? { tab: "homework", exerciseAssignmentId: n.entityId } : { tab: "homework" };
    case "REVIEW_VIDEO_ASSIGNMENT":
      return n.entityId != null ? { tab: "homework", reviewVideoAssignmentId: n.entityId } : { tab: "homework" };
    case "ATTENDANCE_MARK":
      return { tab: "schedule" };
    case "GRADE_ENTRY":
    case "GRADE_PERIOD_RESULT":
      return { tab: "grades" };
    case "STUDENT":
      switch (n.notificationType) {
        case "HOMEWORK_DUE_SOON_REMINDER":
        case "HOMEWORK_MISS_REMINDER":
        case "HOMEWORK_MISS_WARNING":
        case "HOMEWORK_MISS_PARENT_MEETING_INVITE":
        case "HOMEWORK_MISS_REMINDER_NON_CONSECUTIVE":
          return { tab: "homework", studentId: n.entityId ?? undefined };
        default:
          return null;
      }
    default:
      return n.notificationType === "INVOICE_DUE" ? { tab: "billing" } : null;
  }
}

interface NotificationBellProps {
  /** Gọi khi bấm 1 thông báo có đích chuyển trang xác định — PortalPage tự set activeTab tương ứng. */
  onNavigate?: (target: NotificationNavTarget) => void;
}

/**
 * Chuông thông báo ở header Portal — tái dùng GET /notifications đã có (HomeTab đã dùng để hiện ở
 * "Bảng tin"), thêm mark-as-read qua POST /notifications/{id}/read.
 *
 * Backend CHƯA có API xóa thông báo hay đánh dấu-tất-cả-đã-đọc trong 1 lần gọi (đã kiểm tra API.md,
 * 2026-07-31) — "Đánh dấu tất cả đã đọc" ở đây ghép nhiều lệnh mark-as-read đơn lẻ đã có sẵn (N+1,
 * chấp nhận được vì mỗi lần chỉ tối đa PAGE_SIZE mục chưa đọc). Xóa thông báo KHÔNG làm được (không có
 * API), không tự chế giả — đã báo lại người phụ trách backend.
 */
export default function NotificationBell({ onNavigate }: NotificationBellProps) {
  const [open, setOpen] = useState(false);
  const [notifications, setNotifications] = useState<NotificationResponse[]>([]);
  const [loading, setLoading] = useState(false);
  const [loadingMore, setLoadingMore] = useState(false);
  const [markingAll, setMarkingAll] = useState(false);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const ref = useRef<HTMLDivElement>(null);

  const load = () => {
    setLoading(true);
    listMyNotifications(0, PAGE_SIZE)
      .then((res) => {
        setNotifications(res.content);
        setPage(res.number);
        setTotalPages(res.totalPages);
      })
      .catch(() => undefined)
      .finally(() => setLoading(false));
  };

  useEffect(load, []);

  useEffect(() => {
    if (!open) return;
    const handleClickOutside = (e: MouseEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false);
    };
    document.addEventListener("mousedown", handleClickOutside);
    return () => document.removeEventListener("mousedown", handleClickOutside);
  }, [open]);

  const unreadCount = notifications.filter((n) => !n.readAt).length;
  const hasMore = page + 1 < totalPages;

  const handleOpenNotification = (n: NotificationResponse) => {
    if (!n.readAt) {
      markNotificationRead(n.id)
        .then((updated) => setNotifications((prev) => prev.map((x) => (x.id === updated.id ? updated : x))))
        .catch(() => undefined);
    }
    const target = resolveNotificationTarget(n);
    if (target) {
      onNavigate?.(target);
      setOpen(false);
    }
  };

  /** Ghép nhiều lệnh mark-as-read đơn lẻ (chưa có API mark-all-read thật) — chỉ gọi cho các mục đang chưa đọc, dò lại state sau khi xong. */
  const handleMarkAllRead = () => {
    const unread = notifications.filter((n) => !n.readAt);
    if (unread.length === 0) return;
    setMarkingAll(true);
    Promise.allSettled(unread.map((n) => markNotificationRead(n.id)))
      .then((results) => {
        const updatedById = new Map(
          results.filter((r): r is PromiseFulfilledResult<NotificationResponse> => r.status === "fulfilled").map((r) => [r.value.id, r.value])
        );
        setNotifications((prev) => prev.map((n) => updatedById.get(n.id) ?? n));
      })
      .finally(() => setMarkingAll(false));
  };

  const loadMore = () => {
    setLoadingMore(true);
    listMyNotifications(page + 1, PAGE_SIZE)
      .then((res) => {
        setNotifications((prev) => [...prev, ...res.content]);
        setPage(res.number);
        setTotalPages(res.totalPages);
      })
      .catch(() => undefined)
      .finally(() => setLoadingMore(false));
  };

  return (
    <div className="relative" ref={ref}>
      <button
        type="button"
        onClick={() => setOpen((v) => !v)}
        aria-label="Thông báo"
        aria-haspopup="dialog"
        aria-expanded={open}
        className="relative w-10 h-10 rounded-xl bg-white/15 hover:bg-white/25 border border-white/20 text-white lg:bg-sky-2 lg:hover:bg-sky lg:border-line lg:text-ink flex items-center justify-center transition-colors"
      >
        <Bell size={18} aria-hidden="true" />
        {unreadCount > 0 && (
          <span className="absolute -top-1 -right-1 min-w-[18px] h-[18px] px-1 rounded-full bg-coral text-white text-[10px] font-extrabold flex items-center justify-center border-2 border-white">
            {unreadCount > 9 ? "9+" : unreadCount}
          </span>
        )}
      </button>

      {open && (
        <div
          role="dialog"
          aria-label="Danh sách thông báo"
          className="absolute right-0 top-full mt-2 z-50 w-80 max-h-[420px] overflow-y-auto bg-white border-2 border-teal/20 rounded-2xl shadow-[0_20px_50px_-10px_rgba(14,140,134,0.35)]"
        >
          {/* Header sticky BẮT BUỘC nền đặc (không dùng bg-teal/10 kiểu trong suốt) — nền có alpha để lộ
              chữ cuộn bên dưới lấp ló qua, gây hiện tượng "đè chữ" khi cuộn (lỗi đã gặp, 2026-07-31).
              Thêm z-10 để chắc chắn luôn nổi trên danh sách cuộn bên dưới. */}
          <div className="px-4 py-3 bg-sky-2 border-b-2 border-teal/30 rounded-t-2xl flex items-center justify-between sticky top-0 z-10 gap-2">
            <div className="flex items-center gap-2 min-w-0">
              <span className="text-xs font-extrabold text-teal-deep shrink-0">Thông báo</span>
              {unreadCount > 0 && <span className="text-[10px] bg-coral text-white px-2 py-0.5 rounded-full font-bold shrink-0">{unreadCount} chưa đọc</span>}
            </div>
            {unreadCount > 0 && (
              <button
                type="button"
                onClick={handleMarkAllRead}
                disabled={markingAll}
                className="shrink-0 flex items-center gap-1 text-[11px] font-bold text-teal-deep hover:underline disabled:opacity-50 disabled:no-underline"
              >
                <CheckCheck size={13} aria-hidden="true" /> {markingAll ? "Đang xử lý..." : "Đọc tất cả"}
              </button>
            )}
          </div>
          {loading ? (
            <p className="text-xs text-muted font-bold p-4">Đang tải...</p>
          ) : notifications.length === 0 ? (
            <p className="text-xs text-muted font-bold italic p-4">Chưa có thông báo nào.</p>
          ) : (
            <>
              <div className="divide-y-2 divide-slate-200">
                {notifications.map((n) => (
                  <button
                    key={n.id}
                    type="button"
                    onClick={() => handleOpenNotification(n)}
                    className={`w-full text-left p-2.5 hover:bg-slate-50/80 transition-colors ${!n.readAt ? "bg-teal/10 border-l-4 border-l-teal" : "border-l-4 border-l-transparent"}`}
                  >
                    <div className="flex items-start gap-2">
                      <div className={`w-2 h-2 rounded-full mt-1.5 shrink-0 ${!n.readAt ? "bg-teal" : "bg-transparent"}`} />
                      <div className="min-w-0">
                        <h4 className={`text-sm leading-snug ${!n.readAt ? "font-extrabold text-ink" : "font-bold text-muted"}`}>{n.title}</h4>
                        <p className="text-xs text-muted font-semibold mt-0.5" style={clampLines(2)}>
                          {n.content}
                        </p>
                        <span className="text-[11px] text-muted/70 font-mono block mt-0.5">{new Date(n.createdAt).toLocaleString("vi-VN")}</span>
                      </div>
                    </div>
                  </button>
                ))}
              </div>
              {hasMore && (
                <div className="p-2.5 border-t border-line">
                  <button
                    type="button"
                    onClick={loadMore}
                    disabled={loadingMore}
                    className="w-full text-center text-xs font-bold text-teal-deep hover:underline disabled:opacity-50 disabled:no-underline"
                  >
                    {loadingMore ? "Đang tải..." : "Xem thông báo cũ hơn"}
                  </button>
                </div>
              )}
            </>
          )}
        </div>
      )}
    </div>
  );
}
