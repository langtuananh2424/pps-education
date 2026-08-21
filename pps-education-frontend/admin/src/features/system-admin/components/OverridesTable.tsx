import React, { useState } from "react";
import { useTranslation } from "react-i18next";
import { Calendar, X } from "lucide-react";
import { UserPermissionOverrideSummary } from "../api";
import TableContainer, { Td, Th } from "@/components/ui/TableContainer";
import Badge from "@/components/ui/Badge";
import { formatDateLong } from "@/lib/i18nFormat";

interface OverridesTableProps {
  overrides: UserPermissionOverrideSummary[];
  onRemove: (permissionId: number) => Promise<void>;
}

export default function OverridesTable({ overrides, onRemove }: OverridesTableProps) {
  const { t, i18n } = useTranslation("system-admin-overrides");
  const [removingId, setRemovingId] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);

  const handleRemove = async (permissionId: number) => {
    setRemovingId(permissionId);
    setError(null);
    try {
      await onRemove(permissionId);
    } catch (err) {
      setError(err instanceof Error ? err.message : t("overridesTable.removeError"));
    } finally {
      setRemovingId(null);
    }
  };

  return (
    <div className="lg:col-span-3 bg-white border border-slate-200 rounded-xl overflow-hidden shadow-sm h-fit">
      <div className="p-4 border-b bg-slate-50/20">
        <span className="text-xs font-bold text-slate-800 uppercase tracking-wider block">{t("overridesTable.title", { count: overrides.length })}</span>
      </div>
      {error && <div className="text-[11px] text-rose-600 bg-rose-50 border-b border-rose-100 p-2.5">{error}</div>}
      {overrides.length === 0 ? (
        <p className="text-xs text-slate-400 italic p-6 text-center">{t("overridesTable.empty")}</p>
      ) : (
        <TableContainer className="rounded-none border-0">
          <thead>
            <tr>
              <Th>{t("overridesTable.columns.permission")}</Th>
              <Th>{t("overridesTable.columns.type")}</Th>
              <Th>{t("overridesTable.columns.reason")}</Th>
              <Th>{t("overridesTable.columns.expiresAt")}</Th>
              <Th className="text-center">{t("overridesTable.columns.remove")}</Th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {overrides.map((ov) => (
              <tr key={ov.permissionId} className="hover:bg-slate-50/50 transition-colors">
                <Td className="font-mono font-bold text-brand-red">{ov.permissionCode}</Td>
                <Td>
                  <Badge variant={ov.overrideType === "GRANT" ? "success" : "danger"}>
                    {ov.overrideType === "GRANT" ? t("overridesTable.overrideType.GRANT") : t("overridesTable.overrideType.REVOKE")}
                  </Badge>
                </Td>
                <Td className="text-slate-500 italic max-w-xs truncate" title={ov.reason}>
                  {ov.reason}
                </Td>
                <Td className="font-mono text-[10px] text-slate-500">
                  {ov.expiresAt ? (
                    <div className="flex items-center gap-1">
                      <Calendar className="w-3.5 h-3.5 text-slate-400 shrink-0" />
                      <span>{formatDateLong(new Date(ov.expiresAt), i18n.language)}</span>
                    </div>
                  ) : (
                    t("overridesTable.neverExpires")
                  )}
                </Td>
                <Td className="text-center">
                  <button
                    onClick={() => handleRemove(ov.permissionId)}
                    disabled={removingId === ov.permissionId}
                    className="p-1 rounded text-slate-400 hover:text-rose-600 hover:bg-rose-50 transition-colors disabled:opacity-50"
                    title={t("overridesTable.removeTitle")}
                  >
                    <X className="w-4 h-4" />
                  </button>
                </Td>
              </tr>
            ))}
          </tbody>
        </TableContainer>
      )}
    </div>
  );
}
