import React, { useEffect, useMemo, useState } from "react";
import { Search } from "lucide-react";
import { useTranslation } from "react-i18next";
import { ApiError } from "@/lib/apiClient";
import { useApp } from "@/context/AppContext";
import { Badge, TableContainer, Td, Th } from "@/components/ui";
import Select from "@/components/ui/Select";
import DatePicker from "@/components/ui/DatePicker";
import Tabs from "@/components/ui/Tabs";
import { checkInStatusLabel, checkInStatusVariants } from "@/features/academic/components/ClassDetailPanel";
import { listSites, SiteResponse } from "@/features/facility/api";
import {
  AttendanceRecordAdminResponse,
  ClassSessionCheckInAdminResponse,
  EmployeeResponse,
  listAttendanceRecords,
  listClassSessionCheckIns,
  listEmployees
} from "../api";
import { attendanceMethodLabel, attendanceStatusLabel, attendanceStatusVariant, formatAttendanceTime } from "../attendanceFormat";
import SelfAttendanceCard from "../components/SelfAttendanceCard";

export default function AttendancePage() {
  const { t } = useTranslation("hrm-attendance");
  const { hasPermission } = useApp();
  const canViewAll = hasPermission("hrm.attendance.view-all");
  const [activeTab, setActiveTab] = useState<"shift" | "classSession">("shift");

  const [sites, setSites] = useState<SiteResponse[]>([]);

  useEffect(() => {
    listSites()
      .then(setSites)
      .catch(() => setSites([]));
  }, []);

  return (
    <div className="space-y-6">
      <div className="border-b border-slate-200 pb-4">
        <h1 className="text-xl font-bold font-display tracking-tight text-slate-900">{t("attendancePage.title")}</h1>
        <p className="text-xs text-slate-500 mt-1">{t("attendancePage.description")}</p>
      </div>

      {/* <div className="bg-white rounded-xl border border-slate-200 shadow-soft p-5">
        <SelfAttendanceCard sites={sites} />
      </div> */}

      {canViewAll && (
        <>
          <Tabs
            items={[
              { id: "shift", label: t("attendancePage.tabs.shift") },
              { id: "classSession", label: t("attendancePage.tabs.classSession") }
            ]}
            activeId={activeTab}
            onChange={(id) => setActiveTab(id as "shift" | "classSession")}
            className="self-start"
          />
          {activeTab === "shift" ? <AttendanceAdminSummary sites={sites} /> : <ClassSessionCheckInAdminSummary sites={sites} />}
        </>
      )}
    </div>
  );
}

