import React, { isValidElement, useEffect, useLayoutEffect, useMemo, useRef, useState } from "react";
import { createPortal } from "react-dom";
import { ChevronDown } from "lucide-react";
import { cn } from "@/lib/cn";

interface SelectOptionData {
  value: string;
  label: React.ReactNode;
  disabled?: boolean;
}

interface SelectProps {
  value: string | number | undefined;
  onChange: (e: { target: { value: string } }) => void;
  onBlur?: () => void;
  children: React.ReactNode;
  className?: string;
  disabled?: boolean;
  required?: boolean;
  id?: string;
  name?: string;
  "aria-label"?: string;
}

function extractOptions(children: React.ReactNode): SelectOptionData[] {
  const options: SelectOptionData[] = [];
  React.Children.forEach(children, (child) => {
    if (!isValidElement(child)) return;
    const props = child.props as { value?: string | number; children?: React.ReactNode; disabled?: boolean };
    options.push({ value: props.value == null ? "" : String(props.value), label: props.children, disabled: props.disabled });
  });
  return options;
}

/**
 * Thay thế <select> gốc — giữ nguyên API value/onChange/children (<option>) nên chỉ cần đổi tên thẻ ở
 * hầu hết chỗ dùng, nhưng tự vẽ panel lựa chọn thay vì list xổ mặc định của trình duyệt. Panel render
 * qua portal vào document.body (position: fixed theo toạ độ trigger) để không bị cắt mất khi đặt trong
 * ô bảng có overflow-x-auto (VD DailyCommentPanel, GradeSheetTable) — khác với dropdown thường chỉ absolute
 * trong DOM cha, sẽ bị overflow-x-auto của bảng cắt theo cả trục dọc.
 */
export default function Select({ value, onChange, children, className, disabled, id, name, ...aria }: SelectProps) {
  const [open, setOpen] = useState(false);
  const [rect, setRect] = useState<{ top: number; left: number; width: number } | null>(null);
  const triggerRef = useRef<HTMLButtonElement>(null);
  const panelRef = useRef<HTMLDivElement>(null);
  const options = useMemo(() => extractOptions(children), [children]);
  const selected = options.find((o) => o.value === String(value ?? ""));

  const updateRect = () => {
    const el = triggerRef.current;
    if (!el) return;
    const r = el.getBoundingClientRect();
    setRect({ top: r.bottom + 4, left: r.left, width: r.width });
  };

  useLayoutEffect(() => {
    if (open) updateRect();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open]);

  useEffect(() => {
    if (!open) return;
    const handlePointerDown = (e: MouseEvent) => {
      if (triggerRef.current?.contains(e.target as Node)) return;
      if (panelRef.current?.contains(e.target as Node)) return;
      setOpen(false);
    };
    const handleReposition = () => updateRect();
    document.addEventListener("mousedown", handlePointerDown);
    window.addEventListener("scroll", handleReposition, true);
    window.addEventListener("resize", handleReposition);
    return () => {
      document.removeEventListener("mousedown", handlePointerDown);
      window.removeEventListener("scroll", handleReposition, true);
      window.removeEventListener("resize", handleReposition);
    };
  }, [open]);

  return (
    <>
      <button
        ref={triggerRef}
        type="button"
        id={id}
        name={name}
        disabled={disabled}
        onClick={() => !disabled && setOpen((v) => !v)}
        className={cn("flex items-center justify-between gap-1.5 text-left cursor-pointer disabled:opacity-50 disabled:cursor-not-allowed", className)}
        {...aria}
      >
        <span className="truncate">{selected ? selected.label : ""}</span>
        <ChevronDown className={`w-3.5 h-3.5 text-slate-400 shrink-0 transition-transform ${open ? "rotate-180" : ""}`} aria-hidden="true" />
      </button>
      {open &&
        rect &&
        createPortal(
          <div
            ref={panelRef}
            style={{ position: "fixed", top: rect.top, left: rect.left, width: Math.max(rect.width, 160) }}
            className="z-[200] max-h-64 overflow-y-auto bg-white border border-slate-200 rounded-lg shadow-lg py-1 animate-in fade-in duration-100"
          >
            {options.map((opt, i) => (
              <button
                key={`${opt.value}-${i}`}
                type="button"
                disabled={opt.disabled}
                onClick={() => {
                  onChange({ target: { value: opt.value } });
                  setOpen(false);
                }}
                className={`w-full text-left px-3 py-2 text-sm transition-colors disabled:opacity-40 disabled:cursor-not-allowed ${
                  opt.value === String(value ?? "") ? "bg-brand-orange/10 text-brand-orange font-semibold" : "text-slate-700 hover:bg-slate-50"
                }`}
              >
                {opt.label}
              </button>
            ))}
          </div>,
          document.body
        )}
    </>
  );
}
