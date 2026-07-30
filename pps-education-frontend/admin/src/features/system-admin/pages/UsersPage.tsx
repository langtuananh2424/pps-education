import React, { useEffect, useState } from "react";
import { KeyRound, Lock, Search, ShieldCheck, Unlock, Users as UsersIcon } from "lucide-react";
import TableContainer, { Td, Th } from "@/components/ui/TableContainer";
import Badge, { BadgeVariant } from "@/components/ui/Badge";
import Button from "@/components/ui/Button";
import Modal from "@/components/ui/Modal";
import EmptyState from "@/components/ui/EmptyState";
import { ApiError } from "@/lib/apiClient";
import { useToast } from "@/lib/useToast";
import Toast from "@/components/ui/Toast";
import { DepartmentResponse, listDepartments } from "@/features/hrm/api";
import {
  changeUserPassword,
  getUserDetail,
  listRoles,
  RoleResponse,
  searchUsers,
  updateUser,
  UpdateUserRequest,
  updateUserStatus,
  UserDetailResponse,
  UserListItemResponse
} from "../api";
import Select from "@/components/ui/Select";

const PAGE_SIZE_OPTIONS = [10, 20, 50, 100];

const statusLabels: Record<string, string> = {
  ACTIVE: "Đang hoạt động",
  INACTIVE: "Ngừng hoạt động",
  SUSPENDED: "Tạm khóa"
};

const statusVariants: Record<string, BadgeVariant> = {
  ACTIVE: "success",
  INACTIVE: "neutral",
  SUSPENDED: "danger"
};

const inputClass = "w-full bg-slate-50 border border-slate-200 text-xs p-2.5 rounded-lg focus:outline-none";
const labelClass = "text-[10px] uppercase font-bold text-slate-500 block mb-1";

