import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { LogOut, Monitor, Smartphone } from "lucide-react";
import TableContainer, { Td, Th } from "@/components/ui/TableContainer";
import Button from "@/components/ui/Button";
import { useDialog } from "@/components/ui/DialogProvider";
import { useApp } from "@/context/AppContext";
import { ApiError } from "@/lib/apiClient";
import { formatDateTime } from "@/lib/i18nFormat";
import { getUserSessions, revokeAllUserSessions, revokeUserSession, UserSessionResponse } from "../api";
import FloatingError from "@/components/ui/FloatingError";

/** Rút gọn User-Agent thành "Chrome · Windows" — chỉ nhận diện các trình duyệt/HĐH phổ biến, còn lại giữ nguyên chuỗi gốc ở tooltip. */
function describeDevice(userAgent: string | null): { label: string; mobile: boolean } {
  if (!userAgent) return { label: "-", mobile: false };
  const ua = userAgent;
  const browser = /Edg\//.test(ua)
    ? "Edge"
    : /OPR\//.test(ua)
      ? "Opera"
      : /CriOS|Chrome\//.test(ua)
        ? "Chrome"
        : /FxiOS|Firefox\//.test(ua)
          ? "Firefox"
          : /Safari\//.test(ua)
            ? "Safari"
            : null;
  const os = /iPhone|iPad|iPod/.test(ua)
    ? "iOS"
    : /Android/.test(ua)
      ? "Android"
      : /Windows/.test(ua)
        ? "Windows"
        : /Mac OS X|Macintosh/.test(ua)
          ? "macOS"
          : /Linux/.test(ua)
            ? "Linux"
            : null;
  const mobile = /Mobile|iPhone|iPad|Android/.test(ua);
  const label = [browser, os].filter(Boolean).join(" · ");
  return { label: label || ua, mobile };
}

/**
 * UC-44 bổ sung ngoài SDD gốc (đã xác nhận với người dùng 2026-09-29) — Quản lý người dùng → Xem/Sửa →
 * Thiết bị đang đăng nhập: danh sách refresh token còn hiệu lực và nút gỡ từng thiết bị/tất cả (cần quyền
 * user.update). Thiết bị bị gỡ chỉ bị đăng xuất thật ở lần gọi API kế tiếp (tối đa ~15 phút, TTL access
 * token) — xem UserSessionService phía backend.
 */
export default function UserSessionsSection({ userId, username }: { userId: number; username: string }) {
  const { t, i18n } = useTranslation("system-admin-users");
  const { hasPermission } = useApp();
  const { confirmDialog } = useDialog();
  const [sessions, setSessions] = useState<UserSessionResponse[]>([]);
  const [loading, setLoading] = useState(false);
  const [busyId, setBusyId] = useState<number | "all" | null>(null);
  const [error, setError] = useState<string | null>(null);
  const canRevoke = hasPermission("user.update");

  const load = () => {
    setLoading(true);
    setError(null);
    getUserSessions(userId)
      .then(setSessions)
      .catch((err) => setError(err instanceof ApiError ? err.message : t("usersPage.detail.sessions.loadError")))
      .finally(() => setLoading(false));
  };

  useEffect(load, [userId]);

  const revoke = async (target: UserSessionResponse | "all") => {
    const message =
      target === "all"
        ? t("usersPage.detail.sessions.confirmRevokeAll", { username })
        : t("usersPage.detail.sessions.confirmRevokeOne", {
            username,
            device: describeDevice(target.deviceInfo).label,
            ip: target.ipAddress ?? "-"
          });
    if (!(await confirmDialog(message, { danger: true, confirmLabel: t("usersPage.detail.sessions.revoke") }))) return;
    setBusyId(target === "all" ? "all" : target.id);
    setError(null);
    try {
      if (target === "all") await revokeAllUserSessions(userId);
      else await revokeUserSession(userId, target.id);
      load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t("usersPage.detail.sessions.revokeError"));
    } finally {
      setBusyId(null);
    }
  };

  return (
    <div className="border-t border-slate-100 pt-4 space-y-2">
      <div className="flex items-start justify-between gap-2">
        <div>
          <span className="text-[10px] font-bold uppercase text-slate-500">
            {t("usersPage.detail.sessions.sectionTitle", { count: sessions.length })}
          </span>
          <p className="text-[10px] text-slate-400">{t("usersPage.detail.sessions.sectionDescription")}</p>
        </div>
        {canRevoke && sessions.length > 1 && (
          <Button size="sm" variant="danger" disabled={busyId !== null} onClick={() => revoke("all")} className="whitespace-nowrap">
            <LogOut className="w-3.5 h-3.5" /> {t("usersPage.detail.sessions.revokeAll")}
          </Button>
        )}
      </div>
      <FloatingError message={error} onClose={() => setError(null)} />
      {loading ? (
        <p className="text-xs text-slate-500">{t("usersPage.detail.sessions.loading")}</p>
      ) : sessions.length === 0 ? (
        <p className="text-xs text-slate-400 italic">{t("usersPage.detail.sessions.empty")}</p>
      ) : (
        <TableContainer>
          <thead>
            <tr>
              <Th>{t("usersPage.detail.sessions.columns.device")}</Th>
              <Th>{t("usersPage.detail.sessions.columns.ip")}</Th>
              <Th>{t("usersPage.detail.sessions.columns.lastActive")}</Th>
              <Th>{t("usersPage.detail.sessions.columns.expires")}</Th>
              {canRevoke && <Th />}
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {sessions.map((s) => {
              const device = describeDevice(s.deviceInfo);
              const Icon = device.mobile ? Smartphone : Monitor;
              return (
                <tr key={s.id}>
                  <Td className="max-w-[200px]" title={s.deviceInfo ?? undefined}>
                    <span className="flex items-center gap-1.5 truncate">
                      <Icon className="w-3.5 h-3.5 shrink-0 text-slate-400" /> {device.label}
                    </span>
                  </Td>
                  <Td className="font-mono">{s.ipAddress ?? "-"}</Td>
                  <Td className="font-mono whitespace-nowrap">{formatDateTime(s.lastActiveAt, i18n.language)}</Td>
                  <Td className="font-mono whitespace-nowrap">{formatDateTime(s.expiresAt, i18n.language)}</Td>
                  {canRevoke && (
                    <Td className="text-right">
                      <Button size="sm" variant="ghost" disabled={busyId !== null} onClick={() => revoke(s)} className="whitespace-nowrap text-rose-600">
                        <LogOut className="w-3.5 h-3.5" /> {t("usersPage.detail.sessions.revoke")}
                      </Button>
                    </Td>
                  )}
                </tr>
              );
            })}
          </tbody>
        </TableContainer>
      )}
    </div>
  );
}
