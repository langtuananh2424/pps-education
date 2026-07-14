import React from "react";
import { cn } from "@/lib/cn";

interface TableContainerProps {
  children: React.ReactNode;
  className?: string;
}

/** Bọc <table> để cuộn ngang trên mobile thay vì vỡ layout. */
export default function TableContainer({ children, className }: TableContainerProps) {
  return (
    <div className={cn("overflow-x-auto rounded-xl border border-slate-200", className)}>
      <table className="w-full text-xs text-left">{children}</table>
    </div>
  );
}

export function Th({ children, className }: { children: React.ReactNode; className?: string }) {
  return (
    <th
      className={cn(
        "px-4 py-3 bg-slate-50 text-[10px] font-bold uppercase tracking-wide text-slate-500 whitespace-nowrap",
        className
      )}
    >
      {children}
    </th>
  );
}

export function Td({ children, className, title }: { children: React.ReactNode; className?: string; title?: string }) {
  return (
    <td className={cn("px-4 py-3 text-slate-700 align-middle", className)} title={title}>
      {children}
    </td>
  );
}
