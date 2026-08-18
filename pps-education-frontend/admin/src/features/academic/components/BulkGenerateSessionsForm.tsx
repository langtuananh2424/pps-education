import React, { useEffect, useState } from "react";
import { Sparkles } from "lucide-react";
import { useTranslation } from "react-i18next";
import { ApiError } from "@/lib/apiClient";
import Button from "@/components/ui/Button";
import { RoomResponse, listRoomsBySite } from "@/features/facility/api";
import { BulkCreateClassSessionRequest, BulkCreateClassSessionResponse, bulkCreateClassSessions } from "../api";
import DatePicker from "@/components/ui/DatePicker";
import Select from "@/components/ui/Select";

const inputClass = "w-full bg-white border border-slate-200 text-xs p-2 rounded-lg focus:outline-none";
const labelClass = "text-[10px] uppercase font-bold text-slate-500 block mb-1";

/** Giá trị enum DayOfWeek theo đúng thứ tự hiển thị cũ (Thứ 2 → Chủ nhật) — nhãn dịch qua
 * `enums.weekday.<value>` (namespace "academic-classes"), dùng chung với enums.weekday ở MyTeachingSchedulePage.tsx. */
const weekdayValues = ["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"] as const;

interface BulkGenerateSessionsFormProps {
  classId: number;
  siteId: number;
  onDone: () => void;
  onCancel: () => void;
}

