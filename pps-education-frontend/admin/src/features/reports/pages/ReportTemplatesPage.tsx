import React, { useEffect, useState } from "react";
import { ClipboardCheck, Plus, FileText, Settings, Download, Trash2, FileType } from "lucide-react";
import { useTranslation } from "react-i18next";
import { useApp } from "@/context/AppContext";
import Button from "@/components/ui/Button";
import {
  ReportTemplateResponse,
  REPORT_TEMPLATE_TYPE_LABELS,
  listReportTemplates,
  deleteReportTemplate,
} from "@/features/academic/api";
import UploadTemplateModal from "../components/UploadTemplateModal";
import FieldMappingsDrawer from "../components/FieldMappingsDrawer";
import { useToast } from "@/lib/useToast";
import Toast from "@/components/ui/Toast";
import { ApiError } from "@/lib/apiClient";

const FILE_FORMAT_COLORS: Record<string, string> = {
  DOCX: "bg-blue-100 text-blue-700",
  PDF: "bg-rose-100 text-rose-700",
  HTML: "bg-amber-100 text-amber-700",
};

export default function ReportTemplatesPage() {
  const { t } = useTranslation("reports-templates");
  const { hasPermission } = useApp();
  const canManage = hasPermission("report.template.create") || hasPermission("report.template.update");
  const [templates, setTemplates] = useState<ReportTemplateResponse[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const [uploadOpen, setUploadOpen] = useState(false);
  const [mappingOpen, setMappingOpen] = useState(false);
  const [selectedTemplate, setSelectedTemplate] = useState<ReportTemplateResponse | null>(null);

  const { message: toastMessage, showToast } = useToast();

  const load = () => {
    setLoading(true);
    listReportTemplates()
      .then(setTemplates)
      .catch((err) => setError(err instanceof ApiError ? err.message : t("reportTemplatesPage.loadError")))
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    load();
  }, []);

  const handleDelete = async (id: number) => {
    if (!confirm(t("reportTemplatesPage.confirmDelete"))) return;
    try {
      await deleteReportTemplate(id);
      showToast(t("reportTemplatesPage.deleteSuccessToast"));
      load();
    } catch (err: any) {
      alert(err.message || t("reportTemplatesPage.deleteErrorAlert"));
    }
  };

  const openMapping = (tpl: ReportTemplateResponse) => {
    setSelectedTemplate(tpl);
    setMappingOpen(true);
  };

  return (
    <div className="space-y-6">
      <div className="border-b border-slate-200 pb-4 flex items-start justify-between gap-3 flex-wrap">
        <div>
          <h1 className="text-xl font-bold font-display tracking-tight text-slate-900">{t("reportTemplatesPage.title")}</h1>
          <p className="text-xs text-slate-500 mt-1">
            {t("reportTemplatesPage.subtitle")}
          </p>
        </div>
        {canManage && (
          <Button size="sm" onClick={() => setUploadOpen(true)}>
            <Plus className="w-3.5 h-3.5" />
            {t("reportTemplatesPage.uploadNewButton")}
          </Button>
        )}
      </div>

      {/* Hướng dẫn cách dùng */}
      <div className="grid grid-cols-4 gap-4">
        {[
          { step: "1", title: t("reportTemplatesPage.steps.step1.title"), desc: t("reportTemplatesPage.steps.step1.desc") },
          { step: "2", title: t("reportTemplatesPage.steps.step2.title"), desc: t("reportTemplatesPage.steps.step2.desc") },
          { step: "3", title: t("reportTemplatesPage.steps.step3.title"), desc: t("reportTemplatesPage.steps.step3.desc") },
          { step: "4", title: t("reportTemplatesPage.steps.step4.title"), desc: t("reportTemplatesPage.steps.step4.desc") },
        ].map(({ step, title, desc }) => (
          <div key={step} className="bg-white border border-slate-200 rounded-xl p-4 flex gap-3 shadow-sm">
            <div className="w-7 h-7 rounded-full bg-brand-orange text-white text-xs font-bold flex items-center justify-center shrink-0">{step}</div>
            <div>
              <p className="text-sm font-semibold text-slate-800">{title}</p>
              <p className="text-xs text-slate-500 mt-0.5">{desc}</p>
            </div>
          </div>
        ))}
      </div>

      {error && <div className="text-sm text-rose-500 bg-rose-50 p-3 rounded-lg border border-rose-100">{error}</div>}

      <div className="bg-white rounded-xl border border-slate-200/60 shadow-sm overflow-hidden">
        <table className="w-full text-left text-sm">
          <thead className="bg-slate-50/50 border-b border-slate-200/60 text-slate-500 font-medium text-xs">
            <tr>
              <th className="px-4 py-3">{t("reportTemplatesPage.table.name")}</th>
              <th className="px-4 py-3">{t("reportTemplatesPage.table.type")}</th>
              <th className="px-4 py-3">{t("reportTemplatesPage.table.format")}</th>
              <th className="px-4 py-3">{t("reportTemplatesPage.table.placeholders")}</th>
              <th className="px-4 py-3">{t("reportTemplatesPage.table.status")}</th>
              <th className="px-4 py-3 text-right">{t("reportTemplatesPage.table.actions")}</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-200/60">
            {templates.map((tpl) => (
              <tr key={tpl.id} className="hover:bg-slate-50/50 transition-colors">
                <td className="px-4 py-3">
                  <div className="flex items-center gap-2">
                    <FileText className="w-4 h-4 text-brand-orange shrink-0" />
                    <div>
                      <span className="font-semibold text-slate-700 block text-sm">{tpl.name}</span>
                      {tpl.description && <span className="text-xs text-slate-400">{tpl.description}</span>}
                    </div>
                  </div>
                </td>
                <td className="px-4 py-3">
                  <span className="text-xs text-slate-600 font-medium">
                    {REPORT_TEMPLATE_TYPE_LABELS[tpl.templateType] ?? tpl.templateType}
                  </span>
                </td>
                <td className="px-4 py-3">
                  <span className={`px-2 py-0.5 rounded text-xs font-bold ${FILE_FORMAT_COLORS[tpl.fileFormat] ?? "bg-slate-100 text-slate-600"}`}>
                    {tpl.fileFormat}
                  </span>
                </td>
                <td className="px-4 py-3">
                  <div className="flex items-center gap-1.5">
                    <FileType className="w-3.5 h-3.5 text-slate-400" />
                    <span className="text-xs text-slate-500">{t("reportTemplatesPage.placeholderCount", { count: (tpl.placeholderKeys ?? []).length })}</span>
                  </div>
                </td>
                <td className="px-4 py-3">
                  <span className={`px-2 py-0.5 rounded text-xs font-semibold ${tpl.active ? "bg-emerald-100 text-emerald-700" : "bg-slate-100 text-slate-600"}`}>
                    {tpl.active ? t("reportTemplatesPage.statusActive") : t("reportTemplatesPage.statusArchived")}
                  </span>
                </td>
                <td className="px-4 py-3 text-right">
                  <div className="flex items-center justify-end gap-2">
                    <Button
                      size="sm"
                      variant="secondary"
                      onClick={() => openMapping(tpl)}
                      title={t("reportTemplatesPage.mappingButtonTitle")}
                    >
                      <Settings className="w-3.5 h-3.5 text-slate-500" />
                    </Button>
                    <Button
                      size="sm"
                      variant="danger"
                      onClick={() => handleDelete(tpl.id)}
                      title={t("reportTemplatesPage.deleteButtonTitle")}
                    >
                      <Trash2 className="w-3.5 h-3.5" />
                    </Button>
                  </div>
                </td>
              </tr>
            ))}
            {!loading && templates.length === 0 && (
              <tr>
                <td colSpan={6} className="px-4 py-10 text-center text-slate-500">
                  <ClipboardCheck className="w-10 h-10 mx-auto text-slate-200 mb-2" />
                  <p className="text-sm font-medium">{t("reportTemplatesPage.emptyTitle")}</p>
                  <p className="text-xs text-slate-400 mt-1">{t("reportTemplatesPage.emptyHint")}</p>
                </td>
              </tr>
            )}
            {loading && (
              <tr>
                <td colSpan={6} className="px-4 py-8 text-center text-slate-400 text-sm">{t("reportTemplatesPage.loading")}</td>
              </tr>
            )}
          </tbody>
        </table>
      </div>

      {uploadOpen && (
        <UploadTemplateModal
          onClose={() => setUploadOpen(false)}
          onSuccess={() => { setUploadOpen(false); showToast(t("reportTemplatesPage.uploadSuccessToast")); load(); }}
        />
      )}
      {mappingOpen && selectedTemplate && (
        <FieldMappingsDrawer
          template={selectedTemplate}
          onClose={() => setMappingOpen(false)}
          onSuccess={() => { setMappingOpen(false); showToast(t("reportTemplatesPage.mappingSuccessToast")); load(); }}
        />
      )}

      <Toast message={toastMessage} />
    </div>
  );
}
