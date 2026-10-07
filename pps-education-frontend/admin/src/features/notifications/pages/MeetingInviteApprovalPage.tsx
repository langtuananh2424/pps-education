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
import FloatingError from "@/components/ui/FloatingError";
import Pagination from "@/components/ui/Pagination";
import { usePagedSelection } from "../usePagedSelection";

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
  const [decidingIds, setDecidingIds] = useState<number[]>([]);
  const paged = usePagedSelection(invites);
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

  const handleDecide = async (ids: number[], decision: "APPROVED" | "REJECTED") => {
    if (ids.length === 0) return;
    let reason: string | undefined;
    if (decision === "REJECTED") {
      reason = (await promptDialog(t("meetingInvites.rejectReasonPrompt"), { required: true, multiline: true })) ?? undefined;
      if (!reason?.trim()) return;
    }
    setDecidingIds(ids);
    setError(null);
    try {
      await decideMeetingInvites(ids, decision, reason?.trim());
      setInvites((prev) => prev.filter((it) => !ids.includes(it.id)));
      showToast(
        ids.length > 1
          ? t(decision === "APPROVED" ? "meetingInvites.bulkApprovedToast" : "meetingInvites.bulkRejectedToast", { count: ids.length })
          : t(decision === "APPROVED" ? "meetingInvites.approvedToast" : "meetingInvites.rejectedToast")
      );
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t("meetingInvites.decideError"));
    } finally {
      setDecidingIds([]);
    }
  };

  return (
    <div className="space-y-6">
      <div className="border-b border-slate-200 pb-4">
        <h1 className="text-xl font-bold font-display tracking-tight text-slate-900">{t("meetingInvites.title")}</h1>
        <p className="text-sm text-slate-500 mt-1">{t("meetingInvites.description")}</p>
      </div>

      <FloatingError message={error} onClose={() => setError(null)} />

      <Card padded={false} className="overflow-hidden">
        <div className="px-5 py-4 border-b border-slate-100 bg-slate-50">
          <span className="text-sm font-bold text-slate-700 font-display">{t("meetingInvites.sectionTitle")}</span>
        </div>

        {loading ? (
          <p className="text-sm text-slate-500 font-medium p-5">{t("meetingInvites.loading")}</p>
        ) : invites.length === 0 ? (
          <p className="text-sm text-slate-400 italic p-5">{t("meetingInvites.empty")}</p>
        ) : (
          <>
          {paged.selectedIds.length > 0 && (
            <div className="flex flex-wrap items-center justify-between gap-2 px-5 py-2.5 border-b border-slate-100 bg-amber-50/60">
              <span className="text-[13px] font-bold text-slate-700">
                {t("meetingInvites.selectedCount", { count: paged.selectedIds.length })}
              </span>
              <div className="flex gap-1.5">
                <button
                  onClick={paged.clearSelection}
                  disabled={decidingIds.length > 0}
                  className="px-2 py-1 text-slate-600 hover:bg-slate-100 border border-slate-200 text-[13px] font-bold rounded-lg disabled:opacity-50"
                >
                  {t("meetingInvites.clearSelection")}
                </button>
                <button
                  onClick={() => handleDecide(paged.selectedIds, "REJECTED")}
                  disabled={decidingIds.length > 0}
                  className="px-2 py-1 text-rose-600 hover:bg-rose-50 border border-rose-200 text-[13px] font-bold rounded-lg disabled:opacity-50"
                >
                  <X className="w-3 h-3 inline mr-0.5" />
                  {t("meetingInvites.bulkReject")}
                </button>
                <button
                  onClick={() => handleDecide(paged.selectedIds, "APPROVED")}
                  disabled={decidingIds.length > 0}
                  className="px-2 py-1 bg-emerald-600 hover:bg-emerald-700 text-white text-[13px] font-bold rounded-lg disabled:opacity-50"
                >
                  <Check className="w-3 h-3 inline mr-0.5" />
                  {t("meetingInvites.bulkApprove")}
                </button>
              </div>
            </div>
          )}
          <TableContainer className="rounded-none border-0">
            <thead>
              <tr>
                <Th className="w-10">
                  <input
                    type="checkbox"
                    aria-label={t("meetingInvites.selectPage")}
                    checked={paged.allPageSelected}
                    ref={(el) => {
                      if (el) el.indeterminate = !paged.allPageSelected && paged.somePageSelected;
                    }}
                    onChange={paged.togglePage}
                  />
                </Th>
                <Th>{t("meetingInvites.columns.student")}</Th>
                <Th>{t("meetingInvites.columns.class")}</Th>
                <Th>{t("meetingInvites.columns.channel")}</Th>
                <Th>{t("meetingInvites.columns.missCount")}</Th>
                <Th>{t("meetingInvites.columns.createdAt")}</Th>
                <Th>{t("meetingInvites.columns.action")}</Th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {paged.pageItems.map((invite) => (
                <tr key={invite.id} className="hover:bg-slate-50/40">
                  <Td className="w-10">
                    <input type="checkbox" checked={paged.selectedSet.has(invite.id)} onChange={() => paged.toggleOne(invite.id)} />
                  </Td>
                  <Td className="font-bold text-slate-900">{invite.studentName}</Td>
                  <Td>{invite.className}</Td>
                  <Td>{invite.channelLabel}</Td>
                  <Td>{t("meetingInvites.missCountValue", { count: invite.missCount })}</Td>
                  <Td className="whitespace-nowrap text-slate-500">{new Date(invite.createdAt).toLocaleString()}</Td>
                  <Td className="whitespace-nowrap">
                    <div className="flex gap-1.5">
                      <button
                        onClick={() => handleDecide([invite.id], "REJECTED")}
                        disabled={decidingIds.includes(invite.id)}
                        className="px-2 py-1 text-rose-600 hover:bg-rose-50 border border-rose-200 text-[13px] font-bold rounded-lg disabled:opacity-50"
                      >
                        <X className="w-3 h-3 inline mr-0.5" />
                        {t("meetingInvites.actionReject")}
                      </button>
                      <button
                        onClick={() => handleDecide([invite.id], "APPROVED")}
                        disabled={decidingIds.includes(invite.id)}
                        className="px-2 py-1 bg-emerald-600 hover:bg-emerald-700 text-white text-[13px] font-bold rounded-lg disabled:opacity-50"
                      >
                        <Check className="w-3 h-3 inline mr-0.5" />
                        {decidingIds.includes(invite.id) ? t("meetingInvites.deciding") : t("meetingInvites.actionApprove")}
                      </button>
                    </div>
                  </Td>
                </tr>
              ))}
            </tbody>
          </TableContainer>
          <Pagination
            page={paged.page}
            pageSize={paged.pageSize}
            totalElements={invites.length}
            itemLabel={t("meetingInvites.itemLabel")}
            onPageChange={paged.setPage}
            onPageSizeChange={paged.setPageSize}
          />
          </>
        )}
      </Card>

      <Toast message={toastMessage} />
    </div>
  );
}
