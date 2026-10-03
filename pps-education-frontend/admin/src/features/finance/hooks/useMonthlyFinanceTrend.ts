import { useEffect, useState } from "react";
import { getChainReport } from "../api";
import { monthRange } from "../format";

export interface MonthlyFinancePoint {
  /** "YYYY-MM" */
  month: string;
  revenue: number;
  expense: number;
}

/**
 * Thu/Chi toàn chuỗi của `months` tháng gần nhất (tháng hiện tại ở cuối) — mỗi tháng 1 lần gọi báo cáo UC-32.
 * Chỉ gọi khi `enabled` (người dùng có quyền finance.report.view), tránh 403 cho vai trò không được xem.
 */
export function useMonthlyFinanceTrend(enabled: boolean, months = 6) {
  const [points, setPoints] = useState<MonthlyFinancePoint[]>([]);
  const [loading, setLoading] = useState(enabled);
  const [error, setError] = useState(false);

  useEffect(() => {
    if (!enabled) return;
    let cancelled = false;
    setLoading(true);
    const ranges = Array.from({ length: months }, (_, i) => monthRange(new Date(), i - months + 1));
    Promise.all(ranges.map((r) => getChainReport(r.from, r.to)))
      .then((reports) => {
        if (cancelled) return;
        setPoints(
          reports.map((report, i) => ({
            month: ranges[i].from.slice(0, 7),
            revenue: report.totalRevenue,
            expense: report.totalExpense
          }))
        );
        setError(false);
      })
      .catch(() => !cancelled && setError(true))
      .finally(() => !cancelled && setLoading(false));
    return () => {
      cancelled = true;
    };
  }, [enabled, months]);

  return { points, loading, error };
}
