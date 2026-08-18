import { useRef, useState } from "react";
import { Building2, CheckCircle2, MapPin, MapPinCheck } from "lucide-react";
import { ApiError } from "@/lib/apiClient";
import { describeGeolocationError, getCurrentPosition } from "@/lib/geolocation";
import Badge from "@/components/ui/Badge";
import Button from "@/components/ui/Button";
import { checkInStatusLabels, checkInStatusVariants, sessionStatusVariants } from "./ClassDetailPanel";
import { checkInClassSession, ClassSessionCheckInStatusResponse, ClassSessionResponse } from "../api";

const teacherTypeLabels: Record<string, string> = { VIETNAMESE: "GV Việt Nam", FOREIGN: "GV nước ngoài" };
const sessionTypeLabels: Record<string, string> = { REGULAR: "Buổi học thường", MAKEUP: "Học bù", EXAM: "Kiểm tra", SPECIAL: "Buổi đặc biệt" };

function formatCheckInTime(iso: string | null): string {
  if (!iso) return "";
  return new Date(iso).toLocaleTimeString("vi-VN", { hour: "2-digit", minute: "2-digit" });
}

interface SessionCardProps {
  session: ClassSessionResponse;
  siteName?: string;
  checkInStatus?: ClassSessionCheckInStatusResponse;
  onCheckedIn?: (status: ClassSessionCheckInStatusResponse) => void;
}

/**
 * Thẻ chi tiết 1 buổi dạy — hiện giờ giấc/lớp/phòng + trạng thái điểm danh
 * lớp và nút "Nhận Lớp" (UC-71, bổ sung ngoài SDD gốc, xác nhận 2026-08-18).
 * Tách khỏi MyTeachingSchedulePage.tsx để dùng lại được ở popup nhanh trên
 * Header (giống pattern SelfAttendanceCard của UC-09 Chấm công).
 */
export default function SessionCard({ session, siteName, checkInStatus, onCheckedIn }: SessionCardProps) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const busyRef = useRef(false);

  const handleCheckIn = async () => {
    if (busyRef.current) return;
    busyRef.current = true;
    setBusy(true);
    setError(null);
    try {
      const position = await getCurrentPosition();
      const result = await checkInClassSession(session.id, position.coords.latitude, position.coords.longitude);
      onCheckedIn?.({ classSessionId: session.id, effectiveStatus: result.status, checkInTime: result.checkInTime });
    } catch (err) {
      if (err instanceof ApiError) {
        setError(err.message);
      } else if (typeof err === "object" && err !== null && "code" in err && typeof (err as { code: unknown }).code === "number") {
        setError(describeGeolocationError(err as { code: number; message?: string }));
      } else {
        setError(err instanceof Error ? err.message : "Nhận lớp thất bại — vui lòng thử lại.");
      }
    } finally {
      busyRef.current = false;
      setBusy(false);
    }
  };

  const canCheckIn = checkInStatus?.effectiveStatus === "PENDING";

  return (
    <div className="border border-slate-150 rounded-lg p-2.5 space-y-1 text-xs">
      <div className="flex items-center justify-between gap-2 flex-wrap">
        <span className="font-mono text-[10px] text-slate-500">{session.startTime}–{session.endTime}</span>
        <div className="flex items-center gap-1.5 flex-wrap justify-end">
          <Badge variant={sessionStatusVariants[session.status] ?? "neutral"}>{session.status}</Badge>
          {checkInStatus && session.status !== "CANCELLED" && session.status !== "RESCHEDULED" && (
            <Badge variant={checkInStatusVariants[checkInStatus.effectiveStatus] ?? "neutral"}>
              {checkInStatusLabels[checkInStatus.effectiveStatus] ?? checkInStatus.effectiveStatus}
              {checkInStatus.checkInTime && ` (${formatCheckInTime(checkInStatus.checkInTime)})`}
            </Badge>
          )}
        </div>
      </div>
      <p className="font-bold text-slate-800">{session.className}</p>
      <p className="text-[11px] text-slate-500 flex items-center gap-1">
        <Building2 className="w-3 h-3 text-slate-400 shrink-0" />
        {siteName ?? "Đang tải điểm trường..."}
      </p>
      <p className="text-[11px] text-slate-500 flex items-center gap-1">
        <MapPin className="w-3 h-3 text-slate-400 shrink-0" />
        {session.roomName ?? "Chưa gán phòng"}
      </p>
      <p className="text-[11px] text-slate-400">
        GV: {session.primaryTeacherName}
        {session.teacherType && ` (${teacherTypeLabels[session.teacherType]})`} · {sessionTypeLabels[session.sessionType] ?? session.sessionType}
      </p>
      {session.status === "CANCELLED" && session.cancellationReason && (
        <p className="text-rose-500">Lý do hủy: {session.cancellationReason}</p>
      )}
      {session.lessonContent && <p className="text-slate-500">Bài học: {session.lessonContent}</p>}
      {canCheckIn && (
        <div className="pt-1">
          {error && <p className="text-rose-500 mb-1.5">{error}</p>}
          <Button variant="primary" disabled={busy} onClick={handleCheckIn} className="w-full justify-center gap-1.5 py-2">
            <MapPinCheck className="w-3.5 h-3.5 shrink-0" />
            {busy ? "Đang nhận lớp..." : "Nhận lớp"}
          </Button>
        </div>
      )}
      {checkInStatus?.effectiveStatus === "ON_TIME" || checkInStatus?.effectiveStatus === "LATE" ? (
        <p className="text-emerald-600 flex items-center gap-1 pt-0.5">
          <CheckCircle2 className="w-3.5 h-3.5 shrink-0" />
          Đã nhận lớp lúc {formatCheckInTime(checkInStatus.checkInTime)}
        </p>
      ) : null}
    </div>
  );
}
