import React, { useEffect, useMemo, useState } from "react";
import { Search, X } from "lucide-react";
import { ApiError } from "@/lib/apiClient";
import {
  listPermissions,
  listRoles,
  PermissionAuditLogResponse,
  PermissionCatalogItem,
  RoleResponse,
  searchAuditLogs,
  searchUsers,
  UserListItemResponse
} from "../api";
import TableContainer, { Td, Th } from "@/components/ui/TableContainer";
import Badge, { BadgeVariant } from "@/components/ui/Badge";
import Button from "@/components/ui/Button";
import EmptyState from "@/components/ui/EmptyState";
import DatePicker from "@/components/ui/DatePicker";
import Select from "@/components/ui/Select";

const PAGE_SIZE_OPTIONS = [10, 20, 50, 100];
const TODAY_ISO = new Date().toISOString().slice(0, 10);

/** Dùng cho dropdown lọc "Hành động" — không đổi (vẫn đúng 4 giá trị enum Action thật của backend). */
const actionLabels: Record<string, string> = {
  ROLE_GRANTED: "Cấp vai trò",
  ROLE_REVOKED: "Thu hồi vai trò",
  PERM_OVERRIDE_ADDED: "Cấp ngoại lệ",
  PERM_OVERRIDE_REMOVED: "Hủy ngoại lệ"
};

/**
 * Nhãn hiện trong bảng — với PERM_OVERRIDE_ADDED, backend nay đã ghi kèm
 * `details.overrideType` (GRANT/REVOKE) nên hiện đúng "Cấp thêm quyền"/
 * "Tước bỏ quyền" thay vì "Cấp ngoại lệ" chung chung (dễ hiểu lầm khi
 * override thực chất đang TƯỚC quyền). Log cũ trước khi backend vá (details
 * = null) vẫn fallback về nhãn chung — không tự suy đoán loại override.
 */
function getActionDisplay(log: PermissionAuditLogResponse): { label: string; variant: BadgeVariant } {
  const overrideType = (log.details as { overrideType?: string } | null)?.overrideType;

  if (log.action === "PERM_OVERRIDE_ADDED") {
    if (overrideType === "GRANT") return { label: "Cấp thêm quyền", variant: "success" };
    if (overrideType === "REVOKE") return { label: "Tước bỏ quyền", variant: "danger" };
    return { label: actionLabels.PERM_OVERRIDE_ADDED, variant: "success" };
  }
  if (log.action === "ROLE_GRANTED") return { label: actionLabels.ROLE_GRANTED, variant: "success" };
  return { label: actionLabels[log.action] ?? log.action, variant: "danger" };
}

const inputClass = "w-full bg-slate-50 border border-slate-200 text-sm p-2.5 rounded-lg focus:outline-none";
const labelClass = "text-sm uppercase font-bold text-slate-500 block mb-1";

