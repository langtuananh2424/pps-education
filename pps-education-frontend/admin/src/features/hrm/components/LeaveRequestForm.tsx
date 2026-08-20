import React, { useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { PlusCircle, Repeat } from "lucide-react";
import DatePicker from "@/components/ui/DatePicker";
import Select from "@/components/ui/Select";
import { ApiError } from "@/lib/apiClient";
import { useApp } from "@/context/AppContext";
import { UserRole } from "@/types";
import SubstituteTeacherCombobox from "./SubstituteTeacherCombobox";
import type { ClassSessionResponse } from "@/features/academic/api";
import { CreateLeaveRequestRequest, listTeachingSessionsForSubstitution, submitLeaveRequest, TeacherLookupResponse } from "../api";
import { useLeaveTypes } from "../hooks/useLeaveTypes";

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
  const { t } = useTranslation("hrm-leaves");
  const { currentRole } = useApp();
  const isTeacher = currentRole === UserRole.TEACHER;
  const { leaveTypes, loading: loadingLeaveTypes } = useLeaveTypes();

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
  const [substitutes, setSubstitutes] = useState<Record<number, TeacherLookupResponse | null>>({});

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

  const applySameSubstituteToAll = (user: TeacherLookupResponse | null) => {
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
      setError(t("leaveRequestForm.errors.missingSubstitutes"));
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
      setError(err instanceof ApiError ? err.message : t("leaveRequestForm.errors.genericSubmitError"));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="bg-white rounded-xl border border-slate-200 shadow-soft overflow-hidden">
      <div className="p-5 space-y-4">
        <h3 className="text-xs font-bold text-slate-400 block uppercase tracking-wider font-display border-b border-slate-100 pb-2">
          {t("leaveRequestForm.title")}
        </h3>
        <p className="text-xs text-slate-500">{t("leaveRequestForm.description")}</p>

        {error && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg">{error}</div>}

        <form id="leave-request-form" onSubmit={handleSubmit} className="space-y-3.5">
        <div className="space-y-1">
          <label className="text-[10px] uppercase font-bold tracking-wider text-slate-500">{t("leaveRequestForm.leaveTypeLabel")}</label>
          <Select
            value={leaveType}
            onChange={(e) => setLeaveType(e.target.value as CreateLeaveRequestRequest["leaveType"])}
            disabled={loadingLeaveTypes}
            className="w-full bg-slate-50 border border-slate-200 text-xs px-3 py-2 rounded-lg focus:outline-none disabled:opacity-50"
          >
            {loadingLeaveTypes ? (
              <option>{t("leaveRequestForm.loadingLeaveTypes")}</option>
            ) : (
              leaveTypes.map((type) => (
                <option key={type.code} value={type.code}>
                  {type.label}
                </option>
              ))
            )}
          </Select>
        </div>

        <div className="grid grid-cols-2 gap-3">
          <div className="space-y-1">
            <label className="text-[10px] uppercase font-bold tracking-wider text-slate-500">{t("leaveRequestForm.startDateLabel")}</label>
            <DatePicker value={startDate} onChange={setStartDate} max={partialDay ? startDate : endDate || undefined} />
          </div>
          <div className="space-y-1">
            <label className="text-[10px] uppercase font-bold tracking-wider text-slate-500">{t("leaveRequestForm.endDateLabel")}</label>
            <DatePicker value={endDate} onChange={setEndDate} min={startDate || undefined} disabled={partialDay} />
          </div>
        </div>

        {partialDay && (
          <div className="grid grid-cols-2 gap-3">
            <div className="space-y-1">
              <label className="text-[10px] uppercase font-bold tracking-wider text-slate-500">{t("leaveRequestForm.startTimeLabel")}</label>
              <input type="time" value={startTime} onChange={(e) => setStartTime(e.target.value)} className="w-full bg-slate-50 border border-slate-200 text-xs px-3 py-2 rounded-lg focus:outline-none" />
            </div>
            <div className="space-y-1">
              <label className="text-[10px] uppercase font-bold tracking-wider text-slate-500">{t("leaveRequestForm.endTimeLabel")}</label>
              <input type="time" value={endTime} onChange={(e) => setEndTime(e.target.value)} className="w-full bg-slate-50 border border-slate-200 text-xs px-3 py-2 rounded-lg focus:outline-none" />
            </div>
          </div>
        )}

        <div className="space-y-1">
          <label className="text-[10px] uppercase font-bold tracking-wider text-slate-500">{t("leaveRequestForm.reasonLabel")}</label>
          <textarea
            required
            rows={2}
            placeholder={t("leaveRequestForm.reasonPlaceholder")}
            value={reason}
            onChange={(e) => setReason(e.target.value)}
            className="w-full bg-slate-50 border border-slate-200 text-xs px-3 py-2 rounded-lg focus:outline-none"
          />
        </div>

        {isTeacher && loadingSessions && <p className="text-[11px] text-slate-400 italic">{t("leaveRequestForm.checkingSchedule")}</p>}

        {isTeacher && !loadingSessions && needsSubstituteSelection && (
          <div className="space-y-2.5 border border-amber-200 bg-amber-50/60 rounded-lg p-3">
            <p className="text-[11px] font-bold text-amber-800">{t("leaveRequestForm.substituteNotice")}</p>

            <div className="space-y-1">
              <label className="text-[10px] uppercase font-bold tracking-wider text-slate-500">{t("leaveRequestForm.selectClassLabel")}</label>
              <Select
                value={selectedClassId ?? ""}
                onChange={(e) => setSelectedClassId(e.target.value ? Number(e.target.value) : null)}
                className="w-full bg-white border border-slate-200 text-xs px-3 py-2 rounded-lg focus:outline-none"
              >
                <option value="">{t("leaveRequestForm.selectClassPlaceholder")}</option>
                {Array.from(sessionsByClass.entries()).map(([classId, { className, sessions }]) => (
                  <option key={classId} value={classId}>
                    {className} ({t("leaveRequestForm.sessionsCount", { count: sessions.length })})
                  </option>
                ))}
              </Select>
              {sessionsByClass.size > 1 && (
                <p className="text-[10px] text-amber-700 italic">
                  {t("leaveRequestForm.multiClassWarning", { count: sessionsByClass.size })}
                </p>
              )}
            </div>

            {selectedClassId != null && (
              <div className="space-y-2">
                <div className="flex items-center justify-between">
                  <label className="text-[10px] uppercase font-bold tracking-wider text-slate-500">{t("leaveRequestForm.substituteTeacherLabel")}</label>
                  <button
                    type="button"
                    onClick={() => {
                      const first = Object.values(substitutes).find((u): u is TeacherLookupResponse => !!u);
                      if (first) applySameSubstituteToAll(first);
                    }}
                    className="text-[10px] font-bold text-slate-500 hover:text-slate-800 flex items-center gap-1"
                  >
                    <Repeat className="w-3 h-3" /> {t("leaveRequestForm.applyToAll")}
                  </button>
                </div>
                {selectedClassSessions.map((s) => (
                  <div key={s.id} className="bg-white border border-slate-200 rounded-lg p-2 space-y-1.5">
                    <p className="text-[11px] text-slate-600 font-semibold">
                      {t("leaveRequestForm.sessionTime", {
                        date: s.sessionDate,
                        startTime: s.startTime.slice(0, 5),
                        endTime: s.endTime.slice(0, 5)
                      })}
                    </p>
                    <SubstituteTeacherCombobox
                      value={substitutes[s.id] ?? null}
                      onChange={(u) => setSubstitutes((prev) => ({ ...prev, [s.id]: u }))}
                      placeholder={t("leaveRequestForm.substitutePlaceholder")}
                    />
                  </div>
                ))}
              </div>
            )}
          </div>
        )}

        </form>
      </div>

      <div className="px-5 py-4 bg-slate-50 border-t border-slate-100 flex justify-end">
        <button
          type="submit"
          form="leave-request-form"
          disabled={submitting}
          className="bg-brand-gradient hover:opacity-95 disabled:opacity-60 text-white font-semibold text-xs px-4 py-2 rounded-lg flex items-center gap-1.5 shadow-glow transition-all"
        >
          <PlusCircle className="w-4 h-4 text-white" />
          {submitting ? t("leaveRequestForm.submitting") : t("leaveRequestForm.submitButton")}
        </button>
      </div>
    </div>
  );
}
