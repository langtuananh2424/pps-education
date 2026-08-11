import React, { useEffect, useState } from "react";
import { ClipboardCheck, Plus, FileText, Settings, Download, Trash2, FileType } from "lucide-react";
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
      .catch((err) => setError(err instanceof ApiError ? err.message : "Không tải được danh sách mẫu báo cáo."))
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    load();
  }, []);

  const handleDelete = async (id: number) => {
    if (!confirm("Bạn có chắc chắn muốn xoá mẫu báo cáo này?")) return;
    try {
      await deleteReportTemplate(id);
      showToast("Xoá thành công");
      load();
    } catch (err: any) {
      alert(err.message || "Lỗi khi xoá");
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
          <h1 className="text-xl font-bold font-display tracking-tight text-slate-900">Mẫu báo cáo tự động</h1>
          <p className="text-xs text-slate-500 mt-1">
            Tải lên mẫu DOCX/PDF/HTML, cấu hình mapping và sử dụng để xuất báo cáo tự động.
          </p>
        </div>
        {canManage && (
          <Button size="sm" onClick={() => setUploadOpen(true)}>
            <Plus className="w-3.5 h-3.5" />
            Tải lên mẫu mới
          </Button>
        )}
      </div>

      {/* Hướng dẫn cách dùng */}
      <div className="grid grid-cols-4 gap-4">
        {[
          { step: "1", title: "Tải lên mẫu", desc: "Chọn file .docx/.pdf/.html đã có placeholder [TEN_BIEN]" },
          { step: "2", title: "Chọn loại báo cáo", desc: "Phân loại để hệ thống biết mẫu dùng cho chức năng nào" },
          { step: "3", title: "Cấu hình Mapping", desc: "Ánh xạ từng placeholder với dữ liệu thực tế" },
          { step: "4", title: "Xuất báo cáo", desc: "Dùng mẫu trong các trang Thống kê để xuất file" },
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
              <th className="px-4 py-3">Tên mẫu</th>
              <th className="px-4 py-3">Loại báo cáo</th>
              <th className="px-4 py-3">Định dạng</th>
              <th className="px-4 py-3">Placeholders</th>
              <th className="px-4 py-3">Trạng thái</th>
              <th className="px-4 py-3 text-right">Thao tác</th>
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
                    <span className="text-xs text-slate-500">{(tpl.placeholderKeys ?? []).length} biến</span>
                  </div>
                </td>
                <td className="px-4 py-3">
                  <span className={`px-2 py-0.5 rounded text-xs font-semibold ${tpl.active ? "bg-emerald-100 text-emerald-700" : "bg-slate-100 text-slate-600"}`}>
                    {tpl.active ? "Đang dùng" : "Đã lưu trữ"}
                  </span>
                </td>
                <td className="px-4 py-3 text-right">
                  <div className="flex items-center justify-end gap-2">
                    <Button
                      size="sm"
                      variant="secondary"
                      onClick={() => openMapping(tpl)}
                      title="Cấu hình Mapping"
                    >
                      <Settings className="w-3.5 h-3.5 text-slate-500" />
                    </Button>
                    <Button
                      size="sm"
                      variant="danger"
                      onClick={() => handleDelete(tpl.id)}
                      title="Xoá"
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
                  <p className="text-sm font-medium">Chưa có mẫu báo cáo nào</p>
                  <p className="text-xs text-slate-400 mt-1">Bấm "Tải lên mẫu mới" để bắt đầu</p>
                </td>
              </tr>
            )}
            {loading && (
              <tr>
                <td colSpan={6} className="px-4 py-8 text-center text-slate-400 text-sm">Đang tải...</td>
              </tr>
            )}
          </tbody>
        </table>
      </div>

      {uploadOpen && (
        <UploadTemplateModal
          onClose={() => setUploadOpen(false)}
          onSuccess={() => { setUploadOpen(false); showToast("Tải mẫu lên thành công!"); load(); }}
        />
      )}
      {mappingOpen && selectedTemplate && (
        <FieldMappingsDrawer
          template={selectedTemplate}
          onClose={() => setMappingOpen(false)}
          onSuccess={() => { setMappingOpen(false); showToast("Đã lưu mappings!"); load(); }}
        />
      )}

      <Toast message={toastMessage} />
    </div>
  );
}
