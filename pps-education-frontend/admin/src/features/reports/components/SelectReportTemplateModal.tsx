import React, { useState, useEffect, useMemo } from "react";
import { useTranslation } from "react-i18next";
import Modal from "../../../components/ui/Modal";
import Button from "../../../components/ui/Button";
import Select from "../../../components/ui/Select";
import { ReportTemplateResponse, AcademicTermResponse, ReportPeriodSelector } from "../../academic/api";
import { Download, FileText, CheckCircle2, AlertCircle, FileType, Filter, Plus, X, CalendarRange } from "lucide-react";

const PERIOD_TEMPLATE_TYPES = new Set(["TRANSCRIPT", "GRADE_REPORT"]);

/** Nhãn dịch qua i18next namespace "reports-templates" — xem src/i18n/locales/{vi,en}/reports-templates.json. */
function evaluationTypeLabel(t: (key: string) => string, evaluationType: string): string {
  return t(`evaluationType.${evaluationType}`);
}

interface PeriodRow {
  label: string;
  academicTermId: number | "";
  evaluationType: "MID_TERM" | "END_TERM" | "";
}

const EMPTY_PERIOD_ROW: PeriodRow = { label: "", academicTermId: "", evaluationType: "" };

interface SelectReportTemplateModalProps {
  open: boolean;
  onClose: () => void;
  title: string;
  description?: string;
  templates: ReportTemplateResponse[];
  /** Danh sách kỳ đánh giá khả dụng — bắt buộc nếu có mẫu TRANSCRIPT/GRADE_REPORT trong `templates`. */
  academicTerms?: AcademicTermResponse[];
  onExport: (templateId: number, outputFormat: "DOCX" | "PDF", periods: ReportPeriodSelector[]) => void;
  exporting?: boolean;
}

