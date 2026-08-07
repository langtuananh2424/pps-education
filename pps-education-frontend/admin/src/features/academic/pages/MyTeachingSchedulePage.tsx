import React, { useEffect, useState } from "react";
import { CalendarDays, ChevronLeft, ChevronRight, MapPin } from "lucide-react";
import { ApiError } from "@/lib/apiClient";
import { ClassSessionResponse, getMyTeachingSchedule } from "../api";

const weekdayLabels = ["Chủ nhật", "Thứ 2", "Thứ 3", "Thứ 4", "Thứ 5", "Thứ 6", "Thứ 7"];

function toISODate(date: Date): string {
  return date.toISOString().slice(0, 10);
}

function getWeekDates(refDate: Date): Date[] {
  const day = refDate.getDay();
  const diff = refDate.getDate() - day + (day === 0 ? -6 : 1);
  const monday = new Date(refDate);
  monday.setDate(diff);
  return Array.from({ length: 7 }, (_, i) => {
    const d = new Date(monday);
    d.setDate(monday.getDate() + i);
    return d;
  });
}

/** UC-58: Xem lịch dạy tổng hợp ("Lịch của tôi") — self-service, chỉ hiện buổi dạy của chính tài khoản Giáo viên đang đăng nhập. */
export default function MyTeachingSchedulePage() {
  const [weekRef, setWeekRef] = useState(new Date());
  const [sessions, setSessions] = useState<ClassSessionResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const weekDates = getWeekDates(weekRef);

  useEffect(() => {
    setLoading(true);
    setError(null);
    getMyTeachingSchedule(toISODate(weekDates[0]), toISODate(weekDates[6]))
      .then(setSessions)
      .catch((err) => setError(err instanceof ApiError ? err.message : "Không tải được lịch dạy."))
      .finally(() => setLoading(false));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [toISODate(weekDates[0])]);

  const navigateWeek = (direction: "prev" | "next") => {
    const next = new Date(weekRef);
    next.setDate(weekRef.getDate() + (direction === "prev" ? -7 : 7));
    setWeekRef(next);
  };

  return (
    <div className="space-y-6">
      <div className="border-b border-slate-200 pb-4">
        <h1 className="text-xl font-bold font-display tracking-tight text-slate-900">Lịch của tôi</h1>
        <p className="text-sm text-slate-500 mt-1">Tổng hợp mọi buổi dạy của bạn qua tất cả các lớp đang phụ trách.</p>
      </div>

      <div className="bg-slate-50 border border-slate-200 p-4 rounded-2xl flex items-center justify-between gap-4">
        <button onClick={() => navigateWeek("prev")} className="p-2 bg-white border border-slate-200 hover:bg-slate-100 rounded-xl">
          <ChevronLeft className="w-4 h-4 text-slate-600" />
        </button>
        <span className="text-sm font-bold text-slate-800 min-w-[180px] text-center bg-white border border-slate-200 px-3 py-2 rounded-xl">
          Tuần: {weekDates[0].toLocaleDateString("vi-VN", { day: "2-digit", month: "2-digit" })} —{" "}
          {weekDates[6].toLocaleDateString("vi-VN", { day: "2-digit", month: "2-digit", year: "numeric" })}
        </span>
        <button onClick={() => navigateWeek("next")} className="p-2 bg-white border border-slate-200 hover:bg-slate-100 rounded-xl">
          <ChevronRight className="w-4 h-4 text-slate-600" />
        </button>
      </div>

      {error && <div className="text-sm text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg">{error}</div>}

      <div className="bg-white border border-slate-200 rounded-2xl p-5 shadow-soft">
        {loading ? (
          <p className="text-sm text-slate-500 text-center py-8">Đang tải...</p>
        ) : (
          <div className="grid grid-cols-1 md:grid-cols-7 gap-3">
            {weekDates.map((dateObj, idx) => {
              const dateStr = toISODate(dateObj);
              const daySessions = sessions.filter((s) => s.sessionDate === dateStr);
              const isToday = toISODate(new Date()) === dateStr;

              return (
                <div
                  key={idx}
                  className={`border rounded-xl p-3 space-y-2 min-h-[180px] flex flex-col justify-between ${
                    isToday ? "bg-orange-50/40 border-brand-red/40" : "bg-slate-50/50 border-slate-200/80"
                  }`}
                >
                  <div>
                    <div className="flex items-center justify-between border-b border-slate-150 pb-1.5 mb-2">
                      <span className="text-sm font-bold text-slate-800">{weekdayLabels[dateObj.getDay()]}</span>
                      <span className={`text-sm font-mono font-bold ${isToday ? "text-brand-red" : "text-slate-400"}`}>
                        {dateObj.getDate()}/{dateObj.getMonth() + 1}
                      </span>
                    </div>
                    <div className="space-y-2">
                      {daySessions.length === 0 ? (
                        <p className="py-6 text-center text-sm text-slate-400 italic">Trống lịch dạy</p>
                      ) : (
                        daySessions.map((s) => (
                          <div key={s.id} className="bg-white border border-slate-150 rounded-lg p-2 space-y-1">
                            <div className="flex items-center justify-between">
                              <span className="text-[9px] text-slate-400 font-mono">{s.startTime}–{s.endTime}</span>
                              {s.status !== "SCHEDULED" && <span className="text-[9px] text-rose-500 font-bold">{s.status}</span>}
                            </div>
                            <p className="text-sm font-bold text-slate-800 flex items-center gap-0.5">
                              <MapPin className="w-3 h-3 text-slate-400 shrink-0" />
                              <span className="truncate">{s.roomName ?? "Chưa gán phòng"}</span>
                            </p>
                          </div>
                        ))
                      )}
                    </div>
                  </div>
                  {daySessions.length > 0 && (
                    <span className="text-[9px] bg-brand-red/10 text-brand-red px-1.5 py-0.5 rounded-md font-bold text-center uppercase flex items-center justify-center gap-1">
                      <CalendarDays className="w-3 h-3" />
                      {daySessions.length} ca dạy
                    </span>
                  )}
                </div>
              );
            })}
          </div>
        )}
      </div>
    </div>
  );
}
