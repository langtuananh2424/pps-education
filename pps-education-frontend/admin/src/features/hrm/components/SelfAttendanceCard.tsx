import React, { useEffect, useRef, useState } from "react";
import { CheckCircle2, Clock3, LogIn, LogOut, MapPin } from "lucide-react";
import { ApiError } from "@/lib/apiClient";
import { Badge, Button } from "@/components/ui";
import Select from "@/components/ui/Select";
import Toast from "@/components/ui/Toast";
import { useToast } from "@/lib/useToast";
import { SiteResponse } from "@/features/facility/api";
import { AttendanceRecordResponse, checkIn, checkOut } from "../api";
import { attendanceStatusLabels, attendanceStatusVariant, formatAttendanceTime } from "../attendanceFormat";

function getCurrentPosition(): Promise<GeolocationPosition> {
  return new Promise((resolve, reject) => {
    if (!navigator.geolocation) {
      reject(new Error("Trình duyệt không hỗ trợ định vị GPS."));
      return;
    }
    navigator.geolocation.getCurrentPosition(resolve, reject, {
      enableHighAccuracy: true,
      timeout: 15000,
      maximumAge: 30000
    });
  });
}

/**
 * GeolocationPositionError.code không phải lúc nào cũng là "chưa cấp quyền" — trước đây hiện
 * chung 1 câu "vui lòng cho phép quyền định vị" cho MỌI lỗi GPS, gây hiểu lầm khi người dùng đã
 * cấp quyền trình duyệt rồi mà vẫn lỗi (thường do POSITION_UNAVAILABLE: tắt Location Services ở
 * hệ điều hành, hoặc TIMEOUT: máy không có GPS/Wi-Fi định vị được trong thời gian chờ).
 */
function describeGeolocationError(err: { code: number; message?: string }): string {
  // Dùng literal số (1/2/3) thay vì err.PERMISSION_DENIED/... — các hằng số này lúc có lúc
  // không truy cập được tuỳ trình duyệt/cách object lỗi được tạo ra, literal luôn đáng tin cậy.
  switch (err.code) {
    case 1: // PERMISSION_DENIED
      return "Trình duyệt đang CHẶN quyền định vị cho trang này — bấm vào biểu tượng khoá/vị trí trên thanh địa chỉ, chọn Vị trí = Cho phép, rồi tải lại trang và thử lại.";
    case 2: // POSITION_UNAVAILABLE
      return "Không xác định được vị trí hiện tại — kiểm tra dịch vụ định vị (Location Services) của máy/hệ điều hành đã bật chưa, rồi thử lại.";
    case 3: // TIMEOUT
      return "Định vị GPS quá thời gian chờ — thử lại ở nơi có tín hiệu GPS/Wi-Fi tốt hơn.";
    default:
      return `Không lấy được vị trí GPS${err.message ? ` (${err.message})` : ""} — vui lòng thử lại.`;
  }
}

interface SelfAttendanceCardProps {
  sites: SiteResponse[];
  /** Gọi sau khi chấm công vào/ra thành công — dùng để nơi gọi (VD pill trạng thái ở Header) tự cập nhật theo, không cần refetch. */
  onChecked?: (record: AttendanceRecordResponse) => void;
}

/** Khối "Chấm công của tôi" tự phục vụ (UC-09) — tách khỏi AttendancePage để dùng lại được cả
 * trong popup chấm công nhanh ở Header, không bắt buộc điều hướng sang trang riêng. */
export default function SelfAttendanceCard({ sites, onChecked }: SelfAttendanceCardProps) {
  const [selectedSiteId, setSelectedSiteId] = useState<number | "">("");
  const [processing, setProcessing] = useState<"in" | "out" | null>(null);
  const [selfError, setSelfError] = useState<string | null>(null);
  const [lastRecord, setLastRecord] = useState<AttendanceRecordResponse | null>(null);
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
    try {
      const position = await getCurrentPosition();
      const request = {
        method: "GPS" as const,
        siteId: selectedSiteId === "" ? undefined : selectedSiteId,
        latitude: position.coords.latitude,
        longitude: position.coords.longitude
      };
      const result = await (kind === "in" ? checkIn(request) : checkOut(request));
      setLastRecord(result);
      onChecked?.(result);
      showToast(
        kind === "in"
          ? `Chấm công vào thành công lúc ${formatAttendanceTime(result.checkInAt)}!`
          : `Chấm công ra thành công lúc ${formatAttendanceTime(result.checkOutAt)}!`
      );
    } catch (err) {
      if (err instanceof ApiError) {
        setSelfError(err.message);
      } else if (typeof err === "object" && err !== null && "code" in err && typeof (err as { code: unknown }).code === "number") {
        const geoErr = err as { code: number; message?: string };
        // eslint-disable-next-line no-console
        console.warn("Chấm công — lỗi định vị GPS:", geoErr.code, geoErr.message);
        setSelfError(describeGeolocationError(geoErr));
      } else {
        // eslint-disable-next-line no-console
        console.error("Chấm công thất bại — lỗi không xác định:", err);
        setSelfError(err instanceof Error ? err.message : "Chấm công thất bại — mở Console (F12) để xem chi tiết lỗi.");
      }
    } finally {
      busyRef.current = false;
      setProcessing(null);
    }
  };

  return (
    <div className="space-y-4">
      <div className="flex items-center gap-2">
        <Clock3 className="w-4 h-4 text-slate-400" />
        <span className="text-xs font-bold text-slate-700 font-display">Chấm công của tôi</span>
      </div>

      {selfError && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg">{selfError}</div>}

      <div className="flex flex-nowrap items-center gap-2 overflow-x-auto">
        <div className="flex items-center gap-1.5 text-xs text-slate-500 shrink-0">
          <MapPin className="w-3.5 h-3.5 text-slate-400 shrink-0" />
          <Select
            value={selectedSiteId}
            onChange={(e) => setSelectedSiteId(e.target.value === "" ? "" : Number(e.target.value))}
            className="bg-slate-50 border border-slate-200 text-xs p-2 rounded-lg focus:outline-none max-w-[160px]"
          >
            {sites.length === 0 && <option value="">Chưa có điểm trường</option>}
            {sites.map((s) => (
              <option key={s.id} value={s.id}>
                {s.name}
              </option>
            ))}
          </Select>
        </div>

        <Button variant="primary" disabled={processing !== null} onClick={() => handleCheck("in")} className="shrink-0 whitespace-nowrap">
          <LogIn className="w-3.5 h-3.5 shrink-0" />
          {processing === "in" ? "Đang xử lý..." : "Chấm công vào"}
        </Button>
        <Button variant="secondary" disabled={processing !== null} onClick={() => handleCheck("out")} className="shrink-0 whitespace-nowrap">
          <LogOut className="w-3.5 h-3.5 shrink-0" />
          {processing === "out" ? "Đang xử lý..." : "Chấm công ra"}
        </Button>
      </div>

      {lastRecord && (
        <div className="flex items-center gap-4 text-xs text-slate-600 bg-slate-50 border border-slate-100 rounded-lg p-3">
          <CheckCircle2 className="w-4 h-4 text-emerald-500 shrink-0" />
          <span>
            Giờ vào: <strong>{formatAttendanceTime(lastRecord.checkInAt)}</strong> · Giờ ra: <strong>{formatAttendanceTime(lastRecord.checkOutAt)}</strong>
          </span>
          {lastRecord.status && <Badge variant={attendanceStatusVariant[lastRecord.status]}>{attendanceStatusLabels[lastRecord.status]}</Badge>}
        </div>
      )}

      <Toast message={toastMessage} position="top-center" />
    </div>
  );
}
