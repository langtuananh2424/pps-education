import { useState } from "react";
import { CalendarClock, Save } from "lucide-react";
import { ApiError } from "@/lib/apiClient";
import { DayPart, RoomResponse } from "@/features/facility/api";
import Button from "@/components/ui/Button";
import Modal from "@/components/ui/Modal";
import Select from "@/components/ui/Select";
import DatePicker from "@/components/ui/DatePicker";
import { ClassSessionResponse, rescheduleClassSession, UpdateSessionAssignmentRequest } from "../api";
import PeriodMultiSelect from "./PeriodMultiSelect";
import TeacherSearchSelect from "./TeacherSearchSelect";

const inputClass = "w-full bg-slate-50 border border-slate-200 text-xs p-2.5 rounded-lg focus:outline-none";
const labelClass = "text-[10px] uppercase font-bold text-slate-500 block mb-1";

export interface SessionAssignmentPreview {
  roomName: string | null;
  teacherType: "VIETNAMESE" | "FOREIGN";
  primaryTeacherName: string;
  assistantTeacherName: string | null;
  cmTeacherName: string | null;
}

interface SessionEditModalProps {
  session: ClassSessionResponse;
  siteId: number;
  rooms: RoomResponse[];
  onClose: () => void;
  /** Tab "Sửa thông tin" — CHƯA gọi API, chỉ thêm vào hàng chờ của lưới, có hiệu lực khi bấm "Lưu" (bổ sung ngoài SDD gốc, xác nhận với người dùng 2026-08-21). */
  onQueueUpdate: (request: UpdateSessionAssignmentRequest, preview: SessionAssignmentPreview) => void;
  /** Tab "Dời lịch" — vẫn gọi API ngay (đổi ngày thực chất tạo buổi mới + đánh dấu RESCHEDULED buổi cũ, không hợp với mô hình nháp theo field). */
  onRescheduled: () => void;
  /** Buổi đang sửa là 1 mục "mới thêm, chưa lưu" trên lưới — chưa có sessionId thật nên ẩn tab "Dời lịch" (không có ý nghĩa trước khi Lưu), bổ sung ngoài SDD gốc, xác nhận với người dùng 2026-08-21. */
  hideReschedule?: boolean;
}

/**
 * Sửa nhanh tại chỗ 1 buổi học từ lưới thời khóa biểu (bổ sung ngoài SDD
 * gốc, xác nhận với người dùng 2026-08-19) — "Sửa thông tin" (đổi GV/phòng/
 * tiết cùng ngày) giờ chỉ ghi vào hàng chờ của lưới (xác nhận 2026-08-21,
 * xem ClassPeriodGrid), "Dời lịch" (đổi ngày) vẫn lưu ngay để giữ đúng ngữ
 * nghĩa RESCHEDULED. "Hủy buổi" đã chuyển ra menu chuột phải trên lưới.
 */
