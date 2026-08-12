import React, { useEffect, useMemo, useState } from "react";
import { CheckCircle2, Clock3, LogIn, LogOut, MapPin, Search } from "lucide-react";
import { ApiError } from "@/lib/apiClient";
import { useApp } from "@/context/AppContext";
import { Badge, Button, TableContainer, Td, Th } from "@/components/ui";
import { listSites, SiteResponse } from "@/features/facility/api";
import {
  AttendanceRecordAdminResponse,
  AttendanceRecordResponse,
  checkIn,
  checkOut,
  EmployeeResponse,
  listAttendanceRecords,
  listEmployees
} from "../api";

type AttendanceStatus = Exclude<AttendanceRecordResponse["status"], null>;

const statusVariant: Record<AttendanceStatus, "success" | "danger" | "warning" | "neutral"> = {
  NORMAL: "success",
  LATE: "warning",
  EARLY_LEAVE: "warning",
  MISSING: "danger"
};

const statusLabels: Record<AttendanceStatus, string> = {
  NORMAL: "Đúng giờ",
  LATE: "Đi muộn",
  EARLY_LEAVE: "Về sớm",
  MISSING: "Thiếu chấm công"
};

const methodLabels: Record<string, string> = {
  GPS: "GPS",
  FINGERPRINT: "Vân tay",
  FACE: "Khuôn mặt",
  MANUAL: "Thủ công"
};

function formatTime(value: string | null): string {
  if (!value) return "—";
  return new Date(value).toLocaleTimeString("vi-VN", { hour: "2-digit", minute: "2-digit" });
}

function getCurrentPosition(): Promise<GeolocationPosition> {
  return new Promise((resolve, reject) => {
    if (!navigator.geolocation) {
      reject(new Error("Trình duyệt không hỗ trợ định vị GPS."));
      return;
    }
    navigator.geolocation.getCurrentPosition(resolve, (err) => reject(err), { enableHighAccuracy: true, timeout: 10000 });
  });
}

export default function AttendancePage() {
  const { hasPermission } = useApp();
  const canViewAll = hasPermission("hrm.attendance.view-all");

  const [sites, setSites] = useState<SiteResponse[]>([]);
  const [selectedSiteId, setSelectedSiteId] = useState<number | "">("");
  const [processing, setProcessing] = useState<"in" | "out" | null>(null);
  const [selfError, setSelfError] = useState<string | null>(null);
  const [lastRecord, setLastRecord] = useState<AttendanceRecordResponse | null>(null);

  useEffect(() => {
    listSites()
      .then((res) => {
        setSites(res);
        if (res.length > 0) setSelectedSiteId(res[0].id);
      })
      .catch(() => setSites([]));
  }, []);

  const handleCheck = async (kind: "in" | "out") => {
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
    } catch (err) {
      if (err instanceof ApiError) {
        setSelfError(err.message);
      } else if (typeof err === "object" && err !== null && "code" in err && "message" in err) {
        setSelfError("Không lấy được vị trí GPS — vui lòng cho phép quyền định vị trên trình duyệt.");
      } else {
        setSelfError(err instanceof Error ? err.message : "Chấm công thất bại.");
      }
    } finally {
      setProcessing(null);
    }
  };

  return (
    <div className="space-y-6">
      <div className="border-b border-slate-200 pb-4">
        <h1 className="text-xl font-bold font-display tracking-tight text-slate-900">Phân hệ Quản lý Nhân sự & Tiền lương (HRM)</h1>
        <p className="text-xs text-slate-500 mt-1">Dữ liệu chấm công thực tế đa phương thức (vân tay, khuôn mặt, GPS).</p>
      </div>

      <div className="bg-white rounded-xl border border-slate-200 shadow-soft p-5 space-y-4">
        <div className="flex items-center gap-2">
          <Clock3 className="w-4 h-4 text-slate-400" />
          <span className="text-xs font-bold text-slate-700 font-display">Chấm công của tôi</span>
        </div>

        {selfError && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg">{selfError}</div>}

        <div className="flex flex-wrap items-center gap-3">
          <div className="flex items-center gap-1.5 text-xs text-slate-500">
            <MapPin className="w-3.5 h-3.5 text-slate-400" />
            <select
              value={selectedSiteId}
              onChange={(e) => setSelectedSiteId(e.target.value === "" ? "" : Number(e.target.value))}
              className="bg-slate-50 border border-slate-200 text-xs p-2 rounded-lg focus:outline-none"
            >
              {sites.length === 0 && <option value="">Chưa có điểm trường</option>}
              {sites.map((s) => (
                <option key={s.id} value={s.id}>
                  {s.name}
                </option>
              ))}
            </select>
          </div>

          <Button variant="primary" disabled={processing !== null} onClick={() => handleCheck("in")}>
            <LogIn className="w-3.5 h-3.5" />
            {processing === "in" ? "Đang xử lý..." : "Chấm công vào"}
          </Button>
          <Button variant="secondary" disabled={processing !== null} onClick={() => handleCheck("out")}>
            <LogOut className="w-3.5 h-3.5" />
            {processing === "out" ? "Đang xử lý..." : "Chấm công ra"}
          </Button>
        </div>

        {lastRecord && (
          <div className="flex items-center gap-4 text-xs text-slate-600 bg-slate-50 border border-slate-100 rounded-lg p-3">
            <CheckCircle2 className="w-4 h-4 text-emerald-500 shrink-0" />
            <span>
              Giờ vào: <strong>{formatTime(lastRecord.checkInAt)}</strong> · Giờ ra: <strong>{formatTime(lastRecord.checkOutAt)}</strong>
            </span>
            {lastRecord.status && <Badge variant={statusVariant[lastRecord.status]}>{statusLabels[lastRecord.status]}</Badge>}
          </div>
        )}
      </div>

      {canViewAll && <AttendanceAdminSummary sites={sites} />}
    </div>
  );
}

