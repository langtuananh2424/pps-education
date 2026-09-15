import React, { useEffect, useRef, useState } from "react";
import { Building2, CheckCircle2, Fingerprint, Loader2, MapPin } from "lucide-react";
import { useTranslation } from "react-i18next";
import { ApiError } from "@/lib/apiClient";
import { Badge } from "@/components/ui";
import { useDialog } from "@/components/ui/DialogProvider";
import { useApp } from "@/context/AppContext";
import { toLocaleTag } from "@/lib/i18nFormat";
import { describeGeolocationError, getCurrentPosition } from "@/lib/geolocation";
import { ATTENDANCE_CHECKED_EVENT, AttendanceRecordResponse, DetectedSiteResponse, checkIn, checkOut, detectAttendanceSite } from "../api";
import { attendanceStatusLabel, attendanceStatusVariant, formatAttendanceTime } from "../attendanceFormat";
import AttendanceSuccessModal from "./AttendanceSuccessModal";

/** Đồng bộ với badge "PPS English" ở LoginHeroPanel/auth.json — tên hiển thị chung của trung tâm. */
const COMPANY_NAME = "PPS English";

interface SelfAttendanceCardProps {
  /** Trạng thái chấm công hôm nay đã fetch sẵn ở nơi gọi (VD Header) — seed để thẻ hiện đúng
   * trạng thái (đã vào/đã ra) ngay khi mở, không đợi người dùng bấm trong phiên hiện tại mới biết. */
  todayRecord?: AttendanceRecordResponse;
  /** Gọi sau khi chấm công vào/ra thành công — dùng để nơi gọi (VD pill trạng thái ở Header) tự cập nhật theo, không cần refetch. */
  onChecked?: (record: AttendanceRecordResponse) => void;
  /** Đóng hẳn popup Chấm công cha — dùng cho nút "Quay lại" ở dialog thành công. */
  onRequestClose?: () => void;
}

/**
 * Khối "Chấm công của tôi" tự phục vụ (UC-09) — tách khỏi AttendancePage để dùng lại được cả
 * trong popup chấm công nhanh ở Header, không bắt buộc điều hướng sang trang riêng.
 *
 * Địa điểm hiển thị (V176, sửa 2026-09-14): TRƯỚC đây lấy tên điểm trường đang chọn ở pill
 * "Điểm trường" của Header để hiển thị -- hợp lý khi Chấm công còn gắn với điểm trường đang xem.
 * Từ khi tách used_for_attendance khỏi used_for_classes (Chấm công GPS giờ luôn nhắm tới địa
 * điểm được cấu hình dùng cho chấm công, VD trụ sở công ty, KHÔNG còn phụ thuộc điểm trường đang
 * chọn ở Header), hiển thị theo pill Header sai/gây hiểu lầm cho nhân viên hành chính (họ không
 * liên quan gì tới điểm trường). Nay tự dò vị trí thật qua GPS ngay khi mở thẻ (best-effort, im
 * lặng nếu lỗi/chưa cấp quyền) để hiển thị ĐÚNG địa điểm sẽ được dùng khi chấm công.
 */
