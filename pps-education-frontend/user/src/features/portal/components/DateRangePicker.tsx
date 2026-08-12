import React, { useEffect, useRef, useState } from "react";
import { ChevronDown, ChevronLeft, ChevronRight, Clock } from "lucide-react";

const WEEKDAY_LABELS = ["T2", "T3", "T4", "T5", "T6", "T7", "CN"];
const pad2 = (n: number) => String(n).padStart(2, "0");

interface DateRangePickerProps {
  fromDate: string;
  toDate: string;
  onChange: (fromDate: string, toDate: string) => void;
  label?: string;
}

/**
 * Datepicker chọn khoảng ngày (2 tháng liền kề) — tách ra từ
 * DailyLearningProgressTab.tsx (nút "Từ → Đến" ở tab Quá trình học tập, đã
 * xác nhận với người dùng 2026-08-12 dùng lại đúng UI này thay cho 2 ô
 * <input type="date"> thô ban đầu). DailyLearningProgressTab.tsx giữ
 * nguyên bản gốc của nó (không đổi sang dùng component này) — chỉ trích
 * xuất để tái dùng ở nơi khác, không đụng vào chỗ đang chạy ổn định.
 */
export default function DateRangePicker({ fromDate, toDate, onChange, label = "Lọc theo khoảng thời gian" }: DateRangePickerProps) {
  const [open, setOpen] = useState(false);
  const [viewMonth, setViewMonth] = useState<Date>(() => new Date());
  const ref = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!open) return;
    const handleClickOutside = (e: MouseEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false);
    };
    document.addEventListener("mousedown", handleClickOutside);
    return () => document.removeEventListener("mousedown", handleClickOutside);
  }, [open]);

  const toggleOpen = () => {
    setOpen((v) => {
      const next = !v;
      if (next) setViewMonth(new Date(fromDate || toDate || Date.now()));
      return next;
    });
  };

  const formatDateVN = (dateStr: string) => {
    const [y, m, d] = dateStr.split("-");
    return `${d}/${m}/${y}`;
  };

  const buildMonthCells = (year: number, month: number): (number | null)[] => {
    const firstWeekday = (new Date(year, month, 1).getDay() + 6) % 7;
    const daysInMonth = new Date(year, month + 1, 0).getDate();
    return [...Array.from({ length: firstWeekday }, () => null), ...Array.from({ length: daysInMonth }, (_, i) => i + 1)];
  };
  const dateStrForYM = (year: number, month: number, day: number) => `${year}-${pad2(month + 1)}-${pad2(day)}`;

  /** Bấm 1: đặt Từ ngày. Bấm 2 (sau ngày Từ): đặt Đến ngày. Bấm khi đã đủ cặp hoặc bấm ngày trước "Từ": bắt đầu chọn lại từ đầu. */
  const handleDayClick = (dateStr: string) => {
    if (!fromDate || toDate || dateStr < fromDate) {
      onChange(dateStr, "");
    } else {
      onChange(fromDate, dateStr);
    }
  };

  const cellState = (dateStr: string): "start" | "end" | "single" | "in-range" | "none" => {
    if (fromDate === dateStr && toDate === dateStr) return "single";
    if (fromDate === dateStr) return toDate ? "start" : "single";
    if (toDate === dateStr) return "end";
    if (fromDate && toDate && dateStr > fromDate && dateStr < toDate) return "in-range";
    return "none";
  };

  return (
    <div className="relative" ref={ref}>
      <button
        type="button"
        onClick={toggleOpen}
        aria-label={label}
        aria-haspopup="dialog"
        aria-expanded={open}
        className="flex items-center gap-2 min-h-[44px] bg-white border border-line rounded-xl pl-3.5 pr-3 py-2.5 text-xs font-bold text-ink focus:outline-none focus:ring-2 focus:ring-teal/50 shadow-sm cursor-pointer"
      >
        <Clock size={14} className="text-teal shrink-0" aria-hidden="true" />
        <span className="whitespace-nowrap">
          <span className={fromDate ? "text-ink" : "text-muted"}>{fromDate ? formatDateVN(fromDate) : "Từ"}</span>
          <span className="text-muted mx-1">→</span>
          <span className={toDate ? "text-ink" : "text-muted"}>{toDate ? formatDateVN(toDate) : "Đến"}</span>
        </span>
        <ChevronDown size={14} className={`text-muted shrink-0 transition-transform ${open ? "rotate-180" : ""}`} aria-hidden="true" />
      </button>

      {open && (
        <div
          role="dialog"
          aria-label="Chọn khoảng thời gian"
          className="absolute right-0 top-full mt-2 z-30 w-[min(92vw,580px)] bg-white border border-line rounded-2xl shadow-lg p-4 space-y-3"
        >
          <p className="text-[11px] font-bold text-muted">
            {!fromDate ? "Chọn ngày bắt đầu" : !toDate ? "Chọn ngày kết thúc" : `${formatDateVN(fromDate)} → ${formatDateVN(toDate)}`}
          </p>

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            {[0, 1].map((offset) => {
              const panelDate = new Date(viewMonth.getFullYear(), viewMonth.getMonth() + offset, 1);
              const pYear = panelDate.getFullYear();
              const pMonth = panelDate.getMonth();
              const cells = buildMonthCells(pYear, pMonth);
              return (
                <div key={offset} className="space-y-2">
                  <div className="flex items-center justify-between">
                    <button
                      type="button"
                      onClick={() => setViewMonth((prev) => new Date(prev.getFullYear(), prev.getMonth() - 1, 1))}
                      aria-label="Tháng trước"
                      className={`w-7 h-7 flex items-center justify-center rounded-lg hover:bg-sky-2 text-muted ${offset === 1 ? "invisible" : ""}`}
                    >
                      <ChevronLeft size={15} aria-hidden="true" />
                    </button>
                    <span className="text-xs font-black text-ink capitalize">{panelDate.toLocaleDateString("vi-VN", { month: "long", year: "numeric" })}</span>
                    <button
                      type="button"
                      onClick={() => setViewMonth((prev) => new Date(prev.getFullYear(), prev.getMonth() + 1, 1))}
                      aria-label="Tháng sau"
                      className={`w-7 h-7 flex items-center justify-center rounded-lg hover:bg-sky-2 text-muted ${offset === 0 ? "invisible" : ""}`}
                    >
                      <ChevronRight size={15} aria-hidden="true" />
                    </button>
                  </div>

                  <div className="grid grid-cols-7 gap-1 text-center">
                    {WEEKDAY_LABELS.map((w) => (
                      <span key={w} className="text-[10px] font-bold text-muted py-1">
                        {w}
                      </span>
                    ))}
                    {cells.map((day, i) => {
                      if (day == null) return <span key={`blank-${offset}-${i}`} />;
                      const dateStr = dateStrForYM(pYear, pMonth, day);
                      const state = cellState(dateStr);
                      return (
                        <button
                          key={day}
                          type="button"
                          onClick={() => handleDayClick(dateStr)}
                          aria-label={`Chọn ngày ${day}/${pMonth + 1}`}
                          className={`relative w-8 h-8 mx-auto flex items-center justify-center text-xs font-bold transition-colors cursor-pointer ${
                            state === "start" || state === "end" || state === "single"
                              ? "bg-teal text-white rounded-full"
                              : state === "in-range"
                                ? "bg-teal/15 text-teal-deep rounded-none"
                                : "text-ink hover:bg-teal/10 rounded-full"
                          }`}
                        >
                          {day}
                        </button>
                      );
                    })}
                  </div>
                </div>
              );
            })}
          </div>

          <div className="flex items-center justify-between gap-2 pt-1 border-t border-line/60">
            <button type="button" onClick={() => onChange("", "")} className="text-[11px] font-bold text-muted hover:text-ink">
              Xóa lọc
            </button>
            <button type="button" onClick={() => setOpen(false)} className="px-3 py-1.5 bg-teal text-white text-[11px] font-bold rounded-lg hover:bg-teal-deep">
              Xong
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
