import React, { useEffect, useState } from "react";
import { Scale, TrendingDown, TrendingUp, Wallet } from "lucide-react";
import { useTranslation } from "react-i18next";
import { ApiError } from "@/lib/apiClient";
import { useApp } from "@/context/AppContext";
import { FinancialReportResponse, getChainReport, getMySiteReports } from "../api";
import { formatVnd, labelClass, monthRange } from "../format";
import { useMonthlyFinanceTrend } from "../hooks/useMonthlyFinanceTrend";
import FinanceTrendChart from "../components/FinanceTrendChart";
import Button from "@/components/ui/Button";
import Card from "@/components/ui/Card";
import DatePicker from "@/components/ui/DatePicker";
import FloatingError from "@/components/ui/FloatingError";
import StatCard from "@/components/ui/StatCard";
import TableContainer, { Td, Th } from "@/components/ui/TableContainer";

interface ReportTotals {
  revenue: number;
  expense: number;
  outstanding: number;
  /** Chi dùng chung nhiều điểm trường (không gắn site) — chỉ có ở báo cáo toàn chuỗi. */
  sharedExpense: number;
}

/**
 * UC-32: Báo cáo Thu/Chi/Công nợ. Có quyền finance.report.view (Ban giám đốc, Kế toán): tổng hợp toàn chuỗi
 * kèm chi tiết từng điểm trường; ngược lại chỉ các điểm trường người dùng phụ trách (Quản lý điểm trường).
 * Thu = khoản đã thu trong kỳ; Chi = khoản chi trong kỳ trừ khoản bị từ chối; Công nợ = phần còn nợ của hóa
 * đơn phát hành trong kỳ.
 */
