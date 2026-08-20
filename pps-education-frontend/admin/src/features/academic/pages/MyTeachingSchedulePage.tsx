import React, { useEffect, useMemo, useState } from "react";
import { createPortal } from "react-dom";
import { Building2, CalendarDays, CalendarRange, ChevronLeft, ChevronRight, MapPin } from "lucide-react";
import { useTranslation } from "react-i18next";
import { ApiError } from "@/lib/apiClient";
import { cn } from "@/lib/cn";
import { getMonthGridDates, getWeekDates, toISODate } from "@/lib/calendarDates";
import { toLocaleTag } from "@/lib/i18nFormat";
import Badge from "@/components/ui/Badge";
import Modal from "@/components/ui/Modal";
import SessionCard from "../components/SessionCard";
import { checkInStatusLabels, checkInStatusVariants } from "../components/ClassDetailPanel";
import { ClassSessionCheckInStatusResponse, ClassSessionResponse, getMyClassSessionCheckInStatus, getMyTeachingSchedule, listClasses } from "../api";

/** Thứ tự enum DayOfWeek dùng để tra nhãn qua `enums.weekday.<value>` / `enums.weekdayShort.<value>`
 * (namespace "academic-classes") — weekdayOrderSunFirst khớp Date.getDay() (0=CN), weekdayOrderMonFirst
 * dùng cho header lưới tháng (Thứ 2 → Chủ nhật), dùng chung với BulkGenerateSessionsForm.tsx. */
const weekdayOrderSunFirst = ["SUNDAY", "MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY"] as const;
const weekdayOrderMonFirst = ["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"] as const;

