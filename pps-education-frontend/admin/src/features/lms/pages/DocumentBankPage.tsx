import React, { useEffect, useState } from "react";
import { FileText, Plus } from "lucide-react";
import { useTranslation } from "react-i18next";
import { ApiError } from "@/lib/apiClient";
import { CurriculumResponse, listCurriculums } from "@/features/academic/api";
import {
  createCurriculumDocument,
  CreateCurriculumDocumentRequest,
  CurriculumDocumentResponse,
  CurriculumDocumentStatus,
  CurriculumDocumentType,
  listCurriculumDocuments,
  updateCurriculumDocument,
  uploadMedia,
  UpdateCurriculumDocumentRequest
} from "../api";
import Card from "@/components/ui/Card";
import Button from "@/components/ui/Button";
import Badge, { BadgeVariant } from "@/components/ui/Badge";
import Modal from "@/components/ui/Modal";
import FileUploadField from "@/components/ui/FileUploadField";
import { useToast } from "@/lib/useToast";
import Toast from "@/components/ui/Toast";
import Select from "@/components/ui/Select";

/** Khớp DOCUMENT_CONTENT_TYPES + audio/image/video của module CURRICULUM_DOCUMENT (xem MediaStorageService.java). */
const DOCUMENT_UPLOAD_ACCEPT =
  "image/*,audio/*,video/*,.pdf,.doc,.docx,.xls,.xlsx,.ppt,.pptx,application/pdf,application/msword,application/vnd.openxmlformats-officedocument.wordprocessingml.document,application/vnd.ms-excel,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet,application/vnd.ms-powerpoint,application/vnd.openxmlformats-officedocument.presentationml.presentation";

const inputClass = "w-full bg-slate-50 border border-slate-200 text-xs p-2.5 rounded-lg focus:outline-none";
const labelClass = "text-[10px] uppercase font-bold text-slate-500 block mb-1";

/**
 * Nhãn dịch qua i18next namespace "lms-documents" (key `documentType.<Type>` / `status.<Status>`) —
 * dùng `documentTypeLabel(t, type)` / `statusLabel(t, status)` thay vì tra map tĩnh cũ, vì nhãn giờ
 * phải đổi theo ngôn ngữ đang chọn.
 */
function documentTypeLabel(t: (key: string) => string, type: CurriculumDocumentType): string {
  return t(`documentType.${type}`);
}

function statusLabel(t: (key: string) => string, status: CurriculumDocumentStatus): string {
  return t(`status.${status}`);
}

const statusVariants: Record<CurriculumDocumentStatus, BadgeVariant> = { DRAFT: "neutral", PUBLISHED: "success", ARCHIVED: "danger" };
const documentTypes: CurriculumDocumentType[] = ["VIDEO", "PDF", "AUDIO", "SLIDE", "IMAGE", "OTHER"];
const documentStatuses: CurriculumDocumentStatus[] = ["DRAFT", "PUBLISHED", "ARCHIVED"];

/**
 * UC-60: Kho tài liệu tham khảo — độc lập với Kho bài giảng (UC-23, gắn 1 bài giảng cụ thể).
 * Tài liệu ở đây chỉ gắn theo khung chương trình, không gắn lớp/bài giảng nào — Học sinh xem
 * theo curriculum của (các) lớp đang ghi danh để "tự học thêm", không qua 1 bài giảng cụ thể.
 */
