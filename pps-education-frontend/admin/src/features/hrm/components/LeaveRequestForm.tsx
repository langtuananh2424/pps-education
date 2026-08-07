import React, { useEffect, useMemo, useState } from "react";
import { PlusCircle, Repeat } from "lucide-react";
import DatePicker from "@/components/ui/DatePicker";
import Select from "@/components/ui/Select";
import { ApiError } from "@/lib/apiClient";
import { useApp } from "@/context/AppContext";
import { UserRole } from "@/types";
import UserSearchCombobox from "@/features/system-admin/components/UserSearchCombobox";
import type { UserListItemResponse } from "@/features/system-admin/api";
import type { ClassSessionResponse } from "@/features/academic/api";
import { CreateLeaveRequestRequest, listTeachingSessionsForSubstitution, submitLeaveRequest } from "../api";

const leaveTypeLabels: Record<CreateLeaveRequestRequest["leaveType"], string> = {
  ANNUAL: "Nghỉ phép năm",
  SICK: "Nghỉ ốm",
  UNPAID: "Nghỉ không lương",
  PERSONAL: "Nghỉ việc riêng",
  LATE: "Đi trễ có lý do",
  EARLY_LEAVE: "Về sớm"
};

interface LeaveRequestFormProps {
  onSubmitted: () => void;
}

/**
 * UC-10: Nộp đơn từ. Nếu người nộp là Giáo viên và có buổi dạy trong
 * khoảng nghỉ, hiển thị thêm bước chọn 1 lớp + giáo viên dạy thay cho
 * từng buổi (bắt buộc chọn đủ — A3/A4, xem docs/uc/phan-he-04-nhan-su.md).
 * Việc dạy thay được hệ thống áp dụng NGAY khi nộp đơn, không đợi duyệt.
 */
