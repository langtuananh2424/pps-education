import React, { useEffect, useState } from "react";
import { Check, ClipboardList, Flag, X } from "lucide-react";
import { ApiError } from "@/lib/apiClient";
import { StudentCommentResponse, decideComments, listClassEnrollments } from "../api";
import Badge from "@/components/ui/Badge";
import TableContainer, { Td, Th } from "@/components/ui/TableContainer";

const commentTypeLabels: Record<StudentCommentResponse["commentType"], string> = { DAILY: "Hàng ngày", MID_TERM: "Giữa kỳ", END_TERM: "Cuối kỳ" };

interface CommentApprovalDetailProps {
  comment: StudentCommentResponse | null;
  onDecided: () => void;
}

/** UC-22 bước 2-3: xem chi tiết 1 yêu cầu đã chọn từ danh sách — cùng khuôn bảng Mã ID/Họ tên/Nhận xét như màn Giáo viên nhập, Duyệt hoặc Từ chối kèm lý do. */
export default function CommentApprovalDetail({ comment, onDecided }: CommentApprovalDetailProps) {
  const [studentCode, setStudentCode] = useState<string | null>(null);
  const [deciding, setDeciding] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    setStudentCode(null);
    if (!comment) return;
    // GET /api/students/{id} yêu cầu quyền student.profile.view (tách từ student.manage ở V44) — lấy
    // studentCode qua danh sách ghi danh của lớp thay vì tra thẳng hồ sơ học sinh, tránh phụ thuộc quyền đó.
    listClassEnrollments(comment.classId)
      .then((enrollments) => setStudentCode(enrollments.find((en) => en.studentId === comment.studentId)?.studentCode ?? null))
      .catch(() => undefined);
  }, [comment?.classId, comment?.studentId]);

  const handleDecide = async (decision: "APPROVED" | "REJECTED") => {
    if (!comment) return;
    let reason: string | undefined;
    if (decision === "REJECTED") {
      reason = window.prompt("Lý do từ chối (bắt buộc — giáo viên sẽ dựa vào đây để sửa lại):") ?? undefined;
      if (!reason?.trim()) return;
    }
    setDeciding(true);
    setError(null);
    try {
      await decideComments([comment.id], decision, reason?.trim());
      onDecided();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Duyệt nhận xét thất bại.");
    } finally {
      setDeciding(false);
    }
  };

  if (!comment) {
    return (
      <div className="bg-white rounded-xl border border-slate-200 shadow-soft flex flex-col items-center justify-center p-12 text-center text-slate-400 space-y-3">
        <ClipboardList className="w-12 h-12 text-slate-300" />
        <div>
          <h3 className="text-sm font-bold text-slate-700">Chưa chọn yêu cầu nào</h3>
          <p className="text-xs text-slate-400 mt-1">Chọn 1 yêu cầu ở danh sách bên dưới để xem chi tiết nhận xét.</p>
        </div>
      </div>
    );
  }

  return (
    <div className="bg-white rounded-xl border border-slate-200 shadow-soft overflow-hidden">
      <div className="px-5 py-4 border-b border-slate-100 bg-slate-50 flex items-center justify-between flex-wrap gap-2">
        <div>
          <span className="text-xs font-bold text-slate-700 font-display">Chi tiết yêu cầu duyệt nhận xét (UC-22)</span>
          <p className="text-[10px] text-slate-400 mt-0.5">{comment.commentDate}</p>
        </div>
        <div className="flex items-center gap-2">
          <Badge variant="info">{commentTypeLabels[comment.commentType]}</Badge>
          <Badge variant="warning">Chờ duyệt</Badge>
        </div>
      </div>

      {error && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 m-4 rounded-lg">{error}</div>}

      <TableContainer className="rounded-none border-0">
        <thead>
          <tr>
            <Th>Mã ID</Th>
            <Th>Họ và tên</Th>
            <Th>Nhận xét của giáo viên</Th>
          </tr>
        </thead>
        <tbody className="divide-y divide-slate-100">
          <tr>
            <Td className="font-mono font-bold text-slate-500 align-top">{studentCode ?? "—"}</Td>
            <Td className="font-bold text-slate-900 whitespace-nowrap align-top">{comment.studentFullName}</Td>
            <Td className="whitespace-pre-wrap">{comment.content}</Td>
          </tr>
        </tbody>
      </TableContainer>

      <div className="px-5 py-3 border-t border-slate-100 flex items-center justify-between flex-wrap gap-3">
        <div className="flex items-center gap-4 text-xs">
          {comment.isWarning && (
            <span className="text-rose-600 font-semibold flex items-center gap-1">
              <Flag className="w-3.5 h-3.5" /> Có cảnh báo đặc biệt
            </span>
          )}
        </div>

        <div className="flex justify-end gap-2">
          <button
            onClick={() => handleDecide("REJECTED")}
            disabled={deciding}
            className="px-3 py-1.5 text-rose-600 hover:bg-rose-50 border border-rose-200 text-xs font-bold rounded-lg disabled:opacity-50"
          >
            <X className="w-3.5 h-3.5 inline mr-1" />
            Từ chối
          </button>
          <button
            onClick={() => handleDecide("APPROVED")}
            disabled={deciding}
            className="px-4 py-1.5 bg-slate-900 hover:bg-slate-800 text-white text-xs font-bold rounded-lg flex items-center gap-1.5 disabled:opacity-50"
          >
            <Check className="w-3.5 h-3.5 text-brand-yellow" />
            {deciding ? "Đang xử lý..." : "Duyệt nhận xét"}
          </button>
        </div>
      </div>
    </div>
  );
}
