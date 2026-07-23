import React, { useEffect, useMemo, useState } from "react";
import { Info, Search, ShieldQuestion, UserCheck } from "lucide-react";
import { ApiError } from "@/lib/apiClient";
import {
  EffectivePermissionsResponse,
  getEffectivePermissions,
  getUserDetail,
  listPermissions,
  PermissionCatalogItem,
  removeUserPermissionOverride,
  searchUsers,
  upsertUserPermissionOverride,
  UserDetailResponse,
  UserListItemResponse
} from "../api";
import CreateOverrideForm from "../components/CreateOverrideForm";
import OverridesTable from "../components/OverridesTable";
import EmptyState from "@/components/ui/EmptyState";
import { useToast } from "@/lib/useToast";
import Toast from "@/components/ui/Toast";

export default function OverridesPage() {
  const [allUsers, setAllUsers] = useState<UserListItemResponse[]>([]);
  const [userSearchQuery, setUserSearchQuery] = useState("");
  const [selectedUserId, setSelectedUserId] = useState<number | null>(null);

  const [permissions, setPermissions] = useState<PermissionCatalogItem[]>([]);
  const [userDetail, setUserDetail] = useState<UserDetailResponse | null>(null);
  const [effective, setEffective] = useState<EffectivePermissionsResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const { message: toastMessage, showToast } = useToast();

  useEffect(() => {
    searchUsers({}, 0, 1000).then((res) => setAllUsers(res.content)).catch(() => undefined);
    listPermissions().then(setPermissions).catch(() => undefined);
  }, []);

  const loadSelectedUser = (userId: number) => {
    setLoading(true);
    setError(null);
    Promise.all([getUserDetail(userId), getEffectivePermissions(userId)])
      .then(([detail, eff]) => {
        setUserDetail(detail);
        setEffective(eff);
      })
      .catch((err) => setError(err instanceof ApiError ? err.message : "Không tải được dữ liệu tài khoản."))
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    if (selectedUserId != null) loadSelectedUser(selectedUserId);
    else {
      setUserDetail(null);
      setEffective(null);
    }
  }, [selectedUserId]);

  const filteredUsers = useMemo(() => {
    const q = userSearchQuery.trim().toLowerCase();
    if (!q) return [];
    return allUsers
      .filter((u) => u.username.toLowerCase().includes(q) || u.fullName.toLowerCase().includes(q) || u.email.toLowerCase().includes(q))
      .slice(0, 8);
  }, [allUsers, userSearchQuery]);

  // Override còn hiệu lực: backend không xóa cứng khi gỡ (chỉ set expiresAt = lúc gỡ) — phải tự lọc phía FE (UC-04 A1).
  const activeOverrides = (userDetail?.permissionOverrides ?? []).filter(
    (ov) => !ov.expiresAt || new Date(ov.expiresAt) > new Date()
  );

  const handleUpsert = async (permissionId: number, overrideType: "GRANT" | "REVOKE", reason: string, expiresAt: string) => {
    if (!selectedUserId) return;
    await upsertUserPermissionOverride(selectedUserId, permissionId, overrideType, reason, expiresAt || undefined);
    loadSelectedUser(selectedUserId);
    showToast("Đã lưu ngoại lệ quyền thành công!");
  };

  const handleRemove = async (permissionId: number) => {
    if (!selectedUserId) return;
    await removeUserPermissionOverride(selectedUserId, permissionId);
    loadSelectedUser(selectedUserId);
    showToast("Đã gỡ ngoại lệ quyền thành công!");
  };

  return (
    <div className="space-y-6 animate-in fade-in duration-200">
      <div className="bg-gradient-to-r from-orange-50 to-amber-50/50 border border-orange-200/60 p-4 rounded-xl flex items-start gap-3 shadow-sm">
        <Info className="w-5 h-5 text-brand-red shrink-0 mt-0.5" />
        <div className="space-y-1">
          <span className="text-xs font-bold text-brand-red uppercase tracking-wider block">TÙY CHỈNH TÀI KHOẢN (USER OVERRIDES - UC-04)</span>
          <p className="text-xs text-slate-600 leading-relaxed">
            Cấp thêm hoặc tước bỏ trực tiếp một quyền hạt nhân cho 1 tài khoản cụ thể, có độ ưu tiên cao nhất trong công thức
            quyền hiệu lực — không cần đổi vai trò vĩnh viễn của họ.
          </p>
        </div>
      </div>

      <div className="bg-white border border-slate-200 rounded-xl p-4 shadow-sm space-y-3">
        <label className="text-[10px] uppercase font-bold text-slate-500 block">Bước 1 — Tìm và chọn tài khoản</label>
        <div className="relative max-w-md">
          <Search className="absolute left-3 top-2.5 w-3.5 h-3.5 text-slate-400" />
          <input
            value={userSearchQuery}
            onChange={(e) => setUserSearchQuery(e.target.value)}
            placeholder="Tìm theo username / họ tên / email..."
            className="w-full bg-slate-50 border border-slate-200 text-xs pl-8 pr-3 py-2.5 rounded-lg focus:outline-none"
          />
          {filteredUsers.length > 0 && (
            <div className="absolute z-10 mt-1 w-full bg-white border border-slate-200 rounded-lg shadow-lg divide-y divide-slate-100 max-h-64 overflow-y-auto">
              {filteredUsers.map((u) => (
                <button
                  key={u.id}
                  onClick={() => {
                    setSelectedUserId(u.id);
                    setUserSearchQuery("");
                  }}
                  className="w-full text-left px-3 py-2 hover:bg-slate-50 text-xs"
                >
                  <span className="font-bold text-slate-800">{u.fullName}</span>{" "}
                  <span className="text-slate-400">
                    ({u.username} · {u.email})
                  </span>
                </button>
              ))}
            </div>
          )}
        </div>

        {userDetail && (
          <div className="flex items-center gap-2 bg-emerald-50 border border-emerald-100 text-emerald-700 text-xs font-semibold px-3 py-2 rounded-lg w-fit">
            <UserCheck className="w-4 h-4" />
            Đang chọn: {userDetail.fullName} ({userDetail.username})
          </div>
        )}
      </div>

      {error && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg">{error}</div>}

      {loading && <p className="text-xs text-slate-500">Đang tải...</p>}

      {!selectedUserId && !loading && (
        <div className="bg-white border border-slate-200 rounded-xl shadow-soft">
          <EmptyState
            icon={ShieldQuestion}
            title="Chưa chọn tài khoản nào"
            description="Tìm và chọn 1 tài khoản ở ô phía trên để xem quyền hiệu lực hiện tại và thiết lập ngoại lệ (cấp thêm/tước bỏ quyền riêng) cho tài khoản đó."
          />
        </div>
      )}

      {userDetail && effective && !loading && (
        <>
          <div className="bg-white border border-slate-200 rounded-xl p-4 shadow-sm space-y-2">
            <span className="text-[10px] uppercase font-bold text-slate-500 block">
              Quyền hiệu lực hiện tại ({effective.permissions.length}) — hợp từ vai trò + ngoại lệ đang áp dụng
            </span>
            <div className="flex flex-wrap gap-1.5">
              {effective.permissions.length === 0 ? (
                <span className="text-xs text-slate-400 italic">Chưa có quyền nào.</span>
              ) : (
                effective.permissions.map((code) => (
                  <code key={code} className="text-[10px] font-mono font-bold text-brand-red bg-orange-50/60 border border-orange-100 px-1.5 py-0.5 rounded">
                    {code}
                  </code>
                ))
              )}
            </div>
          </div>

          <div className="grid grid-cols-1 lg:grid-cols-4 gap-6">
            <CreateOverrideForm permissions={permissions} onSubmit={handleUpsert} />
            <OverridesTable overrides={activeOverrides} onRemove={handleRemove} />
          </div>
        </>
      )}

      <Toast message={toastMessage} />
    </div>
  );
}
