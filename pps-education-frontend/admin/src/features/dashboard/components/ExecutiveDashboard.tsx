import React from "react";
import { useNavigate } from "react-router-dom";
import { useTranslation } from "react-i18next";
import { AlertTriangle, ArrowRight, Calendar } from "lucide-react";
import { mockLeads } from "@/data/mockData";
import { useApp } from "@/context/AppContext";
import { useMonthlyFinanceTrend } from "@/features/finance/hooks/useMonthlyFinanceTrend";
import FinanceTrendChart from "@/features/finance/components/FinanceTrendChart";
import Card from "@/components/ui/Card";
import Button from "@/components/ui/Button";

interface ExecutiveDashboardProps {
  activeStudentsCount: number;
  campusesCount: number;
}

/**
 * Thu/Chi/biên lợi nhuận và biểu đồ 6 tháng lấy từ báo cáo tài chính thật (UC-32, cùng nguồn trang Báo cáo
 * kế toán) — chỉ hiện với tài khoản có quyền finance.report.view. Phễu CRM và thông báo gia hạn vẫn là dữ
 * liệu minh hoạ, chưa nằm trong phạm vi hoàn thiện tài chính 2026-10-03.
 */
export default function ExecutiveDashboard({ activeStudentsCount, campusesCount }: ExecutiveDashboardProps) {
  const navigate = useNavigate();
  const { t } = useTranslation("dashboard");
  const { hasPermission } = useApp();
  const canViewFinance = hasPermission("finance.report.view");
  const trend = useMonthlyFinanceTrend(canViewFinance);
  const current = trend.points[trend.points.length - 1];
  const previous = trend.points[trend.points.length - 2];
  const revenue = current?.revenue ?? 0;
  const expense = current?.expense ?? 0;
  const net = revenue - expense;
  const revenueChange = previous && previous.revenue > 0 ? ((revenue - previous.revenue) / previous.revenue) * 100 : null;
  const now = new Date();

  const funnel = [
    { stage: t("executive.funnelNewLeads"), count: mockLeads.length, percentage: 100, color: "bg-slate-800" },
    { stage: t("executive.funnelContacted"), count: mockLeads.filter((l) => l.status !== "NEW").length, percentage: 80, color: "bg-brand-orange" },
    { stage: t("executive.funnelQualified"), count: mockLeads.filter((l) => ["QUALIFIED", "WON"].includes(l.status)).length, percentage: 60, color: "bg-brand-yellow" },
    { stage: t("executive.funnelWon"), count: mockLeads.filter((l) => l.status === "WON").length, percentage: 33, color: "bg-brand-red animate-pulse" }
  ];

  return (
    <div className="space-y-6">
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4">
        <div>
          <h1 className="text-xl font-bold font-display tracking-tight text-slate-900">{t("executive.title")}</h1>
          <p className="text-sm text-slate-500 mt-1">{t("executive.subtitle")}</p>
        </div>
        <div className="flex items-center gap-2 text-sm font-medium text-slate-600 bg-white border border-slate-200 px-3 py-2 rounded-lg">
          <Calendar className="w-4 h-4 text-brand-orange" />
          <span>{t("executive.reportPeriod", { month: now.getMonth() + 1, year: now.getFullYear() })}</span>
        </div>
      </div>

      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        {canViewFinance && (
          <>
            <Card className="relative overflow-hidden">
              <span className="text-sm text-slate-400 font-bold block uppercase tracking-wider font-display">{t("executive.actualRevenue")}</span>
              <div className="flex items-baseline gap-1.5 mt-2">
                <span className="text-2xl font-bold text-slate-900 font-display">{revenue.toLocaleString("vi-VN")}</span>
                <span className="text-sm font-semibold text-slate-500">VND</span>
              </div>
              <div className={`flex items-center gap-1 mt-2 text-[12px] font-semibold ${revenueChange != null && revenueChange < 0 ? "text-rose-600" : "text-emerald-600"}`}>
                <span>
                  {revenueChange == null
                    ? t("executive.noPreviousMonth")
                    : t("executive.vsLastMonth", { value: `${revenueChange >= 0 ? "+" : ""}${revenueChange.toFixed(1)}` })}
                </span>
              </div>
            </Card>

            <Card className="relative overflow-hidden">
              <span className="text-sm text-slate-400 font-bold block uppercase tracking-wider font-display">{t("executive.operatingCost")}</span>
              <div className="flex items-baseline gap-1.5 mt-2">
                <span className="text-2xl font-bold text-slate-900 font-display">{expense.toLocaleString("vi-VN")}</span>
                <span className="text-sm font-semibold text-slate-500">VND</span>
              </div>
              <div className="flex items-center gap-1 mt-2 text-[12px] font-semibold text-slate-500">
                <span>{t("executive.operatingCostNote")}</span>
              </div>
            </Card>

            <Card className="relative overflow-hidden">
              <span className="text-sm text-slate-400 font-bold block uppercase tracking-wider font-display">{t("executive.profitMargin")}</span>
              <div className="flex items-baseline gap-1.5 mt-2">
                <span className={`text-2xl font-bold font-display ${net < 0 ? "text-rose-600" : "text-slate-900"}`}>
                  {revenue > 0 ? `${net >= 0 ? "+" : ""}${((net / revenue) * 100).toFixed(1)}%` : t("executive.pendingUpdate")}
                </span>
              </div>
              <div className="flex items-center gap-1 mt-2 text-[12px] font-semibold text-brand-orange">
                <span>{t("executive.netThisMonth", { amount: net.toLocaleString("vi-VN") })}</span>
              </div>
            </Card>
          </>
        )}

        <Card className="relative overflow-hidden">
          <span className="text-sm text-slate-400 font-bold block uppercase tracking-wider font-display">{t("executive.activeStudents")}</span>
          <div className="flex items-baseline gap-1.5 mt-2">
            <span className="text-2xl font-bold text-slate-900 font-display">{activeStudentsCount}</span>
            <span className="text-sm font-medium text-slate-500">{t("executive.studentsUnit")}</span>
          </div>
          <div className="flex items-center gap-1 mt-2 text-[12px] font-semibold text-slate-500">
            <span>{t("executive.distributedAcross", { count: campusesCount })}</span>
          </div>
        </Card>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {canViewFinance && (
          <Card className="lg:col-span-2 flex flex-col justify-between">
            <div>
              <h3 className="text-sm font-bold text-slate-800 font-display">{t("executive.revenueChartTitle")}</h3>
              <p className="text-[13px] text-slate-400 mt-0.5">{t("executive.revenueChartSubtitle")}</p>
            </div>
            {trend.loading ? (
              <p className="text-sm text-slate-500 mt-4">{t("executive.loadingFinance")}</p>
            ) : trend.error ? (
              <p className="text-sm text-rose-600 mt-4">{t("executive.financeLoadError")}</p>
            ) : (
              <FinanceTrendChart points={trend.points} />
            )}
          </Card>
        )}

        <Card className="flex flex-col justify-between">
          <div>
            <h3 className="text-sm font-bold text-slate-800 font-display">{t("executive.funnelTitle")}</h3>
            <p className="text-[13px] text-slate-400 mt-0.5">{t("executive.funnelSubtitle")}</p>
          </div>

          <div className="space-y-3.5 my-4">
            {funnel.map((f) => (
              <div key={f.stage} className="space-y-1">
                <div className="flex items-center justify-between text-sm font-semibold">
                  <span className="text-slate-600">{f.stage}</span>
                  <span className="text-slate-900 font-mono">{t("executive.studentsCount", { count: f.count })}</span>
                </div>
                <div className="w-full bg-slate-100 h-2.5 rounded-full overflow-hidden">
                  <div style={{ width: `${f.percentage}%` }} className={`h-full rounded-full ${f.color}`} />
                </div>
              </div>
            ))}
          </div>

          <Button variant="secondary" onClick={() => navigate("/crm/leads")} className="w-full">
            <span>{t("executive.viewLeadsList")}</span>
            <ArrowRight className="w-3.5 h-3.5" />
          </Button>
        </Card>
      </div>

      <div className="bg-brand-gradient/5 border border-brand-orange/20 rounded-xl p-4 flex flex-col md:flex-row items-start md:items-center justify-between gap-4">
        <div className="flex items-start gap-3">
          <AlertTriangle className="w-5 h-5 text-brand-orange mt-0.5 shrink-0" />
          <div>
            <h4 className="text-sm font-bold text-slate-800 font-display">{t("executive.renewalNoticeTitle")}</h4>
            <p className="text-[13px] text-slate-600 mt-1">
              {t("executive.renewalNoticePrefix")} <strong>Trường THCS Lê Quý Đôn</strong> {t("executive.renewalNoticeMiddle")}{" "}
              <strong>(31/08/2026)</strong>. {t("executive.renewalNoticeSuffix")}
            </p>
          </div>
        </div>
        <Button variant="secondary" onClick={() => navigate("/facility/campuses")} className="whitespace-nowrap">
          {t("executive.manageContracts")}
        </Button>
      </div>
    </div>
  );
}
