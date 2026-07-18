import React from "react";
import { TrendingDown, TrendingUp } from "lucide-react";
import Card from "./Card";
import { cn } from "@/lib/cn";

interface StatCardProps {
  icon: React.ComponentType<{ className?: string }>;
  label: string;
  value: string;
  trend?: { value: string; direction: "up" | "down" };
  tone?: "brand" | "slate";
}

export default function StatCard({ icon: Icon, label, value, trend, tone = "brand" }: StatCardProps) {
  return (
    <Card className="flex items-start justify-between gap-3">
      <div className="min-w-0">
        <p className="text-[11px] font-semibold text-slate-500 truncate">{label}</p>
        <p className="text-xl md:text-2xl font-bold font-display text-slate-900 mt-1 truncate">{value}</p>
        {trend && (
          <div
            className={cn(
              "inline-flex items-center gap-1 mt-2 text-[11px] font-semibold",
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
      </div>
      <div
        className={cn(
          "w-10 h-10 rounded-xl flex items-center justify-center shrink-0",
          tone === "brand" ? "bg-brand-gradient text-white shadow-glow" : "bg-slate-100 text-slate-500"
        )}
      >
        <Icon className="w-5 h-5" />
      </div>
    </Card>
  );
}
