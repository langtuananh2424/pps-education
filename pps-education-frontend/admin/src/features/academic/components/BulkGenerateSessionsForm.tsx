import React, { useEffect, useState } from "react";
import { Search, Sparkles, X } from "lucide-react";
import { ApiError } from "@/lib/apiClient";
import Button from "@/components/ui/Button";
import { searchUsers, UserListItemResponse } from "@/features/system-admin/api";
import { RoomResponse, listRoomsBySite } from "@/features/facility/api";
import { BulkCreateClassSessionRequest, BulkCreateClassSessionResponse, bulkCreateClassSessions } from "../api";
import DatePicker from "@/components/ui/DatePicker";
import Select from "@/components/ui/Select";

const inputClass = "w-full bg-white border border-slate-200 text-xs p-2 rounded-lg focus:outline-none";
const labelClass = "text-[10px] uppercase font-bold text-slate-500 block mb-1";

const weekdays: { value: string; label: string }[] = [
  { value: "MONDAY", label: "Thứ 2" },
  { value: "TUESDAY", label: "Thứ 3" },
  { value: "WEDNESDAY", label: "Thứ 4" },
  { value: "THURSDAY", label: "Thứ 5" },
  { value: "FRIDAY", label: "Thứ 6" },
  { value: "SATURDAY", label: "Thứ 7" },
  { value: "SUNDAY", label: "Chủ nhật" }
];

interface BulkGenerateSessionsFormProps {
  classId: number;
  siteId: number;
  onDone: () => void;
  onCancel: () => void;
}

