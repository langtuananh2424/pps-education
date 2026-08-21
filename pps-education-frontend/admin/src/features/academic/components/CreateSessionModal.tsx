import React, { useEffect, useState } from "react";
import { Sparkles } from "lucide-react";
import Button from "@/components/ui/Button";
import Modal from "@/components/ui/Modal";
import Select from "@/components/ui/Select";
import DatePicker from "@/components/ui/DatePicker";
import { DayPart, listSites, SiteResponse } from "@/features/facility/api";
import { BulkCreateClassSessionRequest, ClassResponse, listClasses } from "../api";
import PeriodMultiSelect from "./PeriodMultiSelect";
import TeacherSearchSelect from "./TeacherSearchSelect";

const inputClass = "w-full bg-slate-50 border border-slate-200 text-xs p-2.5 rounded-lg focus:outline-none";
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

export interface CreateSessionModalPrefill {
  /** Ngày dạng "YYYY-MM-DD" — suy ra luôn "Từ ngày"/"Đến ngày"/"Chọn thứ". */
  date: string;
  dayPart: DayPart;
  periodNumbers: number[];
  classId?: number;
}

export interface QueuedCreatePayload {
  classId: number;
  className: string;
  request: BulkCreateClassSessionRequest;
  primaryTeacherName: string;
  assistantTeacherName: string | null;
  cmTeacherName: string | null;
}

interface CreateSessionModalProps {
  defaultSiteId: number | null;
  onClose: () => void;
  /** Thêm vào hàng chờ (nháp) trên lưới — CHƯA gọi API, chỉ có hiệu lực khi bấm "Lưu" ở lưới (bổ sung ngoài SDD gốc, xác nhận với người dùng 2026-08-20). */
  onQueued: (payload: QueuedCreatePayload) => void;
  /** Bôi đen ô tiết trên lưới rồi chuột phải → "Xếp lịch" (bổ sung ngoài SDD gốc, xác nhận với người dùng 2026-08-20). */
  prefill?: CreateSessionModalPrefill;
}

export function weekdayOf(dateIso: string): string {
  const [y, m, d] = dateIso.split("-").map(Number);
  const dow = new Date(y, m - 1, d).getDay(); // 0=CN..6=T7
  return ["SUNDAY", "MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY"][dow];
}

/**
 * Nút "Xếp lịch" ở lưới thời khóa biểu (bổ sung ngoài SDD gốc, xác nhận với
 * người dùng 2026-08-19, đổi sang cơ chế nháp/Lưu 2026-08-21) — Trường →
 * Lớp → chọn thứ → chọn tiết → loại GV → GV chính/phụ/CM → khoảng ngày.
 * KHÔNG gọi bulkCreateClassSessions ngay — chỉ thêm 1 mục vào hàng chờ của
 * lưới (hiện dạng thẻ "chưa lưu"), API chỉ được gọi khi người dùng bấm nút
 * "Lưu" tổng ở lưới — cho phép Hoàn tác/xem lại trước khi ghi thật.
 */
