import React, { useState } from "react";
import { Plus } from "lucide-react";
import { ApiError } from "@/lib/apiClient";
import { createSite, CreateSiteRequest } from "../api";
import Modal from "@/components/ui/Modal";
import Button from "@/components/ui/Button";

const inputClass = "w-full bg-slate-50 border border-slate-200 text-xs p-2.5 rounded-lg focus:outline-none";
const labelClass = "text-[10px] uppercase font-bold text-slate-500 block mb-1";

interface SiteFormModalProps {
  onClose: () => void;
  onCreated: (id: number) => void;
}

/** UC-36 Main Flow bước 1-4: khởi tạo điểm trường mới. */
export default function SiteFormModal({ onClose, onCreated }: SiteFormModalProps) {
  const [form, setForm] = useState({
    code: "",
    name: "",
    siteType: "OWNED" as "OWNED" | "PARTNER",
    address: "",
    district: "",
    phone: "",
    contactPersonName: "",
    contactPersonTitle: "",
    contactPhone: "",
    contactEmail: "",
    additionalInfo: ""
  });
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!form.code.trim() || !form.name.trim()) {
      setError("Vui lòng điền mã và tên điểm trường.");
      return;
    }
    setSubmitting(true);
    setError(null);
    try {
      const request: CreateSiteRequest = {
        code: form.code.trim(),
        name: form.name.trim(),
        siteType: form.siteType,
        address: form.address.trim() || undefined,
        district: form.district.trim() || undefined,
        phone: form.phone.trim() || undefined,
        partnerInfo:
          form.siteType === "PARTNER"
            ? {
                contactPersonName: form.contactPersonName.trim() || null,
                contactPersonTitle: form.contactPersonTitle.trim() || null,
                contactPhone: form.contactPhone.trim() || null,
                contactEmail: form.contactEmail.trim() || null,
                additionalInfo: form.additionalInfo.trim() || null
              }
            : undefined
      };
      const site = await createSite(request);
      onCreated(site.id);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Tạo điểm trường thất bại.");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Modal open onClose={onClose} title="Thêm điểm trường mới (UC-36)" size="lg">
      <form onSubmit={handleSubmit} className="space-y-4">
        {error && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg">{error}</div>}
        <div className="grid grid-cols-2 gap-3">
          <div>
            <label className={labelClass}>Mã điểm trường *</label>
            <input value={form.code} onChange={(e) => setForm({ ...form, code: e.target.value.toUpperCase() })} className={`${inputClass} font-mono`} />
          </div>
          <div>
            <label className={labelClass}>Loại hình *</label>
            <select value={form.siteType} onChange={(e) => setForm({ ...form, siteType: e.target.value as "OWNED" | "PARTNER" })} className={inputClass}>
              <option value="OWNED">Cơ sở tự vận hành</option>
              <option value="PARTNER">Trường liên kết</option>
            </select>
          </div>
          <div className="col-span-2">
            <label className={labelClass}>Tên điểm trường *</label>
            <input value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} className={inputClass} />
          </div>
          <div>
            <label className={labelClass}>Địa chỉ</label>
            <input value={form.address} onChange={(e) => setForm({ ...form, address: e.target.value })} className={inputClass} />
          </div>
          <div>
            <label className={labelClass}>Quận/Huyện</label>
            <input value={form.district} onChange={(e) => setForm({ ...form, district: e.target.value })} className={inputClass} />
          </div>
          <div>
            <label className={labelClass}>Số điện thoại</label>
            <input value={form.phone} onChange={(e) => setForm({ ...form, phone: e.target.value })} className={inputClass} />
          </div>
        </div>

        {form.siteType === "PARTNER" && (
          <div className="space-y-3 border-t border-slate-100 pt-4">
            <span className="text-[10px] font-bold uppercase text-slate-500">Liên hệ đầu mối trường liên kết</span>
            <div className="grid grid-cols-2 gap-3">
              <input value={form.contactPersonName} onChange={(e) => setForm({ ...form, contactPersonName: e.target.value })} placeholder="Tên người liên hệ" className={inputClass} />
              <input value={form.contactPersonTitle} onChange={(e) => setForm({ ...form, contactPersonTitle: e.target.value })} placeholder="Chức danh" className={inputClass} />
              <input value={form.contactPhone} onChange={(e) => setForm({ ...form, contactPhone: e.target.value })} placeholder="SĐT liên hệ" className={inputClass} />
              <input type="email" value={form.contactEmail} onChange={(e) => setForm({ ...form, contactEmail: e.target.value })} placeholder="Email liên hệ" className={inputClass} />
              <textarea
                value={form.additionalInfo}
                onChange={(e) => setForm({ ...form, additionalInfo: e.target.value })}
                placeholder="Ghi chú thêm"
                rows={2}
                className={`${inputClass} col-span-2`}
              />
            </div>
          </div>
        )}

        <div className="flex justify-end gap-2 pt-2">
          <Button type="button" variant="secondary" onClick={onClose}>
            Hủy
          </Button>
          <Button type="submit" variant="primary" disabled={submitting}>
            <Plus className="w-3.5 h-3.5" />
            {submitting ? "Đang tạo..." : "Tạo điểm trường"}
          </Button>
        </div>
      </form>
    </Modal>
  );
}
