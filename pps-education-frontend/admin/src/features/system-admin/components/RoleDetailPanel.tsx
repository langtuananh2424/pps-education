import React from "react";
import { Clock, Power, Save, Shield, Trash2, Users } from "lucide-react";
import { Employee, Permission, PermissionAuditLog } from "@/types";
import { PermissionGroup } from "../types";
import RolePermissionsEditor from "./RolePermissionsEditor";
import RoleMembersPanel from "./RoleMembersPanel";
import RoleAuditTimeline from "./RoleAuditTimeline";

export type RoleDetailTab = "permissions" | "members" | "audit";

interface RoleDetailPanelProps {
  isNew: boolean;
  activeGroup?: PermissionGroup;
  editName: string;
  editDesc: string;
  editStatus: "active" | "inactive";
  editPermissions: string[];
  onNameChange: (v: string) => void;
  onDescChange: (v: string) => void;
  onToggleStatus: () => void;
  onDelete: () => void;
  onSave: () => void;
  rightActiveTab: RoleDetailTab;
  onTabChange: (tab: RoleDetailTab) => void;
  permissions: Permission[];
  onTogglePermission: (code: string) => void;
  onToggleModuleAll: (codes: string[]) => void;
  onClearAllPermissions: () => void;
  onSelectAllPermissions: () => void;
  memberEmployees: Employee[];
  allEmployees: Employee[];
  memberIds: string[];
  onAssignMember: (employeeId: string) => void;
  onRemoveMember: (employeeId: string) => void;
  roleAuditLogs: PermissionAuditLog[];
}

