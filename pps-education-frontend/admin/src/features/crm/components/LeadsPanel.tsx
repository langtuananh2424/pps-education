import React, { useState } from "react";
import { AlertCircle, PhoneCall, UserPlus } from "lucide-react";
import { Lead } from "@/types";
import Badge, { BadgeVariant } from "@/components/ui/Badge";
import { cn } from "@/lib/cn";
import Select from "@/components/ui/Select";

const statusLabels: Record<Lead["status"], string> = {
  NEW: "Mới đổ về",
  CONTACTED: "Đang tư vấn",
  QUALIFIED: "Đủ điều kiện",
  WON: "Đã chốt học",
  LOST: "Thất bại"
};

const statusVariants: Record<Lead["status"], BadgeVariant> = {
  NEW: "info",
  CONTACTED: "neutral",
  QUALIFIED: "warning",
  WON: "success",
  LOST: "danger"
};

interface LeadsPanelProps {
  leads: Lead[];
  onAddCallLog: (leadId: string, text: string, status: Lead["status"]) => void;
  onConvertToStudent: (leadId: string) => void;
}

export default function LeadsPanel({ leads, onAddCallLog, onConvertToStudent }: LeadsPanelProps) {
  const [selectedLeadId, setSelectedLeadId] = useState<string | null>(null);
  const [callLogText, setCallLogText] = useState("");
  const [leadStatus, setLeadStatus] = useState<Lead["status"]>("NEW");

  const selectedLead = leads.find((l) => l.id === selectedLeadId) || null;

  const handleSelect = (lead: Lead) => {
    setSelectedLeadId(lead.id);
    setLeadStatus(lead.status);
  };

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!callLogText || !selectedLead) return;
    onAddCallLog(selectedLead.id, callLogText, leadStatus);
    setCallLogText("");
  };

  return (
    <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
      <div className="lg:col-span-2 bg-white rounded-xl border border-slate-200 shadow-soft overflow-hidden">
        <div className="px-5 py-4 border-b border-slate-100 flex items-center justify-between bg-slate-50">
          <span className="text-sm font-bold text-slate-700 font-display">Danh sách Khách hàng Tiềm năng</span>
          <span className="text-sm font-mono font-bold bg-white px-2 py-0.5 border rounded-full text-slate-500">Landing Page API Active</span>
        </div>
        <div className="divide-y divide-slate-100">
          {leads.map((lead) => (
            <div
              key={lead.id}
              onClick={() => handleSelect(lead)}
              className={cn(
                "p-4 flex flex-col sm:flex-row sm:items-center justify-between gap-4 hover:bg-slate-50/40 transition-colors cursor-pointer",
                selectedLead?.id === lead.id && "bg-slate-50"
              )}
            >
              <div>
                <div className="flex items-center gap-2">
                  <h4 className="text-sm font-bold text-slate-900">{lead.fullName}</h4>
                  <span className="bg-slate-100 text-slate-600 text-[9px] font-bold px-2 py-0.5 rounded">Kênh: {lead.source}</span>
                </div>
                <div className="flex items-center gap-2 mt-1.5 text-sm text-slate-400">
                  <span>SĐT: {lead.phone}</span>
                  <span>•</span>
                  <span>Ngày tạo: {lead.createdAt}</span>
                </div>
              </div>

              <div className="flex items-center gap-3">
                <Badge variant={statusVariants[lead.status]}>{statusLabels[lead.status]}</Badge>

                {lead.status === "QUALIFIED" && (
                  <button
                    onClick={(e) => {
                      e.stopPropagation();
                      onConvertToStudent(lead.id);
                    }}
                    className="bg-brand-gradient hover:opacity-95 text-white font-bold text-sm px-2.5 py-1 rounded flex items-center gap-1 shadow-soft"
                    title="Đăng ký học chính thức"
                  >
                    <UserPlus className="w-3.5 h-3.5" />
                    <span>Chuyển học viên</span>
                  </button>
                )}
              </div>
            </div>
          ))}
        </div>
      </div>

      <div className="bg-white p-5 rounded-xl border border-slate-200 shadow-soft">
        {selectedLead ? (
          <div className="space-y-4">
            <div className="border-b border-slate-100 pb-3 flex items-center justify-between">
              <div>
                <span className="text-sm font-mono font-bold text-slate-400">HỒ SƠ TƯ VẤN: {selectedLead.id}</span>
                <h3 className="text-sm font-bold text-slate-800">{selectedLead.fullName}</h3>
              </div>
              <button onClick={() => setSelectedLeadId(null)} className="text-slate-400 hover:text-slate-700 text-sm">
                Đóng
              </button>
            </div>

            <div className="space-y-2">
              <span className="text-sm uppercase font-bold tracking-wider text-slate-400 block font-display">Lịch sử cuộc gọi</span>
              <div className="space-y-2 max-h-48 overflow-y-auto bg-slate-50 p-2.5 rounded-lg border">
                {selectedLead.history.map((hist, idx) => (
                  <div key={idx} className="text-sm text-slate-600 border-b border-dashed pb-1.5 last:border-b-0 leading-normal">
                    {hist}
                  </div>
                ))}
              </div>
            </div>

            <form onSubmit={handleSubmit} className="space-y-3 pt-2">
              <div className="space-y-1">
                <label className="text-sm uppercase font-bold tracking-wider text-slate-500">Cập nhật trạng thái Leads</label>
                <Select
                  value={leadStatus}
                  onChange={(e) => setLeadStatus(e.target.value as Lead["status"])}
                  className="w-full bg-slate-50 border border-slate-200 text-sm px-2.5 py-1.5 rounded-lg focus:outline-none"
                >
                  <option value="NEW">Mới nhận (NEW)</option>
                  <option value="CONTACTED">Đã gọi điện tư vấn (CONTACTED)</option>
                  <option value="QUALIFIED">Đủ điều kiện & Muốn tham quan (QUALIFIED)</option>
                  <option value="LOST">Từ chối học / Thất bại (LOST)</option>
                </Select>
              </div>

              <div className="space-y-1">
                <label className="text-sm uppercase font-bold tracking-wider text-slate-500">Nội dung cuộc gọi</label>
                <textarea
                  required
                  placeholder="Nhập ghi chú cuộc trò chuyện với phụ huynh..."
                  value={callLogText}
                  onChange={(e) => setCallLogText(e.target.value)}
                  rows={2}
                  className="w-full bg-slate-50 border border-slate-200 text-sm px-3 py-2 rounded-lg focus:outline-none"
                />
              </div>

              <button type="submit" className="w-full bg-brand-gradient hover:opacity-95 text-white font-semibold text-sm py-2 rounded-lg flex items-center justify-center gap-1">
                <PhoneCall className="w-3.5 h-3.5 text-white" />
                Lưu lịch sử gọi
              </button>
            </form>

            {selectedLead.status === "QUALIFIED" && (
              <div className="border-t border-slate-100 pt-3 flex flex-col gap-2">
                <p className="text-sm text-slate-400 italic">Học sinh đã hoàn thành tư vấn, click nút dưới để tự động tạo hồ sơ học viên chính thức.</p>
                <button
                  onClick={() => onConvertToStudent(selectedLead.id)}
                  className="w-full bg-brand-gradient text-white font-bold text-sm py-2 rounded-lg shadow-glow flex items-center justify-center gap-1.5"
                >
                  <UserPlus className="w-4 h-4" />
                  Chuyển đổi hồ sơ chính thức
                </button>
              </div>
            )}
          </div>
        ) : (
          <div className="h-64 border border-dashed rounded-xl flex flex-col items-center justify-center text-slate-400 text-sm italic gap-1.5 text-center p-4">
            <AlertCircle className="w-6 h-6 text-slate-300" />
            <span>Nhấp chọn một Lead bên danh sách để ghi nhận cuộc gọi và cập nhật phễu tư vấn.</span>
          </div>
        )}
      </div>
    </div>
  );
}