export default function SelectReportTemplateModal({
  open,
  onClose,
  title,
  description,
  templates,
  academicTerms = [],
  onExport,
  exporting = false,
}: SelectReportTemplateModalProps) {
  const { t } = useTranslation("reports-templates");
  const [selectedTemplateId, setSelectedTemplateId] = useState<number | null>(null);
  const [selectedFormatFilter, setSelectedFormatFilter] = useState<string>("ALL");
  const [targetFormat, setTargetFormat] = useState<"DOCX" | "PDF">("DOCX");
  const [periodRows, setPeriodRows] = useState<PeriodRow[]>([EMPTY_PERIOD_ROW]);

  // Danh sách các định dạng thực tế có trong templates
  const availableFormats = useMemo(() => {
    const formats = new Set<string>();
    templates.forEach((t) => formats.add(t.fileFormat.toUpperCase()));
    return Array.from(formats);
  }, [templates]);

  // Lọc danh sách mẫu theo định dạng đang chọn
  const filteredTemplates = useMemo(() => {
    if (selectedFormatFilter === "ALL") return templates;
    return templates.filter((t) => t.fileFormat.toUpperCase() === selectedFormatFilter);
  }, [templates, selectedFormatFilter]);

  useEffect(() => {
    if (open && filteredTemplates.length > 0) {
      if (!selectedTemplateId || !filteredTemplates.some((t) => t.id === selectedTemplateId)) {
        const firstTpl = filteredTemplates[0];
        setSelectedTemplateId(firstTpl.id);
        setTargetFormat(firstTpl.fileFormat.toUpperCase() === "PDF" ? "PDF" : "DOCX");
      }
    }
  }, [open, filteredTemplates, selectedTemplateId]);

  useEffect(() => {
    if (!open) setPeriodRows([EMPTY_PERIOD_ROW]);
  }, [open]);

  const handleTemplateSelect = (tpl: ReportTemplateResponse) => {
    setSelectedTemplateId(tpl.id);
    if (tpl.fileFormat.toUpperCase() === "PDF") {
      setTargetFormat("PDF");
    }
  };

  const selectedTemplate = useMemo(
    () => templates.find((t) => t.id === selectedTemplateId),
    [templates, selectedTemplateId]
  );

  const needsPeriods = !!selectedTemplate && PERIOD_TEMPLATE_TYPES.has(selectedTemplate.templateType);

  const updatePeriodRow = (index: number, patch: Partial<PeriodRow>) => {
    setPeriodRows((rows) => rows.map((r, i) => (i === index ? { ...r, ...patch } : r)));
  };
  const addPeriodRow = () => setPeriodRows((rows) => [...rows, EMPTY_PERIOD_ROW]);
  const removePeriodRow = (index: number) => setPeriodRows((rows) => rows.filter((_, i) => i !== index));

  const isPeriodRowComplete = (r: PeriodRow) => !!r.label.trim() && r.academicTermId !== "" && !!r.evaluationType;
  const periodsValid = !needsPeriods || (academicTerms.length > 0 && periodRows.length > 0 && periodRows.every(isPeriodRowComplete));

  const handleConfirm = () => {
    if (!selectedTemplateId || !periodsValid) return;
    const periods: ReportPeriodSelector[] = needsPeriods
      ? periodRows.map((r) => ({
          label: r.label.trim(),
          academicTermId: r.academicTermId as number,
          evaluationType: r.evaluationType as "MID_TERM" | "END_TERM",
        }))
      : [];
    onExport(selectedTemplateId, targetFormat, periods);
  };

  const getFormatBadgeClass = (format: string) => {
    switch (format.toUpperCase()) {
      case "DOCX":
        return "bg-blue-50 text-blue-700 border-blue-200";
      case "PDF":
        return "bg-red-50 text-red-700 border-red-200";
      case "HTML":
        return "bg-emerald-50 text-emerald-700 border-emerald-200";
      default:
        return "bg-slate-50 text-slate-700 border-slate-200";
    }
  };

  return (
    <Modal
      open={open}
      onClose={onClose}
      title={title}
      description={description ?? t("selectTemplateModal.defaultDescription")}
      footer={
        <div className="flex items-center gap-2">
          <Button variant="secondary" onClick={onClose} disabled={exporting}>
            {t("selectTemplateModal.cancel")}
          </Button>
          <Button
            variant="primary"
            disabled={!selectedTemplateId || !periodsValid || exporting}
            onClick={handleConfirm}
            className="flex items-center gap-1.5"
          >
            <Download className="w-4 h-4" />
            {exporting ? t("selectTemplateModal.exporting") : t("selectTemplateModal.exportButton", { format: targetFormat })}
          </Button>
        </div>
      }
    >
      {templates.length === 0 ? (
        <div className="bg-amber-50 border border-amber-200/80 rounded-xl p-4 text-amber-800 text-xs flex items-start gap-2.5">
          <AlertCircle className="w-4 h-4 text-amber-600 shrink-0 mt-0.5" />
          <div>
            <p className="font-semibold">{t("selectTemplateModal.noTemplates.title")}</p>
            <p className="mt-0.5 text-amber-700">
              {t("selectTemplateModal.noTemplates.descPrefix")} <b>{t("selectTemplateModal.noTemplates.descPageLabel")}</b> {t("selectTemplateModal.noTemplates.descSuffix")}
            </p>
          </div>
        </div>
      ) : (
        <div className="space-y-4">
          {/* Bộ chọn lọc định dạng file mẫu */}
          {availableFormats.length > 1 && (
            <div>
              <label className="block text-xs font-semibold text-slate-600 mb-1.5 flex items-center gap-1">
                <Filter className="w-3.5 h-3.5 text-slate-400" /> {t("selectTemplateModal.filterLabel")}
              </label>
              <div className="flex items-center gap-1.5 flex-wrap">
                <button
                  type="button"
                  onClick={() => setSelectedFormatFilter("ALL")}
                  className={`text-xs px-2.5 py-1 rounded-lg border font-medium transition-all ${
                    selectedFormatFilter === "ALL"
                      ? "bg-brand-orange text-white border-brand-orange shadow-xs font-semibold"
                      : "bg-white text-slate-600 border-slate-200 hover:border-slate-300"
                  }`}
                >
                  {t("selectTemplateModal.filterAll", { count: templates.length })}
                </button>
                {availableFormats.map((fmt) => {
                  const count = templates.filter((t) => t.fileFormat.toUpperCase() === fmt).length;
                  return (
                    <button
                      key={fmt}
                      type="button"
                      onClick={() => setSelectedFormatFilter(fmt)}
                      className={`text-xs px-2.5 py-1 rounded-lg border font-medium transition-all flex items-center gap-1 ${
                        selectedFormatFilter === fmt
                          ? "bg-brand-orange text-white border-brand-orange shadow-xs font-semibold"
                          : "bg-white text-slate-600 border-slate-200 hover:border-slate-300"
                      }`}
                    >
                      <span>{fmt}</span>
                      <span className="opacity-75">({count})</span>
                    </button>
                  );
                })}
              </div>
            </div>
          )}

          {/* Danh sách mẫu báo cáo */}
          <div>
            <div className="flex items-center justify-between mb-1.5">
              <label className="block text-xs font-semibold text-slate-700">
                {t("selectTemplateModal.availableTemplatesLabel", { count: filteredTemplates.length })}
              </label>
            </div>
            <div className="space-y-2 max-h-[200px] overflow-y-auto pr-1">
              {filteredTemplates.map((tpl) => {
                const isSelected = selectedTemplateId === tpl.id;
                return (
                  <div
                    key={tpl.id}
                    onClick={() => handleTemplateSelect(tpl)}
                    className={`relative cursor-pointer rounded-xl border p-3.5 transition-all flex items-start gap-3 ${
                      isSelected
                        ? "border-brand-orange bg-orange-50/50 shadow-xs ring-1 ring-brand-orange/30"
                        : "border-slate-200 hover:border-slate-300 hover:bg-slate-50/50"
                    }`}
                  >
                    <div className="mt-0.5 shrink-0">
                      <FileText className={`w-5 h-5 ${isSelected ? "text-brand-orange" : "text-slate-400"}`} />
                    </div>
                    <div className="flex-1 min-w-0">
                      <div className="flex items-center gap-2 flex-wrap">
                        <span className="text-sm font-semibold text-slate-900 truncate">{tpl.name}</span>
                        <span
                          className={`text-[10px] font-bold px-1.5 py-0.5 rounded border ${getFormatBadgeClass(
                            tpl.fileFormat
                          )}`}
                        >
                          {tpl.fileFormat}
                        </span>
                      </div>
                      {tpl.description ? (
                        <p className="text-xs text-slate-500 mt-1 line-clamp-2">{tpl.description}</p>
                      ) : (
                        <p className="text-xs text-slate-400 mt-1 italic">{t("selectTemplateModal.originalFileLabel", { filename: tpl.originalFilename })}</p>
                      )}
                    </div>
                    {isSelected && (
                      <CheckCircle2 className="w-5 h-5 text-brand-orange shrink-0 mt-0.5" />
                    )}
                  </div>
                );
              })}
            </div>
          </div>

          {/* Chọn định dạng file đầu ra */}
          {selectedTemplate && (
            <div className="space-y-2 border-t border-slate-100 pt-3">
              <label className="block text-xs font-semibold text-slate-700 flex items-center gap-1">
                <FileType className="w-3.5 h-3.5 text-brand-orange" /> {t("selectTemplateModal.outputFormatLabel")}
              </label>
              <div className="grid grid-cols-2 gap-2">
                <button
                  type="button"
                  onClick={() => setTargetFormat("DOCX")}
                  className={`p-2.5 rounded-xl border text-left flex items-start gap-2.5 transition-all ${
                    targetFormat === "DOCX"
                      ? "border-brand-orange bg-orange-50/60 ring-1 ring-brand-orange/30 shadow-2xs"
                      : "border-slate-200 hover:bg-slate-50 text-slate-600"
                  }`}
                >
                  <div className="mt-0.5">
                    <div className={`w-3.5 h-3.5 rounded-full border flex items-center justify-center ${
                      targetFormat === "DOCX" ? "border-brand-orange bg-brand-orange" : "border-slate-300 bg-white"
                    }`}>
                      {targetFormat === "DOCX" && <div className="w-1.5 h-1.5 rounded-full bg-white" />}
                    </div>
                  </div>
                  <div className="min-w-0">
                    <p className={`text-xs font-bold ${targetFormat === "DOCX" ? "text-brand-orange" : "text-slate-800"}`}>
                      {t("selectTemplateModal.formatDocxTitle")}
                    </p>
                    <p className="text-[10px] text-slate-500 mt-0.5">{t("selectTemplateModal.formatDocxDesc")}</p>
                  </div>
                </button>

                <button
                  type="button"
                  onClick={() => setTargetFormat("PDF")}
                  className={`p-2.5 rounded-xl border text-left flex items-start gap-2.5 transition-all ${
                    targetFormat === "PDF"
                      ? "border-brand-orange bg-orange-50/60 ring-1 ring-brand-orange/30 shadow-2xs"
                      : "border-slate-200 hover:bg-slate-50 text-slate-600"
                  }`}
                >
                  <div className="mt-0.5">
                    <div className={`w-3.5 h-3.5 rounded-full border flex items-center justify-center ${
                      targetFormat === "PDF" ? "border-brand-orange bg-brand-orange" : "border-slate-300 bg-white"
                    }`}>
                      {targetFormat === "PDF" && <div className="w-1.5 h-1.5 rounded-full bg-white" />}
                    </div>
                  </div>
                  <div className="min-w-0">
                    <p className={`text-xs font-bold ${targetFormat === "PDF" ? "text-brand-orange" : "text-slate-800"}`}>
                      {t("selectTemplateModal.formatPdfTitle")}
                    </p>
                    <p className="text-[10px] text-slate-500 mt-0.5">{t("selectTemplateModal.formatPdfDesc")}</p>
                  </div>
                </button>
              </div>
            </div>
          )}

          {/* Chọn kỳ đánh giá (chỉ TRANSCRIPT/GRADE_REPORT — UC-68 bước 2) */}
          {needsPeriods && (
            <div className="space-y-2 border-t border-slate-100 pt-3">
              <label className="block text-xs font-semibold text-slate-700 flex items-center gap-1">
                <CalendarRange className="w-3.5 h-3.5 text-brand-orange" /> {t("selectTemplateModal.periodsLabel")}
              </label>
              <p className="text-[11px] text-slate-500">
                {t("selectTemplateModal.periodsHintPrefix")}{" "}
                <code>[READING_MID1]</code> {t("selectTemplateModal.periodsHintMiddle")} <code>MID1</code>{t("selectTemplateModal.periodsHintSuffix")}
              </p>
              {academicTerms.length === 0 && (
                <div className="bg-amber-50 border border-amber-200/80 rounded-lg p-2.5 text-xs text-amber-800 flex items-start gap-2">
                  <AlertCircle className="w-3.5 h-3.5 shrink-0 mt-0.5" />
                  {t("selectTemplateModal.noTermsWarning")}
                </div>
              )}
              <div className="space-y-2">
                {periodRows.map((row, idx) => (
                  <div key={idx} className="flex items-center gap-1.5">
                    <input
                      type="text"
                      value={row.label}
                      onChange={(e) => updatePeriodRow(idx, { label: e.target.value })}
                      placeholder={t("selectTemplateModal.labelPlaceholder")}
                      className="w-28 border border-slate-300 rounded-lg text-xs p-1.5 focus:outline-none focus:ring-1 focus:ring-brand-orange"
                    />
                    <Select
                      value={row.academicTermId}
                      onChange={(e) => updatePeriodRow(idx, { academicTermId: e.target.value ? Number(e.target.value) : "" })}
                      className="flex-1 border border-slate-300 rounded-lg text-xs p-1.5 focus:outline-none focus:ring-1 focus:ring-brand-orange"
                    >
                      <option value="">{t("selectTemplateModal.termSelectPlaceholder")}</option>
                      {academicTerms.map((term) => (
                        <option key={term.id} value={term.id}>{term.name}</option>
                      ))}
                    </Select>
                    <Select
                      value={row.evaluationType}
                      onChange={(e) => updatePeriodRow(idx, { evaluationType: e.target.value as PeriodRow["evaluationType"] })}
                      className="w-28 border border-slate-300 rounded-lg text-xs p-1.5 focus:outline-none focus:ring-1 focus:ring-brand-orange"
                    >
                      <option value="">{t("selectTemplateModal.evalTypeSelectPlaceholder")}</option>
                      {(["MID_TERM", "END_TERM"] as const).map((key) => (
                        <option key={key} value={key}>{evaluationTypeLabel(t, key)}</option>
                      ))}
                    </Select>
                    <button
                      type="button"
                      onClick={() => removePeriodRow(idx)}
                      disabled={periodRows.length === 1}
                      className="text-slate-400 hover:text-rose-500 disabled:opacity-30 disabled:cursor-not-allowed p-1"
                      title={t("selectTemplateModal.removeRowTitle")}
                    >
                      <X className="w-3.5 h-3.5" />
                    </button>
                  </div>
                ))}
              </div>
              <button
                type="button"
                onClick={addPeriodRow}
                className="text-xs text-brand-orange font-medium flex items-center gap-1 hover:underline"
              >
                <Plus className="w-3.5 h-3.5" /> {t("selectTemplateModal.addPeriodButton")}
              </button>
            </div>
          )}
        </div>
      )}
    </Modal>
  );
}