/** UC-56: Sinh lịch học hàng loạt theo mẫu lặp — 1 khung giờ chung áp dụng cho mọi ngày trong tuần đã tick chọn (khớp đúng BulkCreateClassSessionRequest thật, không hỗ trợ giờ riêng theo từng ngày). */
export default function BulkGenerateSessionsForm({ classId, siteId, onDone, onCancel }: BulkGenerateSessionsFormProps) {
  const [rooms, setRooms] = useState<RoomResponse[]>([]);
  const [startDate, setStartDate] = useState("");
  const [endDate, setEndDate] = useState("");
  const [selectedDays, setSelectedDays] = useState<Set<string>>(new Set());
  const [startTime, setStartTime] = useState("18:00");
  const [endTime, setEndTime] = useState("19:30");
  const [roomId, setRoomId] = useState("");
  const [sessionType, setSessionType] = useState("REGULAR");
  const [query, setQuery] = useState("");
  const [results, setResults] = useState<UserListItemResponse[]>([]);
  const [teacher, setTeacher] = useState<UserListItemResponse | null>(null);
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

  const handleSearch = (q: string) => {
    setQuery(q);
    if (!q.trim()) {
      setResults([]);
      return;
    }
    searchUsers({ keyword: q.trim() }, 0, 8).then((res) => setResults(res.content));
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    if (!startDate || !endDate || selectedDays.size === 0 || !teacher) {
      setError("Vui lòng chọn khoảng ngày, tối thiểu 1 ngày trong tuần, và giáo viên phụ trách.");
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
        primaryTeacherId: teacher.id,
        sessionType
      };
      const res = await bulkCreateClassSessions(classId, request);
      setResult(res);
      if (res.createdCount > 0) onDone();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Sinh lịch hàng loạt thất bại.");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <form onSubmit={handleSubmit} className="bg-purple-50/40 border border-purple-200 rounded-xl p-4 space-y-3">
      {error && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg">{error}</div>}

      <div className="grid grid-cols-2 gap-3">
        <div>
          <label className={labelClass}>Từ ngày *</label>
          <DatePicker value={startDate} onChange={setStartDate} max={endDate || undefined} />
        </div>
        <div>
          <label className={labelClass}>Đến ngày *</label>
          <DatePicker value={endDate} onChange={setEndDate} min={startDate || undefined} />
        </div>
      </div>

      <div>
        <label className={labelClass}>Các ngày trong tuần áp dụng *</label>
        <div className="flex flex-wrap gap-1.5">
          {weekdays.map((d) => (
            <button
              key={d.value}
              type="button"
              onClick={() => toggleDay(d.value)}
              className={`text-[11px] font-bold px-2.5 py-1.5 rounded-lg border transition-all ${
                selectedDays.has(d.value) ? "bg-purple-600 border-purple-600 text-white" : "bg-white border-slate-200 text-slate-600 hover:bg-slate-50"
              }`}
            >
              {d.label}
            </button>
          ))}
        </div>
      </div>

      <div className="grid grid-cols-2 gap-3">
        <div>
          <label className={labelClass}>Giờ bắt đầu *</label>
          <input required type="time" value={startTime} onChange={(e) => setStartTime(e.target.value)} className={inputClass} />
        </div>
        <div>
          <label className={labelClass}>Giờ kết thúc *</label>
          <input required type="time" value={endTime} onChange={(e) => setEndTime(e.target.value)} className={inputClass} />
        </div>
      </div>

      <div className="grid grid-cols-2 gap-3">
        <div>
          <label className={labelClass}>Phòng học</label>
          <Select value={roomId} onChange={(e) => setRoomId(e.target.value)} className={inputClass}>
            <option value="">-- Không gán --</option>
            {rooms.map((r) => (
              <option key={r.id} value={r.id}>
                {r.code} — {r.name}
              </option>
            ))}
          </Select>
        </div>
        <div>
          <label className={labelClass}>Loại buổi học</label>
          <Select value={sessionType} onChange={(e) => setSessionType(e.target.value)} className={inputClass}>
            <option value="REGULAR">Buổi học thường</option>
            <option value="REVIEW">Ôn tập</option>
            <option value="EXAM">Kiểm tra</option>
            <option value="MAKEUP">Học bù</option>
          </Select>
        </div>
      </div>

      <div>
        <label className={labelClass}>Giáo viên phụ trách *</label>
        {teacher ? (
          <div className="flex items-center justify-between bg-emerald-50 border border-emerald-100 text-emerald-700 text-xs font-semibold px-3 py-2 rounded-lg">
            <span>{teacher.fullName} ({teacher.username})</span>
            <button type="button" onClick={() => setTeacher(null)} className="text-emerald-600 hover:text-rose-600">
              <X className="w-3.5 h-3.5" />
            </button>
          </div>
        ) : (
          <div className="relative">
            <Search className="absolute left-3 top-2.5 w-3.5 h-3.5 text-slate-400" />
            <input value={query} onChange={(e) => handleSearch(e.target.value)} placeholder="Tìm theo họ tên / email / username..." className={`${inputClass} pl-8`} />
            {results.length > 0 && (
              <div className="absolute z-10 mt-1 w-full bg-white border border-slate-200 rounded-lg shadow-lg divide-y divide-slate-100 max-h-56 overflow-y-auto">
                {results.map((u) => (
                  <button
                    key={u.id}
                    type="button"
                    onClick={() => {
                      setTeacher(u);
                      setResults([]);
                    }}
                    className="w-full text-left px-3 py-2 hover:bg-slate-50 text-xs"
                  >
                    {u.fullName} <span className="text-slate-400">({u.username} · {u.email})</span>
                  </button>
                ))}
              </div>
            )}
          </div>
        )}
      </div>

      {result && (
        <div className="p-3 bg-white border border-slate-200 rounded-lg text-xs space-y-1.5">
          <p className="font-bold text-slate-700">
            Đã tạo <span className="text-emerald-600">{result.createdCount}</span> / {result.totalDates} buổi
            {result.skippedCount > 0 && <span className="text-rose-500"> — bỏ qua {result.skippedCount} buổi trùng lịch</span>}
          </p>
          {result.skipped.length > 0 && (
            <div className="space-y-0.5 max-h-24 overflow-y-auto">
              {result.skipped.map((s, i) => (
                <p key={i} className="text-[10px] text-rose-500">
                  {s.date}: {s.reason}
                </p>
              ))}
            </div>
          )}
        </div>
      )}

      <div className="flex gap-2 pt-1">
        <Button type="button" variant="secondary" size="sm" onClick={onCancel}>
          Đóng
        </Button>
        <Button type="submit" variant="primary" size="sm" disabled={submitting}>
          <Sparkles className="w-3.5 h-3.5" />
          {submitting ? "Đang sinh lịch..." : "Kích hoạt sinh chu kỳ"}
        </Button>
      </div>
    </form>
  );
}
