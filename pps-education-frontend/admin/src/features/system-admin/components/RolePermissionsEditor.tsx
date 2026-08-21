import React, { useEffect, useState } from "react";
import { Layers, Save } from "lucide-react";
import { Trans, useTranslation } from "react-i18next";
import { ApiError } from "@/lib/apiClient";
import { getRolePermissionMatrix, PermissionMatrixItem, updateRolePermissions } from "../api";
import Button from "@/components/ui/Button";
import PermissionChecklist from "./PermissionChecklist";
import { useToast } from "@/lib/useToast";
import Toast from "@/components/ui/Toast";
import { useDialog } from "@/components/ui/DialogProvider";

interface RolePermissionsEditorProps {
  roleId: number;
  roleName: string;
}

export default function RolePermissionsEditor({ roleId, roleName }: RolePermissionsEditorProps) {
  const { t } = useTranslation("system-admin-roles");
  const [items, setItems] = useState<PermissionMatrixItem[]>([]);
  const [selectedIds, setSelectedIds] = useState<Set<number>>(new Set());
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const { message: toastMessage, showToast } = useToast();
  const { confirmDialog } = useDialog();

  useEffect(() => {
    setLoading(true);
    setError(null);
    getRolePermissionMatrix(roleId)
      .then((matrix) => {
        setItems(matrix.permissions);
        setSelectedIds(new Set(matrix.permissions.filter((p) => p.granted).map((p) => p.permissionId)));
      })
      .catch((err) => setError(err instanceof ApiError ? err.message : t("rolePermissionsEditor.loadError")))
      .finally(() => setLoading(false));
  }, [roleId]);

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
    setSaving(true);
    setError(null);
    try {
      await updateRolePermissions(roleId, Array.from(selectedIds), false);
      showToast(t("rolePermissionsEditor.saveSuccess"));
    } catch (err) {
      if (err instanceof ApiError && err.status === 409) {
        const proceed = await confirmDialog(t("rolePermissionsEditor.conflictConfirm", { message: err.message }), { danger: true });
        if (!proceed) {
          setSaving(false);
          return;
        }
        try {
          await updateRolePermissions(roleId, Array.from(selectedIds), true);
          showToast(t("rolePermissionsEditor.saveSuccess"));
        } catch (err2) {
          setError(err2 instanceof ApiError ? err2.message : t("rolePermissionsEditor.saveError"));
          setSaving(false);
          return;
        }
      } else {
        setError(err instanceof ApiError ? err.message : t("rolePermissionsEditor.saveError"));
        setSaving(false);
        return;
      }
    }
    setSaving(false);
  };

  if (loading) return <p className="text-xs text-slate-500">{t("rolePermissionsEditor.loadingMatrix")}</p>;

  return (
    <div className="space-y-6">
      {error && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg">{error}</div>}

      <div className="flex justify-end">
        <Button variant="primary" size="sm" onClick={handleSave} disabled={saving}>
          <Save className="w-3.5 h-3.5" />
          {saving ? t("rolePermissionsEditor.saving") : t("rolePermissionsEditor.saveButton")}
        </Button>
      </div>

      <div className="bg-orange-50/60 border border-orange-100 rounded-xl p-3.5 flex items-center gap-2 text-xs text-slate-800 shadow-sm">
        <Layers className="w-4.5 h-4.5 text-brand-red shrink-0" />
        <span>
          <Trans
            i18nKey="rolePermissionsEditor.activatedSummary"
            t={t}
            values={{ count: selectedIds.size, total: items.length, roleName }}
            components={{ b1: <strong />, b2: <strong /> }}
          />
        </span>
      </div>

      <PermissionChecklist items={items} selectedIds={selectedIds} onToggle={toggle} onToggleModuleAll={toggleModuleAll} />

      <Toast message={toastMessage} />
    </div>
  );
}