export default function DocumentBankPage() {
  const { t } = useTranslation("lms-documents");
  const [curriculums, setCurriculums] = useState<CurriculumResponse[]>([]);
  const [selectedCurriculumId, setSelectedCurriculumId] = useState<number | null>(null);
  const [documents, setDocuments] = useState<CurriculumDocumentResponse[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const [showCreateForm, setShowCreateForm] = useState(false);
  const [editingDocument, setEditingDocument] = useState<CurriculumDocumentResponse | null>(null);
  const { message: toastMessage, showToast } = useToast();

  useEffect(() => {
    listCurriculums().then(setCurriculums).catch(() => undefined);
  }, []);

  const loadDocuments = () => {
    if (!selectedCurriculumId) {
      setDocuments([]);
      return;
    }
    setLoading(true);
    setError(null);
    listCurriculumDocuments(selectedCurriculumId)
      .then(setDocuments)
      .catch((err) => setError(err instanceof ApiError ? err.message : t("errors.loadFailed")))
      .finally(() => setLoading(false));
  };

  useEffect(loadDocuments, [selectedCurriculumId]);

  return (
    <div className="space-y-6">
      <div className="border-b border-slate-200 pb-4">
        <h1 className="text-xl font-bold font-display tracking-tight text-slate-900">{t("page.title")}</h1>
        <p className="text-xs text-slate-500 mt-1">{t("page.subtitle")}</p>
      </div>

      {error && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg">{error}</div>}

      <Card className="flex flex-wrap items-center gap-3">
        <Select value={selectedCurriculumId ?? ""} onChange={(e) => setSelectedCurriculumId(e.target.value ? Number(e.target.value) : null)} className={`${inputClass} w-72`}>
          <option value="">{t("filter.curriculumPlaceholder")}</option>
          {curriculums.map((c) => (
            <option key={c.id} value={c.id}>
              {c.code} — {c.name}
            </option>
          ))}
        </Select>
        <Button variant="primary" disabled={!selectedCurriculumId} onClick={() => setShowCreateForm(true)} className="ml-auto">
          <Plus className="w-4 h-4" />
          <span>{t("filter.addButton")}</span>
        </Button>
      </Card>

      {loading ? (
        <p className="text-xs text-slate-500">{t("list.loading")}</p>
      ) : !selectedCurriculumId ? (
        <p className="text-xs text-slate-400 italic text-center py-10">{t("list.selectCurriculumPrompt")}</p>
      ) : documents.length === 0 ? (
        <p className="text-xs text-slate-400 italic text-center py-10">{t("list.empty")}</p>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-5">
          {documents.map((doc) => (
            <Card key={doc.id} padded={false} className="flex flex-col justify-between overflow-hidden">
              {doc.coverImageUrl ? (
                <img src={doc.coverImageUrl} alt="" className="w-full h-28 object-cover" />
              ) : (
                <div className="w-full h-28 bg-amber-50 flex items-center justify-center text-brand-orange">
                  <FileText className="w-8 h-8" />
                </div>
              )}
              <div className="p-4 space-y-2 flex-1">
                <div className="flex items-center justify-end">
                  <Badge variant={statusVariants[doc.status]}>{statusLabel(t, doc.status)}</Badge>
                </div>
                <div>
                  <h4 className="text-xs font-bold text-slate-800 leading-normal">{doc.title}</h4>
                  {doc.description && <p className="text-[11px] text-slate-500 mt-1">{doc.description}</p>}
                </div>
                <p className="text-[11px] text-slate-400 break-all bg-slate-50 p-2 rounded border">{doc.fileUrl}</p>
              </div>
              <div className="border-t border-slate-100 px-4 py-3 flex items-center justify-between">
                <span className="text-[10px] font-bold text-brand-orange">{documentTypeLabel(t, doc.documentType)}</span>
                <Button size="sm" variant="secondary" onClick={() => setEditingDocument(doc)}>
                  {t("list.editButton")}
                </Button>
              </div>
            </Card>
          ))}
        </div>
      )}

      {showCreateForm && selectedCurriculumId && (
        <CreateDocumentModal
          curriculumId={selectedCurriculumId}
          onClose={() => setShowCreateForm(false)}
          onCreated={() => {
            setShowCreateForm(false);
            loadDocuments();
            showToast(t("toast.created"));
          }}
        />
      )}

      {editingDocument && (
        <EditDocumentModal
          document={editingDocument}
          onClose={() => setEditingDocument(null)}
          onSaved={() => {
            setEditingDocument(null);
            loadDocuments();
            showToast(t("toast.updated"));
          }}
        />
      )}

      <Toast message={toastMessage} />
    </div>
  );
}

function CreateDocumentModal({ curriculumId, onClose, onCreated }: { curriculumId: number; onClose: () => void; onCreated: () => void }) {
  const { t } = useTranslation("lms-documents");
  const [form, setForm] = useState({ title: "", description: "", documentType: "PDF" as CurriculumDocumentType, fileUrl: "", displayOrder: "", coverImageUrl: "" });
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!form.title.trim() || !form.fileUrl.trim()) {
      setError(t("errors.titleAndFileRequired"));
      return;
    }
    setSubmitting(true);
    setError(null);
    try {
      const request: CreateCurriculumDocumentRequest = {
        title: form.title.trim(),
        description: form.description.trim() || undefined,
        documentType: form.documentType,
        fileUrl: form.fileUrl.trim(),
        displayOrder: form.displayOrder ? Number(form.displayOrder) : undefined,
        coverImageUrl: form.coverImageUrl || undefined
      };
      await createCurriculumDocument(curriculumId, request);
      onCreated();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t("errors.createFailed"));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Modal open onClose={onClose} title={t("createModal.title")} size="lg">
      <form onSubmit={handleSubmit} className="space-y-4">
        {error && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg">{error}</div>}
        <div className="grid grid-cols-2 gap-3">
          <div>
            <label className={labelClass}>{t("createModal.fields.title")}</label>
            <input value={form.title} onChange={(e) => setForm({ ...form, title: e.target.value })} className={inputClass} required />
          </div>
          <div>
            <label className={labelClass}>{t("createModal.fields.documentType")}</label>
            <Select value={form.documentType} onChange={(e) => setForm({ ...form, documentType: e.target.value as CurriculumDocumentType })} className={inputClass}>
              {documentTypes.map((value) => (
                <option key={value} value={value}>
                  {documentTypeLabel(t, value)}
                </option>
              ))}
            </Select>
          </div>
        </div>
        <div>
          <label className={labelClass}>{t("createModal.fields.description")}</label>
          <textarea value={form.description} onChange={(e) => setForm({ ...form, description: e.target.value })} rows={2} className={inputClass} />
        </div>
        <div>
          <label className={labelClass}>{t("createModal.fields.file")}</label>
          <FileUploadField
            value={form.fileUrl}
            onChange={(url) => setForm({ ...form, fileUrl: url })}
            onUpload={(file) => uploadMedia(file, "CURRICULUM_DOCUMENT")}
            accept={DOCUMENT_UPLOAD_ACCEPT}
            placeholder={t("createModal.fields.filePlaceholder")}
          />
        </div>
        <div>
          <label className={labelClass}>{t("createModal.fields.coverImage")}</label>
          <FileUploadField
            value={form.coverImageUrl}
            onChange={(url) => setForm({ ...form, coverImageUrl: url })}
            onUpload={(file) => uploadMedia(file, "CURRICULUM_DOCUMENT")}
            accept="image/*"
            placeholder={t("createModal.fields.coverImagePlaceholder")}
          />
        </div>
        <div>
          <label className={labelClass}>{t("createModal.fields.displayOrder")}</label>
          <input type="number" value={form.displayOrder} onChange={(e) => setForm({ ...form, displayOrder: e.target.value })} className={`${inputClass} w-32`} />
        </div>
        <p className="text-[10px] text-slate-400 italic">{t("createModal.draftHint")}</p>
        <div className="flex justify-end gap-2 pt-2">
          <Button type="button" variant="secondary" onClick={onClose}>
            {t("createModal.cancel")}
          </Button>
          <Button type="submit" variant="primary" disabled={submitting}>
            {submitting ? t("createModal.submitting") : t("createModal.submit")}
          </Button>
        </div>
      </form>
    </Modal>
  );
}

function EditDocumentModal({
  document: doc,
  onClose,
  onSaved
}: {
  document: CurriculumDocumentResponse;
  onClose: () => void;
  onSaved: () => void;
}) {
  const { t } = useTranslation("lms-documents");
  const [form, setForm] = useState({
    title: doc.title,
    description: doc.description ?? "",
    displayOrder: String(doc.displayOrder),
    status: doc.status,
    coverImageUrl: doc.coverImageUrl ?? ""
  });
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!form.title.trim()) {
      setError(t("errors.titleRequired"));
      return;
    }
    setSubmitting(true);
    setError(null);
    try {
      const request: UpdateCurriculumDocumentRequest = {
        title: form.title.trim(),
        description: form.description.trim() || undefined,
        displayOrder: form.displayOrder ? Number(form.displayOrder) : undefined,
        status: form.status,
        coverImageUrl: form.coverImageUrl || undefined
      };
      await updateCurriculumDocument(doc.id, request);
      onSaved();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t("errors.updateFailed"));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Modal open onClose={onClose} title={t("editModal.title", { title: doc.title })} size="lg">
      <form onSubmit={handleSubmit} className="space-y-4">
        {error && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg">{error}</div>}
        <div>
          <label className={labelClass}>{t("editModal.fields.title")}</label>
          <input value={form.title} onChange={(e) => setForm({ ...form, title: e.target.value })} className={inputClass} required />
        </div>
        <div>
          <label className={labelClass}>{t("editModal.fields.description")}</label>
          <textarea value={form.description} onChange={(e) => setForm({ ...form, description: e.target.value })} rows={2} className={inputClass} />
        </div>
        <div>
          <label className={labelClass}>{t("editModal.fields.coverImage")}</label>
          <FileUploadField
            value={form.coverImageUrl}
            onChange={(url) => setForm({ ...form, coverImageUrl: url })}
            onUpload={(file) => uploadMedia(file, "CURRICULUM_DOCUMENT")}
            accept="image/*"
            placeholder={t("editModal.fields.coverImagePlaceholder")}
          />
        </div>
        <div className="grid grid-cols-2 gap-3">
          <div>
            <label className={labelClass}>{t("editModal.fields.displayOrder")}</label>
            <input type="number" value={form.displayOrder} onChange={(e) => setForm({ ...form, displayOrder: e.target.value })} className={inputClass} />
          </div>
          <div>
            <label className={labelClass}>{t("editModal.fields.status")}</label>
            <Select value={form.status} onChange={(e) => setForm({ ...form, status: e.target.value as CurriculumDocumentStatus })} className={inputClass}>
              {documentStatuses.map((value) => (
                <option key={value} value={value}>
                  {statusLabel(t, value)}
                </option>
              ))}
            </Select>
          </div>
        </div>
        <p className="text-[10px] text-slate-400 italic">{t("editModal.immutableHint")}</p>
        <div className="flex justify-end gap-2 pt-2">
          <Button type="button" variant="secondary" onClick={onClose}>
            {t("editModal.cancel")}
          </Button>
          <Button type="submit" variant="primary" disabled={submitting}>
            {submitting ? t("editModal.submitting") : t("editModal.submit")}
          </Button>
        </div>
      </form>
    </Modal>
  );
}
