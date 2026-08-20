import React, { useState } from "react";
import { Crosshair, Plus, Save } from "lucide-react";
import { useTranslation } from "react-i18next";
import { ApiError } from "@/lib/apiClient";
import { createSite, CreateSiteRequest, SiteResponse, updateSite, UpdateSiteRequest } from "@/features/facility/api";
import Modal from "@/components/ui/Modal";
import Button from "@/components/ui/Button";
import Select from "@/components/ui/Select";

const inputClass = "w-full bg-slate-50 border border-slate-200 text-xs p-2.5 rounded-lg focus:outline-none";
const labelClass = "text-[10px] uppercase font-bold text-slate-500 block mb-1";

interface AttendanceSiteFormModalProps {
  site?: SiteResponse | null;
  onClose: () => void;
  onSaved: (id: number) => void;
}

/**
 * UC-36 (createSite/updateSite) — dùng cho setup "Địa điểm chấm công": khác
 * SiteFormModal (facility) ở chỗ tập trung vào toạ độ GPS (dùng validate bán
 * kính chấm công UC-09 A2), không có phần liên hệ trường liên kết.
 */
export default function AttendanceSiteFormModal({ site, onClose, onSaved }: AttendanceSiteFormModalProps) {
  const { t } = useTranslation("hrm-attendance");
  const isEdit = site != null;
  const [form, setForm] = useState({
    code: site?.code ?? "",
    name: site?.name ?? "",
    siteType: (site?.siteType ?? "OWNED") as "OWNED" | "PARTNER",
    address: site?.address ?? "",
    district: site?.district ?? "",
    phone: site?.phone ?? "",
    status: (site?.status ?? "ACTIVE") as "ACTIVE" | "INACTIVE" | "PENDING",
    latitude: site?.latitude != null ? String(site.latitude) : "",
    longitude: site?.longitude != null ? String(site.longitude) : ""
  });
  const [submitting, setSubmitting] = useState(false);
  const [locating, setLocating] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const useCurrentLocation = () => {
    if (!navigator.geolocation) {
      setError(t("attendanceSiteForm.gpsUnsupported"));
      return;
    }
    setLocating(true);
    navigator.geolocation.getCurrentPosition(
      (pos) => {
        setForm((f) => ({ ...f, latitude: String(pos.coords.latitude), longitude: String(pos.coords.longitude) }));
        setLocating(false);
      },
      () => {
        setError(t("attendanceSiteForm.gpsError"));
        setLocating(false);
      },
      { enableHighAccuracy: true, timeout: 10000 }
    );
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!form.code.trim() || !form.name.trim()) {
      setError(t("attendanceSiteForm.requiredFieldsError"));
      return;
    }
    const latitude = form.latitude.trim() === "" ? undefined : Number(form.latitude);
    const longitude = form.longitude.trim() === "" ? undefined : Number(form.longitude);
    if ((latitude != null && Number.isNaN(latitude)) || (longitude != null && Number.isNaN(longitude))) {
      setError(t("attendanceSiteForm.invalidCoordinates"));
      return;
    }

    setSubmitting(true);
    setError(null);
    try {
      if (isEdit) {
        const request: UpdateSiteRequest = {
          name: form.name.trim(),
          siteType: form.siteType,
          address: form.address.trim() || undefined,
          district: form.district.trim() || undefined,
          phone: form.phone.trim() || undefined,
          status: form.status,
          latitude,
          longitude
        };
        const updated = await updateSite(site!.id, request);
        onSaved(updated.id);
      } else {
        const request: CreateSiteRequest = {
          code: form.code.trim(),
          name: form.name.trim(),
          siteType: form.siteType,
          address: form.address.trim() || undefined,
          district: form.district.trim() || undefined,
          phone: form.phone.trim() || undefined,
          latitude,
          longitude
        };
        const created = await createSite(request);
        onSaved(created.id);
      }
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t("attendanceSiteForm.saveError"));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Modal open onClose={onClose} title={isEdit ? t("attendanceSiteForm.editTitle") : t("attendanceSiteForm.createTitle")} size="lg">
      <form onSubmit={handleSubmit} className="space-y-4">
        {error && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg">{error}</div>}
        <div className="grid grid-cols-2 gap-3">
          <div>
            <label className={labelClass}>{t("attendanceSiteForm.codeLabel")}</label>
            <input
              value={form.code}
              disabled={isEdit}
              onChange={(e) => setForm({ ...form, code: e.target.value.toUpperCase() })}
              className={`${inputClass} font-mono disabled:opacity-60`}
            />
          </div>
          <div>
            <label className={labelClass}>{t("attendanceSiteForm.typeLabel")}</label>
            <Select
              value={form.siteType}
              onChange={(e) => setForm({ ...form, siteType: e.target.value as "OWNED" | "PARTNER" })}
              className={inputClass}
            >
              <option value="OWNED">{t("attendanceSiteForm.typeOwned")}</option>
              <option value="PARTNER">{t("attendanceSiteForm.typePartner")}</option>
            </Select>
          </div>
          <div className="col-span-2">
            <label className={labelClass}>{t("attendanceSiteForm.nameLabel")}</label>
            <input value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} className={inputClass} />
          </div>
          <div>
            <label className={labelClass}>{t("attendanceSiteForm.addressLabel")}</label>
            <input value={form.address} onChange={(e) => setForm({ ...form, address: e.target.value })} className={inputClass} />
          </div>
          <div>
            <label className={labelClass}>{t("attendanceSiteForm.districtLabel")}</label>
            <input value={form.district} onChange={(e) => setForm({ ...form, district: e.target.value })} className={inputClass} />
          </div>
          <div>
            <label className={labelClass}>{t("attendanceSiteForm.phoneLabel")}</label>
            <input value={form.phone} onChange={(e) => setForm({ ...form, phone: e.target.value })} className={inputClass} />
          </div>
          {isEdit && (
            <div>
              <label className={labelClass}>{t("attendanceSiteForm.statusLabel")}</label>
              <Select
                value={form.status}
                onChange={(e) => setForm({ ...form, status: e.target.value as "ACTIVE" | "INACTIVE" | "PENDING" })}
                className={inputClass}
              >
                <option value="ACTIVE">{t("attendanceSiteForm.statusActive")}</option>
                <option value="INACTIVE">{t("attendanceSiteForm.statusInactive")}</option>
                <option value="PENDING">{t("attendanceSiteForm.statusPending")}</option>
              </Select>
            </div>
          )}
        </div>

        <div className="space-y-2 border-t border-slate-100 pt-4">
          <div className="flex items-center justify-between">
            <span className="text-[10px] font-bold uppercase text-slate-500">{t("attendanceSiteForm.gpsSectionTitle")}</span>
            <Button type="button" variant="ghost" size="sm" disabled={locating} onClick={useCurrentLocation}>
              <Crosshair className="w-3.5 h-3.5" />
              {locating ? t("attendanceSiteForm.locating") : t("attendanceSiteForm.useCurrentLocation")}
            </Button>
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className={labelClass}>{t("attendanceSiteForm.latitudeLabel")}</label>
              <input
                value={form.latitude}
                onChange={(e) => setForm({ ...form, latitude: e.target.value })}
                placeholder={t("attendanceSiteForm.latitudeExample")}
                className={`${inputClass} font-mono`}
              />
            </div>
            <div>
              <label className={labelClass}>{t("attendanceSiteForm.longitudeLabel")}</label>
              <input
                value={form.longitude}
                onChange={(e) => setForm({ ...form, longitude: e.target.value })}
                placeholder={t("attendanceSiteForm.longitudeExample")}
                className={`${inputClass} font-mono`}
              />
            </div>
          </div>
          <p className="text-[10px] text-slate-400">{t("attendanceSiteForm.gpsHint")}</p>
        </div>

        <div className="flex justify-end gap-2 pt-2">
          <Button type="button" variant="secondary" onClick={onClose}>
            {t("attendanceSiteForm.cancel")}
          </Button>
          <Button type="submit" variant="primary" disabled={submitting}>
            {isEdit ? <Save className="w-3.5 h-3.5" /> : <Plus className="w-3.5 h-3.5" />}
            {submitting ? t("attendanceSiteForm.saving") : isEdit ? t("attendanceSiteForm.saveChangesButton") : t("attendanceSiteForm.createButton")}
          </Button>
        </div>
      </form>
    </Modal>
  );
}
