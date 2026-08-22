import { useRef, useState } from "react";
import { Building2, CheckCircle2, MapPin, MapPinCheck } from "lucide-react";
import { useTranslation } from "react-i18next";
import { ApiError } from "@/lib/apiClient";
import { describeGeolocationError, getCurrentPosition } from "@/lib/geolocation";
import { formatTimeHm } from "@/lib/i18nFormat";
import Badge from "@/components/ui/Badge";
import Button from "@/components/ui/Button";
import { checkInStatusLabel, checkInStatusVariants, sessionStatusVariants, teacherTypeLabel } from "./ClassDetailPanel";
import { checkInClassSession, ClassSessionCheckInStatusResponse, ClassSessionResponse } from "../api";

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
  const { t, i18n } = useTranslation("academic-classes");
  const { t: tc } = useTranslation("common");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const busyRef = useRef(false);

  const handleCheckIn = async () => {
    if (busyRef.current) return;
    busyRef.current = true;
    setBusy(true);
    setError(null);
    try {
      const position = await getCurrentPosition(tc);
      const result = await checkInClassSession(session.id, position.coords.latitude, position.coords.longitude);
      onCheckedIn?.({ classSessionId: session.id, effectiveStatus: result.status, checkInTime: result.checkInTime });
    } catch (err) {
      if (err instanceof ApiError) {
        setError(err.message);
      } else if (typeof err === "object" && err !== null && "code" in err && typeof (err as { code: unknown }).code === "number") {
        setError(describeGeolocationError(err as { code: number; message?: string }, tc));
      } else {
        setError(err instanceof Error ? err.message : t("sessionCard.checkInFailedGeneric"));
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
              {checkInStatusLabel(tc, checkInStatus.effectiveStatus)}
              {checkInStatus.checkInTime && ` (${formatTimeHm(checkInStatus.checkInTime, i18n.language)})`}
            </Badge>
          )}
        </div>
      </div>
      <p className="font-bold text-slate-800">{session.className}</p>
      <p className="text-[11px] text-slate-500 flex items-center gap-1">
        <Building2 className="w-3 h-3 text-slate-400 shrink-0" />
        {siteName ?? t("myTeachingSchedule.loadingSite")}
      </p>
      <p className="text-[11px] text-slate-500 flex items-center gap-1">
        <MapPin className="w-3 h-3 text-slate-400 shrink-0" />
        {session.roomName ?? t("myTeachingSchedule.unassignedRoom")}
      </p>
      <p className="text-[11px] text-slate-400">
        {t("myTeachingSchedule.teacherLine", {
          teacher: session.primaryTeacherName,
          type: session.teacherType ? ` (${teacherTypeLabel(t, session.teacherType)})` : "",
          sessionType: t(`enums.sessionType.${session.sessionType}`, session.sessionType)
        })}
      </p>
      {(session.assistantTeacherName || session.cmTeacherName) && (
        <p className="text-[11px] text-slate-400">
          {session.assistantTeacherName && <>GV phụ: {session.assistantTeacherName}</>}
          {session.assistantTeacherName && session.cmTeacherName && " · "}
          {session.cmTeacherName && <>CM: {session.cmTeacherName}</>}
        </p>
      )}
      {session.status === "CANCELLED" && session.cancellationReason && (
        <p className="text-rose-500">{t("myTeachingSchedule.cancellationReason", { reason: session.cancellationReason })}</p>
      )}
      {session.lessonContent && <p className="text-slate-500">{t("myTeachingSchedule.lessonContent", { content: session.lessonContent })}</p>}
      {canCheckIn && (
        <div className="pt-1">
          {error && <p className="text-rose-500 mb-1.5">{error}</p>}
          <Button variant="primary" disabled={busy} onClick={handleCheckIn} className="w-full justify-center gap-1.5 py-2">
            <MapPinCheck className="w-3.5 h-3.5 shrink-0" />
            {busy ? t("sessionCard.checkingIn") : t("sessionCard.checkInButton")}
          </Button>
        </div>
      )}
      {checkInStatus?.effectiveStatus === "ON_TIME" || checkInStatus?.effectiveStatus === "LATE" ? (
        <p className="text-emerald-600 flex items-center gap-1 pt-0.5">
          <CheckCircle2 className="w-3.5 h-3.5 shrink-0" />
          {t("sessionCard.checkedInAt", { time: formatTimeHm(checkInStatus.checkInTime, i18n.language) })}
        </p>
      ) : null}
    </div>
  );
}
