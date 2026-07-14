import React from "react";
import { Edit, Eye, Plus, Power } from "lucide-react";
import { Employee } from "@/types";
import { roleLabels } from "@/constants/roles";
import { cn } from "@/lib/cn";

const campusShortNames: Record<string, string> = {
  "CAMP-01": "Hai Bà Trưng",
  "CAMP-02": "Cầu Giấy",
  "CAMP-03": "Nghĩa Tân",
  "CAMP-04": "Lê Quý Đôn"
};

interface EmployeeListPanelProps {
  employees: Employee[];
  selectedEmpId: string;
  onSelect: (id: string) => void;
  onCreate: () => void;
  onEdit: (emp: Employee) => void;
  onToggleStatus: (empId: string) => void;
}

export default function EmployeeListPanel({ employees, selectedEmpId, onSelect, onCreate, onEdit, onToggleStatus }: EmployeeListPanelProps) {
  return (
    <div className="lg:col-span-2 bg-white rounded-xl border border-slate-200 shadow-soft overflow-hidden flex flex-col h-full">
      <div className="px-5 py-4 border-b border-slate-100 flex items-center justify-between bg-slate-50 shrink-0">
        <div className="space-y-0.5">
          <span className="text-xs font-bold text-slate-700 font-display block">Danh sách Cán bộ - Giảng viên</span>
          <p className="text-[10px] text-slate-400">Nhấp chọn nhân sự để xem chi tiết & điều phối hợp đồng</p>
        </div>
        <button
          onClick={onCreate}
          className="bg-brand-gradient text-white text-[11px] font-bold px-3 py-1.5 rounded-lg shadow-soft hover:opacity-90 active:scale-95 transition-all flex items-center gap-1.5 shrink-0"
        >
          <Plus className="w-3.5 h-3.5 text-white" />
          <span className="hidden sm:inline">Thêm nhân sự mới</span>
        </button>
      </div>

      <div className="divide-y divide-slate-100 overflow-y-auto max-h-[620px] lg:max-h-[680px]">
        {employees.length === 0 ? (
          <div className="p-8 text-center text-slate-400 text-xs">Không tìm thấy cán bộ nào trong danh sách.</div>
        ) : (
          employees.map((emp) => {
            const isSelected = emp.id === selectedEmpId;
            return (
              <div
                key={emp.id}
                onClick={() => onSelect(emp.id)}
                className={cn(
                  "p-4 flex flex-col sm:flex-row sm:items-center justify-between gap-4 transition-all cursor-pointer relative",
                  isSelected ? "bg-slate-50/90 border-l-4 border-brand-orange pl-3.5" : "hover:bg-slate-50/40 border-l-4 border-transparent"
                )}
              >
                <div className="flex items-start gap-3">
                  <div
                    className={cn(
                      "w-9 h-9 rounded-full flex items-center justify-center font-bold text-sm shrink-0 shadow-sm",
                      isSelected ? "bg-brand-gradient text-white" : "bg-slate-100 text-slate-700"
                    )}
                  >
                    {emp.fullName.charAt(0)}
                  </div>
                  <div>
                    <div className="flex items-center gap-1.5">
                      <h4 className="text-xs font-bold text-slate-900">{emp.fullName}</h4>
                      <span className={cn("text-[9px] px-1.5 py-0.5 rounded-full font-bold uppercase", emp.status === "ACTIVE" ? "bg-emerald-50 text-emerald-600" : "bg-slate-100 text-slate-400")}>
                        {emp.status === "ACTIVE" ? "Đang làm việc" : "Nghỉ việc"}
                      </span>
                    </div>

                    <div className="flex flex-col gap-1 mt-1">
                      <div className="flex items-center gap-1.5 text-[10px] text-slate-400">
                        <span>ID: {emp.id}</span>
                        <span>•</span>
                        <span className="text-brand-orange font-bold font-mono">{roleLabels[emp.role]}</span>
                      </div>

                      <div className="flex flex-wrap gap-1 mt-0.5">
                        {emp.campusIds.map((cId) => (
                          <span key={cId} className="bg-slate-100 text-slate-500 text-[8px] font-semibold px-1.5 py-0.2 rounded font-sans">
                            {campusShortNames[cId] || cId}
                          </span>
                        ))}
                      </div>
                    </div>
                  </div>
                </div>

                <div className="flex flex-wrap gap-1 max-w-[200px] sm:justify-end">
                  {emp.degrees.slice(0, 1).map((deg, dIdx) => (
                    <span key={dIdx} className="bg-slate-50 text-slate-600 text-[9px] font-medium px-2 py-0.5 rounded border border-slate-100 truncate max-w-[140px]" title={deg}>
                      {deg}
                    </span>
                  ))}
                  {emp.certificates.slice(0, 1).map((cert, cIdx) => (
                    <span key={cIdx} className="bg-brand-yellow/10 text-brand-orange text-[9px] font-bold px-2 py-0.5 rounded border border-brand-yellow/20 truncate max-w-[140px]" title={cert}>
                      {cert}
                    </span>
                  ))}
                  {(emp.degrees.length > 1 || emp.certificates.length > 1) && (
                    <span className="text-[8px] text-slate-400 font-bold px-1.5 py-0.5 rounded bg-slate-50 border self-center">
                      +{emp.degrees.length + emp.certificates.length - 2}
                    </span>
                  )}
                </div>

                <div className="flex items-center gap-1.5 self-end sm:self-center shrink-0">
                  <button
                    onClick={(e) => {
                      e.stopPropagation();
                      onSelect(emp.id);
                    }}
                    className="p-1.5 rounded-lg text-slate-400 hover:text-brand-orange hover:bg-slate-100 transition-all"
                    title="Xem thông tin chi tiết"
                  >
                    <Eye className="w-3.5 h-3.5" />
                  </button>
                  <button
                    onClick={(e) => {
                      e.stopPropagation();
                      onEdit(emp);
                    }}
                    className="p-1.5 rounded-lg text-slate-400 hover:text-slate-700 hover:bg-slate-100 transition-all"
                    title="Sửa thông tin"
                  >
                    <Edit className="w-3.5 h-3.5" />
                  </button>
                  <button
                    onClick={(e) => {
                      e.stopPropagation();
                      onToggleStatus(emp.id);
                    }}
                    className={cn(
                      "p-1.5 rounded-lg transition-all",
                      emp.status === "ACTIVE" ? "text-rose-400 hover:text-rose-600 hover:bg-rose-50" : "text-emerald-400 hover:text-emerald-600 hover:bg-emerald-50"
                    )}
                    title={emp.status === "ACTIVE" ? "Tắt trạng thái hoạt động" : "Bật trạng thái hoạt động"}
                  >
                    <Power className="w-3.5 h-3.5" />
                  </button>
                </div>
              </div>
            );
          })
        )}
      </div>
    </div>
  );
}