export default function SelfAttendanceCard({ todayRecord, onChecked, onRequestClose }: SelfAttendanceCardProps) {
  const { t, i18n } = useTranslation("hrm-attendance");
  const { t: tc } = useTranslation("common");
  const { currentUser, currentRoleLabel } = useApp();
  const { alertDialog } = useDialog();
  const [processing, setProcessing] = useState<"in" | "out" | null>(null);
  const [lastRecord, setLastRecord] = useState<AttendanceRecordResponse | null>(todayRecord ?? null);
  const [successRecord, setSuccessRecord] = useState<{ kind: "in" | "out"; record: AttendanceRecordResponse } | null>(null);
  const [now, setNow] = useState(() => new Date());
  const [detectedSite, setDetectedSite] = useState<DetectedSiteResponse | null>(null);
  const [detecting, setDetecting] = useState(true);
  // Phân biệt 2 trường hợp khác nhau khi không có detectedSite -- "outOfRange": lấy GPS THÀNH
  // CÔNG nhưng không có địa điểm chấm công nào trong bán kính (đúng, không phải lỗi kỹ thuật);
  // "gpsError": bản thân việc lấy toạ độ GPS thất bại (quyền/timeout/không hỗ trợ). Gộp chung 1
  // câu trước đây khiến người dùng tưởng nhầm "ngoài bán kính" là "lỗi xin quyền GPS".
  const [locationIssue, setLocationIssue] = useState<"outOfRange" | "gpsError" | null>(null);
  // Chặn gọi chồng lấn khi bấm nhanh 2 lần liên tiếp — `processing` (state) chỉ cập nhật
  // disabled sau khi React re-render (có độ trễ, nhất là ở dev/HMR), nên riêng nó không đủ
  // nhanh để chặn click thứ 2 lọt qua trước khi nút kịp vô hiệu hoá; cờ ref này đồng bộ tức thời.
  const busyRef = useRef(false);

  useEffect(() => {
    if (todayRecord) setLastRecord(todayRecord);
  }, [todayRecord]);

  useEffect(() => {
    const id = setInterval(() => setNow(new Date()), 1000);
    return () => clearInterval(id);
  }, []);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      let position: GeolocationPosition;
      try {
        position = await getCurrentPosition(tc);
      } catch (err) {
        // Lỗi lấy toạ độ GPS thật sự (quyền/timeout/không hỗ trợ) -- log ra console để debug.
        // eslint-disable-next-line no-console
        console.warn("Chấm công — không lấy được toạ độ GPS lúc mở popup:", err);
        if (!cancelled) {
          setDetectedSite(null);
          setLocationIssue("gpsError");
          setDetecting(false);
        }
        return;
      }
      try {
        const detected = await detectAttendanceSite(position.coords.latitude, position.coords.longitude);
        if (!cancelled) {
          setDetectedSite(detected ?? null);
          // Lấy GPS thành công (position có giá trị) nhưng không có site nào trong bán kính --
          // đây là kết quả ĐÚNG (ngoài bán kính), không phải lỗi kỹ thuật.
          setLocationIssue(detected ? null : "outOfRange");
        }
      } catch (err) {
        // eslint-disable-next-line no-console
        console.warn("Chấm công — không tự dò được địa điểm lúc mở popup:", err);
        if (!cancelled) {
          setDetectedSite(null);
          setLocationIssue("gpsError");
        }
      } finally {
        if (!cancelled) setDetecting(false);
      }
      // eslint-disable-next-line react-hooks/exhaustive-deps
    })();
    return () => {
      cancelled = true;
    };
  }, []);

  const alreadyCheckedIn = lastRecord?.checkInAt != null;
  const alreadyCheckedOut = lastRecord?.checkOutAt != null;
  const nextAction: "in" | "out" | null = alreadyCheckedOut ? null : alreadyCheckedIn ? "out" : "in";

  const handleCheck = async (kind: "in" | "out") => {
    if (busyRef.current) return;
    busyRef.current = true;
    setProcessing(kind);
    try {
      const position = await getCurrentPosition(tc);
      // Mặc định dùng địa điểm đã tự dò lúc mở thẻ -- dò lại 1 lần nữa ngay lúc bấm để lấy toạ độ
      // mới nhất (vị trí có thể đổi giữa lúc mở thẻ và lúc bấm nút thật sự).
      let siteId = detectedSite?.siteId;
      try {
        const detected = await detectAttendanceSite(position.coords.latitude, position.coords.longitude);
        if (detected) {
          siteId = detected.siteId;
        }
      } catch {
        // Bỏ qua lỗi nhận diện tự động -- vẫn tiếp tục chấm công bằng lựa chọn thủ công.
      }
      const request = {
        method: "GPS" as const,
        siteId,
        latitude: position.coords.latitude,
        longitude: position.coords.longitude
      };
      const result = await (kind === "in" ? checkIn(request) : checkOut(request));
      setLastRecord(result);
      onChecked?.(result);
      // Đồng bộ các nơi khác đang fetch trạng thái chấm công độc lập (VD AttendanceReminderBanner).
      window.dispatchEvent(new CustomEvent(ATTENDANCE_CHECKED_EVENT));
      setSuccessRecord({ kind, record: result });
    } catch (err) {
      let message: string;
      if (err instanceof ApiError) {
        message = err.message;
      } else if (typeof err === "object" && err !== null && "code" in err && typeof (err as { code: unknown }).code === "number") {
        const geoErr = err as { code: number; message?: string };
        // eslint-disable-next-line no-console
        console.warn("Chấm công — lỗi định vị GPS:", geoErr.code, geoErr.message);
        message = describeGeolocationError(geoErr, tc);
      } else {
        // eslint-disable-next-line no-console
        console.error("Chấm công thất bại — lỗi không xác định:", err);
        message = err instanceof Error ? err.message : t("selfAttendance.checkInFailedUnknown");
      }
      // Popup cảnh báo dùng chung của app (thay window.alert) -- yêu cầu người dùng khi review UI
      // chấm công, thay cho banner đỏ inline cũ (dễ bị lẫn/không đủ nổi bật khi lỗi GPS/quyền định vị).
      void alertDialog(message);
    } finally {
      busyRef.current = false;
      setProcessing(null);
    }
  };

  return (
    <div className="max-w-sm mx-auto space-y-5">
      <div className="text-center mb-0">
        <p className="text-sm sm:text-base font-bold text-slate-900">
          {currentUser?.fullName}
          {currentRoleLabel && <span className="font-normal text-slate-400"> ({currentRoleLabel})</span>}
        </p>
        <p className="mt-2 text-[10px] font-bold uppercase tracking-wider text-slate-400 mb-2">{t("selfAttendance.currentTimeLabel")}</p>
        <p className="text-2xl sm:text-3xl font-bold font-mono text-slate-800 tabular-nums">
          {now.toLocaleTimeString(toLocaleTag(i18n.language), { hour: "2-digit", minute: "2-digit", second: "2-digit" })}
        </p>
      </div>

      <div className="flex justify-center py-1 mb-0">
        {/* Khung ngoài kích thước CỐ ĐỊNH + overflow-hidden -- vòng nháy phóng to tràn ra ngoài nút
            (biến đổi transform vẫn tính vào vùng scroll của ancestor), nếu không cắt ở đây thì Modal
            (overflow-y-auto) cứ nhấp nháy tính lại chiều cao cuộn theo từng nhịp nháy, làm thanh
            cuộn nhảy giật -- xác nhận qua ảnh chụp thực tế của người dùng. Khung phải ĐỦ LỚN để chứa
            trọn vòng nháy lúc phình to nhất + BO TRÒN (rounded-full, không phải hình vuông) -- nếu
            không vòng nháy tròn sẽ bị hình vuông cắt cụt góc/cạnh giữa chừng thay vì mờ dần tự nhiên
            hết vòng, nhìn không tự nhiên (dùng animate-attendance-pulse riêng, scale 1.4 thay vì 2x
            mặc định của animate-ping, để khung này không phải phóng quá to mới chứa vừa). */}
        <div className="relative w-44 h-44 sm:w-52 sm:h-52 rounded-full flex items-center justify-center overflow-hidden">
          {/* Nhẫn nháy nháy mời bấm -- chỉ hiện khi nút thật sự bấm được, cùng tinh thần chấm trạng
              thái "chưa chấm công" ở pill Header (animate-ping), nhưng scale nhỏ hơn -- xem index.css. */}
          {nextAction && processing === null && (
            <span className="absolute w-28 h-28 sm:w-36 sm:h-36 rounded-full bg-brand-orange animate-attendance-pulse" />
          )}
          <button
            type="button"
            disabled={processing !== null || nextAction === null}
            onClick={() => nextAction && handleCheck(nextAction)}
            aria-label={
              processing
                ? t("selfAttendance.processing")
                : nextAction === "out"
                  ? t("selfAttendance.checkOutButton")
                  : nextAction === "in"
                    ? t("selfAttendance.checkInButton")
                    : t("selfAttendance.doneToday")
            }
            className="relative w-28 h-28 sm:w-36 sm:h-36 rounded-full bg-brand-gradient shadow-glow text-white flex items-center justify-center cursor-pointer transition-transform hover:scale-[1.03] active:scale-95 disabled:opacity-50 disabled:hover:scale-100 disabled:cursor-not-allowed"
          >
            {processing ? (
              <Loader2 className="w-11 h-11 sm:w-14 sm:h-14 animate-spin" />
            ) : nextAction === null ? (
              <CheckCircle2 className="w-11 h-11 sm:w-14 sm:h-14" />
            ) : (
              <Fingerprint className="w-12 h-12 sm:w-16 sm:h-16" />
            )}
          </button>
        </div>
      </div>

      <div className="text-center space-y-0.5">
        <p className="text-sm font-bold text-slate-800 flex items-center justify-center gap-1.5">
          <Building2 className="w-4 h-4 text-brand-orange shrink-0" />
          {COMPANY_NAME}
          {detectedSite && <span className="font-normal text-slate-500"> · {detectedSite.siteName}</span>}
        </p>
        {detecting ? (
          <p className="text-xs text-slate-400 italic">{t("selfAttendance.detectingLocation")}</p>
        ) : (
          locationIssue && (
            <p className="text-xs text-slate-400 flex items-center justify-center gap-1">
              <MapPin className="w-3 h-3 shrink-0" />
              {locationIssue === "outOfRange" ? t("selfAttendance.outOfRange") : t("selfAttendance.noSiteSelected")}
            </p>
          )
        )}
      </div>

      {(alreadyCheckedIn || alreadyCheckedOut) && lastRecord && (
        <div className="flex items-center gap-3 text-xs text-slate-600 bg-slate-50 border border-slate-100 rounded-lg p-3">
          <span>
            {t("selfAttendance.checkInTimeLabel")}
            <strong>{formatAttendanceTime(lastRecord.checkInAt, i18n.language)}</strong> · {t("selfAttendance.checkOutTimeLabel")}
            <strong>{formatAttendanceTime(lastRecord.checkOutAt, i18n.language)}</strong>
          </span>
          {lastRecord.status && <Badge variant={attendanceStatusVariant[lastRecord.status]}>{attendanceStatusLabel(t, lastRecord.status)}</Badge>}
        </div>
      )}

      {successRecord && (
        <AttendanceSuccessModal
          kind={successRecord.kind}
          record={successRecord.record}
          onViewDetail={() => setSuccessRecord(null)}
          onBack={() => {
            setSuccessRecord(null);
            onRequestClose?.();
          }}
        />
      )}
    </div>
  );
}
