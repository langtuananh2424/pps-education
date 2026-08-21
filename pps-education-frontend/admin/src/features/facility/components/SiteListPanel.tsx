import React from "react";
import { Building2, Plus } from "lucide-react";
import { useTranslation } from "react-i18next";
import { SiteResponse } from "../api";
import Badge, { BadgeVariant } from "@/components/ui/Badge";
import Button from "@/components/ui/Button";
import EmptyState from "@/components/ui/EmptyState";

export const siteStatusVariants: Record<string, BadgeVariant> = { ACTIVE: "success", INACTIVE: "neutral", PENDING: "warning" };

/** Nhãn dịch qua i18next namespace "facility" — xem src/i18n/locales/{vi,en}/facility.json. */
export function siteTypeLabel(t: (key: string) => string, siteType: string): string {
  return t(`siteType.${siteType}`);
}

export function siteStatusLabel(t: (key: string) => string, status: string): string {
  return t(`siteStatus.${status}`);
}

interface SiteListPanelProps {
  sites: SiteResponse[];
  loading: boolean;
  selectedId: number | null;
  onSelect: (id: number) => void;
  onCreate: () => void;
}

export default function SiteListPanel({ sites, loading, selectedId, onSelect, onCreate }: SiteListPanelProps) {
  const { t } = useTranslation("facility");
  return (
    <div className="lg:col-span-2 bg-white rounded-xl border border-slate-200 shadow-soft overflow-hidden flex flex-col h-full">
      <div className="px-5 py-4 border-b border-slate-100 flex items-center justify-between gap-3 bg-slate-50 shrink-0">
        <div className="space-y-0.5">
          <span className="text-xs font-bold text-slate-700 font-display block">{t("siteList.title")}</span>
          <p className="text-[10px] text-slate-400">{t("siteList.subtitle")}</p>
        </div>
        <Button variant="primary" size="sm" onClick={onCreate}>
          <Plus className="w-3.5 h-3.5" />
          {t("siteList.addButton")}
        </Button>
      </div>

      <div className="divide-y divide-slate-100 overflow-y-auto max-h-[620px] lg:max-h-[680px]">
        {loading ? (
          <div className="p-8 text-center text-slate-400 text-xs">{t("siteList.loading")}</div>
        ) : sites.length === 0 ? (
          <EmptyState icon={Building2} title={t("siteList.emptyTitle")} description={t("siteList.emptyDescription")} />
        ) : (
          sites.map((s) => {
            const isSelected = s.id === selectedId;
            return (
              <button
                key={s.id}
                onClick={() => onSelect(s.id)}
                className={`w-full text-left p-4 flex items-center justify-between gap-4 transition-all cursor-pointer border-l-4 ${
                  isSelected ? "bg-slate-50/90 border-brand-orange" : "hover:bg-slate-50/40 border-transparent"
                }`}
              >
                <div className="flex items-start gap-3">
                  <div className={`w-9 h-9 rounded-full flex items-center justify-center font-bold text-sm shrink-0 shadow-sm ${isSelected ? "bg-brand-gradient text-white" : "bg-slate-100 text-slate-700"}`}>
                    <Building2 className="w-4 h-4" />
                  </div>
                  <div>
                    <div className="flex items-center gap-1.5 flex-wrap">
                      <h4 className="text-xs font-bold text-slate-900">{s.name}</h4>
                      <Badge variant={siteStatusVariants[s.status]}>{siteStatusLabel(t, s.status)}</Badge>
                    </div>
                    <div className="flex items-center gap-1.5 text-[10px] text-slate-400 mt-1">
                      <span className="font-mono">{s.code}</span>
                      <span>•</span>
                      <span className="text-brand-orange font-bold">{siteTypeLabel(t, s.siteType)}</span>
                    </div>
                  </div>
                </div>
              </button>
            );
          })
        )}
      </div>
    </div>
  );
}
