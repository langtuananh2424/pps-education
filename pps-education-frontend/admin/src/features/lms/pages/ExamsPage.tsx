import React, { useState } from "react";
import { StudentExamSubmission } from "@/types";
import { mockStudentExamSubmissions } from "@/data/mockData";
import Card from "@/components/ui/Card";
import Badge from "@/components/ui/Badge";
import { cn } from "@/lib/cn";
import GradingWorkshop from "../components/GradingWorkshop";

export default function ExamsPage() {
  const [submissions, setSubmissions] = useState<StudentExamSubmission[]>(mockStudentExamSubmissions);
  const [selectedSubId, setSelectedSubId] = useState<string | null>(null);
  const selectedSub = submissions.find((s) => s.id === selectedSubId) || null;

  const handleSaveGrade = (score: number, feedback: string) => {
    if (!selectedSub) return;
    setSubmissions((prev) =>
      prev.map((sub) =>
        sub.id === selectedSub.id
          ? { ...sub, score, teacherFeedback: feedback, status: "GRADED", skillsScores: { ...sub.skillsScores, speaking: score } }
          : sub
      )
    );
    setSelectedSubId(null);
    alert("Đã cập nhật điểm số và nhận xét kỹ năng Nói của học sinh thành công (UC-41)!");
  };

  return (
    <div className="space-y-6">
      <div className="border-b border-slate-200 pb-4">
        <h1 className="text-xl font-bold font-display tracking-tight text-slate-900">Tài Liệu & Khảo Thí LMS (E-Learning)</h1>
        <p className="text-xs text-slate-500 mt-1">Chấm bài thủ công kỹ năng Nghe-Nói (UC-40/41).</p>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        <Card padded={false} className="lg:col-span-2 overflow-hidden">
          <div className="px-5 py-4 border-b border-slate-100 bg-slate-50">
            <span className="text-xs font-bold text-slate-700 font-display">Hàng chờ chấm thi trắc nghiệm & Nghe-Nói (UC-41)</span>
          </div>

          <div className="divide-y divide-slate-100 max-h-[500px] overflow-y-auto">
            {submissions.map((sub) => (
              <div
                key={sub.id}
                onClick={() => setSelectedSubId(sub.id)}
                className={cn("p-4 flex flex-col sm:flex-row sm:items-center justify-between gap-4 hover:bg-slate-50/40 transition-colors cursor-pointer", selectedSub?.id === sub.id && "bg-slate-50")}
              >
                <div className="space-y-1">
                  <div className="flex items-center gap-2">
                    <h4 className="text-xs font-bold text-slate-900">{sub.studentName}</h4>
                    <span className="text-[10px] text-slate-400 font-mono">Lớp: {sub.className}</span>
                  </div>
                  <span className="text-[11px] text-slate-500 font-medium block">{sub.examTitle}</span>
                  <span className="text-[10px] text-slate-400 block">Thời gian nộp: {sub.submittedAt}</span>
                </div>

                <div>
                  {sub.status === "GRADED" ? (
                    <div className="text-xs font-bold text-slate-900">
                      Điểm số: <span className="text-emerald-600 font-mono font-extrabold text-sm">{sub.score}</span>
                    </div>
                  ) : (
                    <Badge variant="danger">Chờ chấm Speaking</Badge>
                  )}
                </div>
              </div>
            ))}
          </div>
        </Card>

        <Card>
          <GradingWorkshop submission={selectedSub} onClose={() => setSelectedSubId(null)} onSave={handleSaveGrade} />
        </Card>
      </div>
    </div>
  );
}
