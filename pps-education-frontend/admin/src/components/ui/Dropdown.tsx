import React, { useState } from "react";
import { cn } from "@/lib/cn";

interface DropdownProps {
  trigger: React.ReactNode;
  children: React.ReactNode;
  align?: "left" | "right";
  panelClassName?: string;
}

/**
 * Popover chuẩn cho role-switcher, notification, profile menu ở Header.
 *
 * Responsive dưới sm/mobile (bổ sung ngoài thiết kế gốc, xác nhận với người dùng 2026-09-23): panel
 * "absolute" neo cứng theo trigger (VD notification "w-80" neo right-0) dễ tràn ra ngoài màn hình hẹp
 * (<360px) hoặc bị cắt sát mép phải, gây scroll ngang cả trang -- đổi sang "fixed" căn theo 2 mép
 * trái/phải viewport (cách Header 1 khoảng, giống notification center của app mobile) khi dưới sm.
 * Từ sm trở lên GIỮ NGUYÊN hành vi cũ (absolute, neo theo trigger, rộng theo panelClassName) — không
 * đổi giao diện chính trên desktop.
 */
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
              "fixed left-3 right-3 top-[72px] sm:absolute sm:inset-x-auto sm:left-auto sm:right-auto sm:top-auto sm:mt-2",
              "bg-white rounded-2xl sm:rounded-lg shadow-xl border border-slate-200 z-50 animate-in fade-in slide-in-from-top-3 sm:slide-in-from-top-2 duration-150 overflow-hidden",
              align === "right" ? "sm:right-0" : "sm:left-0",
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
