import React, { useState } from "react";
import { cn } from "@/lib/cn";

interface DropdownProps {
  trigger: React.ReactNode;
  children: React.ReactNode;
  align?: "left" | "right";
  panelClassName?: string;
}

/** Popover chuẩn cho role-switcher, notification, profile menu ở Header. */
export default function Dropdown({ trigger, children, align = "right", panelClassName }: DropdownProps) {
  const [open, setOpen] = useState(false);

  return (
    <div className="relative">
      <div onClick={() => setOpen((v) => !v)}>{trigger}</div>
      {open && (
        <>
          <div className="fixed inset-0 z-40" onClick={() => setOpen(false)} />
          <div
            className={cn(
              "absolute mt-2 bg-white rounded-lg shadow-xl border border-slate-200 z-50 animate-in fade-in slide-in-from-top-2 duration-150 overflow-hidden",
              align === "right" ? "right-0" : "left-0",
              panelClassName
            )}
            onClick={() => setOpen(false)}
          >
            {children}
          </div>
        </>
      )}
    </div>
  );
}
