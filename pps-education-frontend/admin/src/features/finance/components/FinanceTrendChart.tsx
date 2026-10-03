import React from "react";
import { useTranslation } from "react-i18next";
import { MonthlyFinancePoint } from "../hooks/useMonthlyFinanceTrend";
import { formatVnd } from "../format";

/** Biểu đồ cột Thu/Chi theo tháng (số liệu thật từ báo cáo UC-32) — cột trái là thu, cột phải là chi. */
export default function FinanceTrendChart({ points }: { points: MonthlyFinancePoint[] }) {
  const { t } = useTranslation("finance");
  const maxVal = Math.max(1, ...points.flatMap((p) => [p.revenue, p.expense]));

  return (
    <div>
      <div className="h-56 w-full mt-4 flex items-end justify-between gap-2 px-2 border-b border-slate-100 pb-1 overflow-x-auto">
        {points.map((p) => {
          const [y, m] = p.month.split("-");
          return (
            <div key={p.month} className="flex flex-col items-center gap-1 min-w-[48px] flex-1">
              <div className="flex gap-1.5 h-44 items-end w-full justify-center">
                <div
                  style={{ height: `${(p.revenue / maxVal) * 100}%` }}
                  title={`${t("reports.revenue")}: ${formatVnd(p.revenue)}`}
                  className="w-3 sm:w-4 min-h-[2px] bg-brand-gradient rounded-t-xs"
                />
                <div
                  style={{ height: `${(p.expense / maxVal) * 100}%` }}
                  title={`${t("reports.expense")}: ${formatVnd(p.expense)}`}
                  className="w-3 sm:w-4 min-h-[2px] bg-slate-300 rounded-t-xs"
                />
              </div>
              <span className="text-[12px] font-semibold text-slate-500 mt-1 whitespace-nowrap">
                T{Number(m)}/{y.slice(2)}
              </span>
            </div>
          );
        })}
      </div>
      <div className="flex items-center gap-5 mt-3 text-[13px] font-medium justify-center">
        <div className="flex items-center gap-1.5">
          <span className="w-2.5 h-2.5 rounded-full bg-brand-gradient" />
          <span className="text-slate-600">{t("reports.revenue")}</span>
        </div>
        <div className="flex items-center gap-1.5">
          <span className="w-2.5 h-2.5 rounded-full bg-slate-300" />
          <span className="text-slate-600">{t("reports.expense")}</span>
        </div>
      </div>
    </div>
  );
}