export default function UsersPage() {
  const [rows, setRows] = useState<UserListItemResponse[]>([]);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(20);
  const [keyword, setKeyword] = useState("");
  const [status, setStatus] = useState<"" | "ACTIVE" | "INACTIVE" | "SUSPENDED">("");
  const [departmentId, setDepartmentId] = useState("");
  const [roleCode, setRoleCode] = useState("");
  const [loading, setLoading] = useState(false);
  const [listError, setListError] = useState<string | null>(null);
  const [departments, setDepartments] = useState<DepartmentResponse[]>([]);
  const [roles, setRoles] = useState<RoleResponse[]>([]);

  const [selectedUserId, setSelectedUserId] = useState<number | null>(null);

  useEffect(() => {
    // GET /api/departments không yêu cầu quyền riêng — chỉ dùng để đổi ID sang tên hiển thị, không dùng để sửa.
    listDepartments().then(setDepartments).catch(() => {});
    listRoles().then(setRoles).catch(() => {});
  }, []);

  const departmentName = (id: number | null): string => departments.find((d) => d.id === id)?.name ?? "—";

  /**
   * GET /api/users chưa có param lọc theo vai trò (chỉ keyword/status/departmentId) — lọc
   * theo vai trò đang làm tạm ở FE: khi có chọn vai trò, tải 1 lô lớn (bỏ qua phân trang
   * server) rồi tự lọc + tự phân trang lại ở đây. Đã báo BE bổ sung param roleCode cho
   * GET /api/users (tương tự departmentId) để chuyển hẳn về lọc server-side, tránh giới
   * hạn lô lớn khi hệ thống có nhiều tài khoản hơn.
   */
  const loadUsers = () => {
    setLoading(true);
    setListError(null);
    const filter = { keyword: keyword.trim() || undefined, status: status || undefined, departmentId: departmentId ? Number(departmentId) : undefined };

    if (roleCode) {
      searchUsers(filter, 0, 1000)
        .then((res) => {
          const filtered = res.content.filter((u) => u.roles.some((r) => r.code === roleCode));
          setTotalElements(filtered.length);
          setTotalPages(Math.max(1, Math.ceil(filtered.length / pageSize)));
          setRows(filtered.slice(page * pageSize, (page + 1) * pageSize));
        })
        .catch((err) => setListError(err instanceof ApiError ? err.message : "Không tải được danh sách tài khoản."))
        .finally(() => setLoading(false));
      return;
    }

    searchUsers(filter, page, pageSize)
      .then((res) => {
        setRows(res.content);
        setTotalPages(res.totalPages);
        setTotalElements(res.totalElements);
      })
      .catch((err) => setListError(err instanceof ApiError ? err.message : "Không tải được danh sách tài khoản."))
      .finally(() => setLoading(false));
  };

  useEffect(loadUsers, [page, pageSize]);

  const handleSearch = (e: React.FormEvent) => {
    e.preventDefault();
    setPage(0);
    loadUsers();
  };

  return (
    <div className="space-y-4 animate-in fade-in duration-200">
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-3">
        <div>
          <h2 className="text-sm font-bold text-slate-800 uppercase tracking-wider block">Quản lý người dùng</h2>
          <p className="text-[10px] text-slate-400 mt-0.5">
            Danh sách tài khoản toàn hệ thống — tra cứu, cập nhật hồ sơ, khóa/mở khóa. Tài khoản được khởi tạo từ Quản lý nhân sự /
            Quản lý học sinh.
          </p>
        </div>
      </div>

      <form onSubmit={handleSearch} className="bg-white p-4 rounded-xl border border-slate-200 shadow-soft flex flex-col md:flex-row gap-3">
        <div className="flex-1 relative">
          <Search className="w-3.5 h-3.5 text-slate-400 absolute left-3 top-1/2 -translate-y-1/2" />
          <input
            value={keyword}
            onChange={(e) => setKeyword(e.target.value)}
            placeholder="Tìm theo username / email / họ tên..."
            className="w-full bg-slate-50 border border-slate-200 text-xs pl-8 pr-3 py-2.5 rounded-lg focus:outline-none"
          />
        </div>
        <Select value={status} onChange={(e) => setStatus(e.target.value as typeof status)} className="bg-slate-50 border border-slate-200 text-xs p-2.5 rounded-lg focus:outline-none">
          <option value="">-- Mọi trạng thái --</option>
          <option value="ACTIVE">Đang hoạt động</option>
          <option value="INACTIVE">Ngừng hoạt động</option>
          <option value="SUSPENDED">Tạm khóa</option>
        </Select>
        <Select
          value={departmentId}
          onChange={(e) => setDepartmentId(e.target.value)}
          className="w-48 bg-slate-50 border border-slate-200 text-xs p-2.5 rounded-lg focus:outline-none"
        >
          <option value="">-- Mọi phòng ban --</option>
          {departments.map((d) => (
            <option key={d.id} value={d.id}>
              {d.name}
            </option>
          ))}
        </Select>
        <Select
          value={roleCode}
          onChange={(e) => setRoleCode(e.target.value)}
          className="w-48 bg-slate-50 border border-slate-200 text-xs p-2.5 rounded-lg focus:outline-none"
        >
          <option value="">-- Mọi vai trò --</option>
          {roles.map((r) => (
            <option key={r.id} value={r.code}>
              {r.name}
            </option>
          ))}
        </Select>
        <Button type="submit" variant="dark">
          Tìm kiếm
        </Button>
      </form>

      <div className="bg-white rounded-xl border border-slate-200 shadow-soft overflow-hidden">
        {listError && <div className="p-4 text-xs text-rose-600 bg-rose-50 border-b border-rose-100">{listError}</div>}
        {!loading && rows.length === 0 && !listError ? (
          <EmptyState icon={UsersIcon} title="Không tìm thấy tài khoản phù hợp" description="Thử nới lỏng từ khóa hoặc bộ lọc." />
        ) : (
          <TableContainer className="rounded-none border-0">
            <thead>
              <tr>
                <Th>Username</Th>
                <Th>Họ tên</Th>
                <Th>Email</Th>
                <Th>Phòng ban</Th>
                <Th>Vai trò</Th>
                <Th>Trạng thái</Th>
                <Th>{" "}</Th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {rows.map((u) => (
                <tr key={u.id} className="hover:bg-slate-50/50 transition-colors">
                  <Td className="font-mono font-bold text-slate-800">{u.username}</Td>
                  <Td className="font-semibold">{u.fullName}</Td>
                  <Td>{u.email}</Td>
                  <Td>{departmentName(u.departmentId)}</Td>
                  <Td>
                    <div className="flex flex-wrap gap-1">
                      {u.roles.length === 0 ? (
                        <span className="text-slate-400">Chưa gán</span>
                      ) : (
                        u.roles.map((r) => (
                          <Badge key={r.id} variant="info">
                            {r.code}
                          </Badge>
                        ))
                      )}
                    </div>
                  </Td>
                  <Td>
                    <Badge variant={statusVariants[u.status]}>{statusLabels[u.status]}</Badge>
                  </Td>
                  <Td>
                    <Button size="sm" onClick={() => setSelectedUserId(u.id)}>
                      Xem/Sửa
                    </Button>
                  </Td>
                </tr>
              ))}
            </tbody>
          </TableContainer>
        )}

        {rows.length > 0 && (
          <div className="flex flex-col sm:flex-row items-center justify-between gap-2 px-4 py-3 border-t border-slate-100 text-[11px] text-slate-500">
            <div className="flex items-center gap-2">
              <span>Tổng {totalElements} tài khoản</span>
              <span className="text-slate-300">|</span>
              <label className="flex items-center gap-1.5">
                Số dòng/trang:
                <Select
                  value={pageSize}
                  onChange={(e) => {
                    setPageSize(Number(e.target.value));
                    setPage(0);
                  }}
                  className="bg-slate-50 border border-slate-200 text-[11px] px-1.5 py-1 rounded-md focus:outline-none cursor-pointer"
                >
                  {PAGE_SIZE_OPTIONS.map((size) => (
                    <option key={size} value={size}>
                      {size}
                    </option>
                  ))}
                </Select>
              </label>
            </div>
            {totalPages > 1 && (
              <div className="flex items-center gap-2">
                <Button size="sm" variant="secondary" disabled={page <= 0} onClick={() => setPage((p) => p - 1)}>
                  Trước
                </Button>
                <span className="font-mono">
                  Trang {page + 1}/{totalPages}
                </span>
                <Button size="sm" variant="secondary" disabled={page + 1 >= totalPages} onClick={() => setPage((p) => p + 1)}>
                  Sau
                </Button>
              </div>
            )}
          </div>
        )}
      </div>

      <UserDetailModal
        userId={selectedUserId}
        departments={departments}
        onClose={() => setSelectedUserId(null)}
        onChanged={loadUsers}
      />
    </div>
  );
}

