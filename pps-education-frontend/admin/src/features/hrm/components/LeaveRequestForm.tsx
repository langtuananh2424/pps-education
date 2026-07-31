import React, { useState } from "react";
import { PlusCircle } from "lucide-react";
import DatePicker from "@/components/ui/DatePicker";
import Select from "@/components/ui/Select";

interface LeaveRequestFormProps {
  onSubmit: (input: { type: "LEAVE" | "LATE" | "EARLY"; startDate: string; endDate: string; reason: string }) => void;
}

export default function LeaveRequestForm({ onSubmit }: LeaveRequestFormProps) {
  const [type, setType] = useState<"LEAVE" | "LATE" | "EARLY">("LEAVE");
  const [startDate, setStartDate] = useState("2026-07-15");
  const [endDate, setEndDate] = useState("2026-07-16");
  const [reason, setReason] = useState("");

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!reason.trim()) return;
    onSubmit({ type, startDate, endDate, reason });
    setReason("");
  };

  return (
    <div className="bg-white p-5 rounded-xl border border-slate-200 shadow-soft space-y-4">
      <h3 className="text-xs font-bold text-slate-400 block uppercase tracking-wider font-display border-b border-slate-100 pb-2">
        Nộp Đơn Nghỉ Phép / Đi Muộn
      </h3>
      <p className="text-xs text-slate-500">Nhân viên gõ lý do xin nghỉ để tạo quy trình xét duyệt tự động 1-2 bước tương ứng.</p>

      <form onSubmit={handleSubmit} className="space-y-3.5">
        <div className="space-y-1">
          <label className="text-[10px] uppercase font-bold tracking-wider text-slate-500">Hình thức nộp</label>
          <Select value={type} onChange={(e) => setType(e.target.value as "LEAVE" | "LATE" | "EARLY")} className="w-full bg-slate-50 border border-slate-200 text-xs px-3 py-2 rounded-lg focus:outline-none">
            <option value="LEAVE">Nghỉ phép thường niên (Full-day)</option>
            <option value="LATE">Đi trễ có lý do (Late morning)</option>
            <option value="EARLY">Về sớm cá nhân (Early leave)</option>
          </Select>
        </div>

        <div className="grid grid-cols-2 gap-3">
          <div className="space-y-1">
            <label className="text-[10px] uppercase font-bold tracking-wider text-slate-500">Từ ngày/giờ</label>
            <DatePicker value={startDate} onChange={setStartDate} max={endDate || undefined} />
          </div>
          <div className="space-y-1">
            <label className="text-[10px] uppercase font-bold tracking-wider text-slate-500">Đến ngày/giờ</label>
            <DatePicker value={endDate} onChange={setEndDate} min={startDate || undefined} />
          </div>
        </div>

        <div className="space-y-1">
          <label className="text-[10px] uppercase font-bold tracking-wider text-slate-500">Lý do nghỉ</label>
          <textarea
            required
            rows={2}
            placeholder="Ví dụ: Đi tái khám răng, bị ốm sốt..."
            value={reason}
            onChange={(e) => setReason(e.target.value)}
            className="w-full bg-slate-50 border border-slate-200 text-xs px-3 py-2 rounded-lg focus:outline-none"
          />
        </div>

        <button type="submit" className="w-full bg-slate-900 hover:bg-slate-800 text-white font-semibold text-xs py-2 rounded-lg flex items-center justify-center gap-1.5">
          <PlusCircle className="w-4 h-4 text-brand-yellow" />
          Gửi đơn chờ duyệt
        </button>
      </form>
    </div>
  );
}
