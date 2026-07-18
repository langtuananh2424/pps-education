import React, { useState } from "react";
import { AlertTriangle, PlusCircle } from "lucide-react";
import { CommentEntry } from "@/types";

interface StudentCommentsTabProps {
  comments: CommentEntry[];
  onAddComment: (type: CommentEntry["type"], content: string, isWarning: boolean) => void;
}

export default function StudentCommentsTab({ comments, onAddComment }: StudentCommentsTabProps) {
  const [type, setType] = useState<CommentEntry["type"]>("DAILY");
  const [content, setContent] = useState("");
  const [isWarning, setIsWarning] = useState(false);

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!content.trim()) return;
    onAddComment(type, content, isWarning);
    setContent("");
    setIsWarning(false);
  };

  return (
    <div className="space-y-4">
      <form onSubmit={handleSubmit} className="bg-slate-50 border border-slate-200/60 p-3 rounded-xl space-y-3">
        <span className="text-[10px] font-bold text-slate-500 uppercase tracking-wider block">Gửi Đánh Giá Học Tập Mới</span>

        <div className="flex gap-2">
          <select value={type} onChange={(e) => setType(e.target.value as CommentEntry["type"])} className="bg-white border text-[10px] font-semibold text-slate-700 px-2 py-1 rounded">
            <option value="DAILY">Hàng ngày</option>
            <option value="MIDTERM">Giữa kỳ</option>
            <option value="FINAL">Cuối kỳ</option>
          </select>

          <label className="flex items-center gap-1.5 ml-auto text-[10px] font-bold text-rose-600 cursor-pointer">
            <input type="checkbox" checked={isWarning} onChange={(e) => setIsWarning(e.target.checked)} className="h-3.5 w-3.5 rounded border-slate-300 text-rose-600 focus:ring-rose-500" />
            <span>⚠️ Cảnh báo học lực</span>
          </label>
        </div>

        <textarea
          placeholder="Nhập nội dung nhận xét của giáo viên về tinh thần học, bài tập..."
          value={content}
          onChange={(e) => setContent(e.target.value)}
          rows={2}
          className="w-full bg-white border border-slate-200 rounded p-2 text-xs focus:outline-none"
          required
        />

        <div className="flex justify-end">
          <button type="submit" className="bg-brand-orange hover:bg-brand-orange/90 text-white text-[10px] font-bold px-3 py-1.5 rounded flex items-center gap-1 transition-all">
            <PlusCircle className="w-3.5 h-3.5" />
            <span>Gửi nhận xét</span>
          </button>
        </div>
      </form>

      <div className="space-y-2">
        <span className="text-[10px] font-bold text-slate-400 uppercase tracking-wider block">Lịch sử đánh giá của GV</span>
        {comments.length === 0 ? (
          <p className="text-[11px] text-slate-400 italic text-center py-4">Chưa có nhận xét nào được lưu cho học viên này.</p>
        ) : (
          comments.map((cmt) => (
            <div key={cmt.id} className={`p-3 rounded-lg border text-xs leading-relaxed ${cmt.isWarning ? "bg-rose-50/50 border-rose-100 text-rose-950" : "bg-white border-slate-100 text-slate-700"}`}>
              <div className="flex items-center justify-between gap-1 mb-1.5">
                <span className={`text-[9px] font-bold px-1.5 py-0.5 rounded-full ${cmt.type === "DAILY" ? "bg-slate-100 text-slate-600" : "bg-brand-orange text-white"}`}>
                  {cmt.type === "DAILY" ? "Hàng ngày" : cmt.type === "MIDTERM" ? "Giữa kỳ" : "Cuối kỳ"}
                </span>
                <span className="text-[10px] text-slate-400 font-medium">{cmt.createdAt}</span>
              </div>
              <p className="font-medium">"{cmt.content}"</p>
              {cmt.isWarning && (
                <div className="flex items-center gap-1.5 mt-1.5 text-[10px] font-bold text-rose-600">
                  <AlertTriangle className="w-3.5 h-3.5" />
                  <span>Học viên bị cảnh báo học vụ - Đã gửi SMS liên hệ phụ huynh.</span>
                </div>
              )}
            </div>
          ))
        )}
      </div>
    </div>
  );
}
