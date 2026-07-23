import React, { useEffect, useRef, useState } from "react";
import { Check, ChevronDown } from "lucide-react";

interface PortalDropdownOption<T extends string | number> {
  value: T;
  label: string;
}

interface PortalDropdownProps<T extends string | number> {
  icon: React.ComponentType<{ size?: number }>;
  label: string;
  value: T;
  options: PortalDropdownOption<T>[];
  onChange: (value: T) => void;
}

/**
 * Dropdown tự dựng (không dùng <select> thô) — <select> để browser tự vẽ danh
 * sách lựa chọn (viền cứng, tô xanh mặc định của OS/browser) không style được,
 * nhìn "nhựa"/lệch hẳn phong cách bo tròn + teal của thiết kế Portal. Đóng khi
 * click ra ngoài (mousedown, không phải click, để bắt được trước khi nút bên
 * dưới panel kịp nhận sự kiện).
 */
export default function PortalDropdown<T extends string | number>({ icon: Icon, label, value, options, onChange }: PortalDropdownProps<T>) {
  const [open, setOpen] = useState(false);
  const rootRef = useRef<HTMLDivElement>(null);
  const selected = options.find((o) => o.value === value);

  useEffect(() => {
    if (!open) return;
    const handleClickOutside = (e: MouseEvent) => {
      if (rootRef.current && !rootRef.current.contains(e.target as Node)) setOpen(false);
    };
    document.addEventListener("mousedown", handleClickOutside);
    return () => document.removeEventListener("mousedown", handleClickOutside);
  }, [open]);

  return (
    <div ref={rootRef} className="relative">
      <button
        type="button"
        onClick={() => setOpen((v) => !v)}
        className={`w-full flex items-center gap-2.5 bg-sky-2 border px-3 py-2.5 rounded-[16px] transition-colors ${
          open ? "border-teal ring-1 ring-teal/30" : "border-line hover:border-teal/40"
        }`}
      >
        <div className="w-8 h-8 rounded-full bg-teal/15 border border-teal/30 flex items-center justify-center text-teal-deep shrink-0">
          <Icon size={15} />
        </div>
        <div className="flex-1 min-w-0 text-left">
          <div className="text-[9px] text-muted font-extrabold uppercase tracking-wide">{label}</div>
          <div className="text-xs font-extrabold text-ink truncate">{selected?.label ?? "—"}</div>
        </div>
        <ChevronDown size={14} className={`text-muted shrink-0 transition-transform ${open ? "rotate-180" : ""}`} />
      </button>

      {open && (
        <div className="absolute z-20 top-[calc(100%+6px)] left-0 right-0 bg-white border border-line/80 rounded-[16px] shadow-[0_12px_30px_rgba(30,42,69,0.12)] p-1.5 max-h-64 overflow-y-auto">
          {options.map((o) => (
            <button
              key={o.value}
              type="button"
              onClick={() => {
                onChange(o.value);
                setOpen(false);
              }}
              className={`w-full flex items-center justify-between gap-2 px-3 py-2 rounded-[12px] text-xs font-bold text-left transition-colors ${
                o.value === value ? "bg-teal/10 text-teal-deep font-extrabold" : "text-ink hover:bg-sky-2"
              }`}
            >
              <span className="truncate">{o.label}</span>
              {o.value === value && <Check size={14} className="text-teal-deep shrink-0" />}
            </button>
          ))}
        </div>
      )}
    </div>
  );
}