function AttendanceAdminSummary({ sites }: { sites: SiteResponse[] }) {
  const { t, i18n } = useTranslation("hrm-attendance");
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
        setError(err instanceof ApiError ? err.message : t("attendancePage.loadError"));
      })
      .finally(() => setLoading(false));
  };

  useEffect(load, [from, to, employeeId, siteId]);

  const siteLabel = useMemo(() => new Map(sites.map((s) => [s.id, s.name])), [sites]);

  return (
    <div className="bg-white rounded-xl border border-slate-200 shadow-soft overflow-hidden">
      <div className="px-5 py-3 border-b border-slate-100 bg-slate-50 flex flex-wrap items-center justify-between gap-3">
        <div>
          <span className="text-xs font-bold text-slate-700 font-display block">{t("attendancePage.summaryTitle")}</span>
          <p className="text-[10px] text-slate-400">{t("attendancePage.summarySubtitle")}</p>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <div className="w-36">
            <DatePicker value={from} onChange={setFrom} max={to || undefined} />
          </div>
          <span className="text-[10px] text-slate-400">{t("attendancePage.toLabel")}</span>
          <div className="w-36">
            <DatePicker value={to} onChange={setTo} min={from || undefined} />
          </div>
          <Select
            value={employeeId}
            onChange={(e) => setEmployeeId(e.target.value === "" ? "" : Number(e.target.value))}
            className="bg-white border border-slate-200 text-xs p-2 rounded-lg focus:outline-none max-w-[160px]"
          >
            <option value="">{t("attendancePage.allEmployees")}</option>
            {employees.map((e) => (
              <option key={e.id} value={e.id}>
                {e.fullName}
              </option>
            ))}
          </Select>
          <Select
            value={siteId}
            onChange={(e) => setSiteId(e.target.value === "" ? "" : Number(e.target.value))}
            className="bg-white border border-slate-200 text-xs p-2 rounded-lg focus:outline-none max-w-[160px]"
          >
            <option value="">{t("attendancePage.allSites")}</option>
            {sites.map((s) => (
              <option key={s.id} value={s.id}>
                {s.name}
              </option>
            ))}
          </Select>
        </div>
      </div>

      {error && <div className="m-4 text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg">{error}</div>}

      <TableContainer className="rounded-none border-0">
        <thead>
          <tr>
            <Th>{t("attendancePage.columns.employee")}</Th>
            <Th>{t("attendancePage.columns.date")}</Th>
            <Th>{t("attendancePage.columns.checkIn")}</Th>
            <Th>{t("attendancePage.columns.checkOut")}</Th>
            <Th>{t("attendancePage.columns.method")}</Th>
            <Th>{t("attendancePage.columns.site")}</Th>
            <Th>{t("attendancePage.columns.status")}</Th>
          </tr>
        </thead>
        <tbody className="divide-y divide-slate-100">
          {loading ? (
            <tr>
              <Td colSpan={7} className="text-center text-slate-400">
                {t("attendancePage.loading")}
              </Td>
            </tr>
          ) : records.length === 0 ? (
            <tr>
              <Td colSpan={7} className="text-center text-slate-400">
                <div className="flex flex-col items-center gap-1.5 py-4">
                  <Search className="w-5 h-5 text-slate-300" />
                  {t("attendancePage.empty")}
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
                <Td>{formatAttendanceTime(r.checkInAt, i18n.language)}</Td>
                <Td>{formatAttendanceTime(r.checkOutAt, i18n.language)}</Td>
                <Td>{r.checkInMethod ? attendanceMethodLabel(t, r.checkInMethod) : "—"}</Td>
                <Td>{r.siteName ?? siteLabel.get(r.siteId ?? -1) ?? "—"}</Td>
                <Td>
                  <Badge variant={attendanceStatusVariant[r.status]}>{attendanceStatusLabel(t, r.status)}</Badge>
                </Td>
              </tr>
            ))
          )}
        </tbody>
      </TableContainer>
    </div>
  );
}

/**
 * UC-71 "Nhận lớp" — bổ sung ngoài SDD gốc, đã xác nhận với người dùng
 * 2026-09-11. Tab riêng biệt với "Dữ liệu chấm công ca làm việc" (UC-09,
 * AttendanceAdminSummary) — 2 luồng chấm công độc lập trong hệ thống, tránh
 * gộp chung gây nhầm lẫn 2 khái niệm khác nhau. Mirror y hệt cấu trúc
 * filter bar/bảng của AttendanceAdminSummary.
 */
