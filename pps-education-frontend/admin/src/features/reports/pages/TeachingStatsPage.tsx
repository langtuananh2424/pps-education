import React, { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { BarChart3, Download, Info } from "lucide-react";
import { useApp } from "@/context/AppContext";
import { ApiError } from "@/lib/apiClient";
import { toISODate } from "@/lib/calendarDates";
import { downloadBlob } from "@/lib/xlsxTemplate";
import { useToast } from "@/lib/useToast";
import Toast from "@/components/ui/Toast";
import Card from "@/components/ui/Card";
import Button from "@/components/ui/Button";
import DatePicker from "@/components/ui/DatePicker";
import TableContainer, { Th, Td } from "@/components/ui/TableContainer";
import EmptyState from "@/components/ui/EmptyState";
import { cn } from "@/lib/cn";
import {
  TeacherTeachingStatsRow,
  TeachingStatsResponse,
  exportTeachingStats,
  getTeachingStats
} from "@/features/academic/oversightApi";
import FloatingError from "@/components/ui/FloatingError";

function firstDayOfMonthIso(): string {
  const now = new Date();
  return toISODate(new Date(now.getFullYear(), now.getMonth(), 1));
}

/** Tô màu tỷ lệ đúng giờ để Trưởng phòng đào tạo nhận ra ngay giáo viên cần nhắc nhở. */
function rateClass(rate: number | null): string {
  if (rate == null) return "text-slate-400";
  if (rate >= 90) return "text-emerald-600";
  if (rate >= 70) return "text-amber-600";
  return "text-rose-600";
}

/**
 * "Thống kê giảng dạy theo giáo viên" (V203 — bổ sung ngoài SDD gốc, xác nhận với người dùng 2026-09-30):
 * số lớp, buổi đã xếp/đã diễn ra/bị huỷ, số tiết đã dạy, nhận lớp đúng giờ/trễ/không nhận lớp của từng
 * giáo viên trong khoảng ngày chọn; lọc theo điểm trường trên Header, xuất Excel.
 */
export default function TeachingStatsPage() {
  const { t } = useTranslation("academic-oversight");
  const { selectedCampusId } = useApp();
  const siteId = selectedCampusId !== "ALL" ? Number(selectedCampusId) : undefined;

  const [fromDate, setFromDate] = useState<string>(firstDayOfMonthIso());
  const [toDate, setToDate] = useState<string>(toISODate(new Date()));
  const [stats, setStats] = useState<TeachingStatsResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [exporting, setExporting] = useState(false);
  const { message: toastMsg, showToast } = useToast();

  useEffect(() => {
    if (!fromDate || !toDate) return;
    let cancelled = false;
    setLoading(true);
    setError(null);
    getTeachingStats({ siteId, fromDate, toDate })
      .then((res) => {
        if (!cancelled) setStats(res);
      })
      .catch((err) => {
        if (cancelled) return;
        setStats(null);
        setError(err instanceof ApiError ? err.message : t("teachingStats.loadFailed"));
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [siteId, fromDate, toDate, t]);

  const handleExport = async () => {
    setExporting(true);
    try {
      const blob = await exportTeachingStats({ siteId, fromDate, toDate });
      downloadBlob(blob, `giang-day-theo-giao-vien-${fromDate}-${toDate}.xlsx`);
      showToast(t("teachingStats.exportSuccess"));
    } catch (err) {
      showToast(err instanceof ApiError ? err.message : t("teachingStats.exportFailed"));
    } finally {
      setExporting(false);
    }
  };

  const renderRow = (r: TeacherTeachingStatsRow, isTotal = false) => (
    <tr key={r.teacherUserId ?? "total"} className={isTotal ? "bg-slate-50 font-bold" : "hover:bg-slate-50/50"}>
      <Td>
        <p className={cn("text-slate-800", !isTotal && "font-semibold")}>{isTotal ? t("teachingStats.totals") : r.teacherName}</p>
        {!isTotal && r.employeeCode && <p className="text-[13px] text-slate-400">{r.employeeCode}</p>}
      </Td>
      <Td className="text-right">{r.classCount}</Td>
      <Td className="text-right">{r.scheduledSessions}</Td>
      <Td className="text-right">{r.heldSessions}</Td>
      <Td className="text-right">{r.cancelledSessions}</Td>
      <Td className="text-right">{r.taughtPeriods}</Td>
      <Td className="text-right text-emerald-600">{r.onTimeCheckIns}</Td>
      <Td className={cn("text-right", r.lateCheckIns > 0 && "text-amber-600")}>{r.lateCheckIns}</Td>
      <Td className={cn("text-right", r.missingCheckIns > 0 && "text-rose-600")}>{r.missingCheckIns}</Td>
      <Td className={cn("text-right font-semibold", rateClass(r.onTimeRate))}>
        {r.onTimeRate == null ? "—" : `${r.onTimeRate}%`}
      </Td>
      <Td className="text-right text-emerald-600">{r.reportOnTimeCount ?? "—"}</Td>
      <Td className={cn("text-right", (r.reportLateCount ?? 0) > 0 && "text-amber-600")}>{r.reportLateCount ?? "—"}</Td>
      <Td className={cn("text-right", (r.reportMissingCount ?? 0) > 0 && "text-rose-600")}>{r.reportMissingCount ?? "—"}</Td>
    </tr>
  );

  return (
    <div className="space-y-6">
      <div className="border-b border-slate-200 pb-4 flex items-start justify-between gap-3 flex-wrap">
        <div>
          <h1 className="text-xl font-bold font-display tracking-tight text-slate-900">{t("teachingStats.title")}</h1>
          <p className="text-sm text-slate-500 mt-1">{t("teachingStats.description")}</p>
        </div>
        <Button variant="primary" onClick={handleExport} disabled={exporting || loading || !stats || stats.teachers.length === 0}>
          <Download className="w-3.5 h-3.5" /> {exporting ? t("teachingStats.exporting") : t("teachingStats.export")}
        </Button>
      </div>

      <Card>
        <div className="flex flex-wrap items-end gap-3">
          <div className="w-[200px]">
            <label className="block text-sm text-slate-500 mb-1">{t("teachingStats.fromDate")}</label>
            <DatePicker value={fromDate} onChange={setFromDate} max={toDate || undefined} />
          </div>
          <div className="w-[200px]">
            <label className="block text-sm text-slate-500 mb-1">{t("teachingStats.toDate")}</label>
            <DatePicker value={toDate} onChange={setToDate} min={fromDate || undefined} />
          </div>
          <p className="text-sm text-slate-500 pb-2">{stats?.siteName ?? t("teachingStats.allSites")}</p>
        </div>
      </Card>

      <FloatingError message={error} onClose={() => setError(null)} />

      <Card padded={false} className="overflow-hidden">
        {loading && !stats ? (
          <div className="py-16 text-center text-slate-300"><BarChart3 className="w-12 h-12 mx-auto animate-pulse" /></div>
        ) : !stats || stats.teachers.length === 0 ? (
          <EmptyState icon={BarChart3} title={t("teachingStats.empty")} />
        ) : (
          <TableContainer className={cn("rounded-none border-0", loading && "opacity-60")}>
            <thead>
              <tr>
                <Th>{t("teachingStats.columns.teacher")}</Th>
                <Th className="text-right">{t("teachingStats.columns.classes")}</Th>
                <Th className="text-right">{t("teachingStats.columns.scheduled")}</Th>
                <Th className="text-right">{t("teachingStats.columns.held")}</Th>
                <Th className="text-right">{t("teachingStats.columns.cancelled")}</Th>
                <Th className="text-right">{t("teachingStats.columns.periods")}</Th>
                <Th className="text-right">{t("teachingStats.columns.onTime")}</Th>
                <Th className="text-right">{t("teachingStats.columns.late")}</Th>
                <Th className="text-right">{t("teachingStats.columns.missing")}</Th>
                <Th className="text-right">{t("teachingStats.columns.onTimeRate")}</Th>
                <Th className="text-right">{t("teachingStatsReport.reportOnTime")}</Th>
                <Th className="text-right">{t("teachingStatsReport.reportLate")}</Th>
                <Th className="text-right">{t("teachingStatsReport.reportMissing")}</Th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {stats.teachers.map((r) => renderRow(r))}
              {renderRow(stats.totals, true)}
            </tbody>
          </TableContainer>
        )}
      </Card>

      <div className="text-[13px] text-slate-500 space-y-1">
        {(["held", "periods", "checkIn"] as const).map((key) => (
          <p key={key} className="flex items-start gap-1.5">
            <Info className="w-3 h-3 mt-0.5 shrink-0" /> {t(`teachingStats.notes.${key}`)}
          </p>
        ))}
      </div>

      <Toast message={toastMsg} />
    </div>
  );
}