function UserDetailModal({
  userId,
  departments,
  onClose,
  onChanged
}: {
  userId: number | null;
  departments: DepartmentResponse[];
  onClose: () => void;
  onChanged: () => void;
}) {
  const [detail, setDetail] = useState<UserDetailResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [savingProfile, setSavingProfile] = useState(false);
  const [profileForm, setProfileForm] = useState<UpdateUserRequest>({ fullName: "", phone: "" });
  const [newPassword, setNewPassword] = useState("");
  const [changingPassword, setChangingPassword] = useState(false);
  const [changingStatus, setChangingStatus] = useState(false);
  const { message: toastMessage, showToast } = useToast();

  const loadDetail = (id: number) => {
    setLoading(true);
    setError(null);
    getUserDetail(id)
      .then((d) => {
        setDetail(d);
        setProfileForm({ fullName: d.fullName, phone: d.phone ?? "" });
      })
      .catch((err) => setError(err instanceof ApiError ? err.message : "Không tải được chi tiết tài khoản."))
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    if (userId != null) {
      loadDetail(userId);
      setNewPassword("");
    } else {
      setDetail(null);
    }
  }, [userId]);

  if (userId == null) return null;

  const handleSaveProfile = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!detail) return;
    setSavingProfile(true);
    setError(null);
    try {
      await updateUser(detail.id, {
        fullName: profileForm.fullName,
        phone: profileForm.phone?.trim() || undefined
      });
      loadDetail(detail.id);
      onChanged();
      showToast("Đã lưu hồ sơ thành công!");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Cập nhật hồ sơ thất bại.");
    } finally {
      setSavingProfile(false);
    }
  };

  const handleToggleStatus = async (newStatus: "ACTIVE" | "INACTIVE" | "SUSPENDED") => {
    if (!detail) return;
    if (newStatus !== "ACTIVE" && !window.confirm(`Xác nhận chuyển tài khoản "${detail.username}" sang trạng thái ${statusLabels[newStatus]}?`)) {
      return;
    }
    setChangingStatus(true);
    setError(null);
    try {
      await updateUserStatus(detail.id, newStatus);
      loadDetail(detail.id);
      onChanged();
      showToast(`Đã chuyển tài khoản sang trạng thái "${statusLabels[newStatus]}"!`);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Đổi trạng thái thất bại.");
    } finally {
      setChangingStatus(false);
    }
  };

  const handleChangePassword = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!detail || newPassword.trim().length < 8) {
      setError("Mật khẩu mới phải từ 8 ký tự trở lên.");
      return;
    }
    if (!window.confirm(`Xác nhận đặt mật khẩu mới cho tài khoản "${detail.username}"? Tài khoản này có thể đăng nhập ngay bằng mật khẩu mới, mật khẩu cũ sẽ không còn dùng được.`)) {
      return;
    }
    setChangingPassword(true);
    setError(null);
    try {
      await changeUserPassword(detail.id, newPassword.trim());
      setNewPassword("");
      loadDetail(detail.id);
      showToast("Đã đặt lại mật khẩu thành công!");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Đặt lại mật khẩu thất bại.");
    } finally {
      setChangingPassword(false);
    }
  };

  return (
    <Modal open={userId != null} onClose={onClose} title={detail ? `Tài khoản: ${detail.username}` : "Chi tiết tài khoản"} size="lg">
      {loading && <p className="text-xs text-slate-500">Đang tải...</p>}
      {error && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg mb-4">{error}</div>}

      {detail && (
        <div className="space-y-5">
          <div className="flex flex-wrap items-center gap-2">
            <Badge variant={statusVariants[detail.status]}>{statusLabels[detail.status]}</Badge>
            {detail.roles.map((r) => (
              <Badge key={r.id} variant="info">
                <ShieldCheck className="w-3 h-3" /> {r.code}
              </Badge>
            ))}
            {detail.googleLinked && <Badge variant="brand">Liên kết Google</Badge>}
          </div>

          <div className="grid grid-cols-2 gap-x-4 gap-y-1 text-[11px] text-slate-500">
            <span>Đăng nhập gần nhất: <span className="font-mono text-slate-700">{detail.lastLoginAt ?? "Chưa từng"}</span></span>
            <span>Số lần đăng nhập sai: <span className="font-mono text-slate-700">{detail.failedLoginCount}</span></span>
            {detail.lockedUntil && <span>Khóa tạm tới: <span className="font-mono text-slate-700">{detail.lockedUntil}</span></span>}
            <span>
              Phòng ban: <span className="font-mono text-slate-700">{departments.find((d) => d.id === detail.departmentId)?.name ?? "—"}</span>
            </span>
            <span>
              Miễn trừ chấm công: <span className="font-mono text-slate-700">{detail.isManagement ? "Có" : "Không"}</span>
            </span>
          </div>
          <p className="text-[10px] text-slate-400 italic -mt-3">
            Phòng ban / miễn trừ chấm công thuộc hồ sơ nhân sự — sửa tại "Quản lý nhân sự", không sửa được ở đây.
          </p>

          <form onSubmit={handleSaveProfile} className="space-y-3 border-t border-slate-100 pt-4">
            <div>
              <span className="text-[10px] font-bold uppercase text-slate-500">Cập nhật hồ sơ</span>
              <p className="text-[10px] text-slate-400">Chỉ đổi họ tên/số điện thoại hiển thị của tài khoản này.</p>
            </div>
            <div className="grid grid-cols-2 gap-3">
              <div>
                <label className={labelClass}>Họ tên *</label>
                <input value={profileForm.fullName} onChange={(e) => setProfileForm({ ...profileForm, fullName: e.target.value })} className={inputClass} required />
              </div>
              <div>
                <label className={labelClass}>Số điện thoại</label>
                <input value={profileForm.phone ?? ""} onChange={(e) => setProfileForm({ ...profileForm, phone: e.target.value })} className={inputClass} />
              </div>
            </div>
            <Button type="submit" variant="primary" size="sm" disabled={savingProfile}>
              {savingProfile ? "Đang lưu..." : "Lưu hồ sơ"}
            </Button>
          </form>

          <form onSubmit={handleChangePassword} className="space-y-2 border-t border-slate-100 pt-4">
            <div>
              <span className="text-[10px] font-bold uppercase text-slate-500 flex items-center gap-1">
                <KeyRound className="w-3 h-3" /> Đặt lại mật khẩu
              </span>
              <p className="text-[10px] text-slate-400">Admin đặt thẳng mật khẩu mới cho tài khoản này, có hiệu lực ngay — không cần biết mật khẩu cũ (dùng khi người dùng quên mật khẩu).</p>
            </div>
            <div className="flex gap-2">
              <input
                type="password"
                value={newPassword}
                onChange={(e) => setNewPassword(e.target.value)}
                placeholder="Mật khẩu mới (tối thiểu 8 ký tự)"
                className={inputClass}
              />
              <Button type="submit" variant="secondary" size="sm" disabled={changingPassword} className="whitespace-nowrap">
                {changingPassword ? "Đang lưu..." : "Đặt lại mật khẩu"}
              </Button>
            </div>
          </form>

          <div className="border-t border-slate-100 pt-4 flex flex-wrap gap-2">
            <div className="w-full">
              <span className="text-[10px] font-bold uppercase text-slate-500">Khóa/Mở khóa</span>
              <p className="text-[10px] text-slate-400">Đổi trạng thái đăng nhập của tài khoản — ngừng/tạm khóa sẽ chặn đăng nhập ngay.</p>
            </div>
            {detail.status !== "ACTIVE" && (
              <Button size="sm" variant="secondary" disabled={changingStatus} onClick={() => handleToggleStatus("ACTIVE")}>
                <Unlock className="w-3.5 h-3.5" /> Mở khóa (ACTIVE)
              </Button>
            )}
            {detail.status !== "INACTIVE" && (
              <Button size="sm" variant="danger" disabled={changingStatus} onClick={() => handleToggleStatus("INACTIVE")}>
                <Lock className="w-3.5 h-3.5" /> Ngừng hoạt động
              </Button>
            )}
            {detail.status !== "SUSPENDED" && (
              <Button size="sm" variant="danger" disabled={changingStatus} onClick={() => handleToggleStatus("SUSPENDED")}>
                <Lock className="w-3.5 h-3.5" /> Tạm khóa
              </Button>
            )}
          </div>
        </div>
      )}

      <Toast message={toastMessage} />
    </Modal>
  );
}
