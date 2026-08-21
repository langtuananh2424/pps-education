import React, { useEffect, useRef, useState } from "react";
import { CheckCircle2, LogIn, LogOut, MapPin } from "lucide-react";
import { useTranslation } from "react-i18next";
import { ApiError } from "@/lib/apiClient";
import { Badge, Button } from "@/components/ui";
import Select from "@/components/ui/Select";
import Toast from "@/components/ui/Toast";
import { useToast } from "@/lib/useToast";
import { describeGeolocationError, getCurrentPosition } from "@/lib/geolocation";
import { SiteResponse } from "@/features/facility/api";
import { AttendanceRecordResponse, checkIn, checkOut, detectAttendanceSite } from "../api";
import { attendanceStatusLabel, attendanceStatusVariant, formatAttendanceTime } from "../attendanceFormat";

interface SelfAttendanceCardProps {
  sites: SiteResponse[];
  /** Gọi sau khi chấm công vào/ra thành công — dùng để nơi gọi (VD pill trạng thái ở Header) tự cập nhật theo, không cần refetch. */
  onChecked?: (record: AttendanceRecordResponse) => void;
}

/** Khối "Chấm công của tôi" tự phục vụ (UC-09) — tách khỏi AttendancePage để dùng lại được cả
 * trong popup chấm công nhanh ở Header, không bắt buộc điều hướng sang trang riêng. */
