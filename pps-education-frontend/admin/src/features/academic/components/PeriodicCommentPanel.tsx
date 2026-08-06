import React, { useEffect, useState } from "react";
import { ApiError } from "@/lib/apiClient";
import { useApp } from "@/context/AppContext";
import { ClassEnrollmentResponse, StudentCommentResponse, listClassEnrollments, listComments } from "../api";
import { useEligibleClasses } from "../hooks/useEligibleClasses";
import Card from "@/components/ui/Card";
import CommentForm from "./CommentForm";
import CommentHistoryList from "./CommentHistoryList";
import Select from "@/components/ui/Select";

const inputClass = "bg-slate-50 border border-slate-200 text-xs p-2 rounded-lg focus:outline-none";

/** UC-21 nhánh MID_TERM/END_TERM: chọn lớp + học sinh + kỳ điểm, viết nhận xét định kỳ (không gắn buổi học). */
export default function PeriodicCommentPanel() {
  const { selectedClassId } = useApp();
  const { classes } = useEligibleClasses();
  const [enrollments, setEnrollments] = useState<ClassEnrollmentResponse[]>([]);
  const [selectedStudentId, setSelectedStudentId] = useState<number | null>(null);
  const [history, setHistory] = useState<StudentCommentResponse[]>([]);
  const [error, setError] = useState<string | null>(null);

  const selectedClass = classes.find((c) => c.id === selectedClassId) ?? null;

  useEffect(() => {
    setSelectedStudentId(null);
    setHistory([]);
    if (!selectedClassId) return;
    listClassEnrollments(selectedClassId).then(setEnrollments).catch(() => undefined);
  }, [selectedClassId]);

  const loadHistory = () => {
    if (!selectedClassId || !selectedStudentId) return;
    listComments(selectedClassId, selectedStudentId)
      .then(setHistory)
      .catch((err) => setError(err instanceof ApiError ? err.message : "Không tải được lịch sử nhận xét."));
  };
  useEffect(loadHistory, [selectedClassId, selectedStudentId]);

  return (
    <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
      {error && <div className="lg:col-span-3 text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg">{error}</div>}

      <Card className="lg:col-span-1 space-y-4">
        <h3 className="text-xs font-bold text-slate-400 block uppercase tracking-wider font-display border-b border-slate-100 pb-2">Viết nhận xét định kỳ</h3>

        <div className="space-y-2">
          <p className="text-xs font-bold text-slate-700">
            {selectedClass ? `Lớp: ${selectedClass.classCode} — ${selectedClass.name}` : "Chưa chọn lớp — chọn ở góc trên bên phải (Header)"}
          </p>
          <Select
            value={selectedStudentId ?? ""}
            onChange={(e) => setSelectedStudentId(e.target.value ? Number(e.target.value) : null)}
            disabled={!selectedClassId}
            className={`${inputClass} w-full disabled:opacity-50`}
          >
            <option value="">-- Chọn học sinh --</option>
            {enrollments
              .filter((en) => en.status === "ACTIVE")
              .map((en) => (
                <option key={en.studentId} value={en.studentId}>
                  {en.studentFullName} ({en.studentCode})
                </option>
              ))}
          </Select>
        </div>

        {selectedClass && selectedStudentId ? (
          <CommentForm classId={selectedClass.id} studentId={selectedStudentId} siteId={selectedClass.siteId} onSubmitted={loadHistory} />
        ) : (
          <p className="text-xs text-slate-400 italic">Chọn lớp và học sinh để viết nhận xét.</p>
        )}
      </Card>

      <Card className="lg:col-span-2 space-y-2">
        <h3 className="text-xs font-bold text-slate-400 block uppercase tracking-wider font-display border-b border-slate-100 pb-2">Lịch sử nhận xét định kỳ</h3>
        {!selectedStudentId ? (
          <p className="text-xs text-slate-400 italic py-6 text-center">Chọn lớp và học sinh để xem lịch sử.</p>
        ) : (
          selectedClassId && <CommentHistoryList classId={selectedClassId} history={history} onChanged={loadHistory} />
        )}
      </Card>
    </div>
  );
}
