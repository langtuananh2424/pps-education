import React, { useState } from "react";
import { ChevronDown, ChevronUp, Layers, Search } from "lucide-react";
import { Permission } from "@/types";
import { BUSINESS_MODULES, permissionModuleFilterOptions } from "../data";

interface RolePermissionsEditorProps {
  permissions: Permission[];
  editPermissions: string[];
  onToggle: (code: string) => void;
  onToggleModuleAll: (codes: string[]) => void;
  onClearAll: () => void;
  onSelectAll: () => void;
}

export default function RolePermissionsEditor({
  permissions,
  editPermissions,
  onToggle,
  onToggleModuleAll,
  onClearAll,
  onSelectAll
}: RolePermissionsEditorProps) {
  const [searchQuery, setSearchQuery] = useState("");
  const [moduleFilter, setModuleFilter] = useState("ALL");
  const [expandedModules, setExpandedModules] = useState<Record<string, boolean>>(
    Object.fromEntries(BUSINESS_MODULES.map((m) => [m.id, true]))
  );

  const toggleExpand = (moduleId: string) => setExpandedModules((prev) => ({ ...prev, [moduleId]: !prev[moduleId] }));

  return (
    <div className="space-y-6">
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
          {permissionModuleFilterOptions.map((opt) => (
            <option key={opt.value} value={opt.value}>
              {opt.label}
            </option>
          ))}
        </select>
      </div>

      <div className="bg-orange-50/60 border border-orange-100 rounded-xl p-3.5 flex items-center justify-between text-xs text-slate-800 shadow-sm">
        <div className="flex items-center gap-2">
          <Layers className="w-4.5 h-4.5 text-brand-red shrink-0" />
          <span>
            Đã kích hoạt <strong>{editPermissions.length} / {permissions.length}</strong> quyền hạt nhân được khai báo trong code cho
            vai trò này.
          </span>
        </div>
        <div className="flex gap-2">
          <button type="button" onClick={onClearAll} className="text-[10px] font-bold text-slate-600 hover:text-slate-900 border px-2 py-1 bg-white rounded-md shadow-sm">
            Xóa hết
          </button>
          <button type="button" onClick={onSelectAll} className="text-[10px] font-bold text-brand-red hover:text-brand-red/80 border border-orange-200 px-2 py-1 bg-white rounded-md shadow-sm">
            Toàn quyền (Admin)
          </button>
        </div>
      </div>

      <div className="space-y-4">
        {BUSINESS_MODULES.filter((mod) => moduleFilter === "ALL" || mod.id === moduleFilter).map((moduleItem) => {
          const modulePermObjects = permissions.filter((p) => moduleItem.permissions.includes(p.code));
          const filteredObjects = modulePermObjects.filter(
            (p) =>
              p.name.toLowerCase().includes(searchQuery.toLowerCase()) ||
              p.code.toLowerCase().includes(searchQuery.toLowerCase()) ||
              p.description.toLowerCase().includes(searchQuery.toLowerCase())
          );

          if (filteredObjects.length === 0) return null;

          const isExpanded = expandedModules[moduleItem.id] !== false;
          const selectedInModule = filteredObjects.filter((p) => editPermissions.includes(p.code));
          const isAllSelected = selectedInModule.length === filteredObjects.length;

          return (
            <div key={moduleItem.id} className="border border-slate-200 rounded-xl overflow-hidden shadow-sm bg-white">
              <div className="bg-slate-50/70 p-3.5 border-b border-slate-200 flex items-center justify-between gap-3">
                <div className="flex items-center gap-3">
                  <button type="button" onClick={() => toggleExpand(moduleItem.id)} className="p-1 rounded hover:bg-slate-200/50 text-slate-500 transition-colors">
                    {isExpanded ? <ChevronUp className="w-4 h-4" /> : <ChevronDown className="w-4 h-4" />}
                  </button>

                  <input
                    type="checkbox"
                    checked={isAllSelected && filteredObjects.length > 0}
                    onChange={() => onToggleModuleAll(filteredObjects.map((p) => p.code))}
                    className="rounded border-slate-300 text-brand-red focus:ring-brand-orange h-4 w-4 cursor-pointer"
                  />

                  <span className="text-xs font-bold text-slate-800 font-sans tracking-tight leading-none">{moduleItem.name}</span>
                </div>

                <span className="text-[10px] font-mono font-bold bg-white text-slate-600 border px-2.5 py-0.5 rounded-full shadow-inner">
                  {selectedInModule.length} / {filteredObjects.length} selected
                </span>
              </div>

              {isExpanded && (
                <div className="divide-y divide-slate-100 p-1.5 bg-white">
                  {filteredObjects.map((permObj) => {
                    const isChecked = editPermissions.includes(permObj.code);
                    return (
                      <label
                        key={permObj.id}
                        className={`flex items-start gap-3.5 p-3.5 rounded-lg transition-colors cursor-pointer ${
                          isChecked ? "bg-orange-50/40 hover:bg-orange-50/60 text-slate-900" : "hover:bg-slate-50/50 text-slate-700"
                        }`}
                      >
                        <input
                          type="checkbox"
                          checked={isChecked}
                          onChange={() => onToggle(permObj.code)}
                          className="mt-0.5 rounded border-slate-300 text-brand-red focus:ring-brand-orange h-4 w-4 cursor-pointer shrink-0"
                        />
                        <div className="flex-1 space-y-1">
                          <div className="flex flex-wrap items-center gap-2">
                            <span className="text-xs font-bold tracking-tight text-slate-900 block leading-tight">{permObj.name}</span>
                            <code className="text-[9px] font-mono font-bold text-brand-red bg-orange-50/60 border border-orange-100 px-1.5 py-0.2 rounded shrink-0">
                              {permObj.code}
                            </code>
                          </div>
                          <p className="text-[11px] text-slate-500 leading-normal">{permObj.description}</p>
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
