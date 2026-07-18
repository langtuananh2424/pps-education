import React from "react";
import { Flag } from "lucide-react";
import { StudentCommentResponse } from "../api";
import Badge from "@/components/ui/Badge";

const commentTypeLabels: Record<StudentCommentResponse["commentType"], string> = { DAILY: "Hàng ngày", MID_TERM: "Giữa kỳ", END_TERM: "Cuối kỳ" };

interface CommentApprovalListProps {
  items: StudentCommentResponse[];
  loading: boolean;
  selectedId: number | null;
  onSelect: (id: number) => void;
}

/** UC-22 bước 1: danh sách gọn các yêu cầu chờ duyệt — bấm vào 1 dòng mới xem chi tiết, không hiện hết nội dung ngay. */
export default function CommentApprovalList({ items, loading, selectedId, onSelect }: CommentApprovalListProps) {
  if (loading) return <p className="text-xs text-slate-500 p-4">Đang tải...</p>;
  if (items.length === 0) return <p className="text-xs text-slate-400 italic text-center py-6">Chưa có nhận xét nào chờ duyệt.</p>;

  return (
    <div className="divide-y divide-slate-100">
      {items.map((cm) => (
        <button
          key={cm.id}
          onClick={() => onSelect(cm.id)}
          className={`w-full text-left px-4 py-3 flex items-center justify-between gap-3 transition-all ${
            selectedId === cm.id ? "bg-orange-50" : "hover:bg-slate-50"
          }`}
        >
          <div className="flex items-center gap-2 min-w-0">
            <span className="text-xs font-bold text-slate-800 truncate">{cm.studentFullName}</span>
            <Badge variant="info">{commentTypeLabels[cm.commentType]}</Badge>
            {cm.isWarning && <Flag className="w-3.5 h-3.5 text-rose-500 shrink-0" />}
          </div>
          <span className="text-[10px] text-slate-400 shrink-0">{cm.commentDate}</span>
        </button>
      ))}
    </div>
  );
}
