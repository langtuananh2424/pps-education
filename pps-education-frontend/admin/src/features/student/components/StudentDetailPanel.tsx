import React, { useState } from "react";
import { BookOpen, Clock, TrendingUp, Users } from "lucide-react";
import { CommentEntry, GradeEntry, Student } from "@/types";
import StudentCommentsTab from "./StudentCommentsTab";
import StudentGradesTab from "./StudentGradesTab";

const DEFAULT_AVATAR = "https://images.unsplash.com/photo-1544717305-2782549b5136?auto=format&fit=crop&w=150&h=150&q=80";

interface StudentDetailPanelProps {
  student: Student | null;
  comments: CommentEntry[];
  grade: GradeEntry | null;
  onUpdateStatus: (studentId: string, status: Student["status"]) => void;
  onAddComment: (type: CommentEntry["type"], content: string, isWarning: boolean) => void;
  onSaveGrade: (midterm: number, final: number, comments: string) => void;
}

export default function StudentDetailPanel({ student, comments, grade, onUpdateStatus, onAddComment, onSaveGrade }: StudentDetailPanelProps) {
  const [tab, setTab] = useState<"comments" | "grades" | "history">("comments");

  if (!student) {
    return (
      <div className="bg-slate-50 rounded-xl border border-dashed border-slate-200 p-12 text-center h-full flex flex-col justify-center items-center space-y-2">
        <Users className="w-8 h-8 text-slate-300" />
        <h3 className="text-xs font-bold text-slate-700">Chưa chọn học viên</h3>
        <p className="text-[11px] text-slate-400 max-w-xs">
          Vui lòng chọn một học viên bên danh sách để bắt đầu theo dõi trạng thái, nhận xét hàng ngày, cập nhật điểm và quản lý học trình.
        </p>
      </div>
    );
  }

  return (
    <div className="bg-white rounded-xl border border-slate-200 shadow-soft overflow-hidden flex flex-col h-full">
      <div className="p-5 border-b border-slate-100 bg-slate-50/70">
        <div className="flex items-start gap-4">
          <img src={student.avatarUrl || DEFAULT_AVATAR} alt={student.fullName} className="w-14 h-14 rounded-xl object-cover border border-slate-200 shrink-0 bg-white shadow-sm" referrerPolicy="no-referrer" />
          <div className="space-y-1">
            <div className="flex items-center gap-2 flex-wrap">
              <h3 className="text-sm font-bold text-slate-900">{student.fullName}</h3>
              <span className="text-[10px] font-mono bg-slate-200 text-slate-700 px-1.5 py-0.5 rounded font-bold">{student.id}</span>
            </div>
            <span className="text-[11px] text-slate-500 block">Ngày sinh: {student.birthDate}</span>
            <div className="flex items-center gap-1.5 mt-1">
              <span className="text-[10px] font-bold text-slate-400">Trạng thái:</span>
              <select
                value={student.status}
                onChange={(e) => onUpdateStatus(student.id, e.target.value as Student["status"])}
                className="bg-white border border-slate-200 text-[10px] font-bold text-slate-700 px-1.5 py-0.5 rounded focus:outline-none"
              >
                <option value="STUDYING">Đang Học</option>
                <option value="RESERVED">Bảo Lưu</option>
                <option value="SUSPENDED">Đình Chỉ</option>
                <option value="GRADUATED">Tốt Nghiệp</option>
              </select>
            </div>
          </div>
        </div>
      </div>

      <div className="flex border-b border-slate-100 bg-white">
        {(
          [
            { id: "comments" as const, label: `Nhận xét (${comments.length})`, icon: BookOpen },
            { id: "grades" as const, label: "Học bạ điểm số", icon: TrendingUp },
            { id: "history" as const, label: `Nhật ký (${student.history.length})`, icon: Clock }
          ]
        ).map((item) => (
          <button
            key={item.id}
            onClick={() => setTab(item.id)}
            className={`flex-1 py-2.5 text-center text-xs font-bold border-b-2 transition-all flex items-center justify-center gap-1.5 ${
              tab === item.id ? "border-brand-orange text-brand-orange" : "border-transparent text-slate-400 hover:text-slate-600"
            }`}
          >
            <item.icon className="w-3.5 h-3.5" />
            {item.label}
          </button>
        ))}
      </div>

      <div className="p-4 flex-1 overflow-y-auto max-h-[460px]">
        {tab === "comments" && <StudentCommentsTab comments={comments} onAddComment={onAddComment} />}
        {tab === "grades" && <StudentGradesTab key={student.id} grade={grade} onSave={onSaveGrade} />}
        {tab === "history" && (
          <div className="space-y-4">
            <span className="text-[10px] font-bold text-slate-400 uppercase tracking-wider block">Nhật ký sự kiện học trình (Audit Log)</span>
            <div className="relative border-l border-slate-200 pl-4 ml-2 space-y-4">
              {student.history.map((hist, index) => (
                <div key={index} className="relative">
                  <span className="absolute -left-[21px] top-0.5 bg-slate-200 border-2 border-white w-2.5 h-2.5 rounded-full" />
                  <p className="text-xs text-slate-700 leading-relaxed font-medium">{hist}</p>
                </div>
              ))}
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
