import React from "react";
import { useNavigate } from "react-router-dom";
import { useTranslation } from "react-i18next";
import { AlertTriangle, ArrowRight, Calendar } from "lucide-react";
import { mockLeads } from "@/data/mockData";
import Card from "@/components/ui/Card";
import Button from "@/components/ui/Button";

interface ExecutiveDashboardProps {
  totalPaid: number;
  totalExpenses: number;
  profit: number;
  activeStudentsCount: number;
  campusesCount: number;
}

const revenueSeries = [
  { month: "T12/25", in: 120, out: 95 },
  { month: "T1/26", in: 150, out: 100 },
  { month: "T2/26", in: 95, out: 90 },
  { month: "T3/26", in: 180, out: 110 },
  { month: "T4/26", in: 210, out: 125 },
  { month: "T5/26", in: 160, out: 115 },
  { month: "T6/26", in: 240, out: 140 }
];

export default function ExecutiveDashboard({ totalPaid, totalExpenses, profit, activeStudentsCount, campusesCount }: ExecutiveDashboardProps) {
  const navigate = useNavigate();
  const { t } = useTranslation("dashboard");
  const chartData = [...revenueSeries, { month: t("executive.thisMonth"), in: totalPaid / 1_000_000, out: totalExpenses / 1_000_000 }];
  const maxVal = 260;

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
          <p className="text-xs text-slate-500 mt-1">{t("executive.subtitle")}</p>
        </div>
        <div className="flex items-center gap-2 text-xs font-medium text-slate-600 bg-white border border-slate-200 px-3 py-2 rounded-lg">
          <Calendar className="w-4 h-4 text-brand-orange" />
          <span>{t("executive.reportPeriod")}</span>
        </div>
      </div>

      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        <Card className="relative overflow-hidden">
          <span className="text-xs text-slate-400 font-bold block uppercase tracking-wider font-display">{t("executive.actualRevenue")}</span>
          <div className="flex items-baseline gap-1.5 mt-2">
            <span className="text-2xl font-bold text-slate-900 font-display">{totalPaid.toLocaleString("vi-VN")}</span>
            <span className="text-xs font-semibold text-slate-500">VND</span>
          </div>
          <div className="flex items-center gap-1 mt-2 text-[10px] font-semibold text-emerald-600">
            <span>{t("executive.vsLastMonth")}</span>
          </div>
        </Card>

        <Card className="relative overflow-hidden">
          <span className="text-xs text-slate-400 font-bold block uppercase tracking-wider font-display">{t("executive.operatingCost")}</span>
          <div className="flex items-baseline gap-1.5 mt-2">
            <span className="text-2xl font-bold text-slate-900 font-display">{totalExpenses.toLocaleString("vi-VN")}</span>
            <span className="text-xs font-semibold text-slate-500">VND</span>
          </div>
          <div className="flex items-center gap-1 mt-2 text-[10px] font-semibold text-slate-500">
            <span>{t("executive.operatingCostNote")}</span>
          </div>
        </Card>

        <Card className="relative overflow-hidden">
          <span className="text-xs text-slate-400 font-bold block uppercase tracking-wider font-display">{t("executive.profitMargin")}</span>
          <div className="flex items-baseline gap-1.5 mt-2">
            <span className="text-2xl font-bold text-slate-900 font-display">
              {profit > 0 ? `+${((profit / totalPaid) * 100).toFixed(1)}%` : t("executive.pendingUpdate")}
            </span>
          </div>
          <div className="flex items-center gap-1 mt-2 text-[10px] font-semibold text-brand-orange">
            <span>{t("executive.onTargetNote")}</span>
          </div>
        </Card>

        <Card className="relative overflow-hidden">
          <span className="text-xs text-slate-400 font-bold block uppercase tracking-wider font-display">{t("executive.activeStudents")}</span>
          <div className="flex items-baseline gap-1.5 mt-2">
            <span className="text-2xl font-bold text-slate-900 font-display">{activeStudentsCount}</span>
            <span className="text-xs font-medium text-slate-500">{t("executive.studentsUnit")}</span>
          </div>
          <div className="flex items-center gap-1 mt-2 text-[10px] font-semibold text-slate-500">
            <span>{t("executive.distributedAcross", { count: campusesCount })}</span>
          </div>
        </Card>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        <Card className="lg:col-span-2 flex flex-col justify-between">
          <div>
            <h3 className="text-sm font-bold text-slate-800 font-display">{t("executive.revenueChartTitle")}</h3>
            <p className="text-[11px] text-slate-400 mt-0.5">{t("executive.revenueChartSubtitle")}</p>
          </div>

          <div className="h-60 w-full mt-4 flex items-end justify-between relative px-2 border-b border-slate-100 pb-1 overflow-x-auto">
            {chartData.map((data, index) => {
              const inHeight = (data.in / maxVal) * 100;
              const outHeight = (data.out / maxVal) * 100;
              return (
                <div key={index} className="flex flex-col items-center gap-1 z-10 min-w-[10%]">
                  <div className="flex gap-1.5 h-44 items-end w-full justify-center">
                    <div
                      style={{ height: `${inHeight}%` }}
                      className="w-3 sm:w-4 bg-brand-gradient rounded-t-xs transition-all duration-500 cursor-pointer hover:opacity-90 relative group"
                    >
                      <div className="absolute bottom-full mb-1 left-1/2 -translate-x-1/2 bg-slate-900 text-white text-[9px] px-1.5 py-0.5 rounded opacity-0 group-hover:opacity-100 transition-opacity pointer-events-none whitespace-nowrap">
                        {data.in.toFixed(0)}M VND
                      </div>
                    </div>
                    <div
                      style={{ height: `${outHeight}%` }}
                      className="w-3 sm:w-4 bg-slate-300 rounded-t-xs transition-all duration-500 cursor-pointer hover:bg-slate-400 relative group"
                    >
                      <div className="absolute bottom-full mb-1 left-1/2 -translate-x-1/2 bg-slate-900 text-white text-[9px] px-1.5 py-0.5 rounded opacity-0 group-hover:opacity-100 transition-opacity pointer-events-none whitespace-nowrap">
                        {data.out.toFixed(0)}M VND
                      </div>
                    </div>
                  </div>
                  <span className="text-[10px] font-semibold text-slate-500 mt-1 font-sans whitespace-nowrap">{data.month}</span>
                </div>
              );
            })}
          </div>

          <div className="flex items-center gap-5 mt-4 text-[11px] font-medium justify-center border-t border-slate-100 pt-3">
            <div className="flex items-center gap-1.5">
              <span className="w-2.5 h-2.5 rounded-full bg-brand-gradient" />
              <span className="text-slate-600">{t("executive.legendRevenue")}</span>
            </div>
            <div className="flex items-center gap-1.5">
              <span className="w-2.5 h-2.5 rounded-full bg-slate-300" />
              <span className="text-slate-600">{t("executive.legendExpense")}</span>
            </div>
          </div>
        </Card>

        <Card className="flex flex-col justify-between">
          <div>
            <h3 className="text-sm font-bold text-slate-800 font-display">{t("executive.funnelTitle")}</h3>
            <p className="text-[11px] text-slate-400 mt-0.5">{t("executive.funnelSubtitle")}</p>
          </div>

          <div className="space-y-3.5 my-4">
            {funnel.map((f) => (
              <div key={f.stage} className="space-y-1">
                <div className="flex items-center justify-between text-xs font-semibold">
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
            <h4 className="text-xs font-bold text-slate-800 font-display">{t("executive.renewalNoticeTitle")}</h4>
            <p className="text-[11px] text-slate-600 mt-1">
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
