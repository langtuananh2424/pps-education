import React from "react";
import { cn } from "@/lib/cn";

interface TabItem {
  id: string;
  label: string;
}

interface TabsProps {
  items: TabItem[];
  activeId: string;
  onChange: (id: string) => void;
  className?: string;
}

export default function Tabs({ items, activeId, onChange, className }: TabsProps) {
  return (
    <div className={cn("flex items-center gap-1.5 overflow-x-auto pb-1", className)}>
      {items.map((item) => (
        <button
          key={item.id}
          onClick={() => onChange(item.id)}
          className={cn(
            "px-3.5 py-1.5 rounded-full text-sm font-semibold whitespace-nowrap transition-all",
            activeId === item.id
              ? "bg-brand-gradient text-white shadow-soft"
              : "bg-white border border-slate-200 text-slate-600 hover:bg-slate-50"
          )}
        >
          {item.label}
        </button>
      ))}
    </div>
  );
}
