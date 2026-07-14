import React from "react";
import { Plus, Search, Shield, Users } from "lucide-react";
import { PermissionGroup } from "../types";
import { cn } from "@/lib/cn";
import Badge from "@/components/ui/Badge";

interface RoleListPanelProps {
  groups: PermissionGroup[];
  groupMembers: Record<string, string[]>;
  selectedGroupId: string;
  onSelect: (id: string) => void;
  onAddNew: () => void;
  searchQuery: string;
  onSearchChange: (value: string) => void;
}

export default function RoleListPanel({ groups, groupMembers, selectedGroupId, onSelect, onAddNew, searchQuery, onSearchChange }: RoleListPanelProps) {
  const filtered = groups.filter(
    (g) =>
      g.name.toLowerCase().includes(searchQuery.toLowerCase()) ||
      g.description.toLowerCase().includes(searchQuery.toLowerCase()) ||
      g.code.toLowerCase().includes(searchQuery.toLowerCase())
  );

  return (
    <div className="w-full md:w-80 lg:w-96 border-r border-slate-200 flex flex-col bg-slate-50/50">
      <div className="p-4 border-b border-slate-200 space-y-3 bg-white">
        <div className="flex items-center justify-between">
          <span className="text-xs font-bold text-slate-800 uppercase tracking-wide">Danh sách Vai trò ({groups.length})</span>
          <button
            onClick={onAddNew}
            className="bg-brand-gradient hover:opacity-90 text-white p-1.5 rounded-lg flex items-center gap-1 text-[11px] font-semibold transition-all shadow-sm"
            title="Thêm vai trò mới"
          >
            <Plus className="w-4 h-4" />
            <span>Vai trò</span>
          </button>
        </div>

        <div className="relative">
          <Search className="absolute left-3 top-2.5 w-4 h-4 text-slate-400" />
          <input
            type="text"
            placeholder="Tìm kiếm vai trò hệ thống..."
            value={searchQuery}
            onChange={(e) => onSearchChange(e.target.value)}
            className="w-full bg-slate-50 border border-slate-200 text-xs pl-9 pr-3 py-2 rounded-lg focus:outline-none focus:ring-1 focus:ring-brand-orange text-slate-800 font-sans"
          />
        </div>
      </div>

      <div className="flex-1 overflow-y-auto divide-y divide-slate-150 p-2 space-y-1 bg-slate-50/30 max-h-[580px]">
        {filtered.length === 0 ? (
          <div className="py-12 text-center text-slate-400 text-xs italic">Không tìm thấy vai trò phù hợp</div>
        ) : (
          filtered.map((roleItem) => {
            const isSelected = selectedGroupId === roleItem.id;
            const memberCount = groupMembers[roleItem.id]?.length || 0;

            return (
              <button
                key={roleItem.id}
                onClick={() => onSelect(roleItem.id)}
                className={cn(
                  "w-full text-left p-3.5 rounded-xl border transition-all duration-150 flex flex-col gap-1.5",
                  isSelected
                    ? "bg-orange-50/80 border-brand-orange shadow-sm text-slate-900 ring-1 ring-brand-orange"
                    : "bg-white border-slate-200 hover:bg-slate-50/75 text-slate-700"
                )}
              >
                <div className="flex items-center justify-between w-full">
                  <span className={cn("text-xs font-bold leading-tight", isSelected ? "text-brand-red" : "text-slate-900")}>
                    {roleItem.name}
                  </span>
                  <Badge variant={roleItem.status === "active" ? "success" : "neutral"}>
                    {roleItem.status === "active" ? "Active" : "Inactive"}
                  </Badge>
                </div>

                <p className="text-[10px] text-slate-400 line-clamp-2 leading-normal">{roleItem.description}</p>

                <div className="flex items-center gap-3 pt-1 text-[10px] text-slate-500 font-medium">
                  <span className="flex items-center gap-1">
                    <Users className="w-3 h-3 text-slate-400" />
                    <span>{memberCount} nhân sự</span>
                  </span>
                  <span className="flex items-center gap-1">
                    <Shield className="w-3 h-3 text-slate-400" />
                    <span>{roleItem.permissions.length} quyền</span>
                  </span>
                </div>
              </button>
            );
          })
        )}
      </div>
    </div>
  );
}
