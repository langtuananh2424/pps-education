import React, { useState } from "react";
import { Info } from "lucide-react";
import { PermissionAuditLog, UserOverride } from "@/types";
import { mockAuditLogs, mockEmployees, mockPermissions, mockUserOverrides } from "@/data/mockData";
import CreateOverrideForm from "../components/CreateOverrideForm";
import OverridesTable from "../components/OverridesTable";

export default function OverridesPage() {
  const [overrides, setOverrides] = useState<UserOverride[]>(mockUserOverrides);
  const [, setAuditLogs] = useState<PermissionAuditLog[]>(mockAuditLogs);

  const handleCreate = (input: { userId: string; permissionId: string; type: "GRANT" | "REVOKE"; reason: string; expiresAt: string }) => {
    const empObj = mockEmployees.find((e) => e.id === input.userId);
    const permObj = mockPermissions.find((p) => p.id === input.permissionId);
    if (!empObj || !permObj) return;

    const newOverride: UserOverride = {
      userId: input.userId,
      permissionId: input.permissionId,
      type: input.type,
      reason: input.reason,
      expiresAt: input.expiresAt || undefined
    };
    setOverrides((prev) => [newOverride, ...prev]);

    setAuditLogs((prev) => [
      {
        id: `LOG-00${prev.length + 1}`,
        actorName: "Lăng Tuấn Anh (SYS_ADMIN)",
        targetName: empObj.fullName,
        action: "PERM_OVERRIDE_ADDED",
        details: `${input.type === "GRANT" ? "Cấp thêm quyền ngoại lệ" : "Tước bỏ quyền ngoại lệ"} "${permObj.code}" (${permObj.name}). Thời hạn: ${
          input.expiresAt || "Vô thời hạn"
        }. Lý do: ${input.reason}`,
        createdAt: new Date().toISOString().replace("T", " ").substring(0, 16)
      },
      ...prev
    ]);

    alert(`Đã lưu thiết lập ngoại lệ tài khoản cho "${empObj.fullName}" thành công!`);
  };

  const handleRemove = (index: number) => {
    setOverrides((prev) => prev.filter((_, i) => i !== index));
  };

  return (
    <div className="space-y-6 animate-in fade-in duration-200">
      <div className="bg-gradient-to-r from-orange-50 to-amber-50/50 border border-orange-200/60 p-4 rounded-xl flex items-start gap-3 shadow-sm">
        <Info className="w-5 h-5 text-brand-red shrink-0 mt-0.5" />
        <div className="space-y-1">
          <span className="text-xs font-bold text-brand-red uppercase tracking-wider block">CHẾ ĐỘ NGOẠI LỆ TÀI KHOẢN (USER OVERRIDES - UC-04)</span>
          <p className="text-xs text-slate-600 leading-relaxed">
            Cho phép Quản trị viên cấp thêm hoặc tước bỏ trực tiếp một số quyền hạt nhân nhất định đối với từng tài khoản nhân sự độc lập
            trong một khoảng thời gian nhất định (ủy quyền hỗ trợ, tăng cường kiểm soát an ninh) mà không cần thay đổi vai trò vĩnh viễn
            của họ.
          </p>
        </div>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-4 gap-6">
        <CreateOverrideForm employees={mockEmployees} permissions={mockPermissions} onSubmit={handleCreate} />
        <OverridesTable overrides={overrides} employees={mockEmployees} permissions={mockPermissions} onRemove={handleRemove} />
      </div>
    </div>
  );
}
