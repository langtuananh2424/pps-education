import React, { useEffect, useState } from "react";
import { ApiError } from "@/lib/apiClient";
import { useApp } from "@/context/AppContext";
import { ClassEnrollmentResponse, ClassResponse, StudentCommentResponse, listClassEnrollments, listClassTeachers, listClasses, listComments } from "../api";
import Card from "@/components/ui/Card";
import CommentForm from "./CommentForm";
import CommentHistoryList from "./CommentHistoryList";

const inputClass = "bg-slate-50 border border-slate-200 text-xs p-2 rounded-lg focus:outline-none";

/** UC-21 nhánh MID_TERM/END_TERM: chọn lớp + học sinh + kỳ điểm, viết nhận xét định kỳ (không gắn buổi học). */
export default function PeriodicCommentPanel() {
  const { hasPermission, currentUser } = useApp();
  const canManage = hasPermission("academic.class.manage");
  const [classes, setClasses] = useState<ClassResponse[]>([]);
  const [selectedClassId, setSelectedClassId] = useState<number | null>(null);
  const [enrollments, setEnrollments] = useState<ClassEnrollmentResponse[]>([]);
  const [selectedStudentId, setSelectedStudentId] = useState<number | null>(null);
  const [history, setHistory] = useState<StudentCommentResponse[]>([]);
  const [error, setError] = useState<string | null>(null);

  const selectedClass = classes.find((c) => c.id === selectedClassId) ?? null;

  /** UC-21 Precondition: GV chỉ nhận xét lớp mình được phân công dạy (class_teachers) — cùng gốc rễ với fix ở DailyCommentPanel/GradesPage/ClassesPage/AttendancePage. */
  useEffect(() => {
    listClasses()
      .then(async (res) => {
        if (canManage || !currentUser) {
          setClasses(res);
          return;
        }
        const teacherLists = await Promise.all(res.map((c) => listClassTeachers(c.id).catch(() => [])));
        setClasses(res.filter((_, i) => teacherLists[i].some((t) => t.teacherUserId === currentUser.id)));
      })
      .catch(() => undefined);
  }, [canManage, currentUser]);

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
          <select value={selectedClassId ?? ""} onChange={(e) => setSelectedClassId(e.target.value ? Number(e.target.value) : null)} className={`${inputClass} w-full`}>
            <option value="">-- Chọn lớp --</option>
            {classes.map((c) => (
              <option key={c.id} value={c.id}>
                {c.classCode} — {c.name}
              </option>
            ))}
          </select>
          <select
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
          </select>
        </div>

        {selectedClass && selectedStudentId ? (
          <CommentForm classId={selectedClass.id} studentId={selectedStudentId} curriculumId={selectedClass.curriculumId} onSubmitted={loadHistory} />
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
