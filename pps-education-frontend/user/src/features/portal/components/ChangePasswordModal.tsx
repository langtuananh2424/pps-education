import React, { useState } from "react";
import { useTranslation } from "react-i18next";
import { KeyRound, X } from "lucide-react";
import { ApiError } from "@/lib/apiClient";
import { changeOwnPassword } from "../api";

interface ChangePasswordModalProps {
  onClose: () => void;
}

const inputClass = "w-full bg-sky-2/60 border border-line/70 text-xs p-2.5 rounded-[12px] focus:outline-none focus:ring-1 focus:ring-teal/40";
const inputErrorClass = "w-full bg-rose-50/60 border border-rose-300 text-xs p-2.5 rounded-[12px] focus:outline-none focus:ring-1 focus:ring-rose-300";
const labelClass = "text-[10px] uppercase font-extrabold text-muted block mb-1";

/** UC-45: Học sinh/Phụ huynh tự đổi mật khẩu — tách riêng khỏi ProfileModal, mở từ nút "Đổi mật khẩu" trong đó. */
export default function ChangePasswordModal({ onClose }: ChangePasswordModalProps) {
  const { t } = useTranslation("portal-account");
  const [currentPassword, setCurrentPassword] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState(false);

  const newPasswordInvalid = newPassword.length > 0 && newPassword.length < 8;
  const confirmMismatch = confirmPassword.length > 0 && confirmPassword !== newPassword;

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!newPassword) {
      setError(t("changePassword.newPasswordRequired"));
      return;
    }
    if (newPassword.length < 8) {
      setError(t("changePassword.newPasswordTooShort"));
      return;
    }
    if (newPassword !== confirmPassword) {
      setError(t("changePassword.confirmMismatch"));
      return;
    }
    setSubmitting(true);
    setError(null);
    try {
      await changeOwnPassword(currentPassword, newPassword);
      setSuccess(true);
      setCurrentPassword("");
      setNewPassword("");
      setConfirmPassword("");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t("changePassword.changeFailed"));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div
      className="fixed inset-0 bg-ink/40 backdrop-blur-sm z-[110] flex items-center justify-center p-4"
      onClick={(e) => {
        e.stopPropagation();
        onClose();
      }}
    >
      <div
        className="bg-white rounded-[24px] max-w-md w-full shadow-2xl overflow-hidden"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="bg-[linear-gradient(to_right,#17a6a0,#0e8c86,#1e2a45)] h-16 relative flex items-center px-6">
          <span className="text-white font-extrabold text-sm flex items-center gap-1.5">
            <KeyRound size={15} /> {t("changePassword.title")}
          </span>
          <button
            onClick={onClose}
            className="absolute top-4 right-4 w-8 h-8 rounded-full bg-white/20 hover:bg-white/30 flex items-center justify-center text-white transition-colors"
          >
            <X size={16} />
          </button>
        </div>

        <form onSubmit={handleSubmit} className="p-6 space-y-3">
          {success ? (
            <div className="text-xs text-emerald-700 bg-emerald-50 border border-emerald-100 p-3 rounded-[12px] font-bold">
              {t("changePassword.changeSuccess")}
            </div>
          ) : (
            <>
              {error && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-[12px] font-bold">{error}</div>}

              <div>
                <label className={labelClass}>{t("changePassword.currentPassword")}</label>
                <input
                  type="password"
                  value={currentPassword}
                  onChange={(e) => setCurrentPassword(e.target.value)}
                  placeholder={t("changePassword.currentPasswordPlaceholder")}
                  className={inputClass}
                />
              </div>
              <div>
                <label className={labelClass}>{t("changePassword.newPassword")}</label>
                <input
                  type="password"
                  value={newPassword}
                  onChange={(e) => setNewPassword(e.target.value)}
                  className={newPasswordInvalid ? inputErrorClass : inputClass}
                />
                {newPasswordInvalid && <p className="text-[10px] text-rose-600 mt-1 font-bold">{t("changePassword.newPasswordTooShort")}</p>}
              </div>
              <div>
                <label className={labelClass}>{t("changePassword.confirmPassword")}</label>
                <input
                  type="password"
                  value={confirmPassword}
                  onChange={(e) => setConfirmPassword(e.target.value)}
                  className={confirmMismatch ? inputErrorClass : inputClass}
                />
                {confirmMismatch && <p className="text-[10px] text-rose-600 mt-1 font-bold">{t("changePassword.confirmMismatch")}</p>}
              </div>
            </>
          )}

          <div className="flex justify-end gap-2 pt-1">
            <button
              type="button"
              onClick={onClose}
              className="px-3.5 py-1.5 text-[11px] font-extrabold text-muted hover:text-ink"
            >
              {t("changePassword.close")}
            </button>
            {!success && (
              <button
                type="submit"
                disabled={submitting}
                className="px-3.5 py-1.5 text-[11px] font-extrabold text-white bg-teal hover:bg-teal-deep rounded-[12px] disabled:opacity-50"
              >
                {submitting ? t("changePassword.saving") : t("changePassword.submit")}
              </button>
            )}
          </div>
        </form>
      </div>
    </div>
  );
}
