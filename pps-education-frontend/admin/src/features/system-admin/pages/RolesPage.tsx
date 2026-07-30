import React, { useEffect, useState } from "react";
import { Shield } from "lucide-react";
import { useApp } from "@/context/AppContext";
import { ApiError } from "@/lib/apiClient";
import { deleteRole, listRoles, RoleResponse } from "../api";
import RoleListPanel from "../components/RoleListPanel";
import RoleDetailPanel, { RoleDetailTab } from "../components/RoleDetailPanel";
import CreateRolePanel from "../components/CreateRolePanel";
import { useToast } from "@/lib/useToast";
import Toast from "@/components/ui/Toast";
import Modal from "@/components/ui/Modal";

export default function RolesPage() {
  const { hasPermission } = useApp();
  // user.role.manage đã bị tách nhỏ thành user.role.assign/revoke/view (hạt nhân hóa) — mã gộp cũ
  // không còn tồn tại trong bảng permissions, gate theo mã đó khiến nút "Gán thành viên"/"Gỡ bỏ"
  // không bao giờ hiện với bất kỳ ai (xem UserRoleController: 2 endpoint dùng 2 mã khác nhau).
  const canAssignMembers = hasPermission("user.role.assign");
  const canRevokeMembers = hasPermission("user.role.revoke");

  const [roles, setRoles] = useState<RoleResponse[]>([]);
  const [loading, setLoading] = useState(false);
  const [listError, setListError] = useState<string | null>(null);
  const [selectedRoleId, setSelectedRoleId] = useState<number | null>(null);
  const [rightActiveTab, setRightActiveTab] = useState<RoleDetailTab>("permissions");
  const [roleSearchQuery, setRoleSearchQuery] = useState("");
  const [creatingNew, setCreatingNew] = useState(false);
  const { message: toastMessage, showToast } = useToast();

  const loadRoles = (selectId?: number) => {
    setLoading(true);
    setListError(null);
    listRoles()
      .then((res) => {
        setRoles(res);
        if (selectId != null) {
          setSelectedRoleId(selectId);
        } else if (selectedRoleId == null && res.length > 0) {
          setSelectedRoleId(res[0].id);
        }
      })
      .catch((err) => setListError(err instanceof ApiError ? err.message : "Không tải được danh sách vai trò."))
      .finally(() => setLoading(false));
  };

  useEffect(() => loadRoles(), []);

  const activeRole = roles.find((r) => r.id === selectedRoleId) ?? null;

  const handleDeleteRole = async () => {
    if (!activeRole) return;
    if (!window.confirm(`Bạn có chắc chắn muốn xóa vai trò "${activeRole.name}"?`)) return;
    try {
      await deleteRole(activeRole.id);
      setSelectedRoleId(null);
      loadRoles();
      showToast("Đã xoá vai trò thành công!");
    } catch (err) {
      alert(err instanceof ApiError ? err.message : "Xóa vai trò thất bại.");
    }
  };

  return (
    <div className="space-y-6">
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 border-b border-slate-200 pb-4">
        <div>
          <h1 className="text-xl font-bold text-slate-900 tracking-tight flex items-center gap-2">
            <Shield className="w-6 h-6 text-brand-red" />
            <span>Nhóm vai trò</span>
          </h1>
          <p className="text-xs text-slate-500 mt-1">Cấu hình ma trận quyền theo vai trò và gán/thu hồi vai trò cho tài khoản.</p>
        </div>
      </div>

      {listError && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg">{listError}</div>}

      <div className="flex flex-col md:flex-row border border-slate-200 bg-white rounded-xl shadow-soft overflow-hidden min-h-[620px] animate-in fade-in duration-200">
        <RoleListPanel
          roles={roles}
          loading={loading}
          selectedRoleId={selectedRoleId}
          onSelect={(id) => {
            setSelectedRoleId(id);
            setCreatingNew(false);
            setRightActiveTab("permissions");
          }}
          onAddNew={() => setCreatingNew(true)}
          searchQuery={roleSearchQuery}
          onSearchChange={setRoleSearchQuery}
        />

        <div className="flex-1 flex flex-col bg-white">
          {activeRole ? (
            <RoleDetailPanel
              role={activeRole}
              canAssignMembers={canAssignMembers}
              canRevokeMembers={canRevokeMembers}
              onDelete={handleDeleteRole}
              rightActiveTab={rightActiveTab}
              onTabChange={setRightActiveTab}
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

      <Modal open={creatingNew} onClose={() => setCreatingNew(false)} title="Tạo vai trò tùy chỉnh mới" size="lg">
        <CreateRolePanel
          onCancel={() => setCreatingNew(false)}
          onCreated={(id) => {
            setCreatingNew(false);
            loadRoles(id);
            showToast("Đã tạo vai trò mới thành công!");
          }}
        />
      </Modal>

      <Toast message={toastMessage} />
    </div>
  );
}
