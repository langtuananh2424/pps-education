import React, { useEffect, useState } from "react";
import { FileText, Plus } from "lucide-react";
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

const documentTypeLabels: Record<CurriculumDocumentType, string> = {
  VIDEO: "Video",
  PDF: "PDF",
  AUDIO: "Audio",
  SLIDE: "Slide",
  IMAGE: "Ảnh",
  OTHER: "Khác"
};

const statusLabels: Record<CurriculumDocumentStatus, string> = { DRAFT: "Nháp", PUBLISHED: "Đã công bố", ARCHIVED: "Đã gỡ" };
const statusVariants: Record<CurriculumDocumentStatus, BadgeVariant> = { DRAFT: "neutral", PUBLISHED: "success", ARCHIVED: "danger" };

/**
 * UC-60: Kho tài liệu tham khảo — độc lập với Kho bài giảng (UC-23, gắn 1 bài giảng cụ thể).
 * Tài liệu ở đây chỉ gắn theo khung chương trình, không gắn lớp/bài giảng nào — Học sinh xem
 * theo curriculum của (các) lớp đang ghi danh để "tự học thêm", không qua 1 bài giảng cụ thể.
 */
export default function DocumentBankPage() {
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
      .catch((err) => setError(err instanceof ApiError ? err.message : "Không tải được kho tài liệu."))
      .finally(() => setLoading(false));
  };

  useEffect(loadDocuments, [selectedCurriculumId]);

  return (
    <div className="space-y-6">
      <div className="border-b border-slate-200 pb-4">
        <h1 className="text-xl font-bold font-display tracking-tight text-slate-900">Kho tài liệu tham khảo</h1>
        <p className="text-xs text-slate-500 mt-1">
          Tài liệu chia sẻ chung theo khung chương trình, không gắn với 1 bài giảng cụ thể nào — Học sinh xem để tự học thêm.
        </p>
      </div>

      {error && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg">{error}</div>}

      <Card className="flex flex-wrap items-center gap-3">
        <Select value={selectedCurriculumId ?? ""} onChange={(e) => setSelectedCurriculumId(e.target.value ? Number(e.target.value) : null)} className={`${inputClass} w-72`}>
          <option value="">-- Chọn khung chương trình --</option>
          {curriculums.map((c) => (
            <option key={c.id} value={c.id}>
              {c.code} — {c.name}
            </option>
          ))}
        </Select>
        <Button variant="primary" disabled={!selectedCurriculumId} onClick={() => setShowCreateForm(true)} className="ml-auto">
          <Plus className="w-4 h-4" />
          <span>Thêm tài liệu</span>
        </Button>
      </Card>

      {loading ? (
        <p className="text-xs text-slate-500">Đang tải...</p>
      ) : !selectedCurriculumId ? (
        <p className="text-xs text-slate-400 italic text-center py-10">Chọn khung chương trình ở trên để xem kho tài liệu.</p>
      ) : documents.length === 0 ? (
        <p className="text-xs text-slate-400 italic text-center py-10">Khung chương trình này chưa có tài liệu tham khảo nào.</p>
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
                  <Badge variant={statusVariants[doc.status]}>{statusLabels[doc.status]}</Badge>
                </div>
                <div>
                  <h4 className="text-xs font-bold text-slate-800 leading-normal">{doc.title}</h4>
                  {doc.description && <p className="text-[11px] text-slate-500 mt-1">{doc.description}</p>}
                </div>
                <p className="text-[11px] text-slate-400 break-all bg-slate-50 p-2 rounded border">{doc.fileUrl}</p>
              </div>
              <div className="border-t border-slate-100 px-4 py-3 flex items-center justify-between">
                <span className="text-[10px] font-bold text-brand-orange">{documentTypeLabels[doc.documentType]}</span>
                <Button size="sm" variant="secondary" onClick={() => setEditingDocument(doc)}>
                  Sửa
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
            showToast("Đã thêm tài liệu thành công!");
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
            showToast("Đã lưu tài liệu thành công!");
          }}
        />
      )}

      <Toast message={toastMessage} />
    </div>
  );
}

