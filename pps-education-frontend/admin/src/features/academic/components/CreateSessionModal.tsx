import React, { useEffect, useState } from "react";
import { Sparkles } from "lucide-react";
import { ApiError } from "@/lib/apiClient";
import Button from "@/components/ui/Button";
import Modal from "@/components/ui/Modal";
import Select from "@/components/ui/Select";
import DatePicker from "@/components/ui/DatePicker";
import { DayPart, listSites, SiteResponse } from "@/features/facility/api";
import { BulkCreateClassSessionRequest, BulkCreateClassSessionResponse, ClassResponse, bulkCreateClassSessions, listClasses } from "../api";
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
  /** Ngày dạng "YYYY-MM-DD" — suy ra luôn "Từ ngày"/"Đến ngày"/"Chọn thứ" (bỏ trống nếu mở từ nút "Xếp lịch" tổng, không bôi ô trước). */
  date?: string;
  dayPart?: DayPart;
  periodNumbers?: number[];
  /** Đã có sẵn từ bộ lọc "Lớp" trên trang (Header/EmployeeSchedulePage) hoặc từ ô đã bôi — khoá field Lớp, không hỏi lại (bổ sung ngoài SDD gốc, xác nhận với người dùng 2026-08-21). */
  classId?: number;
}

export interface QueuedCreatePayload {
  classId: number;
  className: string;
  classColor: string;
  request: BulkCreateClassSessionRequest;
  primaryTeacherName: string;
  assistantTeacherName: string | null;
  cmTeacherName: string | null;
}

