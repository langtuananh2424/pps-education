import React, { useEffect, useState } from "react";
import {
  ClassEnrollmentResponse,
  ClassSessionResponse,
  GradeComponentResponse,
  GradePeriodResponse,
  GradePeriodResultResponse,
  StudentCommentResponse,
  getAttendanceSession,
  listClassSessions,
  listComments,
  listGradeComponents,
  listGradeEntries,
  listGradePeriods,
  listPeriodResults
} from "../api";
import Modal from "@/components/ui/Modal";
import Badge from "@/components/ui/Badge";
import Select from "@/components/ui/Select";

const inputClass = "bg-slate-50 border border-slate-200 text-xs p-2 rounded-lg focus:outline-none";
const labelClass = "text-[10px] uppercase font-bold text-slate-500 block mb-1";

const attendanceStatusLabels: Record<string, string> = {
  PRESENT: "Có mặt",
  ABSENT: "Vắng",
  LATE: "Đi trễ",
  EARLY_LEAVE: "Về sớm",
  EXCUSED: "Vắng có phép"
};

const commentTypeLabels: Record<StudentCommentResponse["commentType"], string> = {
  DAILY: "Hàng ngày",
  MID_TERM: "Giữa kỳ",
  END_TERM: "Cuối kỳ"
};

interface StudentInfoModalProps {
  enrollment: ClassEnrollmentResponse;
  classId: number;
  curriculumId: number;
  onClose: () => void;
}

/**
 * Popup xem nhanh 1 học sinh cho Giáo viên — chỉ dùng API không đòi quyền student.profile.view
 * (đã dính lỗi 403 ở lần trước): điểm theo kỳ, tỉ lệ chuyên cần, nhận xét gần đây — đều là dữ liệu
 * Giáo viên đã có quyền xem/thao tác sẵn ở các tab khác của Quản lý lớp học (UC-18/19/21).
 */
