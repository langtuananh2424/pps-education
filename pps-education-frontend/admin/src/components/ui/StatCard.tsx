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

export default function StatCard({ icon: Icon, label, value, trend, hint, tone = "brand" }: StatCardProps) {
  return (
    <Card className="flex items-start justify-between gap-3">
      <div className="min-w-0">
        <p className="text-sm font-bold text-slate-500 uppercase tracking-wide truncate">{label}</p>
        <p className="text-2xl md:text-3xl font-bold font-display text-slate-900 mt-1.5 truncate">{value}</p>
        {trend && (
          <div
            className={cn(
              "inline-flex items-center gap-1 mt-2 text-sm font-semibold",
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
        {!trend && hint && <p className="text-sm text-slate-400 mt-2 truncate">{hint}</p>}
      </div>
      <div className={cn("w-12 h-12 rounded-xl flex items-center justify-center shrink-0", toneClasses[tone])}>
        <Icon className="w-6 h-6" />
      </div>
    </Card>
  );
}
