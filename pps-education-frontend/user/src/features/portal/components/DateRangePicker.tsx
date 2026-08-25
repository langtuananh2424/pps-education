import React, { useEffect, useRef, useState } from "react";
import { ChevronDown, ChevronLeft, ChevronRight, Clock } from "lucide-react";
import { useTranslation } from "react-i18next";
import { toLocaleTag } from "@/lib/format";

const pad2 = (n: number) => String(n).padStart(2, "0");

export const formatDateVN = (dateStr: string) => {
  const [y, m, d] = dateStr.split("-");
  return `${d}/${m}/${y}`;
};

const buildMonthCells = (year: number, month: number): (number | null)[] => {
  const firstWeekday = (new Date(year, month, 1).getDay() + 6) % 7;
  const daysInMonth = new Date(year, month + 1, 0).getDate();
  return [...Array.from({ length: firstWeekday }, () => null), ...Array.from({ length: daysInMonth }, (_, i) => i + 1)];
};
const dateStrForYM = (year: number, month: number, day: number) => `${year}-${pad2(month + 1)}-${pad2(day)}`;

interface RangeCalendarBodyProps {
  fromDate: string;
  toDate: string;
  onChange: (fromDate: string, toDate: string) => void;
  onDone: () => void;
}

/**
 * Chỉ phần nội dung lịch (không kèm nút trigger/popup wrapper) — tách riêng để dùng lại được ở nhiều
 * chỗ khác nhau (VD panel gộp Học kỳ/Từ-Đến trên mobile ở ScheduleFilterBar, giống cách
 * DailyLearningProgressTab.tsx tách renderRangePickerBody, đã xác nhận với người dùng 2026-08-12).
 * Tự giữ viewMonth riêng, reset về tháng của fromDate/toDate mỗi lần MOUNT (component cha chỉ render
 * khi popup đang mở nên tự nhiên remount mỗi lần mở lại).
 */
