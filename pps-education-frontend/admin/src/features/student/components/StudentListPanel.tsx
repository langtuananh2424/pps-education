import React from "react";
import { Edit, Plus, Search, Trash2 } from "lucide-react";
import { Classroom, Student } from "@/types";
import { getStatusLabel, getStatusVariant } from "../utils";
import { cn } from "@/lib/cn";
import Badge from "@/components/ui/Badge";

interface StudentListPanelProps {
  students: Student[];
  classrooms: Classroom[];
  selectedStudentId: string | null;
  searchTerm: string;
  onSearchChange: (value: string) => void;
  onSelect: (id: string) => void;
  onAdd: () => void;
  onEdit: (student: Student) => void;
  onDelete: (studentId: string) => void;
}

const DEFAULT_AVATAR = "https://images.unsplash.com/photo-1544717305-2782549b5136?auto=format&fit=crop&w=150&h=150&q=80";

export default function StudentListPanel({ students, classrooms, selectedStudentId, searchTerm, onSearchChange, onSelect, onAdd, onEdit, onDelete }: StudentListPanelProps) {
  return (
    <div className="bg-white rounded-xl border border-slate-200 shadow-soft p-4 space-y-4">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
        <div className="relative w-full max-w-sm">
          <Search className="absolute left-3 top-2.5 w-4 h-4 text-slate-400" />
          <input
            type="text"
            placeholder="Tìm theo tên học sinh, ID, phụ huynh..."
            value={searchTerm}
            onChange={(e) => onSearchChange(e.target.value)}
            className="w-full bg-slate-50 border border-slate-200 pl-9 pr-4 py-2 rounded-lg text-xs focus:outline-none focus:border-brand-orange focus:ring-1 focus:ring-brand-orange/30"
          />
        </div>

        <button onClick={onAdd} className="bg-brand-orange hover:bg-brand-orange/90 text-white text-xs font-semibold px-3 py-2 rounded-lg flex items-center gap-1.5 transition-all shadow-sm shrink-0">
          <Plus className="w-4 h-4 text-white" />
          <span>Thêm học viên mới</span>
        </button>
      </div>

      <div className="space-y-3 max-h-[560px] overflow-y-auto pr-1">
        {students.length === 0 ? (
          <div className="text-center py-12 bg-slate-50 rounded-xl border border-dashed border-slate-200">
            <p className="text-xs text-slate-500 font-medium">Không tìm thấy học viên nào phù hợp.</p>
          </div>
        ) : (
          students.map((student) => {
            const isSelected = selectedStudentId === student.id;
            return (
              <div
                key={student.id}
                onClick={() => onSelect(student.id)}
                className={cn(
                  "p-4 rounded-xl border transition-all cursor-pointer flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4",
                  isSelected ? "bg-brand-orange border-brand-orange text-white shadow-md ring-2 ring-brand-orange/20" : "bg-white hover:bg-slate-50 border-slate-200 text-slate-700 shadow-soft"
                )}
              >
                <div className="flex items-center gap-3">
                  <img
                    src={student.avatarUrl || DEFAULT_AVATAR}
                    alt={student.fullName}
                    className="w-11 h-11 rounded-lg object-cover border border-slate-200/50 shrink-0 bg-slate-100"
                    referrerPolicy="no-referrer"
                  />
                  <div className="space-y-1">
                    <div className="flex items-center gap-2">
                      <span className="text-xs font-bold">{student.fullName}</span>
                      <span className={cn("text-[9px] font-mono font-bold", isSelected ? "text-orange-100" : "text-slate-400")}>{student.id}</span>
                    </div>
                    <span className={cn("text-[10px] block", isSelected ? "text-orange-100" : "text-slate-500")}>
                      Lớp: {student.classIds.map((cId) => classrooms.find((c) => c.id === cId)?.name || cId).join(", ")}
                    </span>
                    <span className={cn("text-[10px] block font-medium", isSelected ? "text-orange-100" : "text-slate-400")}>
                      PH: {student.parentName} ({student.parentPhone})
                    </span>
                  </div>
                </div>

                <div className="flex items-center gap-2 self-end sm:self-center">
                  <Badge variant={getStatusVariant(student.status)}>{getStatusLabel(student.status)}</Badge>

                  <div className="flex gap-1">
                    <button
                      onClick={(e) => {
                        e.stopPropagation();
                        onEdit(student);
                      }}
                      className={cn("p-1.5 rounded hover:bg-slate-200/30 transition-all", isSelected ? "text-orange-100 hover:text-white" : "text-slate-500")}
                      title="Sửa thông tin"
                    >
                      <Edit className="w-3.5 h-3.5" />
                    </button>
                    <button
                      onClick={(e) => {
                        e.stopPropagation();
                        onDelete(student.id);
                      }}
                      className="p-1.5 rounded hover:bg-rose-100/30 text-rose-500 transition-all"
                      title="Xóa học viên"
                    >
                      <Trash2 className="w-3.5 h-3.5" />
                    </button>
                  </div>
                </div>
              </div>
            );
          })
        )}
      </div>
    </div>
  );
}
