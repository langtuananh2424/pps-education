import React, { useEffect, useState } from "react";
import { Download, Search } from "lucide-react";
import { useTranslation } from "react-i18next";
import { ApiError } from "@/lib/apiClient";
import { downloadBlob } from "@/lib/xlsxTemplate";
import { Td, Th } from "@/components/ui";
import Select from "@/components/ui/Select";
import Button from "@/components/ui/Button";
import FloatingError from "@/components/ui/FloatingError";
import type { SiteResponse } from "@/features/facility/api";
import {
  AttendanceTimesheetResponse,
  DepartmentResponse,
  exportAttendanceTimesheet,
  getAttendanceTimesheet,
  listDepartments,
  TimesheetCell
} from "../api";

const CODE_STYLE: Record<TimesheetCell["code"], string> = {
  X: "bg-emerald-50 text-emerald-700",
  M: "bg-amber-50 text-amber-700",
  S: "bg-amber-50 text-amber-700",
  V: "bg-red-50 text-red-700",
  P: "bg-sky-50 text-sky-700",
  KP: "bg-slate-100 text-slate-600"
};

/** Bảng chấm công tháng tự động + xuất Excel (2026-10-16) — dữ liệu từ GET /api/attendance/timesheet. */
export default function AttendanceTimesheetTab({ sites }: { sites: SiteResponse[] }) {
  const { t } = useTranslation("hrm-attendance");
  const now = new Date();
  const [month, setMonth] = useState(`${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, "0")}`);
  const [siteId, setSiteId] = useState<number | "">("");
  const [departmentId, setDepartmentId] = useState<number | "">("");
  const [departments, setDepartments] = useState<DepartmentResponse[]>([]);
  const [data, setData] = useState<AttendanceTimesheetResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [exporting, setExporting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    listDepartments()
      .then(setDepartments)
      .catch(() => setDepartments([]));
  }, []);

  const params = () => {
    const [y, m] = month.split("-").map(Number);
    return {
      year: y,
      month: m,
      siteId: siteId === "" ? undefined : siteId,
      departmentId: departmentId === "" ? undefined : departmentId
    };
  };

  useEffect(() => {
    if (!month) return;
    setLoading(true);
    setError(null);
    getAttendanceTimesheet(params())
      .then(setData)
      .catch((err) => {
        if (err instanceof ApiError && err.status === 403) {
          setData(null);
          return;
        }
        setError(err instanceof ApiError ? err.message : t("attendancePage.loadError"));
      })
      .finally(() => setLoading(false));
  }, [month, siteId, departmentId]);

  const handleExport = async () => {
    setExporting(true);
    try {
      const blob = await exportAttendanceTimesheet(params());
      downloadBlob(blob, `bang-cham-cong-${month}.xlsx`);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t("attendancePage.timesheet.exportError"));
    } finally {
      setExporting(false);
    }
  };

  const daysInMonth = data ? new Date(data.toDate).getDate() : 0;
  const days = Array.from({ length: daysInMonth }, (_, i) => i + 1);

  return (
    <div className="bg-white rounded-xl border border-slate-200 shadow-soft overflow-hidden">
      <div className="px-5 py-3 border-b border-slate-100 bg-slate-50 flex flex-wrap items-center justify-between gap-3">
        <div>
          <span className="text-sm font-bold text-slate-700 font-display block">{t("attendancePage.timesheet.title")}</span>
          <p className="text-[12px] text-slate-400">{t("attendancePage.timesheet.subtitle")}</p>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <input
            type="month"
            value={month}
            onChange={(e) => setMonth(e.target.value)}
            aria-label={t("attendancePage.timesheet.month")}
            className="bg-white border border-slate-200 text-sm p-2 rounded-lg focus:outline-none"
          />
          <Select
            value={departmentId}
            onChange={(e) => setDepartmentId(e.target.value === "" ? "" : Number(e.target.value))}
            className="bg-white border border-slate-200 text-sm p-2 rounded-lg focus:outline-none max-w-[160px]"
          >
            <option value="">{t("attendancePage.timesheet.allDepartments")}</option>
            {departments.map((d) => (
              <option key={d.id} value={d.id}>
                {d.name}
              </option>
            ))}
          </Select>
          <Select
            value={siteId}
            onChange={(e) => setSiteId(e.target.value === "" ? "" : Number(e.target.value))}
            className="bg-white border border-slate-200 text-sm p-2 rounded-lg focus:outline-none max-w-[160px]"
          >
            <option value="">{t("attendancePage.allSites")}</option>
            {sites.map((s) => (
              <option key={s.id} value={s.id}>
                {s.name}
              </option>
            ))}
          </Select>
          <Button variant="primary" size="sm" onClick={handleExport} disabled={exporting || !month}>
            <Download className="w-3.5 h-3.5" />
            {exporting ? t("attendancePage.timesheet.exporting") : t("attendancePage.timesheet.exportButton")}
          </Button>
        </div>
      </div>

      <FloatingError message={error} onClose={() => setError(null)} />

      <p className="px-5 py-2 text-[12px] text-slate-500 border-b border-slate-100">{t("attendancePage.timesheet.legend")}</p>

      <div className="overflow-x-auto">
        <table className="min-w-full text-sm">
          <thead>
            <tr>
              <Th className="sticky left-0 bg-white z-10">{t("attendancePage.timesheet.columns.employee")}</Th>
              {days.map((d) => (
                <Th key={d} className="text-center px-1">
                  {d}
                </Th>
              ))}
              <Th className="text-center">{t("attendancePage.timesheet.columns.workDays")}</Th>
              <Th className="text-center">{t("attendancePage.timesheet.columns.late")}</Th>
              <Th className="text-center">{t("attendancePage.timesheet.columns.earlyLeave")}</Th>
              <Th className="text-center">{t("attendancePage.timesheet.columns.missing")}</Th>
              <Th className="text-center">{t("attendancePage.timesheet.columns.paidLeave")}</Th>
              <Th className="text-center">{t("attendancePage.timesheet.columns.unpaidLeave")}</Th>
              <Th className="text-center">{t("attendancePage.timesheet.columns.hours")}</Th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {loading ? (
              <tr>
                <Td colSpan={days.length + 8} className="text-center text-slate-400">
                  {t("attendancePage.loading")}
                </Td>
              </tr>
            ) : !data || data.rows.length === 0 ? (
              <tr>
                <Td colSpan={days.length + 8} className="text-center text-slate-400">
                  <div className="flex flex-col items-center gap-1.5 py-4">
                    <Search className="w-5 h-5 text-slate-300" />
                    {t("attendancePage.timesheet.empty")}
                  </div>
                </Td>
              </tr>
            ) : (
              data.rows.map((row) => {
                const byDay = new Map(row.cells.map((c) => [Number(c.date.slice(8, 10)), c]));
                const tot = row.totals;
                return (
                  <tr key={row.employeeId} className="hover:bg-slate-50/50 transition-colors">
                    <Td className="font-bold text-slate-800 whitespace-nowrap sticky left-0 bg-white">
                      {row.employeeFullName}
                      <div className="text-[13px] text-slate-400 font-normal">{row.employeeCode}</div>
                    </Td>
                    {days.map((d) => {
                      const cell = byDay.get(d);
                      return (
                        <td key={d} className="px-0.5 py-1 text-center">
                          {cell && (
                            <span
                              title={cell.hours != null ? `${cell.hours}h` : undefined}
                              className={`inline-block min-w-[1.75rem] rounded px-1 text-[12px] font-bold ${CODE_STYLE[cell.code]}`}
                            >
                              {cell.code}
                            </span>
                          )}
                        </td>
                      );
                    })}
                    <Td className="text-center font-bold">{tot.workDays}</Td>
                    <Td className="text-center">{tot.lateDays}</Td>
                    <Td className="text-center">{tot.earlyLeaveDays}</Td>
                    <Td className="text-center">{tot.missingDays}</Td>
                    <Td className="text-center">{tot.paidLeaveDays}</Td>
                    <Td className="text-center">{tot.unpaidLeaveDays}</Td>
                    <Td className="text-center">{tot.totalHours}</Td>
                  </tr>
                );
              })
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
}
