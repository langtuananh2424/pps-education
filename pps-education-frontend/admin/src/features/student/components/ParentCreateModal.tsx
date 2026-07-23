import React, { useState } from "react";
import { Plus } from "lucide-react";
import { ApiError } from "@/lib/apiClient";
import AccountSelector, { AccountSelection } from "@/features/system-admin/components/AccountSelector";
import { createParent, ParentResponse } from "../api";
import AvatarUploadField from "@/components/ui/AvatarUploadField";
import { uploadMedia } from "@/features/lms/api";

const inputClass = "w-full bg-slate-50 border border-slate-200 text-xs p-2.5 rounded-lg focus:outline-none";
const labelClass = "text-[10px] uppercase font-bold text-slate-500 block mb-1";

interface ParentCreateModalProps {
  onClose: () => void;
  onCreated: (parent: ParentResponse) => void;
}

/**
 * UC-13 Main Flow bước 2: khởi tạo hồ sơ Phụ huynh ĐỘC LẬP, chưa cần biết
 * trước con em họ là ai — liên kết học sinh làm sau ở màn chi tiết. Trước
 * đây chỉ tạo được lồng trong flow tạo Học sinh; giờ tách riêng theo đúng
 * cơ chế backend (`createParent` không bắt buộc studentId).
 */
export default function ParentCreateModal({ onClose, onCreated }: ParentCreateModalProps) {
  const [account, setAccount] = useState<AccountSelection>({ newAccount: { username: "", email: "", fullName: "", phone: "", password: "" } });
  const [form, setForm] = useState({ occupation: "", workplace: "", address: "", notes: "", portraitUrl: "" });
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [submitAttempted, setSubmitAttempted] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setSubmitAttempted(true);
    if (!account.userId && (!account.newAccount?.username || !account.newAccount?.email || !account.newAccount?.fullName)) {
      setError("Vui lòng chọn tài khoản có sẵn hoặc điền đủ thông tin tài khoản mới.");
      return;
    }
    setSubmitting(true);
    setError(null);
    try {
      const parent = await createParent({
        userId: account.userId,
        newAccount: account.userId ? undefined : account.newAccount,
        occupation: form.occupation.trim() || undefined,
        workplace: form.workplace.trim() || undefined,
        address: form.address.trim() || undefined,
        notes: form.notes.trim() || undefined,
        portraitUrl: form.portraitUrl || undefined
      });
      onCreated(parent);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Tạo hồ sơ phụ huynh thất bại.");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
      <div className="fixed inset-0 bg-slate-900/50 backdrop-blur-xs" onClick={onClose} />
      <div className="relative w-full max-w-lg max-h-[90vh] overflow-y-auto bg-white rounded-2xl border border-slate-200 shadow-xl">
        <div className="sticky top-0 bg-white px-5 py-4 border-b border-slate-100">
          <h3 className="text-sm font-bold font-display text-slate-900">Thêm phụ huynh mới (UC-13)</h3>
          <p className="text-[11px] text-slate-500 mt-1">Tạo hồ sơ phụ huynh trước, liên kết con em sau tại màn chi tiết.</p>
        </div>
        <form onSubmit={handleSubmit} className="p-5 space-y-4">
          {error && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg">{error}</div>}

          <div className="space-y-2">
            <span className="text-[10px] font-bold uppercase text-slate-500">Tài khoản</span>
            <AccountSelector value={account} onChange={setAccount} submitAttempted={submitAttempted} />
          </div>

          <div className="space-y-2 border-t border-slate-100 pt-4">
            <span className="text-[10px] font-bold uppercase text-slate-500">Ảnh đại diện</span>
            <AvatarUploadField
              value={form.portraitUrl}
              onChange={(url) => setForm({ ...form, portraitUrl: url })}
              onUpload={(file) => uploadMedia(file, "PARENT")}
              fallbackName={account.newAccount?.fullName || "Phụ huynh"}
            />
          </div>

          <div className="space-y-3 border-t border-slate-100 pt-4">
            <span className="text-[10px] font-bold uppercase text-slate-500">Thông tin phụ huynh</span>
            <div className="grid grid-cols-2 gap-3">
              <input value={form.occupation} onChange={(e) => setForm({ ...form, occupation: e.target.value })} placeholder="Nghề nghiệp" className={inputClass} />
              <input value={form.workplace} onChange={(e) => setForm({ ...form, workplace: e.target.value })} placeholder="Nơi làm việc" className={inputClass} />
              <input value={form.address} onChange={(e) => setForm({ ...form, address: e.target.value })} placeholder="Địa chỉ" className={`${inputClass} col-span-2`} />
              <textarea value={form.notes} onChange={(e) => setForm({ ...form, notes: e.target.value })} placeholder="Ghi chú" rows={2} className={`${inputClass} col-span-2`} />
            </div>
          </div>

          <div className="flex justify-end gap-2 pt-2">
            <button type="button" onClick={onClose} className="px-4 py-2 text-xs font-semibold rounded-lg border border-slate-200 text-slate-700 hover:bg-slate-50">
              Hủy
            </button>
            <button
              type="submit"
              disabled={submitting}
              className="px-4 py-2 text-xs font-bold rounded-lg bg-brand-gradient text-white shadow-glow hover:opacity-95 disabled:opacity-50 flex items-center gap-1.5"
            >
              <Plus className="w-3.5 h-3.5" />
              {submitting ? "Đang tạo..." : "Tạo phụ huynh"}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
