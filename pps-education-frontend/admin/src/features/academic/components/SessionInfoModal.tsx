import { MapPin, User, Users } from "lucide-react";
import Modal from "@/components/ui/Modal";
import Badge from "@/components/ui/Badge";
import { dayPartLabels } from "@/features/facility/api";
import { ClassSessionResponse } from "../api";

const statusLabels: Record<string, string> = {
  SCHEDULED: "Đã xếp lịch",
  IN_PROGRESS: "Đang diễn ra",
  COMPLETED: "Đã hoàn thành",
  CANCELLED: "Đã hủy",
  RESCHEDULED: "Đã dời lịch"
};

const teacherTypeLabels: Record<string, string> = { VIETNAMESE: "GV Việt Nam", FOREIGN: "GV nước ngoài" };

const row = (label: string, value: React.ReactNode) => (
  <div className="flex justify-between gap-3 py-1.5 border-b border-slate-100 last:border-0">
    <span className="text-[11px] font-bold text-slate-500 uppercase">{label}</span>
    <span className="text-xs text-slate-800 text-right">{value}</span>
  </div>
);

interface SessionInfoModalProps {
  session: ClassSessionResponse;
  /** true nếu buổi này chỉ mới thêm trên lưới, chưa "Lưu" — hiện rõ để tránh hiểu nhầm là dữ liệu thật (bổ sung ngoài SDD gốc, xác nhận với người dùng 2026-08-21). */
  isPendingCreate?: boolean;
  onClose: () => void;
}

/** Xem nhanh thông tin 1 buổi học từ lưới thời khóa biểu (click chuột trái) — chỉ đọc, không sửa. */
export default function SessionInfoModal({ session, isPendingCreate, onClose }: SessionInfoModalProps) {
  return (
    <Modal
      open
      onClose={onClose}
      title={`${session.className}${session.sessionNumber ? ` — Buổi ${session.sessionNumber}` : ""}`}
      description={`${session.sessionDate} · ${session.dayPart ? dayPartLabels[session.dayPart] : ""}${
        session.periodNumbers.length > 0 ? ` · Tiết ${session.periodNumbers.join(", ")}` : ""
      }`}
      size="md"
    >
      <div className="space-y-3">
        {isPendingCreate && (
          <div className="text-xs text-amber-700 bg-amber-50 border border-amber-100 p-2.5 rounded-lg">
            Buổi này mới thêm trên lưới, <b>chưa lưu</b> — bấm "Lưu" ở đầu lưới để ghi thật.
          </div>
        )}

        <div className="bg-slate-50 border border-slate-200 rounded-lg p-3">
          {row("Trạng thái", <Badge variant={session.status === "CANCELLED" ? "danger" : session.status === "RESCHEDULED" ? "warning" : "success"}>{statusLabels[session.status] ?? session.status}</Badge>)}
          {session.status === "CANCELLED" && session.cancellationReason && row("Lý do hủy", session.cancellationReason)}
          {row(
            "Phòng",
            session.roomName ?? <span className="text-slate-400 italic">Chưa gán</span>
          )}
          {session.teacherType && row("Loại GV", teacherTypeLabels[session.teacherType] ?? session.teacherType)}
        </div>

        <div className="bg-slate-50 border border-slate-200 rounded-lg p-3 space-y-0">
          {row(
            "GV chính",
            <span className="flex items-center gap-1 justify-end">
              <User className="w-3 h-3 text-slate-400" />
              {session.primaryTeacherName}
            </span>
          )}
          {session.assistantTeacherName &&
            row(
              "GV phụ",
              <span className="flex items-center gap-1 justify-end">
                <Users className="w-3 h-3 text-slate-400" />
                {session.assistantTeacherName}
              </span>
            )}
          {session.cmTeacherName &&
            row(
              "CM",
              <span className="flex items-center gap-1 justify-end">
                <Users className="w-3 h-3 text-slate-400" />
                {session.cmTeacherName}
              </span>
            )}
          {session.roomName &&
            row(
              "Phòng học",
              <span className="flex items-center gap-1 justify-end">
                <MapPin className="w-3 h-3 text-slate-400" />
                {session.roomName}
              </span>
            )}
        </div>

        {session.lessonContent && (
          <div className="bg-slate-50 border border-slate-200 rounded-lg p-3">
            <p className="text-[11px] font-bold text-slate-500 uppercase mb-1">Bài học hôm nay</p>
            <p className="text-xs text-slate-700 whitespace-pre-wrap">{session.lessonContent}</p>
          </div>
        )}
      </div>
    </Modal>
  );
}
