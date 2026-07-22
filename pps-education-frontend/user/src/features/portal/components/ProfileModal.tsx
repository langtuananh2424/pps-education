import React from "react";
import { Award, GraduationCap, Heart, Lock, Phone, Sparkles, User, Users, X } from "lucide-react";

const STATUS_LABEL: Record<string, string> = {
  ACTIVE: "Đang hoạt động",
  COMPLETED: "Đã hoàn thành",
  SUSPENDED: "Tạm ngưng",
  WITHDRAWN: "Đã rút lớp"
};

interface ProfileModalProps {
  fullName: string;
  studentId: number | null;
  className: string | null;
  classCode: string | null;
  enrollmentStatus: string | null;
  onClose: () => void;
}

/**
 * Khớp layout modal "Thông tin cá nhân" trong bản thiết kế gốc (Google AI Studio),
 * nhưng chỉ điền dữ liệu thật đang có (họ tên, lớp, trạng thái ghi danh). Phần
 * "Thành tích & điểm thưởng" (EXP/xu/streak/huy hiệu) không tồn tại trong schema
 * PPS Education — không tự bịa số liệu, hiện "—"/khoá thay vì số giả. "Liên hệ
 * gia đình" cần API tra phụ huynh mà Học sinh tự xem chưa gọi được (student.parent.view,
 * tách từ student.manage ở V44, hiện chỉ dành Nhân viên/Quản lý điểm trường) — giữ
 * đúng khung 2 dòng như bản gốc, không tự đoán dữ liệu.
 */
export default function ProfileModal({ fullName, studentId, className, classCode, enrollmentStatus, onClose }: ProfileModalProps) {
  return (
    <div className="fixed inset-0 bg-ink/40 backdrop-blur-sm z-[100] flex items-center justify-center p-4" onClick={onClose}>
      <div
        className="bg-white rounded-[24px] max-w-2xl w-full max-h-[88vh] overflow-y-auto shadow-2xl"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="bg-gradient-to-r from-teal via-teal-deep to-ink h-24 relative">
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
              <div className="w-20 h-20 rounded-full bg-sky-2 border-4 border-white shadow-md flex items-center justify-center text-teal-deep">
                <GraduationCap size={30} />
              </div>
              <span className="absolute bottom-0 right-0 w-4 h-4 rounded-full bg-emerald-400 border-2 border-white" />
            </div>
            <div className="pb-1 flex items-center gap-2 flex-wrap">
              <h3 className="text-xl font-extrabold text-ink">{fullName}</h3>
              <span className="bg-teal/10 text-teal-deep border border-teal/30 px-2.5 py-0.5 rounded-full text-[10px] font-extrabold uppercase tracking-wide">
                Học sinh
              </span>
            </div>
          </div>
          <p className="text-[11px] text-muted font-bold -mt-4 mb-6 flex items-center gap-1.5">
            <User size={12} /> ID hệ thống: {studentId ?? "—"}
          </p>

          <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
            <div>
              <h4 className="text-xs font-extrabold text-teal-deep uppercase tracking-wide flex items-center gap-1.5 mb-3">
                <GraduationCap size={14} /> Thông tin học tập
              </h4>
              <div className="bg-sky-2/60 border border-line/70 rounded-[16px] p-4 space-y-2.5 text-xs">
                <div className="flex justify-between border-b border-line/60 pb-2">
                  <span className="text-muted font-bold">Lớp học:</span>
                  <span className="font-extrabold text-ink text-right">
                    {className ?? "—"}
                    {classCode ? ` (${classCode})` : ""}
                  </span>
                </div>
                <div className="flex justify-between">
                  <span className="text-muted font-bold">Trạng thái:</span>
                  <span className="font-extrabold text-teal-deep">
                    {enrollmentStatus ? STATUS_LABEL[enrollmentStatus] ?? enrollmentStatus : "—"}
                  </span>
                </div>
              </div>
            </div>

            <div>
              <h4 className="text-xs font-extrabold text-gold uppercase tracking-wide flex items-center gap-1.5 mb-3">
                <Award size={14} /> Thành tích & điểm thưởng
              </h4>
              <div className="space-y-2">
                <div className="bg-gold/5 border border-dashed border-gold/30 rounded-[16px] p-3 flex items-center justify-between">
                  <div className="flex items-center gap-2">
                    <Lock size={13} className="text-gold/70" />
                    <span className="text-[11px] font-bold text-muted">Tiến trình học tập</span>
                  </div>
                  <span className="text-[10px] font-extrabold text-gold uppercase">Sắp có</span>
                </div>
                <div className="grid grid-cols-2 gap-2">
                  <div className="bg-gold/5 border border-dashed border-gold/30 rounded-[16px] p-3 text-center">
                    <p className="text-[10px] font-bold text-muted uppercase mb-1">Ví xu thưởng</p>
                    <p className="text-base font-extrabold text-gold/60">—</p>
                  </div>
                  <div className="bg-gold/5 border border-dashed border-gold/30 rounded-[16px] p-3 text-center">
                    <p className="text-[10px] font-bold text-muted uppercase mb-1">Chuỗi học tập</p>
                    <p className="text-base font-extrabold text-gold/60">—</p>
                  </div>
                </div>
              </div>
            </div>
          </div>

          <div className="mt-6">
            <h4 className="text-xs font-extrabold text-teal-deep uppercase tracking-wide flex items-center gap-1.5 mb-3">
              <Users size={14} /> Liên hệ gia đình
            </h4>
            <div className="bg-sky-2/60 border border-dashed border-line rounded-[16px] p-4 space-y-2.5 text-xs">
              <div className="flex justify-between items-center border-b border-line/60 pb-2">
                <span className="text-muted font-bold flex items-center gap-1.5">
                  <User size={12} /> Phụ huynh:
                </span>
                <span className="font-extrabold text-muted/70">—</span>
              </div>
              <div className="flex justify-between items-center">
                <span className="text-muted font-bold flex items-center gap-1.5">
                  <Phone size={12} /> Số điện thoại:
                </span>
                <span className="font-extrabold text-muted/70">—</span>
              </div>
              <p className="text-[10px] text-gold font-bold pt-1">
                Sắp có — đang chờ Backend mở API cho Học sinh tự tra thông tin phụ huynh liên kết với chính mình.
              </p>
            </div>
          </div>

          <div className="mt-6">
            <h4 className="text-xs font-extrabold text-coral uppercase tracking-wide flex items-center gap-1.5 mb-3">
              <Heart size={14} /> Chia sẻ & mục tiêu học tập
            </h4>
            <div className="bg-gold/5 border border-dashed border-gold/30 rounded-[16px] p-4 flex items-start gap-2.5">
              <Sparkles size={16} className="text-gold shrink-0 mt-0.5" />
              <p className="text-xs text-muted font-bold leading-relaxed">
                Sắp có — tính năng chưa có trong thiết kế PPS Education hiện tại (không có cột dữ liệu tương ứng, cũng chưa
                cho phép Học sinh tự chỉnh sửa hồ sơ của mình).
              </p>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
