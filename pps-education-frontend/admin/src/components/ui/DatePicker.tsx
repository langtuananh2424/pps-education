import React, { useEffect, useLayoutEffect, useMemo, useRef, useState } from "react";
import { createPortal } from "react-dom";
import { CalendarDays, ChevronLeft, ChevronRight } from "lucide-react";
import { cn } from "@/lib/cn";

interface DatePickerProps {
  value: string; // "YYYY-MM-DD" hoặc ""
  onChange: (value: string) => void;
  min?: string; // "YYYY-MM-DD"
  max?: string; // "YYYY-MM-DD"
  placeholder?: string;
  hasError?: boolean;
  disabled?: boolean;
  className?: string;
}

const WEEKDAYS = ["CN", "T2", "T3", "T4", "T5", "T6", "T7"];
const MONTH_LABELS = Array.from({ length: 12 }, (_, i) => `Tháng ${i + 1}`);

function parseIso(s: string): Date | null {
  if (!s) return null;
  const [y, m, d] = s.split("-").map(Number);
  if (!y || !m || !d) return null;
  return new Date(y, m - 1, d);
}

function toIso(date: Date): string {
  const y = date.getFullYear();
  const m = String(date.getMonth() + 1).padStart(2, "0");
  const d = String(date.getDate()).padStart(2, "0");
  return `${y}-${m}-${d}`;
}

function formatDisplay(s: string): string {
  const date = parseIso(s);
  if (!date) return "";
  return `${String(date.getDate()).padStart(2, "0")}/${String(date.getMonth() + 1).padStart(2, "0")}/${date.getFullYear()}`;
}

/**
 * Lịch chọn ngày tự dựng (thay input[type=date] gốc) — trình duyệt không cho CSS style lại popup lịch
 * gốc, nên cần component riêng để đồng bộ giao diện + cho phép nhảy nhanh tới năm bất kỳ (không phải
 * bấm lùi từng tháng).
 *
 * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-06 — panel lịch trước đây `position:
 * absolute` bên trong DOM cha, bị cắt mất khi đặt trong khối có `overflow-hidden` (VD Card ở
 * HomeworkStatsPage.tsx). Render qua Portal vào document.body (mirror Select.tsx — cùng vấn đề dropdown
 * bị overflow cha cắt), `position: fixed` theo toạ độ nút bấm, không còn bị cắt bởi bất kỳ cha nào.
 */
