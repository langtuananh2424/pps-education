import React, { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { History, Repeat } from "lucide-react";
import Badge from "@/components/ui/Badge";
import { ApiError } from "@/lib/apiClient";
import { useToast } from "@/lib/useToast";
import Toast from "@/components/ui/Toast";
import { useApp } from "@/context/AppContext";
import { UserRole } from "@/types";
import LeaveRequestForm from "../components/LeaveRequestForm";
import LeaveApprovalQueue from "../components/LeaveApprovalQueue";
import {
  LeaveRequestResponse,
  LeaveSubstitutionResponse,
  listLeaveSubstitutionHistory,
  listMyLeaveRequests,
  listPendingLeaveRequestsForApprover
} from "../api";
import { useLeaveTypeLabel } from "../hooks/useLeaveTypeLabel";

/**
 * Vai trò luôn vừa có thể nộp đơn vừa có thể duyệt đơn người khác (UC-10 bước 4a:
 * Quản lý vận hành là cấp duyệt thứ 2; SYS_ADMIN/SUPER_ADMIN giữ nguyên đầy đủ để
 * phục vụ demo/QA) — hiển thị đủ 4 khối như thiết kế gốc, kể cả khi hàng chờ rỗng.
 */
const alwaysShowApprovalQueueRoles: UserRole[] = [UserRole.OPS_MANAGER, UserRole.SYS_ADMIN, UserRole.SUPER_ADMIN];

const statusVariant: Record<LeaveRequestResponse["status"], "success" | "danger" | "warning" | "neutral"> = {
  APPROVED: "success",
  REJECTED: "danger",
  PENDING: "warning",
  CANCELLED: "neutral"
};

/** Nhãn trạng thái dịch qua i18next namespace "hrm-leaves" — xem src/i18n/locales/{vi,en}/hrm-leaves.json. */
function leaveRequestStatusLabel(t: (key: string) => string, status: LeaveRequestResponse["status"]): string {
  return t(`leaveRequestStatus.${status}`);
}

export default function LeavesPage() {
  const { t } = useTranslation("hrm-leaves");
  const { currentRole } = useApp();
  const getLeaveTypeLabel = useLeaveTypeLabel();
  const [pending, setPending] = useState<LeaveRequestResponse[]>([]);
  const [pendingLoading, setPendingLoading] = useState(false);
  const [mine, setMine] = useState<LeaveRequestResponse[]>([]);
  const [mineLoading, setMineLoading] = useState(false);
  const [substitutions, setSubstitutions] = useState<LeaveSubstitutionResponse[]>([]);
  const { message: toastMessage, showToast } = useToast();

  const loadPending = () => {
    setPendingLoading(true);
    listPendingLeaveRequestsForApprover()
      .then(setPending)
      .catch(() => setPending([]))
      .finally(() => setPendingLoading(false));
  };

  const loadMine = () => {
    setMineLoading(true);
    listMyLeaveRequests()
      .then(setMine)
      .catch(() => setMine([]))
      .finally(() => setMineLoading(false));
  };

  const loadSubstitutions = () => {
    listLeaveSubstitutionHistory()
      .then(setSubstitutions)
      .catch((err) => {
        // Không phải ai cũng cần thấy mục này — bỏ qua lỗi 403 âm thầm, chỉ log các lỗi khác.
        if (!(err instanceof ApiError) || err.status !== 403) setSubstitutions([]);
      });
  };

  useEffect(() => {
    loadPending();
    loadMine();
    loadSubstitutions();
  }, []);

  // Ban giám đốc bị chặn nộp đơn hoàn toàn (UC-10 Precondition/A1) — với họ, đơn từ/lịch sử
  // dạy thay của "bản thân" không có ý nghĩa vì họ không bao giờ nộp đơn qua hệ thống.
  const isExecutive = currentRole === UserRole.EXECUTIVE;
  const showSubmitForm = !isExecutive;
  const showOwnHistory = !isExecutive;

  // Giáo viên/Nhân viên và 3 role "cấp Quản lý" (Quản lý điểm trường/Trưởng phòng đào tạo/Quản lý
  // nhân sự) không bao giờ là người duyệt (UC-10 bước 4) nên ẩn khối này theo mặc định. Vẫn hiện
  // nếu thực sự có đơn chờ duyệt — vì "Trưởng phòng ban" là role tuỳ biến tạo qua UC-03, không nằm
  // trong enum UserRole cố định nên FE không thể biết chắc 100% ai giữ vai trò đó chỉ từ currentRole.
  const showApprovalQueue = isExecutive || alwaysShowApprovalQueueRoles.includes(currentRole) || pending.length > 0;

  return (
    <div className="space-y-6">
      <div className="border-b border-slate-200 pb-4">
        <h1 className="text-xl font-bold font-display tracking-tight text-slate-900">{t("leavesPage.title")}</h1>
        <p className="text-xs text-slate-500 mt-1">{t("leavesPage.subtitle")}</p>
      </div>

      <div className={showSubmitForm && showApprovalQueue ? "grid grid-cols-1 lg:grid-cols-2 gap-6" : "grid grid-cols-1 gap-6"}>
        {showSubmitForm && (
          <LeaveRequestForm
            onSubmitted={() => {
              showToast(t("leavesPage.submittedToast"));
              loadMine();
              loadSubstitutions();
            }}
          />
        )}
        {showApprovalQueue && (
          <LeaveApprovalQueue
            leaveRequests={pending}
            loading={pendingLoading}
            onDecided={() => {
              loadPending();
              loadSubstitutions();
            }}
          />
        )}
      </div>

      {showOwnHistory && (
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <div className="bg-white rounded-xl border border-slate-200 shadow-soft overflow-hidden">
          <div className="px-5 py-3 border-b border-slate-100 bg-slate-50">
            <span className="text-xs font-bold text-slate-700 font-display block">{t("leavesPage.myHistory.title")}</span>
            <p className="text-[10px] text-slate-400">{t("leavesPage.myHistory.subtitle")}</p>
          </div>
          <div className="divide-y divide-slate-100 max-h-96 overflow-y-auto">
            {mineLoading ? (
              <div className="p-6 text-center text-xs text-slate-400">{t("leavesPage.myHistory.loading")}</div>
            ) : mine.length === 0 ? (
              <div className="p-6 text-center text-xs text-slate-400">{t("leavesPage.myHistory.empty")}</div>
            ) : (
              mine.map((req) => (
                <div key={req.id} className="p-3 space-y-1">
                  <div className="flex items-center justify-between gap-2">
                    <span className="text-xs font-bold text-slate-800">{getLeaveTypeLabel(req.leaveType)}</span>
                    <Badge variant={statusVariant[req.status]}>{leaveRequestStatusLabel(t, req.status)}</Badge>
                  </div>
                  <p className="text-[11px] text-slate-500 italic">"{req.reason}"</p>
                  <p className="text-[10px] text-slate-400">
                    {t("leavesPage.myHistory.dateRange", { startDate: req.startDate, endDate: req.endDate, totalDays: req.totalDays })}
                  </p>
                </div>
              ))
            )}
          </div>
        </div>

        <div className="bg-white rounded-xl border border-slate-200 shadow-soft overflow-hidden">
          <div className="px-5 py-3 border-b border-slate-100 bg-slate-50 flex items-center gap-2">
            <History className="w-3.5 h-3.5 text-slate-400 shrink-0" />
            <div>
              <span className="text-xs font-bold text-slate-700 font-display block">{t("leavesPage.substituteHistory.title")}</span>
              <p className="text-[10px] text-slate-400">{t("leavesPage.substituteHistory.subtitle")}</p>
            </div>
          </div>
          <div className="divide-y divide-slate-100 max-h-96 overflow-y-auto">
            {substitutions.length === 0 ? (
              <div className="p-6 text-center text-xs text-slate-400">{t("leavesPage.substituteHistory.empty")}</div>
            ) : (
              substitutions.map((s) => (
                <div key={s.id} className="p-3 space-y-1">
                  <div className="flex items-center justify-between gap-2">
                    <span className="text-xs font-bold text-slate-800 flex items-center gap-1.5">
                      <Repeat className="w-3 h-3 text-slate-400" />
                      {s.className}
                    </span>
                    <Badge variant={s.revokedAt ? "neutral" : "success"}>
                      {s.revokedAt ? t("substitutionStatus.revoked") : t("substitutionStatus.active")}
                    </Badge>
                  </div>
                  <p className="text-[11px] text-slate-500">
                    {s.substituteTeacherName} <span className="text-slate-400">{t("leavesPage.substituteHistory.substituteFor")}</span> {s.originalTeacherName}
                  </p>
                  <p className="text-[10px] text-slate-400">{t("leavesPage.substituteHistory.sessionDate", { date: s.sessionDate })}</p>
                </div>
              ))
            )}
          </div>
        </div>
      </div>
      )}

      <Toast message={toastMessage} />
    </div>
  );
}
