import React, { useEffect, useState } from "react";
import { Check, X } from "lucide-react";
import { useTranslation } from "react-i18next";
import { ApiError } from "@/lib/apiClient";
import { StudentAttitudeEscalationResponse, decideAttitudeEscalations, listPendingAttitudeEscalations } from "../api";
import Card from "@/components/ui/Card";
import TableContainer, { Td, Th } from "@/components/ui/TableContainer";
import { useDialog } from "@/components/ui/DialogProvider";
import { useToast } from "@/lib/useToast";
import Toast from "@/components/ui/Toast";

/**
 * Duyệt cảnh báo thái độ học tập Yếu/Trung bình liên tục 3 buổi trước khi gửi xuống Phụ huynh —
 * bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-12. Mirror 1:1
 * MeetingInviteApprovalPage.tsx. Chỉ Quản lý điểm trường phụ trách đúng site mới thấy/xử lý
 * được (BE tự chặn qua site_managers) — cảnh báo 1 buổi đơn lẻ (STUDENT_ATTITUDE_ALERT) gửi
 * thẳng ngay lúc nhận xét được duyệt, không xuất hiện ở đây.
 */
export default function StudentAttitudeEscalationApprovalPage() {
  const { t } = useTranslation("notifications");
  const [escalations, setEscalations] = useState<StudentAttitudeEscalationResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [decidingId, setDecidingId] = useState<number | null>(null);
  const { promptDialog } = useDialog();
  const { message: toastMessage, showToast } = useToast();

  const load = () => {
    setLoading(true);
    setError(null);
    listPendingAttitudeEscalations()
      .then(setEscalations)
      .catch((err) => setError(err instanceof ApiError ? err.message : t("attitudeEscalations.loadError")))
      .finally(() => setLoading(false));
  };

  useEffect(load, []);

  const handleDecide = async (escalation: StudentAttitudeEscalationResponse, decision: "APPROVED" | "REJECTED") => {
    let reason: string | undefined;
    if (decision === "REJECTED") {
      reason = (await promptDialog(t("attitudeEscalations.rejectReasonPrompt"), { required: true, multiline: true })) ?? undefined;
      if (!reason?.trim()) return;
    }
    setDecidingId(escalation.id);
    setError(null);
    try {
      await decideAttitudeEscalations([escalation.id], decision, reason?.trim());
      setEscalations((prev) => prev.filter((it) => it.id !== escalation.id));
      showToast(decision === "APPROVED" ? t("attitudeEscalations.approvedToast") : t("attitudeEscalations.rejectedToast"));
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t("attitudeEscalations.decideError"));
    } finally {
      setDecidingId(null);
    }
  };

  return (
    <div className="space-y-6">
      <div className="border-b border-slate-200 pb-4">
        <h1 className="text-xl font-bold font-display tracking-tight text-slate-900">{t("attitudeEscalations.title")}</h1>
        <p className="text-xs text-slate-500 mt-1">{t("attitudeEscalations.description")}</p>
      </div>

      {error && <div className="text-xs font-bold text-rose-600 bg-rose-50 border border-rose-100 p-3 rounded-xl">{error}</div>}

      <Card padded={false} className="overflow-hidden">
        <div className="px-5 py-4 border-b border-slate-100 bg-slate-50">
          <span className="text-xs font-bold text-slate-700 font-display">{t("attitudeEscalations.sectionTitle")}</span>
        </div>

        {loading ? (
          <p className="text-xs text-slate-500 font-medium p-5">{t("attitudeEscalations.loading")}</p>
        ) : escalations.length === 0 ? (
          <p className="text-xs text-slate-400 italic p-5">{t("attitudeEscalations.empty")}</p>
        ) : (
          <TableContainer className="rounded-none border-0">
            <thead>
              <tr>
                <Th>{t("attitudeEscalations.columns.student")}</Th>
                <Th>{t("attitudeEscalations.columns.class")}</Th>
                <Th>{t("attitudeEscalations.columns.streakCount")}</Th>
                <Th>{t("attitudeEscalations.columns.createdAt")}</Th>
                <Th>{t("attitudeEscalations.columns.action")}</Th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {escalations.map((escalation) => (
                <tr key={escalation.id} className="hover:bg-slate-50/40">
                  <Td className="font-bold text-slate-900">{escalation.studentName}</Td>
                  <Td>{escalation.className}</Td>
                  <Td>{t("attitudeEscalations.streakCountValue", { count: escalation.streakCount })}</Td>
                  <Td className="whitespace-nowrap text-slate-500">{new Date(escalation.createdAt).toLocaleString()}</Td>
                  <Td className="whitespace-nowrap">
                    <div className="flex gap-1.5">
                      <button
                        onClick={() => handleDecide(escalation, "REJECTED")}
                        disabled={decidingId === escalation.id}
                        className="px-2 py-1 text-rose-600 hover:bg-rose-50 border border-rose-200 text-[11px] font-bold rounded-lg disabled:opacity-50"
                      >
                        <X className="w-3 h-3 inline mr-0.5" />
                        {t("attitudeEscalations.actionReject")}
                      </button>
                      <button
                        onClick={() => handleDecide(escalation, "APPROVED")}
                        disabled={decidingId === escalation.id}
                        className="px-2 py-1 bg-emerald-600 hover:bg-emerald-700 text-white text-[11px] font-bold rounded-lg disabled:opacity-50"
                      >
                        <Check className="w-3 h-3 inline mr-0.5" />
                        {decidingId === escalation.id ? t("attitudeEscalations.deciding") : t("attitudeEscalations.actionApprove")}
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
