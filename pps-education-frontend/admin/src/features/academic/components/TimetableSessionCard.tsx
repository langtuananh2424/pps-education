import type { CSSProperties, MouseEvent } from "react";
import { MapPin, Users } from "lucide-react";
import { cn } from "@/lib/cn";
import { ClassSessionResponse } from "../api";

const teacherTypeLabels: Record<string, string> = { VIETNAMESE: "GV VN", FOREIGN: "GV NN" };

export type SessionPendingKind = "create" | "update" | "cancel";

interface TimetableSessionCardProps {
  session: ClassSessionResponse;
  style: CSSProperties;
  onClick: () => void;
  onContextMenu?: (e: MouseEvent) => void;
  /** Buổi đang nằm trong hàng chờ "chưa lưu" của lưới — viền đứt nét + nhãn riêng (bổ sung ngoài SDD gốc, xác nhận với người dùng 2026-08-21). */
  pendingKind?: SessionPendingKind;
}

/** 1 thẻ trên lưới thời khóa biểu — kéo dài đúng số tiết qua `style.gridRow` do TimetablePage tính sẵn. */
export default function TimetableSessionCard({ session, style, onClick, onContextMenu, pendingKind }: TimetableSessionCardProps) {
  const cancelled = session.status === "CANCELLED";
  const rescheduled = session.status === "RESCHEDULED";

  return (
    <button
      type="button"
      onClick={onClick}
      onContextMenu={onContextMenu}
      style={style}
      className={cn(
        "text-left rounded-lg border p-1.5 flex flex-col gap-0.5 overflow-hidden transition-all hover:shadow-md hover:z-10 pointer-events-auto",
        pendingKind && "border-dashed",
        cancelled
          ? "bg-rose-50 border-rose-300"
          : rescheduled
          ? "bg-slate-100 border-slate-300 opacity-60"
          : session.teacherType === "FOREIGN"
          ? "bg-sky-50 border-sky-200 hover:border-sky-400"
          : "bg-orange-50 border-orange-200 hover:border-brand-red/50"
      )}
    >
      {pendingKind && (
        <span className="text-[8px] font-bold uppercase px-1 py-0.5 rounded w-fit bg-purple-100 text-purple-700">
          {pendingKind === "create" ? "Mới · chưa lưu" : pendingKind === "cancel" ? "Hủy · chưa lưu" : "Sửa · chưa lưu"}
        </span>
      )}
      <p className={cn("text-[10px] font-bold truncate", cancelled ? "text-rose-600 line-through" : rescheduled ? "text-slate-500 line-through" : "text-slate-800")}>
        {session.className}
      </p>
      {!cancelled && (
        <>
          <p className="text-[9px] text-slate-500 truncate">{session.primaryTeacherName}</p>
          {session.cmTeacherName && (
            <p className="text-[9px] text-slate-400 truncate flex items-center gap-0.5">
              <Users className="w-2.5 h-2.5 shrink-0" />
              CM: {session.cmTeacherName}
            </p>
          )}
          {session.roomName && (
            <p className="text-[9px] text-slate-400 truncate flex items-center gap-0.5">
              <MapPin className="w-2.5 h-2.5 shrink-0" />
              {session.roomName}
            </p>
          )}
          {session.teacherType && (
            <span
              className={cn(
                "text-[8px] font-bold px-1 py-0.5 rounded w-fit",
                session.teacherType === "FOREIGN" ? "bg-sky-100 text-sky-700" : "bg-orange-100 text-brand-red"
              )}
            >
              {teacherTypeLabels[session.teacherType]}
            </span>
          )}
        </>
      )}
      {cancelled && <p className="text-[9px] text-rose-500 font-bold">Đã hủy{session.cancellationReason ? `: ${session.cancellationReason}` : ""}</p>}
      {rescheduled && <p className="text-[9px] text-amber-600 font-bold">Đã dời lịch</p>}
    </button>
  );
}
