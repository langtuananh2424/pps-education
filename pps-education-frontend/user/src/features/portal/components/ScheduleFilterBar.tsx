import React, { useEffect, useRef, useState } from "react";
import { Calendar as CalendarIcon, Check, ChevronDown, RotateCcw } from "lucide-react";
import { AcademicTermResponse } from "../api";
import DateRangePicker from "./DateRangePicker";

interface ScheduleFilterBarProps {
  fromDate: string;
  toDate: string;
  onDateRangeChange: (fromDate: string, toDate: string) => void;
  terms: AcademicTermResponse[];
  selectedTermId: number | null;
  onSelectTerm: (termId: number | null) => void;
  onReset: () => void;
  isActive: boolean;
}

/**
 * Thanh lọc dùng chung ScheduleTab (Phụ huynh)/StudentScheduleTab (Học sinh) — đặt ngang hàng với
 * tiêu đề "Lịch buổi học" (theo yêu cầu người dùng 2026-08-12, tránh chiếm hẳn 1 hàng riêng phía
 * trên gây vỡ bố cục). Dropdown "Học kỳ" tự dựng compact cùng kiểu nút với DateRangePicker (không
 * dùng PortalDropdown — component đó thiết kế cho sidebar, cao hơn nhiều, lệch hàng khi đặt cạnh
 * DateRangePicker trong 1 header row).
 */
export default function ScheduleFilterBar({
  fromDate,
  toDate,
  onDateRangeChange,
  terms,
  selectedTermId,
  onSelectTerm,
  onReset,
  isActive
}: ScheduleFilterBarProps) {
  const [termOpen, setTermOpen] = useState(false);
  const termRef = useRef<HTMLDivElement>(null);
  const selectedTerm = terms.find((t) => t.id === selectedTermId) ?? null;

  useEffect(() => {
    if (!termOpen) return;
    const handleClickOutside = (e: MouseEvent) => {
      if (termRef.current && !termRef.current.contains(e.target as Node)) setTermOpen(false);
    };
    document.addEventListener("mousedown", handleClickOutside);
    return () => document.removeEventListener("mousedown", handleClickOutside);
  }, [termOpen]);

  return (
    <div className="flex flex-wrap items-center gap-2">
      {terms.length > 0 && (
        <div className="relative" ref={termRef}>
          <button
            type="button"
            onClick={() => setTermOpen((v) => !v)}
            aria-label="Lọc theo học kỳ"
            aria-haspopup="dialog"
            aria-expanded={termOpen}
            className="flex items-center gap-2 min-h-[44px] bg-white border border-line rounded-xl pl-3.5 pr-3 py-2.5 text-xs font-bold text-ink focus:outline-none focus:ring-2 focus:ring-teal/50 shadow-sm cursor-pointer"
          >
            <CalendarIcon size={14} className="text-teal shrink-0" aria-hidden="true" />
            <span className={`whitespace-nowrap ${selectedTerm ? "text-ink" : "text-muted"}`}>{selectedTerm ? selectedTerm.name : "Học kỳ"}</span>
            <ChevronDown size={14} className={`text-muted shrink-0 transition-transform ${termOpen ? "rotate-180" : ""}`} aria-hidden="true" />
          </button>

          {termOpen && (
            <div
              role="dialog"
              aria-label="Chọn học kỳ"
              className="absolute right-0 top-full mt-2 z-30 w-64 bg-white border border-line rounded-2xl shadow-lg p-1.5 space-y-0.5"
            >
              {terms.map((t) => (
                <button
                  key={t.id}
                  type="button"
                  onClick={() => {
                    onSelectTerm(t.id);
                    setTermOpen(false);
                  }}
                  className={`w-full flex items-center justify-between gap-2 px-3 py-2 rounded-xl text-xs font-bold text-left transition-colors ${
                    t.id === selectedTermId ? "bg-teal/10 text-teal-deep font-extrabold" : "text-ink hover:bg-sky-2"
                  }`}
                >
                  <span className="truncate">
                    {t.name} <span className="text-[10px] text-muted font-bold">({t.startDate} – {t.endDate})</span>
                  </span>
                  {t.id === selectedTermId && <Check size={14} className="text-teal-deep shrink-0" />}
                </button>
              ))}
            </div>
          )}
        </div>
      )}

      <DateRangePicker fromDate={fromDate} toDate={toDate} onChange={onDateRangeChange} label="Lọc theo khoảng thời gian" />

      {isActive && (
        <button type="button" onClick={onReset} className="flex items-center gap-1 text-xs font-extrabold text-teal-deep hover:underline shrink-0">
          <RotateCcw size={13} /> Xem tất cả
        </button>
      )}
    </div>
  );
}
