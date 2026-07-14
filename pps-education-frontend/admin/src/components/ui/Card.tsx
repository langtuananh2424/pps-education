import React from "react";
import { cn } from "@/lib/cn";

interface CardProps extends React.HTMLAttributes<HTMLDivElement> {
  padded?: boolean;
}

export default function Card({ padded = true, className, children, ...props }: CardProps) {
  return (
    <div
      className={cn(
        "bg-white rounded-2xl border border-slate-200/70 shadow-soft",
        padded && "p-4 md:p-6",
        className
      )}
      {...props}
    >
      {children}
    </div>
  );
}
