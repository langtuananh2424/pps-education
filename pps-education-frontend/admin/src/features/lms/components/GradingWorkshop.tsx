import React, { useState } from "react";
import { Award, Mic, Play, Save } from "lucide-react";
import { StudentExamSubmission } from "@/types";
import { useDialog } from "@/components/ui/DialogProvider";

interface GradingWorkshopProps {
  submission: StudentExamSubmission | null;
  onClose: () => void;
  onSave: (score: number, feedback: string) => void;
}

export default function GradingWorkshop({ submission, onClose, onSave }: GradingWorkshopProps) {
  const [score, setScore] = useState("8.5");
  const [feedback, setFeedback] = useState("");
  const { alertDialog } = useDialog();

  if (!submission) {
    return (
      <div className="h-64 border border-dashed rounded-xl flex flex-col items-center justify-center text-slate-400 text-sm italic gap-1.5 text-center p-4">
        <Award className="w-6 h-6 text-slate-300 animate-bounce" />
        <span>Nhấp chọn một học sinh trong hàng chờ bên cạnh để kiểm tra bài ghi âm kỹ năng Nói và chấm điểm thủ công.</span>
      </div>
    );
  }

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    onSave(parseFloat(score), feedback);
    setFeedback("");
  };

  return (
    <div className="space-y-4">
      <div className="border-b border-slate-100 pb-2.5 flex items-center justify-between">
        <div>
          <span className="text-sm font-mono font-bold text-slate-400">CHẤM BÀI PHÁT ÂM: {submission.id}</span>
          <h3 className="text-sm font-bold text-slate-800">{submission.studentName}</h3>
        </div>
        <button onClick={onClose} className="text-sm text-slate-400 hover:text-slate-800">
          Đóng
        </button>
      </div>

      <div className="p-3.5 bg-slate-50 rounded-lg border border-slate-100 space-y-3">
        <span className="text-sm uppercase font-bold tracking-wider text-rose-600 font-display flex items-center gap-1">
          <Mic className="w-3.5 h-3.5 animate-pulse" />
          Bản ghi âm phản xạ phát âm học viên
        </span>

        <div className="p-2 bg-slate-900 text-white rounded flex items-center justify-between text-sm">
          <span className="font-mono text-sm">audiotrack_speech_sample.mp3</span>
          <button onClick={() => alertDialog("🔈 Đang phát audio ghi âm phản xạ của học sinh...")} className="p-1 rounded bg-brand-gradient hover:opacity-90 font-bold">
            <Play className="w-3 h-3 text-white fill-white" />
          </button>
        </div>
      </div>

      <form onSubmit={handleSubmit} className="space-y-4 pt-1">
        <div className="space-y-1">
          <label className="text-sm uppercase font-bold tracking-wider text-slate-500">Cho điểm kỹ năng nói (0 - 10)</label>
          <input
            type="number"
            required
            min={0}
            max={10}
            step={0.1}
            value={score}
            onChange={(e) => setScore(e.target.value)}
            className="w-full bg-slate-50 border border-slate-200 text-sm px-3 py-2 rounded-lg focus:outline-none"
          />
        </div>

        <div className="space-y-1">
          <label className="text-sm uppercase font-bold tracking-wider text-slate-500">Nhận xét chi tiết phát âm (Teacher Feedback)</label>
          <textarea
            required
            placeholder="Ghi nhận từ vựng phát âm chuẩn, sửa ngữ điệu luyến âm..."
            value={feedback}
            onChange={(e) => setFeedback(e.target.value)}
            rows={3}
            className="w-full bg-slate-50 border border-slate-200 text-sm px-3 py-2 rounded-lg focus:outline-none"
          />
        </div>

        <button type="submit" className="w-full bg-brand-gradient hover:opacity-95 text-white font-semibold text-sm py-2 rounded-lg flex items-center justify-center gap-1.5">
          <Save className="w-4 h-4 text-white" />
          Lưu kết quả chấm thi
        </button>
      </form>
    </div>
  );
}
