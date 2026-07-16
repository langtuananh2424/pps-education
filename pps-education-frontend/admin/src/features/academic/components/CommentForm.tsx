import React, { useEffect, useState } from "react";
import { Flag, Plus } from "lucide-react";
import { ApiError } from "@/lib/apiClient";
import { ClassSessionResponse, CreateStudentCommentRequest, GradePeriodResponse, StudentCommentResponse, listClassSessions, listGradePeriods, writeComment } from "../api";

const inputClass = "w-full bg-slate-50 border border-slate-200 text-xs px-2.5 py-1.5 rounded-lg focus:outline-none";
const labelClass = "text-[10px] uppercase font-bold tracking-wider text-slate-500";

interface CommentFormProps {
  classId: number;
  studentId: number;
  curriculumId: number;
  onSubmitted: (comment: StudentCommentResponse) => void;
}

export default function CommentForm({ classId, studentId, curriculumId, onSubmitted }: CommentFormProps) {
  const [commentType, setCommentType] = useState<CreateStudentCommentRequest["commentType"]>("DAILY");
  const [sessions, setSessions] = useState<ClassSessionResponse[]>([]);
  const [periods, setPeriods] = useState<GradePeriodResponse[]>([]);
  const [classSessionId, setClassSessionId] = useState("");
  const [gradePeriodId, setGradePeriodId] = useState("");
  const [commentDate, setCommentDate] = useState(() => new Date().toISOString().slice(0, 10));
  const [content, setContent] = useState("");
  const [severity, setSeverity] = useState<NonNullable<CreateStudentCommentRequest["severity"]>>("NORMAL");
  const [isWarning, setIsWarning] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    listClassSessions(classId).then(setSessions).catch(() => undefined);
    listGradePeriods(curriculumId).then(setPeriods).catch(() => undefined);
  }, [classId, curriculumId]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!content.trim()) {
      setError("Vui lòng nhập nội dung nhận xét.");
      return;
    }
    if (commentType === "DAILY" && !classSessionId) {
      setError("Nhận xét hàng ngày cần chọn buổi học tương ứng.");
      return;
    }
    if ((commentType === "MID_TERM" || commentType === "END_TERM") && !gradePeriodId) {
      setError("Nhận xét định kỳ cần chọn kỳ điểm tương ứng.");
      return;
    }
    setSubmitting(true);
    setError(null);
    try {
      const created = await writeComment(classId, {
        studentId,
        commentType,
        classSessionId: commentType === "DAILY" ? Number(classSessionId) : undefined,
        gradePeriodId: commentType !== "DAILY" ? Number(gradePeriodId) : undefined,
        commentDate,
        content: content.trim(),
        severity,
        isWarning
      });
      onSubmitted(created);
      setContent("");
      setIsWarning(false);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Viết nhận xét thất bại.");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <form onSubmit={handleSubmit} className="space-y-3.5">
      {error && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg">{error}</div>}

      <div className="space-y-1">
        <label className={labelClass}>Hình thức nhận xét</label>
        <select value={commentType} onChange={(e) => setCommentType(e.target.value as CreateStudentCommentRequest["commentType"])} className={inputClass}>
          <option value="DAILY">Hàng ngày (theo buổi học)</option>
          <option value="MID_TERM">Định kỳ giữa kỳ</option>
          <option value="END_TERM">Tổng kết cuối kỳ</option>
        </select>
      </div>

      {commentType === "DAILY" ? (
        <div className="space-y-1">
          <label className={labelClass}>Buổi học</label>
          <select value={classSessionId} onChange={(e) => setClassSessionId(e.target.value)} className={inputClass}>
            <option value="">-- Chọn buổi học --</option>
            {sessions.map((s) => (
              <option key={s.id} value={s.id}>
                {s.sessionDate} ({s.startTime}–{s.endTime})
              </option>
            ))}
          </select>
        </div>
      ) : (
        <div className="space-y-1">
          <label className={labelClass}>Kỳ điểm</label>
          <select value={gradePeriodId} onChange={(e) => setGradePeriodId(e.target.value)} className={inputClass}>
            <option value="">-- Chọn kỳ điểm --</option>
            {periods.map((p) => (
              <option key={p.id} value={p.id}>
                {p.name}
              </option>
            ))}
          </select>
        </div>
      )}

      <div className="space-y-1">
        <label className={labelClass}>Ngày nhận xét</label>
        <input type="date" value={commentDate} onChange={(e) => setCommentDate(e.target.value)} className={inputClass} required />
      </div>

      <div className="space-y-1">
        <label className={labelClass}>Mức độ</label>
        <select value={severity} onChange={(e) => setSeverity(e.target.value as NonNullable<CreateStudentCommentRequest["severity"]>)} className={inputClass}>
          <option value="POSITIVE">Tích cực</option>
          <option value="NORMAL">Bình thường</option>
          <option value="CONCERN">Cần lưu ý</option>
          <option value="WARNING">Cảnh báo</option>
        </select>
      </div>

      <div className="space-y-1">
        <label className={labelClass}>Nội dung nhận xét</label>
        <textarea
          required
          placeholder="Viết nhận xét chi tiết về thái độ, bài làm của học sinh..."
          value={content}
          onChange={(e) => setContent(e.target.value)}
          rows={3}
          className="w-full bg-slate-50 border border-slate-200 text-xs px-3 py-2 rounded-lg focus:outline-none"
        />
      </div>

      <label className="flex items-center gap-2 cursor-pointer p-2 bg-rose-50/50 border border-rose-100 rounded-lg">
        <input type="checkbox" checked={isWarning} onChange={(e) => setIsWarning(e.target.checked)} className="h-4 w-4 text-brand-red focus:ring-brand-red border-slate-300 rounded" />
        <div>
          <span className="text-xs font-bold text-rose-600 flex items-center gap-1">
            <Flag className="w-3.5 h-3.5 shrink-0" />
            Đánh dấu cảnh báo đặc biệt
          </span>
          <span className="text-[9px] text-slate-400 block mt-0.5">Hiển thị nổi bật tại Cổng Portal Phụ huynh.</span>
        </div>
      </label>

      <button
        type="submit"
        disabled={submitting}
        className="w-full bg-slate-900 hover:bg-slate-800 text-white font-semibold text-xs py-2 rounded-lg flex items-center justify-center gap-1.5 disabled:opacity-50"
      >
        <Plus className="w-4 h-4 text-brand-yellow" />
        {submitting ? "Đang lưu..." : "Lưu nhận xét (nháp)"}
      </button>
    </form>
  );
}
