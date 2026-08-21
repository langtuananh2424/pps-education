import React, { useEffect, useState } from "react";
import { MapPin, Plus } from "lucide-react";
import { useTranslation } from "react-i18next";
import { ApiError } from "@/lib/apiClient";
import { listSites, SiteResponse } from "@/features/facility/api";
import { Badge, Button, TableContainer, Td, Th } from "@/components/ui";
import Toast from "@/components/ui/Toast";
import { useToast } from "@/lib/useToast";
import AttendanceSiteFormModal from "../components/AttendanceSiteFormModal";

const statusVariant: Record<SiteResponse["status"], "success" | "neutral" | "warning"> = {
  ACTIVE: "success",
  INACTIVE: "neutral",
  PENDING: "warning"
};

/**
 * Setup địa điểm chấm công (toạ độ GPS của Site — UC-09 A2) — bổ sung ngoài
 * SDD gốc, xác nhận với người dùng 2026-08-12. Tái dùng Site CRUD (UC-36):
 * mọi điểm trường ở đây cũng chính là điểm trường quản lý ở
 * Facility > Điểm trường & HĐ, chỉ khác góc nhìn (tập trung vào GPS).
 */
export default function AttendanceSitesPage() {
  const { t } = useTranslation("hrm-attendance");
  const [sites, setSites] = useState<SiteResponse[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [formOpen, setFormOpen] = useState(false);
  const [editingSite, setEditingSite] = useState<SiteResponse | null>(null);
  const { message: toastMessage, showToast } = useToast();

  const load = () => {
    setLoading(true);
    setError(null);
    listSites()
      .then(setSites)
      .catch((err) => setError(err instanceof ApiError ? err.message : t("attendanceSitesPage.loadError")))
      .finally(() => setLoading(false));
  };

  useEffect(load, []);

  return (
    <div className="space-y-6">
      <div className="border-b border-slate-200 pb-4 flex items-center justify-between gap-3">
        <div>
          <h1 className="text-xl font-bold font-display tracking-tight text-slate-900">{t("attendanceSitesPage.title")}</h1>
          <p className="text-xs text-slate-500 mt-1">{t("attendanceSitesPage.description")}</p>
        </div>
        <Button
          variant="primary"
          onClick={() => {
            setEditingSite(null);
            setFormOpen(true);
          }}
        >
          <Plus className="w-3.5 h-3.5" />
          {t("attendanceSitesPage.addButton")}
        </Button>
      </div>

      {error && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg">{error}</div>}

      <TableContainer>
        <thead>
          <tr>
            <Th>{t("attendanceSitesPage.columns.code")}</Th>
            <Th>{t("attendanceSitesPage.columns.name")}</Th>
            <Th>{t("attendanceSitesPage.columns.type")}</Th>
            <Th>{t("attendanceSitesPage.columns.address")}</Th>
            <Th>{t("attendanceSitesPage.columns.gps")}</Th>
            <Th>{t("attendanceSitesPage.columns.status")}</Th>
            <Th />
          </tr>
        </thead>
        <tbody className="divide-y divide-slate-100">
          {loading ? (
            <tr>
              <Td colSpan={7} className="text-center text-slate-400">
                {t("attendanceSitesPage.loading")}
              </Td>
            </tr>
          ) : sites.length === 0 ? (
            <tr>
              <Td colSpan={7} className="text-center text-slate-400">
                {t("attendanceSitesPage.empty")}
              </Td>
            </tr>
          ) : (
            sites.map((s) => (
              <tr
                key={s.id}
                className="hover:bg-slate-50/50 transition-colors cursor-pointer"
                onClick={() => {
                  setEditingSite(s);
                  setFormOpen(true);
                }}
              >
                <Td className="font-mono font-bold text-slate-700">{s.code}</Td>
                <Td className="font-bold text-slate-800">{s.name}</Td>
                <Td>{t(`attendanceSitesPage.siteType.${s.siteType}`)}</Td>
                <Td className="text-slate-500">{s.address ?? "—"}</Td>
                <Td>
                  {s.latitude != null && s.longitude != null ? (
                    <span className="font-mono text-[11px] text-slate-600 flex items-center gap-1">
                      <MapPin className="w-3 h-3 text-emerald-500 shrink-0" />
                      {s.latitude.toFixed(5)}, {s.longitude.toFixed(5)}
                    </span>
                  ) : (
                    <Badge variant="warning">{t("attendanceSitesPage.notConfigured")}</Badge>
                  )}
                </Td>
                <Td>
                  <Badge variant={statusVariant[s.status]}>{t(`attendanceSitesPage.siteStatus.${s.status}`)}</Badge>
                </Td>
                <Td>
                  <Button
                    size="sm"
                    variant="secondary"
                    onClick={(e) => {
                      e.stopPropagation();
                      setEditingSite(s);
                      setFormOpen(true);
                    }}
                  >
                    {t("attendanceSitesPage.editButton")}
                  </Button>
                </Td>
              </tr>
            ))
          )}
        </tbody>
      </TableContainer>

      {formOpen && (
        <AttendanceSiteFormModal
          site={editingSite}
          onClose={() => setFormOpen(false)}
          onSaved={() => {
            setFormOpen(false);
            load();
            showToast(editingSite ? t("attendanceSitesPage.updatedToast") : t("attendanceSitesPage.createdToast"));
          }}
        />
      )}

      <Toast message={toastMessage} />
    </div>
  );
}
