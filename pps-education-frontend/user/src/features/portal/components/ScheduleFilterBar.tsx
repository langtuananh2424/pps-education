import React, { useEffect, useRef, useState } from "react";
import { Calendar as CalendarIcon, Check, ChevronDown } from "lucide-react";
import { useTranslation } from "react-i18next";
import { AcademicTermResponse } from "../api";
import DateRangePicker from "./DateRangePicker";

interface ScheduleFilterBarProps {
  fromDate: string;
  toDate: string;
  onDateRangeChange: (fromDate: string, toDate: string) => void;
  terms: AcademicTermResponse[];
  selectedTermId: number | null;
  onSelectTerm: (termId: number | null) => void;
}

/**
 * Thanh lọc dùng chung ScheduleTab (Phụ huynh)/StudentScheduleTab (Học sinh) — đặt ngang hàng với
 * tiêu đề "Lịch buổi học" (theo yêu cầu người dùng 2026-08-12, tránh chiếm hẳn 1 hàng riêng phía
 * trên gây vỡ bố cục). Dropdown "Học kỳ" tự dựng compact cùng kiểu nút với DateRangePicker (không
 * dùng PortalDropdown — component đó thiết kế cho sidebar, cao hơn nhiều, lệch hàng khi đặt cạnh
 * DateRangePicker trong 1 header row). Giữ 2 bộ lọc TÁCH RIÊNG (đã xác nhận với người dùng
 * 2026-08-12, không gộp thành 1 dropdown như DailyLearningProgressTab.tsx).
 *
 * Nút "Xem tất cả" (reset) KHÔNG còn render ở đây nữa — đã chuyển ra ngoài, đặt ngang hàng với tiêu
 * đề "Lịch buổi học" thay vì đứng chung hàng với 2 nút lọc (theo yêu cầu người dùng 2026-08-12, xem
 * StudentScheduleTab.tsx/ScheduleTab.tsx — cả 2 nơi gọi component này đều tự render nút đó).
 *
 * Mobile (bug 2026-08-12): 2 nút đặt cạnh nhau trong 1 hàng khiến nút không còn nằm sát mép phải màn
 * hình — popup "absolute right-0" của cả 2 (rộng tới 256-580px) bị đẩy tràn hẳn ra ngoài bên trái,
 * cắt mất chữ/lịch. DateRangePicker tự lo phần mình (xem DateRangePicker.tsx); dropdown "Học kỳ" ở
 * đây dùng cùng cách: dưới sm chuyển sang khung cố định giữa màn hình (fixed, không phụ thuộc vị trí
 * nút) + lớp phủ nền, từ sm trở lên giữ nguyên dropdown neo theo nút như cũ.
 */
export default function ScheduleFilterBar({
  fromDate,
  toDate,
  onDateRangeChange,
  terms,
  selectedTermId,
  onSelectTerm
}: ScheduleFilterBarProps) {
  const { t } = useTranslation("portal-schedule");
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
            aria-label={t("filterBar.filterByTerm")}
            aria-haspopup="dialog"
            aria-expanded={termOpen}
            className="flex items-center gap-2 min-h-[44px] bg-white border border-line rounded-xl pl-3.5 pr-3 py-2.5 text-xs font-bold text-ink focus:outline-none focus:ring-2 focus:ring-teal/50 shadow-sm cursor-pointer"
          >
            <CalendarIcon size={14} className="text-teal shrink-0" aria-hidden="true" />
            <span className={`whitespace-nowrap ${selectedTerm ? "text-ink" : "text-muted"}`}>{selectedTerm ? selectedTerm.name : t("filterBar.term")}</span>
            <ChevronDown size={14} className={`text-muted shrink-0 transition-transform ${termOpen ? "rotate-180" : ""}`} aria-hidden="true" />
          </button>

          {termOpen && (
            <>
              <div className="fixed inset-0 z-30 bg-ink/30 sm:hidden" aria-hidden="true" onClick={() => setTermOpen(false)} />
              <div
                role="dialog"
                aria-label={t("filterBar.selectTermDialog")}
                className="fixed left-4 right-4 top-1/2 -translate-y-1/2 z-30 max-h-[70vh] overflow-y-auto sm:absolute sm:left-auto sm:right-0 sm:top-full sm:translate-y-0 sm:mt-2 sm:max-h-none sm:overflow-visible sm:w-64 bg-white border border-line rounded-2xl shadow-lg p-1.5 space-y-0.5"
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
            </>
          )}
        </div>
      )}

      <DateRangePicker fromDate={fromDate} toDate={toDate} onChange={onDateRangeChange} label={t("dateRangePicker.filterByDateRange")} />
    </div>
  );
}
