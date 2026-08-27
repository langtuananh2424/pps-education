import React, { useEffect, useRef, useState } from "react";
import { Building2, CheckCircle2, Fingerprint, Loader2, MapPin } from "lucide-react";
import { useTranslation } from "react-i18next";
import { ApiError } from "@/lib/apiClient";
import { Badge } from "@/components/ui";
import { useDialog } from "@/components/ui/DialogProvider";
import { useApp } from "@/context/AppContext";
import { toLocaleTag } from "@/lib/i18nFormat";
import { describeGeolocationError, getCurrentPosition } from "@/lib/geolocation";
import { SiteResponse } from "@/features/facility/api";
import { AttendanceRecordResponse, checkIn, checkOut, detectAttendanceSite } from "../api";
import { attendanceStatusLabel, attendanceStatusVariant, formatAttendanceTime } from "../attendanceFormat";
import AttendanceSuccessModal from "./AttendanceSuccessModal";

/** Đồng bộ với badge "PPS English" ở LoginHeroPanel/auth.json — tên hiển thị chung của trung tâm. */
const COMPANY_NAME = "PPS English";

interface SelfAttendanceCardProps {
  /** Điểm trường dùng để chấm công -- lấy đúng điểm trường đang chọn/khoá ở pill "Điểm trường"
   * của Header (không có dropdown chọn riêng ở đây nữa, tránh chọn lệch với dữ liệu đang xem).
   * undefined khi Header đang ở chế độ "Tất cả cơ sở" -- GPS tự nhận diện sẽ tự bù vào lúc chấm công. */
  site?: SiteResponse;
  /** Trạng thái chấm công hôm nay đã fetch sẵn ở nơi gọi (VD Header) — seed để thẻ hiện đúng
   * trạng thái (đã vào/đã ra) ngay khi mở, không đợi người dùng bấm trong phiên hiện tại mới biết. */
  todayRecord?: AttendanceRecordResponse;
  /** Gọi sau khi chấm công vào/ra thành công — dùng để nơi gọi (VD pill trạng thái ở Header) tự cập nhật theo, không cần refetch. */
  onChecked?: (record: AttendanceRecordResponse) => void;
  /** Đóng hẳn popup Chấm công cha — dùng cho nút "Quay lại" ở dialog thành công. */
  onRequestClose?: () => void;
}

/** Khối "Chấm công của tôi" tự phục vụ (UC-09) — tách khỏi AttendancePage để dùng lại được cả
 * trong popup chấm công nhanh ở Header, không bắt buộc điều hướng sang trang riêng. */
export default function SelfAttendanceCard({ site, todayRecord, onChecked, onRequestClose }: SelfAttendanceCardProps) {
  const { t, i18n } = useTranslation("hrm-attendance");
  const { t: tc } = useTranslation("common");
  const { currentUser, currentRoleLabel } = useApp();
  const { alertDialog } = useDialog();
  const [processing, setProcessing] = useState<"in" | "out" | null>(null);
  const [lastRecord, setLastRecord] = useState<AttendanceRecordResponse | null>(todayRecord ?? null);
  const [successRecord, setSuccessRecord] = useState<{ kind: "in" | "out"; record: AttendanceRecordResponse } | null>(null);
  const [now, setNow] = useState(() => new Date());
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

  const alreadyCheckedIn = lastRecord?.checkInAt != null;
  const alreadyCheckedOut = lastRecord?.checkOutAt != null;
  const nextAction: "in" | "out" | null = alreadyCheckedOut ? null : alreadyCheckedIn ? "out" : "in";

  const handleCheck = async (kind: "in" | "out") => {
    if (busyRef.current) return;
    busyRef.current = true;
    setProcessing(kind);
    try {
      const position = await getCurrentPosition(tc);
      // Mặc định dùng điểm trường đang chọn ở Header -- tự nhận diện theo GPS chỉ dùng để tự
      // động sửa lại nếu khác với vị trí thực tế lúc chấm công (không chặn luồng nếu nhận diện lỗi).
      let siteId = site?.id;
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
          {site && <span className="font-normal text-slate-500"> · {site.name}</span>}
        </p>
        {site?.address ? (
          <p className="text-xs text-slate-400 flex items-center justify-center gap-1">
            <MapPin className="w-3 h-3 shrink-0" />
            {site.address}
          </p>
        ) : (
          !site && <p className="text-xs text-slate-400 italic">{t("selfAttendance.noSiteSelected")}</p>
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