function AttendanceAdminSummary({ sites }: { sites: SiteResponse[] }) {
  const today = new Date().toISOString().slice(0, 10);
  const weekAgo = new Date(Date.now() - 6 * 24 * 60 * 60 * 1000).toISOString().slice(0, 10);

  const [from, setFrom] = useState(weekAgo);
  const [to, setTo] = useState(today);
  const [employeeId, setEmployeeId] = useState<number | "">("");
  const [siteId, setSiteId] = useState<number | "">("");
  const [employees, setEmployees] = useState<EmployeeResponse[]>([]);
  const [records, setRecords] = useState<AttendanceRecordAdminResponse[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    listEmployees()
      .then(setEmployees)
      .catch(() => setEmployees([]));
  }, []);

  const load = () => {
    setLoading(true);
    setError(null);
    listAttendanceRecords({
      from,
      to,
      employeeId: employeeId === "" ? undefined : employeeId,
      siteId: siteId === "" ? undefined : siteId
    })
      .then(setRecords)
      .catch((err) => {
        // Quyền có thể đổi runtime -- bỏ qua 403 âm thầm thay vì báo lỗi gây hoang mang.
        if (err instanceof ApiError && err.status === 403) {
          setRecords([]);
          return;
        }
        setError(err instanceof ApiError ? err.message : "Không tải được dữ liệu chấm công.");
      })
      .finally(() => setLoading(false));
  };

  useEffect(load, [from, to, employeeId, siteId]);

  const siteLabel = useMemo(() => new Map(sites.map((s) => [s.id, s.name])), [sites]);

  return (
    <div className="bg-white rounded-xl border border-slate-200 shadow-soft overflow-hidden">
      <div className="px-5 py-3 border-b border-slate-100 bg-slate-50 flex flex-wrap items-center justify-between gap-3">
        <div>
          <span className="text-xs font-bold text-slate-700 font-display block">Tổng hợp chấm công</span>
          <p className="text-[10px] text-slate-400">Toàn trung tâm, lọc theo nhân sự/điểm trường/khoảng ngày.</p>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <input type="date" value={from} onChange={(e) => setFrom(e.target.value)} className="bg-white border border-slate-200 text-xs p-2 rounded-lg focus:outline-none" />
          <span className="text-[10px] text-slate-400">đến</span>
          <input type="date" value={to} onChange={(e) => setTo(e.target.value)} className="bg-white border border-slate-200 text-xs p-2 rounded-lg focus:outline-none" />
          <select
            value={employeeId}
            onChange={(e) => setEmployeeId(e.target.value === "" ? "" : Number(e.target.value))}
            className="bg-white border border-slate-200 text-xs p-2 rounded-lg focus:outline-none max-w-[160px]"
          >
            <option value="">Tất cả nhân sự</option>
            {employees.map((e) => (
              <option key={e.id} value={e.id}>
                {e.fullName}
              </option>
            ))}
          </select>
          <select
            value={siteId}
            onChange={(e) => setSiteId(e.target.value === "" ? "" : Number(e.target.value))}
            className="bg-white border border-slate-200 text-xs p-2 rounded-lg focus:outline-none max-w-[160px]"
          >
            <option value="">Tất cả điểm trường</option>
            {sites.map((s) => (
              <option key={s.id} value={s.id}>
                {s.name}
              </option>
            ))}
          </select>
        </div>
      </div>

      {error && <div className="m-4 text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg">{error}</div>}

      <TableContainer className="rounded-none border-0">
        <thead>
          <tr>
            <Th>Nhân sự</Th>
            <Th>Ngày</Th>
            <Th>Giờ vào</Th>
            <Th>Giờ ra</Th>
            <Th>Phương thức</Th>
            <Th>Điểm trường</Th>
            <Th>Trạng thái</Th>
          </tr>
        </thead>
        <tbody className="divide-y divide-slate-100">
          {loading ? (
            <tr>
              <Td colSpan={7} className="text-center text-slate-400">
                Đang tải...
              </Td>
            </tr>
          ) : records.length === 0 ? (
            <tr>
              <Td colSpan={7} className="text-center text-slate-400">
                <div className="flex flex-col items-center gap-1.5 py-4">
                  <Search className="w-5 h-5 text-slate-300" />
                  Không có bản ghi chấm công trong khoảng đã chọn.
                </div>
              </Td>
            </tr>
          ) : (
            records.map((r) => (
              <tr key={r.id} className="hover:bg-slate-50/50 transition-colors">
                <Td className="font-bold text-slate-800">
                  {r.employeeFullName}
                  <div className="text-[10px] text-slate-400 font-normal">{r.employeeCode}</div>
                </Td>
                <Td>{r.workDate}</Td>
                <Td>{formatTime(r.checkInAt)}</Td>
                <Td>{formatTime(r.checkOutAt)}</Td>
                <Td>{methodLabels[r.checkInMethod ?? ""] ?? "—"}</Td>
                <Td>{r.siteName ?? siteLabel.get(r.siteId ?? -1) ?? "—"}</Td>
                <Td>
                  <Badge variant={statusVariant[r.status]}>{statusLabels[r.status]}</Badge>
                </Td>
              </tr>
            ))
          )}
        </tbody>
      </TableContainer>
    </div>
  );
}