export default function DatePicker({ value, onChange, min, max, placeholder, hasError, disabled, className }: DatePickerProps) {
  const [open, setOpen] = useState(false);
  const selected = parseIso(value);
  const minDate = parseIso(min ?? "");
  const maxDate = parseIso(max ?? "");
  const [viewDate, setViewDate] = useState<Date>(() => selected ?? maxDate ?? new Date());
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
    if (open) setViewDate(selected ?? maxDate ?? new Date());
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
    const nowYear = new Date().getFullYear();
    const lo = minDate ? minDate.getFullYear() : nowYear - 100;
    const hi = maxDate ? maxDate.getFullYear() : nowYear + 10;
    const years: number[] = [];
    for (let y = hi; y >= lo; y--) years.push(y);
    return years;
  }, [minDate, maxDate]);

  const isDisabled = (date: Date) => !!(minDate && date < minDate) || !!(maxDate && date > maxDate);

  const weeks = useMemo(() => {
    const year = viewDate.getFullYear();
    const month = viewDate.getMonth();
    const firstOfMonth = new Date(year, month, 1);
    const startOffset = firstOfMonth.getDay();
    const gridStart = new Date(year, month, 1 - startOffset);
    const cells: Date[] = Array.from({ length: 42 }, (_, i) => new Date(gridStart.getFullYear(), gridStart.getMonth(), gridStart.getDate() + i));
    const result: Date[][] = [];
    for (let i = 0; i < cells.length; i += 7) result.push(cells.slice(i, i + 7));
    return result;
  }, [viewDate]);

  const pick = (date: Date) => {
    if (isDisabled(date)) return;
    onChange(toIso(date));
    setOpen(false);
  };

  return (
    <>
      <button
        ref={triggerRef}
        type="button"
        disabled={disabled}
        onClick={() => setOpen((v) => !v)}
        className={cn(
          "w-full bg-slate-50 border text-xs p-2.5 rounded-lg focus:outline-none flex items-center justify-between gap-2 disabled:opacity-50",
          hasError ? "border-rose-400 focus:ring-1 focus:ring-rose-300" : "border-slate-200 hover:border-slate-300",
          className
        )}
      >
        <span className={value ? "text-slate-800 font-medium" : "text-slate-400"}>{value ? formatDisplay(value) : placeholder ?? "-- Chọn ngày --"}</span>
        <CalendarDays className="w-3.5 h-3.5 text-slate-400 shrink-0" />
      </button>

      {open &&
        rect &&
        createPortal(
          <div
            ref={panelRef}
            style={{ position: "fixed", top: rect.top, left: rect.left, width: Math.max(rect.width, 288) }}
            className="z-[200] bg-white border border-slate-200 rounded-xl shadow-xl p-3 space-y-2.5 animate-in fade-in slide-in-from-top-1 duration-150"
          >
          <div className="flex items-center gap-1.5">
            <button
              type="button"
              onClick={() => setViewDate(new Date(viewDate.getFullYear(), viewDate.getMonth() - 1, 1))}
              className="p-1.5 rounded-lg text-slate-400 hover:text-slate-700 hover:bg-slate-100 transition-colors shrink-0"
            >
              <ChevronLeft className="w-3.5 h-3.5" />
            </button>
            <select
              value={viewDate.getMonth()}
              onChange={(e) => setViewDate(new Date(viewDate.getFullYear(), Number(e.target.value), 1))}
              className="flex-1 bg-slate-50 border border-slate-200 text-xs font-semibold text-slate-700 px-2 py-1.5 rounded-lg focus:outline-none"
            >
              {MONTH_LABELS.map((label, i) => (
                <option key={label} value={i}>
                  {label}
                </option>
              ))}
            </select>
            <select
              value={viewDate.getFullYear()}
              onChange={(e) => setViewDate(new Date(Number(e.target.value), viewDate.getMonth(), 1))}
              className="bg-slate-50 border border-slate-200 text-xs font-bold text-brand-red px-2 py-1.5 rounded-lg focus:outline-none"
            >
              {yearRange.map((y) => (
                <option key={y} value={y}>
                  {y}
                </option>
              ))}
            </select>
            <button
              type="button"
              onClick={() => setViewDate(new Date(viewDate.getFullYear(), viewDate.getMonth() + 1, 1))}
              className="p-1.5 rounded-lg text-slate-400 hover:text-slate-700 hover:bg-slate-100 transition-colors shrink-0"
            >
              <ChevronRight className="w-3.5 h-3.5" />
            </button>
          </div>

          <div className="grid grid-cols-7 gap-0.5 text-center">
            {WEEKDAYS.map((w) => (
              <span key={w} className="text-[10px] font-bold text-slate-400 py-1">
                {w}
              </span>
            ))}
            {weeks.map((week, wi) =>
              week.map((date, di) => {
                const inMonth = date.getMonth() === viewDate.getMonth();
                const isSelected = !!selected && toIso(date) === toIso(selected);
                const isToday = toIso(date) === toIso(new Date());
                const disabledCell = isDisabled(date);
                return (
                  <button
                    key={`${wi}-${di}`}
                    type="button"
                    disabled={disabledCell}
                    onClick={() => pick(date)}
                    className={cn(
                      "text-[11px] font-semibold rounded-lg py-1.5 transition-colors",
                      !inMonth && "text-slate-300",
                      inMonth && !isSelected && "text-slate-700 hover:bg-orange-50",
                      isSelected && "bg-brand-red text-white shadow-sm hover:bg-brand-red",
                      !isSelected && isToday && inMonth && "ring-1 ring-brand-red/40",
                      disabledCell && "opacity-30 cursor-not-allowed hover:bg-transparent"
                    )}
                  >
                    {date.getDate()}
                  </button>
                );
              })
            )}
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
              onClick={() => pick(new Date())}
              className="text-[11px] font-bold text-brand-red hover:underline"
            >
              Hôm nay
            </button>
          </div>
        </div>,
        document.body
      )}
    </>
  );
}
