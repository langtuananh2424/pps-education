import React from "react";
import { CheckCircle2 } from "lucide-react";
import { useTranslation } from "react-i18next";
import { Button, Modal } from "@/components/ui";
import { AttendanceRecordResponse } from "../api";
import { formatAttendanceTime } from "../attendanceFormat";

interface AttendanceSuccessModalProps {
  kind: "in" | "out";
  record: AttendanceRecordResponse;
  /** Chỉ đóng dialog thành công, để lộ lại thẻ chấm công phía sau (đã có sẵn chi tiết giờ/badge). */
  onViewDetail: () => void;
  /** Đóng cả dialog thành công lẫn popup Chấm công cha. */
  onBack: () => void;
}

/** Dialog báo chấm công thành công (icon tick cam + giờ), tách khỏi SelfAttendanceCard để dễ đọc --
 * thay cho Toast cũ, theo mockup người dùng cung cấp khi redesign UI chấm công. Màu dùng lại đúng
 * bg-brand-gradient/shadow-glow của toàn app (không thêm màu mới) để đồng bộ với nút CHECK IN. */
export default function AttendanceSuccessModal({ kind, record, onViewDetail, onBack }: AttendanceSuccessModalProps) {
  const { t, i18n } = useTranslation("hrm-attendance");
  const time = formatAttendanceTime(kind === "in" ? record.checkInAt : record.checkOutAt, i18n.language);

  return (
    <Modal open onClose={onViewDetail} title="" size="md">
      <div className="flex flex-col items-center text-center gap-3 py-2">
        <div className="w-16 h-16 rounded-full bg-brand-gradient shadow-glow flex items-center justify-center">
          <CheckCircle2 className="w-9 h-9 text-white" />
        </div>
        <h3 className="text-lg font-bold font-display text-brand-gradient">
          {kind === "in" ? t("selfAttendance.successTitleIn") : t("selfAttendance.successTitleOut")}
        </h3>
        <p className="text-sm text-slate-500">{t("selfAttendance.successSubtitle", { time })}</p>
      </div>
      <div className="mt-4 flex gap-2.5 justify-center">
        <Button variant="secondary" onClick={onBack} className="flex-1 justify-center">
          {t("selfAttendance.backButton")}
        </Button>
        <Button variant="primary" onClick={onViewDetail} className="flex-1 justify-center">
          {t("selfAttendance.viewDetailButton")}
        </Button>
      </div>
    </Modal>
  );
}