export default function CreateSessionModal({ defaultSiteId, onClose, onQueued, prefill }: CreateSessionModalProps) {
  const [sites, setSites] = useState<SiteResponse[]>([]);
  const [siteId, setSiteId] = useState<number | "">(defaultSiteId ?? "");
  const [classes, setClasses] = useState<ClassResponse[]>([]);
  const [classId, setClassId] = useState<number | "">("");

  const [startDate, setStartDate] = useState(prefill?.date ?? "");
  const [endDate, setEndDate] = useState(prefill?.date ?? "");
  const [selectedDays, setSelectedDays] = useState<Set<string>>(() => (prefill ? new Set([weekdayOf(prefill.date)]) : new Set()));
  const [dayPart, setDayPart] = useState<DayPart>(prefill?.dayPart ?? "MORNING");
  const [selectedPeriods, setSelectedPeriods] = useState<Set<number>>(() => new Set(prefill?.periodNumbers ?? []));
  const [sessionType, setSessionType] = useState("REGULAR");
  const [teacherType, setTeacherType] = useState("");
  const [primaryTeacherId, setPrimaryTeacherId] = useState<number | null>(null);
  const [primaryTeacherName, setPrimaryTeacherName] = useState<string | null>(null);
  const [assistantTeacherId, setAssistantTeacherId] = useState<number | null>(null);
  const [assistantTeacherName, setAssistantTeacherName] = useState<string | null>(null);
  const [cmTeacherId, setCmTeacherId] = useState<number | null>(null);
  const [cmTeacherName, setCmTeacherName] = useState<string | null>(null);

  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    listSites().then(setSites).catch(() => undefined);
  }, []);

  useEffect(() => {
    setClassId("");
    if (!siteId) {
      setClasses([]);
      return;
    }
    listClasses({ siteId: Number(siteId) }).then(setClasses).catch(() => undefined);
  }, [siteId]);

  useEffect(() => {
    if (!prefill?.classId) return;
    if (classes.some((c) => c.id === prefill.classId)) setClassId(prefill.classId);
  }, [classes, prefill?.classId]);

  const toggleDay = (day: string) => {
    setSelectedDays((prev) => {
      const next = new Set(prev);
      if (next.has(day)) next.delete(day);
      else next.add(day);
      return next;
    });
  };

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    const selectedClass = classes.find((c) => c.id === classId);
    if (!classId || !selectedClass || !startDate || !endDate || selectedDays.size === 0) {
      setError("Vui lòng chọn lớp, khoảng ngày và tối thiểu 1 ngày trong tuần.");
      return;
    }
    if (selectedPeriods.size === 0) {
      setError("Vui lòng chọn tối thiểu 1 tiết học.");
      return;
    }
    if (!teacherType || !primaryTeacherId || !primaryTeacherName) {
      setError("Vui lòng chọn loại giáo viên và giáo viên chính.");
      return;
    }
    const request: BulkCreateClassSessionRequest = {
      startDate,
      endDate,
      daysOfWeek: Array.from(selectedDays),
      dayPart,
      periodNumbers: Array.from(selectedPeriods),
      sessionType,
      teacherType: teacherType as "VIETNAMESE" | "FOREIGN",
      primaryTeacherId,
      assistantTeacherId: assistantTeacherId ?? undefined,
      cmTeacherId: cmTeacherId ?? undefined
    };
    onQueued({
      classId: Number(classId),
      className: `${selectedClass.classCode} — ${selectedClass.name}`,
      request,
      primaryTeacherName,
      assistantTeacherName,
      cmTeacherName
    });
  };

  return (
    <Modal open onClose={onClose} title="Xếp lịch buổi học" size="lg">
      <form onSubmit={handleSubmit} className="space-y-3">
        {error && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg">{error}</div>}

        <div className="grid grid-cols-2 gap-3">
          <div>
            <label className={labelClass}>Trường *</label>
            <Select value={siteId} onChange={(e) => setSiteId(e.target.value ? Number(e.target.value) : "")} className={inputClass}>
              <option value="">-- Chọn điểm trường --</option>
              {sites.map((s) => (
                <option key={s.id} value={s.id}>
                  {s.name}
                </option>
              ))}
            </Select>
          </div>
          <div>
            <label className={labelClass}>Lớp *</label>
            <Select value={classId} onChange={(e) => setClassId(e.target.value ? Number(e.target.value) : "")} className={inputClass} disabled={!siteId}>
              <option value="">-- Chọn lớp --</option>
              {classes.map((c) => (
                <option key={c.id} value={c.id}>
                  {c.classCode} — {c.name}
                </option>
              ))}
            </Select>
          </div>
        </div>

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
          <label className={labelClass}>Chọn thứ *</label>
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

        {siteId ? (
          <PeriodMultiSelect
            siteId={Number(siteId)}
            required
            dayPart={dayPart}
            onDayPartChange={setDayPart}
            selected={selectedPeriods}
            onChange={setSelectedPeriods}
          />
        ) : (
          <p className="text-[11px] text-slate-400 italic">Chọn trường trước để xem danh sách tiết học.</p>
        )}

        <div>
          <label className={labelClass}>Loại giáo viên *</label>
          <Select value={teacherType} onChange={(e) => setTeacherType(e.target.value)} className={inputClass}>
            <option value="">-- Chọn loại giáo viên --</option>
            <option value="VIETNAMESE">GV Việt Nam</option>
            <option value="FOREIGN">GV nước ngoài</option>
          </Select>
        </div>

        <TeacherSearchSelect
          label="Giáo viên chính"
          required
          value={primaryTeacherId}
          valueName={primaryTeacherName}
          onChange={(id, name) => {
            setPrimaryTeacherId(id);
            setPrimaryTeacherName(name);
          }}
        />
        <TeacherSearchSelect
          label="Giáo viên phụ (tuỳ chọn)"
          value={assistantTeacherId}
          valueName={assistantTeacherName}
          onChange={(id, name) => {
            setAssistantTeacherId(id);
            setAssistantTeacherName(name);
          }}
        />
        <TeacherSearchSelect
          label="CM (tuỳ chọn)"
          value={cmTeacherId}
          valueName={cmTeacherName}
          onChange={(id, name) => {
            setCmTeacherId(id);
            setCmTeacherName(name);
          }}
        />

        <p className="text-[11px] text-slate-400 italic">
          Buổi vừa thêm sẽ hiện trên lưới ở dạng "chưa lưu" — bấm "Lưu" ở đầu lưới để ghi thật (server sẽ tự bỏ qua ngày bị trùng lịch).
        </p>

        <div className="flex justify-end gap-2 pt-1">
          <Button type="button" variant="secondary" size="sm" onClick={onClose}>
            Đóng
          </Button>
          <Button type="submit" variant="primary" size="sm">
            <Sparkles className="w-3.5 h-3.5" />
            Thêm vào lưới
          </Button>
        </div>
      </form>
    </Modal>
  );
}
