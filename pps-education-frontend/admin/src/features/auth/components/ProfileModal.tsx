import React, { useEffect, useState } from "react";
import { Mail, MapPin, Phone, ShieldCheck, User as UserIcon } from "lucide-react";
import { useTranslation } from "react-i18next";
import { ApiError } from "@/lib/apiClient";
import { useApp } from "@/context/AppContext";
import { EmployeeResponse, getMyEmployeeProfile, updateMyEmployeeProfile } from "@/features/hrm/api";
import { uploadMedia } from "@/features/lms/api";
import { roleLabel } from "@/constants/roles";
import { UserRole } from "@/types";
import Modal from "@/components/ui/Modal";
import Button from "@/components/ui/Button";
import Badge from "@/components/ui/Badge";
import Avatar from "@/components/ui/Avatar";
import AvatarUploadField from "@/components/ui/AvatarUploadField";
import Toast from "@/components/ui/Toast";
import { useToast } from "@/lib/useToast";

const inputClass = "w-full bg-slate-50 border border-slate-200 text-xs p-2.5 rounded-lg focus:outline-none";
const inputErrorClass = "w-full bg-rose-50/40 border border-rose-400 text-xs p-2.5 rounded-lg focus:outline-none focus:ring-1 focus:ring-rose-300";
const labelClass = "text-[10px] uppercase font-bold text-slate-500 block mb-1";

interface ProfileModalProps {
  onClose: () => void;
}

/** Xem hồ sơ cá nhân + UC-63 (tự sửa ảnh đại diện/địa chỉ nếu có hồ sơ Employee) — đổi mật khẩu (UC-45) đã tách sang ChangePasswordModal riêng. */
export default function ProfileModal({ onClose }: ProfileModalProps) {
  const { currentUser } = useApp();
  const { t } = useTranslation(["auth", "layout"]);

  // UC-63: chỉ Nhân viên (Giáo viên/Nhân viên/Quản lý) mới có hồ sơ Employee — SUPER_ADMIN thuần gọi /employees/me
  // sẽ 404, coi như không có phần này, không báo lỗi cho người dùng (im lặng ẩn khối tự sửa hồ sơ).
  const [employeeProfile, setEmployeeProfile] = useState<EmployeeResponse | null>(null);
  const [loadingEmployee, setLoadingEmployee] = useState(true);
  const [employeeForm, setEmployeeForm] = useState({ portraitUrl: "", permanentAddress: "", currentAddress: "" });
  const [savingProfile, setSavingProfile] = useState(false);
  const [profileError, setProfileError] = useState<string | null>(null);
  const { message: toastMessage, showToast } = useToast();

  useEffect(() => {
    getMyEmployeeProfile()
      .then((emp) => {
        setEmployeeProfile(emp);
        setEmployeeForm({
          portraitUrl: emp.portraitUrl ?? "",
          permanentAddress: emp.permanentAddress ?? "",
          currentAddress: emp.currentAddress ?? ""
        });
      })
      .catch(() => setEmployeeProfile(null))
      .finally(() => setLoadingEmployee(false));
  }, []);

  /** Ảnh đại diện lưu ngay khi chọn xong (upload + PUT nối tiếp) — không cần đợi bấm "Lưu hồ sơ" chung với địa chỉ. */
  const handleAvatarChange = async (url: string) => {
    setEmployeeForm((f) => ({ ...f, portraitUrl: url }));
    setProfileError(null);
    try {
      const updated = await updateMyEmployeeProfile({ portraitUrl: url });
      setEmployeeProfile(updated);
    } catch (err) {
      setProfileError(err instanceof ApiError ? err.message : t("profileModal.updateAvatarFailed"));
    }
  };

  const handleSaveProfile = async (e: React.FormEvent) => {
    e.preventDefault();
    setSavingProfile(true);
    setProfileError(null);
    try {
      const updated = await updateMyEmployeeProfile({
        portraitUrl: employeeForm.portraitUrl || undefined,
        permanentAddress: employeeForm.permanentAddress.trim() || undefined,
        currentAddress: employeeForm.currentAddress.trim() || undefined
      });
      setEmployeeProfile(updated);
      showToast(t("profileModal.saveSuccess"));
    } catch (err) {
      setProfileError(err instanceof ApiError ? err.message : t("profileModal.saveFailed"));
    } finally {
      setSavingProfile(false);
    }
  };

  return (
    <>
      <Modal open onClose={onClose} title={t("profileModal.title")} size="md">
      <div className="space-y-5">
        <div className="flex items-center gap-3 pb-4 border-b border-slate-100">
          {employeeProfile ? (
            <AvatarUploadField
              value={employeeForm.portraitUrl}
              onChange={handleAvatarChange}
              onUpload={(file) => uploadMedia(file, "EMPLOYEE")}
              fallbackName={currentUser?.fullName || "U"}
              size="md"
            />
          ) : (
            <Avatar name={currentUser?.fullName || "U"} size="md" />
          )}
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
                  {roleLabel(t, code as UserRole) ?? code}
                </Badge>
              ))}
            </div>
          </div>
        </div>

        {!loadingEmployee && employeeProfile && (
          <form onSubmit={handleSaveProfile} className="space-y-3 border-t border-slate-100 pt-4">
            <span className="text-[10px] font-bold uppercase text-slate-500 flex items-center gap-1.5">
              <MapPin className="w-3.5 h-3.5" />
              {t("profileModal.updateSectionTitle")}
            </span>

            {profileError && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg">{profileError}</div>}

            <div>
              <label className={labelClass}>{t("profileModal.permanentAddress")}</label>
              <input
                value={employeeForm.permanentAddress}
                onChange={(e) => setEmployeeForm({ ...employeeForm, permanentAddress: e.target.value })}
                className={inputClass}
              />
            </div>
            <div>
              <label className={labelClass}>{t("profileModal.currentAddress")}</label>
              <input
                value={employeeForm.currentAddress}
                onChange={(e) => setEmployeeForm({ ...employeeForm, currentAddress: e.target.value })}
                className={inputClass}
              />
            </div>
            <div className="flex justify-end">
              <Button type="submit" variant="secondary" disabled={savingProfile}>
                {savingProfile ? t("profileModal.saving") : t("profileModal.save")}
              </Button>
            </div>
          </form>
        )}

        <div className="flex justify-end pt-2">
          <Button type="button" variant="secondary" onClick={onClose}>
            {t("profileModal.close")}
          </Button>
        </div>
      </div>
      </Modal>
      <Toast message={toastMessage} />
    </>
  );
}
