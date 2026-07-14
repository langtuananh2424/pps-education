import React, { useState } from "react";
import { Flag, Plus } from "lucide-react";
import { CommentEntry } from "@/types";

interface CommentFormProps {
  onSubmit: (type: CommentEntry["type"], content: string, isWarning: boolean) => void;
}

export default function CommentForm({ onSubmit }: CommentFormProps) {
  const [type, setType] = useState<CommentEntry["type"]>("DAILY");
  const [content, setContent] = useState("");
  const [isWarning, setIsWarning] = useState(false);

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!content) return;
    onSubmit(type, content, isWarning);
    setContent("");
    setIsWarning(false);
  };

  return (
    <form onSubmit={handleSubmit} className="space-y-3.5">
      <div className="space-y-1">
        <label className="text-[10px] uppercase font-bold tracking-wider text-slate-500">Hình thức nhận xét</label>
        <select value={type} onChange={(e) => setType(e.target.value as CommentEntry["type"])} className="w-full bg-slate-50 border border-slate-200 text-xs px-2.5 py-1.5 rounded-lg focus:outline-none">
          <option value="DAILY">Hàng ngày (Ý thức, bài tập về nhà)</option>
          <option value="MIDTERM">Định kỳ Giữa kỳ (Năng lực tiếp thu)</option>
          <option value="FINAL">Tổng kết Cuối học phần (Đánh giá chuẩn đầu ra)</option>
        </select>
      </div>

      <div className="space-y-1">
        <label className="text-[10px] uppercase font-bold tracking-wider text-slate-500">Nội dung nhận xét</label>
        <textarea
          required
          placeholder="Viết nhận xét chi tiết về thái độ, bài làm của bé..."
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
            <Flag className="w-3.5 h-3.5 shrink-0 animate-bounce" />
            Đánh dấu cảnh báo đặc biệt (is_warning)
          </span>
          <span className="text-[9px] text-slate-400 block mt-0.5">Hiển thị nổi bật hàng đầu tại Cổng Portal Phụ huynh để phụ huynh chú ý.</span>
        </div>
      </label>

      <button type="submit" className="w-full bg-slate-900 hover:bg-slate-800 text-white font-semibold text-xs py-2 rounded-lg flex items-center justify-center gap-1.5">
        <Plus className="w-4 h-4 text-brand-yellow" />
        Gửi duyệt nhận xét
      </button>
    </form>
  );
}
