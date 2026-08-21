import React, { useState } from "react";
import { useTranslation } from "react-i18next";
import { Plus } from "lucide-react";
import { ApiError } from "@/lib/apiClient";
import { PermissionCatalogItem } from "../api";
import DatePicker from "@/components/ui/DatePicker";
import Select from "@/components/ui/Select";

const TODAY_ISO = new Date().toISOString().slice(0, 10);

interface CreateOverrideFormProps {
  permissions: PermissionCatalogItem[];
  onSubmit: (permissionId: number, overrideType: "GRANT" | "REVOKE", reason: string, expiresAt: string) => Promise<void>;
}

export default function CreateOverrideForm({ permissions, onSubmit }: CreateOverrideFormProps) {
  const { t } = useTranslation("system-admin-overrides");
  const [permissionId, setPermissionId] = useState("");
  const [type, setType] = useState<"GRANT" | "REVOKE">("GRANT");
  const [expiresAt, setExpiresAt] = useState("");
  const [reason, setReason] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!permissionId || !reason.trim()) {
      setError(t("createOverrideForm.validationError"));
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
      setError(err instanceof ApiError ? err.message : t("createOverrideForm.submitError"));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="lg:col-span-1 bg-white p-5 rounded-xl border border-slate-200 shadow-sm space-y-4 h-fit">
      <span className="text-xs font-bold text-slate-800 uppercase tracking-wider block border-b pb-2">{t("createOverrideForm.title")}</span>

      <form onSubmit={handleSubmit} className="space-y-4">
        {error && <div className="text-[11px] text-rose-600 bg-rose-50 border border-rose-100 p-2 rounded-lg">{error}</div>}

        <div className="space-y-1">
          <label className="text-[10px] uppercase font-bold text-slate-500 block">{t("createOverrideForm.permissionLabel")}</label>
          <Select value={permissionId} onChange={(e) => setPermissionId(e.target.value)} required className="w-full bg-slate-50 border border-slate-200 text-xs p-2.5 rounded-lg focus:outline-none">
            <option value="">{t("createOverrideForm.permissionPlaceholder")}</option>
            {permissions.map((p) => (
              <option key={p.id} value={p.id}>
                {p.code} - {p.name}
              </option>
            ))}
          </Select>
        </div>

        <div className="space-y-1">
          <label className="text-[10px] uppercase font-bold text-slate-500 block">{t("createOverrideForm.typeLabel")}</label>
          <div className="grid grid-cols-2 gap-2">
            <button
              type="button"
              onClick={() => setType("GRANT")}
              className={`py-1.5 text-xs font-bold rounded-lg border text-center transition-all ${
                type === "GRANT" ? "bg-emerald-50 text-emerald-600 border-emerald-200 shadow-sm" : "bg-slate-50 border-slate-200 text-slate-500"
              }`}
            >
              {t("createOverrideForm.typeGrant")}
            </button>
            <button
              type="button"
              onClick={() => setType("REVOKE")}
              className={`py-1.5 text-xs font-bold rounded-lg border text-center transition-all ${
                type === "REVOKE" ? "bg-rose-50 text-rose-600 border-rose-200 shadow-sm" : "bg-slate-50 border-slate-200 text-slate-500"
              }`}
            >
              {t("createOverrideForm.typeRevoke")}
            </button>
          </div>
        </div>

        <div className="space-y-1">
          <label className="text-[10px] uppercase font-bold text-slate-500 block">{t("createOverrideForm.expiresLabel")}</label>
          <DatePicker value={expiresAt} onChange={setExpiresAt} min={TODAY_ISO} />
        </div>

        <div className="space-y-1">
          <label className="text-[10px] uppercase font-bold text-slate-500 block">{t("createOverrideForm.reasonLabel")}</label>
          <textarea
            value={reason}
            onChange={(e) => setReason(e.target.value)}
            placeholder={t("createOverrideForm.reasonPlaceholder")}
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
          <span>{submitting ? t("createOverrideForm.submitting") : t("createOverrideForm.submitButton")}</span>
        </button>
      </form>
    </div>
  );
}
