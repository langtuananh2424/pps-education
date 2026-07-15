import React, { useEffect, useMemo, useState } from "react";
import { Save, X } from "lucide-react";
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

/** UC-03 bổ sung — tạo vai trò tùy chỉnh mới NGAY TRONG panel (không dùng modal), nhập thông tin + tick quyền ban đầu rồi Lưu 1 lần — đúng flow gốc. */
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
    <div className="flex-1 flex flex-col h-full">
      <div className="p-6 border-b border-slate-200 space-y-4 bg-slate-50/20">
        <div className="flex items-center justify-between">
          <h2 className="text-sm font-bold text-slate-800">Tạo vai trò tùy chỉnh mới (UC-03)</h2>
          <button onClick={onCancel} className="p-1 rounded-md text-slate-400 hover:text-slate-700 hover:bg-slate-100">
            <X className="w-4 h-4" />
          </button>
        </div>

        <div>
          <label className={labelClass}>Tên vai trò *</label>
          <input value={name} onChange={(e) => setName(e.target.value)} placeholder="VD: Trưởng nhóm Marketing" className={inputClass} autoFocus />
          {code && (
            <p className="text-[10px] text-slate-400 mt-1">
              Mã vai trò (tự sinh): <code className="font-mono font-bold text-brand-red">{code}</code>
            </p>
          )}
        </div>
        <div>
          <label className={labelClass}>Mô tả</label>
          <textarea value={description} onChange={(e) => setDescription(e.target.value)} rows={2} className={inputClass} />
        </div>
      </div>

      <div className="flex-1 p-6 overflow-y-auto max-h-[460px] space-y-4">
        {error && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg">{error}</div>}

        <div className="flex items-center justify-between">
          <span className="text-[10px] uppercase font-bold text-slate-500">Tick chọn quyền ban đầu ({selectedIds.size} đã chọn) — có thể để trống, cấu hình sau</span>
          <Button variant="primary" size="sm" onClick={handleSave} disabled={saving || loading}>
            <Save className="w-3.5 h-3.5" />
            {saving ? "Đang tạo..." : "Lưu vai trò mới"}
          </Button>
        </div>

        {loading ? (
          <p className="text-xs text-slate-500">Đang tải danh mục quyền...</p>
        ) : (
          <PermissionChecklist
            items={permissions.map((p) => ({ permissionId: p.id, code: p.code, name: p.name, module: p.module }))}
            selectedIds={selectedIds}
            onToggle={toggle}
            onToggleModuleAll={toggleModuleAll}
          />
        )}
      </div>
    </div>
  );
}
