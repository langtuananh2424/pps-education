import React, { useState } from "react";
import { GradeEntry } from "@/types";
import { mockGradeEntries } from "@/data/mockData";
import Card from "@/components/ui/Card";
import GradebookTable from "../components/GradebookTable";
import GradeApprovalQueue from "../components/GradeApprovalQueue";

export default function GradesPage() {
  const [gradeEntries, setGradeEntries] = useState<GradeEntry[]>(mockGradeEntries);
  const [selectedClassId] = useState("CLS-01");
  const [midtermInput, setMidtermInput] = useState<Record<string, string>>({});
  const [finalInput, setFinalInput] = useState<Record<string, string>>({});
  const [gradeErrors, setGradeErrors] = useState<Record<string, string>>({});

  const handleScoreChange = (studentId: string, type: "mid" | "final", value: string) => {
    if (type === "mid") setMidtermInput((prev) => ({ ...prev, [studentId]: value }));
    else setFinalInput((prev) => ({ ...prev, [studentId]: value }));

    const num = parseFloat(value);
    setGradeErrors((prev) => {
      const next = { ...prev };
      if (value !== "" && (isNaN(num) || num < 0 || num > 10)) {
        next[studentId] = "⚠️ Điểm số bắt buộc từ 0.0 đến 10.0";
      } else {
        delete next[studentId];
      }
      return next;
    });
  };

  const handleSubmitGrades = (studentId: string) => {
    if (gradeErrors[studentId]) return;
    const midVal = midtermInput[studentId] ? parseFloat(midtermInput[studentId]) : undefined;
    const finalVal = finalInput[studentId] ? parseFloat(finalInput[studentId]) : undefined;
    const avg = midVal !== undefined && finalVal !== undefined ? (midVal + finalVal) / 2 : undefined;

    setGradeEntries((prev) =>
      prev.map((gr) => (gr.studentId === studentId ? { ...gr, midtermScore: midVal, finalScore: finalVal, averageScore: avg, status: "PENDING", notes: "Nhập điểm và submit bởi Giáo viên Lê Thu Hà." } : gr))
    );
    alert("Đã gửi cột điểm của học sinh này lên hàng chờ phê duyệt của Quản lý điểm trường (UC-19)!");
  };

  const handleApproveGrade = (gradeId: string, status: "APPROVED" | "REJECTED") => {
    setGradeEntries((prev) =>
      prev.map((gr) =>
        gr.id === gradeId
          ? { ...gr, status, notes: status === "APPROVED" ? "Đã duyệt và công khai lên Portal Phụ huynh." : "Bị từ chối duyệt. Liên hệ giáo viên kiểm tra lại." }
          : gr
      )
    );
  };

  return (
    <div className="space-y-6">
      <div className="border-b border-slate-200 pb-4">
        <h1 className="text-xl font-bold font-display tracking-tight text-slate-900">Học Thuật & Quy Chuẩn Đào Tạo (Academic)</h1>
        <p className="text-xs text-slate-500 mt-1">Nhập điểm học phần và phê duyệt sổ điểm (UC-19/20).</p>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        <Card padded={false} className="lg:col-span-2 overflow-hidden">
          <div className="px-5 py-4 border-b border-slate-100 bg-slate-50 flex items-center justify-between">
            <div>
              <span className="text-xs font-bold text-slate-700 font-display">Bảng nhập điểm giáo viên (UC-19)</span>
              <p className="text-[10px] text-slate-400 mt-0.5">Giáo viên nhập điểm thành phần, hệ thống tự động tính trung bình.</p>
            </div>
            <span className="text-xs font-bold text-brand-orange">{selectedClassId}</span>
          </div>

          <GradebookTable
            entries={gradeEntries.filter((gr) => gr.classId === selectedClassId)}
            midtermInput={midtermInput}
            finalInput={finalInput}
            errors={gradeErrors}
            onScoreChange={handleScoreChange}
            onSubmit={handleSubmitGrades}
          />
        </Card>

        <Card className="space-y-4">
          <h3 className="text-xs font-bold text-slate-400 block uppercase tracking-wider font-display border-b border-slate-100 pb-2">Duyệt Điểm Học Phần (CAMPUS_MANAGER - UC-20)</h3>
          <p className="text-xs text-slate-500">Điểm sau khi được giáo viên nộp sẽ chuyển về đây, Quản lý điểm trường duyệt để công khai lên Portal phụ huynh.</p>
          <GradeApprovalQueue entries={gradeEntries.filter((gr) => gr.status === "PENDING")} onApprove={handleApproveGrade} />
        </Card>
      </div>
    </div>
  );
}
