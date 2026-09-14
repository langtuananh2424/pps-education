import React, { useRef, useState } from "react";
import { Download, UploadCloud } from "lucide-react";
import { useTranslation } from "react-i18next";
import { ApiError } from "@/lib/apiClient";
import Modal from "@/components/ui/Modal";
import { buildXlsxTemplateBlob, downloadBlob } from "@/lib/xlsxTemplate";
import { ReviewVideoCatalogImportResponse, importReviewVideoCatalog } from "../api";

const TEMPLATE_HEADERS = ["Mã bộ", "Loại video", "Mã khung chương trình", "Loại giáo viên", "Tên sách", "Mã Unit", "Mã subtopic", "Tiêu đề", "Link video"];

/** Mirror đúng ví dụ thật (2 video cùng 1 Bộ, dòng 2 lặp lại merged cell) để người dùng thấy ngay cách khai forward-fill. */
const TEMPLATE_SAMPLE_ROWS = [
  ["RV-U1-CONN-01", "TKN", "CAMBRIDGE-G7", "GVVN", "Grade 7 Cambridge Standard Chapter 1", "UNIT 1: HOBBIES", "SUB TOPIC 1: MY FAVORITE HOBBIES", "Video 1: Greeting", "https://www.youtube.com/watch?v=AAAAAAAAAAA"],
  ["", "", "", "", "", "", "", "Video 2: Introduction", "https://www.youtube.com/watch?v=BBBBBBBBBBB"]
];

interface ReviewVideoCatalogImportModalProps {
  open: boolean;
  onClose: () => void;
  /** Gọi lại sau khi có ít nhất 1 dòng import thành công — trang cha tự tải lại danh sách Bộ. */
  onImported: () => void;
}

/**
 * UC-73 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-14) —
 * mirror BookCatalogImportModal.tsx (UC-72) nhưng KHÔNG cần chọn tham số
 * mặc định nào trước khi upload — mọi giá trị (khung chương trình, loại
 * video/giáo viên, Sách/Unit/Sub Topic) đọc thẳng từ file.
 */
export default function ReviewVideoCatalogImportModal({ open, onClose, onImported }: ReviewVideoCatalogImportModalProps) {
  const { t } = useTranslation("lms-review-video");
  const inputRef = useRef<HTMLInputElement>(null);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [result, setResult] = useState<ReviewVideoCatalogImportResponse | null>(null);

  const handleClose = () => {
    setResult(null);
    setError(null);
    onClose();
  };

  const handleDownloadTemplate = () => {
    const blob = buildXlsxTemplateBlob(TEMPLATE_HEADERS, TEMPLATE_SAMPLE_ROWS);
    downloadBlob(blob, "mau-bo-video-on-tap.xlsx");
  };

  const handleFile = async (file: File | null) => {
    if (!file) return;
    if (!file.name.toLowerCase().endsWith(".xlsx")) {
      setError(t("importModal.onlyXlsx"));
      return;
    }
    setSubmitting(true);
    setError(null);
    setResult(null);
    try {
      const res = await importReviewVideoCatalog(file);
      setResult(res);
      if (res.successRows > 0) onImported();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t("importModal.importFailed"));
    } finally {
      setSubmitting(false);
      if (inputRef.current) inputRef.current.value = "";
    }
  };

  return (
    <Modal open={open} onClose={handleClose} title={t("importModal.title")} description={t("importModal.description")}>
      <div className="space-y-3">
        <button
          type="button"
          onClick={handleDownloadTemplate}
          className="w-full flex items-center justify-center gap-2 border border-dashed border-slate-300 rounded-lg py-3 text-xs font-semibold text-slate-600 hover:bg-slate-50"
        >
          <Download className="w-4 h-4" />
          {t("importModal.downloadTemplate")}
        </button>

        <button
          type="button"
          onClick={() => inputRef.current?.click()}
          disabled={submitting}
          className="w-full flex flex-col items-center justify-center gap-2 border-2 border-dashed border-slate-200 rounded-xl py-8 text-slate-500 hover:border-brand-red hover:bg-red-50/30 transition-colors disabled:opacity-50"
        >
          <UploadCloud className="w-6 h-6 text-brand-red" />
          <span className="text-xs font-bold text-slate-700">
            {submitting ? t("importModal.importing") : t("importModal.chooseFile")}
          </span>
        </button>
        <input ref={inputRef} type="file" accept=".xlsx" className="hidden" onChange={(e) => handleFile(e.target.files?.[0] ?? null)} />

        {error && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg">{error}</div>}

        {result && (
          <div className="space-y-3">
            <div className="grid grid-cols-3 gap-2 text-center">
              <div className="bg-slate-50 border border-slate-100 rounded-lg py-2">
                <div className="text-sm font-bold text-slate-800">{result.totalRows ?? "—"}</div>
                <div className="text-[10px] text-slate-400">{t("importModal.stats.totalRows")}</div>
              </div>
              <div className="bg-emerald-50 border border-emerald-100 rounded-lg py-2">
                <div className="text-sm font-bold text-emerald-600">{result.successRows}</div>
                <div className="text-[10px] text-emerald-500">{t("importModal.stats.successRows")}</div>
              </div>
              <div className="bg-rose-50 border border-rose-100 rounded-lg py-2">
                <div className="text-sm font-bold text-rose-600">{result.failedRows}</div>
                <div className="text-[10px] text-rose-500">{t("importModal.stats.failedRows")}</div>
              </div>
            </div>

            {result.errorSummary.length > 0 && (
              <div className="border border-rose-100 rounded-lg overflow-hidden">
                <div className="bg-rose-50 px-3 py-1.5 text-[10px] font-bold text-rose-600 uppercase">
                  {t("importModal.errorDetailTitle")}
                </div>
                <div className="max-h-48 overflow-y-auto divide-y divide-slate-100">
                  {result.errorSummary.map((e, i) => (
                    <div key={i} className="px-3 py-1.5 text-xs flex gap-2">
                      <span className="font-mono font-bold text-slate-400 shrink-0">#{e.row}</span>
                      <span className="text-slate-600">{e.reason}</span>
                    </div>
                  ))}
                </div>
              </div>
            )}
          </div>
        )}
      </div>
    </Modal>
  );
}