export default function RoleDetailPanel({
  isNew,
  activeGroup,
  editName,
  editDesc,
  editStatus,
  editPermissions,
  onNameChange,
  onDescChange,
  onToggleStatus,
  onDelete,
  onSave,
  rightActiveTab,
  onTabChange,
  permissions,
  onTogglePermission,
  onToggleModuleAll,
  onClearAllPermissions,
  onSelectAllPermissions,
  memberEmployees,
  allEmployees,
  memberIds,
  onAssignMember,
  onRemoveMember,
  roleAuditLogs
}: RoleDetailPanelProps) {
  return (
    <div className="flex-1 flex flex-col h-full">
      <div className="p-6 border-b border-slate-200 space-y-4 bg-slate-50/20">
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 border-b border-slate-200/60 pb-4">
          <div>
            <span className="text-[10px] font-mono font-bold uppercase tracking-wider text-brand-red bg-orange-50 border border-orange-100 px-2 py-0.5 rounded-md">
              {isNew ? "NEW_ROLE" : activeGroup?.code || "ROLE"}
            </span>
            <h2 className="text-sm font-bold text-slate-800 mt-1">{isNew ? "Khởi tạo vai trò mới" : "Cấu hình chức năng & Vai trò"}</h2>
          </div>

          <div className="flex flex-wrap items-center gap-3">
            <div className="flex items-center gap-2 bg-white border border-slate-200 rounded-lg px-2.5 py-1 shadow-sm">
              <span className="text-[10px] font-bold text-slate-400 uppercase tracking-wider">Trạng thái:</span>
              <button
                type="button"
                onClick={onToggleStatus}
                className={`px-2 py-0.5 text-[10px] font-bold rounded-md flex items-center gap-1 transition-all ${
                  editStatus === "active" ? "bg-emerald-50 text-emerald-600 border border-emerald-100" : "bg-slate-100 text-slate-500 border border-slate-200"
                }`}
              >
                <Power className="w-2.5 h-2.5" />
                <span>{editStatus === "active" ? "Active" : "Inactive"}</span>
              </button>
            </div>

            {!isNew && (
              <button
                onClick={onDelete}
                className="px-3 py-1.5 rounded-lg border border-rose-200 bg-rose-50/40 hover:bg-rose-50 text-rose-600 text-xs font-semibold flex items-center gap-1.5 transition-all shadow-sm cursor-pointer"
                title="Xóa vai trò này vĩnh viễn"
              >
                <Trash2 className="w-3.5 h-3.5" />
                <span>Xóa vai trò</span>
              </button>
            )}

            <button
              onClick={onSave}
              className="bg-brand-gradient hover:opacity-90 text-white px-3.5 py-1.5 rounded-lg text-xs font-bold flex items-center gap-1.5 transition-all shadow-md cursor-pointer"
            >
              <Save className="w-3.5 h-3.5" />
              <span>{isNew ? "Khởi tạo" : "Lưu thay đổi"}</span>
            </button>
          </div>
        </div>

        <div className="space-y-3.5">
          <div className="space-y-1">
            <label className="text-[10px] font-bold text-slate-400 uppercase tracking-wider block">Tên vai trò (Role Name)</label>
            <input
              type="text"
              value={editName}
              onChange={(e) => onNameChange(e.target.value)}
              placeholder="Ví dụ: Trưởng nhóm Đào tạo"
              className="w-full bg-white border border-slate-200 text-sm px-3.5 py-2.5 rounded-lg font-bold focus:outline-none focus:ring-1 focus:ring-brand-orange text-slate-800 shadow-sm"
            />
          </div>

          <div className="space-y-1">
            <label className="text-[10px] font-bold text-slate-400 uppercase tracking-wider block">Mô tả chức năng nhiệm vụ (Description)</label>
            <textarea
              value={editDesc}
              onChange={(e) => onDescChange(e.target.value)}
              placeholder="Mô tả cụ thể nhiệm vụ của vai trò để nhân viên nắm bắt..."
              rows={2}
              className="w-full bg-white border border-slate-200 text-xs px-3.5 py-2.5 rounded-lg focus:outline-none focus:ring-1 focus:ring-brand-orange text-slate-700 leading-normal shadow-sm"
            />
          </div>
        </div>

        {!isNew && (
          <div className="flex border-b border-slate-200 pt-2 gap-6">
            <button
              onClick={() => onTabChange("permissions")}
              className={`pb-2.5 text-xs font-bold border-b-2 flex items-center gap-1.5 transition-all ${
                rightActiveTab === "permissions" ? "border-brand-red text-brand-red font-bold" : "border-transparent text-slate-500 hover:text-slate-700"
              }`}
            >
              <Shield className="w-4 h-4" />
              <span>Quyền hạn gán ({editPermissions.length})</span>
            </button>
            <button
              onClick={() => onTabChange("members")}
              className={`pb-2.5 text-xs font-bold border-b-2 flex items-center gap-1.5 transition-all ${
                rightActiveTab === "members" ? "border-brand-red text-brand-red font-bold" : "border-transparent text-slate-500 hover:text-slate-700"
              }`}
            >
              <Users className="w-4 h-4" />
              <span>Thành viên gán ({memberIds.length})</span>
            </button>
            <button
              onClick={() => onTabChange("audit")}
              className={`pb-2.5 text-xs font-bold border-b-2 flex items-center gap-1.5 transition-all ${
                rightActiveTab === "audit" ? "border-brand-red text-brand-red font-bold" : "border-transparent text-slate-500 hover:text-slate-700"
              }`}
            >
              <Clock className="w-4 h-4" />
              <span>Nhật ký bảo mật ({roleAuditLogs.length})</span>
            </button>
          </div>
        )}
      </div>

      <div className="flex-1 p-6 overflow-y-auto max-h-[460px]">
        {(rightActiveTab === "permissions" || isNew) && (
          <RolePermissionsEditor
            permissions={permissions}
            editPermissions={editPermissions}
            onToggle={onTogglePermission}
            onToggleModuleAll={onToggleModuleAll}
            onClearAll={onClearAllPermissions}
            onSelectAll={onSelectAllPermissions}
          />
        )}

        {!isNew && rightActiveTab === "members" && (
          <RoleMembersPanel
            memberEmployees={memberEmployees}
            allEmployees={allEmployees}
            memberIds={memberIds}
            onAssign={onAssignMember}
            onRemove={onRemoveMember}
          />
        )}

        {!isNew && rightActiveTab === "audit" && <RoleAuditTimeline roleName={activeGroup?.name} logs={roleAuditLogs} />}
      </div>
    </div>
  );
}
