import React, { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { KeyRound, Lock, Search, ShieldCheck, Unlock, Users as UsersIcon } from "lucide-react";
import TableContainer, { Td, Th } from "@/components/ui/TableContainer";
import Badge, { BadgeVariant } from "@/components/ui/Badge";
import Button from "@/components/ui/Button";
import Modal from "@/components/ui/Modal";
import EmptyState from "@/components/ui/EmptyState";
import { ApiError } from "@/lib/apiClient";
import { useToast } from "@/lib/useToast";
import Toast from "@/components/ui/Toast";
import { useDialog } from "@/components/ui/DialogProvider";
import { DepartmentResponse, listDepartments } from "@/features/hrm/api";
import {
  changeUserPassword,
  getUserDetail,
  getUserLoginHistory,
  listRoles,
  LoginHistoryItemResponse,
  RoleResponse,
  searchUsers,
  updateUser,
  updateUserEmail,
  UpdateUserRequest,
  updateUserStatus,
  UserDetailResponse,
  UserListItemResponse
} from "../api";
import Select from "@/components/ui/Select";
import { formatDateTime } from "@/lib/i18nFormat";

const PAGE_SIZE_OPTIONS = [10, 20, 50, 100];

/** Nhãn trạng thái dịch qua i18next namespace "system-admin-users" — xem src/i18n/locales/{vi,en}/system-admin-users.json. */
function userStatusLabel(t: (key: string) => string, status: string): string {
  return t(`userStatus.${status}`);
}

const statusVariants: Record<string, BadgeVariant> = {
  ACTIVE: "success",
  INACTIVE: "neutral",
  SUSPENDED: "danger"
};

const inputClass = "w-full bg-slate-50 border border-slate-200 text-xs p-2.5 rounded-lg focus:outline-none";
const labelClass = "text-[10px] uppercase font-bold text-slate-500 block mb-1";
const LOGIN_HISTORY_PAGE_SIZE = 10;

export default function UsersPage() {
  const { t } = useTranslation("system-admin-users");
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
    listDepartments().then(setDepartments).catch(() => { });
    listRoles().then(setRoles).catch(() => { });
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
        .catch((err) => setListError(err instanceof ApiError ? err.message : t("usersPage.loadError")))
        .finally(() => setLoading(false));
      return;
    }

    searchUsers(filter, page, pageSize)
      .then((res) => {
        setRows(res.content);
        setTotalPages(res.totalPages);
        setTotalElements(res.totalElements);
      })
      .catch((err) => setListError(err instanceof ApiError ? err.message : t("usersPage.loadError")))
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
          <h2 className="text-sm font-bold text-slate-800 uppercase tracking-wider block">{t("usersPage.title")}</h2>
          <p className="text-[10px] text-slate-400 mt-0.5">{t("usersPage.description")}</p>
        </div>
      </div>

      <form onSubmit={handleSearch} className="bg-white p-4 rounded-xl border border-slate-200 shadow-soft flex flex-col md:flex-row gap-3">
        <div className="flex-1 relative">
          <Search className="w-3.5 h-3.5 text-slate-400 absolute left-3 top-1/2 -translate-y-1/2" />
          <input
            value={keyword}
            onChange={(e) => setKeyword(e.target.value)}
            placeholder={t("usersPage.search.placeholder")}
            className="w-full bg-slate-50 border border-slate-200 text-xs pl-8 pr-3 py-2.5 rounded-lg focus:outline-none"
          />
        </div>
        <Select value={status} onChange={(e) => setStatus(e.target.value as typeof status)} className="bg-slate-50 border border-slate-200 text-xs p-2.5 rounded-lg focus:outline-none">
          <option value="">{t("usersPage.search.allStatuses")}</option>
          <option value="ACTIVE">{userStatusLabel(t, "ACTIVE")}</option>
          <option value="INACTIVE">{userStatusLabel(t, "INACTIVE")}</option>
          <option value="SUSPENDED">{userStatusLabel(t, "SUSPENDED")}</option>
        </Select>
        <Select
          value={departmentId}
          onChange={(e) => setDepartmentId(e.target.value)}
          className="w-48 bg-slate-50 border border-slate-200 text-xs p-2.5 rounded-lg focus:outline-none"
        >
          <option value="">{t("usersPage.search.allDepartments")}</option>
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
          <option value="">{t("usersPage.search.allRoles")}</option>
          {roles.map((r) => (
            <option key={r.id} value={r.code}>
              {r.name}
            </option>
          ))}
        </Select>
        <Button type="submit" variant="dark">
          {t("usersPage.search.submit")}
        </Button>
      </form>

      <div className="bg-white rounded-xl border border-slate-200 shadow-soft overflow-hidden">
        {listError && <div className="p-4 text-xs text-rose-600 bg-rose-50 border-b border-rose-100">{listError}</div>}
        {!loading && rows.length === 0 && !listError ? (
          <EmptyState icon={UsersIcon} title={t("usersPage.empty.title")} description={t("usersPage.empty.description")} />
        ) : (
          <TableContainer className="rounded-none border-0">
            <thead>
              <tr>
                <Th>{t("usersPage.table.username")}</Th>
                <Th>{t("usersPage.table.fullName")}</Th>
                <Th>{t("usersPage.table.email")}</Th>
                <Th>{t("usersPage.table.department")}</Th>
                <Th>{t("usersPage.table.role")}</Th>
                <Th>{t("usersPage.table.status")}</Th>
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
                        <span className="text-slate-400">{t("usersPage.table.noRoles")}</span>
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
                    <Badge variant={statusVariants[u.status]}>{userStatusLabel(t, u.status)}</Badge>
                  </Td>
                  <Td>
                    <Button size="sm" onClick={() => setSelectedUserId(u.id)}>
                      {t("usersPage.table.viewEdit")}
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
              <span>{t("usersPage.pagination.total", { count: totalElements })}</span>
              <span className="text-slate-300">|</span>
              <label className="flex items-center gap-1.5">
                {t("usersPage.pagination.rowsPerPage")}
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
                  {t("usersPage.pagination.prev")}
                </Button>
                <span className="font-mono">
                  {t("usersPage.pagination.pageOf", { page: page + 1, total: totalPages })}
                </span>
                <Button size="sm" variant="secondary" disabled={page + 1 >= totalPages} onClick={() => setPage((p) => p + 1)}>
                  {t("usersPage.pagination.next")}
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
  const { t, i18n } = useTranslation("system-admin-users");
  const [detail, setDetail] = useState<UserDetailResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [loginHistory, setLoginHistory] = useState<LoginHistoryItemResponse[]>([]);
  const [loginHistoryLoading, setLoginHistoryLoading] = useState(false);
  const [loginHistoryLoadingMore, setLoginHistoryLoadingMore] = useState(false);
  const [loginHistoryPage, setLoginHistoryPage] = useState(0);
  const [loginHistoryTotalPages, setLoginHistoryTotalPages] = useState(0);
  const [savingProfile, setSavingProfile] = useState(false);
  const [profileForm, setProfileForm] = useState<UpdateUserRequest>({ fullName: "", phone: "" });
  const [newPassword, setNewPassword] = useState("");
  const [changingPassword, setChangingPassword] = useState(false);
  const [changingStatus, setChangingStatus] = useState(false);
  const [newEmail, setNewEmail] = useState("");
  const [changingEmail, setChangingEmail] = useState(false);
  const { message: toastMessage, showToast } = useToast();
  const { confirmDialog } = useDialog();

  const loadDetail = (id: number) => {
    setLoading(true);
    setError(null);
    getUserDetail(id)
      .then((d) => {
        setDetail(d);
        setProfileForm({ fullName: d.fullName, phone: d.phone ?? "" });
        setNewEmail(d.email);
      })
      .catch((err) => setError(err instanceof ApiError ? err.message : t("usersPage.detail.loadError")))
      .finally(() => setLoading(false));
  };

  /** UC-44 bổ sung ngoài SDD gốc (đã xác nhận với người dùng 2026-09-05): lịch sử đăng nhập/thiết bị. */
  const loadLoginHistory = (id: number, targetPage: number) => {
    const setBusy = targetPage === 0 ? setLoginHistoryLoading : setLoginHistoryLoadingMore;
    setBusy(true);
    getUserLoginHistory(id, targetPage, LOGIN_HISTORY_PAGE_SIZE)
      .then((res) => {
        setLoginHistory((prev) => (targetPage === 0 ? res.content : [...prev, ...res.content]));
        setLoginHistoryPage(res.number);
        setLoginHistoryTotalPages(res.totalPages);
      })
      .catch(() => undefined)
      .finally(() => setBusy(false));
  };

  useEffect(() => {
    if (userId != null) {
      loadDetail(userId);
      loadLoginHistory(userId, 0);
      setNewPassword("");
      setNewEmail("");
    } else {
      setDetail(null);
      setLoginHistory([]);
      setLoginHistoryPage(0);
      setLoginHistoryTotalPages(0);
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
      showToast(t("usersPage.detail.profile.savedToast"));
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t("usersPage.detail.profile.saveError"));
    } finally {
      setSavingProfile(false);
    }
  };

  const handleToggleStatus = async (newStatus: "ACTIVE" | "INACTIVE" | "SUSPENDED") => {
    if (!detail) return;
    if (
      newStatus !== "ACTIVE" &&
      !(await confirmDialog(
        t("usersPage.detail.status.confirmMessage", { username: detail.username, statusLabel: userStatusLabel(t, newStatus) }),
        { danger: true }
      ))
    ) {
      return;
    }
    setChangingStatus(true);
    setError(null);
    try {
      await updateUserStatus(detail.id, newStatus);
      loadDetail(detail.id);
      onChanged();
      showToast(t("usersPage.detail.status.changedToast", { statusLabel: userStatusLabel(t, newStatus) }));
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t("usersPage.detail.status.saveError"));
    } finally {
      setChangingStatus(false);
    }
  };

  /** UC-55: tách riêng khỏi handleSaveProfile (UC-49) — backend coi 2 luồng độc lập, xem docs/uc/phan-he-02-phan-quyen.md. */
  const handleChangeEmail = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!detail || newEmail.trim() === detail.email) return;
    setChangingEmail(true);
    setError(null);
    try {
      await updateUserEmail(detail.id, { newEmail: newEmail.trim() });
      loadDetail(detail.id);
      onChanged();
      showToast(t("usersPage.detail.email.savedToast"));
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t("usersPage.detail.email.saveError"));
    } finally {
      setChangingEmail(false);
    }
  };

  const handleChangePassword = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!detail || newPassword.trim().length < 8) {
      setError(t("usersPage.detail.password.validationError"));
      return;
    }
    if (
      !(await confirmDialog(
        t("usersPage.detail.password.confirmMessage", { username: detail.username }),
        { danger: true }
      ))
    ) {
      return;
    }
    setChangingPassword(true);
    setError(null);
    try {
      await changeUserPassword(detail.id, newPassword.trim());
      setNewPassword("");
      loadDetail(detail.id);
      showToast(t("usersPage.detail.password.savedToast"));
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t("usersPage.detail.password.saveError"));
    } finally {
      setChangingPassword(false);
    }
  };

  return (
    <Modal open={userId != null} onClose={onClose} title={detail ? t("usersPage.detail.titleWithUsername", { username: detail.username }) : t("usersPage.detail.titleFallback")} size="lg">
      {loading && <p className="text-xs text-slate-500">{t("usersPage.detail.loading")}</p>}
      {error && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg mb-4">{error}</div>}

      {detail && (
        <div className="space-y-5">
          <div className="flex flex-wrap items-center gap-2">
            <Badge variant={statusVariants[detail.status]}>{userStatusLabel(t, detail.status)}</Badge>
            {detail.roles.map((r) => (
              <Badge key={r.id} variant="info">
                <ShieldCheck className="w-3 h-3" /> {r.code}
              </Badge>
            ))}
            {detail.googleLinked && <Badge variant="brand">{t("usersPage.detail.googleLinked")}</Badge>}
          </div>

          <div className="grid grid-cols-2 gap-x-4 gap-y-1 text-[11px] text-slate-500">
            <span>{t("usersPage.detail.lastLogin")} <span className="font-mono text-slate-700">{detail.lastLoginAt ?? t("usersPage.detail.neverLoggedIn")}</span></span>
            <span>{t("usersPage.detail.failedLoginCount")} <span className="font-mono text-slate-700">{detail.failedLoginCount}</span></span>
            {detail.lockedUntil && <span>{t("usersPage.detail.lockedUntil")} <span className="font-mono text-slate-700">{detail.lockedUntil}</span></span>}
            <span>
              {t("usersPage.detail.department")} <span className="font-mono text-slate-700">{departments.find((d) => d.id === detail.departmentId)?.name ?? t("usersPage.detail.departmentFallback")}</span>
            </span>
            <span>
              {t("usersPage.detail.attendanceExempt")} <span className="font-mono text-slate-700">{detail.isManagement ? t("usersPage.detail.yes") : t("usersPage.detail.no")}</span>
            </span>
          </div>
          <p className="text-[10px] text-slate-400 italic -mt-3">
            {t("usersPage.detail.hint")}
          </p>

          <form onSubmit={handleSaveProfile} className="space-y-3 border-t border-slate-100 pt-4">
            <div>
              <span className="text-[10px] font-bold uppercase text-slate-500">{t("usersPage.detail.profile.sectionTitle")}</span>
              <p className="text-[10px] text-slate-400">{t("usersPage.detail.profile.sectionDescription")}</p>
            </div>
            <div className="grid grid-cols-2 gap-3">
              <div>
                <label className={labelClass}>{t("usersPage.detail.profile.fullNameLabel")}</label>
                <input value={profileForm.fullName} onChange={(e) => setProfileForm({ ...profileForm, fullName: e.target.value })} className={inputClass} required />
              </div>
              <div>
                <label className={labelClass}>{t("usersPage.detail.profile.phoneLabel")}</label>
                <input value={profileForm.phone ?? ""} onChange={(e) => setProfileForm({ ...profileForm, phone: e.target.value })} className={inputClass} />
              </div>
            </div>
            <Button type="submit" variant="primary" size="sm" disabled={savingProfile}>
              {savingProfile ? t("usersPage.detail.profile.saving") : t("usersPage.detail.profile.save")}
            </Button>
          </form>

          <form onSubmit={handleChangeEmail} className="space-y-2 border-t border-slate-100 pt-4">
            <div>
              <span className="text-[10px] font-bold uppercase text-slate-500">{t("usersPage.detail.email.sectionTitle")}</span>
              <p className="text-[10px] text-slate-400">
                {t("usersPage.detail.email.sectionDescription")}
              </p>
            </div>
            <div className="flex gap-2">
              <input
                type="email"
                value={newEmail}
                onChange={(e) => setNewEmail(e.target.value)}
                placeholder={t("usersPage.detail.email.placeholder")}
                className={inputClass}
                required
              />
              <Button
                type="submit"
                variant="secondary"
                size="sm"
                disabled={changingEmail || newEmail.trim() === detail.email}
                className="whitespace-nowrap"
              >
                {changingEmail ? t("usersPage.detail.email.saving") : t("usersPage.detail.email.save")}
              </Button>
            </div>
          </form>

          <form onSubmit={handleChangePassword} className="space-y-2 border-t border-slate-100 pt-4">
            <div>
              <span className="text-[10px] font-bold uppercase text-slate-500 flex items-center gap-1">
                <KeyRound className="w-3 h-3" /> {t("usersPage.detail.password.sectionTitle")}
              </span>
              <p className="text-[10px] text-slate-400">{t("usersPage.detail.password.sectionDescription")}</p>
            </div>
            <div className="flex gap-2">
              <input
                type="password"
                value={newPassword}
                onChange={(e) => setNewPassword(e.target.value)}
                placeholder={t("usersPage.detail.password.placeholder")}
                className={inputClass}
              />
              <Button type="submit" variant="secondary" size="sm" disabled={changingPassword} className="whitespace-nowrap">
                {changingPassword ? t("usersPage.detail.password.saving") : t("usersPage.detail.password.save")}
              </Button>
            </div>
          </form>

          <div className="border-t border-slate-100 pt-4 flex flex-wrap gap-2">
            <div className="w-full">
              <span className="text-[10px] font-bold uppercase text-slate-500">{t("usersPage.detail.status.sectionTitle")}</span>
              <p className="text-[10px] text-slate-400">{t("usersPage.detail.status.sectionDescription")}</p>
            </div>
            {detail.status !== "ACTIVE" && (
              <Button size="sm" variant="secondary" disabled={changingStatus} onClick={() => handleToggleStatus("ACTIVE")}>
                <Unlock className="w-3.5 h-3.5" /> {t("usersPage.detail.status.unlock")}
              </Button>
            )}
            {detail.status !== "INACTIVE" && (
              <Button size="sm" variant="danger" disabled={changingStatus} onClick={() => handleToggleStatus("INACTIVE")}>
                <Lock className="w-3.5 h-3.5" /> {t("usersPage.detail.status.deactivate")}
              </Button>
            )}
            {detail.status !== "SUSPENDED" && (
              <Button size="sm" variant="danger" disabled={changingStatus} onClick={() => handleToggleStatus("SUSPENDED")}>
                <Lock className="w-3.5 h-3.5" /> {t("usersPage.detail.status.suspend")}
              </Button>
            )}
          </div>

          <div className="border-t border-slate-100 pt-4 space-y-2">
            <div>
              <span className="text-[10px] font-bold uppercase text-slate-500">{t("usersPage.detail.loginHistory.sectionTitle")}</span>
              <p className="text-[10px] text-slate-400">{t("usersPage.detail.loginHistory.sectionDescription")}</p>
            </div>
            {loginHistoryLoading ? (
              <p className="text-xs text-slate-500">{t("usersPage.detail.loginHistory.loading")}</p>
            ) : loginHistory.length === 0 ? (
              <p className="text-xs text-slate-400 italic">{t("usersPage.detail.loginHistory.empty")}</p>
            ) : (
              <>
                <TableContainer>
                  <thead>
                    <tr>
                      <Th>{t("usersPage.detail.loginHistory.columns.time")}</Th>
                      <Th>{t("usersPage.detail.loginHistory.columns.ip")}</Th>
                      <Th>{t("usersPage.detail.loginHistory.columns.device")}</Th>
                      <Th>{t("usersPage.detail.loginHistory.columns.screenResolution")}</Th>
                      <Th>{t("usersPage.detail.loginHistory.columns.language")}</Th>
                      <Th>{t("usersPage.detail.loginHistory.columns.timezone")}</Th>
                      <Th>{t("usersPage.detail.loginHistory.columns.result")}</Th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-100">
                    {loginHistory.map((h, idx) => (
                      <tr key={idx}>
                        <Td className="font-mono whitespace-nowrap">{formatDateTime(h.createdAt, i18n.language)}</Td>
                        <Td className="font-mono">{h.ipAddress}</Td>
                        <Td className="max-w-[160px] truncate" title={h.userAgent ?? undefined}>{h.userAgent ?? "-"}</Td>
                        <Td>{h.screenResolution ?? "-"}</Td>
                        <Td>{h.browserLanguage ?? "-"}</Td>
                        <Td>{h.timezone ?? "-"}</Td>
                        <Td>
                          {h.success ? (
                            <Badge variant="success">{t("usersPage.detail.loginHistory.success")}</Badge>
                          ) : (
                            <Badge variant="danger">
                              {h.failureReason ? t(`usersPage.detail.loginHistory.failureReason.${h.failureReason}`) : t("usersPage.detail.loginHistory.failure")}
                            </Badge>
                          )}
                        </Td>
                      </tr>
                    ))}
                  </tbody>
                </TableContainer>
                {loginHistoryPage + 1 < loginHistoryTotalPages && (
                  <Button
                    size="sm"
                    variant="secondary"
                    disabled={loginHistoryLoadingMore}
                    onClick={() => loadLoginHistory(detail.id, loginHistoryPage + 1)}
                  >
                    {loginHistoryLoadingMore ? t("usersPage.detail.loginHistory.loading") : t("usersPage.detail.loginHistory.loadMore")}
                  </Button>
                )}
              </>
            )}
          </div>
        </div>
      )}

      <Toast message={toastMessage} />
    </Modal>
  );
}
