import React, { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { Info, ShieldQuestion, UserCheck } from "lucide-react";
import { ApiError } from "@/lib/apiClient";
import {
  EffectivePermissionsResponse,
  getEffectivePermissions,
  getUserDetail,
  listPermissions,
  PermissionCatalogItem,
  removeUserPermissionOverride,
  upsertUserPermissionOverride,
  UserDetailResponse,
  UserListItemResponse
} from "../api";
import CreateOverrideForm from "../components/CreateOverrideForm";
import OverridesTable from "../components/OverridesTable";
import UserSearchCombobox from "../components/UserSearchCombobox";
import EmptyState from "@/components/ui/EmptyState";
import { useToast } from "@/lib/useToast";
import Toast from "@/components/ui/Toast";

export default function OverridesPage() {
  const { t } = useTranslation("system-admin-overrides");
  const [selectedUser, setSelectedUser] = useState<UserListItemResponse | null>(null);

  const [permissions, setPermissions] = useState<PermissionCatalogItem[]>([]);
  const [userDetail, setUserDetail] = useState<UserDetailResponse | null>(null);
  const [effective, setEffective] = useState<EffectivePermissionsResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const { message: toastMessage, showToast } = useToast();
  const selectedUserId = selectedUser?.id ?? null;

  useEffect(() => {
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
      .catch((err) => setError(err instanceof ApiError ? err.message : t("overridesPage.loadError")))
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    if (selectedUserId != null) loadSelectedUser(selectedUserId);
    else {
      setUserDetail(null);
      setEffective(null);
    }
  }, [selectedUserId]);

  // Override còn hiệu lực: backend không xóa cứng khi gỡ (chỉ set expiresAt = lúc gỡ) — phải tự lọc phía FE (UC-04 A1).
  const activeOverrides = (userDetail?.permissionOverrides ?? []).filter(
    (ov) => !ov.expiresAt || new Date(ov.expiresAt) > new Date()
  );

  const handleUpsert = async (permissionId: number, overrideType: "GRANT" | "REVOKE", reason: string, expiresAt: string) => {
    if (!selectedUserId) return;
    await upsertUserPermissionOverride(selectedUserId, permissionId, overrideType, reason, expiresAt || undefined);
    loadSelectedUser(selectedUserId);
    showToast(t("overridesPage.upsertToast"));
  };

  const handleRemove = async (permissionId: number) => {
    if (!selectedUserId) return;
    await removeUserPermissionOverride(selectedUserId, permissionId);
    loadSelectedUser(selectedUserId);
    showToast(t("overridesPage.removeToast"));
  };

  return (
    <div className="space-y-6 animate-in fade-in duration-200">
      <div className="bg-gradient-to-r from-orange-50 to-amber-50/50 border border-orange-200/60 p-4 rounded-xl flex items-start gap-3 shadow-sm">
        <Info className="w-5 h-5 text-brand-red shrink-0 mt-0.5" />
        <div className="space-y-1">
          <span className="text-xs font-bold text-brand-red uppercase tracking-wider block">{t("overridesPage.banner.badge")}</span>
          <p className="text-xs text-slate-600 leading-relaxed">{t("overridesPage.banner.description")}</p>
        </div>
      </div>

      <div className="bg-white border border-slate-200 rounded-xl p-4 shadow-sm space-y-3">
        <label className="text-[10px] uppercase font-bold text-slate-500 block">{t("overridesPage.step1Label")}</label>
        <div className="max-w-md">
          <UserSearchCombobox value={selectedUser} onChange={setSelectedUser} placeholder={t("overridesPage.searchPlaceholder")} />
        </div>

        {userDetail && (
          <div className="flex items-center gap-2 bg-emerald-50 border border-emerald-100 text-emerald-700 text-xs font-semibold px-3 py-2 rounded-lg w-fit">
            <UserCheck className="w-4 h-4" />
            {t("overridesPage.selectedUser", { fullName: userDetail.fullName, username: userDetail.username })}
          </div>
        )}
      </div>

      {error && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg">{error}</div>}

      {loading && <p className="text-xs text-slate-500">{t("overridesPage.loading")}</p>}

      {!selectedUserId && !loading && (
        <div className="bg-white border border-slate-200 rounded-xl shadow-soft">
          <EmptyState
            icon={ShieldQuestion}
            title={t("overridesPage.emptyTitle")}
            description={t("overridesPage.emptyDescription")}
          />
        </div>
      )}

      {userDetail && effective && !loading && (
        <>
          <div className="bg-white border border-slate-200 rounded-xl p-4 shadow-sm space-y-2">
            <span className="text-[10px] uppercase font-bold text-slate-500 block">
              {t("overridesPage.effectivePermissions.title", { count: effective.permissions.length })}
            </span>
            <div className="flex flex-wrap gap-1.5">
              {effective.permissions.length === 0 ? (
                <span className="text-xs text-slate-400 italic">{t("overridesPage.effectivePermissions.empty")}</span>
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
