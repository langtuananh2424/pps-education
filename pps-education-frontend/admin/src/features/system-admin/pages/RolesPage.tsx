import React, { useEffect, useState } from "react";
import { Shield } from "lucide-react";
import { useApp } from "@/context/AppContext";
import { ApiError } from "@/lib/apiClient";
import { createRole, deleteRole, listRoles, RoleResponse } from "../api";
import RoleListPanel from "../components/RoleListPanel";
import RoleDetailPanel, { RoleDetailTab } from "../components/RoleDetailPanel";
import Modal from "@/components/ui/Modal";
import Button from "@/components/ui/Button";

export default function RolesPage() {
  const { hasPermission } = useApp();
  const canManageMembers = hasPermission("user.role.manage");

  const [roles, setRoles] = useState<RoleResponse[]>([]);
  const [loading, setLoading] = useState(false);
  const [listError, setListError] = useState<string | null>(null);
  const [selectedRoleId, setSelectedRoleId] = useState<number | null>(null);
  const [rightActiveTab, setRightActiveTab] = useState<RoleDetailTab>("permissions");
  const [roleSearchQuery, setRoleSearchQuery] = useState("");
  const [createOpen, setCreateOpen] = useState(false);

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
            <span>Nhóm vai trò (UC-03)</span>
          </h1>
          <p className="text-xs text-slate-500 mt-1">Cấu hình ma trận quyền theo vai trò và gán/thu hồi vai trò cho tài khoản (UC-46).</p>
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
            setRightActiveTab("permissions");
          }}
          onAddNew={() => setCreateOpen(true)}
          searchQuery={roleSearchQuery}
          onSearchChange={setRoleSearchQuery}
        />

        <div className="flex-1 flex flex-col bg-white">
          {activeRole ? (
            <RoleDetailPanel
              role={activeRole}
              canManageMembers={canManageMembers}
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

      <CreateRoleModal
        open={createOpen}
        onClose={() => setCreateOpen(false)}
        onCreated={(id) => {
          setCreateOpen(false);
          loadRoles(id);
        }}
      />
    </div>
  );
}

function CreateRoleModal({ open, onClose, onCreated }: { open: boolean; onClose: () => void; onCreated: (id: number) => void }) {
  const [code, setCode] = useState("");
  const [name, setName] = useState("");
  const [description, setDescription] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (open) {
      setCode("");
      setName("");
      setDescription("");
      setError(null);
    }
  }, [open]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!code.trim() || !name.trim()) {
      setError("Vui lòng điền mã vai trò và tên vai trò.");
      return;
    }
    setSubmitting(true);
    setError(null);
    try {
      const role = await createRole({ code: code.trim(), name: name.trim(), description: description.trim() || undefined });
      onCreated(role.id);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Tạo vai trò thất bại.");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Modal open={open} onClose={onClose} title="Tạo vai trò tùy chỉnh mới (UC-03)">
      <form onSubmit={handleSubmit} className="space-y-4">
        {error && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg">{error}</div>}
        <div>
          <label className="text-[10px] uppercase font-bold text-slate-500 block mb-1">Mã vai trò *</label>
          <input
            value={code}
            onChange={(e) => setCode(e.target.value.toUpperCase())}
            placeholder="VD: MARKETING_LEAD"
            className="w-full bg-slate-50 border border-slate-200 text-xs p-2.5 rounded-lg focus:outline-none font-mono"
            required
          />
        </div>
        <div>
          <label className="text-[10px] uppercase font-bold text-slate-500 block mb-1">Tên vai trò *</label>
          <input
            value={name}
            onChange={(e) => setName(e.target.value)}
            className="w-full bg-slate-50 border border-slate-200 text-xs p-2.5 rounded-lg focus:outline-none"
            required
          />
        </div>
        <div>
          <label className="text-[10px] uppercase font-bold text-slate-500 block mb-1">Mô tả</label>
          <textarea
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            rows={3}
            className="w-full bg-slate-50 border border-slate-200 text-xs p-2.5 rounded-lg focus:outline-none"
          />
        </div>
        <p className="text-[10px] text-slate-400">Sau khi tạo, vào tab "Quyền hạn gán" để cấu hình ma trận quyền cho vai trò này.</p>
        <div className="flex justify-end gap-2 pt-2">
          <Button type="button" variant="secondary" onClick={onClose}>
            Hủy
          </Button>
          <Button type="submit" variant="primary" disabled={submitting}>
            {submitting ? "Đang tạo..." : "Tạo vai trò"}
          </Button>
        </div>
      </form>
    </Modal>
  );
}
