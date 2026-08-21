import React, { useState, useRef } from "react";
import { useTranslation } from "react-i18next";
import Modal from "@/components/ui/Modal";
import Button from "@/components/ui/Button";
import Select from "@/components/ui/Select";
import { UploadCloud, FileText, X } from "lucide-react";
import {
  CreateReportTemplateRequest,
  ReportTemplateType,
  REPORT_TEMPLATE_TYPE_LABELS,
  createReportTemplate,
} from "@/features/academic/api";
import { ApiError } from "@/lib/apiClient";

interface Props {
  onClose: () => void;
  onSuccess: () => void;
}

const ACCEPTED_FORMATS = ".docx,.pdf,.html";

export default function UploadTemplateModal({ onClose, onSuccess }: Props) {
  const { t } = useTranslation("reports-templates");
  const [name, setName] = useState("");
  const [templateType, setTemplateType] = useState<ReportTemplateType | "">("");
  const [description, setDescription] = useState("");
  const [file, setFile] = useState<File | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const f = e.target.files?.[0];
    if (!f) return;
    setFile(f);
    if (!name) setName(f.name.replace(/\.[^.]+$/, ""));
  };

  const handleDrop = (e: React.DragEvent) => {
    e.preventDefault();
    const f = e.dataTransfer.files?.[0];
    if (!f) return;
    setFile(f);
    if (!name) setName(f.name.replace(/\.[^.]+$/, ""));
  };

  const handleSubmit = async () => {
    if (!file || !name.trim() || !templateType) {
      setError(t("uploadTemplateModal.validationError"));
      return;
    }
    setLoading(true);
    setError(null);
    try {
      const req: CreateReportTemplateRequest = {
        name: name.trim(),
        templateType: templateType as ReportTemplateType,
        description: description.trim() || undefined,
        file,
      };
      await createReportTemplate(req);
      onSuccess();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t("uploadTemplateModal.uploadError"));
    } finally {
      setLoading(false);
    }
  };

  const formatFileSize = (bytes: number) => {
    if (bytes < 1024) return `${bytes} B`;
    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
    return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
  };

  return (
    <Modal open={true} size="lg" title={t("uploadTemplateModal.modalTitle")} description={t("uploadTemplateModal.modalDescription")} onClose={onClose}>
      <div className="space-y-4">
        {error && (
          <div className="text-sm text-rose-600 bg-rose-50 border border-rose-100 p-3 rounded-lg">{error}</div>
        )}

        {/* File dropzone */}
        <div
          className={`border-2 border-dashed rounded-xl p-6 text-center cursor-pointer transition-colors ${
            file ? "border-emerald-400 bg-emerald-50" : "border-slate-300 hover:border-brand-orange hover:bg-orange-50"
          }`}
          onClick={() => fileInputRef.current?.click()}
          onDrop={handleDrop}
          onDragOver={(e) => e.preventDefault()}
        >
          <input
            ref={fileInputRef}
            type="file"
            accept={ACCEPTED_FORMATS}
            className="hidden"
            onChange={handleFileChange}
          />
          {file ? (
            <div className="flex items-center justify-center gap-3">
              <FileText className="w-8 h-8 text-emerald-500 shrink-0" />
              <div className="text-left">
                <p className="font-semibold text-slate-800 text-sm">{file.name}</p>
                <p className="text-xs text-slate-500">{formatFileSize(file.size)}</p>
              </div>
              <button
                className="ml-2 text-slate-400 hover:text-rose-500"
                onClick={(e) => { e.stopPropagation(); setFile(null); }}
              >
                <X className="w-4 h-4" />
              </button>
            </div>
          ) : (
            <div>
              <UploadCloud className="w-10 h-10 text-slate-300 mx-auto mb-2" />
              <p className="text-sm text-slate-500">{t("uploadTemplateModal.dropzonePromptPrefix")} <span className="text-brand-orange font-semibold">{t("uploadTemplateModal.dropzonePromptEmphasis")}</span></p>
              <p className="text-xs text-slate-400 mt-1">{t("uploadTemplateModal.dropzoneHint")}</p>
            </div>
          )}
        </div>

        {/* Tên mẫu */}
        <div>
          <label className="block text-xs font-semibold text-slate-700 mb-1">
            {t("uploadTemplateModal.nameLabel")} <span className="text-rose-500">*</span>
          </label>
          <input
            type="text"
            value={name}
            onChange={(e) => setName(e.target.value)}
            placeholder={t("uploadTemplateModal.namePlaceholder")}
            className="w-full border border-slate-300 rounded-lg text-sm p-2.5 focus:outline-none focus:ring-2 focus:ring-brand-orange/50 focus:border-brand-orange"
          />
        </div>

        {/* Loại báo cáo */}
        <div>
          <label className="block text-xs font-semibold text-slate-700 mb-1">
            {t("uploadTemplateModal.typeLabel")} <span className="text-rose-500">*</span>
          </label>
          <Select
            value={templateType}
            onChange={(e) => setTemplateType(e.target.value as ReportTemplateType)}
            className="w-full border border-slate-300 rounded-lg text-sm p-2.5 focus:outline-none focus:ring-2 focus:ring-brand-orange/50 focus:border-brand-orange"
          >
            <option value="">{t("uploadTemplateModal.typeSelectPlaceholder")}</option>
            {(Object.entries(REPORT_TEMPLATE_TYPE_LABELS) as [ReportTemplateType, string][]).map(([key, label]) => (
              <option key={key} value={key}>{label}</option>
            ))}
          </Select>
          <p className="text-xs text-slate-400 mt-1">
            {t("uploadTemplateModal.typeHint")}
          </p>
        </div>

        {/* Mô tả */}
        <div>
          <label className="block text-xs font-semibold text-slate-700 mb-1">{t("uploadTemplateModal.descriptionLabel")}</label>
          <textarea
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            placeholder={t("uploadTemplateModal.descriptionPlaceholder")}
            rows={2}
            className="w-full border border-slate-300 rounded-lg text-sm p-2.5 focus:outline-none focus:ring-2 focus:ring-brand-orange/50 focus:border-brand-orange resize-none"
          />
        </div>

        {/* Thông tin sau upload */}
        {file && templateType && (
          <div className="bg-blue-50 border border-blue-100 rounded-lg p-3 text-xs text-blue-700 space-y-1">
            <p className="font-semibold text-blue-800">{t("uploadTemplateModal.afterUploadTitle")}</p>
            <p>• {t("uploadTemplateModal.afterUploadLine1Prefix")} <code>[TEN_BIEN]</code> {t("uploadTemplateModal.afterUploadLine1Suffix")}</p>
            <p>• {t("uploadTemplateModal.afterUploadLine2Prefix")} <strong>{t("uploadTemplateModal.afterUploadLine2Bold")}</strong> {t("uploadTemplateModal.afterUploadLine2Suffix")}</p>
          </div>
        )}

        <div className="flex justify-end gap-2 pt-2">
          <Button variant="secondary" onClick={onClose} disabled={loading}>{t("uploadTemplateModal.cancel")}</Button>
          <Button onClick={handleSubmit} disabled={loading || !file || !name || !templateType}>
            {loading ? t("uploadTemplateModal.uploading") : t("uploadTemplateModal.uploadButton")}
          </Button>
        </div>
      </div>
    </Modal>
  );
}
