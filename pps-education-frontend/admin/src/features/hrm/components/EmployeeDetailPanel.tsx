import React, { useState } from "react";
import { Award, Edit, GraduationCap, Power } from "lucide-react";
import { Employee } from "@/types";
import { roleLabels } from "@/constants/roles";

interface EmployeeDetailPanelProps {
  employee: Employee;
  onToggleStatus: (empId: string) => void;
  onEdit: (emp: Employee) => void;
  onAddReward: (empId: string, text: string) => void;
  onAddDiscipline: (empId: string, text: string) => void;
}

export default function EmployeeDetailPanel({ employee, onToggleStatus, onEdit, onAddReward, onAddDiscipline }: EmployeeDetailPanelProps) {
  const [rewardText, setRewardText] = useState("");
  const [disciplineText, setDisciplineText] = useState("");
  const primaryContract = employee.contracts[0] || null;

  return (
    <div className="bg-white p-5 rounded-xl border border-slate-200 shadow-soft flex flex-col justify-between space-y-5 h-full">
      <div className="space-y-4">
        <div className="flex items-center justify-between border-b border-slate-100 pb-3">
          <h3 className="text-xs font-bold text-slate-400 uppercase tracking-wider font-display">Hồ sơ lý lịch & Hợp đồng (UC-08)</h3>
          <span className="text-[10px] font-mono font-bold bg-slate-100 text-slate-500 px-2 py-0.5 rounded-md">{employee.id}</span>
        </div>

        <div className="flex items-center gap-3.5 p-3 bg-slate-50/50 rounded-xl border border-slate-100">
          <div className="w-12 h-12 rounded-full bg-brand-gradient text-white flex items-center justify-center font-display font-black text-lg shadow-soft shrink-0">
            {employee.fullName.charAt(0)}
          </div>
          <div className="space-y-0.5">
            <h4 className="text-sm font-bold text-slate-900 leading-snug">{employee.fullName}</h4>
            <p className="text-[10px] text-brand-orange font-bold font-mono">{roleLabels[employee.role]}</p>
            <div className="flex items-center gap-1.5 pt-0.5">
              <span className={`w-1.5 h-1.5 rounded-full ${employee.status === "ACTIVE" ? "bg-emerald-500 animate-pulse" : "bg-slate-400"}`} />
              <span className="text-[9px] text-slate-400 font-bold uppercase">{employee.status === "ACTIVE" ? "Đang hoạt động" : "Tắt hoạt động"}</span>
            </div>
          </div>
        </div>

        <div className="space-y-2 text-xs">
          <span className="text-[10px] font-bold text-slate-400 uppercase tracking-wider">Thông tin liên hệ</span>
          <div className="grid grid-cols-1 gap-2 p-3 bg-slate-50 rounded-lg border border-slate-100 font-medium text-slate-700">
            <div className="flex items-center gap-2">
              <span className="text-slate-400 w-4 font-bold">✉</span>
              <a href={`mailto:${employee.email}`} className="hover:underline text-slate-600 truncate">
                {employee.email}
              </a>
            </div>
            <div className="flex items-center gap-2">
              <span className="text-slate-400 w-4 font-bold">☎</span>
              <span className="text-slate-600">{employee.phone}</span>
            </div>
            <div className="flex items-center gap-2">
              <span className="text-slate-400 w-4 font-bold">📅</span>
              <span className="text-slate-500">Ngày gia nhập: {employee.joinedDate}</span>
            </div>
          </div>
        </div>

        <div className="space-y-2 text-xs">
          <span className="text-[10px] font-bold text-slate-400 uppercase tracking-wider block">Bằng cấp & Chứng chỉ chuyên môn</span>
          <div className="space-y-1.5">
            {employee.degrees.length === 0 && employee.certificates.length === 0 ? (
              <p className="text-[10px] text-slate-400 italic">Chưa khai báo bằng cấp chứng chỉ.</p>
            ) : (
              <>
                {employee.degrees.map((deg, idx) => (
                  <div key={idx} className="flex items-start gap-1.5 text-[11px] text-slate-600 leading-normal">
                    <GraduationCap className="w-3.5 h-3.5 text-slate-400 shrink-0 mt-0.5" />
                    <span>{deg}</span>
                  </div>
                ))}
                {employee.certificates.map((cert, idx) => (
                  <div key={idx} className="flex items-start gap-1.5 text-[11px] text-slate-600 leading-normal">
                    <Award className="w-3.5 h-3.5 text-brand-orange shrink-0 mt-0.5" />
                    <span className="font-semibold text-slate-700">{cert}</span>
                  </div>
                ))}
              </>
            )}
          </div>
        </div>

        <div className="p-3 bg-slate-50 rounded-xl space-y-2 border border-slate-100 shadow-inner">
          <div className="flex items-center justify-between">
            <span className="text-[9px] font-extrabold text-orange-400 uppercase tracking-widest font-mono">Thông tin hợp đồng lao động</span>
            <span className="text-[8px] bg-white/10 text-white px-2 py-0.5 rounded font-bold font-mono">{primaryContract ? primaryContract.type : "N/A"}</span>
          </div>
          <div className="space-y-1 text-[11px]">
            <div className="flex justify-between">
              <span className="text-slate-400">Định mức lương cơ bản:</span>
              <span className="font-mono font-bold text-white text-xs">{primaryContract ? primaryContract.baseSalary.toLocaleString("vi-VN") : "0"} đ</span>
            </div>
            <div className="flex justify-between">
              <span className="text-slate-400">Hình thức ký hợp đồng:</span>
              <span className="text-slate-200">
                {primaryContract?.type === "LABOR" ? "Hợp đồng lao động dài hạn" : primaryContract?.type === "COLLABORATOR" ? "Cộng tác viên / Khoán" : "Thử việc"}
              </span>
            </div>
            <div className="flex justify-between">
              <span className="text-slate-400">Ngày bắt đầu hiệu lực:</span>
              <span className="font-mono text-slate-200">{primaryContract ? primaryContract.startDate : employee.joinedDate}</span>
            </div>
          </div>
        </div>

        <div className="space-y-2 border-t border-slate-100 pt-3">
          <span className="text-[10px] font-bold text-slate-400 uppercase tracking-wider block">Khen thưởng & Kỷ luật</span>
          <div className="space-y-1.5 max-h-[120px] overflow-y-auto">
            {employee.rewards.length === 0 && employee.disciplines.length === 0 ? (
              <p className="text-[10px] text-slate-400 italic">Chưa ghi nhận quyết định khen thưởng / kỷ luật nào.</p>
            ) : (
              <>
                {employee.rewards.map((rew, idx) => (
                  <div key={idx} className="p-1.5 bg-emerald-50 border border-emerald-100 rounded text-[10px] text-emerald-800 font-medium flex items-center gap-1.5">
                    <span className="text-emerald-500 font-bold shrink-0">✔</span>
                    <span>{rew}</span>
                  </div>
                ))}
                {employee.disciplines.map((dis, idx) => (
                  <div key={idx} className="p-1.5 bg-rose-50 border border-rose-100 rounded text-[10px] text-rose-800 font-medium flex items-center gap-1.5">
                    <span className="text-rose-500 font-bold shrink-0">✕</span>
                    <span>{dis}</span>
                  </div>
                ))}
              </>
            )}
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-2 pt-2">
            <div className="space-y-1">
              <input
                type="text"
                placeholder="Nhập khen thưởng..."
                value={rewardText}
                onChange={(e) => setRewardText(e.target.value)}
                className="w-full bg-slate-50 border border-slate-200 text-[10px] px-2 py-1 rounded focus:outline-none focus:border-emerald-300"
              />
              <button
                type="button"
                onClick={() => {
                  if (!rewardText.trim()) return;
                  onAddReward(employee.id, rewardText.trim());
                  setRewardText("");
                }}
                className="w-full bg-emerald-50 hover:bg-emerald-100 border border-emerald-200 text-emerald-700 text-[9px] font-bold py-0.5 rounded transition-colors"
              >
                + Ghi nhận KT
              </button>
            </div>

            <div className="space-y-1">
              <input
                type="text"
                placeholder="Nhập kỷ luật..."
                value={disciplineText}
                onChange={(e) => setDisciplineText(e.target.value)}
                className="w-full bg-slate-50 border border-slate-200 text-[10px] px-2 py-1 rounded focus:outline-none focus:border-rose-300"
              />
              <button
                type="button"
                onClick={() => {
                  if (!disciplineText.trim()) return;
                  onAddDiscipline(employee.id, disciplineText.trim());
                  setDisciplineText("");
                }}
                className="w-full bg-rose-50 hover:bg-rose-100 border border-rose-200 text-rose-700 text-[9px] font-bold py-0.5 rounded transition-colors"
              >
                + Ghi nhận KL
              </button>
            </div>
          </div>
        </div>
      </div>

      <div className="grid grid-cols-2 gap-2 border-t border-slate-100 pt-3.5 mt-2">
        <button
          onClick={() => onToggleStatus(employee.id)}
          className={`text-xs font-bold py-2 px-3 rounded-lg flex items-center justify-center gap-1.5 transition-all active:scale-95 border ${employee.status === "ACTIVE"
              ? "bg-slate-100 border-slate-200 text-slate-600 hover:bg-rose-50 hover:text-rose-600 hover:border-rose-200"
              : "bg-emerald-50 border-emerald-100 text-emerald-700 hover:bg-emerald-100"
            }`}
        >
          <Power className="w-3.5 h-3.5" />
          <span>{employee.status === "ACTIVE" ? "Tắt hoạt động" : "Kích hoạt lại"}</span>
        </button>

        <button
          onClick={() => onEdit(employee)}
          className="bg-slate-900 hover:bg-slate-800 text-white text-xs font-bold py-2 px-3 rounded-lg flex items-center justify-center gap-1.5 transition-all active:scale-95 shadow-sm"
        >
          <Edit className="w-3.5 h-3.5 text-brand-yellow" />
          <span>Sửa thông tin</span>
        </button>
      </div>
    </div>
  );
}
