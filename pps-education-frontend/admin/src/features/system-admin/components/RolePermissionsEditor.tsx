import React, { useEffect, useState } from "react";
import { Layers, Save } from "lucide-react";
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
      .catch((err) => setError(err instanceof ApiError ? err.message : "Không tải được ma trận quyền."))
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
      showToast("Đã lưu quyền hạn thành công!");
    } catch (err) {
      if (err instanceof ApiError && err.status === 409) {
        const proceed = await confirmDialog(`${err.message}\n\nBạn có chắc chắn muốn tiếp tục?`, { danger: true });
        if (!proceed) {
          setSaving(false);
          return;
        }
        try {
          await updateRolePermissions(roleId, Array.from(selectedIds), true);
          showToast("Đã lưu quyền hạn thành công!");
        } catch (err2) {
          setError(err2 instanceof ApiError ? err2.message : "Lưu quyền hạn thất bại.");
          setSaving(false);
          return;
        }
      } else {
        setError(err instanceof ApiError ? err.message : "Lưu quyền hạn thất bại.");
        setSaving(false);
        return;
      }
    }
    setSaving(false);
  };

  if (loading) return <p className="text-xs text-slate-500">Đang tải ma trận quyền...</p>;

  return (
    <div className="space-y-6">
      {error && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg">{error}</div>}

      <div className="flex justify-end">
        <Button variant="primary" size="sm" onClick={handleSave} disabled={saving}>
          <Save className="w-3.5 h-3.5" />
          {saving ? "Đang lưu..." : "Lưu thay đổi"}
        </Button>
      </div>

      <div className="bg-orange-50/60 border border-orange-100 rounded-xl p-3.5 flex items-center gap-2 text-xs text-slate-800 shadow-sm">
        <Layers className="w-4.5 h-4.5 text-brand-red shrink-0" />
        <span>
          Đã kích hoạt <strong>{selectedIds.size} / {items.length}</strong> quyền hạt nhân cho vai trò <strong>{roleName}</strong>.
        </span>
      </div>

      <PermissionChecklist items={items} selectedIds={selectedIds} onToggle={toggle} onToggleModuleAll={toggleModuleAll} />

      <Toast message={toastMessage} />
    </div>
  );
}