export default function SelfAttendanceCard({ sites, onChecked }: SelfAttendanceCardProps) {
  const { t, i18n } = useTranslation("hrm-attendance");
  const { t: tc } = useTranslation("common");
  const [selectedSiteId, setSelectedSiteId] = useState<number | "">("");
  const [processing, setProcessing] = useState<"in" | "out" | null>(null);
  const [selfError, setSelfError] = useState<string | null>(null);
  const [lastRecord, setLastRecord] = useState<AttendanceRecordResponse | null>(null);
  /** Tên điểm trường vừa được tự nhận diện theo GPS (null = chưa nhận diện được, dùng dropdown thủ công). */
  const [detectedSiteName, setDetectedSiteName] = useState<string | null>(null);
  // Chặn gọi chồng lấn khi bấm nhanh 2 lần liên tiếp — `processing` (state) chỉ cập nhật
  // disabled sau khi React re-render (có độ trễ, nhất là ở dev/HMR), nên riêng nó không đủ
  // nhanh để chặn click thứ 2 lọt qua trước khi nút kịp vô hiệu hoá; cờ ref này đồng bộ tức thời.
  const busyRef = useRef(false);
  const { message: toastMessage, showToast } = useToast();

  useEffect(() => {
    if (selectedSiteId === "" && sites.length > 0) {
      setSelectedSiteId(sites[0].id);
    }
  }, [sites, selectedSiteId]);

  const handleCheck = async (kind: "in" | "out") => {
    if (busyRef.current) return;
    busyRef.current = true;
    setProcessing(kind);
    setSelfError(null);
    setDetectedSiteName(null);
    try {
      const position = await getCurrentPosition(tc);
      // Tự nhận diện điểm chấm công theo GPS trước — nếu không nhận diện được (không có
      // điểm trường nào trong bán kính, hoặc lỗi gọi API) thì fallback dùng điểm đang chọn
      // ở dropdown thủ công (selectedSiteId), không chặn luồng chấm công.
      let siteId = selectedSiteId === "" ? undefined : selectedSiteId;
      try {
        const detected = await detectAttendanceSite(position.coords.latitude, position.coords.longitude);
        if (detected) {
          siteId = detected.siteId;
          setSelectedSiteId(detected.siteId);
          setDetectedSiteName(detected.siteName);
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
      showToast(
        kind === "in"
          ? t("selfAttendance.checkInSuccess", { time: formatAttendanceTime(result.checkInAt, i18n.language) })
          : t("selfAttendance.checkOutSuccess", { time: formatAttendanceTime(result.checkOutAt, i18n.language) })
      );
    } catch (err) {
      if (err instanceof ApiError) {
        setSelfError(err.message);
      } else if (typeof err === "object" && err !== null && "code" in err && typeof (err as { code: unknown }).code === "number") {
        const geoErr = err as { code: number; message?: string };
        // eslint-disable-next-line no-console
        console.warn("Chấm công — lỗi định vị GPS:", geoErr.code, geoErr.message);
        setSelfError(describeGeolocationError(geoErr, tc));
      } else {
        // eslint-disable-next-line no-console
        console.error("Chấm công thất bại — lỗi không xác định:", err);
        setSelfError(err instanceof Error ? err.message : t("selfAttendance.checkInFailedUnknown"));
      }
    } finally {
      busyRef.current = false;
      setProcessing(null);
    }
  };

  return (
    <div className="space-y-4">
      {selfError && <div className="text-sm text-rose-600 bg-rose-50 border border-rose-100 p-3 rounded-lg">{selfError}</div>}
      {detectedSiteName && !selfError && (
        <div className="text-sm text-emerald-600 bg-emerald-50 border border-emerald-100 p-3 rounded-lg">
          {t("selfAttendance.detectedSitePrefix")}<strong>{detectedSiteName}</strong>{t("selfAttendance.detectedSiteSuffix")}
        </div>
      )}

      {/* Xếp dọc từng dòng (dropdown điểm trường / nút vào / nút ra) -- yêu cầu người dùng
          2026-08-18, thay cho hàng ngang cũ bị tràn + phải cuộn ngang trong modal ở mobile.
          Cỡ chữ + nút phóng to theo yêu cầu tiếp theo cùng ngày -- className override riêng
          ở đây (không đổi size chuẩn "sm"/"md" của component Button dùng chung toàn app). */}
      <div className="flex flex-col gap-3">
        {/* Bình thường ẩn -- chấm công đã tự nhận diện điểm trường theo GPS (detectAttendanceSite ở
            handleCheck). Chỉ hiện dropdown chọn thủ công khi chấm công lỗi (selfError), để người
            dùng tự chọn đúng điểm trường rồi thử lại -- yêu cầu người dùng 2026-08-18. */}
        {selfError && (
          <div className="flex items-center gap-2 text-sm text-slate-500">
            <MapPin className="w-5 h-5 text-slate-400 shrink-0" />
            <Select
              value={selectedSiteId}
              onChange={(e) => setSelectedSiteId(e.target.value === "" ? "" : Number(e.target.value))}
              className="bg-slate-50 border border-slate-200 text-sm p-3 rounded-lg focus:outline-none w-full"
            >
              {sites.length === 0 && <option value="">{t("selfAttendance.noSites")}</option>}
              {sites.map((s) => (
                <option key={s.id} value={s.id}>
                  {s.name}
                </option>
              ))}
            </Select>
          </div>
        )}

        <Button
          variant="primary"
          disabled={processing !== null}
          onClick={() => handleCheck("in")}
          className="w-full justify-center text-base py-4 gap-2.5"
        >
          <LogIn className="w-5 h-5 shrink-0" />
          {processing === "in" ? t("selfAttendance.processing") : t("selfAttendance.checkInButton")}
        </Button>
        <Button
          variant="secondary"
          disabled={processing !== null}
          onClick={() => handleCheck("out")}
          className="w-full justify-center text-base py-4 gap-2.5"
        >
          <LogOut className="w-5 h-5 shrink-0" />
          {processing === "out" ? t("selfAttendance.processing") : t("selfAttendance.checkOutButton")}
        </Button>
      </div>

      {lastRecord && (
        <div className="flex items-center gap-4 text-sm text-slate-600 bg-slate-50 border border-slate-100 rounded-lg p-3.5">
          <CheckCircle2 className="w-5 h-5 text-emerald-500 shrink-0" />
          <span>
            {t("selfAttendance.checkInTimeLabel")}<strong>{formatAttendanceTime(lastRecord.checkInAt, i18n.language)}</strong> · {t("selfAttendance.checkOutTimeLabel")}<strong>{formatAttendanceTime(lastRecord.checkOutAt, i18n.language)}</strong>
          </span>
          {lastRecord.status && <Badge variant={attendanceStatusVariant[lastRecord.status]}>{attendanceStatusLabel(t, lastRecord.status)}</Badge>}
        </div>
      )}

      <Toast message={toastMessage} position="top-center" />
    </div>
  );
}
