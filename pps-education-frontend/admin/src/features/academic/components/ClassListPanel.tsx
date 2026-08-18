import React, { useEffect, useState } from "react";
import { Download, GraduationCap, Plus, Search } from "lucide-react";
import { useTranslation } from "react-i18next";
import Badge, { BadgeVariant } from "@/components/ui/Badge";
import Button from "@/components/ui/Button";
import EmptyState from "@/components/ui/EmptyState";
import Select from "@/components/ui/Select";
import { buildXlsxTemplateBlob, downloadBlob } from "@/lib/xlsxTemplate";
import { AcademicYearResponse, listAcademicYears, type ClassResponse } from "../api";

/** Nhãn trạng thái lớp dịch qua i18next namespace "academic-classes" (key `enums.classStatus.<status>`) —
 * dùng `classStatusLabel(t, status)` thay vì tra map tĩnh cũ, để tự đổi theo ngôn ngữ đang chọn. Dùng chung
 * ở ClassListPanel.tsx và ClassDetailPanel.tsx (cùng import từ đây, khớp pattern roleLabel ở constants/roles.ts). */
export function classStatusLabel(t: (key: string, options?: Record<string, unknown>) => string, status: ClassResponse["status"]): string {
  return t(`enums.classStatus.${status}`, { defaultValue: status });
}

/** Danh sách đủ 5 giá trị enum trạng thái lớp, dùng để dựng option cho <Select> (thay Object.entries(classStatusLabels) cũ). */
export const classStatusValues: ClassResponse["status"][] = ["PLANNED", "OPEN_ENROLLMENT", "IN_PROGRESS", "COMPLETED", "CANCELLED"];

export const classStatusVariants: Record<ClassResponse["status"], BadgeVariant> = {
  PLANNED: "neutral",
  OPEN_ENROLLMENT: "info",
  IN_PROGRESS: "success",
  COMPLETED: "brand",
  CANCELLED: "danger"
};

interface ClassListPanelProps {
  classes: ClassResponse[];
  loading: boolean;
  selectedId: number | null;
  onSelect: (id: number) => void;
  onCreate: () => void;
  query: string;
  onQueryChange: (q: string) => void;
  canManage: boolean;
  academicYearFilter: string;
  onAcademicYearFilterChange: (id: string) => void;
}