export default function SessionEditModal({ session, siteId, rooms, onClose, onQueueUpdate, onRescheduled, hideReschedule }: SessionEditModalProps) {
  const [mode, setMode] = useState<"edit" | "reschedule">("edit");

  const [roomId, setRoomId] = useState(session.roomId != null ? String(session.roomId) : "");
  const [teacherType, setTeacherType] = useState(session.teacherType ?? "");
  const [primaryTeacherId, setPrimaryTeacherId] = useState<number | null>(session.primaryTeacherId);
  const [primaryTeacherName, setPrimaryTeacherName] = useState<string | null>(session.primaryTeacherName);
  const [assistantTeacherId, setAssistantTeacherId] = useState<number | null>(session.assistantTeacherId);
  const [assistantTeacherName, setAssistantTeacherName] = useState<string | null>(session.assistantTeacherName);
  const [cmTeacherId, setCmTeacherId] = useState<number | null>(session.cmTeacherId);
  const [cmTeacherName, setCmTeacherName] = useState<string | null>(session.cmTeacherName);
  const [dayPart, setDayPart] = useState<DayPart>(session.dayPart ?? "MORNING");
  const [selectedPeriods, setSelectedPeriods] = useState<Set<number>>(new Set(session.periodNumbers));

  const [newSessionDate, setNewSessionDate] = useState(session.sessionDate);
  const [newDayPart, setNewDayPart] = useState<DayPart>(session.dayPart ?? "MORNING");
  const [newPeriods, setNewPeriods] = useState<Set<number>>(new Set(session.periodNumbers));
  const [rescheduleReason, setRescheduleReason] = useState("");

  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const canEdit = session.status === "SCHEDULED";

  const handleSaveAssignment = () => {
    if (!teacherType || !primaryTeacherId || !primaryTeacherName || selectedPeriods.size === 0) {
      setError("Vui lòng chọn đủ loại giáo viên, giáo viên chính, và tối thiểu 1 tiết.");
      return;
    }
    setError(null);
    const request: UpdateSessionAssignmentRequest = {
      roomId: roomId ? Number(roomId) : undefined,
      teacherType: teacherType as "VIETNAMESE" | "FOREIGN",
      primaryTeacherId,
      assistantTeacherId: assistantTeacherId ?? undefined,
      cmTeacherId: cmTeacherId ?? undefined,
      dayPart,
      periodNumbers: Array.from(selectedPeriods)
    };
    onQueueUpdate(request, {
      roomName: roomId ? rooms.find((r) => r.id === Number(roomId))?.name ?? null : null,
      teacherType: teacherType as "VIETNAMESE" | "FOREIGN",
      primaryTeacherName,
      assistantTeacherName,
      cmTeacherName
    });
  };

  const handleReschedule = async () => {
    if (!newSessionDate || newPeriods.size === 0) {
      setError("Vui lòng chọn ngày mới và tối thiểu 1 tiết mới.");
      return;
    }
    setSubmitting(true);
    setError(null);
    try {
      await rescheduleClassSession(session.classId, session.id, {
        newSessionDate,
        newDayPart,
        newPeriodNumbers: Array.from(newPeriods),
        newRoomId: roomId ? Number(roomId) : undefined,
        reason: rescheduleReason.trim() || undefined
      });
      onRescheduled();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Dời lịch buổi học thất bại.");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Modal open onClose={onClose} title={`${session.className} — Buổi ${session.sessionNumber}`} description={`${session.sessionDate} · ${session.startTime}–${session.endTime}`} size="lg">
      <div className="space-y-4">
        {error && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg">{error}</div>}

        {!canEdit && (
          <p className="text-xs text-slate-500 bg-slate-50 border border-slate-200 rounded-lg p-2.5">
            Buổi này đang ở trạng thái <b>{session.status}</b> — chỉ sửa/hủy/dời được buổi đang SCHEDULED.
          </p>
        )}

        {canEdit && (
          <>
            {!hideReschedule && (
              <div className="flex border-b border-slate-200 gap-4">
                <button
                  type="button"
                  onClick={() => setMode("edit")}
                  className={`pb-2 text-xs font-bold border-b-2 ${mode === "edit" ? "border-brand-red text-brand-red" : "border-transparent text-slate-500"}`}
                >
                  Sửa thông tin
                </button>
                <button
                  type="button"
                  onClick={() => setMode("reschedule")}
                  className={`pb-2 text-xs font-bold border-b-2 flex items-center gap-1 ${mode === "reschedule" ? "border-brand-red text-brand-red" : "border-transparent text-slate-500"}`}
                >
                  <CalendarClock className="w-3.5 h-3.5" />
                  Dời lịch
                </button>
              </div>
            )}

            {mode === "edit" || hideReschedule ? (
              <div className="space-y-3">
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
                    <label className={labelClass}>Loại giáo viên *</label>
                    <Select value={teacherType} onChange={(e) => setTeacherType(e.target.value)} className={inputClass}>
                      <option value="">-- Chọn loại giáo viên --</option>
                      <option value="VIETNAMESE">GV Việt Nam</option>
                      <option value="FOREIGN">GV nước ngoài</option>
                    </Select>
                  </div>
                </div>

                <PeriodMultiSelect
                  siteId={siteId}
                  required
                  dayPart={dayPart}
                  onDayPartChange={setDayPart}
                  selected={selectedPeriods}
                  onChange={setSelectedPeriods}
                />

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

                <p className="text-[11px] text-slate-400 italic">Thay đổi chỉ hiện tạm trên lưới — bấm "Lưu" ở đầu lưới để ghi thật.</p>

                <div className="flex justify-end pt-2 border-t border-slate-100">
                  <Button type="button" variant="primary" size="sm" onClick={handleSaveAssignment}>
                    <Save className="w-3.5 h-3.5" />
                    Ghi vào lưới
                  </Button>
                </div>
              </div>
            ) : (
              <div className="space-y-3">
                <div>
                  <label className={labelClass}>Ngày học mới</label>
                  <DatePicker value={newSessionDate} onChange={setNewSessionDate} />
                </div>
                <PeriodMultiSelect
                  siteId={siteId}
                  label="Tiết mới"
                  required
                  dayPart={newDayPart}
                  onDayPartChange={setNewDayPart}
                  selected={newPeriods}
                  onChange={setNewPeriods}
                />
                <div>
                  <label className={labelClass}>Phòng học mới</label>
                  <Select value={roomId} onChange={(e) => setRoomId(e.target.value)} className={inputClass}>
                    <option value="">-- Không gán --</option>
                    {rooms.map((r) => (
                      <option key={r.id} value={r.id}>
                        {r.code} — {r.name}
                      </option>
                    ))}
                  </Select>
                </div>
                <p className="text-[11px] text-slate-500">Giáo viên chính giữ nguyên: {session.primaryTeacherName}.</p>
                <div>
                  <label className={labelClass}>Lý do dời lịch (không bắt buộc)</label>
                  <input value={rescheduleReason} onChange={(e) => setRescheduleReason(e.target.value)} className={inputClass} />
                </div>
                <p className="text-[11px] text-amber-600 italic">Dời lịch ghi lại ngay, không qua nút "Lưu" ở lưới.</p>
                <div className="flex justify-end pt-2 border-t border-slate-100">
                  <Button type="button" variant="primary" size="sm" onClick={handleReschedule} disabled={submitting}>
                    {submitting ? "Đang lưu..." : "Dời lịch"}
                  </Button>
                </div>
              </div>
            )}
          </>
        )}
      </div>
    </Modal>
  );
}
