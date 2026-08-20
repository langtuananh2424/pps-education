import React from "react";
import { Plus, Search, Shield } from "lucide-react";
import { useTranslation } from "react-i18next";
import { RoleResponse } from "../api";
import { cn } from "@/lib/cn";
import Badge from "@/components/ui/Badge";

interface RoleListPanelProps {
  roles: RoleResponse[];
  loading: boolean;
  selectedRoleId: number | null;
  onSelect: (id: number) => void;
  onAddNew: () => void;
  searchQuery: string;
  onSearchChange: (value: string) => void;
}

export default function RoleListPanel({ roles, loading, selectedRoleId, onSelect, onAddNew, searchQuery, onSearchChange }: RoleListPanelProps) {
  const { t } = useTranslation("system-admin-roles");
  const filtered = roles.filter(
    (r) =>
      r.name.toLowerCase().includes(searchQuery.toLowerCase()) ||
      r.code.toLowerCase().includes(searchQuery.toLowerCase()) ||
      (r.description ?? "").toLowerCase().includes(searchQuery.toLowerCase())
  );

  return (
    <div className="w-full md:w-80 lg:w-96 border-r border-slate-200 flex flex-col bg-slate-50/50">
      <div className="p-4 border-b border-slate-200 space-y-3 bg-white">
        <div className="flex items-center justify-between">
          <span className="text-xs font-bold text-slate-800 uppercase tracking-wide">{t("roleListPanel.title", { count: roles.length })}</span>
          <button
            onClick={onAddNew}
            className="bg-brand-gradient hover:opacity-90 text-white p-1.5 rounded-lg flex items-center gap-1 text-[11px] font-semibold transition-all shadow-sm"
            title={t("roleListPanel.addButtonTitle")}
          >
            <Plus className="w-4 h-4" />
            <span>{t("roleListPanel.addButton")}</span>
          </button>
        </div>

        <div className="relative">
          <Search className="absolute left-3 top-2.5 w-4 h-4 text-slate-400" />
          <input
            type="text"
            placeholder={t("roleListPanel.searchPlaceholder")}
            value={searchQuery}
            onChange={(e) => onSearchChange(e.target.value)}
            className="w-full bg-slate-50 border border-slate-200 text-xs pl-9 pr-3 py-2 rounded-lg focus:outline-none focus:ring-1 focus:ring-brand-orange text-slate-800 font-sans"
          />
        </div>
      </div>

      <div className="flex-1 overflow-y-auto divide-y divide-slate-150 p-2 space-y-1 bg-slate-50/30 max-h-[580px]">
        {loading ? (
          <div className="py-12 text-center text-slate-400 text-xs">{t("roleListPanel.loading")}</div>
        ) : filtered.length === 0 ? (
          <div className="py-12 text-center text-slate-400 text-xs italic">{t("roleListPanel.empty")}</div>
        ) : (
          filtered.map((role) => {
            const isSelected = selectedRoleId === role.id;

            return (
              <button
                key={role.id}
                onClick={() => onSelect(role.id)}
                className={cn(
                  "w-full text-left p-3.5 rounded-xl border transition-all duration-150 flex flex-col gap-1.5",
                  isSelected
                    ? "bg-orange-50/80 border-brand-orange shadow-sm text-slate-900 ring-1 ring-brand-orange"
                    : "bg-white border-slate-200 hover:bg-slate-50/75 text-slate-700"
                )}
              >
                <div className="flex items-center justify-between w-full">
                  <span className={cn("text-xs font-bold leading-tight", isSelected ? "text-brand-red" : "text-slate-900")}>
                    {role.name}
                  </span>
                  <Badge variant={role.isSystem ? "info" : "neutral"}>{role.isSystem ? t("roleListPanel.badgeSystem") : t("roleListPanel.badgeCustom")}</Badge>
                </div>

                {role.description && <p className="text-[10px] text-slate-400 line-clamp-2 leading-normal">{role.description}</p>}

                <div className="flex items-center gap-3 pt-1 text-[10px] text-slate-500 font-medium">
                  <span className="flex items-center gap-1">
                    <Shield className="w-3 h-3 text-slate-400" />
                    <code className="font-mono">{role.code}</code>
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
