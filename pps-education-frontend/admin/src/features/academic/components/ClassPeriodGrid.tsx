import { useEffect, useMemo, useState } from "react";
import { assignLanes, MAX_LANES } from "@/lib/timetableLanes";
import { toISODate } from "@/lib/calendarDates";
import {
  DayPart,
  dayPartLabels,
  dayPartOrder,
  listRoomsBySite,
  listSitePeriodTemplates,
  RoomResponse,
  SitePeriodTemplateResponse
} from "@/features/facility/api";
import { ClassSessionResponse, listSessionsForSiteTimetable } from "../api";
import TimetableSessionCard from "./TimetableSessionCard";
import SessionEditModal from "./SessionEditModal";

const HEADER_ROW_HEIGHT = 44;
const SECTION_ROW_HEIGHT = 24;
const PERIOD_ROW_HEIGHT = 68;

interface ClassPeriodGridProps {
  siteId: number;
  /** Danh sách ngày hiển thị theo cột (tăng dần) — cho phép tái dùng cả ở "Thời khóa biểu" (1 tuần cố định) lẫn "Lịch làm việc" (Ngày/Tuần tùy chọn). */
  dates: Date[];
  /** Tùy chọn — chỉ hiển thị buổi của 1 lớp cụ thể (khớp filter "Lớp" ở trang Lịch làm việc). Không truyền = hiện mọi lớp của site. */
  classId?: number;
}

/**
 * Lưới thời khóa biểu (hàng đầu = thứ, cột đầu = tiết) — tách khỏi
 * TimetablePage.tsx để dùng chung được ở trang "Lịch làm việc" (HRM), bổ
 * sung ngoài SDD gốc, xác nhận với người dùng 2026-08-20 (thử gộp 2 trang
 * lịch để so sánh UX, chưa xoá trang Thời khóa biểu độc lập).
 */
