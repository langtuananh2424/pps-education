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
              // Sửa 2026-09-23 (lỗi thật, phát hiện qua test tay) — trước đây vừa có "sm:left-auto
              // sm:right-auto" (base) vừa có "sm:right-0"/"sm:left-0" (theo align) cùng áp dụng ở
              // breakpoint sm: — cn() chỉ nối chuỗi (không dedupe như tailwind-merge), class nào
              // Tailwind SINH CSS sau mới thắng (không theo thứ tự viết ở đây), có lúc "right-auto" đè
              // mất "right-0" khiến panel mất neo, trôi lệch khỏi trigger. Gộp thẳng cặp
              // left/right-0/auto tương ứng theo align, không còn 2 class cùng thuộc tính chung breakpoint.
              "fixed left-3 right-3 top-[72px] sm:absolute sm:top-auto sm:mt-2",
              "bg-white rounded-2xl sm:rounded-lg shadow-xl border border-slate-200 z-50 animate-in fade-in slide-in-from-top-3 sm:slide-in-from-top-2 duration-150 overflow-hidden",
              align === "right" ? "sm:left-auto sm:right-0" : "sm:right-auto sm:left-0",
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
