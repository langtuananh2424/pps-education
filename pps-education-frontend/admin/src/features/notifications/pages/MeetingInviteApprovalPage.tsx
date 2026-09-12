import React, { useEffect, useState } from "react";
import { Check, X } from "lucide-react";
import { useTranslation } from "react-i18next";
import { ApiError } from "@/lib/apiClient";
import { HomeworkParentMeetingInviteResponse, decideMeetingInvites, listPendingMeetingInvites } from "../api";
import Card from "@/components/ui/Card";
import TableContainer, { Td, Th } from "@/components/ui/TableContainer";
import { useDialog } from "@/components/ui/DialogProvider";
import { useToast } from "@/lib/useToast";
import Toast from "@/components/ui/Toast";

/**
 * Duyệt "Thư mời phụ huynh tới làm việc" (học sinh thiếu bài liên tục 4 buổi) trước khi gửi
 * xuống Phụ huynh — bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-12. Chỉ Quản lý
 * điểm trường phụ trách đúng site mới thấy/xử lý được (BE tự chặn qua site_managers) — 3 loại
 * cảnh báo BTVN khác (2/3 buổi, không liên tục) vẫn gửi thẳng, không xuất hiện ở đây.
 */
export default function MeetingInviteApprovalPage() {
  const { t } = useTranslation("notifications");
  const [invites, setInvites] = useState<HomeworkParentMeetingInviteResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [decidingId, setDecidingId] = useState<number | null>(null);
  const { promptDialog } = useDialog();
  const { message: toastMessage, showToast } = useToast();

  const load = () => {
    setLoading(true);
    setError(null);
    listPendingMeetingInvites()
      .then(setInvites)
      .catch((err) => setError(err instanceof ApiError ? err.message : t("meetingInvites.loadError")))
      .finally(() => setLoading(false));
  };

  useEffect(load, []);

  const handleDecide = async (invite: HomeworkParentMeetingInviteResponse, decision: "APPROVED" | "REJECTED") => {
    let reason: string | undefined;
    if (decision === "REJECTED") {
      reason = (await promptDialog(t("meetingInvites.rejectReasonPrompt"), { required: true, multiline: true })) ?? undefined;
      if (!reason?.trim()) return;
    }
    setDecidingId(invite.id);
    setError(null);
    try {
      await decideMeetingInvites([invite.id], decision, reason?.trim());
      setInvites((prev) => prev.filter((it) => it.id !== invite.id));
      showToast(decision === "APPROVED" ? t("meetingInvites.approvedToast") : t("meetingInvites.rejectedToast"));
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t("meetingInvites.decideError"));
    } finally {
      setDecidingId(null);
    }
  };

  return (
    <div className="space-y-6">
      <div className="border-b border-slate-200 pb-4">
        <h1 className="text-xl font-bold font-display tracking-tight text-slate-900">{t("meetingInvites.title")}</h1>
        <p className="text-xs text-slate-500 mt-1">{t("meetingInvites.description")}</p>
      </div>

      {error && <div className="text-xs font-bold text-rose-600 bg-rose-50 border border-rose-100 p-3 rounded-xl">{error}</div>}

      <Card padded={false} className="overflow-hidden">
        <div className="px-5 py-4 border-b border-slate-100 bg-slate-50">
          <span className="text-xs font-bold text-slate-700 font-display">{t("meetingInvites.sectionTitle")}</span>
        </div>

        {loading ? (
          <p className="text-xs text-slate-500 font-medium p-5">{t("meetingInvites.loading")}</p>
        ) : invites.length === 0 ? (
          <p className="text-xs text-slate-400 italic p-5">{t("meetingInvites.empty")}</p>
        ) : (
          <TableContainer className="rounded-none border-0">
            <thead>
              <tr>
                <Th>{t("meetingInvites.columns.student")}</Th>
                <Th>{t("meetingInvites.columns.class")}</Th>
                <Th>{t("meetingInvites.columns.channel")}</Th>
                <Th>{t("meetingInvites.columns.missCount")}</Th>
                <Th>{t("meetingInvites.columns.createdAt")}</Th>
                <Th>{t("meetingInvites.columns.action")}</Th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {invites.map((invite) => (
                <tr key={invite.id} className="hover:bg-slate-50/40">
                  <Td className="font-bold text-slate-900">{invite.studentName}</Td>
                  <Td>{invite.className}</Td>
                  <Td>{invite.channelLabel}</Td>
                  <Td>{t("meetingInvites.missCountValue", { count: invite.missCount })}</Td>
                  <Td className="whitespace-nowrap text-slate-500">{new Date(invite.createdAt).toLocaleString()}</Td>
                  <Td className="whitespace-nowrap">
                    <div className="flex gap-1.5">
                      <button
                        onClick={() => handleDecide(invite, "REJECTED")}
                        disabled={decidingId === invite.id}
                        className="px-2 py-1 text-rose-600 hover:bg-rose-50 border border-rose-200 text-[11px] font-bold rounded-lg disabled:opacity-50"
                      >
                        <X className="w-3 h-3 inline mr-0.5" />
                        {t("meetingInvites.actionReject")}
                      </button>
                      <button
                        onClick={() => handleDecide(invite, "APPROVED")}
                        disabled={decidingId === invite.id}
                        className="px-2 py-1 bg-emerald-600 hover:bg-emerald-700 text-white text-[11px] font-bold rounded-lg disabled:opacity-50"
                      >
                        <Check className="w-3 h-3 inline mr-0.5" />
                        {decidingId === invite.id ? t("meetingInvites.deciding") : t("meetingInvites.actionApprove")}
                      </button>
                    </div>
                  </Td>
                </tr>
              ))}
            </tbody>
          </TableContainer>
        )}
      </Card>

      <Toast message={toastMessage} />
    </div>
  );
}