export default function ClassListPanel({
  classes,
  loading,
  selectedId,
  onSelect,
  onCreate,
  query,
  onQueryChange,
  canManage,
  academicYearFilter,
  onAcademicYearFilterChange
}: ClassListPanelProps) {
  const { t } = useTranslation("academic-classes");
  const [academicYears, setAcademicYears] = useState<AcademicYearResponse[]>([]);
  useEffect(() => {
    listAcademicYears().then(setAcademicYears).catch(() => undefined);
  }, []);

  /** Xuất đúng danh sách lớp đang hiển thị (đã áp bộ lọc điểm trường/năm học/tìm kiếm ở trên) ra Excel. */
  const handleExport = () => {
    const headers = [
      t("classList.exportHeaders.classCode"),
      t("classList.exportHeaders.name"),
      t("classList.exportHeaders.site"),
      t("classList.exportHeaders.classType"),
      t("classList.exportHeaders.curriculum"),
      t("classList.exportHeaders.maxStudents"),
      t("classList.exportHeaders.minStudents"),
      t("classList.exportHeaders.startDate"),
      t("classList.exportHeaders.endDate"),
      t("classList.exportHeaders.academicYear"),
      t("classList.exportHeaders.status")
    ];
    const rows = classes.map((c) => [
      c.classCode,
      c.name,
      c.siteName,
      c.classType === "LINKED" ? t("enums.classType.LINKED") : t("enums.classType.OPEN"),
      c.curriculumCode,
      String(c.maxStudents),
      c.minStudents != null ? String(c.minStudents) : "",
      c.startDate,
      c.endDate ?? "",
      c.academicYear ?? "",
      classStatusLabel(t, c.status)
    ]);
    const blob = buildXlsxTemplateBlob(headers, rows);
    downloadBlob(blob, `${t("classList.exportFileName")}-${new Date().toISOString().slice(0, 10)}.xlsx`);
  };

  return (
    <div className="lg:col-span-2 bg-white rounded-xl border border-slate-200 shadow-soft overflow-hidden flex flex-col h-full">
      <div className="px-5 py-4 border-b border-slate-100 flex items-center justify-between gap-3 bg-slate-50 shrink-0">
        <div className="space-y-0.5">
          <span className="text-xs font-bold text-slate-700 font-display block">{t("classList.title")}</span>
          <p className="text-[10px] text-slate-400">{t("classList.subtitle")}</p>
        </div>
        <div className="flex items-center gap-2">
          <button
            type="button"
            onClick={handleExport}
            disabled={classes.length === 0}
            title={t("classList.exportButtonTitle")}
            className="flex items-center gap-1.5 border border-dashed border-slate-300 rounded-lg px-2.5 py-1.5 text-[11px] font-semibold text-slate-600 hover:bg-white disabled:opacity-50"
          >
            <Download className="w-3.5 h-3.5" />
            {t("classList.exportButton")}
          </button>
          {canManage && (
            <Button variant="primary" size="sm" onClick={onCreate}>
              <Plus className="w-3.5 h-3.5" />
              {t("classList.addButton")}
            </Button>
          )}
        </div>
      </div>

      <div className="px-4 py-3 border-b border-slate-100 shrink-0 space-y-2">
        <div className="relative">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-3.5 h-3.5 text-slate-400" />
          <input
            value={query}
            onChange={(e) => onQueryChange(e.target.value)}
            placeholder={t("classList.searchPlaceholder")}
            className="w-full bg-slate-50 border border-slate-200 text-xs pl-8 pr-3 py-2 rounded-lg focus:outline-none"
          />
        </div>
        <Select
          value={academicYearFilter}
          onChange={(e) => onAcademicYearFilterChange(e.target.value)}
          className="w-full bg-slate-50 border border-slate-200 text-xs px-3 py-2 rounded-lg focus:outline-none"
        >
          <option value="">{t("classList.allAcademicYears")}</option>
          {academicYears.map((y) => (
            <option key={y.id} value={y.id}>
              {y.code} — {y.name}
            </option>
          ))}
        </Select>
      </div>

      <div className="divide-y divide-slate-100 overflow-y-auto max-h-[560px] lg:max-h-[620px]">
        {loading ? (
          <div className="p-8 text-center text-slate-400 text-xs">{t("common.loading")}</div>
        ) : classes.length === 0 ? (
          <EmptyState
            icon={GraduationCap}
            title={t("classList.emptyTitle")}
            description={canManage ? t("classList.emptyDescriptionManage") : t("classList.emptyDescriptionNoManage")}
          />
        ) : (
          classes.map((c) => {
            const isSelected = c.id === selectedId;
            return (
              <button
                key={c.id}
                onClick={() => onSelect(c.id)}
                className={`w-full text-left p-4 flex items-start justify-between gap-3 transition-all cursor-pointer border-l-4 ${
                  isSelected ? "bg-slate-50/90 border-brand-orange" : "hover:bg-slate-50/40 border-transparent"
                }`}
              >
                <div>
                  <div className="flex items-center gap-2 flex-wrap">
                    <span className="text-[10px] font-mono font-bold uppercase text-brand-red bg-orange-50 border border-orange-100 px-1.5 py-0.5 rounded">
                      {c.classCode}
                    </span>
                    <Badge variant={classStatusVariants[c.status]}>{classStatusLabel(t, c.status)}</Badge>
                  </div>
                  <h4 className="text-xs font-bold text-slate-900 mt-1.5">{c.name}</h4>
                  <p className="text-[10px] text-slate-400 mt-1">
                    {c.siteName} · {c.classType === "LINKED" ? t("enums.classType.LINKED") : t("enums.classType.OPEN")}
                  </p>
                </div>
              </button>
            );
          })
        )}
      </div>
    </div>
  );
}