export function RangeCalendarBody({ fromDate, toDate, onChange, onDone }: RangeCalendarBodyProps) {
  const { t, i18n } = useTranslation("portal-schedule");
  const weekdayLabels = t("dateRangePicker.weekdays", { returnObjects: true }) as string[];
  const [viewMonth, setViewMonth] = useState<Date>(() => new Date(fromDate || toDate || Date.now()));

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
    <>
      <p className="text-[11px] font-bold text-muted">
        {!fromDate
          ? t("dateRangePicker.selectStartDate")
          : !toDate
            ? t("dateRangePicker.selectEndDate")
            : `${formatDateVN(fromDate)} → ${formatDateVN(toDate)}`}
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
                  aria-label={t("dateRangePicker.prevMonth")}
                  className={`w-7 h-7 flex items-center justify-center rounded-lg hover:bg-sky-2 text-muted ${offset === 1 ? "invisible" : ""}`}
                >
                  <ChevronLeft size={15} aria-hidden="true" />
                </button>
                <span className="text-xs font-black text-ink capitalize">
                  {panelDate.toLocaleDateString(toLocaleTag(i18n.language), { month: "long", year: "numeric" })}
                </span>
                <button
                  type="button"
                  onClick={() => setViewMonth((prev) => new Date(prev.getFullYear(), prev.getMonth() + 1, 1))}
                  aria-label={t("dateRangePicker.nextMonth")}
                  className={`w-7 h-7 flex items-center justify-center rounded-lg hover:bg-sky-2 text-muted ${offset === 0 ? "invisible" : ""}`}
                >
                  <ChevronRight size={15} aria-hidden="true" />
                </button>
              </div>

              <div className="grid grid-cols-7 gap-1 text-center">
                {weekdayLabels.map((w) => (
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
                      aria-label={t("dateRangePicker.selectDay", { day, month: pMonth + 1 })}
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
          {t("dateRangePicker.clearFilter")}
        </button>
        <button type="button" onClick={onDone} className="px-3 py-1.5 bg-teal text-white text-[11px] font-bold rounded-lg hover:bg-teal-deep">
          {t("dateRangePicker.done")}
        </button>
      </div>
    </>
  );
}

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
 *
 * Mobile (bug 2026-08-12, đã xác nhận với người dùng GIỮ NGUYÊN 2 nút lọc
 * "Học kỳ"/"Từ→Đến" tách riêng như thiết kế gốc, KHÔNG gộp làm 1, và LUÔN
 * nằm chung 1 hàng — kể cả lúc panel đang mở): "absolute right-0" neo theo
 * nút bấm — khi nút không nằm sát mép phải màn hình (VD đứng cạnh nút "Học
 * kỳ" khác trong cùng 1 hàng), popup rộng min(92vw,580px) bị đẩy tràn ra
 * ngoài. Đã thử 2 cách trước đó (fixed giữa màn hình + lớp phủ nền; rồi
 * nút tự w-full lúc mở) nhưng người dùng muốn 2 nút LUÔN gọn, nằm chung 1
 * hàng, nhãn dài thì "..." (truncate) thay vì đẩy nút giãn ra/xuống dòng
 * (đã xác nhận 2026-08-12). Giải pháp cuối: nút giữ nguyên gọn mọi lúc
 * (nhãn "truncate" — dài thì hiện "..."); panel lịch tách RA NGOÀI wrapper
 * hẹp của nút (return 1 Fragment gồm 2 khối anh em, không lồng nhau) —
 * dưới sm panel là 1 khối "w-full" riêng, ĐỨNG SAU CẢ 2 NÚT trong dòng
 * chảy flex-wrap của ScheduleFilterBar nên tự động xuống dòng MỚI chiếm
 * trọn bề ngang (không bị ép hẹp theo bề rộng nút), đẩy nội dung bên dưới
 * xuống — không còn phụ thuộc bề rộng nút cha nữa. Từ sm trở lên vẫn dùng
 * đúng popup nổi neo theo nút như cũ (khối riêng, ẩn ở mobile).
 */
export default function DateRangePicker({ fromDate, toDate, onChange, label }: DateRangePickerProps) {
  const { t } = useTranslation("portal-schedule");
  const resolvedLabel = label ?? t("dateRangePicker.filterByDateRange");
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);
  // Panel mobile giờ là 1 khối ANH EM với nút (không lồng trong "ref" phía trên) — cần ref riêng để
  // click-outside coi bấm vào lịch (trong panel này) vẫn là "bên trong", không tự đóng ngay lập tức.
  const mobilePanelRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!open) return;
    const handleClickOutside = (e: MouseEvent) => {
      const target = e.target as Node;
      const insideButton = ref.current?.contains(target);
      const insideMobilePanel = mobilePanelRef.current?.contains(target);
      if (!insideButton && !insideMobilePanel) setOpen(false);
    };
    document.addEventListener("mousedown", handleClickOutside);
    return () => document.removeEventListener("mousedown", handleClickOutside);
  }, [open]);

  return (
    <>
      <div className="relative" ref={ref}>
        <button
          type="button"
          onClick={() => setOpen((v) => !v)}
          aria-label={resolvedLabel}
          aria-haspopup="dialog"
          aria-expanded={open}
          className="flex items-center gap-2 min-h-[44px] bg-white border border-line rounded-xl pl-3.5 pr-3 py-2.5 text-xs font-bold text-ink focus:outline-none focus:ring-2 focus:ring-teal/50 shadow-sm cursor-pointer"
        >
          <Clock size={14} className="text-teal shrink-0" aria-hidden="true" />
          <span className="max-w-[110px] min-w-0 truncate sm:max-w-none sm:whitespace-nowrap">
            <span className={fromDate ? "text-ink" : "text-muted"}>{fromDate ? formatDateVN(fromDate) : t("dateRangePicker.from")}</span>
            <span className="text-muted mx-1">→</span>
            <span className={toDate ? "text-ink" : "text-muted"}>{toDate ? formatDateVN(toDate) : t("dateRangePicker.to")}</span>
          </span>
          <ChevronDown size={14} className={`text-muted shrink-0 transition-transform ${open ? "rotate-180" : ""}`} aria-hidden="true" />
        </button>

        {/*
         * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-25 — fix bug thật: nút "Từ → Đến"
         * ở ScheduleFilterBar KHÔNG nằm sát mép phải card (đứng đầu hàng, cạnh "Học kỳ") — neo
         * "right-0" (canh mép PHẢI popup theo mép phải nút) kéo popup rộng tới 580px tràn hẳn sang
         * TRÁI, đè lên sidebar điều hướng. Đổi sang "left-0" (canh mép TRÁI popup theo mép trái nút,
         * mở rộng dần sang phải) — đúng hướng có chỗ trống thật (card "Lịch buổi học" luôn đủ rộng
         * hơn 580px ở các nơi đang dùng component này).
         */}
        {open && (
          <div
            role="dialog"
            aria-label={t("dateRangePicker.selectDateRangeDialog")}
            className="hidden sm:block absolute left-0 top-full mt-2 z-30 w-[min(92vw,580px)] bg-white border border-line rounded-2xl shadow-lg p-4 space-y-3"
          >
            <RangeCalendarBody fromDate={fromDate} toDate={toDate} onChange={onChange} onDone={() => setOpen(false)} />
          </div>
        )}
      </div>

      {/* Mobile: khối RIÊNG (anh em với nút, không lồng trong wrapper hẹp) — flex-wrap của
          ScheduleFilterBar tự đẩy nó xuống dòng mới chiếm trọn bề ngang vì w-full, không co hẹp theo
          bề rộng nút cha nữa, không ảnh hưởng tới việc 2 nút Học kỳ/Từ→Đến ở dòng trên vẫn gọn. */}
      {open && (
        <div
          ref={mobilePanelRef}
          role="dialog"
          aria-label={t("dateRangePicker.selectDateRangeDialog")}
          className="sm:hidden w-full mt-2 z-30 bg-white border border-line rounded-2xl shadow-lg p-4 space-y-3"
        >
          <RangeCalendarBody fromDate={fromDate} toDate={toDate} onChange={onChange} onDone={() => setOpen(false)} />
        </div>
      )}
    </>
  );
}
