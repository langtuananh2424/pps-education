import React, { useState } from "react";
import { Plus } from "lucide-react";
import { useTranslation } from "react-i18next";
import { ApiError } from "@/lib/apiClient";
import { createSite, CreateSiteRequest } from "../api";
import Modal from "@/components/ui/Modal";
import Button from "@/components/ui/Button";
import Select from "@/components/ui/Select";

const inputClass = "w-full bg-slate-50 border border-slate-200 text-xs p-2.5 rounded-lg focus:outline-none";
const labelClass = "text-[10px] uppercase font-bold text-slate-500 block mb-1";

interface SiteFormModalProps {
  onClose: () => void;
  onCreated: (id: number) => void;
}

/** UC-36 Main Flow bước 1-4: khởi tạo điểm trường mới. */
export default function SiteFormModal({ onClose, onCreated }: SiteFormModalProps) {
  const { t } = useTranslation("facility");
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
      setError(t("siteForm.validationError"));
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
      setError(err instanceof ApiError ? err.message : t("siteForm.createError"));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Modal open onClose={onClose} title={t("siteForm.modalTitle")} size="lg">
      <form onSubmit={handleSubmit} className="space-y-4">
        {error && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg">{error}</div>}
        <div className="grid grid-cols-2 gap-3">
          <div>
            <label className={labelClass}>{t("siteForm.codeLabel")}</label>
            <input value={form.code} onChange={(e) => setForm({ ...form, code: e.target.value.toUpperCase() })} className={`${inputClass} font-mono`} />
          </div>
          <div>
            <label className={labelClass}>{t("siteForm.siteTypeLabel")}</label>
            <Select value={form.siteType} onChange={(e) => setForm({ ...form, siteType: e.target.value as "OWNED" | "PARTNER" })} className={inputClass}>
              <option value="OWNED">{t("siteType.OWNED")}</option>
              <option value="PARTNER">{t("siteType.PARTNER")}</option>
            </Select>
          </div>
          <div className="col-span-2">
            <label className={labelClass}>{t("siteForm.nameLabel")}</label>
            <input value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} className={inputClass} />
          </div>
          <div>
            <label className={labelClass}>{t("siteForm.addressLabel")}</label>
            <input value={form.address} onChange={(e) => setForm({ ...form, address: e.target.value })} className={inputClass} />
          </div>
          <div>
            <label className={labelClass}>{t("siteForm.districtLabel")}</label>
            <input value={form.district} onChange={(e) => setForm({ ...form, district: e.target.value })} className={inputClass} />
          </div>
          <div>
            <label className={labelClass}>{t("siteForm.phoneLabel")}</label>
            <input value={form.phone} onChange={(e) => setForm({ ...form, phone: e.target.value })} className={inputClass} />
          </div>
        </div>

        {form.siteType === "PARTNER" && (
          <div className="space-y-3 border-t border-slate-100 pt-4">
            <span className="text-[10px] font-bold uppercase text-slate-500">{t("siteForm.partnerContactTitle")}</span>
            <div className="grid grid-cols-2 gap-3">
              <input value={form.contactPersonName} onChange={(e) => setForm({ ...form, contactPersonName: e.target.value })} placeholder={t("siteForm.contactNamePlaceholder")} className={inputClass} />
              <input value={form.contactPersonTitle} onChange={(e) => setForm({ ...form, contactPersonTitle: e.target.value })} placeholder={t("siteForm.contactTitlePlaceholder")} className={inputClass} />
              <input value={form.contactPhone} onChange={(e) => setForm({ ...form, contactPhone: e.target.value })} placeholder={t("siteForm.contactPhonePlaceholder")} className={inputClass} />
              <input type="email" value={form.contactEmail} onChange={(e) => setForm({ ...form, contactEmail: e.target.value })} placeholder={t("siteForm.contactEmailPlaceholder")} className={inputClass} />
              <textarea
                value={form.additionalInfo}
                onChange={(e) => setForm({ ...form, additionalInfo: e.target.value })}
                placeholder={t("siteForm.additionalInfoPlaceholder")}
                rows={2}
                className={`${inputClass} col-span-2`}
              />
            </div>
          </div>
        )}

        <div className="flex justify-end gap-2 pt-2">
          <Button type="button" variant="secondary" onClick={onClose}>
            {t("siteForm.cancel")}
          </Button>
          <Button type="submit" variant="primary" disabled={submitting}>
            <Plus className="w-3.5 h-3.5" />
            {submitting ? t("siteForm.creating") : t("siteForm.createButton")}
          </Button>
        </div>
      </form>
    </Modal>
  );
}
