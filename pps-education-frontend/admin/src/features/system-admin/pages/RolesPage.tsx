import React, { useEffect, useState } from "react";
import { Shield } from "lucide-react";
import { Employee, PermissionAuditLog } from "@/types";
import { mockAuditLogs, mockEmployees, mockPermissions, mockRolePermissions } from "@/data/mockData";
import { PermissionGroup } from "../types";
import { initialGroupMembers, initialGroups, initialRoleGroups } from "../data";
import RoleListPanel from "../components/RoleListPanel";
import RoleDetailPanel, { RoleDetailTab } from "../components/RoleDetailPanel";

function syncRolePermissionsFromGroups(updatedRoleGroups: Record<string, string[]>, currentGroups: PermissionGroup[]) {
  const updatedRolePerms = Object.entries(updatedRoleGroups).map(([role, gIds]) => {
    const activeGroups = currentGroups.filter((g) => gIds.includes(g.id) && g.status === "active");
    const activeCodes = Array.from(new Set(activeGroups.flatMap((g) => g.permissions)));
    return { role, permissions: activeCodes };
  });

  mockRolePermissions.length = 0;
  updatedRolePerms.forEach((rp) => mockRolePermissions.push(rp));
}

export default function RolesPage() {
  const [groups, setGroups] = useState<PermissionGroup[]>(initialGroups);
  const [employees] = useState<Employee[]>(mockEmployees);
  const [auditLogs, setAuditLogs] = useState<PermissionAuditLog[]>(mockAuditLogs);
  const [roleGroups, setRoleGroups] = useState<Record<string, string[]>>(initialRoleGroups);
  const [groupMembers, setGroupMembers] = useState<Record<string, string[]>>(initialGroupMembers);

  const [selectedGroupId, setSelectedGroupId] = useState("GRP-01");
  const [rightActiveTab, setRightActiveTab] = useState<RoleDetailTab>("permissions");
  const [roleSearchQuery, setRoleSearchQuery] = useState("");

  const activeGroup = groups.find((g) => g.id === selectedGroupId);
  const isNew = selectedGroupId === "new";

  const [editName, setEditName] = useState("");
  const [editDesc, setEditDesc] = useState("");
  const [editStatus, setEditStatus] = useState<"active" | "inactive">("active");
  const [editPermissions, setEditPermissions] = useState<string[]>([]);

  useEffect(() => {
    if (activeGroup) {
      setEditName(activeGroup.name);
      setEditDesc(activeGroup.description);
      setEditStatus(activeGroup.status);
      setEditPermissions([...activeGroup.permissions]);
    } else if (isNew) {
      setEditName("");
      setEditDesc("");
      setEditStatus("active");
      setEditPermissions([]);
    }
  }, [selectedGroupId, activeGroup, isNew]);

  const pushAuditLog = (entry: Omit<PermissionAuditLog, "id" | "createdAt">) => {
    const log: PermissionAuditLog = {
      id: `LOG-00${auditLogs.length + 1}`,
      createdAt: new Date().toISOString().replace("T", " ").substring(0, 16),
      ...entry
    };
    setAuditLogs((prev) => [log, ...prev]);
  };

  const handleDeleteRole = () => {
    if (!activeGroup) return;
    if (!window.confirm(`Bạn có chắc chắn muốn loại bỏ vai trò "${activeGroup.name}"? Hành động này sẽ thu hồi quyền hạn của toàn bộ thành viên gán kèm.`)) return;

    const updatedGroups = groups.filter((g) => g.id !== selectedGroupId);
    setGroups(updatedGroups);

    const updatedRoleGroups = { ...roleGroups };
    Object.keys(updatedRoleGroups).forEach((role) => {
      updatedRoleGroups[role] = updatedRoleGroups[role].filter((gId) => gId !== selectedGroupId);
    });
    setRoleGroups(updatedRoleGroups);

    pushAuditLog({
      actorName: "Lăng Tuấn Anh (SYS_ADMIN)",
      targetName: activeGroup.name,
      action: "ROLE_REVOKED",
      details: `Xóa vai trò "${activeGroup.name}" (${activeGroup.code}) khỏi cấu hình phân quyền hệ thống.`
    });

    syncRolePermissionsFromGroups(updatedRoleGroups, updatedGroups);
    setSelectedGroupId(updatedGroups[0]?.id || "");
  };

  const handleSaveRoleChanges = () => {
    if (!editName.trim()) {
      alert("Tên vai trò không được để trống.");
      return;
    }
    if (!editDesc.trim()) {
      alert("Mô tả vai trò không được để trống.");
      return;
    }

    if (isNew) {
      const newId = `GRP-${Date.now()}`;
      const newCode = `GRP-CUSTOM-${Date.now().toString().slice(-4)}`;
      const newGroup: PermissionGroup = {
        id: newId,
        code: newCode,
        name: editName.trim(),
        module: "SYS",
        description: editDesc.trim(),
        permissions: editPermissions,
        status: editStatus,
        createdBy: "Lăng Tuấn Anh",
        updatedAt: new Date().toISOString().substring(0, 10)
      };

      const updatedGroups = [...groups, newGroup];
      setGroups(updatedGroups);
      setGroupMembers((prev) => ({ ...prev, [newId]: [] }));

      const updatedRoleGroups = { ...roleGroups, [newCode]: [newId] };
      setRoleGroups(updatedRoleGroups);

      pushAuditLog({
        actorName: "Lăng Tuấn Anh (SYS_ADMIN)",
        targetName: editName.trim(),
        action: "PERM_OVERRIDE_ADDED",
        details: `Khởi tạo vai trò mới "${editName.trim()}" với ${editPermissions.length} quyền hạt nhân.`
      });

      syncRolePermissionsFromGroups(updatedRoleGroups, updatedGroups);
      setSelectedGroupId(newId);
      alert(`Khởi tạo thành công vai trò "${editName.trim()}"!`);
    } else {
      const updatedGroups = groups.map((g) =>
        g.id === selectedGroupId
          ? { ...g, name: editName.trim(), description: editDesc.trim(), status: editStatus, permissions: editPermissions, updatedAt: new Date().toISOString().substring(0, 10) }
          : g
      );
      setGroups(updatedGroups);

      pushAuditLog({
        actorName: "Lăng Tuấn Anh (SYS_ADMIN)",
        targetName: editName.trim(),
        action: "PERM_OVERRIDE_ADDED",
        details: `Cập nhật cấu hình vai trò "${editName.trim()}" (${editPermissions.length} quyền hoạt vụ). Trạng thái: ${editStatus}.`
      });

      syncRolePermissionsFromGroups(roleGroups, updatedGroups);
      alert(`Đã lưu thay đổi cấu hình cho "${editName.trim()}"!`);
    }
  };

  const handleAssignMember = (employeeId: string) => {
    if (!employeeId) return;
    const emp = employees.find((e) => e.id === employeeId);
    if (!emp) return;

    const currentMembers = groupMembers[selectedGroupId] || [];
    if (currentMembers.includes(employeeId)) {
      alert("Nhân viên này đã là thành viên của vai trò này.");
      return;
    }

    setGroupMembers((prev) => ({ ...prev, [selectedGroupId]: [...currentMembers, employeeId] }));
    pushAuditLog({
      actorName: "Lăng Tuấn Anh (SYS_ADMIN)",
      targetName: emp.fullName,
      action: "ROLE_GRANTED",
      details: `Gán thành viên "${emp.fullName}" (${emp.email}) vào vai trò "${activeGroup?.name}".`
    });
  };

  const handleRemoveMember = (employeeId: string) => {
    const emp = employees.find((e) => e.id === employeeId);
    if (!emp) return;
    if (!window.confirm(`Bạn có chắc muốn gỡ "${emp.fullName}" khỏi vai trò này?`)) return;

    setGroupMembers((prev) => ({ ...prev, [selectedGroupId]: (prev[selectedGroupId] || []).filter((id) => id !== employeeId) }));
    pushAuditLog({
      actorName: "Lăng Tuấn Anh (SYS_ADMIN)",
      targetName: emp.fullName,
      action: "ROLE_REVOKED",
      details: `Gỡ thành viên "${emp.fullName}" khỏi vai trò "${activeGroup?.name}".`
    });
  };

  const handleTogglePermission = (code: string) => {
    setEditPermissions((prev) => (prev.includes(code) ? prev.filter((c) => c !== code) : [...prev, code]));
  };

  const handleToggleModuleAll = (codes: string[]) => {
    const hasAll = codes.every((code) => editPermissions.includes(code));
    setEditPermissions((prev) => (hasAll ? prev.filter((code) => !codes.includes(code)) : [...prev, ...codes.filter((c) => !prev.includes(c))]));
  };

  const roleAuditLogs = auditLogs.filter((log) => {
    if (!activeGroup) return false;
    const lowerDetails = log.details.toLowerCase();
    const lowerTarget = log.targetName.toLowerCase();
    const roleName = activeGroup.name.toLowerCase();
    return lowerDetails.includes(roleName) || lowerTarget.includes(roleName) || lowerDetails.includes(activeGroup.id.toLowerCase());
  });

  const memberIds = groupMembers[selectedGroupId] || [];
  const memberEmployees = employees.filter((e) => memberIds.includes(e.id));

  return (
    <div className="space-y-6">
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 border-b border-slate-200 pb-4">
        <div>
          <h1 className="text-xl font-bold text-slate-900 tracking-tight flex items-center gap-2">
            <Shield className="w-6 h-6 text-brand-red" />
            <span>Phân hệ Quản trị & Bảo mật Hệ thống</span>
          </h1>
          <p className="text-xs text-slate-500 mt-1">Thiết lập ma trận phân quyền, quản trị vai trò, ủy quyền ngoại lệ và truy vết hoạt động bảo mật.</p>
        </div>
      </div>

      <div className="flex flex-col md:flex-row border border-slate-200 bg-white rounded-xl shadow-soft overflow-hidden min-h-[620px] animate-in fade-in duration-200">
        <RoleListPanel
          groups={groups}
          groupMembers={groupMembers}
          selectedGroupId={selectedGroupId}
          onSelect={setSelectedGroupId}
          onAddNew={() => {
            setSelectedGroupId("new");
            setRightActiveTab("permissions");
          }}
          searchQuery={roleSearchQuery}
          onSearchChange={setRoleSearchQuery}
        />

        <div className="flex-1 flex flex-col bg-white">
          {isNew || activeGroup ? (
            <RoleDetailPanel
              isNew={isNew}
              activeGroup={activeGroup}
              editName={editName}
              editDesc={editDesc}
              editStatus={editStatus}
              editPermissions={editPermissions}
              onNameChange={setEditName}
              onDescChange={setEditDesc}
              onToggleStatus={() => setEditStatus((s) => (s === "active" ? "inactive" : "active"))}
              onDelete={handleDeleteRole}
              onSave={handleSaveRoleChanges}
              rightActiveTab={rightActiveTab}
              onTabChange={setRightActiveTab}
              permissions={mockPermissions}
              onTogglePermission={handleTogglePermission}
              onToggleModuleAll={handleToggleModuleAll}
              onClearAllPermissions={() => setEditPermissions([])}
              onSelectAllPermissions={() => setEditPermissions(mockPermissions.map((p) => p.code))}
              memberEmployees={memberEmployees}
              allEmployees={employees}
              memberIds={memberIds}
              onAssignMember={handleAssignMember}
              onRemoveMember={handleRemoveMember}
              roleAuditLogs={roleAuditLogs}
            />
          ) : (
            <div className="flex-1 flex flex-col items-center justify-center p-12 text-center text-slate-400 bg-slate-50/10 space-y-3">
              <Shield className="w-12 h-12 text-slate-300" />
              <div>
                <h3 className="text-sm font-bold text-slate-700">Vui lòng chọn một vai trò</h3>
                <p className="text-xs text-slate-400 mt-1 max-w-sm mx-auto leading-relaxed">
                  Chọn một vai trò từ danh mục bên trái hoặc bấm tạo mới để thiết lập cấu hình phân quyền và thành viên liên quan.
                </p>
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