function ClassSessionCheckInAdminSummary({ sites }: { sites: SiteResponse[] }) {
  const { t, i18n } = useTranslation("hrm-attendance");
  const { t: tc } = useTranslation("common");
  const today = new Date().toISOString().slice(0, 10);
  const weekAgo = new Date(Date.now() - 6 * 24 * 60 * 60 * 1000).toISOString().slice(0, 10);

  const [from, setFrom] = useState(weekAgo);
  const [to, setTo] = useState(today);
  const [employeeId, setEmployeeId] = useState<number | "">("");
  const [siteId, setSiteId] = useState<number | "">("");
  const [employees, setEmployees] = useState<EmployeeResponse[]>([]);
  const [rows, setRows] = useState<ClassSessionCheckInAdminResponse[]>([]);
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
    listClassSessionCheckIns({
      from,
      to,
      employeeId: employeeId === "" ? undefined : employeeId,
      siteId: siteId === "" ? undefined : siteId
    })
      .then(setRows)
      .catch((err) => {
        if (err instanceof ApiError && err.status === 403) {
          setRows([]);
          return;
        }
        setError(err instanceof ApiError ? err.message : t("attendancePage.loadError"));
      })
      .finally(() => setLoading(false));
  };

  useEffect(load, [from, to, employeeId, siteId]);

  return (
    <div className="bg-white rounded-xl border border-slate-200 shadow-soft overflow-hidden">
      <div className="px-5 py-3 border-b border-slate-100 bg-slate-50 flex flex-wrap items-center justify-between gap-3">
        <div>
          <span className="text-xs font-bold text-slate-700 font-display block">{t("attendancePage.tabs.classSession")}</span>
          <p className="text-[10px] text-slate-400">{t("attendancePage.summarySubtitle")}</p>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <div className="w-36">
            <DatePicker value={from} onChange={setFrom} max={to || undefined} />
          </div>
          <span className="text-[10px] text-slate-400">{t("attendancePage.toLabel")}</span>
          <div className="w-36">
            <DatePicker value={to} onChange={setTo} min={from || undefined} />
          </div>
          <Select
            value={employeeId}
            onChange={(e) => setEmployeeId(e.target.value === "" ? "" : Number(e.target.value))}
            className="bg-white border border-slate-200 text-xs p-2 rounded-lg focus:outline-none max-w-[160px]"
          >
            <option value="">{t("attendancePage.allEmployees")}</option>
            {employees.map((e) => (
              <option key={e.id} value={e.id}>
                {e.fullName}
              </option>
            ))}
          </Select>
          <Select
            value={siteId}
            onChange={(e) => setSiteId(e.target.value === "" ? "" : Number(e.target.value))}
            className="bg-white border border-slate-200 text-xs p-2 rounded-lg focus:outline-none max-w-[160px]"
          >
            <option value="">{t("attendancePage.allSites")}</option>
            {sites.map((s) => (
              <option key={s.id} value={s.id}>
                {s.name}
              </option>
            ))}
          </Select>
        </div>
      </div>

      {error && <div className="m-4 text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg">{error}</div>}

      <TableContainer className="rounded-none border-0">
        <thead>
          <tr>
            <Th>{t("attendancePage.columns.employee")}</Th>
            <Th>{t("attendancePage.columns.date")}</Th>
            <Th>{t("attendancePage.classSessionColumns.session")}</Th>
            <Th>{t("attendancePage.columns.site")}</Th>
            <Th>{t("attendancePage.columns.status")}</Th>
          </tr>
        </thead>
        <tbody className="divide-y divide-slate-100">
          {loading ? (
            <tr>
              <Td colSpan={5} className="text-center text-slate-400">
                {t("attendancePage.loading")}
              </Td>
            </tr>
          ) : rows.length === 0 ? (
            <tr>
              <Td colSpan={5} className="text-center text-slate-400">
                <div className="flex flex-col items-center gap-1.5 py-4">
                  <Search className="w-5 h-5 text-slate-300" />
                  {t("attendancePage.empty")}
                </div>
              </Td>
            </tr>
          ) : (
            rows.map((r) => (
              <tr key={r.classSessionId} className="hover:bg-slate-50/50 transition-colors">
                <Td className="font-bold text-slate-800">
                  {r.teacherFullName}
                  <div className="text-[10px] text-slate-400 font-normal">{r.teacherCode}</div>
                </Td>
                <Td>{r.sessionDate}</Td>
                <Td>
                  {r.className}
                  <div className="text-[10px] text-slate-400 font-normal">
                    {r.startTime}–{r.endTime}
                    {r.checkInTime && ` · ${formatAttendanceTime(r.checkInTime, i18n.language)}`}
                  </div>
                </Td>
                <Td>{r.siteName}</Td>
                <Td>
                  <Badge variant={checkInStatusVariants[r.effectiveStatus] ?? "neutral"}>{checkInStatusLabel(tc, r.effectiveStatus)}</Badge>
                </Td>
              </tr>
            ))
          )}
        </tbody>
      </TableContainer>
    </div>
  );
}