export default function StudentInfoModal({ enrollment, classId, curriculumId, onClose }: StudentInfoModalProps) {
  const [gradePeriods, setGradePeriods] = useState<GradePeriodResponse[]>([]);
  const [selectedPeriodId, setSelectedPeriodId] = useState<number | null>(null);
  const [componentScores, setComponentScores] = useState<{ component: GradeComponentResponse; score: number | null }[]>([]);
  const [periodResult, setPeriodResult] = useState<GradePeriodResultResponse | null>(null);
  const [loadingGrades, setLoadingGrades] = useState(false);

  const [attendanceCounts, setAttendanceCounts] = useState<Record<string, number> | null>(null);
  const [attendanceTotal, setAttendanceTotal] = useState(0);
  const [loadingAttendance, setLoadingAttendance] = useState(true);

  const [comments, setComments] = useState<StudentCommentResponse[]>([]);
  const [loadingComments, setLoadingComments] = useState(true);

  useEffect(() => {
    listGradePeriods(curriculumId)
      .then((periods) => {
        setGradePeriods(periods);
        const latest = [...periods].sort((a, b) => b.displayOrder - a.displayOrder)[0];
        setSelectedPeriodId((prev) => prev ?? latest?.id ?? null);
      })
      .catch(() => undefined);
  }, [curriculumId]);

  useEffect(() => {
    if (!selectedPeriodId) {
      setComponentScores([]);
      setPeriodResult(null);
      return;
    }
    setLoadingGrades(true);
    listGradeComponents(selectedPeriodId)
      .then(async (components) => {
        const entriesByComponent = await Promise.all(components.map((c) => listGradeEntries(classId, c.id).catch(() => [])));
        setComponentScores(
          components.map((component, i) => ({
            component,
            score: entriesByComponent[i].find((e) => e.studentId === enrollment.studentId)?.score ?? null
          }))
        );
      })
      .finally(() => setLoadingGrades(false));
    listPeriodResults(classId, selectedPeriodId)
      .then((results) => setPeriodResult(results.find((r) => r.studentId === enrollment.studentId) ?? null))
      .catch(() => setPeriodResult(null));
  }, [selectedPeriodId, classId, enrollment.studentId]);

  useEffect(() => {
    setLoadingAttendance(true);
    listClassSessions(classId)
      .then(async (sessions: ClassSessionResponse[]) => {
        const attendanceSessions = await Promise.all(sessions.map((s) => getAttendanceSession(s.id).catch(() => null)));
        const counts: Record<string, number> = {};
        let total = 0;
        attendanceSessions.forEach((session) => {
          const mark = session?.marks.find((m) => m.studentId === enrollment.studentId);
          if (mark) {
            counts[mark.status] = (counts[mark.status] ?? 0) + 1;
            total += 1;
          }
        });
        setAttendanceCounts(counts);
        setAttendanceTotal(total);
      })
      .finally(() => setLoadingAttendance(false));
  }, [classId, enrollment.studentId]);

  useEffect(() => {
    setLoadingComments(true);
    listComments(classId, enrollment.studentId)
      .then((list) => setComments([...list].sort((a, b) => b.commentDate.localeCompare(a.commentDate)).slice(0, 5)))
      .catch(() => setComments([]))
      .finally(() => setLoadingComments(false));
  }, [classId, enrollment.studentId]);

  return (
    <Modal open onClose={onClose} title="Thông tin học sinh" size="lg">
      <div className="space-y-5">
        <div className="flex items-center justify-between flex-wrap gap-2">
          <div>
            <p className="text-sm font-bold text-slate-800">{enrollment.studentFullName}</p>
            <p className="text-[10px] font-mono text-slate-400">{enrollment.studentCode}</p>
          </div>
          <Badge variant={enrollment.status === "ACTIVE" ? "success" : "neutral"}>{enrollment.status}</Badge>
        </div>

        <div>
          <div className="flex items-center justify-between mb-2">
            <span className="text-[10px] font-bold uppercase text-slate-500">Điểm số</span>
            {gradePeriods.length > 0 && (
              <Select
                value={selectedPeriodId ?? ""}
                onChange={(e) => setSelectedPeriodId(e.target.value ? Number(e.target.value) : null)}
                className={inputClass}
              >
                {gradePeriods.map((p) => (
                  <option key={p.id} value={p.id}>
                    {p.name}
                  </option>
                ))}
              </Select>
            )}
          </div>
          {gradePeriods.length === 0 ? (
            <p className="text-xs text-slate-400 italic">Khung chương trình chưa có kỳ điểm nào.</p>
          ) : loadingGrades ? (
            <p className="text-xs text-slate-500">Đang tải...</p>
          ) : componentScores.length === 0 ? (
            <p className="text-xs text-slate-400 italic">Kỳ điểm này chưa có đầu điểm nào.</p>
          ) : (
            <div className="space-y-1.5">
              <div className="flex flex-wrap gap-1.5">
                {componentScores.map(({ component, score }) => (
                  <span key={component.id} className="bg-slate-100 border border-slate-200 text-slate-700 text-[11px] font-semibold px-2 py-1 rounded-lg">
                    {component.name}: {score ?? "—"}
                  </span>
                ))}
              </div>
              {periodResult && (
                <p className="text-xs text-slate-600">
                  Tổng kết kỳ: <span className="font-bold text-slate-800">{periodResult.overallScore ?? "—"}</span>
                  {periodResult.level && ` (${periodResult.level})`}
                </p>
              )}
            </div>
          )}
        </div>

        <div>
          <span className="text-[10px] font-bold uppercase text-slate-500 block mb-2">Chuyên cần</span>
          {loadingAttendance ? (
            <p className="text-xs text-slate-500">Đang tải...</p>
          ) : attendanceTotal === 0 ? (
            <p className="text-xs text-slate-400 italic">Chưa có dữ liệu điểm danh.</p>
          ) : (
            <div className="flex flex-wrap gap-1.5">
              {Object.entries(attendanceCounts ?? {}).map(([status, count]) => (
                <span key={status} className="bg-slate-100 border border-slate-200 text-slate-700 text-[11px] font-semibold px-2 py-1 rounded-lg">
                  {attendanceStatusLabels[status] ?? status}: {count}
                </span>
              ))}
              <span className="text-[11px] text-slate-400 font-semibold px-1 py-1">/ {attendanceTotal} buổi đã điểm danh</span>
            </div>
          )}
        </div>

        <div>
          <span className="text-[10px] font-bold uppercase text-slate-500 block mb-2">Nhận xét gần đây</span>
          {loadingComments ? (
            <p className="text-xs text-slate-500">Đang tải...</p>
          ) : comments.length === 0 ? (
            <p className="text-xs text-slate-400 italic">Chưa có nhận xét nào.</p>
          ) : (
            <div className="space-y-2">
              {comments.map((c) => (
                <div key={c.id} className="border border-slate-200 rounded-lg p-2.5 text-xs">
                  <div className="flex items-center gap-2 mb-1">
                    <Badge variant="info">{commentTypeLabels[c.commentType]}</Badge>
                    <span className="text-slate-400">{c.commentDate}</span>
                  </div>
                  <p className="text-slate-700">{c.content}</p>
                </div>
              ))}
            </div>
          )}
        </div>

        <p className={`${labelClass} pt-2 border-t border-slate-100`}>
          Ngày ghi danh: <span className="normal-case font-semibold text-slate-600">{enrollment.enrolledDate}</span>
          {enrollment.withdrawnDate && (
            <>
              {" · "}Ngày rút: <span className="normal-case font-semibold text-slate-600">{enrollment.withdrawnDate}</span>
            </>
          )}
        </p>
      </div>
    </Modal>
  );
}
