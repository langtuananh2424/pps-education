import React, { useState } from "react";
import { Send } from "lucide-react";
import { Task } from "@/types";
import Modal from "@/components/ui/Modal";
import Select from "@/components/ui/Select";

interface TaskDetailModalProps {
  task: Task;
  onClose: () => void;
  onChangeStatus: (status: Task["status"]) => void;
  onAddComment: (content: string) => void;
}

export default function TaskDetailModal({ task, onClose, onChangeStatus, onAddComment }: TaskDetailModalProps) {
  const [newComment, setNewComment] = useState("");

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!newComment.trim()) return;
    onAddComment(newComment);
    setNewComment("");
  };

  return (
    <Modal open onClose={onClose} title={task.title} description={`Mã tác vụ: ${task.id}`} size="lg">
      <div className="space-y-4">
        <div className="space-y-1">
          <span className="text-[10px] uppercase font-bold tracking-wider text-slate-400 block font-display">Mô tả chi tiết</span>
          <p className="text-xs text-slate-700 leading-relaxed bg-slate-50 p-3 rounded-lg border border-slate-100">{task.description}</p>
        </div>

        <div className="grid grid-cols-2 gap-4 bg-slate-50 p-3.5 rounded-lg border border-slate-100">
          <div>
            <span className="text-[10px] uppercase font-bold tracking-wider text-slate-400 block font-display">Cập nhật Trạng thái (UC-07)</span>
            <Select
              value={task.status}
              onChange={(e) => onChangeStatus(e.target.value as Task["status"])}
              className="mt-1 w-full bg-white border border-slate-200 text-xs px-2.5 py-1.5 rounded-lg focus:outline-none font-semibold text-slate-700"
            >
              <option value="TODO">Cần Làm (Todo)</option>
              <option value="IN_PROGRESS">Đang làm (In Progress)</option>
              <option value="WAITING_APPROVAL">Chờ duyệt (Waiting Approval)</option>
              <option value="COMPLETED">Đã Hoàn thành (Completed)</option>
            </Select>
          </div>

          <div className="text-right flex flex-col justify-end">
            <span className="text-[10px] text-slate-400 font-medium">Bàn giao bởi:</span>
            <span className="text-xs font-bold text-slate-800 block mt-0.5">{task.assignedBy}</span>
          </div>
        </div>

        <div className="space-y-3.5">
          <span className="text-[10px] uppercase font-bold tracking-wider text-slate-400 block font-display">Lịch sử Trao Đổi</span>

          <div className="space-y-2.5">
            {(task.comments || []).map((cmt) => (
              <div key={cmt.id} className="p-2.5 rounded-lg bg-slate-50 border border-slate-100 text-xs">
                <div className="flex items-center justify-between font-semibold text-slate-700">
                  <span>{cmt.authorName}</span>
                  <span className="text-[9px] text-slate-400 font-mono">{cmt.createdAt}</span>
                </div>
                <p className="text-slate-600 mt-1">{cmt.content}</p>
              </div>
            ))}

            {(task.comments || []).length === 0 && <p className="text-xs text-slate-400 italic">Chưa có bình luận hay báo cáo phản hồi nào.</p>}
          </div>

          <form onSubmit={handleSubmit} className="flex gap-2">
            <input
              type="text"
              required
              placeholder="Viết phản hồi tiến độ..."
              value={newComment}
              onChange={(e) => setNewComment(e.target.value)}
              className="flex-1 bg-slate-50 border border-slate-200 text-xs px-3 py-2 rounded-lg focus:outline-none"
            />
            <button type="submit" className="bg-slate-900 hover:bg-slate-800 text-white p-2 rounded-lg shrink-0">
              <Send className="w-4 h-4 text-brand-yellow" />
            </button>
          </form>
        </div>
      </div>
    </Modal>
  );
}
