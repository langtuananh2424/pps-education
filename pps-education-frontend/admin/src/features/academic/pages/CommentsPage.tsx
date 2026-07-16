import React, { useEffect, useState } from "react";
import { ApiError } from "@/lib/apiClient";
import { ClassEnrollmentResponse, ClassResponse, StudentCommentResponse, listClassEnrollments, listClasses, listComments, submitComments } from "../api";
import Card from "@/components/ui/Card";
import Badge from "@/components/ui/Badge";
import Button from "@/components/ui/Button";
import CommentForm from "../components/CommentForm";
import CommentApprovalQueue from "../components/CommentApprovalQueue";

const inputClass = "bg-slate-50 border border-slate-200 text-xs p-2 rounded-lg focus:outline-none";
const statusLabels: Record<StudentCommentResponse["status"], string> = { DRAFT: "Nháp", PENDING: "Chờ duyệt", APPROVED: "Đã duyệt", REJECTED: "Bị từ chối" };

export default function CommentsPage() {
  const [classes, setClasses] = useState<ClassResponse[]>([]);
  const [selectedClassId, setSelectedClassId] = useState<number | null>(null);
  const [enrollments, setEnrollments] = useState<ClassEnrollmentResponse[]>([]);
  const [selectedStudentId, setSelectedStudentId] = useState<number | null>(null);
  const [history, setHistory] = useState<StudentCommentResponse[]>([]);
  const [error, setError] = useState<string | null>(null);

  const selectedClass = classes.find((c) => c.id === selectedClassId) ?? null;

  useEffect(() => {
    listClasses().then(setClasses).catch(() => undefined);
  }, []);

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

  const handleSubmitDraft = async (commentId: number) => {
    if (!selectedClassId) return;
    try {
      await submitComments(selectedClassId, [commentId]);
      loadHistory();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Nộp nhận xét thất bại.");
    }
  };

  return (
    <div className="space-y-6">
      <div className="border-b border-slate-200 pb-4">
        <h1 className="text-xl font-bold font-display tracking-tight text-slate-900">Nhận xét học viên (UC-21/22)</h1>
        <p className="text-xs text-slate-500 mt-1">Giáo viên viết nhận xét định kỳ/hàng ngày, Quản lý điểm trường duyệt trước khi hiển thị Portal phụ huynh.</p>
      </div>

      {error && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg">{error}</div>}

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        <Card className="space-y-4">
          <h3 className="text-xs font-bold text-slate-400 block uppercase tracking-wider font-display border-b border-slate-100 pb-2">Viết nhận xét (UC-21)</h3>

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

          {history.length > 0 && (
            <div className="space-y-2 border-t border-slate-100 pt-3">
              <span className="text-[10px] font-bold uppercase text-slate-500">Lịch sử nhận xét học sinh này</span>
              {history.map((h) => (
                <div key={h.id} className="border border-slate-200 rounded-lg p-2.5 text-[11px] space-y-1">
                  <div className="flex items-center justify-between">
                    <span className="text-slate-400">{h.commentDate}</span>
                    <Badge variant={h.status === "APPROVED" ? "success" : h.status === "REJECTED" ? "danger" : h.status === "PENDING" ? "warning" : "neutral"}>
                      {statusLabels[h.status]}
                    </Badge>
                  </div>
                  <p className="text-slate-700">{h.content}</p>
                  {h.status === "DRAFT" && (
                    <Button size="sm" variant="secondary" onClick={() => handleSubmitDraft(h.id)}>
                      Nộp duyệt
                    </Button>
                  )}
                </div>
              ))}
            </div>
          )}
        </Card>

        <Card padded={false} className="lg:col-span-2 overflow-hidden">
          <div className="px-5 py-4 border-b border-slate-100 bg-slate-50">
            <span className="text-xs font-bold text-slate-700 font-display">Phê duyệt Nhận xét Học viên (UC-22)</span>
          </div>
          <CommentApprovalQueue />
        </Card>
      </div>
    </div>
  );
}
