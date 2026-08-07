import React from "react";
import { cn } from "@/lib/cn";

export type BadgeVariant = "success" | "warning" | "danger" | "info" | "neutral" | "brand";

const variantClasses: Record<BadgeVariant, string> = {
  success: "bg-emerald-50 text-emerald-600 border-emerald-100",
  warning: "bg-amber-50 text-amber-600 border-amber-100",
  danger: "bg-rose-50 text-rose-600 border-rose-100",
  info: "bg-sky-50 text-sky-600 border-sky-100",
  neutral: "bg-slate-100 text-slate-500 border-slate-200",
  brand: "bg-orange-50 text-brand-red border-orange-100"
};

interface BadgeProps {
  variant?: BadgeVariant;
  children: React.ReactNode;
  className?: string;
}

export default function Badge({ variant = "neutral", children, className }: BadgeProps) {
  return (
    <span
      className={cn(
        "inline-flex items-center gap-1 px-2 py-0.5 rounded-full border text-xs font-bold uppercase tracking-wide whitespace-nowrap",
        variantClasses[variant],
        className
      )}
    >
      {children}
    </span>
  );
}
