import React, { useEffect, useMemo, useState } from "react";
import { Save } from "lucide-react";
import { useTranslation } from "react-i18next";
import { ApiError } from "@/lib/apiClient";
import { toCodeSlug } from "@/lib/slugify";
import { createRole, listPermissions, PermissionCatalogItem, updateRolePermissions } from "../api";
import Button from "@/components/ui/Button";
import PermissionChecklist from "./PermissionChecklist";

const inputClass = "w-full bg-slate-50 border border-slate-200 text-xs p-2.5 rounded-lg focus:outline-none";
const labelClass = "text-[10px] uppercase font-bold text-slate-500 block mb-1";

interface CreateRolePanelProps {
  onCancel: () => void;
  onCreated: (roleId: number) => void;
}

/** UC-03 bổ sung — tạo vai trò tùy chỉnh mới trong popup (Modal), nhập thông tin + tick quyền ban đầu rồi Lưu 1 lần. */
export default function CreateRolePanel({ onCancel, onCreated }: CreateRolePanelProps) {
  const { t } = useTranslation("system-admin-roles");
  const [name, setName] = useState("");
  const code = useMemo(() => toCodeSlug(name), [name]);
  const [description, setDescription] = useState("");
  const [permissions, setPermissions] = useState<PermissionCatalogItem[]>([]);
  const [selectedIds, setSelectedIds] = useState<Set<number>>(new Set());
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    listPermissions()
      .then(setPermissions)
      .catch((err) => setError(err instanceof ApiError ? err.message : t("createRolePanel.loadPermissionsError")))
      .finally(() => setLoading(false));
  }, []);

  const toggle = (permissionId: number) => {
    setSelectedIds((prev) => {
      const next = new Set(prev);
      if (next.has(permissionId)) next.delete(permissionId);
      else next.add(permissionId);
      return next;
    });
  };

  const toggleModuleAll = (ids: number[]) => {
    setSelectedIds((prev) => {
      const hasAll = ids.every((id) => prev.has(id));
      const next = new Set(prev);
      ids.forEach((id) => (hasAll ? next.delete(id) : next.add(id)));
      return next;
    });
  };

  const handleSave = async () => {
    if (!name.trim() || !code) {
      setError(t("createRolePanel.nameRequiredError"));
      return;
    }
    setSaving(true);
    setError(null);
    try {
      const role = await createRole({ code, name: name.trim(), description: description.trim() || undefined });
      if (selectedIds.size > 0) {
        await updateRolePermissions(role.id, Array.from(selectedIds), false);
      }
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
          <p className="text-[10px] text-slate-400 mt-1">
            {t("createRolePanel.codeHint")} <code className="font-mono font-bold text-brand-red">{code}</code>
          </p>
        )}
      </div>
      <div>
        <label className={labelClass}>{t("createRolePanel.descriptionLabel")}</label>
        <textarea value={description} onChange={(e) => setDescription(e.target.value)} rows={2} className={inputClass} />
      </div>

      {error && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg">{error}</div>}

      <div className="flex items-center justify-between">
        <span className="text-[10px] uppercase font-bold text-slate-500">{t("createRolePanel.permissionsLabel", { count: selectedIds.size })}</span>
      </div>

      {loading ? (
        <p className="text-xs text-slate-500">{t("createRolePanel.loadingPermissions")}</p>
      ) : (
        <div className="max-h-[360px] overflow-y-auto">
          <PermissionChecklist
            items={permissions.map((p) => ({ permissionId: p.id, code: p.code, name: p.name, module: p.module }))}
            selectedIds={selectedIds}
            onToggle={toggle}
            onToggleModuleAll={toggleModuleAll}
          />
        </div>
      )}

      <div className="flex gap-2 pt-1">
        <Button type="button" variant="secondary" size="sm" onClick={onCancel}>
          {t("createRolePanel.cancelButton")}
        </Button>
        <Button type="button" variant="primary" size="sm" onClick={handleSave} disabled={saving || loading}>
          <Save className="w-3.5 h-3.5" />
          {saving ? t("createRolePanel.saving") : t("createRolePanel.saveButton")}
        </Button>
      </div>
    </div>
  );
}