export default function LeaveRequestForm({ onSubmitted }: LeaveRequestFormProps) {
  const { currentRole } = useApp();
  const isTeacher = currentRole === UserRole.TEACHER;

  const [leaveType, setLeaveType] = useState<CreateLeaveRequestRequest["leaveType"]>("ANNUAL");
  const [startDate, setStartDate] = useState("");
  const [endDate, setEndDate] = useState("");
  const [startTime, setStartTime] = useState("08:00");
  const [endTime, setEndTime] = useState("09:00");
  const [reason, setReason] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const partialDay = leaveType === "LATE" || leaveType === "EARLY_LEAVE";

  // LATE/EARLY_LEAVE chỉ áp dụng trong 1 ngày (UC-10 Main Flow bước 2).
  useEffect(() => {
    if (partialDay && startDate) setEndDate(startDate);
  }, [partialDay, startDate]);

  const [teachingSessions, setTeachingSessions] = useState<ClassSessionResponse[]>([]);
  const [loadingSessions, setLoadingSessions] = useState(false);
  const [selectedClassId, setSelectedClassId] = useState<number | null>(null);
  const [substitutes, setSubstitutes] = useState<Record<number, UserListItemResponse | null>>({});

  useEffect(() => {
    setSelectedClassId(null);
    setSubstitutes({});
    if (!isTeacher || !startDate || !endDate) {
      setTeachingSessions([]);
      return;
    }
    setLoadingSessions(true);
    listTeachingSessionsForSubstitution(startDate, endDate)
      .then(setTeachingSessions)
      .catch(() => setTeachingSessions([]))
      .finally(() => setLoadingSessions(false));
  }, [isTeacher, startDate, endDate]);

  const sessionsByClass = useMemo(() => {
    const map = new Map<number, { className: string; sessions: ClassSessionResponse[] }>();
    for (const s of teachingSessions) {
      const entry = map.get(s.classId);
      if (entry) entry.sessions.push(s);
      else map.set(s.classId, { className: s.className, sessions: [s] });
    }
    return map;
  }, [teachingSessions]);

  const selectedClassSessions = selectedClassId != null ? (sessionsByClass.get(selectedClassId)?.sessions ?? []) : [];
  const needsSubstituteSelection = teachingSessions.length > 0;
  const allSubstitutesChosen = selectedClassSessions.length > 0 && selectedClassSessions.every((s) => substitutes[s.id]);

  const applySameSubstituteToAll = (user: UserListItemResponse | null) => {
    if (!user) return;
    setSubstitutes((prev) => {
      const next = { ...prev };
      for (const s of selectedClassSessions) next[s.id] = user;
      return next;
    });
  };

  const resetForm = () => {
    setLeaveType("ANNUAL");
    setStartDate("");
    setEndDate("");
    setReason("");
    setSelectedClassId(null);
    setSubstitutes({});
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!reason.trim() || !startDate || !endDate) return;
    if (needsSubstituteSelection && (!selectedClassId || !allSubstitutesChosen)) {
      setError("Bạn có buổi dạy trong khoảng nghỉ này — cần chọn 1 lớp và đủ giáo viên dạy thay cho từng buổi.");
      return;
    }

    setSubmitting(true);
    setError(null);
    try {
      await submitLeaveRequest({
        leaveType,
        startDate,
        endDate,
        startTime: partialDay ? startTime : undefined,
        endTime: partialDay ? endTime : undefined,
        reason,
        substitutes:
          selectedClassId && allSubstitutesChosen
            ? selectedClassSessions.map((s) => ({ classSessionId: s.id, substituteTeacherId: substitutes[s.id]!.id }))
            : null
      });
      resetForm();
      onSubmitted();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Không nộp được đơn từ, vui lòng thử lại.");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="bg-white p-5 rounded-xl border border-slate-200 shadow-soft space-y-4">
      <h3 className="text-xs font-bold text-slate-400 block uppercase tracking-wider font-display border-b border-slate-100 pb-2">
        Nộp Đơn Xin Nghỉ
      </h3>
      <p className="text-xs text-slate-500">Gửi yêu cầu nghỉ phép, đi muộn hoặc về sớm. Hệ thống sẽ tự động xác định quy trình duyệt 1-2 bước theo chức vụ của bạn.</p>

      {error && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg">{error}</div>}

      <form onSubmit={handleSubmit} className="space-y-3.5">
        <div className="space-y-1">
          <label className="text-[10px] uppercase font-bold tracking-wider text-slate-500">Hình thức nộp</label>
          <Select
            value={leaveType}
            onChange={(e) => setLeaveType(e.target.value as CreateLeaveRequestRequest["leaveType"])}
            className="w-full bg-slate-50 border border-slate-200 text-xs px-3 py-2 rounded-lg focus:outline-none"
          >
            {Object.entries(leaveTypeLabels).map(([value, label]) => (
              <option key={value} value={value}>
                {label}
              </option>
            ))}
          </Select>
        </div>

        <div className="grid grid-cols-2 gap-3">
          <div className="space-y-1">
            <label className="text-[10px] uppercase font-bold tracking-wider text-slate-500">Từ ngày</label>
            <DatePicker value={startDate} onChange={setStartDate} max={partialDay ? startDate : endDate || undefined} />
          </div>
          <div className="space-y-1">
            <label className="text-[10px] uppercase font-bold tracking-wider text-slate-500">Đến ngày</label>
            <DatePicker value={endDate} onChange={setEndDate} min={startDate || undefined} disabled={partialDay} />
          </div>
        </div>

        {partialDay && (
          <div className="grid grid-cols-2 gap-3">
            <div className="space-y-1">
              <label className="text-[10px] uppercase font-bold tracking-wider text-slate-500">Giờ bắt đầu</label>
              <input type="time" value={startTime} onChange={(e) => setStartTime(e.target.value)} className="w-full bg-slate-50 border border-slate-200 text-xs px-3 py-2 rounded-lg focus:outline-none" />
            </div>
            <div className="space-y-1">
              <label className="text-[10px] uppercase font-bold tracking-wider text-slate-500">Giờ kết thúc</label>
              <input type="time" value={endTime} onChange={(e) => setEndTime(e.target.value)} className="w-full bg-slate-50 border border-slate-200 text-xs px-3 py-2 rounded-lg focus:outline-none" />
            </div>
          </div>
        )}

        <div className="space-y-1">
          <label className="text-[10px] uppercase font-bold tracking-wider text-slate-500">Lý do nghỉ</label>
          <textarea
            required
            rows={2}
            placeholder="Ví dụ: Đi tái khám răng, bị ốm sốt..."
            value={reason}
            onChange={(e) => setReason(e.target.value)}
            className="w-full bg-slate-50 border border-slate-200 text-xs px-3 py-2 rounded-lg focus:outline-none"
          />
        </div>

        {isTeacher && loadingSessions && <p className="text-[11px] text-slate-400 italic">Đang kiểm tra lịch dạy trong khoảng nghỉ...</p>}

        {isTeacher && !loadingSessions && needsSubstituteSelection && (
          <div className="space-y-2.5 border border-amber-200 bg-amber-50/60 rounded-lg p-3">
            <p className="text-[11px] font-bold text-amber-800">
              Bạn có buổi dạy trong khoảng nghỉ này — hãy chọn 1 lớp và giáo viên dạy thay cho từng buổi. Việc dạy thay có hiệu lực
              ngay khi nộp đơn (không đợi duyệt).
            </p>

            <div className="space-y-1">
              <label className="text-[10px] uppercase font-bold tracking-wider text-slate-500">Chọn lớp cần dạy thay</label>
              <Select
                value={selectedClassId ?? ""}
                onChange={(e) => setSelectedClassId(e.target.value ? Number(e.target.value) : null)}
                className="w-full bg-white border border-slate-200 text-xs px-3 py-2 rounded-lg focus:outline-none"
              >
                <option value="">-- Chọn lớp --</option>
                {Array.from(sessionsByClass.entries()).map(([classId, { className, sessions }]) => (
                  <option key={classId} value={classId}>
                    {className} ({sessions.length} buổi)
                  </option>
                ))}
              </Select>
              {sessionsByClass.size > 1 && (
                <p className="text-[10px] text-amber-700 italic">
                  Bạn có buổi dạy ở {sessionsByClass.size} lớp khác nhau — mỗi đơn chỉ xử lý dạy thay cho 1 lớp, hãy nộp thêm đơn
                  riêng cho các lớp còn lại.
                </p>
              )}
            </div>

            {selectedClassId != null && (
              <div className="space-y-2">
                <div className="flex items-center justify-between">
                  <label className="text-[10px] uppercase font-bold tracking-wider text-slate-500">Giáo viên dạy thay từng buổi</label>
                  <button
                    type="button"
                    onClick={() => {
                      const first = Object.values(substitutes).find((u): u is UserListItemResponse => !!u);
                      if (first) applySameSubstituteToAll(first);
                    }}
                    className="text-[10px] font-bold text-slate-500 hover:text-slate-800 flex items-center gap-1"
                  >
                    <Repeat className="w-3 h-3" /> Áp dụng cho tất cả
                  </button>
                </div>
                {selectedClassSessions.map((s) => (
                  <div key={s.id} className="bg-white border border-slate-200 rounded-lg p-2 space-y-1.5">
                    <p className="text-[11px] text-slate-600 font-semibold">
                      {s.sessionDate} · {s.startTime.slice(0, 5)}-{s.endTime.slice(0, 5)}
                    </p>
                    <UserSearchCombobox
                      value={substitutes[s.id] ?? null}
                      onChange={(u) => setSubstitutes((prev) => ({ ...prev, [s.id]: u }))}
                      roleFilter="TEACHER"
                      placeholder="Chọn giáo viên dạy thay..."
                    />
                  </div>
                ))}
              </div>
            )}
          </div>
        )}

        <button
          type="submit"
          disabled={submitting}
          className="w-full bg-slate-900 hover:bg-slate-800 disabled:opacity-60 text-white font-semibold text-xs py-2 rounded-lg flex items-center justify-center gap-1.5"
        >
          <PlusCircle className="w-4 h-4 text-brand-yellow" />
          {submitting ? "Đang gửi..." : "Gửi đơn chờ duyệt"}
        </button>
      </form>
    </div>
  );
}
