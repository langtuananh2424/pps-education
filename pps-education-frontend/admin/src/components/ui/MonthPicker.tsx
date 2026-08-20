import React, { useEffect, useLayoutEffect, useMemo, useRef, useState } from "react";
import { createPortal } from "react-dom";
import { CalendarDays, ChevronLeft, ChevronRight } from "lucide-react";
import { cn } from "@/lib/cn";

interface MonthPickerProps {
  value: string; // "YYYY-MM" hoặc ""
  onChange: (value: string) => void;
  min?: string; // "YYYY-MM"
  max?: string; // "YYYY-MM"
  placeholder?: string;
  disabled?: boolean;
  className?: string;
}

const MONTH_LABELS = Array.from({ length: 12 }, (_, i) => `Tháng ${i + 1}`);

function parseYm(s: string): { year: number; month: number } | null {
  if (!s) return null;
  const [y, m] = s.split("-").map(Number);
  if (!y || !m) return null;
  return { year: y, month: m };
}

function toYm(year: number, month: number): string {
  return `${String(year).padStart(4, "0")}-${String(month).padStart(2, "0")}`;
}

/**
 * Chọn Tháng/Năm tự dựng (bổ sung ngoài SDD gốc, xác nhận 2026-08-20) — thay `<input type="month">`
 * gốc vì cùng lý do đã xử lý ở Time24Input: định dạng hiển thị của input đó do Region hệ điều hành
 * quyết định (VD "August 2026" trên máy Region tiếng Anh), không phải trang web. Mirror đúng pattern
 * DatePicker.tsx (Portal + dropdown Tháng/Năm) nhưng bỏ lưới ngày — chỉ chọn tới cấp tháng.
 */