export default function AuditLogPage() {
  const [allUsers, setAllUsers] = useState<UserListItemResponse[]>([]);
  const [allRoles, setAllRoles] = useState<RoleResponse[]>([]);
  const [allPermissions, setAllPermissions] = useState<PermissionCatalogItem[]>([]);

  const [rows, setRows] = useState<PermissionAuditLogResponse[]>([]);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(20);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const [actorUser, setActorUser] = useState<UserListItemResponse | null>(null);
  const [targetUser, setTargetUser] = useState<UserListItemResponse | null>(null);
  const [action, setAction] = useState("");
  const [fromDate, setFromDate] = useState("");
  const [toDate, setToDate] = useState("");

  useEffect(() => {
    searchUsers({}, 0, 1000).then((res) => setAllUsers(res.content)).catch(() => undefined);
    listRoles().then(setAllRoles).catch(() => undefined);
    listPermissions().then(setAllPermissions).catch(() => undefined);
  }, []);

  const usersById = useMemo(() => new Map(allUsers.map((u) => [u.id, u])), [allUsers]);
  const rolesById = useMemo(() => new Map(allRoles.map((r) => [r.id, r])), [allRoles]);
  const permissionsById = useMemo(() => new Map(allPermissions.map((p) => [p.id, p])), [allPermissions]);

  const loadLogs = () => {
    setLoading(true);
    setError(null);
    searchAuditLogs(
      {
        actorUserId: actorUser?.id,
        targetUserId: targetUser?.id,
        action: action || undefined,
        fromDate: fromDate ? new Date(`${fromDate}T00:00:00`).toISOString() : undefined,
        toDate: toDate ? new Date(`${toDate}T23:59:59`).toISOString() : undefined
      },
      page,
      pageSize
    )
      .then((res) => {
        setRows(res.content);
        setTotalPages(res.totalPages);
        setTotalElements(res.totalElements);
      })
      .catch((err) => setError(err instanceof ApiError ? err.message : "Không tải được nhật ký."))
      .finally(() => setLoading(false));
  };

  useEffect(loadLogs, [page, pageSize]);

  const handleSearch = (e: React.FormEvent) => {
    e.preventDefault();
    setPage(0);
    loadLogs();
  };

  const handleClearFilters = () => {
    setActorUser(null);
    setTargetUser(null);
    setAction("");
    setFromDate("");
    setToDate("");
    setPage(0);
    setTimeout(loadLogs, 0);
  };

  return (
    <div className="space-y-4 animate-in fade-in duration-200">
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-3">
        <div>
          <h2 className="text-sm font-bold text-slate-800 uppercase tracking-wider block">Nhật ký thay đổi quyền</h2>
          <p className="text-sm text-slate-400 mt-0.5">Truy vết ai gán/thu hồi vai trò, cấp/tước ngoại lệ quyền, cho ai, khi nào.</p>
        </div>
      </div>

      <form onSubmit={handleSearch} className="bg-white p-4 rounded-xl border border-slate-200 shadow-soft grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-3">
        <UserPicker label="Người thực hiện" value={actorUser} onChange={setActorUser} candidates={allUsers} />
        <UserPicker label="Đối tượng bị ảnh hưởng" value={targetUser} onChange={setTargetUser} candidates={allUsers} />
        <div>
          <label className={labelClass}>Hành động</label>
          <Select value={action} onChange={(e) => setAction(e.target.value)} className={inputClass}>
            <option value="">-- Tất cả --</option>
            {Object.entries(actionLabels).map(([value, label]) => (
              <option key={value} value={value}>
                {label}
              </option>
            ))}
          </Select>
        </div>
        <div className="grid grid-cols-2 gap-2">
          <div>
            <label className={labelClass}>Từ ngày</label>
            <DatePicker value={fromDate} onChange={setFromDate} max={toDate || TODAY_ISO} />
          </div>
          <div>
            <label className={labelClass}>Đến ngày</label>
            <DatePicker value={toDate} onChange={setToDate} min={fromDate || undefined} max={TODAY_ISO} />
          </div>
        </div>
        <div className="lg:col-span-4 flex justify-end gap-2">
          <Button type="button" variant="secondary" onClick={handleClearFilters}>
            Xóa bộ lọc
          </Button>
          <Button type="submit" variant="dark">
            Tìm kiếm
          </Button>
        </div>
      </form>

      <div className="bg-white rounded-xl border border-slate-200 shadow-soft overflow-hidden">
        {error && <div className="p-4 text-sm text-rose-600 bg-rose-50 border-b border-rose-100">{error}</div>}
        {!loading && rows.length === 0 && !error ? (
          <EmptyState icon={Search} title="Không tìm thấy bản ghi phù hợp" description="Thử nới lỏng bộ lọc (A1 — UC-05)." />
        ) : (
          <TableContainer className="rounded-none border-0">
            <thead>
              <tr>
                <Th>Thời gian</Th>
                <Th>Người thực hiện</Th>
                <Th>Đối tượng chịu tác động</Th>
                <Th>Tác vụ</Th>
                <Th>Thông tin chi tiết</Th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {rows.map((log) => {
                const actor = usersById.get(log.actorUserId);
                const target = usersById.get(log.targetUserId);
                const role = log.targetRoleId != null ? rolesById.get(log.targetRoleId) : undefined;
                const permission = log.targetPermissionId != null ? permissionsById.get(log.targetPermissionId) : undefined;

                const actionDisplay = getActionDisplay(log);
                const detailReason = (log.details as { reason?: string } | null)?.reason;

                return (
                  <tr key={log.id} className="hover:bg-slate-50/50 transition-colors">
                    <Td className="font-mono whitespace-nowrap">{new Date(log.createdAt).toLocaleString("vi-VN")}</Td>
                    <Td className="font-bold text-slate-800">{actor?.fullName ?? `#${log.actorUserId}`}</Td>
                    <Td className="font-semibold">{target?.fullName ?? `#${log.targetUserId}`}</Td>
                    <Td>
                      <Badge variant={actionDisplay.variant}>{actionDisplay.label}</Badge>
                    </Td>
                    <Td className="text-sm leading-relaxed max-w-lg">
                      {role && <code className="font-mono font-bold text-brand-red">{role.code}</code>}
                      {permission && <code className="font-mono font-bold text-brand-red">{permission.code}</code>}
                      {detailReason && <span className="text-slate-500 italic ml-2">— {detailReason}</span>}
                      {log.ipAddress && <span className="text-slate-400 ml-2">IP: {log.ipAddress}</span>}
                    </Td>
                  </tr>
                );
              })}
            </tbody>
          </TableContainer>
        )}

        {rows.length > 0 && (
          <div className="flex flex-col sm:flex-row items-center justify-between gap-2 px-4 py-3 border-t border-slate-100 text-sm text-slate-500">
            <div className="flex items-center gap-2">
              <span>Tổng {totalElements} bản ghi</span>
              <span className="text-slate-300">|</span>
              <label className="flex items-center gap-1.5">
                Dòng/trang:
                <Select
                  value={pageSize}
                  onChange={(e) => {
                    setPageSize(Number(e.target.value));
                    setPage(0);
                  }}
                  className="bg-slate-50 border border-slate-200 text-sm px-1.5 py-1 rounded-md focus:outline-none cursor-pointer"
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
    </div>
  );
}

function UserPicker({
  label,
  value,
  onChange,
  candidates
}: {
  label: string;
  value: UserListItemResponse | null;
  onChange: (u: UserListItemResponse | null) => void;
  candidates: UserListItemResponse[];
}) {
  const [query, setQuery] = useState("");

  const matches = useMemo(() => {
    const q = query.trim().toLowerCase();
    if (!q) return [];
    return candidates
      .filter((u) => u.username.toLowerCase().includes(q) || u.fullName.toLowerCase().includes(q))
      .slice(0, 6);
  }, [candidates, query]);

  if (value) {
    return (
      <div>
        <label className={labelClass}>{label}</label>
        <div className="flex items-center justify-between bg-slate-50 border border-slate-200 text-sm p-2.5 rounded-lg">
          <span className="truncate">
            {value.fullName} ({value.username})
          </span>
          <button type="button" onClick={() => onChange(null)} className="text-slate-400 hover:text-rose-600 shrink-0 ml-2">
            <X className="w-3.5 h-3.5" />
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="relative">
      <label className={labelClass}>{label}</label>
      <input value={query} onChange={(e) => setQuery(e.target.value)} placeholder="Tìm username/họ tên..." className={inputClass} />
      {matches.length > 0 && (
        <div className="absolute z-10 mt-1 w-full bg-white border border-slate-200 rounded-lg shadow-lg divide-y divide-slate-100 max-h-56 overflow-y-auto">
          {matches.map((u) => (
            <button
              key={u.id}
              type="button"
              onClick={() => {
                onChange(u);
                setQuery("");
              }}
              className="w-full text-left px-3 py-2 hover:bg-slate-50 text-sm"
            >
              {u.fullName} <span className="text-slate-400">({u.username})</span>
            </button>
          ))}
        </div>
      )}
    </div>
  );
}
