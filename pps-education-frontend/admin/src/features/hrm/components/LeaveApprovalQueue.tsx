import React, { useState } from "react";
import { useTranslation } from "react-i18next";
import { Check, Clock } from "lucide-react";
import Badge from "@/components/ui/Badge";
import { cn } from "@/lib/cn";
import { useDialog } from "@/components/ui/DialogProvider";
import { ApiError } from "@/lib/apiClient";
import { decideLeaveRequest, LeaveRequestResponse } from "../api";
import { useLeaveTypeLabel } from "../hooks/useLeaveTypeLabel";
import NotificationBanner from "@/features/student/components/NotificationBanner";

interface LeaveApprovalQueueProps {
  leaveRequests: LeaveRequestResponse[];
  loading: boolean;
  onDecided: () => void;
}

/** Nhãn trạng thái dịch qua i18next namespace "hrm-leaves" — xem src/i18n/locales/{vi,en}/hrm-leaves.json. */
function leaveRequestStatusLabel(t: (key: string) => string, status: LeaveRequestResponse["status"]): string {
  return t(`leaveRequestStatus.${status}`);
}

/** UC-11: Duyệt đơn từ — hàng chờ duyệt thuộc thẩm quyền người gọi (GET /api/leave-requests/pending-for-me). */
export default function LeaveApprovalQueue({ leaveRequests, loading, onDecided }: LeaveApprovalQueueProps) {
  const { t } = useTranslation("hrm-leaves");
  const getLeaveTypeLabel = useLeaveTypeLabel();
  const [opinionNotes, setOpinionNotes] = useState<Record<number, string>>({});
  const [decidingId, setDecidingId] = useState<number | null>(null);
  const [decidedMessage, setDecidedMessage] = useState<string | null>(null);
  const { alertDialog } = useDialog();

  const decide = async (req: LeaveRequestResponse, decision: "APPROVED" | "REJECTED", comment?: string) => {
    setDecidingId(req.id);
    try {
      await decideLeaveRequest(req.id, { decision, comment });
      setDecidedMessage(
        t(decision === "APPROVED" ? "leaveApprovalQueue.decidedApproved" : "leaveApprovalQueue.decidedRejected", {
          name: req.employeeFullName
        })
      );
      onDecided();
    } catch (err) {
      await alertDialog(err instanceof ApiError ? err.message : t("leaveApprovalQueue.decisionError"));
    } finally {
      setDecidingId(null);
    }
  };

  return (
    <div className="bg-white rounded-xl border border-slate-200 shadow-soft overflow-hidden">
      {decidedMessage && (
        <div className="p-3 border-b border-slate-100">
          <NotificationBanner message={decidedMessage} onClose={() => setDecidedMessage(null)} />
        </div>
      )}
      <div className="px-5 py-3 border-b border-slate-100 bg-slate-50 flex items-center justify-between gap-3">
        <div>
          <span className="text-xs font-bold text-slate-700 font-display block">{t("leaveApprovalQueue.title")}</span>
          <p className="text-[10px] text-slate-400">{t("leaveApprovalQueue.subtitle")}</p>
        </div>
        <span className="px-2 py-1 rounded-full text-[10px] font-bold bg-brand-orange text-white shrink-0">{leaveRequests.length}</span>
      </div>

      <div className="divide-y divide-slate-100 max-h-[500px] overflow-y-auto">
        {loading ? (
          <div className="p-8 text-center text-xs text-slate-400">{t("leaveApprovalQueue.loading")}</div>
        ) : leaveRequests.length === 0 ? (
          <div className="p-12 text-center space-y-3">
            <div className="w-12 h-12 bg-slate-50 rounded-full flex items-center justify-center mx-auto border border-slate-100 text-slate-400">
              <Clock className="w-6 h-6" />
            </div>
            <div className="space-y-1">
              <h4 className="text-xs font-bold text-slate-700">{t("leaveApprovalQueue.emptyTitle")}</h4>
              <p className="text-[11px] text-slate-400 max-w-xs mx-auto">{t("leaveApprovalQueue.emptyDescription")}</p>
            </div>
          </div>
        ) : (
          leaveRequests.map((req) => (
            <div key={req.id} className="p-4 space-y-3">
              <div className="flex items-start justify-between gap-2">
                <div>
                  <div className="flex items-center gap-2 flex-wrap">
                    <h4 className="text-xs font-bold text-slate-900">{req.employeeFullName}</h4>
                    <span className="bg-slate-100 text-slate-600 text-[9px] font-bold px-2 py-0.5 rounded">{getLeaveTypeLabel(req.leaveType)}</span>
                    {req.departmentName && <span className="text-[10px] text-slate-400">{req.departmentName}</span>}
                  </div>
                  <p className="text-[11px] text-slate-500 mt-1">
                    {t("leaveApprovalQueue.reasonLabel")} <span className="italic">"{req.reason}"</span>
                  </p>
                  <p className="text-[10px] text-slate-400 mt-0.5">
                    {t("leaveApprovalQueue.timeLabel")} {t("leaveApprovalQueue.timeRange", { startDate: req.startDate, endDate: req.endDate })}
                    {req.startTime && req.endTime &&
                      t("leaveApprovalQueue.timeRangeWithHours", { startTime: req.startTime.slice(0, 5), endTime: req.endTime.slice(0, 5) })}
                    {t("leaveApprovalQueue.totalDays", { count: req.totalDays })}
                  </p>
                </div>

                <Badge variant="warning">{leaveRequestStatusLabel(t, req.status)}</Badge>
              </div>

              <div className="space-y-2 pt-1 border-t border-slate-100">
                <div className="space-y-1">
                  <label className="text-[10px] uppercase font-bold tracking-wider text-slate-400 block font-mono">
                    {t("leaveApprovalQueue.opinionLabel")}
                  </label>
                  <input
                    type="text"
                    placeholder={t("leaveApprovalQueue.opinionPlaceholder")}
                    value={opinionNotes[req.id] || ""}
                    onChange={(e) => setOpinionNotes((prev) => ({ ...prev, [req.id]: e.target.value }))}
                    className="w-full bg-slate-50 border border-slate-200 rounded px-2 py-1 text-xs focus:outline-none"
                  />
                </div>

                <div className="flex gap-2 justify-end">
                  <button
                    disabled={decidingId === req.id}
                    onClick={async () => {
                      const notes = opinionNotes[req.id]?.trim();
                      if (!notes) {
                        await alertDialog(t("leaveApprovalQueue.missingRejectReason"));
                        return;
                      }
                      await decide(req, "REJECTED", notes);
                    }}
                    className={cn(
                      "px-2.5 py-1 hover:bg-rose-50 text-rose-600 border border-rose-200 text-[10px] font-bold rounded",
                      decidingId === req.id && "opacity-50"
                    )}
                  >
                    {t("leaveApprovalQueue.rejectButton")}
                  </button>
                  <button
                    disabled={decidingId === req.id}
                    onClick={() => decide(req, "APPROVED", opinionNotes[req.id]?.trim() || t("leaveApprovalQueue.defaultApproveComment"))}
                    className={cn(
                      "px-3 py-1 bg-brand-gradient hover:opacity-95 text-white text-[10px] font-bold rounded flex items-center gap-1",
                      decidingId === req.id && "opacity-50"
                    )}
                  >
                    <Check className="w-3.5 h-3.5 text-white" />
                    {t("leaveApprovalQueue.approveButton")}
                  </button>
                </div>
              </div>
            </div>
          ))
        )}
      </div>
    </div>
  );
}
