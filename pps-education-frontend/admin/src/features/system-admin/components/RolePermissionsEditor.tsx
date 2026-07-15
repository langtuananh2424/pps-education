import React, { useEffect, useMemo, useState } from "react";
import { ChevronDown, ChevronUp, Layers, Save, Search } from "lucide-react";
import { ApiError } from "@/lib/apiClient";
import { getRolePermissionMatrix, PermissionMatrixItem, updateRolePermissions } from "../api";
import Button from "@/components/ui/Button";

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
  const [searchQuery, setSearchQuery] = useState("");
  const [moduleFilter, setModuleFilter] = useState("ALL");
  const [expandedModules, setExpandedModules] = useState<Record<string, boolean>>({});

  useEffect(() => {
    setLoading(true);
    setError(null);
    getRolePermissionMatrix(roleId)
      .then((matrix) => {
        setItems(matrix.permissions);
        setSelectedIds(new Set(matrix.permissions.filter((p) => p.granted).map((p) => p.permissionId)));
        setExpandedModules(Object.fromEntries(Array.from(new Set(matrix.permissions.map((p) => p.module))).map((m) => [m, true])));
      })
      .catch((err) => setError(err instanceof ApiError ? err.message : "Không tải được ma trận quyền."))
      .finally(() => setLoading(false));
  }, [roleId]);

  const modules = useMemo(() => Array.from(new Set(items.map((p) => p.module))).sort(), [items]);

  const toggleExpand = (mod: string) => setExpandedModules((prev) => ({ ...prev, [mod]: !prev[mod] }));

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
    } catch (err) {
      if (err instanceof ApiError && err.status === 409) {
        const proceed = window.confirm(`${err.message}\n\nBạn có chắc chắn muốn tiếp tục?`);
        if (!proceed) {
          setSaving(false);
          return;
        }
        try {
          await updateRolePermissions(roleId, Array.from(selectedIds), true);
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

      <div className="flex flex-col sm:flex-row items-center justify-between gap-3 bg-slate-50 p-3 rounded-xl border border-slate-200/60">
        <div className="relative w-full sm:w-64">
          <Search className="absolute left-2.5 top-2 w-3.5 h-3.5 text-slate-400" />
          <input
            type="text"
            placeholder="Tìm nhanh quyền trong vai trò..."
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            className="w-full bg-white border border-slate-200 text-xs pl-8 pr-3 py-1.5 rounded-lg focus:outline-none"
          />
        </div>

        <select
          value={moduleFilter}
          onChange={(e) => setModuleFilter(e.target.value)}
          className="bg-white border border-slate-200 text-xs px-2 py-1.5 rounded-lg focus:outline-none text-slate-700 font-semibold w-full sm:w-auto cursor-pointer"
        >
          <option value="ALL">Tất cả phân hệ nghiệp vụ</option>
          {modules.map((mod) => (
            <option key={mod} value={mod}>
              {mod}
            </option>
          ))}
        </select>

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

      <div className="space-y-4">
        {modules
          .filter((mod) => moduleFilter === "ALL" || mod === moduleFilter)
          .map((mod) => {
            const modulePerms = items.filter((p) => p.module === mod);
            const filtered = modulePerms.filter(
              (p) =>
                p.name.toLowerCase().includes(searchQuery.toLowerCase()) ||
                p.code.toLowerCase().includes(searchQuery.toLowerCase())
            );
            if (filtered.length === 0) return null;

            const isExpanded = expandedModules[mod] !== false;
            const selectedInModule = filtered.filter((p) => selectedIds.has(p.permissionId));
            const isAllSelected = selectedInModule.length === filtered.length;

            return (
              <div key={mod} className="border border-slate-200 rounded-xl overflow-hidden shadow-sm bg-white">
                <div className="bg-slate-50/70 p-3.5 border-b border-slate-200 flex items-center justify-between gap-3">
                  <div className="flex items-center gap-3">
                    <button type="button" onClick={() => toggleExpand(mod)} className="p-1 rounded hover:bg-slate-200/50 text-slate-500 transition-colors">
                      {isExpanded ? <ChevronUp className="w-4 h-4" /> : <ChevronDown className="w-4 h-4" />}
                    </button>

                    <input
                      type="checkbox"
                      checked={isAllSelected && filtered.length > 0}
                      onChange={() => toggleModuleAll(filtered.map((p) => p.permissionId))}
                      className="rounded border-slate-300 text-brand-red focus:ring-brand-orange h-4 w-4 cursor-pointer"
                    />

                    <span className="text-xs font-bold text-slate-800 font-sans tracking-tight leading-none uppercase">{mod}</span>
                  </div>

                  <span className="text-[10px] font-mono font-bold bg-white text-slate-600 border px-2.5 py-0.5 rounded-full shadow-inner">
                    {selectedInModule.length} / {filtered.length} selected
                  </span>
                </div>

                {isExpanded && (
                  <div className="divide-y divide-slate-100 p-1.5 bg-white">
                    {filtered.map((p) => {
                      const isChecked = selectedIds.has(p.permissionId);
                      return (
                        <label
                          key={p.permissionId}
                          className={`flex items-start gap-3.5 p-3.5 rounded-lg transition-colors cursor-pointer ${
                            isChecked ? "bg-orange-50/40 hover:bg-orange-50/60 text-slate-900" : "hover:bg-slate-50/50 text-slate-700"
                          }`}
                        >
                          <input
                            type="checkbox"
                            checked={isChecked}
                            onChange={() => toggle(p.permissionId)}
                            className="mt-0.5 rounded border-slate-300 text-brand-red focus:ring-brand-orange h-4 w-4 cursor-pointer shrink-0"
                          />
                          <div className="flex-1 space-y-1">
                            <div className="flex flex-wrap items-center gap-2">
                              <span className="text-xs font-bold tracking-tight text-slate-900 block leading-tight">{p.name}</span>
                              <code className="text-[9px] font-mono font-bold text-brand-red bg-orange-50/60 border border-orange-100 px-1.5 py-0.2 rounded shrink-0">
                                {p.code}
                              </code>
                            </div>
                          </div>
                        </label>
                      );
                    })}
                  </div>
                )}
              </div>
            );
          })}
      </div>
    </div>
  );
}