function CreateDocumentModal({ curriculumId, onClose, onCreated }: { curriculumId: number; onClose: () => void; onCreated: () => void }) {
  const [form, setForm] = useState({ title: "", description: "", documentType: "PDF" as CurriculumDocumentType, fileUrl: "", displayOrder: "", coverImageUrl: "" });
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!form.title.trim() || !form.fileUrl.trim()) {
      setError("Vui lòng điền tiêu đề và URL tệp phân phối.");
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
      setError(err instanceof ApiError ? err.message : "Thêm tài liệu thất bại.");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Modal open onClose={onClose} title="Thêm tài liệu tham khảo" size="lg">
      <form onSubmit={handleSubmit} className="space-y-4">
        {error && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg">{error}</div>}
        <div className="grid grid-cols-2 gap-3">
          <div>
            <label className={labelClass}>Tiêu đề *</label>
            <input value={form.title} onChange={(e) => setForm({ ...form, title: e.target.value })} className={inputClass} required />
          </div>
          <div>
            <label className={labelClass}>Loại tệp *</label>
            <Select value={form.documentType} onChange={(e) => setForm({ ...form, documentType: e.target.value as CurriculumDocumentType })} className={inputClass}>
              {Object.entries(documentTypeLabels).map(([value, label]) => (
                <option key={value} value={value}>
                  {label}
                </option>
              ))}
            </Select>
          </div>
        </div>
        <div>
          <label className={labelClass}>Mô tả</label>
          <textarea value={form.description} onChange={(e) => setForm({ ...form, description: e.target.value })} rows={2} className={inputClass} />
        </div>
        <div>
          <label className={labelClass}>Tệp tài liệu *</label>
          <FileUploadField
            value={form.fileUrl}
            onChange={(url) => setForm({ ...form, fileUrl: url })}
            onUpload={(file) => uploadMedia(file, "CURRICULUM_DOCUMENT")}
            accept={DOCUMENT_UPLOAD_ACCEPT}
            placeholder="Chọn PDF/Word/Excel/ảnh/audio/video..."
          />
        </div>
        <div>
          <label className={labelClass}>Ảnh bìa hiển thị (tùy chọn)</label>
          <FileUploadField
            value={form.coverImageUrl}
            onChange={(url) => setForm({ ...form, coverImageUrl: url })}
            onUpload={(file) => uploadMedia(file, "CURRICULUM_DOCUMENT")}
            accept="image/*"
            placeholder="Chọn ảnh bìa..."
          />
        </div>
        <div>
          <label className={labelClass}>Thứ tự hiển thị</label>
          <input type="number" value={form.displayOrder} onChange={(e) => setForm({ ...form, displayOrder: e.target.value })} className={`${inputClass} w-32`} />
        </div>
        <p className="text-[10px] text-slate-400 italic">Tài liệu mới tạo ở trạng thái Nháp — vào "Sửa" để chuyển sang Đã công bố khi sẵn sàng cho Học sinh xem.</p>
        <div className="flex justify-end gap-2 pt-2">
          <Button type="button" variant="secondary" onClick={onClose}>
            Hủy
          </Button>
          <Button type="submit" variant="primary" disabled={submitting}>
            {submitting ? "Đang lưu..." : "Thêm tài liệu"}
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
      setError("Vui lòng điền tiêu đề.");
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
      setError(err instanceof ApiError ? err.message : "Cập nhật tài liệu thất bại.");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Modal open onClose={onClose} title={`Sửa tài liệu: ${doc.title}`} size="lg">
      <form onSubmit={handleSubmit} className="space-y-4">
        {error && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg">{error}</div>}
        <div>
          <label className={labelClass}>Tiêu đề *</label>
          <input value={form.title} onChange={(e) => setForm({ ...form, title: e.target.value })} className={inputClass} required />
        </div>
        <div>
          <label className={labelClass}>Mô tả</label>
          <textarea value={form.description} onChange={(e) => setForm({ ...form, description: e.target.value })} rows={2} className={inputClass} />
        </div>
        <div>
          <label className={labelClass}>Ảnh bìa hiển thị (tùy chọn)</label>
          <FileUploadField
            value={form.coverImageUrl}
            onChange={(url) => setForm({ ...form, coverImageUrl: url })}
            onUpload={(file) => uploadMedia(file, "CURRICULUM_DOCUMENT")}
            accept="image/*"
            placeholder="Chọn ảnh bìa..."
          />
        </div>
        <div className="grid grid-cols-2 gap-3">
          <div>
            <label className={labelClass}>Thứ tự hiển thị</label>
            <input type="number" value={form.displayOrder} onChange={(e) => setForm({ ...form, displayOrder: e.target.value })} className={inputClass} />
          </div>
          <div>
            <label className={labelClass}>Trạng thái</label>
            <Select value={form.status} onChange={(e) => setForm({ ...form, status: e.target.value as CurriculumDocumentStatus })} className={inputClass}>
              {Object.entries(statusLabels).map(([value, label]) => (
                <option key={value} value={value}>
                  {label}
                </option>
              ))}
            </Select>
          </div>
        </div>
        <p className="text-[10px] text-slate-400 italic">Loại tệp/URL không sửa được sau khi tạo — xoá bằng cách chuyển trạng thái "Đã gỡ" (ARCHIVED), tạo bản ghi mới nếu cần đổi tệp.</p>
        <div className="flex justify-end gap-2 pt-2">
          <Button type="button" variant="secondary" onClick={onClose}>
            Hủy
          </Button>
          <Button type="submit" variant="primary" disabled={submitting}>
            {submitting ? "Đang lưu..." : "Lưu thay đổi"}
          </Button>
        </div>
      </form>
    </Modal>
  );
}
