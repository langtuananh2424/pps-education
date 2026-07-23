import React, { useEffect, useState } from "react";
import { Calendar, CalendarRange } from "lucide-react";
import { ApiError } from "@/lib/apiClient";
import { useApp } from "@/context/AppContext";
import { UserRole } from "@/types";
import { StudentCommentResponse, listPendingComments } from "../api";
import Card from "@/components/ui/Card";
import DailyCommentPanel from "../components/DailyCommentPanel";
import PeriodicCommentPanel from "../components/PeriodicCommentPanel";
import CommentApprovalList from "../components/CommentApprovalList";
import CommentApprovalDetail from "../components/CommentApprovalDetail";
import { useToast } from "@/lib/useToast";
import Toast from "@/components/ui/Toast";

type Tab = "daily" | "periodic";

export default function CommentsPage() {
  const [tab, setTab] = useState<Tab>("daily");
  const { currentUser } = useApp();
  // Hàng chờ duyệt (UC-22) chỉ có ý nghĩa với Quản lý điểm trường — API tự scope theo site được gán.
  // Quản lý điểm trường không viết nhận xét nên thay hẳn khu vực viết ở trên bằng khu vực xem chi tiết yêu cầu duyệt.
  const isSiteManager = currentUser?.roleCodes?.includes(UserRole.SITE_MANAGER) ?? false;

  const [pending, setPending] = useState<StudentCommentResponse[]>([]);
  const [loadingPending, setLoadingPending] = useState(true);
  const [selectedRequestId, setSelectedRequestId] = useState<number | null>(null);
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

  const selectedRequest = pending.find((p) => p.id === selectedRequestId) ?? null;

  return (
    <div className="space-y-6">
      <div className="border-b border-slate-200 pb-4">
        <h1 className="text-xl font-bold font-display tracking-tight text-slate-900">Nhận xét học viên (UC-21/22)</h1>
        <p className="text-xs text-slate-500 mt-1">Giáo viên viết nhận xét hàng ngày/định kỳ, Quản lý điểm trường duyệt trước khi hiển thị Portal phụ huynh.</p>
      </div>

      {error && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg">{error}</div>}

      {isSiteManager ? (
        <>
          <CommentApprovalDetail
            comment={selectedRequest}
            onDecided={() => {
              setSelectedRequestId(null);
              loadPending();
              showToast("Đã xử lý yêu cầu duyệt nhận xét thành công!");
            }}
          />

          <Card padded={false} className="overflow-hidden">
            <div className="px-5 py-4 border-b border-slate-100 bg-slate-50">
              <span className="text-xs font-bold text-slate-700 font-display">Danh sách yêu cầu chờ duyệt ({pending.length})</span>
            </div>
            <CommentApprovalList items={pending} loading={loadingPending} selectedId={selectedRequestId} onSelect={setSelectedRequestId} />
          </Card>
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
                className={`pb-2.5 text-xs font-bold border-b-2 flex items-center gap-1.5 transition-all ${
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
