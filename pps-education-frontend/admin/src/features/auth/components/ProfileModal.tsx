import React, { useState } from "react";
import { KeyRound, Mail, Phone, ShieldCheck, User as UserIcon } from "lucide-react";
import { ApiError } from "@/lib/apiClient";
import { useApp } from "@/context/AppContext";
import { changeOwnPassword } from "../api";
import { roleLabels } from "@/constants/roles";
import { UserRole } from "@/types";
import Modal from "@/components/ui/Modal";
import Button from "@/components/ui/Button";
import Badge from "@/components/ui/Badge";
import Avatar from "@/components/ui/Avatar";

const inputClass = "w-full bg-slate-50 border border-slate-200 text-xs p-2.5 rounded-lg focus:outline-none";
const inputErrorClass = "w-full bg-rose-50/40 border border-rose-400 text-xs p-2.5 rounded-lg focus:outline-none focus:ring-1 focus:ring-rose-300";
const labelClass = "text-[10px] uppercase font-bold text-slate-500 block mb-1";

interface ProfileModalProps {
  onClose: () => void;
}

/** UC-45: xem hồ sơ cá nhân + tự đổi mật khẩu tài khoản đang đăng nhập. */
export default function ProfileModal({ onClose }: ProfileModalProps) {
  const { currentUser } = useApp();
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
    <Modal open onClose={onClose} title="Hồ sơ cá nhân" size="md">
      <div className="space-y-5">
        <div className="flex items-center gap-3 pb-4 border-b border-slate-100">
          <Avatar name={currentUser?.fullName || "U"} size="md" />
          <div>
            <h3 className="text-sm font-bold text-slate-900">{currentUser?.fullName}</h3>
            <p className="text-[11px] text-slate-400 font-mono">@{currentUser?.username}</p>
          </div>
        </div>

        <div className="space-y-2.5 text-xs">
          <div className="flex items-center gap-2 text-slate-600">
            <Mail className="w-3.5 h-3.5 text-slate-400 shrink-0" />
            <span>{currentUser?.email}</span>
          </div>
          {currentUser?.phone && (
            <div className="flex items-center gap-2 text-slate-600">
              <Phone className="w-3.5 h-3.5 text-slate-400 shrink-0" />
              <span>{currentUser.phone}</span>
            </div>
          )}
          {currentUser?.departmentName && (
            <div className="flex items-center gap-2 text-slate-600">
              <UserIcon className="w-3.5 h-3.5 text-slate-400 shrink-0" />
              <span>{currentUser.departmentName}</span>
            </div>
          )}
          <div className="flex items-start gap-2 text-slate-600">
            <ShieldCheck className="w-3.5 h-3.5 text-slate-400 shrink-0 mt-0.5" />
            <div className="flex flex-wrap gap-1.5">
              {(currentUser?.roleCodes ?? []).map((code) => (
                <Badge key={code} variant="brand">
                  {roleLabels[code as UserRole] ?? code}
                </Badge>
              ))}
            </div>
          </div>
        </div>

        <form onSubmit={handleSubmit} className="space-y-3 border-t border-slate-100 pt-4">
          <span className="text-[10px] font-bold uppercase text-slate-500 flex items-center gap-1.5">
            <KeyRound className="w-3.5 h-3.5" />
            Đổi mật khẩu
          </span>

          {error && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg">{error}</div>}
          {success && <div className="text-xs text-emerald-700 bg-emerald-50 border border-emerald-100 p-2.5 rounded-lg">Đã đổi mật khẩu thành công.</div>}

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
            {newPasswordInvalid && <p className="text-[10px] text-rose-600 mt-1">Mật khẩu mới phải từ 8 ký tự trở lên.</p>}
          </div>
          <div>
            <label className={labelClass}>Xác nhận mật khẩu mới *</label>
            <input
              type="password"
              value={confirmPassword}
              onChange={(e) => setConfirmPassword(e.target.value)}
              className={confirmMismatch ? inputErrorClass : inputClass}
            />
            {confirmMismatch && <p className="text-[10px] text-rose-600 mt-1">Xác nhận không khớp với mật khẩu mới.</p>}
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
      </div>
    </Modal>
  );
}
