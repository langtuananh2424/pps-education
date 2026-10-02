import React, { useMemo, useState } from "react";
import { Save } from "lucide-react";
import { useTranslation } from "react-i18next";
import { ApiError } from "@/lib/apiClient";
import { toCodeSlug } from "@/lib/slugify";
import { cn } from "@/lib/cn";
import { createRole, DataScope, RoleResponse } from "../api";
import Button from "@/components/ui/Button";
import Select from "@/components/ui/Select";
import FloatingError from "@/components/ui/FloatingError";

const inputClass = "w-full bg-slate-50 border border-slate-200 text-sm p-2.5 rounded-lg focus:outline-none";
const labelClass = "text-[12px] uppercase font-bold text-slate-500 block mb-1";
const SCOPES: DataScope[] = ["ALL", "SITE", "CLASS"];

interface CreateRolePanelProps {
  /** Vai trò có sẵn để chọn làm mẫu ("Tạo từ mẫu"). */
  roles: RoleResponse[];
  onCancel: () => void;
  onCreated: (roleId: number) => void;
}

/**
 * UC-03 bổ sung — tạo vai trò tùy chỉnh trong popup. V202 (bổ sung ngoài SDD gốc, đã xác nhận với người
 * dùng 2026-09-30): chọn phạm vi dữ liệu + vai trò mẫu để sao chép sẵn quyền; tick chi tiết làm tiếp trên
 * cây sidebar sau khi tạo (RoleAccessEditor), không tick trong popup nữa.
 */
export default function CreateRolePanel({ roles, onCancel, onCreated }: CreateRolePanelProps) {
  const { t } = useTranslation("system-admin-roles");
  const [name, setName] = useState("");
  const code = useMemo(() => toCodeSlug(name), [name]);
  const [description, setDescription] = useState("");
  const [templateRoleId, setTemplateRoleId] = useState("");
  const [scope, setScope] = useState<DataScope>("SITE");
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const chooseTemplate = (value: string) => {
    setTemplateRoleId(value);
    const template = roles.find((r) => String(r.id) === value);
    if (template && template.dataScope !== "SELF") setScope(template.dataScope);
  };

  const handleSave = async () => {
    if (!name.trim() || !code) {
      setError(t("createRolePanel.nameRequiredError"));
      return;
    }
    setSaving(true);
    setError(null);
    try {
      const role = await createRole({
        code,
        name: name.trim(),
        description: description.trim() || undefined,
        dataScope: scope,
        copyFromRoleId: templateRoleId ? Number(templateRoleId) : null
      });
      onCreated(role.id);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t("createRolePanel.createError"));
      setSaving(false);
    }
  };

  return (
    <div className="space-y-4">
      <div>
        <label className={labelClass}>{t("createRolePanel.nameLabel")}</label>
        <input value={name} onChange={(e) => setName(e.target.value)} placeholder={t("createRolePanel.namePlaceholder")} className={inputClass} autoFocus />
        {code && (
          <p className="text-[12px] text-slate-400 mt-1">
            {t("createRolePanel.codeHint")} <code className="font-mono font-bold text-brand-red">{code}</code>
          </p>
        )}
      </div>
      <div>
        <label className={labelClass}>{t("createRolePanel.descriptionLabel")}</label>
        <textarea value={description} onChange={(e) => setDescription(e.target.value)} rows={2} className={inputClass} />
      </div>

      <div>
        <label className={labelClass}>{t("createRolePanelV202.templateLabel")}</label>
        <Select value={templateRoleId} onChange={(e) => chooseTemplate(e.target.value)} className={inputClass} aria-label={t("createRolePanelV202.templateLabel")}>
          <option value="">{t("createRolePanelV202.templateNone")}</option>
          {roles.map((r) => (
            <option key={r.id} value={r.id}>
              {r.name}
            </option>
          ))}
        </Select>
        <p className="text-[12px] text-slate-400 mt-1">{t("createRolePanelV202.templateHint")}</p>
      </div>

      <div>
        <span className={labelClass}>{t("createRolePanelV202.scopeLabel")}</span>
        <div className="grid grid-cols-1 sm:grid-cols-3 gap-2">
          {SCOPES.map((value) => (
            <label
              key={value}
              className={cn("flex gap-2 items-start border rounded-xl p-2.5 cursor-pointer", scope === value ? "border-brand-red bg-orange-50/60" : "border-slate-200 bg-white")}
            >
              <input type="radio" name="create-role-scope" value={value} checked={scope === value} onChange={() => setScope(value)} className="mt-0.5 accent-brand-red" />
              <span>
                <span className="block text-sm font-bold text-slate-800">{t(`roleAccessEditor.scopes.${value}.title`)}</span>
                <span className="block text-[12px] text-slate-500">{t(`roleAccessEditor.scopes.${value}.description`)}</span>
              </span>
            </label>
          ))}
        </div>
      </div>

      <FloatingError message={error} onClose={() => setError(null)} />

      <div className="flex gap-2 pt-1">
        <Button type="button" variant="secondary" size="sm" onClick={onCancel}>
          {t("createRolePanel.cancelButton")}
        </Button>
        <Button type="button" variant="primary" size="sm" onClick={handleSave} disabled={saving}>
          <Save className="w-3.5 h-3.5" />
          {saving ? t("createRolePanel.saving") : t("createRolePanelV202.saveButton")}
        </Button>
      </div>
    </div>
  );
}
