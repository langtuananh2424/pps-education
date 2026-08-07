import React, { useMemo, useState } from "react";
import { ChevronDown, ChevronUp, Search } from "lucide-react";
import { permissionGroupsByModule } from "../constants/permissionGroups";
import Select from "@/components/ui/Select";

export interface ChecklistItem {
  permissionId: number;
  code: string;
  name: string;
  module: string;
}

interface PermissionChecklistProps {
  items: ChecklistItem[];
  selectedIds: Set<number>;
  onToggle: (permissionId: number) => void;
  onToggleModuleAll: (ids: number[]) => void;
}

/** Danh sách quyền hạt nhân theo module, có tìm kiếm/lọc — dùng chung cho sửa quyền role có sẵn (RolePermissionsEditor) và tạo role mới (CreateRolePanel). */
export default function PermissionChecklist({ items, selectedIds, onToggle, onToggleModuleAll }: PermissionChecklistProps) {
  const [searchQuery, setSearchQuery] = useState("");
  const [moduleFilter, setModuleFilter] = useState("ALL");
  const [expandedModules, setExpandedModules] = useState<Record<string, boolean>>({});
  const [expandedGroups, setExpandedGroups] = useState<Record<string, boolean>>({});

  const modules = useMemo(() => Array.from(new Set(items.map((p) => p.module))).sort(), [items]);
  const toggleExpand = (mod: string) => setExpandedModules((prev) => ({ ...prev, [mod]: !prev[mod] }));
  const toggleGroupExpand = (groupKey: string) => setExpandedGroups((prev) => ({ ...prev, [groupKey]: !prev[groupKey] }));

  return (
    <div className="space-y-4">
      <div className="flex flex-col sm:flex-row items-center justify-between gap-3 bg-slate-50 p-3 rounded-xl border border-slate-200/60">
        <div className="relative w-full sm:w-64">
          <Search className="absolute left-2.5 top-2 w-3.5 h-3.5 text-slate-400" />
          <input
            type="text"
            placeholder="Tìm nhanh quyền..."
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            className="w-full bg-white border border-slate-200 text-sm pl-8 pr-3 py-1.5 rounded-lg focus:outline-none"
          />
        </div>

        <Select
          value={moduleFilter}
          onChange={(e) => setModuleFilter(e.target.value)}
          className="bg-white border border-slate-200 text-sm px-2 py-1.5 rounded-lg focus:outline-none text-slate-700 font-semibold w-full sm:w-auto cursor-pointer"
        >
          <option value="ALL">Tất cả phân hệ nghiệp vụ</option>
          {modules.map((mod) => (
            <option key={mod} value={mod}>
              {mod}
            </option>
          ))}
        </Select>
      </div>

      {modules
        .filter((mod) => moduleFilter === "ALL" || mod === moduleFilter)
        .map((mod) => {
          const modulePerms = items.filter((p) => p.module === mod);
          const filtered = modulePerms.filter(
            (p) => p.name.toLowerCase().includes(searchQuery.toLowerCase()) || p.code.toLowerCase().includes(searchQuery.toLowerCase())
          );
          if (filtered.length === 0) return null;

          const isExpanded = expandedModules[mod] !== false;
          const selectedInModule = filtered.filter((p) => selectedIds.has(p.permissionId));
          const isAllSelected = selectedInModule.length === filtered.length;

          // Tách thành nhóm con (VD "Điểm danh" gồm tạo/sửa/xoá) + phần còn lại hiện phẳng như cũ.
          const groupDefs = permissionGroupsByModule[mod] ?? [];
          const groupedCodeSet = new Set(groupDefs.flatMap((g) => g.codes));
          const standaloneItems = filtered.filter((p) => !groupedCodeSet.has(p.code));

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
                    onChange={() => onToggleModuleAll(filtered.map((p) => p.permissionId))}
                    className="rounded border-slate-300 text-brand-red focus:ring-brand-orange h-4 w-4 cursor-pointer"
                  />

                  <span className="text-sm font-bold text-slate-800 font-sans tracking-tight leading-none uppercase">{mod}</span>
                </div>

                <span className="text-sm font-mono font-bold bg-white text-slate-600 border px-2.5 py-0.5 rounded-full shadow-inner">
                  {selectedInModule.length} / {filtered.length} selected
                </span>
              </div>

              {isExpanded && (
                <div className="p-1.5 bg-white space-y-1.5">
                  {groupDefs.map((group) => {
                    const groupItems = filtered.filter((p) => group.codes.includes(p.code));
                    if (groupItems.length === 0) return null;

                    const groupKey = `${mod}.${group.key}`;
                    const isGroupExpanded = expandedGroups[groupKey] !== false;
                    const selectedInGroup = groupItems.filter((p) => selectedIds.has(p.permissionId));
                    const isGroupAllSelected = selectedInGroup.length === groupItems.length;

                    return (
                      <div key={group.key} className="border border-slate-100 rounded-lg overflow-hidden bg-slate-50/40">
                        <div className="p-2.5 flex items-center justify-between gap-3">
                          <div className="flex items-center gap-2.5">
                            <button
                              type="button"
                              onClick={() => toggleGroupExpand(groupKey)}
                              className="p-0.5 rounded hover:bg-slate-200/60 text-slate-400 transition-colors"
                            >
                              {isGroupExpanded ? <ChevronUp className="w-3.5 h-3.5" /> : <ChevronDown className="w-3.5 h-3.5" />}
                            </button>
                            <input
                              type="checkbox"
                              checked={isGroupAllSelected}
                              onChange={() => onToggleModuleAll(groupItems.map((p) => p.permissionId))}
                              className="rounded border-slate-300 text-brand-red focus:ring-brand-orange h-3.5 w-3.5 cursor-pointer"
                            />
                            <span className="text-sm font-bold text-slate-700">{group.label}</span>
                          </div>
                          <span className="text-[9px] font-mono font-bold bg-white text-slate-500 border border-slate-200 px-2 py-0.5 rounded-full">
                            {selectedInGroup.length}/{groupItems.length}
                          </span>
                        </div>

                        {isGroupExpanded && (
                          <div className="divide-y divide-slate-100 pl-7 pr-1.5 pb-1.5">
                            {groupItems.map((p) => (
                              <PermissionRow key={p.permissionId} item={p} checked={selectedIds.has(p.permissionId)} onToggle={onToggle} />
                            ))}
                          </div>
                        )}
                      </div>
                    );
                  })}

                  {standaloneItems.length > 0 && (
                    <div className="divide-y divide-slate-100">
                      {standaloneItems.map((p) => (
                        <PermissionRow key={p.permissionId} item={p} checked={selectedIds.has(p.permissionId)} onToggle={onToggle} />
                      ))}
                    </div>
                  )}
                </div>
              )}
            </div>
          );
        })}
    </div>
  );
}

function PermissionRow({ item, checked, onToggle }: { item: ChecklistItem; checked: boolean; onToggle: (permissionId: number) => void }) {
  return (
    <label
      className={`flex items-start gap-3.5 p-3.5 rounded-lg transition-colors cursor-pointer ${
        checked ? "bg-orange-50/40 hover:bg-orange-50/60 text-slate-900" : "hover:bg-slate-50/50 text-slate-700"
      }`}
    >
      <input
        type="checkbox"
        checked={checked}
        onChange={() => onToggle(item.permissionId)}
        className="mt-0.5 rounded border-slate-300 text-brand-red focus:ring-brand-orange h-4 w-4 cursor-pointer shrink-0"
      />
      <div className="flex-1 space-y-1">
        <div className="flex flex-wrap items-center gap-2">
          <span className="text-sm font-bold tracking-tight text-slate-900 block leading-tight">{item.name}</span>
          <code className="text-[9px] font-mono font-bold text-brand-red bg-orange-50/60 border border-orange-100 px-1.5 py-0.2 rounded shrink-0">
            {item.code}
          </code>
        </div>
      </div>
    </label>
  );
}
