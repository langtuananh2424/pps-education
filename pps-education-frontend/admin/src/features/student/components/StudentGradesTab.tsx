import React, { useState } from "react";
import { Save } from "lucide-react";
import { GradeEntry } from "@/types";

interface StudentGradesTabProps {
  grade: GradeEntry | null;
  onSave: (midterm: number, final: number, comments: string) => void;
}

export default function StudentGradesTab({ grade, onSave }: StudentGradesTabProps) {
  const [midterm, setMidterm] = useState(grade?.midtermScore?.toString() || "");
  const [final, setFinal] = useState(grade?.finalScore?.toString() || "");
  const [comments, setComments] = useState(grade?.teacherComments || "");

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    const midtermNum = parseFloat(midterm);
    const finalNum = parseFloat(final);
    if (isNaN(midtermNum) || midtermNum < 0 || midtermNum > 10 || isNaN(finalNum) || finalNum < 0 || finalNum > 10) {
      alert("Lỗi: Điểm số giữa kỳ và cuối kỳ phải là số hợp lệ từ 0 đến 10!");
      return;
    }
    onSave(midtermNum, finalNum, comments);
  };

  return (
    <form onSubmit={handleSubmit} className="space-y-4">
      <div className="bg-slate-50 p-4 rounded-xl border border-slate-100 grid grid-cols-2 gap-4">
        <div className="space-y-1">
          <label className="text-[10px] font-bold uppercase text-slate-400 tracking-wider">Điểm Giữa Kỳ</label>
          <input
            type="number"
            step="0.1"
            min="0"
            max="10"
            placeholder="Ví dụ: 8.5"
            value={midterm}
            onChange={(e) => setMidterm(e.target.value)}
            className="w-full bg-white border border-slate-200 rounded px-2.5 py-1.5 text-xs focus:outline-none font-bold"
          />
        </div>
        <div className="space-y-1">
          <label className="text-[10px] font-bold uppercase text-slate-400 tracking-wider">Điểm Cuối Kỳ</label>
          <input
            type="number"
            step="0.1"
            min="0"
            max="10"
            placeholder="Ví dụ: 9.0"
            value={final}
            onChange={(e) => setFinal(e.target.value)}
            className="w-full bg-white border border-slate-200 rounded px-2.5 py-1.5 text-xs focus:outline-none font-bold"
          />
        </div>
      </div>

      {midterm && final && (
        <div className="bg-emerald-50 border border-emerald-100 rounded-xl p-3 flex items-center justify-between text-xs">
          <span className="font-bold text-emerald-800">Điểm Trung Bình Dự Kiến:</span>
          <span className="text-sm font-black text-emerald-600 font-mono">{Math.round(((parseFloat(midterm) + parseFloat(final)) / 2) * 10) / 10} / 10</span>
        </div>
      )}

      <div className="space-y-1">
        <label className="text-[10px] font-bold uppercase text-slate-400 tracking-wider block">Lời khuyên của giáo vụ / Ghi chú</label>
        <textarea
          placeholder="Nhập ghi chú học bạ, nhận xét tổng kết..."
          value={comments}
          onChange={(e) => setComments(e.target.value)}
          rows={3}
          className="w-full bg-white border border-slate-200 rounded p-2 text-xs focus:outline-none"
        />
      </div>

      <button type="submit" className="w-full bg-brand-orange hover:bg-brand-orange/90 text-white text-xs font-bold py-2 rounded-lg flex items-center justify-center gap-1.5 transition-all">
        <Save className="w-4 h-4 text-white" />
        <span>Cập nhật Học bạ & Điểm số</span>
      </button>

      {grade ? (
        <div className="bg-slate-50 border border-slate-200/60 p-3.5 rounded-xl space-y-2 mt-4 text-xs">
          <span className="text-[10px] font-bold text-slate-400 uppercase tracking-wider block">Báo cáo học tập chính thức hiện tại</span>
          <div className="grid grid-cols-3 gap-2 text-center text-xs">
            <div className="bg-white p-1.5 rounded border border-slate-100">
              <span className="text-[10px] text-slate-400 block">Giữa kỳ</span>
              <span className="font-bold text-slate-800 font-mono">{grade.midtermScore ?? "-"}</span>
            </div>
            <div className="bg-white p-1.5 rounded border border-slate-100">
              <span className="text-[10px] text-slate-400 block">Cuối kỳ</span>
              <span className="font-bold text-slate-800 font-mono">{grade.finalScore ?? "-"}</span>
            </div>
            <div className="bg-white p-1.5 rounded border border-emerald-100 bg-emerald-50/20">
              <span className="text-[10px] text-emerald-800 block">Trung bình</span>
              <span className="font-bold text-emerald-600 font-mono">{grade.averageScore ?? "-"}</span>
            </div>
          </div>
          {grade.teacherComments && <p className="text-[10px] text-slate-500 italic mt-2 border-t pt-2">" {grade.teacherComments} "</p>}
          <p className="text-[9px] text-slate-400 text-right mt-1">Lần cuối: {grade.updatedAt}</p>
        </div>
      ) : (
        <div className="p-4 border border-dashed rounded-xl text-center text-xs text-slate-400 italic">
          Học viên chưa có điểm số chính thức được cập nhật trên học bạ lớp hiện tại.
        </div>
      )}
    </form>
  );
}
