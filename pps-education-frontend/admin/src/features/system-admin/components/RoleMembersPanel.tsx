import React, { useEffect, useState } from "react";
import { UserMinus, UserPlus, Users } from "lucide-react";
import { ApiError } from "@/lib/apiClient";
import { assignUserRole, revokeUserRole, searchUsers, UserListItemResponse } from "../api";
import TableContainer, { Td, Th } from "@/components/ui/TableContainer";
import Badge, { BadgeVariant } from "@/components/ui/Badge";
import AssignUserModal from "./AssignUserModal";
import { useToast } from "@/lib/useToast";
import Toast from "@/components/ui/Toast";
import { useDialog } from "@/components/ui/DialogProvider";

const statusVariants: Record<string, BadgeVariant> = {
  ACTIVE: "success",
  INACTIVE: "neutral",
  SUSPENDED: "danger"
};

interface RoleMembersPanelProps {
  roleId: number;
  roleName: string;
  canAssign: boolean;
  canRevoke: boolean;
}

export default function RoleMembersPanel({ roleId, roleName, canAssign, canRevoke }: RoleMembersPanelProps) {
  const [allUsers, setAllUsers] = useState<UserListItemResponse[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [showAssignModal, setShowAssignModal] = useState(false);
  const [busyUserId, setBusyUserId] = useState<number | null>(null);
  const { message: toastMessage, showToast } = useToast();
  const { confirmDialog } = useDialog();

  const loadUsers = () => {
    setLoading(true);
    setError(null);
    // size lớn để lấy gần như toàn bộ tài khoản — lọc thành viên của role phía FE vì backend chưa có API "GET members theo role".
    searchUsers({}, 0, 1000)
      .then((res) => setAllUsers(res.content))
      .catch((err) => setError(err instanceof ApiError ? err.message : "Không tải được danh sách tài khoản."))
      .finally(() => setLoading(false));
  };

  useEffect(loadUsers, [roleId]);

  const members = allUsers.filter((u) => u.roles.some((r) => r.id === roleId));

  const handleAssign = async (userId: number) => {
    setBusyUserId(userId);
    setError(null);
    try {
      await assignUserRole(userId, roleId);
      setShowAssignModal(false);
      loadUsers();
      showToast("Đã gán vai trò thành công!");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Gán vai trò thất bại.");
    } finally {
      setBusyUserId(null);
    }
  };

  const handleRemove = async (user: UserListItemResponse) => {
    if (!(await confirmDialog(`Bạn có chắc muốn gỡ "${user.fullName}" khỏi vai trò "${roleName}"?`, { danger: true }))) return;
    setBusyUserId(user.id);
    setError(null);
    try {
      await revokeUserRole(user.id, roleId);
      loadUsers();
      showToast("Đã gỡ vai trò thành công!");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Gỡ vai trò thất bại.");
    } finally {
      setBusyUserId(null);
    }
  };

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <div>
          <h4 className="text-xs font-bold text-slate-800 uppercase tracking-wider block">Thành viên gán vai trò này</h4>
          <p className="text-[10px] text-slate-400 mt-0.5">Quản lý trực tiếp các tài khoản được áp dụng cấu hình vai trò này.</p>
        </div>

        {canAssign && (
          <button
            onClick={() => setShowAssignModal(true)}
            className="bg-brand-gradient hover:opacity-90 text-white px-3.5 py-1.5 rounded-lg text-xs font-bold flex items-center gap-1.5 transition-all shadow-sm"
          >
            <UserPlus className="w-4 h-4" />
            <span>Gán thành viên</span>
          </button>
        )}
      </div>

      {error && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg">{error}</div>}

      <AssignUserModal
        open={showAssignModal}
        candidates={allUsers.filter((u) => !u.roles.some((r) => r.id === roleId))}
        busyUserId={busyUserId}
        onClose={() => setShowAssignModal(false)}
        onAssign={handleAssign}
      />

      {loading ? (
        <p className="text-xs text-slate-500">Đang tải...</p>
      ) : members.length === 0 ? (
        <div className="py-12 border-2 border-dashed border-slate-200 rounded-xl flex flex-col items-center justify-center text-center p-6 space-y-2">
          <Users className="w-8 h-8 text-slate-300" />
          <div>
            <p className="text-xs font-bold text-slate-700">Chưa có thành viên nào gán vai trò này</p>
          </div>
        </div>
      ) : (
        <TableContainer>
          <thead>
            <tr>
              <Th>Username</Th>
              <Th>Họ và tên</Th>
              <Th>Email tài khoản</Th>
              <Th>Trạng thái</Th>
              {canRevoke && <Th className="text-center">Gỡ bỏ</Th>}
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {members.map((u) => (
              <tr key={u.id} className="hover:bg-slate-50/50 transition-colors">
                <Td className="font-mono font-bold text-slate-800">{u.username}</Td>
                <Td className="font-semibold">{u.fullName}</Td>
                <Td className="truncate">{u.email}</Td>
                <Td>
                  <Badge variant={statusVariants[u.status]}>{u.status}</Badge>
                </Td>
                {canRevoke && (
                  <Td className="text-center">
                    <button
                      onClick={() => handleRemove(u)}
                      disabled={busyUserId === u.id}
                      className="p-1 rounded text-slate-400 hover:text-rose-600 hover:bg-rose-50 transition-colors disabled:opacity-50"
                      title="Gỡ khỏi vai trò này"
                    >
                      <UserMinus className="w-4 h-4" />
                    </button>
                  </Td>
                )}
              </tr>
            ))}
          </tbody>
        </TableContainer>
      )}

      <Toast message={toastMessage} />
    </div>
  );
}
