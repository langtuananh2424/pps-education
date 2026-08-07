import React, { useEffect, useState } from "react";
import { Calendar, CalendarRange, Clock, History } from "lucide-react";
import { ApiError } from "@/lib/apiClient";
import { useApp } from "@/context/AppContext";
import { UserRole } from "@/types";
import { StudentCommentResponse, listPendingComments } from "../api";
import DailyCommentPanel from "../components/DailyCommentPanel";
import PeriodicCommentPanel from "../components/PeriodicCommentPanel";
import CommentApprovalByClass from "../components/CommentApprovalByClass";
import CommentHistoryPanel from "../components/CommentHistoryPanel";
import { useToast } from "@/lib/useToast";
import Toast from "@/components/ui/Toast";

type Tab = "daily" | "periodic";
type SiteManagerTab = "pending" | "history";

export default function CommentsPage() {
  const [tab, setTab] = useState<Tab>("daily");
  const [siteManagerTab, setSiteManagerTab] = useState<SiteManagerTab>("pending");
  const { currentUser } = useApp();
  // Hàng chờ duyệt (UC-22) chỉ có ý nghĩa với Quản lý điểm trường — API tự scope theo site được gán.
  // Quản lý điểm trường không viết nhận xét nên thay hẳn khu vực viết ở trên bằng khu vực xem chi tiết yêu cầu duyệt.
  const isSiteManager = currentUser?.roleCodes?.includes(UserRole.SITE_MANAGER) ?? false;

  const [pending, setPending] = useState<StudentCommentResponse[]>([]);
  const [loadingPending, setLoadingPending] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const { message: toastMessage, showToast } = useToast();

  const loadPending = () => {
    setLoadingPending(true);
    listPendingComments()
      .then(setPending)
      .catch((err) => setError(err instanceof ApiError ? err.message : "Không tải được hàng chờ duyệt nhận xét."))
      .finally(() => setLoadingPending(false));
  };

  useEffect(() => {
    if (isSiteManager) loadPending();
  }, [isSiteManager]);

  return (
    <div className="space-y-6">
      <div className="border-b border-slate-200 pb-4">
        <h1 className="text-xl font-bold font-display tracking-tight text-slate-900">Nhận xét học viên</h1>
        <p className="text-sm text-slate-500 mt-1">Giáo viên viết nhận xét hàng ngày/định kỳ, Quản lý điểm trường duyệt trước khi hiển thị Portal phụ huynh.</p>
      </div>

      {error && <div className="text-sm text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg">{error}</div>}

      {isSiteManager ? (
        <>
          <div className="flex border-b border-slate-200 gap-5">
            {(
              [
                ["pending", "Chờ duyệt", Clock],
                ["history", "Lịch sử nhận xét cũ", History]
              ] as const
            ).map(([key, label, Icon]) => (
              <button
                key={key}
                onClick={() => setSiteManagerTab(key)}
                className={`pb-2.5 text-sm font-bold border-b-2 flex items-center gap-1.5 transition-all ${
                  siteManagerTab === key ? "border-brand-red text-brand-red" : "border-transparent text-slate-500 hover:text-slate-700"
                }`}
              >
                <Icon className="w-3.5 h-3.5" />
                {label}
                {key === "pending" && pending.length > 0 && (
                  <span className="bg-brand-red text-white text-sm font-bold px-1.5 py-0.5 rounded-full">{pending.length}</span>
                )}
              </button>
            ))}
          </div>

          {siteManagerTab === "pending" ? (
            <CommentApprovalByClass
              items={pending}
              loading={loadingPending}
              onDecided={() => {
                loadPending();
                showToast("Đã xử lý yêu cầu duyệt nhận xét thành công!");
              }}
            />
          ) : (
            <CommentHistoryPanel />
          )}
        </>
      ) : (
        <>
          <div className="flex border-b border-slate-200 gap-5">
            {(
              [
                ["daily", "Nhận xét hàng ngày", Calendar],
                ["periodic", "Nhận xét giữa kỳ / cuối kỳ", CalendarRange]
              ] as const
            ).map(([key, label, Icon]) => (
              <button
                key={key}
                onClick={() => setTab(key)}
                className={`pb-2.5 text-sm font-bold border-b-2 flex items-center gap-1.5 transition-all ${
                  tab === key ? "border-brand-red text-brand-red" : "border-transparent text-slate-500 hover:text-slate-700"
                }`}
              >
                <Icon className="w-3.5 h-3.5" />
                {label}
              </button>
            ))}
          </div>

          {tab === "daily" ? <DailyCommentPanel /> : <PeriodicCommentPanel />}
        </>
      )}

      <Toast message={toastMessage} />
    </div>
  );
}