/** UC-58: Xem lịch dạy tổng hợp ("Lịch của tôi") — self-service, chỉ hiện buổi dạy của chính tài khoản Giáo viên đang đăng nhập. */
export default function MyTeachingSchedulePage() {
  const { t, i18n } = useTranslation("academic-classes");
  const [viewMode, setViewMode] = useState<"week" | "month">("week");
  const [refDate, setRefDate] = useState(new Date());
  const [sessions, setSessions] = useState<ClassSessionResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [selectedDate, setSelectedDate] = useState<string | null>(null);
  const [siteNameByClassId, setSiteNameByClassId] = useState<Record<number, string>>({});
  const [hoverInfo, setHoverInfo] = useState<{ session: ClassSessionResponse; top: number; left: number } | null>(null);
  /** UC-71 (bổ sung ngoài SDD gốc, xác nhận 2026-08-18) — trạng thái nhận lớp TÍNH RA theo classSessionId. */
  const [checkInStatusBySessionId, setCheckInStatusBySessionId] = useState<Record<number, ClassSessionCheckInStatusResponse>>({});

  useEffect(() => {
    listClasses()
      .then((classes) => {
        const map: Record<number, string> = {};
        classes.forEach((c) => {
          map[c.id] = c.siteName;
        });
        setSiteNameByClassId(map);
      })
      .catch(() => {});
  }, []);

  const weekDates = useMemo(() => getWeekDates(refDate), [refDate]);
  const monthDates = useMemo(() => getMonthGridDates(refDate), [refDate]);
  const rangeDates = viewMode === "week" ? weekDates : monthDates;
  const rangeStart = toISODate(rangeDates[0]);
  const rangeEnd = toISODate(rangeDates[rangeDates.length - 1]);

  useEffect(() => {
    setLoading(true);
    setError(null);
    getMyTeachingSchedule(rangeStart, rangeEnd)
      .then(setSessions)
      .catch((err) => setError(err instanceof ApiError ? err.message : t("myTeachingSchedule.loadError")))
      .finally(() => setLoading(false));
    getMyClassSessionCheckInStatus(rangeStart, rangeEnd)
      .then((statuses) => {
        const map: Record<number, ClassSessionCheckInStatusResponse> = {};
        statuses.forEach((s) => {
          map[s.classSessionId] = s;
        });
        setCheckInStatusBySessionId(map);
      })
      .catch(() => {});
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [rangeStart, rangeEnd]);

  /** Cập nhật ngay trạng thái vừa nhận lớp thành công — khỏi phải refetch cả khoảng ngày. */
  const handleCheckedIn = (status: ClassSessionCheckInStatusResponse) => {
    setCheckInStatusBySessionId((prev) => ({ ...prev, [status.classSessionId]: status }));
  };

  const sessionsByDate = useMemo(() => {
    const map = new Map<string, ClassSessionResponse[]>();
    sessions.forEach((s) => {
      const list = map.get(s.sessionDate) ?? [];
      list.push(s);
      map.set(s.sessionDate, list);
    });
    return map;
  }, [sessions]);

  const navigate = (direction: "prev" | "next") => {
    const next = new Date(refDate);
    if (viewMode === "week") next.setDate(refDate.getDate() + (direction === "prev" ? -7 : 7));
    else next.setMonth(refDate.getMonth() + (direction === "prev" ? -1 : 1));
    setRefDate(next);
  };

  const todayStr = toISODate(new Date());
  const selectedSessions = selectedDate ? sessionsByDate.get(selectedDate) ?? [] : [];

  return (
    <div className="space-y-6">
      <div className="border-b border-slate-200 pb-4 flex items-start justify-between flex-wrap gap-3">
        <div>
          <h1 className="text-xl font-bold font-display tracking-tight text-slate-900">{t("myTeachingSchedule.title")}</h1>
          <p className="text-xs text-slate-500 mt-1">{t("myTeachingSchedule.description")}</p>
        </div>
        <div className="flex items-center gap-1 bg-slate-100 border border-slate-200 rounded-xl p-1">
          <button
            onClick={() => setViewMode("week")}
            className={cn(
              "flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-[11px] font-bold transition-colors",
              viewMode === "week" ? "bg-white text-brand-red shadow-xs" : "text-slate-500 hover:text-slate-700"
            )}
          >
            <CalendarRange className="w-3.5 h-3.5" />
            {t("myTeachingSchedule.weekButton")}
          </button>
          <button
            onClick={() => setViewMode("month")}
            className={cn(
              "flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-[11px] font-bold transition-colors",
              viewMode === "month" ? "bg-white text-brand-red shadow-xs" : "text-slate-500 hover:text-slate-700"
            )}
          >
            <CalendarDays className="w-3.5 h-3.5" />
            {t("myTeachingSchedule.monthButton")}
          </button>
        </div>
      </div>

      <div className="bg-white rounded-3xl border-2 border-slate-300 shadow-soft overflow-hidden">
        <div className="bg-brand-gradient px-5 py-4 flex items-center justify-between gap-3">
          <button
            onClick={() => navigate("prev")}
            className="p-2 rounded-xl bg-white/15 hover:bg-white/25 text-white transition-colors shrink-0"
          >
            <ChevronLeft className="w-4 h-4" />
          </button>
          <span className="text-sm font-bold font-display text-white tracking-tight text-center flex items-center gap-2">
            <CalendarDays className="w-4 h-4 text-white/80 hidden sm:inline-block" />
            {viewMode === "week"
              ? t("myTeachingSchedule.weekRange", {
                  start: weekDates[0].toLocaleDateString(toLocaleTag(i18n.language), { day: "2-digit", month: "2-digit", year: "numeric" }),
                  end: weekDates[6].toLocaleDateString(toLocaleTag(i18n.language), { day: "2-digit", month: "2-digit", year: "numeric" })
                })
              : t("myTeachingSchedule.monthRange", { month: refDate.getMonth() + 1, year: refDate.getFullYear() })}
          </span>
          <button
            onClick={() => navigate("next")}
            className="p-2 rounded-xl bg-white/15 hover:bg-white/25 text-white transition-colors shrink-0"
          >
            <ChevronRight className="w-4 h-4" />
          </button>
        </div>

        {error && <div className="m-5 mb-0 text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg">{error}</div>}

        <div className="p-5">
        {loading ? (
          <p className="text-xs text-slate-500 text-center py-8">{t("common.loading")}</p>
        ) : viewMode === "week" ? (
          <div className="grid grid-cols-1 md:grid-cols-7 gap-3">
            {weekDates.map((dateObj, idx) => {
              const dateStr = toISODate(dateObj);
              const daySessions = sessionsByDate.get(dateStr) ?? [];
              const isToday = todayStr === dateStr;

              return (
                <div
                  key={idx}
                  className={`border rounded-xl p-3 space-y-2 min-h-[180px] flex flex-col justify-between ${
                    isToday ? "bg-orange-50/40 border-brand-red/40" : "bg-slate-50/50 border-slate-200/80"
                  }`}
                >
                  <div>
                    <div className="flex items-center justify-between border-b border-slate-150 pb-1.5 mb-2">
                      <span className="text-[11px] font-bold text-slate-800">{t(`enums.weekday.${weekdayOrderSunFirst[dateObj.getDay()]}`)}</span>
                      <span className={`text-[10px] font-mono font-bold ${isToday ? "text-brand-red" : "text-slate-400"}`}>
                        {dateObj.getDate()}/{dateObj.getMonth() + 1}
                      </span>
                    </div>
                    <div className="space-y-2">
                      {daySessions.length === 0 ? (
                        <p className="py-6 text-center text-[10px] text-slate-400 italic">{t("myTeachingSchedule.emptyDay")}</p>
                      ) : (
                        daySessions.map((s) => {
                          const checkInStatus = checkInStatusBySessionId[s.id];
                          return (
                            <button
                              type="button"
                              key={s.id}
                              onClick={() => setSelectedDate(dateStr)}
                              onMouseEnter={(e) => {
                                const rect = e.currentTarget.getBoundingClientRect();
                                setHoverInfo({ session: s, top: rect.bottom + 6, left: rect.left });
                              }}
                              onMouseLeave={() => setHoverInfo(null)}
                              className="w-full text-left bg-white border border-slate-150 rounded-lg p-2 space-y-1 hover:border-brand-red/50 hover:bg-orange-50/40 transition-colors"
                            >
                              <div className="flex items-center justify-between">
                                <span className="text-[9px] text-slate-400 font-mono">{s.startTime}–{s.endTime}</span>
                              </div>
                              <p className="text-[10px] font-bold text-slate-800 flex items-center gap-0.5">
                                <MapPin className="w-3 h-3 text-slate-400 shrink-0" />
                                <span className="truncate">{s.className ?? t("myTeachingSchedule.unassignedClass")}</span>
                              </p>
                              <p className="text-[9px] text-slate-400 flex items-center gap-0.5">
                                <Building2 className="w-3 h-3 text-slate-400 shrink-0" />
                                <span className="truncate">{siteNameByClassId[s.classId] ?? t("myTeachingSchedule.loadingSite")}</span>
                              </p>
                              <div className="flex items-center justify-between gap-1 flex-wrap">
                                {s.status !== "SCHEDULED" && <span className="text-[9px] text-rose-500 font-bold">{s.status}</span>}
                                {checkInStatus && s.status !== "CANCELLED" && s.status !== "RESCHEDULED" && (
                                  <Badge variant={checkInStatusVariants[checkInStatus.effectiveStatus] ?? "neutral"} className="text-[8px]">
                                    {checkInStatusLabels[checkInStatus.effectiveStatus] ?? checkInStatus.effectiveStatus}
                                  </Badge>
                                )}
                              </div>
                            </button>
                          );
                        })
                      )}
                    </div>
                  </div>
                  {daySessions.length > 0 && (
                    <span className="text-[9px] bg-brand-red/10 text-brand-red px-1.5 py-0.5 rounded-md font-bold text-center uppercase flex items-center justify-center gap-1">
                      <CalendarDays className="w-3 h-3" />
                      {t("myTeachingSchedule.sessionCount", { count: daySessions.length })}
                    </span>
                  )}
                </div>
              );
            })}
          </div>
        ) : (
          <div className="rounded-2xl border-2 border-slate-300 overflow-hidden">
            <div className="grid grid-cols-7 bg-orange-50/70 border-b-2 border-slate-300">
              {weekdayOrderMonFirst.map((day) => (
                <div key={day} className="text-center text-[10px] font-bold text-brand-red uppercase tracking-wide py-2.5">
                  {t(`enums.weekdayShort.${day}`)}
                </div>
              ))}
            </div>
            <div className="grid grid-cols-7 divide-x divide-y divide-slate-300">
              {monthDates.map((dateObj, idx) => {
                const dateStr = toISODate(dateObj);
                const daySessions = sessionsByDate.get(dateStr) ?? [];
                const inMonth = dateObj.getMonth() === refDate.getMonth();
                const isToday = todayStr === dateStr;
                const hasSessions = daySessions.length > 0;
                const visibleSessions = daySessions.slice(0, 2);

                return (
                  <div key={idx} className="relative">
                    <button
                      type="button"
                      onClick={() => hasSessions && setSelectedDate(dateStr)}
                      className={cn(
                        "w-full min-h-[96px] p-2 flex flex-col gap-1 transition-colors text-left",
                        !inMonth && "bg-slate-50/60",
                        inMonth && hasSessions && "bg-orange-50 border-l-4 border-l-brand-red hover:bg-orange-100/80 cursor-pointer",
                        inMonth && !hasSessions && "hover:bg-slate-50"
                      )}
                    >
                      <div className="flex items-center justify-between gap-1">
                        <span
                          className={cn(
                            "w-6 h-6 flex items-center justify-center rounded-full text-[11px] font-mono font-bold shrink-0",
                            isToday
                              ? "bg-brand-gradient text-white shadow-glow"
                              : !inMonth
                              ? "text-slate-300"
                              : hasSessions
                              ? "text-brand-red"
                              : "text-slate-400"
                          )}
                        >
                          {dateObj.getDate()}
                        </span>
                        {hasSessions && (
                          <span className="text-[9px] bg-brand-gradient text-white px-1.5 py-0.5 rounded-full font-bold shadow-xs shrink-0">
                            {t("myTeachingSchedule.monthDayBadge", { count: daySessions.length })}
                          </span>
                        )}
                      </div>
                      {hasSessions && (
                        <div className="flex flex-col gap-0.5 mt-0.5">
                          {visibleSessions.map((s) => (
                            <span
                              key={s.id}
                              onMouseEnter={(e) => {
                                const rect = e.currentTarget.getBoundingClientRect();
                                setHoverInfo({ session: s, top: rect.bottom + 6, left: rect.left });
                              }}
                              onMouseLeave={() => setHoverInfo(null)}
                              className="text-[9px] font-mono font-bold text-brand-red bg-white border border-brand-red/25 rounded-md px-1 py-0.5 truncate hover:bg-brand-red/10 hover:border-brand-red/50"
                            >
                              {s.startTime}–{s.endTime}
                            </span>
                          ))}
                          {daySessions.length > visibleSessions.length && (
                            <span className="text-[9px] text-brand-red/70 font-semibold pl-0.5">
                              {t("myTeachingSchedule.moreSessions", { count: daySessions.length - visibleSessions.length })}
                            </span>
                          )}
                        </div>
                      )}
                    </button>
                  </div>
                );
              })}
            </div>
          </div>
        )}
        </div>
      </div>

      {hoverInfo &&
        createPortal(
          <div
            style={{ position: "fixed", top: hoverInfo.top, left: Math.min(hoverInfo.left, window.innerWidth - 220), zIndex: 100 }}
            className="pointer-events-none max-w-[210px] bg-slate-900 text-white text-[10px] rounded-lg px-2.5 py-1.5 shadow-xl space-y-0.5"
          >
            <p className="font-bold truncate">{hoverInfo.session.className}</p>
            <p className="text-slate-300 truncate">{siteNameByClassId[hoverInfo.session.classId] ?? t("myTeachingSchedule.loadingSite")}</p>
          </div>,
          document.body
        )}

      <Modal
        open={!!selectedDate}
        onClose={() => setSelectedDate(null)}
        title={
          selectedDate
            ? new Date(`${selectedDate}T00:00:00`).toLocaleDateString(toLocaleTag(i18n.language), {
                weekday: "long",
                day: "2-digit",
                month: "2-digit",
                year: "numeric"
              })
            : ""
        }
        description={t("myTeachingSchedule.modalDescription", { count: selectedSessions.length })}
      >
        <div className="space-y-2.5">
          {selectedSessions.map((s) => (
            <SessionCard
              key={s.id}
              session={s}
              siteName={siteNameByClassId[s.classId]}
              checkInStatus={checkInStatusBySessionId[s.id]}
              onCheckedIn={handleCheckedIn}
            />
          ))}
        </div>
      </Modal>
    </div>
  );
}