export default function ReportsPage() {
  const { t } = useTranslation("finance");
  const { hasPermission } = useApp();
  const isChainViewer = hasPermission("finance.report.view");

  const initialRange = monthRange();
  const [from, setFrom] = useState(initialRange.from);
  const [to, setTo] = useState(initialRange.to);
  const [rows, setRows] = useState<FinancialReportResponse[]>([]);
  const [totals, setTotals] = useState<ReportTotals | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const trend = useMonthlyFinanceTrend(isChainViewer);

  useEffect(() => {
    if (!from || !to) return;
    setLoading(true);
    setError(null);
    const request = isChainViewer
      ? getChainReport(from, to).then((chain) => {
          const siteExpense = chain.bySite.reduce((sum, r) => sum + r.totalExpense, 0);
          setRows(chain.bySite);
          setTotals({
            revenue: chain.totalRevenue,
            expense: chain.totalExpense,
            outstanding: chain.totalOutstanding,
            sharedExpense: chain.totalExpense - siteExpense
          });
        })
      : getMySiteReports(from, to).then((reports) => {
          setRows(reports);
          setTotals({
            revenue: reports.reduce((sum, r) => sum + r.totalRevenue, 0),
            expense: reports.reduce((sum, r) => sum + r.totalExpense, 0),
            outstanding: reports.reduce((sum, r) => sum + r.totalOutstanding, 0),
            sharedExpense: 0
          });
        });
    request
      .catch((err) => {
        setRows([]);
        setTotals(null);
        setError(err instanceof ApiError ? err.message : t("reports.loadError"));
      })
      .finally(() => setLoading(false));
  }, [from, to, isChainViewer]);

  const applyPreset = (range: { from: string; to: string }) => {
    setFrom(range.from);
    setTo(range.to);
  };

  const year = new Date().getFullYear();
  const presets = [
    { label: t("reports.presetThisMonth"), range: monthRange() },
    { label: t("reports.presetLastMonth"), range: monthRange(new Date(), -1) },
    { label: t("reports.presetThisYear"), range: { from: `${year}-01-01`, to: `${year}-12-31` } }
  ];

  const isEmpty = totals != null && totals.revenue === 0 && totals.expense === 0 && totals.outstanding === 0;
  const visibleRows = rows.filter((r) => r.totalRevenue !== 0 || r.totalExpense !== 0 || r.totalOutstanding !== 0);

  return (
    <div className="space-y-6">
      <div className="border-b border-slate-200 pb-4">
        <h1 className="text-xl font-bold font-display tracking-tight text-slate-900">{t("reports.title")}</h1>
        <p className="text-sm text-slate-500 mt-1">{isChainViewer ? t("reports.descriptionChain") : t("reports.descriptionSite")}</p>
      </div>

      <FloatingError message={error} onClose={() => setError(null)} />

      <Card>
        <div className="flex flex-wrap items-end gap-3">
          <div className="w-44">
            <label className={labelClass}>{t("expenses.dateFrom")}</label>
            <DatePicker value={from} onChange={setFrom} max={to} />
          </div>
          <div className="w-44">
            <label className={labelClass}>{t("expenses.dateTo")}</label>
            <DatePicker value={to} onChange={setTo} min={from} />
          </div>
          <div className="flex flex-wrap gap-2">
            {presets.map((p) => (
              <Button
                key={p.label}
                size="sm"
                variant={p.range.from === from && p.range.to === to ? "primary" : "secondary"}
                onClick={() => applyPreset(p.range)}
              >
                {p.label}
              </Button>
            ))}
          </div>
        </div>
      </Card>

      {loading ? (
        <p className="text-sm text-slate-500">{t("common.loading")}</p>
      ) : totals == null ? null : isEmpty ? (
        <Card>
          <p className="text-sm text-slate-500">{t("reports.emptyPeriod")}</p>
        </Card>
      ) : (
        <>
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
            <StatCard icon={TrendingUp} label={t("reports.revenue")} value={formatVnd(totals.revenue)} tone="brand" />
            <StatCard
              icon={TrendingDown}
              label={t("reports.expense")}
              value={formatVnd(totals.expense)}
              hint={totals.sharedExpense > 0 ? t("reports.sharedExpenseHint", { amount: formatVnd(totals.sharedExpense) }) : undefined}
              tone="slate"
            />
            <StatCard icon={Scale} label={t("reports.net")} value={formatVnd(totals.revenue - totals.expense)} tone={totals.revenue >= totals.expense ? "brand" : "danger"} />
            <StatCard icon={Wallet} label={t("reports.outstanding")} value={formatVnd(totals.outstanding)} hint={t("reports.outstandingHint")} tone="warning" />
          </div>

          <Card padded={false} className="overflow-hidden">
            <div className="px-5 py-4 border-b border-slate-100 bg-slate-50">
              <span className="text-sm font-bold text-slate-700 font-display">{t("reports.bySiteTitle")}</span>
            </div>
            <TableContainer className="rounded-none border-0">
              <thead>
                <tr>
                  <Th>{t("common.site")}</Th>
                  <Th className="text-right">{t("reports.revenue")}</Th>
                  <Th className="text-right">{t("reports.expense")}</Th>
                  <Th className="text-right">{t("reports.net")}</Th>
                  <Th className="text-right">{t("reports.outstanding")}</Th>
                </tr>
              </thead>
              <tbody>
                {visibleRows.map((r) => (
                  <tr key={r.siteId} className="border-t border-slate-100">
                    <Td className="font-semibold text-slate-800">{r.siteName}</Td>
                    <Td className="text-right">{formatVnd(r.totalRevenue)}</Td>
                    <Td className="text-right">{formatVnd(r.totalExpense)}</Td>
                    <Td className={`text-right font-semibold ${r.totalRevenue - r.totalExpense < 0 ? "text-rose-600" : "text-slate-800"}`}>
                      {formatVnd(r.totalRevenue - r.totalExpense)}
                    </Td>
                    <Td className="text-right">{formatVnd(r.totalOutstanding)}</Td>
                  </tr>
                ))}
                {totals.sharedExpense > 0 && (
                  <tr className="border-t border-slate-100">
                    <Td className="text-slate-500 italic">{t("reports.sharedRow")}</Td>
                    <Td className="text-right">—</Td>
                    <Td className="text-right">{formatVnd(totals.sharedExpense)}</Td>
                    <Td className="text-right text-rose-600">{formatVnd(-totals.sharedExpense)}</Td>
                    <Td className="text-right">—</Td>
                  </tr>
                )}
              </tbody>
            </TableContainer>
          </Card>
        </>
      )}

      {isChainViewer && (
        <Card>
          <h3 className="text-sm font-bold text-slate-800 font-display">{t("reports.trendTitle")}</h3>
          <p className="text-[13px] text-slate-400 mt-0.5">{t("reports.trendSubtitle")}</p>
          {trend.loading ? (
            <p className="text-sm text-slate-500 mt-4">{t("common.loading")}</p>
          ) : trend.error ? (
            <p className="text-sm text-rose-600 mt-4">{t("reports.loadError")}</p>
          ) : (
            <FinanceTrendChart points={trend.points} />
          )}
        </Card>
      )}
    </div>
  );
}
