import React, { useState } from "react";
import { KeyRound } from "lucide-react";
import { ApiError } from "@/lib/apiClient";
import { changeOwnPassword } from "../api";
import Modal from "@/components/ui/Modal";
import Button from "@/components/ui/Button";

const inputClass = "w-full bg-slate-50 border border-slate-200 text-sm p-2.5 rounded-lg focus:outline-none";
const inputErrorClass = "w-full bg-rose-50/40 border border-rose-400 text-sm p-2.5 rounded-lg focus:outline-none focus:ring-1 focus:ring-rose-300";
const labelClass = "text-sm uppercase font-bold text-slate-500 block mb-1";

interface ChangePasswordModalProps {
  onClose: () => void;
}

/** UC-45: tự đổi mật khẩu tài khoản đang đăng nhập — tách riêng khỏi ProfileModal (Hồ sơ cá nhân) theo yêu cầu người dùng. */
export default function ChangePasswordModal({ onClose }: ChangePasswordModalProps) {
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
      setError("Vui lòng nhập mật khẩu mới.");
      return;
    }
    if (newPassword.length < 8) {
      setError("Mật khẩu mới phải từ 8 ký tự trở lên.");
      return;
    }
    if (newPassword !== confirmPassword) {
      setError("Xác nhận mật khẩu mới không khớp.");
      return;
    }
    setSubmitting(true);
    setError(null);
    setSuccess(false);
    try {
      await changeOwnPassword(currentPassword, newPassword);
      setSuccess(true);
      setCurrentPassword("");
      setNewPassword("");
      setConfirmPassword("");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Đổi mật khẩu thất bại.");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Modal open onClose={onClose} title="Đổi mật khẩu" size="md">
      <form onSubmit={handleSubmit} className="space-y-3">
        <span className="text-sm font-bold uppercase text-slate-500 flex items-center gap-1.5">
          <KeyRound className="w-3.5 h-3.5" />
          Đổi mật khẩu
        </span>

        {error && <div className="text-sm text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg">{error}</div>}
        {success && <div className="text-sm text-emerald-700 bg-emerald-50 border border-emerald-100 p-2.5 rounded-lg">Đã đổi mật khẩu thành công.</div>}

        <div>
          <label className={labelClass}>Mật khẩu hiện tại</label>
          <input
            type="password"
            value={currentPassword}
            onChange={(e) => setCurrentPassword(e.target.value)}
            placeholder="Để trống nếu tài khoản chỉ đăng nhập bằng Google và chưa từng đặt mật khẩu"
            className={inputClass}
          />
        </div>
        <div>
          <label className={labelClass}>Mật khẩu mới *</label>
          <input
            type="password"
            value={newPassword}
            onChange={(e) => setNewPassword(e.target.value)}
            className={newPasswordInvalid ? inputErrorClass : inputClass}
          />
          {newPasswordInvalid && <p className="text-sm text-rose-600 mt-1">Mật khẩu mới phải từ 8 ký tự trở lên.</p>}
        </div>
        <div>
          <label className={labelClass}>Xác nhận mật khẩu mới *</label>
          <input
            type="password"
            value={confirmPassword}
            onChange={(e) => setConfirmPassword(e.target.value)}
            className={confirmMismatch ? inputErrorClass : inputClass}
          />
          {confirmMismatch && <p className="text-sm text-rose-600 mt-1">Xác nhận không khớp với mật khẩu mới.</p>}
        </div>

        <div className="flex justify-end gap-2 pt-1">
          <Button type="button" variant="secondary" onClick={onClose}>
            Đóng
          </Button>
          <Button type="submit" variant="primary" disabled={submitting}>
            {submitting ? "Đang lưu..." : "Đổi mật khẩu"}
          </Button>
        </div>
      </form>
    </Modal>
  );
}
