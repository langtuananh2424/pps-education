import React, { useState } from "react";
import { Plus } from "lucide-react";
import { Employee, Permission } from "@/types";

interface CreateOverrideFormProps {
  employees: Employee[];
  permissions: Permission[];
  onSubmit: (input: { userId: string; permissionId: string; type: "GRANT" | "REVOKE"; reason: string; expiresAt: string }) => void;
}

export default function CreateOverrideForm({ employees, permissions, onSubmit }: CreateOverrideFormProps) {
  const [userId, setUserId] = useState("");
  const [permissionId, setPermissionId] = useState("");
  const [type, setType] = useState<"GRANT" | "REVOKE">("GRANT");
  const [expiresAt, setExpiresAt] = useState("2026-08-31");
  const [reason, setReason] = useState("");

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!userId || !permissionId || !reason.trim()) {
      alert("Vui lòng điền đầy đủ thông tin.");
      return;
    }
    onSubmit({ userId, permissionId, type, reason: reason.trim(), expiresAt });
    setReason("");
  };

  return (
    <div className="lg:col-span-1 bg-white p-5 rounded-xl border border-slate-200 shadow-sm space-y-4 h-fit">
      <span className="text-xs font-bold text-slate-800 uppercase tracking-wider block border-b pb-2">Thiết lập ngoại lệ mới</span>

      <form onSubmit={handleSubmit} className="space-y-4">
        <div className="space-y-1">
          <label className="text-[10px] uppercase font-bold text-slate-500 block">Chọn Nhân sự</label>
          <select value={userId} onChange={(e) => setUserId(e.target.value)} required className="w-full bg-slate-50 border border-slate-200 text-xs p-2.5 rounded-lg focus:outline-none">
            <option value="">-- Chọn nhân sự --</option>
            {employees.map((e) => (
              <option key={e.id} value={e.id}>
                {e.fullName} ({e.id})
              </option>
            ))}
          </select>
        </div>

        <div className="space-y-1">
          <label className="text-[10px] uppercase font-bold text-slate-500 block">Chọn Mã Quyền hạt nhân</label>
          <select value={permissionId} onChange={(e) => setPermissionId(e.target.value)} required className="w-full bg-slate-50 border border-slate-200 text-xs p-2.5 rounded-lg focus:outline-none">
            <option value="">-- Chọn mã quyền --</option>
            {permissions.map((p) => (
              <option key={p.id} value={p.id}>
                {p.code} - {p.name}
              </option>
            ))}
          </select>
        </div>

        <div className="space-y-1">
          <label className="text-[10px] uppercase font-bold text-slate-500 block">Loại điều chỉnh</label>
          <div className="grid grid-cols-2 gap-2">
            <button
              type="button"
              onClick={() => setType("GRANT")}
              className={`py-1.5 text-xs font-bold rounded-lg border text-center transition-all ${
                type === "GRANT" ? "bg-emerald-50 text-emerald-600 border-emerald-200 shadow-sm" : "bg-slate-50 border-slate-200 text-slate-500"
              }`}
            >
              Cấp thêm quyền
            </button>
            <button
              type="button"
              onClick={() => setType("REVOKE")}
              className={`py-1.5 text-xs font-bold rounded-lg border text-center transition-all ${
                type === "REVOKE" ? "bg-rose-50 text-rose-600 border-rose-200 shadow-sm" : "bg-slate-50 border-slate-200 text-slate-500"
              }`}
            >
              Tước bỏ quyền
            </button>
          </div>
        </div>

        <div className="space-y-1">
          <label className="text-[10px] uppercase font-bold text-slate-500 block">Thời hạn áp dụng</label>
          <input
            type="date"
            value={expiresAt}
            onChange={(e) => setExpiresAt(e.target.value)}
            className="w-full bg-slate-50 border border-slate-200 text-xs p-2 rounded-lg focus:outline-none font-mono"
          />
        </div>

        <div className="space-y-1">
          <label className="text-[10px] uppercase font-bold text-slate-500 block">Mục đích điều chỉnh / Lý do ủy quyền</label>
          <textarea
            value={reason}
            onChange={(e) => setReason(e.target.value)}
            placeholder="Lý do chi tiết theo quyết định ban giám đốc..."
            required
            rows={3}
            className="w-full bg-slate-50 border border-slate-200 text-xs p-2.5 rounded-lg focus:outline-none text-slate-700"
          />
        </div>

        <button type="submit" className="w-full bg-brand-gradient hover:opacity-90 text-white font-bold text-xs py-2 px-4 rounded-lg flex items-center justify-center gap-1 transition-all shadow-md">
          <Plus className="w-3.5 h-3.5 text-white" />
          <span>Áp dụng tức thì</span>
        </button>
      </form>
    </div>
  );
}
