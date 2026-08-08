import React from "react";
import { TrendingDown, TrendingUp } from "lucide-react";
import Card from "./Card";
import { cn } from "@/lib/cn";

interface StatCardProps {
  icon: React.ComponentType<{ className?: string }>;
  label: string;
  value: string;
  trend?: { value: string; direction: "up" | "down" };
  /** Chú thích tĩnh (không phải xu hướng tăng/giảm) khi không có số liệu so sánh kỳ trước đáng tin cậy — tránh bịa mũi tên tăng/giảm không có dữ liệu thật. */
  hint?: string;
  tone?: "brand" | "slate" | "warning" | "danger";
}

const toneClasses: Record<NonNullable<StatCardProps["tone"]>, string> = {
  brand: "bg-brand-gradient text-white shadow-glow",
  slate: "bg-slate-100 text-slate-500",
  warning: "bg-amber-50 text-amber-600",
  danger: "bg-rose-50 text-rose-600"
};

/** Khối bo tròn mờ ở góc thẻ — cùng tông với icon, chỉ để trang trí (giống thẻ KPI trong hình mẫu). */
const cornerBlobClasses: Record<NonNullable<StatCardProps["tone"]>, string> = {
  brand: "bg-brand-orange/15",
  slate: "bg-slate-300/25",
  warning: "bg-amber-300/25",
  danger: "bg-rose-300/25"
};

/** Icon riêng 1 hàng phía trên, nội dung phía dưới — thẻ cao/hẹp hơn kiểu ngang trước đây, đúng tỉ lệ thẻ trong carousel. */
export default function StatCard({ icon: Icon, label, value, trend, hint, tone = "brand" }: StatCardProps) {
  return (
    <Card className="relative overflow-hidden shadow-lg h-full flex flex-col">
      <div className={cn("absolute -right-6 -bottom-10 w-28 h-28 rounded-full blur-2xl pointer-events-none", cornerBlobClasses[tone])} />
      <div className={cn("relative w-11 h-11 rounded-xl flex items-center justify-center shrink-0", toneClasses[tone])}>
        <Icon className="w-5 h-5" />
      </div>
      <div className="relative min-w-0 mt-4">
        <p className="text-[11px] font-bold text-slate-500 uppercase tracking-wide truncate">{label}</p>
        <p className="text-2xl font-bold font-display text-slate-900 mt-1.5 truncate">{value}</p>
        {trend && (
          <div
            className={cn(
              "inline-flex items-center gap-1 mt-2 text-xs font-semibold",
              trend.direction === "up" ? "text-emerald-600" : "text-rose-500"
            )}
          >
            {trend.direction === "up" ? (
              <TrendingUp className="w-3.5 h-3.5" />
            ) : (
              <TrendingDown className="w-3.5 h-3.5" />
            )}
            <span>{trend.value}</span>
          </div>
        )}
        {!trend && hint && <p className="text-xs text-slate-400 mt-2 truncate">{hint}</p>}
      </div>
    </Card>
  );
}
