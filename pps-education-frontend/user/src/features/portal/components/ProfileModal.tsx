import React, { useEffect, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { Award, Camera, GraduationCap, Heart, Loader2, Lock, Phone, Sparkles, User, Users, X } from "lucide-react";
import { ApiError } from "@/lib/apiClient";
import { getMyParentProfile, getMyParents, getMyStudentProfile, updateMyParentProfile, updateMyStudentProfile, uploadMedia } from "../api";

interface ProfileModalProps {
  fullName: string;
  studentCode: string | null;
  className: string | null;
  classCode: string | null;
  enrollmentStatus: string | null;
  /**
   * Có giá trị khi Phụ huynh đang xem con mình — chính là thông tin của
   * `currentUser` đang đăng nhập (không cần gọi API riêng, Phụ huynh xem con
   * nào thì hiển thị đúng liên hệ của chính họ). `null` khi Học sinh tự xem
   * hồ sơ của mình — component tự gọi GET /students/me/parents (xem effect
   * bên dưới) để lấy phụ huynh liên hệ chính (isPrimaryContact).
   */
  parentName: string | null;
  parentPhone: string | null;
  /** UC-63: true khi Học sinh đang tự xem hồ sơ của chính mình — cho sửa ảnh đại diện ngay trên avatar này. */
  isStudent: boolean;
  /** UC-63: true khi Phụ huynh đang xem hồ sơ con — hồ sơ CỦA CHÍNH Phụ huynh (khác con) sửa ở khối "Liên hệ gia đình". */
  isParent: boolean;
  onClose: () => void;
  /** UC-63: báo cho Header (PortalPage) biết ảnh đại diện vừa đổi — trước đây Header không hay biết
   * gì, đóng modal xong avatar ngoài Header vẫn hiện chữ cái thay vì ảnh vừa tải lên. */
  onPortraitUpdated?: (portraitUrl: string | null) => void;
}

/**
 * Khớp layout modal "Thông tin cá nhân" trong bản thiết kế gốc (Google AI Studio),
 * nhưng chỉ điền dữ liệu thật đang có (họ tên, lớp, trạng thái ghi danh). Phần
 * "Thành tích & điểm thưởng" (EXP/xu/streak/huy hiệu) không tồn tại trong schema
 * PPS Education — không tự bịa số liệu, hiện "—"/khoá thay vì số giả.
 */
export default function ProfileModal({
  fullName,
  studentCode,
  className,
  classCode,
  enrollmentStatus,
  parentName,
  parentPhone,
  isStudent,
  isParent,
  onClose,
  onPortraitUpdated
}: ProfileModalProps) {
  const { t } = useTranslation("portal-account");
  const [studentPortraitUrl, setStudentPortraitUrl] = useState<string | null>(null);
  const [uploadingAvatar, setUploadingAvatar] = useState(false);
  const [avatarError, setAvatarError] = useState<string | null>(null);
  const studentFileInputRef = useRef<HTMLInputElement>(null);

  /** UC-63: Học sinh tự xem hồ sơ — phụ huynh liên hệ chính (isPrimaryContact), tự tra qua GET /students/me/parents. */
  const [myParentContact, setMyParentContact] = useState<{ fullName: string; phone: string | null } | null>(null);

  const [editingParent, setEditingParent] = useState(false);
  const [parentPortraitUrl, setParentPortraitUrl] = useState<string | null>(null);
  const [parentForm, setParentForm] = useState({ occupation: "", workplace: "", address: "" });
  const [savingParent, setSavingParent] = useState(false);
  const [parentError, setParentError] = useState<string | null>(null);

  useEffect(() => {
    if (isStudent) {
      getMyStudentProfile().then((p) => setStudentPortraitUrl(p.portraitUrl)).catch(() => undefined);
      getMyParents()
        .then((parents) => {
          const primary = parents.find((p) => p.isPrimaryContact) ?? parents[0];
          if (primary) setMyParentContact({ fullName: primary.parentFullName, phone: primary.parentPhone });
        })
        .catch(() => undefined);
    } else if (isParent) {
      getMyParentProfile()
        .then((p) => {
          setParentPortraitUrl(p.portraitUrl);
          setParentForm({ occupation: p.occupation ?? "", workplace: p.workplace ?? "", address: p.address ?? "" });
        })
        .catch(() => undefined);
    }
  }, [isStudent, isParent]);

  const handleStudentAvatarChange = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    e.target.value = "";
    if (!file) return;
    setUploadingAvatar(true);
    setAvatarError(null);
    try {
      const { url } = await uploadMedia(file, "STUDENT");
      const updated = await updateMyStudentProfile({ portraitUrl: url });
      setStudentPortraitUrl(updated.portraitUrl);
      onPortraitUpdated?.(updated.portraitUrl);
    } catch (err) {
      setAvatarError(err instanceof ApiError ? err.message : t("profile.errors.avatarUpdateFailed"));
    } finally {
      setUploadingAvatar(false);
    }
  };

  /** Ảnh đại diện lưu ngay khi chọn xong (upload + PUT nối tiếp) — không cần đợi bấm "Lưu" chung với nghề nghiệp/nơi làm/địa chỉ. */
  const handleParentAvatarPick = async (file: File) => {
    setSavingParent(true);
    setParentError(null);
    try {
      const { url } = await uploadMedia(file, "PARENT");
      const updated = await updateMyParentProfile({ portraitUrl: url });
      setParentPortraitUrl(updated.portraitUrl);
    } catch (err) {
      setParentError(err instanceof ApiError ? err.message : t("profile.errors.avatarUploadFailed"));
    } finally {
      setSavingParent(false);
    }
  };

  const handleSaveParentProfile = async () => {
    setSavingParent(true);
    setParentError(null);
    try {
      const updated = await updateMyParentProfile({
        occupation: parentForm.occupation.trim() || undefined,
        workplace: parentForm.workplace.trim() || undefined,
        address: parentForm.address.trim() || undefined,
        portraitUrl: parentPortraitUrl || undefined
      });
      setParentPortraitUrl(updated.portraitUrl);
      setEditingParent(false);
    } catch (err) {
      setParentError(err instanceof ApiError ? err.message : t("profile.errors.profileUpdateFailed"));
    } finally {
      setSavingParent(false);
    }
  };

  const displayParentName = parentName ?? myParentContact?.fullName ?? null;
  const displayParentPhone = parentPhone ?? myParentContact?.phone ?? null;

  return (
    <div className="fixed inset-0 bg-ink/40 backdrop-blur-sm z-[100] flex items-center justify-center p-4" onClick={onClose}>
      <div
        className="bg-white rounded-[24px] max-w-2xl w-full max-h-[88vh] overflow-y-auto shadow-2xl"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="bg-[linear-gradient(to_right,#17a6a0,#0e8c86,#1e2a45)] h-24 relative">
          <button
            onClick={onClose}
            className="absolute top-4 right-4 w-8 h-8 rounded-full bg-white/20 hover:bg-white/30 flex items-center justify-center text-white transition-colors"
          >
            <X size={16} />
          </button>
        </div>

        <div className="px-7 pb-7">
          <div className="flex items-end gap-4 -mt-10 mb-6">
            <div className="relative shrink-0">
              {isStudent && (
                <input ref={studentFileInputRef} type="file" accept="image/*" className="hidden" onChange={handleStudentAvatarChange} disabled={uploadingAvatar} />
              )}
              <div className="w-20 h-20 rounded-full bg-sky-2 border-4 border-white shadow-md flex items-center justify-center text-teal-deep overflow-hidden">
                {studentPortraitUrl ? <img src={studentPortraitUrl} alt="" className="w-full h-full object-cover" /> : <GraduationCap size={30} />}
              </div>
              {isStudent ? (
                <button
                  type="button"
                  onClick={() => studentFileInputRef.current?.click()}
                  disabled={uploadingAvatar}
                  title={t("profile.changeAvatar")}
                  className="absolute bottom-0 right-0 w-7 h-7 rounded-full bg-teal hover:bg-teal-deep text-white flex items-center justify-center shadow-md border-2 border-white disabled:opacity-50"
                >
                  {uploadingAvatar ? <Loader2 size={13} className="animate-spin" /> : <Camera size={13} />}
                </button>
              ) : (
                <span className="absolute bottom-0 right-0 w-4 h-4 rounded-full bg-emerald-400 border-2 border-white" />
              )}
            </div>
            <div className="pb-1 flex items-center gap-2 flex-wrap">
              <h3 className="text-xl font-extrabold text-ink">{fullName}</h3>
              <span className="bg-teal/10 text-teal-deep border border-teal/30 px-2.5 py-0.5 rounded-full text-[10px] font-extrabold uppercase tracking-wide">
                {t("profile.studentBadge")}
              </span>
            </div>
          </div>
          {avatarError && <p className="text-[10px] text-rose-500 font-bold -mt-4 mb-3">{avatarError}</p>}
          <p className="text-[11px] text-muted font-bold -mt-4 mb-6 flex items-center gap-1.5">
            <User size={12} /> {t("profile.systemId", { code: studentCode ?? "—" })}
          </p>

          <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
            <div>
              <h4 className="text-xs font-extrabold text-teal-deep uppercase tracking-wide flex items-center gap-1.5 mb-3">
                <GraduationCap size={14} /> {t("profile.academicInfo")}
              </h4>
              <div className="bg-sky-2/60 border border-line/70 rounded-[16px] p-4 space-y-2.5 text-xs">
                <div className="flex justify-between border-b border-line/60 pb-2">
                  <span className="text-muted font-bold">{t("profile.classLabel")}</span>
                  <span className="font-extrabold text-ink text-right">
                    {className ?? "—"}
                    {classCode ? ` (${classCode})` : ""}
                  </span>
                </div>
                <div className="flex justify-between">
                  <span className="text-muted font-bold">{t("profile.statusLabel")}</span>
                  <span className="font-extrabold text-teal-deep">
                    {enrollmentStatus ? t(`profile.status.${enrollmentStatus}`, { defaultValue: enrollmentStatus }) : "—"}
                  </span>
                </div>
              </div>
            </div>

            <div>
              <h4 className="text-xs font-extrabold text-gold uppercase tracking-wide flex items-center gap-1.5 mb-3">
                <Award size={14} /> {t("profile.achievements")}
              </h4>
              <div className="space-y-2">
                <div className="bg-gold/5 border border-dashed border-gold/30 rounded-[16px] p-3 flex items-center justify-between">
                  <div className="flex items-center gap-2">
                    <Lock size={13} className="text-gold/70" />
                    <span className="text-[11px] font-bold text-muted">{t("profile.learningProgressLocked")}</span>
                  </div>
                  <span className="text-[10px] font-extrabold text-gold uppercase">{t("profile.comingSoon")}</span>
                </div>
                <div className="grid grid-cols-2 gap-2">
                  <div className="bg-gold/5 border border-dashed border-gold/30 rounded-[16px] p-3 text-center">
                    <p className="text-[10px] font-bold text-muted uppercase mb-1">{t("profile.rewardWallet")}</p>
                    <p className="text-base font-extrabold text-gold/60">—</p>
                  </div>
                  <div className="bg-gold/5 border border-dashed border-gold/30 rounded-[16px] p-3 text-center">
                    <p className="text-[10px] font-bold text-muted uppercase mb-1">{t("profile.learningStreak")}</p>
                    <p className="text-base font-extrabold text-gold/60">—</p>
                  </div>
                </div>
              </div>
            </div>
          </div>

          <div className="mt-6">
            <div className="flex items-center justify-between mb-3">
              <h4 className="text-xs font-extrabold text-teal-deep uppercase tracking-wide flex items-center gap-1.5">
                <Users size={14} /> {t("profile.familyContact")}
              </h4>
              {isParent && !editingParent && (
                <button onClick={() => setEditingParent(true)} className="text-[10px] font-extrabold text-teal-deep hover:underline">
                  {t("profile.editMyProfile")}
                </button>
              )}
            </div>
            <div className="bg-sky-2/60 border border-line/70 rounded-[16px] p-4 space-y-2.5 text-xs">
              <div className="flex justify-between items-center border-b border-line/60 pb-2">
                <span className="text-muted font-bold flex items-center gap-1.5">
                  <User size={12} /> {t("profile.parentLabel")}
                </span>
                <span className={`font-extrabold ${displayParentName ? "text-ink" : "text-muted/70"}`}>{displayParentName ?? "—"}</span>
              </div>
              <div className="flex justify-between items-center">
                <span className="text-muted font-bold flex items-center gap-1.5">
                  <Phone size={12} /> {t("profile.phoneLabel")}
                </span>
                <span className={`font-extrabold ${displayParentPhone ? "text-ink" : "text-muted/70"}`}>{displayParentPhone ?? "—"}</span>
              </div>
              {isStudent && !displayParentName && (
                <p className="text-[10px] text-muted font-bold pt-1">{t("profile.noParentLinked")}</p>
              )}

              {isParent && editingParent && (
                <div className="border-t border-line/60 pt-3 mt-1 space-y-2.5">
                  {parentError && <p className="text-[10px] text-rose-500 font-bold">{parentError}</p>}
                  <div className="flex items-center gap-3">
                    <label className="relative shrink-0 cursor-pointer">
                      <input
                        type="file"
                        accept="image/*"
                        className="hidden"
                        disabled={savingParent}
                        onChange={(e) => {
                          const file = e.target.files?.[0];
                          e.target.value = "";
                          if (file) handleParentAvatarPick(file);
                        }}
                      />
                      <div className="w-12 h-12 rounded-full bg-white border-2 border-teal/30 shadow-sm flex items-center justify-center text-teal-deep overflow-hidden">
                        {parentPortraitUrl ? <img src={parentPortraitUrl} alt="" className="w-full h-full object-cover" /> : <User size={18} />}
                      </div>
                      <span className="absolute -bottom-0.5 -right-0.5 w-5 h-5 rounded-full bg-teal text-white flex items-center justify-center border-2 border-white">
                        {savingParent ? <Loader2 size={10} className="animate-spin" /> : <Camera size={10} />}
                      </span>
                    </label>
                    <p className="text-[10px] text-muted font-bold flex-1">{t("profile.changeMyAvatarHint")}</p>
                  </div>
                  <input
                    value={parentForm.occupation}
                    onChange={(e) => setParentForm({ ...parentForm, occupation: e.target.value })}
                    placeholder={t("profile.occupationPlaceholder")}
                    className="w-full bg-white border border-line text-xs px-3 py-2 rounded-[12px] focus:outline-none"
                  />
                  <input
                    value={parentForm.workplace}
                    onChange={(e) => setParentForm({ ...parentForm, workplace: e.target.value })}
                    placeholder={t("profile.workplacePlaceholder")}
                    className="w-full bg-white border border-line text-xs px-3 py-2 rounded-[12px] focus:outline-none"
                  />
                  <input
                    value={parentForm.address}
                    onChange={(e) => setParentForm({ ...parentForm, address: e.target.value })}
                    placeholder={t("profile.addressPlaceholder")}
                    className="w-full bg-white border border-line text-xs px-3 py-2 rounded-[12px] focus:outline-none"
                  />
                  <div className="flex justify-end gap-2 pt-1">
                    <button
                      type="button"
                      onClick={() => setEditingParent(false)}
                      className="px-3 py-1.5 text-[11px] font-extrabold text-muted hover:text-ink"
                    >
                      {t("profile.cancel")}
                    </button>
                    <button
                      type="button"
                      onClick={handleSaveParentProfile}
                      disabled={savingParent}
                      className="px-3.5 py-1.5 text-[11px] font-extrabold text-white bg-teal hover:bg-teal-deep rounded-[12px] disabled:opacity-50"
                    >
                      {savingParent ? t("profile.saving") : t("profile.save")}
                    </button>
                  </div>
                </div>
              )}
            </div>
          </div>

          <div className="mt-6">
            <h4 className="text-xs font-extrabold text-coral uppercase tracking-wide flex items-center gap-1.5 mb-3">
              <Heart size={14} /> {t("profile.shareGoals")}
            </h4>
            <div className="bg-gold/5 border border-dashed border-gold/30 rounded-[16px] p-4 flex items-start gap-2.5">
              <Sparkles size={16} className="text-gold shrink-0 mt-0.5" />
              <p className="text-xs text-muted font-bold leading-relaxed">
                {t("profile.shareGoalsComingSoon")}
              </p>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
