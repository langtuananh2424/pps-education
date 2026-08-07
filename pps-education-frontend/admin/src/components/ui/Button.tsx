import React from "react";
import { cn } from "@/lib/cn";

type Variant = "primary" | "secondary" | "ghost" | "danger" | "dark";
type Size = "sm" | "md";

interface ButtonProps extends React.ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: Variant;
  size?: Size;
}

const variantClasses: Record<Variant, string> = {
  primary: "bg-brand-gradient text-white shadow-glow hover:opacity-95",
  secondary:
    "bg-white border border-slate-200 text-slate-700 hover:bg-slate-50 hover:border-slate-300 shadow-xs",
  ghost: "bg-transparent text-slate-600 hover:bg-slate-100",
  danger:
    "bg-white border border-slate-200 text-rose-500 hover:bg-rose-50 hover:border-rose-200 hover:text-rose-600",
  dark: "bg-brand-gradient text-white hover:opacity-95 shadow-glow"
};

const sizeClasses: Record<Size, string> = {
  sm: "px-3 py-1.5 text-[11px] gap-1.5",
  md: "px-4 py-2 text-xs gap-2"
};

export default function Button({
  variant = "secondary",
  size = "md",
  className,
  children,
  ...props
}: ButtonProps) {
  return (
    <button
      className={cn(
        "inline-flex items-center justify-center rounded-lg font-semibold font-display transition-all disabled:opacity-50 disabled:cursor-not-allowed cursor-pointer",
        variantClasses[variant],
        sizeClasses[size],
        className
      )}
      {...props}
    >
      {children}
    </button>
  );
}