/** UC-56: Sinh lịch học hàng loạt theo mẫu lặp — 1 khung giờ chung áp dụng cho mọi ngày trong tuần đã tick chọn (khớp đúng BulkCreateClassSessionRequest thật, không hỗ trợ giờ riêng theo từng ngày). */
export default function BulkGenerateSessionsForm({ classId, siteId, onDone, onCancel }: BulkGenerateSessionsFormProps) {
  const { t } = useTranslation("academic-classes");
  const [rooms, setRooms] = useState<RoomResponse[]>([]);
  const [startDate, setStartDate] = useState("");
  const [endDate, setEndDate] = useState("");
  const [selectedDays, setSelectedDays] = useState<Set<string>>(new Set());
  const [startTime, setStartTime] = useState("18:00");
  const [endTime, setEndTime] = useState("19:30");
  const [roomId, setRoomId] = useState("");
  const [sessionType, setSessionType] = useState("REGULAR");
  const [teacherType, setTeacherType] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [result, setResult] = useState<BulkCreateClassSessionResponse | null>(null);

  useEffect(() => {
    listRoomsBySite(siteId).then(setRooms).catch(() => undefined);
  }, [siteId]);

  const toggleDay = (day: string) => {
    setSelectedDays((prev) => {
      const next = new Set(prev);
      if (next.has(day)) next.delete(day);
      else next.add(day);
      return next;
    });
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    if (!startDate || !endDate || selectedDays.size === 0) {
      setError(t("bulkGenerateSessions.dateAndDayRequired"));
      return;
    }
    if (!teacherType) {
      setError(t("bulkGenerateSessions.teacherTypeRequired"));
      return;
    }
    setSubmitting(true);
    try {
      const request: BulkCreateClassSessionRequest = {
        startDate,
        endDate,
        daysOfWeek: Array.from(selectedDays),
        startTime,
        endTime,
        roomId: roomId ? Number(roomId) : undefined,
        sessionType,
        teacherType: teacherType as "VIETNAMESE" | "FOREIGN"
      };
      const res = await bulkCreateClassSessions(classId, request);
      setResult(res);
      if (res.createdCount > 0) onDone();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t("bulkGenerateSessions.submitError"));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <form onSubmit={handleSubmit} className="bg-purple-50/40 border border-purple-200 rounded-xl p-4 space-y-3">
      {error && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg">{error}</div>}

      <div className="grid grid-cols-2 gap-3">
        <div>
          <label className={labelClass}>{t("bulkGenerateSessions.fromDateLabel")}</label>
          <DatePicker value={startDate} onChange={setStartDate} max={endDate || undefined} />
        </div>
        <div>
          <label className={labelClass}>{t("bulkGenerateSessions.toDateLabel")}</label>
          <DatePicker value={endDate} onChange={setEndDate} min={startDate || undefined} />
        </div>
      </div>

      <div>
        <label className={labelClass}>{t("bulkGenerateSessions.weekdaysLabel")}</label>
        <div className="flex flex-wrap gap-1.5">
          {weekdayValues.map((day) => (
            <button
              key={day}
              type="button"
              onClick={() => toggleDay(day)}
              className={`text-[11px] font-bold px-2.5 py-1.5 rounded-lg border transition-all ${
                selectedDays.has(day) ? "bg-purple-600 border-purple-600 text-white" : "bg-white border-slate-200 text-slate-600 hover:bg-slate-50"
              }`}
            >
              {t(`enums.weekday.${day}`)}
            </button>
          ))}
        </div>
      </div>

      <div className="grid grid-cols-2 gap-3">
        <div>
          <label className={labelClass}>{t("bulkGenerateSessions.startTimeLabel")}</label>
          <input required type="time" value={startTime} onChange={(e) => setStartTime(e.target.value)} className={inputClass} />
        </div>
        <div>
          <label className={labelClass}>{t("bulkGenerateSessions.endTimeLabel")}</label>
          <input required type="time" value={endTime} onChange={(e) => setEndTime(e.target.value)} className={inputClass} />
        </div>
      </div>

      <div className="grid grid-cols-2 gap-3">
        <div>
          <label className={labelClass}>{t("bulkGenerateSessions.roomLabel")}</label>
          <Select value={roomId} onChange={(e) => setRoomId(e.target.value)} className={inputClass}>
            <option value="">{t("common.noneOption")}</option>
            {rooms.map((r) => (
              <option key={r.id} value={r.id}>
                {r.code} — {r.name}
              </option>
            ))}
          </Select>
        </div>
        <div>
          <label className={labelClass}>{t("bulkGenerateSessions.sessionTypeLabel")}</label>
          <Select value={sessionType} onChange={(e) => setSessionType(e.target.value)} className={inputClass}>
            <option value="REGULAR">{t("enums.sessionType.REGULAR")}</option>
            <option value="REVIEW">{t("enums.sessionType.REVIEW")}</option>
            <option value="EXAM">{t("enums.sessionType.EXAM")}</option>
            <option value="MAKEUP">{t("enums.sessionType.MAKEUP")}</option>
          </Select>
        </div>
      </div>

      <div>
        <label className={labelClass}>{t("bulkGenerateSessions.teacherTypeLabel")}</label>
        <Select value={teacherType} onChange={(e) => setTeacherType(e.target.value)} className={inputClass}>
          <option value="">{t("common.teacherTypePlaceholder")}</option>
          <option value="VIETNAMESE">{t("enums.teacherType.VIETNAMESE")}</option>
          <option value="FOREIGN">{t("enums.teacherType.FOREIGN")}</option>
        </Select>
        {/* Bổ sung ngoài SDD gốc, xác nhận 2026-08-13: giáo viên phụ trách KHÔNG còn chọn tay — hệ
            thống tự động lấy giáo viên chính (PRIMARY) đang phụ trách lớp cùng loại giáo viên đã chọn. */}
        <p className="text-[10px] text-slate-400 italic mt-1">{t("bulkGenerateSessions.teacherAutoHint")}</p>
      </div>

      {result && (
        <div className="p-3 bg-white border border-slate-200 rounded-lg text-xs space-y-1.5">
          <p className="font-bold text-slate-700">
            {t("bulkGenerateSessions.resultSummary", { created: result.createdCount, total: result.totalDates })}
            {result.skippedCount > 0 && (
              <span className="text-rose-500">{t("bulkGenerateSessions.resultSkipped", { count: result.skippedCount })}</span>
            )}
          </p>
          {result.skipped.length > 0 && (
            <div className="space-y-0.5 max-h-24 overflow-y-auto">
              {result.skipped.map((s, i) => (
                <p key={i} className="text-[10px] text-rose-500">
                  {t("bulkGenerateSessions.skippedReason", { date: s.date, reason: s.reason })}
                </p>
              ))}
            </div>
          )}
        </div>
      )}

      <div className="flex gap-2 pt-1">
        <Button type="button" variant="secondary" size="sm" onClick={onCancel}>
          {t("common.closeButton")}
        </Button>
        <Button type="submit" variant="primary" size="sm" disabled={submitting}>
          <Sparkles className="w-3.5 h-3.5" />
          {submitting ? t("bulkGenerateSessions.submitting") : t("bulkGenerateSessions.submitButton")}
        </Button>
      </div>
    </form>
  );
}
