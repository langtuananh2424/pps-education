import React, { useEffect, useMemo, useState } from "react";
import { Save } from "lucide-react";
import { ApiError } from "@/lib/apiClient";
import { toCodeSlug } from "@/lib/slugify";
import { createRole, listPermissions, PermissionCatalogItem, updateRolePermissions } from "../api";
import Button from "@/components/ui/Button";
import PermissionChecklist from "./PermissionChecklist";

const inputClass = "w-full bg-slate-50 border border-slate-200 text-sm p-2.5 rounded-lg focus:outline-none";
const labelClass = "text-sm uppercase font-bold text-slate-500 block mb-1";

interface CreateRolePanelProps {
  onCancel: () => void;
  onCreated: (roleId: number) => void;
}

/** UC-03 bổ sung — tạo vai trò tùy chỉnh mới trong popup (Modal), nhập thông tin + tick quyền ban đầu rồi Lưu 1 lần. */
export default function CreateRolePanel({ onCancel, onCreated }: CreateRolePanelProps) {
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
      .catch((err) => setError(err instanceof ApiError ? err.message : "Không tải được danh mục quyền."))
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
      setError("Vui lòng điền tên vai trò (dùng để tự sinh mã vai trò).");
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
      setError(err instanceof ApiError ? err.message : "Tạo vai trò thất bại.");
      setSaving(false);
    }
  };

  return (
    <div className="space-y-4">
      <div>
        <label className={labelClass}>Tên vai trò *</label>
        <input value={name} onChange={(e) => setName(e.target.value)} placeholder="VD: Trưởng nhóm Marketing" className={inputClass} autoFocus />
        {code && (
          <p className="text-sm text-slate-400 mt-1">
            Mã vai trò (tự sinh): <code className="font-mono font-bold text-brand-red">{code}</code>
          </p>
        )}
      </div>
      <div>
        <label className={labelClass}>Mô tả</label>
        <textarea value={description} onChange={(e) => setDescription(e.target.value)} rows={2} className={inputClass} />
      </div>

      {error && <div className="text-sm text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg">{error}</div>}

      <div className="flex items-center justify-between">
        <span className="text-sm uppercase font-bold text-slate-500">Tick chọn quyền ban đầu ({selectedIds.size} đã chọn) — có thể để trống, cấu hình sau</span>
      </div>

      {loading ? (
        <p className="text-sm text-slate-500">Đang tải danh mục quyền...</p>
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
          Hủy
        </Button>
        <Button type="button" variant="primary" size="sm" onClick={handleSave} disabled={saving || loading}>
          <Save className="w-3.5 h-3.5" />
          {saving ? "Đang tạo..." : "Lưu vai trò mới"}
        </Button>
      </div>
    </div>
  );
}
