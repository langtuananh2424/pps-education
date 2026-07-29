import React, { useState } from "react";
import { AlertCircle } from "lucide-react";
import { Employee } from "@/types";
import DatePicker from "@/components/ui/DatePicker";
import Select from "@/components/ui/Select";

interface TaskFormPanelProps {
  employees: Employee[];
  onSubmit: (input: { title: string; description: string; assignedToId: string; deadline: string }) => void;
  onCancel: () => void;
}

export default function TaskFormPanel({ employees, onSubmit, onCancel }: TaskFormPanelProps) {
  const [title, setTitle] = useState("");
  const [description, setDescription] = useState("");
  const [assignedToId, setAssignedToId] = useState(employees[2]?.id || employees[0]?.id || "");
  const [deadline, setDeadline] = useState("2026-07-15");
  const [error, setError] = useState("");

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!title || !description) {
      setError("Vui lòng điền tiêu đề và mô tả công việc.");
      return;
    }
    onSubmit({ title, description, assignedToId, deadline });
  };

  return (
    <form onSubmit={handleSubmit} className="bg-white p-5 rounded-xl border border-slate-200 shadow-soft space-y-4 animate-in fade-in duration-150">
      <h3 className="text-xs font-bold text-slate-400 uppercase tracking-wider font-display">Tạo hồ sơ phân công công việc</h3>
      <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
        <div className="md:col-span-2 space-y-1">
          <label className="text-[10px] uppercase font-bold tracking-wider text-slate-500">Tiêu đề nhiệm vụ</label>
          <input
            type="text"
            placeholder="Ví dụ: Rà soát trang thiết bị loa Cầu Giấy..."
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            className="w-full bg-slate-50 border border-slate-200 text-xs px-3 py-2 rounded-lg focus:outline-none"
          />
        </div>

        <div className="space-y-1">
          <label className="text-[10px] uppercase font-bold tracking-wider text-slate-500">Hạn hoàn thành (Deadline)</label>
          <DatePicker value={deadline} onChange={setDeadline} />
        </div>

        <div className="md:col-span-2 space-y-1">
          <label className="text-[10px] uppercase font-bold tracking-wider text-slate-500">Mô tả tác vụ cụ thể</label>
          <textarea
            placeholder="Nội dung giao việc, các tài liệu cần đính kèm hoặc bàn giao..."
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            rows={3}
            className="w-full bg-slate-50 border border-slate-200 text-xs px-3 py-2 rounded-lg focus:outline-none"
          />
        </div>

        <div className="space-y-1">
          <label className="text-[10px] uppercase font-bold tracking-wider text-slate-500">Nhân sự nhận việc</label>
          <Select
            value={assignedToId}
            onChange={(e) => setAssignedToId(e.target.value)}
            className="w-full bg-slate-50 border border-slate-200 text-xs px-3 py-2 rounded-lg focus:outline-none"
          >
            {employees.map((emp) => (
              <option key={emp.id} value={emp.id}>
                {emp.fullName} ({emp.role})
              </option>
            ))}
          </Select>
        </div>
      </div>

      <div className="flex items-center justify-between border-t border-slate-100 pt-3">
        <span className="text-[11px] text-slate-400 italic">Người giao: Nguyễn Văn Hùng (Phòng Vận hành)</span>
        <div className="flex items-center gap-2">
          <button type="button" onClick={onCancel} className="px-3.5 py-1.5 text-slate-500 hover:bg-slate-100 text-xs font-semibold rounded-lg">
            Hủy bỏ
          </button>
          <button type="submit" className="bg-slate-900 hover:bg-slate-800 text-white font-semibold text-xs px-4 py-1.5 rounded-lg">
            Xác nhận Giao việc
          </button>
        </div>
      </div>

      {error && (
        <div className="text-xs font-semibold text-brand-red flex items-center gap-1 mt-2">
          <AlertCircle className="w-3.5 h-3.5" />
          <span>{error}</span>
        </div>
      )}
    </form>
  );
}
