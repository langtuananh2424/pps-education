import React, { useEffect, useState } from "react";
import { useSearchParams } from "react-router-dom";
import { Clock, History, PenLine } from "lucide-react";
import { useTranslation } from "react-i18next";
import { ApiError } from "@/lib/apiClient";
import { useApp } from "@/context/AppContext";
import { UserRole } from "@/types";
import { StudentCommentResponse, listPendingComments } from "../api";
import DailyCommentPanel from "../components/DailyCommentPanel";
import CommentApprovalByClass from "../components/CommentApprovalByClass";
import CommentHistoryPanel from "../components/CommentHistoryPanel";

type SiteManagerTab = "write" | "pending" | "history";

export default function CommentsPage() {
  const { t } = useTranslation("academic-comments");
  const { currentUser } = useApp();
  // Hàng chờ duyệt (UC-22) chỉ có ý nghĩa với Quản lý điểm trường — API tự scope theo site được gán.
  const isSiteManager = currentUser?.roleCodes?.includes(UserRole.SITE_MANAGER) ?? false;
  // Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-16: 1 nhân viên có thể VỪA là Quản lý
  // điểm trường VỪA đứng lớp (role TEACHER gán kèm) — trước đây isSiteManager=true thay hẳn khu vực
  // viết nhận xét bằng khu vực duyệt, khiến các tài khoản kiêm nhiệm này không có chỗ tự viết nhận xét
  // cho lớp mình dạy, phải nhờ tài khoản Giáo viên khác viết hộ. DailyCommentPanel tự lọc đúng lớp được
  // phân công qua useEligibleClasses (ưu tiên phân công thật, không bị quyền Site Manager mở rộng phạm
  // vi) nên dùng lại nguyên component đó, chỉ thêm 1 tab "Viết nhận xét" khi có cả 2 role.
  const isTeacher = currentUser?.roleCodes?.includes(UserRole.TEACHER) ?? false;
  const showWriteTab = isSiteManager && isTeacher;
  const [siteManagerTab, setSiteManagerTab] = useState<SiteManagerTab>(showWriteTab ? "write" : "pending");

  const [pending, setPending] = useState<StudentCommentResponse[]>([]);
  const [loadingPending, setLoadingPending] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const loadPending = () => {
    setLoadingPending(true);
    listPendingComments()
      .then(setPending)
      .catch((err) => setError(err instanceof ApiError ? err.message : t("commentsPage.errors.loadPendingFailed")))
      .finally(() => setLoadingPending(false));
  };

  useEffect(() => {
    if (isSiteManager) loadPending();
  }, [isSiteManager]);

  // Deep-link từ thông báo COMMENT_PENDING_APPROVAL ở Header (?classId=) — nhảy sang tab "Chờ duyệt" và
  // cuộn tới đúng khối lớp trong CommentApprovalByClass (Plan link hoá thông báo, 2026-09-22).
  const [searchParams] = useSearchParams();
  const [highlightClassId, setHighlightClassId] = useState<number | null>(null);
  useEffect(() => {
    const param = searchParams.get("classId");
    if (!param || !Number.isFinite(Number(param))) return;
    setHighlightClassId(Number(param));
    if (isSiteManager) setSiteManagerTab("pending");
  }, [searchParams, isSiteManager]);

  return (
    <div className="space-y-6">
      <div className="border-b border-slate-200 pb-4">
        <h1 className="text-xl font-bold font-display tracking-tight text-slate-900">{t("commentsPage.title")}</h1>
        <p className="text-xs text-slate-500 mt-1">{t("commentsPage.subtitle")}</p>
      </div>

      {error && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg">{error}</div>}

      {isSiteManager ? (
        <>
          <div className="flex border-b border-slate-200 gap-5">
            {(
              [
                ...(showWriteTab ? ([["write", t("commentsPage.tabs.write"), PenLine]] as const) : []),
                ["pending", t("commentsPage.tabs.pending"), Clock],
                ["history", t("commentsPage.tabs.history"), History]
              ] as const
            ).map(([key, label, Icon]) => (
              <button
                key={key}
                onClick={() => setSiteManagerTab(key)}
                className={`pb-2.5 text-xs font-bold border-b-2 flex items-center gap-1.5 transition-all ${
                  siteManagerTab === key ? "border-brand-red text-brand-red" : "border-transparent text-slate-500 hover:text-slate-700"
                }`}
              >
                <Icon className="w-3.5 h-3.5" />
                {label}
                {key === "pending" && pending.length > 0 && (
                  <span className="bg-brand-red text-white text-[10px] font-bold px-1.5 py-0.5 rounded-full">{pending.length}</span>
                )}
              </button>
            ))}
          </div>

          {/* "Viết nhận xét" giữ mounted (chỉ ẩn qua CSS) thay vì unmount khi đổi tab — DailyCommentPanel
              giữ state nháp chưa lưu (ô nhận xét đang gõ dở) trong bộ nhớ component, đổi tab qua "Chờ
              duyệt"/"Lịch sử" rồi quay lại không được mất nội dung đang viết dở. */}
          {showWriteTab && (
            <div className={siteManagerTab === "write" ? "" : "hidden"}>
              <DailyCommentPanel />
            </div>
          )}
          {siteManagerTab === "pending" ? (
            <CommentApprovalByClass
              items={pending}
              loading={loadingPending}
              onDecided={loadPending}
              highlightClassId={highlightClassId}
              onHighlightHandled={() => setHighlightClassId(null)}
            />
          ) : siteManagerTab === "history" ? (
            <CommentHistoryPanel />
          ) : null}
        </>
      ) : (
        <DailyCommentPanel />
      )}
    </div>
  );
}