interface CreateSessionModalProps {
  /**
   * Điểm trường của lưới đang xem — cố định, không cho đổi trong modal này
   * (đã chọn ở khối điểm trường đang đứng, bổ sung ngoài SDD gốc, xác nhận
   * với người dùng 2026-08-21). Để trống (mode="immediate", dùng từ nút
   * "+ Xếp lịch" chung ở đầu trang, không gắn với 1 khối điểm trường cụ thể)
   * — modal tự hiện ô chọn điểm trường, xác nhận với người dùng 2026-09-12.
   */
  siteId?: number;
  onClose: () => void;
  /** mode="queue" (mặc định): thêm vào hàng chờ (nháp) trên lưới — CHƯA gọi API, chỉ có hiệu lực khi bấm "Lưu" ở lưới. mode="immediate": gọi API lưu thật ngay khi submit (không có 1 lưới cụ thể nào để giữ hàng chờ). */
  mode?: "queue" | "immediate";
  /** Bắt buộc khi mode="queue" (mặc định). */
  onQueued?: (payload: QueuedCreatePayload) => void;
  /** Bắt buộc khi mode="immediate" — gọi sau khi lưu thành công để trang cha refetch đúng khối điểm trường. */
  onCreated?: (siteId: number) => void;
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
export default function CreateSessionModal({ siteId, onClose, mode = "queue", onQueued, onCreated, prefill }: CreateSessionModalProps) {
  const siteLocked = siteId != null;
  const [sites, setSites] = useState<SiteResponse[]>([]);
  const [selectedSiteId, setSelectedSiteId] = useState<number | null>(siteId ?? null);
  const effectiveSiteId = siteId ?? selectedSiteId;
  const [classes, setClasses] = useState<ClassResponse[]>([]);
  const [submitting, setSubmitting] = useState(false);
  const [classId, setClassId] = useState<number | "">(prefill?.classId ?? "");
  const lockedClassId = prefill?.classId != null;

  const [startDate, setStartDate] = useState(prefill?.date ?? "");
  const [endDate, setEndDate] = useState(prefill?.date ?? "");
  const [selectedDays, setSelectedDays] = useState<Set<string>>(() => (prefill?.date ? new Set([weekdayOf(prefill.date)]) : new Set()));
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
  const [actualTeacherName, setActualTeacherName] = useState("");

  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    listSites().then(setSites).catch(() => undefined);
  }, []);

  useEffect(() => {
    if (effectiveSiteId == null) {
      setClasses([]);
      return;
    }
    listClasses({ siteId: effectiveSiteId }).then(setClasses).catch(() => undefined);
    // Đổi điểm trường (chỉ xảy ra khi không khoá siteId) — lớp/tiết đã chọn của điểm trường cũ không
    // còn hợp lệ, reset lại để tránh gửi nhầm classId/periodNumbers thuộc điểm trường khác.
    if (!siteLocked) {
      setClassId(prefill?.classId ?? "");
      setSelectedPeriods(new Set(prefill?.periodNumbers ?? []));
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [effectiveSiteId]);

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
    if (effectiveSiteId == null) {
      setError("Vui lòng chọn điểm trường.");
      return;
    }
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
      cmTeacherId: cmTeacherId ?? undefined,
      actualTeacherName: teacherType === "FOREIGN" && actualTeacherName.trim() ? actualTeacherName.trim() : undefined
    };

    if (mode === "immediate") {
      setSubmitting(true);
      try {
        const result: BulkCreateClassSessionResponse = await bulkCreateClassSessions(Number(classId), request);
        if (result.skippedCount > 0) {
          setError(`Đã lưu, nhưng bỏ qua ${result.skippedCount}/${result.totalDates} ngày trùng lịch.`);
        }
        onCreated?.(effectiveSiteId);
        if (result.skippedCount === 0) onClose();
      } catch (err) {
        setError(err instanceof ApiError ? err.message : "Xếp lịch thất bại.");
      } finally {
        setSubmitting(false);
      }
      return;
    }

    onQueued?.({
      classId: Number(classId),
      className: `${selectedClass.classCode} — ${selectedClass.name}`,
      classColor: selectedClass.color,
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
            <label className={labelClass}>Trường {!siteLocked && "*"}</label>
            {siteLocked ? (
              <p className="text-xs font-bold text-slate-700 bg-slate-100 border border-slate-200 rounded-lg p-2.5">
                {sites.find((s) => s.id === effectiveSiteId)?.name ?? "…"}
              </p>
            ) : (
              <Select value={selectedSiteId ?? ""} onChange={(e) => setSelectedSiteId(e.target.value ? Number(e.target.value) : null)} className={inputClass}>
                <option value="">-- Chọn điểm trường --</option>
                {sites.map((s) => (
                  <option key={s.id} value={s.id}>
                    {s.code} — {s.name}
                  </option>
                ))}
              </Select>
            )}
          </div>
          <div>
            <label className={labelClass}>Lớp *</label>
            <Select value={classId} onChange={(e) => setClassId(e.target.value ? Number(e.target.value) : "")} className={inputClass} disabled={lockedClassId || effectiveSiteId == null}>
              <option value="">-- Chọn lớp --</option>
              {classes.map((c) => (
                <option key={c.id} value={c.id}>
                  {c.classCode} — {c.name}
                </option>
              ))}
            </Select>
            {lockedClassId && <p className="text-[10px] text-slate-400 italic mt-1">Đã chọn theo bộ lọc "Lớp" trên trang.</p>}
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

        {effectiveSiteId == null ? (
          <p className="text-[11px] text-slate-400 italic">Chọn điểm trường trước để xem tiết học.</p>
        ) : (
          <PeriodMultiSelect siteId={effectiveSiteId} required dayPart={dayPart} onDayPartChange={setDayPart} selected={selectedPeriods} onChange={setSelectedPeriods} />
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

        {teacherType === "FOREIGN" && (
          <div>
            <label className={labelClass}>Tên giáo viên giảng dạy</label>
            <input
              value={actualTeacherName}
              onChange={(e) => setActualTeacherName(e.target.value)}
              placeholder="VD: Alex"
              className={inputClass}
            />
            <p className="text-[10px] text-slate-400 italic mt-1">
              GVNN không có tài khoản hệ thống — nhập tên thật để hiển thị trên lưới, khớp với "Tên giáo viên giảng
              dạy" ở Nhận xét học viên.
            </p>
          </div>
        )}

        <p className="text-[11px] text-slate-400 italic">
          {mode === "immediate"
            ? "Bấm \"Xếp lịch\" sẽ ghi thật ngay (server sẽ tự bỏ qua ngày bị trùng lịch)."
            : "Buổi vừa thêm sẽ hiện trên lưới ở dạng \"chưa lưu\" — bấm \"Lưu\" ở đầu lưới để ghi thật (server sẽ tự bỏ qua ngày bị trùng lịch)."}
        </p>

        <div className="flex justify-end gap-2 pt-1">
          <Button type="button" variant="secondary" size="sm" onClick={onClose}>
            Đóng
          </Button>
          <Button type="submit" variant="primary" size="sm" disabled={submitting}>
            <Sparkles className="w-3.5 h-3.5" />
            {mode === "immediate" ? (submitting ? "Đang xếp lịch..." : "Xếp lịch") : "Thêm vào lưới"}
          </Button>
        </div>
      </form>
    </Modal>
  );
}