export default function ClassPeriodGrid({ siteId, dates, classId }: ClassPeriodGridProps) {
  const [periods, setPeriods] = useState<SitePeriodTemplateResponse[]>([]);
  const [sessions, setSessions] = useState<ClassSessionResponse[]>([]);
  const [rooms, setRooms] = useState<RoomResponse[]>([]);
  const [loading, setLoading] = useState(false);
  const [selectedSession, setSelectedSession] = useState<ClassSessionResponse | null>(null);

  const fromDate = toISODate(dates[0]);
  const toDate = toISODate(dates[dates.length - 1]);

  useEffect(() => {
    listSitePeriodTemplates(siteId).then(setPeriods).catch(() => undefined);
    listRoomsBySite(siteId).then(setRooms).catch(() => undefined);
  }, [siteId]);

  const load = () => {
    setLoading(true);
    listSessionsForSiteTimetable(siteId, fromDate, toDate)
      .then(setSessions)
      .finally(() => setLoading(false));
  };
  // eslint-disable-next-line react-hooks/exhaustive-deps
  useEffect(load, [siteId, fromDate, toDate]);

  const sections = useMemo(() => {
    let rowCursor = 2; // hàng 1 = header ngày
    return dayPartOrder
      .map((dp) => {
        const dpPeriods = periods.filter((p) => p.dayPart === dp);
        if (dpPeriods.length === 0) return null;
        const headerRow = rowCursor;
        rowCursor += 1;
        const startRow = rowCursor;
        const rowIndex = new Map<number, number>();
        dpPeriods.forEach((p) => {
          rowIndex.set(p.periodNumber, rowCursor);
          rowCursor += 1;
        });
        return { dayPart: dp, headerRow, startRow, periods: dpPeriods, rowIndex };
      })
      .filter((s): s is NonNullable<typeof s> => s !== null);
  }, [periods]);

  const rowHeights = useMemo(
    () => [HEADER_ROW_HEIGHT, ...sections.flatMap((s) => [SECTION_ROW_HEIGHT, ...s.periods.map(() => PERIOD_ROW_HEIGHT)])],
    [sections]
  );

  const sessionsByDateAndDayPart = useMemo(() => {
    const filtered = classId == null ? sessions : sessions.filter((s) => s.classId === classId);
    const map = new Map<string, ClassSessionResponse[]>();
    filtered.forEach((s) => {
      if (!s.dayPart) return;
      const key = `${s.sessionDate}:${s.dayPart}`;
      const list = map.get(key) ?? [];
      list.push(s);
      map.set(key, list);
    });
    return map;
  }, [sessions, classId]);

  if (loading && periods.length === 0) {
    return <p className="text-xs text-slate-500 text-center py-8">Đang tải...</p>;
  }
  if (periods.length === 0) {
    return (
      <p className="text-xs text-slate-400 italic text-center py-8">
        Điểm trường này chưa cấu hình tiết học — vào Cơ sở vật chất &amp; Đối tác &gt; Điểm trường &gt; tab "Tiết học" để thêm.
      </p>
    );
  }

  return (
    <div className="p-4 overflow-x-auto">
      <div
        className="grid min-w-[900px]"
        style={{
          gridTemplateColumns: `80px repeat(${dates.length}, 1fr)`,
          gridTemplateRows: rowHeights.map((h) => `${h}px`).join(" ")
        }}
      >
        <div className="border-b border-r border-slate-200 bg-slate-50" style={{ gridColumn: 1, gridRow: 1 }} />
        {dates.map((d, i) => (
          <div key={i} className="border-b border-slate-200 bg-slate-50 flex flex-col items-center justify-center" style={{ gridColumn: i + 2, gridRow: 1 }}>
            <span className="text-[11px] font-bold text-slate-700">{d.toLocaleDateString("vi-VN", { weekday: "short" })}</span>
            <span className="text-[9px] text-slate-400 font-mono">{d.getDate()}/{d.getMonth() + 1}</span>
          </div>
        ))}

        {sections.map((section) => (
          <div
            key={section.dayPart}
            className="border-b border-slate-200 bg-brand-red/5 flex items-center px-2"
            style={{ gridColumn: "1 / -1", gridRow: section.headerRow }}
          >
            <span className="text-[10px] font-bold uppercase text-brand-red">Buổi {dayPartLabels[section.dayPart]}</span>
          </div>
        ))}

        {sections.flatMap((section) =>
          section.periods.map((p) => (
            <div
              key={p.id}
              className="border-b border-r border-slate-200 bg-slate-50 flex flex-col items-center justify-center px-1"
              style={{ gridColumn: 1, gridRow: section.rowIndex.get(p.periodNumber) }}
            >
              <span className="text-[10px] font-bold text-slate-700">{p.label ?? `Tiết ${p.periodNumber}`}</span>
              <span className="text-[8px] text-slate-400 font-mono">
                {p.startTime.slice(0, 5)}–{p.endTime.slice(0, 5)}
              </span>
            </div>
          ))
        )}

        {dates.flatMap((d, dayIdx) =>
          sections.map((section) => {
            const dateStr = toISODate(d);
            const daySessions = sessionsByDateAndDayPart.get(`${dateStr}:${section.dayPart}`) ?? [];
            const laneItems = daySessions
              .filter((s) => s.periodNumbers.length > 0)
              .map((s) => ({
                id: s.id,
                startPeriod: Math.min(...s.periodNumbers),
                endPeriod: Math.max(...s.periodNumbers)
              }));
            const lanes = assignLanes(laneItems);

            return (
              <div
                key={`${dayIdx}:${section.dayPart}`}
                className="border-r border-slate-200 relative"
                style={{ gridColumn: dayIdx + 2, gridRow: `${section.startRow} / span ${section.periods.length}` }}
              >
                <div
                  className="grid absolute inset-0 gap-0.5 p-0.5"
                  style={{
                    gridTemplateColumns: `repeat(${MAX_LANES}, 1fr)`,
                    gridTemplateRows: `repeat(${section.periods.length}, ${PERIOD_ROW_HEIGHT}px)`
                  }}
                >
                  {daySessions.map((s) => {
                    if (s.periodNumbers.length === 0) return null;
                    const startPeriod = Math.min(...s.periodNumbers);
                    const endPeriod = Math.max(...s.periodNumbers);
                    const startRow = section.rowIndex.get(startPeriod);
                    const endRow = section.rowIndex.get(endPeriod);
                    if (startRow === undefined || endRow === undefined) return null;
                    const localStartRow = startRow - section.startRow;
                    const localEndRow = endRow - section.startRow;
                    const laneInfo = lanes.get(s.id);
                    return (
                      <TimetableSessionCard
                        key={s.id}
                        session={s}
                        onClick={() => setSelectedSession(s)}
                        style={{
                          gridColumn: (laneInfo?.lane ?? 0) + 1,
                          gridRow: `${localStartRow + 1} / span ${localEndRow - localStartRow + 1}`
                        }}
                      />
                    );
                  })}
                </div>
              </div>
            );
          })
        )}
      </div>

      {selectedSession && (
        <SessionEditModal
          session={selectedSession}
          siteId={siteId}
          rooms={rooms}
          onClose={() => setSelectedSession(null)}
          onChanged={() => {
            setSelectedSession(null);
            load();
          }}
        />
      )}
    </div>
  );
}
