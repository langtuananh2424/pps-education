import React, { useEffect, useRef, useState } from "react";
import { Bell } from "lucide-react";
import { markNotificationRead, listMyNotifications, NotificationResponse } from "../api";

/** Chuông thông báo ở header Portal — tái dùng GET /notifications đã có (HomeTab đã dùng để hiện ở "Bảng tin"), thêm mark-as-read qua POST /notifications/{id}/read. */
export default function NotificationBell() {
  const [open, setOpen] = useState(false);
  const [notifications, setNotifications] = useState<NotificationResponse[]>([]);
  const [loading, setLoading] = useState(false);
  const ref = useRef<HTMLDivElement>(null);

  const load = () => {
    setLoading(true);
    listMyNotifications(0, 15)
      .then((res) => setNotifications(res.content))
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

  const handleOpenNotification = (n: NotificationResponse) => {
    if (!n.readAt) {
      markNotificationRead(n.id)
        .then((updated) => setNotifications((prev) => prev.map((x) => (x.id === updated.id ? updated : x))))
        .catch(() => undefined);
    }
  };

  return (
    <div className="relative" ref={ref}>
      <button
        type="button"
        onClick={() => setOpen((v) => !v)}
        aria-label="Thông báo"
        aria-haspopup="dialog"
        aria-expanded={open}
        className="relative w-10 h-10 rounded-xl bg-sky-2 hover:bg-sky border border-line flex items-center justify-center text-ink transition-colors"
      >
        <Bell size={18} aria-hidden="true" />
        {unreadCount > 0 && (
          <span className="absolute -top-1 -right-1 min-w-[18px] h-[18px] px-1 rounded-full bg-coral text-white text-[10px] font-extrabold flex items-center justify-center border-2 border-white">
            {unreadCount > 9 ? "9+" : unreadCount}
          </span>
        )}
      </button>

      {open && (
        <div role="dialog" aria-label="Danh sách thông báo" className="absolute right-0 top-full mt-2 z-50 w-80 max-h-[420px] overflow-y-auto bg-white border border-line rounded-2xl shadow-lg">
          <div className="px-4 py-3 bg-slate-50 border-b border-line rounded-t-2xl flex items-center justify-between sticky top-0">
            <span className="text-xs font-extrabold text-ink">Thông báo</span>
            {unreadCount > 0 && <span className="text-[10px] bg-coral text-white px-2 py-0.5 rounded-full font-bold">{unreadCount} chưa đọc</span>}
          </div>
          {loading ? (
            <p className="text-xs text-muted font-bold p-4">Đang tải...</p>
          ) : notifications.length === 0 ? (
            <p className="text-xs text-muted font-bold italic p-4">Chưa có thông báo nào.</p>
          ) : (
            <div className="divide-y divide-line/60">
              {notifications.map((n) => (
                <button
                  key={n.id}
                  type="button"
                  onClick={() => handleOpenNotification(n)}
                  className={`w-full text-left p-3.5 hover:bg-slate-50/80 transition-colors ${!n.readAt ? "bg-teal/5" : ""}`}
                >
                  <div className="flex items-start gap-2.5">
                    <div className={`w-2 h-2 rounded-full mt-1.5 shrink-0 ${!n.readAt ? "bg-teal" : "bg-transparent"}`} />
                    <div className="min-w-0">
                      <h4 className={`text-xs leading-snug ${!n.readAt ? "font-extrabold text-ink" : "font-bold text-muted"}`}>{n.title}</h4>
                      <p className="text-[11px] text-muted font-semibold mt-0.5">{n.content}</p>
                      <span className="text-[10px] text-muted/70 font-mono block mt-1">{new Date(n.createdAt).toLocaleString("vi-VN")}</span>
                    </div>
                  </div>
                </button>
              ))}
            </div>
          )}
        </div>
      )}
    </div>
  );
}
