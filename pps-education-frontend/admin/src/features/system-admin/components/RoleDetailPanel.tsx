import React from "react";
import { Shield, Trash2, Users } from "lucide-react";
import { RoleResponse } from "../api";
import RolePermissionsEditor from "./RolePermissionsEditor";
import RoleMembersPanel from "./RoleMembersPanel";

export type RoleDetailTab = "permissions" | "members";

interface RoleDetailPanelProps {
  role: RoleResponse;
  canManageMembers: boolean;
  onDelete: () => void;
  rightActiveTab: RoleDetailTab;
  onTabChange: (tab: RoleDetailTab) => void;
}

export default function RoleDetailPanel({ role, canManageMembers, onDelete, rightActiveTab, onTabChange }: RoleDetailPanelProps) {
  return (
    <div className="flex-1 flex flex-col h-full">
      <div className="p-6 border-b border-slate-200 space-y-4 bg-slate-50/20">
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 border-b border-slate-200/60 pb-4">
          <div>
            <span className="text-[10px] font-mono font-bold uppercase tracking-wider text-brand-red bg-orange-50 border border-orange-100 px-2 py-0.5 rounded-md">
              {role.code}
            </span>
            <h2 className="text-sm font-bold text-slate-800 mt-1">{role.name}</h2>
            {role.description && <p className="text-xs text-slate-500 mt-1 leading-relaxed max-w-xl">{role.description}</p>}
          </div>

          {!role.isSystem && (
            <button
              onClick={onDelete}
              className="px-3 py-1.5 rounded-lg border border-rose-200 bg-rose-50/40 hover:bg-rose-50 text-rose-600 text-xs font-semibold flex items-center gap-1.5 transition-all shadow-sm cursor-pointer shrink-0"
              title="Xóa vai trò tùy chỉnh này"
            >
              <Trash2 className="w-3.5 h-3.5" />
              <span>Xóa vai trò</span>
            </button>
          )}
        </div>

        <div className="flex border-b border-slate-200 pt-2 gap-6">
          <button
            onClick={() => onTabChange("permissions")}
            className={`pb-2.5 text-xs font-bold border-b-2 flex items-center gap-1.5 transition-all ${
              rightActiveTab === "permissions" ? "border-brand-red text-brand-red font-bold" : "border-transparent text-slate-500 hover:text-slate-700"
            }`}
          >
            <Shield className="w-4 h-4" />
            <span>Quyền hạn gán</span>
          </button>
          <button
            onClick={() => onTabChange("members")}
            className={`pb-2.5 text-xs font-bold border-b-2 flex items-center gap-1.5 transition-all ${
              rightActiveTab === "members" ? "border-brand-red text-brand-red font-bold" : "border-transparent text-slate-500 hover:text-slate-700"
            }`}
          >
            <Users className="w-4 h-4" />
            <span>Thành viên</span>
          </button>
        </div>
      </div>

      <div className="flex-1 p-6 overflow-y-auto max-h-[460px]">
        {rightActiveTab === "permissions" && <RolePermissionsEditor roleId={role.id} roleName={role.name} />}
        {rightActiveTab === "members" && <RoleMembersPanel roleId={role.id} roleName={role.name} canManage={canManageMembers} />}
      </div>
    </div>
  );
}
