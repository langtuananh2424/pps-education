import React, { useEffect, useState } from "react";
import {
  ClassEnrollmentResponse,
  ClassSessionResponse,
  GradeComponentSetupResponse,
  GradeEvaluationComponentResponse,
  GradeEvaluationResultResponse,
  StudentCommentResponse,
  getAttendanceSession,
  listClassSessions,
  listComments,
  listEvaluationResults,
  listGradeComponentSetups,
  listGradeEntries,
  listGradeEvaluationComponents
} from "../api";
import Modal from "@/components/ui/Modal";
import Badge from "@/components/ui/Badge";
import Select from "@/components/ui/Select";
import ClassGradeComparisonTable from "./ClassGradeComparisonTable";

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
  onClose: () => void;
}

function setupLabel(s: GradeComponentSetupResponse): string {
  return `${s.academicTermName} — ${s.evaluationType === "MID_TERM" ? "Giữa kỳ" : "Cuối kỳ"}`;
}

/**
 * Popup xem nhanh 1 học sinh cho Giáo viên — chỉ dùng API không đòi quyền student.profile.view
 * (đã dính lỗi 403 ở lần trước): điểm theo kỳ, tỉ lệ chuyên cần, nhận xét gần đây — đều là dữ liệu
 * Giáo viên đã có quyền xem/thao tác sẵn ở các tab khác của Quản lý lớp học (UC-18/19/21).
 * V94: setup sổ điểm gắn thẳng theo lớp (classId), không cần curriculumId nữa.
 */
export default function StudentInfoModal({ enrollment, classId, onClose }: StudentInfoModalProps) {
  const [setups, setSetups] = useState<GradeComponentSetupResponse[]>([]);
  const [selectedSetupId, setSelectedSetupId] = useState<number | null>(null);
  const [componentScores, setComponentScores] = useState<{ component: GradeEvaluationComponentResponse; score: number | null }[]>([]);
  const [evaluationResult, setEvaluationResult] = useState<GradeEvaluationResultResponse | null>(null);
  const [loadingGrades, setLoadingGrades] = useState(false);

  const [attendanceCounts, setAttendanceCounts] = useState<Record<string, number> | null>(null);
  const [attendanceTotal, setAttendanceTotal] = useState(0);
  const [loadingAttendance, setLoadingAttendance] = useState(true);

  const [comments, setComments] = useState<StudentCommentResponse[]>([]);
  const [loadingComments, setLoadingComments] = useState(true);

  useEffect(() => {
    listGradeComponentSetups(classId)
      .then((s) => {
        setSetups(s);
        const latest = [...s].sort((a, b) => a.academicTermId - b.academicTermId)[s.length - 1];
        setSelectedSetupId((prev) => prev ?? latest?.id ?? null);
      })
      .catch(() => undefined);
  }, [classId]);

  useEffect(() => {
    if (!selectedSetupId) {
      setComponentScores([]);
      setEvaluationResult(null);
      return;
    }
    setLoadingGrades(true);
    listGradeEvaluationComponents(selectedSetupId)
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
    listEvaluationResults(classId, selectedSetupId)
      .then((results) => setEvaluationResult(results.find((r) => r.studentId === enrollment.studentId) ?? null))
      .catch(() => setEvaluationResult(null));
  }, [selectedSetupId, classId, enrollment.studentId]);

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
            {setups.length > 0 && (
              <Select
                value={selectedSetupId ?? ""}
                onChange={(e) => setSelectedSetupId(e.target.value ? Number(e.target.value) : null)}
                className={inputClass}
              >
                {setups.map((s) => (
                  <option key={s.id} value={s.id}>
                    {setupLabel(s)}
                  </option>
                ))}
              </Select>
            )}
          </div>
          {setups.length === 0 ? (
            <p className="text-xs text-slate-400 italic">Lớp chưa có setup sổ điểm nào.</p>
          ) : loadingGrades ? (
            <p className="text-xs text-slate-500">Đang tải...</p>
          ) : componentScores.length === 0 ? (
            <p className="text-xs text-slate-400 italic">Setup này chưa có đầu điểm nào.</p>
          ) : (
            <div className="space-y-1.5">
              <div className="flex flex-wrap gap-1.5">
                {componentScores.map(({ component, score }) => (
                  <span key={component.id} className="bg-slate-100 border border-slate-200 text-slate-700 text-[11px] font-semibold px-2 py-1 rounded-lg">
                    {component.name}: {score ?? "—"}
                  </span>
                ))}
              </div>
              {evaluationResult && (
                <p className="text-xs text-slate-600">
                  Tổng kết kỳ: <span className="font-bold text-slate-800">{evaluationResult.overallScore ?? "—"}</span>
                  {evaluationResult.level && ` (${evaluationResult.level})`}
                  {evaluationResult.comment && <span className="block text-slate-500 mt-0.5">Nhận xét: {evaluationResult.comment}</span>}
                </p>
              )}
            </div>
          )}
        </div>

        <div>
          <span className="text-[10px] font-bold uppercase text-slate-500 block mb-2">So sánh điểm số qua các kỳ</span>
          {/* Tái dùng nguyên bảng tổng hợp cả lớp (ClassGradeSheetPanel dùng để so sánh tiến bộ qua các
              kỳ) nhưng chỉ truyền đúng 1 học sinh này — tránh viết lại logic gộp component theo `code`
              xuyên suốt các kỳ + icon xu hướng tăng/giảm đã có sẵn (2026-07-30). includeAllStatuses để
              vẫn hiện được kể cả học sinh đã rút lớp (enrollment.status !== ACTIVE). */}
          <div className="border border-slate-200 rounded-lg overflow-hidden">
            <ClassGradeComparisonTable classId={classId} enrollments={[enrollment]} includeAllStatuses />
          </div>
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
