import { TriangleAlert } from "lucide-react";
import { useTranslation } from "react-i18next";

interface ForceLogoutConfirmDialogProps {
  submitting: boolean;
  onCancel: () => void;
  onConfirm: () => void;
}

/**
 * Bổ sung ngoài SDD gốc (đã xác nhận với người dùng 2026-09-19) — popup nổi (khác banner nội tuyến
 * trong form) hỏi xác nhận khi tài khoản Học sinh đã có phiên ACTIVE ở thiết bị khác (backend trả 409,
 * xem AuthService#requireNoActiveSessionForStudent). Style theo đúng pattern overlay + card căn giữa
 * đã dùng ở ChangePasswordModal.tsx/ProfileModal.tsx — tái dùng để nhất quán giao diện toàn app.
 */
export default function ForceLogoutConfirmDialog({ submitting, onCancel, onConfirm }: ForceLogoutConfirmDialogProps) {
  const { t } = useTranslation("auth");

  return (
    <div
      className="fixed inset-0 bg-ink/40 backdrop-blur-sm z-[110] flex items-center justify-center p-4"
      onClick={(e) => {
        e.stopPropagation();
        onCancel();
      }}
    >
      <div className="bg-white rounded-[24px] max-w-sm w-full shadow-2xl overflow-hidden" onClick={(e) => e.stopPropagation()}>
        <div className="p-6 flex flex-col items-center text-center gap-3">
          <div className="w-12 h-12 rounded-full bg-amber-50 flex items-center justify-center">
            <TriangleAlert size={22} className="text-amber-600" />
          </div>
          <p className="text-sm font-bold text-ink">{t("card.forceLogout.message")}</p>
        </div>
        <div className="flex border-t border-line/60">
          <button
            type="button"
            onClick={onCancel}
            className="flex-1 py-3 text-xs font-extrabold text-slate-600 hover:bg-slate-50"
          >
            {t("card.forceLogout.cancel")}
          </button>
          <button
            type="button"
            disabled={submitting}
            onClick={onConfirm}
            className="flex-1 py-3 text-xs font-extrabold text-white bg-teal hover:bg-teal-deep disabled:opacity-50"
          >
            {submitting ? t("card.signingIn") : t("card.forceLogout.confirmButton")}
          </button>
        </div>
      </div>
    </div>
  );
}
