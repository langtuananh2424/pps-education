import { useEffect, useMemo, useRef, useState } from "react";
import { DayPart, dayPartLabels, dayPartOrder, listSitePeriodTemplates, SitePeriodTemplateResponse } from "@/features/facility/api";
import { cn } from "@/lib/cn";

interface PeriodMultiSelectProps {
  siteId: number;
  label?: string;
  required?: boolean;
  dayPart: DayPart;
  onDayPartChange: (dayPart: DayPart) => void;
  selected: Set<number>;
  onChange: (selected: Set<number>) => void;
}

/**
 * Chọn buổi (Sáng/Chiều/Tối) + 1 hoặc nhiều "tiết" TRONG buổi đó, theo
 * cấu hình site_period_templates của điểm trường — thay thế nhập
 * startTime/endTime tự do (đảo ngược 2026-08-13, xác nhận lại
 * 2026-08-19). Mỗi buổi đánh số tiết riêng (V129, xác nhận 2026-08-20) —
 * đổi buổi sẽ tự xoá tiết đang chọn (không còn khớp buổi mới). Dùng chung
 * cho UC-48/56 và các form xếp lịch mới (CreateSessionModal/SessionEditModal).
 */
export default function PeriodMultiSelect({
  siteId,
  label = "Chọn tiết",
  required,
  dayPart,
  onDayPartChange,
  selected,
  onChange
}: PeriodMultiSelectProps) {
  const [periods, setPeriods] = useState<SitePeriodTemplateResponse[]>([]);
  const [loading, setLoading] = useState(true);
  // Nhớ lại lựa chọn tiết đã chọn ở mỗi buổi trong phiên chỉnh sửa hiện tại
  // (bổ sung ngoài SDD gốc, xác nhận 2026-08-20) — đổi qua lại Sáng/Chiều/Tối
  // không được xoá mất tiết đã chọn ở buổi trước đó, kể cả khi buổi đó chưa
  // lưu; chỉ Set rỗng thật sự (chưa từng chọn tiết nào ở buổi đó).
  const selectionsByDayPart = useRef(new Map<DayPart, Set<number>>());

  useEffect(() => {
    setLoading(true);
    listSitePeriodTemplates(siteId)
      .then(setPeriods)
      .finally(() => setLoading(false));
  }, [siteId]);

  const availableDayParts = useMemo(() => new Set(periods.map((p) => p.dayPart)), [periods]);
  const periodsInDayPart = useMemo(() => periods.filter((p) => p.dayPart === dayPart), [periods, dayPart]);

  const toggle = (periodNumber: number) => {
    const next = new Set(selected);
    if (next.has(periodNumber)) next.delete(periodNumber);
    else next.add(periodNumber);
    onChange(next);
  };

  const switchDayPart = (dp: DayPart) => {
    if (dp === dayPart) return;
    selectionsByDayPart.current.set(dayPart, selected);
    onDayPartChange(dp);
    onChange(selectionsByDayPart.current.get(dp) ?? new Set());
  };

  return (
    <div>
      <label className="text-[10px] uppercase font-bold text-slate-500 block mb-1">
        {label} {required && "*"}
      </label>
      {loading ? (
        <p className="text-[11px] text-slate-400 italic">Đang tải tiết học...</p>
      ) : periods.length === 0 ? (
        <p className="text-[11px] text-rose-500 italic">
          Điểm trường này chưa cấu hình tiết học — vào Cơ sở vật chất &amp; Đối tác &gt; Điểm trường &gt; tab "Tiết học" để thêm.
        </p>
      ) : (
        <div className="space-y-2">
          <div className="flex items-center gap-1 bg-slate-100 border border-slate-200 rounded-xl p-1 w-fit">
            {dayPartOrder
              .filter((dp) => availableDayParts.has(dp))
              .map((dp) => (
                <button
                  key={dp}
                  type="button"
                  onClick={() => switchDayPart(dp)}
                  className={cn(
                    "px-3 py-1.5 rounded-lg text-[11px] font-bold transition-colors",
                    dayPart === dp ? "bg-white text-brand-red shadow-xs" : "text-slate-500 hover:text-slate-700"
                  )}
                >
                  {dayPartLabels[dp]}
                </button>
              ))}
          </div>
          {periodsInDayPart.length === 0 ? (
            <p className="text-[11px] text-rose-500 italic">Điểm trường chưa cấu hình tiết cho buổi {dayPartLabels[dayPart]}.</p>
          ) : (
            <div className="flex flex-wrap gap-1.5">
              {periodsInDayPart.map((p) => (
                <button
                  key={p.id}
                  type="button"
                  onClick={() => toggle(p.periodNumber)}
                  className={cn(
                    "text-[11px] font-bold px-2.5 py-1.5 rounded-lg border transition-all",
                    selected.has(p.periodNumber) ? "bg-purple-600 border-purple-600 text-white" : "bg-white border-slate-200 text-slate-600 hover:bg-slate-50"
                  )}
                >
                  {p.label ?? `Tiết ${p.periodNumber}`} ({p.startTime.slice(0, 5)}–{p.endTime.slice(0, 5)})
                </button>
              ))}
            </div>
          )}
        </div>
      )}
    </div>
  );
}
