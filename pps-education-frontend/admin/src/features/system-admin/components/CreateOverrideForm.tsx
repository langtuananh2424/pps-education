import React, { useState } from "react";
import { Plus } from "lucide-react";
import { ApiError } from "@/lib/apiClient";
import { PermissionCatalogItem } from "../api";
import DatePicker from "@/components/ui/DatePicker";

const TODAY_ISO = new Date().toISOString().slice(0, 10);

interface CreateOverrideFormProps {
  permissions: PermissionCatalogItem[];
  onSubmit: (permissionId: number, overrideType: "GRANT" | "REVOKE", reason: string, expiresAt: string) => Promise<void>;
}

export default function CreateOverrideForm({ permissions, onSubmit }: CreateOverrideFormProps) {
  const [permissionId, setPermissionId] = useState("");
  const [type, setType] = useState<"GRANT" | "REVOKE">("GRANT");
  const [expiresAt, setExpiresAt] = useState("");
  const [reason, setReason] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!permissionId || !reason.trim()) {
      setError("Vui lòng chọn quyền và nhập lý do.");
      return;
    }
    setSubmitting(true);
    setError(null);
    try {
      // Input date chỉ có ngày — quy ước hết hạn vào cuối ngày đó (giờ local), chuyển sang ISO date-time có offset cho backend (OffsetDateTime).
      const expiresAtIso = expiresAt ? new Date(`${expiresAt}T23:59:59`).toISOString() : "";
      await onSubmit(Number(permissionId), type, reason.trim(), expiresAtIso);
      setPermissionId("");
      setReason("");
      setExpiresAt("");
      setType("GRANT");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Lưu ngoại lệ thất bại.");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="lg:col-span-1 bg-white p-5 rounded-xl border border-slate-200 shadow-sm space-y-4 h-fit">
      <span className="text-xs font-bold text-slate-800 uppercase tracking-wider block border-b pb-2">Bước 2 — Thiết lập ngoại lệ</span>

      <form onSubmit={handleSubmit} className="space-y-4">
        {error && <div className="text-[11px] text-rose-600 bg-rose-50 border border-rose-100 p-2 rounded-lg">{error}</div>}

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
          <label className="text-[10px] uppercase font-bold text-slate-500 block">Thời hạn áp dụng (tùy chọn)</label>
          <DatePicker value={expiresAt} onChange={setExpiresAt} min={TODAY_ISO} />
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

        <button
          type="submit"
          disabled={submitting}
          className="w-full bg-brand-gradient hover:opacity-90 text-white font-bold text-xs py-2 px-4 rounded-lg flex items-center justify-center gap-1 transition-all shadow-md disabled:opacity-50"
        >
          <Plus className="w-3.5 h-3.5 text-white" />
          <span>{submitting ? "Đang lưu..." : "Áp dụng"}</span>
        </button>
      </form>
    </div>
  );
}
