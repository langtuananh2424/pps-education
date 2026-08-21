import React, { useState } from "react";
import { KeyRound } from "lucide-react";
import { useTranslation } from "react-i18next";
import { ApiError } from "@/lib/apiClient";
import { changeOwnPassword } from "../api";
import Modal from "@/components/ui/Modal";
import Button from "@/components/ui/Button";
import Toast from "@/components/ui/Toast";
import { useToast } from "@/lib/useToast";

const inputClass = "w-full bg-slate-50 border border-slate-200 text-xs p-2.5 rounded-lg focus:outline-none";
const inputErrorClass = "w-full bg-rose-50/40 border border-rose-400 text-xs p-2.5 rounded-lg focus:outline-none focus:ring-1 focus:ring-rose-300";
const labelClass = "text-[10px] uppercase font-bold text-slate-500 block mb-1";

interface ChangePasswordModalProps {
  onClose: () => void;
}

/** UC-45: tự đổi mật khẩu tài khoản đang đăng nhập — tách riêng khỏi ProfileModal (Hồ sơ cá nhân) theo yêu cầu người dùng. */
export default function ChangePasswordModal({ onClose }: ChangePasswordModalProps) {
  const { t } = useTranslation("auth");
  const [currentPassword, setCurrentPassword] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const { message: toastMessage, showToast } = useToast();

  const newPasswordInvalid = newPassword.length > 0 && newPassword.length < 8;
  const confirmMismatch = confirmPassword.length > 0 && confirmPassword !== newPassword;

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!newPassword) {
      setError(t("changePasswordModal.newPasswordRequired"));
      return;
    }
    if (newPassword.length < 8) {
      setError(t("changePasswordModal.newPasswordTooShort"));
      return;
    }
    if (newPassword !== confirmPassword) {
      setError(t("changePasswordModal.confirmMismatch"));
      return;
    }
    setSubmitting(true);
    setError(null);
    try {
      await changeOwnPassword(currentPassword, newPassword);
      showToast(t("changePasswordModal.changeSuccess"));
      setCurrentPassword("");
      setNewPassword("");
      setConfirmPassword("");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t("changePasswordModal.changeFailed"));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <>
      <Modal open onClose={onClose} title={t("changePasswordModal.title")} size="md">
      <form onSubmit={handleSubmit} className="space-y-3">
        <span className="text-[10px] font-bold uppercase text-slate-500 flex items-center gap-1.5">
          <KeyRound className="w-3.5 h-3.5" />
          {t("changePasswordModal.sectionTitle")}
        </span>

        {error && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg">{error}</div>}

        <div>
          <label className={labelClass}>{t("changePasswordModal.currentPassword")}</label>
          <input
            type="password"
            value={currentPassword}
            onChange={(e) => setCurrentPassword(e.target.value)}
            placeholder={t("changePasswordModal.currentPasswordPlaceholder")}
            className={inputClass}
          />
        </div>
        <div>
          <label className={labelClass}>{t("changePasswordModal.newPassword")}</label>
          <input
            type="password"
            value={newPassword}
            onChange={(e) => setNewPassword(e.target.value)}
            className={newPasswordInvalid ? inputErrorClass : inputClass}
          />
          {newPasswordInvalid && <p className="text-[10px] text-rose-600 mt-1">{t("changePasswordModal.newPasswordTooShort")}</p>}
        </div>
        <div>
          <label className={labelClass}>{t("changePasswordModal.confirmPassword")}</label>
          <input
            type="password"
            value={confirmPassword}
            onChange={(e) => setConfirmPassword(e.target.value)}
            className={confirmMismatch ? inputErrorClass : inputClass}
          />
          {confirmMismatch && <p className="text-[10px] text-rose-600 mt-1">{t("changePasswordModal.confirmMismatch")}</p>}
        </div>

        <div className="flex justify-end gap-2 pt-1">
          <Button type="button" variant="secondary" onClick={onClose}>
            {t("changePasswordModal.close")}
          </Button>
          <Button type="submit" variant="primary" disabled={submitting}>
            {submitting ? t("changePasswordModal.saving") : t("changePasswordModal.submit")}
          </Button>
        </div>
      </form>
      </Modal>
      <Toast message={toastMessage} />
    </>
  );
}