export default function MonthPicker({ value, onChange, min, max, placeholder, disabled, className }: MonthPickerProps) {
  const [open, setOpen] = useState(false);
  const selected = parseYm(value);
  const minYm = parseYm(min ?? "");
  const maxYm = parseYm(max ?? "");
  const now = new Date();
  const [viewYear, setViewYear] = useState(selected?.year ?? now.getFullYear());
  const [rect, setRect] = useState<{ top: number; left: number; width: number } | null>(null);
  const triggerRef = useRef<HTMLButtonElement>(null);
  const panelRef = useRef<HTMLDivElement>(null);

  const updateRect = () => {
    const el = triggerRef.current;
    if (!el) return;
    const r = el.getBoundingClientRect();
    setRect({ top: r.bottom + 4, left: r.left, width: r.width });
  };

  useEffect(() => {
    if (open) setViewYear(selected?.year ?? now.getFullYear());
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open]);

  useLayoutEffect(() => {
    if (open) updateRect();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open]);

  useEffect(() => {
    if (!open) return;
    const onClickOutside = (e: MouseEvent) => {
      if (triggerRef.current?.contains(e.target as Node)) return;
      if (panelRef.current?.contains(e.target as Node)) return;
      setOpen(false);
    };
    const onKeyDown = (e: KeyboardEvent) => {
      if (e.key === "Escape") setOpen(false);
    };
    const onReposition = () => updateRect();
    document.addEventListener("mousedown", onClickOutside);
    document.addEventListener("keydown", onKeyDown);
    window.addEventListener("scroll", onReposition, true);
    window.addEventListener("resize", onReposition);
    return () => {
      document.removeEventListener("mousedown", onClickOutside);
      document.removeEventListener("keydown", onKeyDown);
      window.removeEventListener("scroll", onReposition, true);
      window.removeEventListener("resize", onReposition);
    };
  }, [open]);

  const yearRange = useMemo(() => {
    const nowYear = now.getFullYear();
    const lo = minYm ? minYm.year : nowYear - 15;
    const hi = maxYm ? maxYm.year : nowYear + 5;
    const years: number[] = [];
    for (let y = hi; y >= lo; y--) years.push(y);
    return years;
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [minYm, maxYm]);

  const isDisabled = (year: number, month: number) => {
    if (minYm && (year < minYm.year || (year === minYm.year && month < minYm.month))) return true;
    if (maxYm && (year > maxYm.year || (year === maxYm.year && month > maxYm.month))) return true;
    return false;
  };

  const pick = (month: number) => {
    if (isDisabled(viewYear, month)) return;
    onChange(toYm(viewYear, month));
    setOpen(false);
  };

  const display = selected ? `Tháng ${selected.month}/${selected.year}` : placeholder ?? "-- Chọn tháng --";

  return (
    <>
      <button
        ref={triggerRef}
        type="button"
        disabled={disabled}
        onClick={() => setOpen((v) => !v)}
        className={cn(
          "w-full bg-slate-50 border border-slate-200 hover:border-slate-300 text-xs p-2.5 rounded-lg focus:outline-none flex items-center justify-between gap-2 disabled:opacity-50",
          className
        )}
      >
        <span className={selected ? "text-slate-800 font-medium" : "text-slate-400"}>{display}</span>
        <CalendarDays className="w-3.5 h-3.5 text-slate-400 shrink-0" />
      </button>

      {open &&
        rect &&
        createPortal(
          <div
            ref={panelRef}
            style={{ position: "fixed", top: rect.top, left: rect.left, width: Math.max(rect.width, 260) }}
            className="z-[200] bg-white border border-slate-200 rounded-xl shadow-xl p-3 space-y-2.5 animate-in fade-in slide-in-from-top-1 duration-150"
          >
            <div className="flex items-center gap-1.5">
              <button
                type="button"
                onClick={() => setViewYear((y) => y - 1)}
                className="p-1.5 rounded-lg text-slate-400 hover:text-slate-700 hover:bg-slate-100 transition-colors shrink-0"
              >
                <ChevronLeft className="w-3.5 h-3.5" />
              </button>
              <select
                value={viewYear}
                onChange={(e) => setViewYear(Number(e.target.value))}
                className="flex-1 bg-slate-50 border border-slate-200 text-xs font-bold text-brand-red px-2 py-1.5 rounded-lg focus:outline-none text-center"
              >
                {yearRange.map((y) => (
                  <option key={y} value={y}>
                    {y}
                  </option>
                ))}
              </select>
              <button
                type="button"
                onClick={() => setViewYear((y) => y + 1)}
                className="p-1.5 rounded-lg text-slate-400 hover:text-slate-700 hover:bg-slate-100 transition-colors shrink-0"
              >
                <ChevronRight className="w-3.5 h-3.5" />
              </button>
            </div>

            <div className="grid grid-cols-4 gap-1">
              {MONTH_LABELS.map((label, i) => {
                const month = i + 1;
                const isSelected = !!selected && selected.year === viewYear && selected.month === month;
                const disabledCell = isDisabled(viewYear, month);
                return (
                  <button
                    key={label}
                    type="button"
                    disabled={disabledCell}
                    onClick={() => pick(month)}
                    className={cn(
                      "text-[11px] font-semibold rounded-lg py-2 transition-colors",
                      !isSelected && "text-slate-700 hover:bg-orange-50",
                      isSelected && "bg-brand-red text-white shadow-sm hover:bg-brand-red",
                      disabledCell && "opacity-30 cursor-not-allowed hover:bg-transparent"
                    )}
                  >
                    T{month}
                  </button>
                );
              })}
            </div>

            <div className="flex items-center justify-between border-t border-slate-100 pt-2">
              <button
                type="button"
                onClick={() => {
                  onChange("");
                  setOpen(false);
                }}
                className="text-[11px] font-semibold text-slate-400 hover:text-rose-600 transition-colors"
              >
                Xoá
              </button>
              <button
                type="button"
                onClick={() => {
                  setViewYear(now.getFullYear());
                  pick(now.getMonth() + 1);
                }}
                className="text-[11px] font-bold text-brand-red hover:underline"
              >
                Tháng này
              </button>
            </div>
          </div>,
          document.body
        )}
    </>
  );
}
