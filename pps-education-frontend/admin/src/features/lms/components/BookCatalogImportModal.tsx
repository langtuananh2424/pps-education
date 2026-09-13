import React, { useRef, useState } from "react";
import { Download, UploadCloud } from "lucide-react";
import { useTranslation } from "react-i18next";
import { ApiError } from "@/lib/apiClient";
import Modal from "@/components/ui/Modal";
import Select from "@/components/ui/Select";
import { buildXlsxTemplateBlob, downloadBlob } from "@/lib/xlsxTemplate";
import { BookCatalogImportResponse, ExamTeacherType, ExamType, importBookCatalog } from "../api";

const TEMPLATE_HEADERS = ["Tên sách", "Tên Unit", "Tên Sub Topic", "Mã Lesson", "Loại giáo viên", "Mã exercise", "Tên exercise"];

/** Mirror đúng ví dụ thật (Lesson lẻ GVVN, Lesson chẵn GVNN xen kẽ) để người dùng thấy ngay cách khai cột E. */
const TEMPLATE_SAMPLE_ROWS = [
  ["Grade 6 Standard Chaper 1", "UNIT 1: MY NEW SCHOOL", "SUB TOPIC 1: SCHOOL ACTIVITIES AND SUBJECTS", "G6-STD-C1-U1-SUB1-L1", "VIETNAMESE", "G6-STD-C1-U1-SUB1-L1-EX1", "Ex. 1: Choose the correct word from the word box below to complete the sentences."],
  ["", "", "", "", "", "G6-STD-C1-U1-SUB1-L1-EX2", "Ex. 2: Underline the correct word in each sentence."],
  ["", "", "", "G6-STD-C1-U1-SUB1-L2", "FOREIGN", "G6-STD-C1-U1-SUB1-L2-EX1", "Ex. 1: Scan the QR code then listen to the audio to finish the task below."],
  ["", "", "", "", "", "G6-STD-C1-U1-SUB1-L2-EX2", "Ex. 2: Scan the QR code and practice speaking with the interactive video."]
];

const inputClass = "w-full bg-slate-50 border border-slate-200 text-xs p-2.5 rounded-lg focus:outline-none";
const labelClass = "text-[10px] uppercase font-bold text-slate-500 block mb-1";

interface BookCatalogImportModalProps {
  open: boolean;
  onClose: () => void;
  curriculumId: number;
  /** Gọi lại sau khi có ít nhất 1 dòng import thành công — trang cha tự tải lại cây Sách/Unit/Sub Topic. */
  onImported: () => void;
}

/**
 * UC-72 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-13) —
 * mirror ReviewVideoQuestionImportPanel.tsx (UC-23) nhưng cần thêm 3 giá
 * trị mặc định (teacherType/examType/totalPoints) áp dụng cho Đề/Bài MỚI
 * tạo — file nguồn chỉ có tên/mã, không có 3 trường vận hành này.
 */
export default function BookCatalogImportModal({ open, onClose, curriculumId, onImported }: BookCatalogImportModalProps) {
  const { t } = useTranslation("lms-question-authoring");
  const inputRef = useRef<HTMLInputElement>(null);
  const [teacherType, setTeacherType] = useState<ExamTeacherType | "">("");
  const [examType, setExamType] = useState<ExamType | "">("");
  const [totalPoints, setTotalPoints] = useState("10");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [result, setResult] = useState<BookCatalogImportResponse | null>(null);

  const handleClose = () => {
    setResult(null);
    setError(null);
    onClose();
  };

  const handleDownloadTemplate = () => {
    const blob = buildXlsxTemplateBlob(TEMPLATE_HEADERS, TEMPLATE_SAMPLE_ROWS);
    downloadBlob(blob, "mau-danh-muc-sach.xlsx");
  };

  const handleFile = async (file: File | null) => {
    if (!file) return;
    if (!file.name.toLowerCase().endsWith(".xlsx")) {
      setError(t("bookCatalogPage.importModal.onlyXlsx"));
      return;
    }
    if (!teacherType || !examType || !totalPoints) {
      setError(t("bookCatalogPage.importModal.requiredFields"));
      return;
    }
    setSubmitting(true);
    setError(null);
    setResult(null);
    try {
      const res = await importBookCatalog(curriculumId, file, teacherType, examType, Number(totalPoints));
      setResult(res);
      if (res.successRows > 0) onImported();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t("bookCatalogPage.importModal.importFailed"));
    } finally {
      setSubmitting(false);
      if (inputRef.current) inputRef.current.value = "";
    }
  };

  return (
    <Modal
      open={open}
      onClose={handleClose}
      title={t("bookCatalogPage.importModal.title")}
      description={t("bookCatalogPage.importModal.description")}
    >
      <div className="space-y-3">
        <button
          type="button"
          onClick={handleDownloadTemplate}
          className="w-full flex items-center justify-center gap-2 border border-dashed border-slate-300 rounded-lg py-3 text-xs font-semibold text-slate-600 hover:bg-slate-50"
        >
          <Download className="w-4 h-4" />
          {t("bookCatalogPage.importModal.downloadTemplate")}
        </button>

        <div>
          <label className={labelClass}>{t("bookCatalogPage.importModal.teacherTypeLabel")}</label>
          <Select value={teacherType} onChange={(e) => setTeacherType(e.target.value as ExamTeacherType | "")} className={inputClass}>
            <option value="">{t("bookCatalogPage.importModal.teacherTypePlaceholder")}</option>
            {(["VIETNAMESE", "FOREIGN"] as ExamTeacherType[]).map((tt) => (
              <option key={tt} value={tt}>
                {t(`assignPage.teacherTypeLabels.${tt}`)}
              </option>
            ))}
          </Select>
        </div>

        <div>
          <label className={labelClass}>{t("bookCatalogPage.importModal.examTypeLabel")}</label>
          <Select value={examType} onChange={(e) => setExamType(e.target.value as ExamType | "")} className={inputClass}>
            <option value="">{t("bookCatalogPage.importModal.examTypePlaceholder")}</option>
            {(["REVIEW", "HOMEWORK"] as ExamType[]).map((et) => (
              <option key={et} value={et}>
                {t(`assignPage.examTypeLabels.${et}`)}
              </option>
            ))}
          </Select>
        </div>

        <div>
          <label className={labelClass}>{t("bookCatalogPage.importModal.totalPointsLabel")}</label>
          <input
            type="number"
            min="0"
            step="0.5"
            value={totalPoints}
            onChange={(e) => setTotalPoints(e.target.value)}
            className={inputClass}
          />
        </div>

        <p className="text-[10px] text-slate-400">{t("bookCatalogPage.importModal.hintDefaults")}</p>

        <button
          type="button"
          onClick={() => inputRef.current?.click()}
          disabled={submitting}
          className="w-full flex flex-col items-center justify-center gap-2 border-2 border-dashed border-slate-200 rounded-xl py-8 text-slate-500 hover:border-brand-red hover:bg-red-50/30 transition-colors disabled:opacity-50"
        >
          <UploadCloud className="w-6 h-6 text-brand-red" />
          <span className="text-xs font-bold text-slate-700">
            {submitting ? t("bookCatalogPage.importModal.importing") : t("bookCatalogPage.importModal.chooseFile")}
          </span>
        </button>
        <input ref={inputRef} type="file" accept=".xlsx" className="hidden" onChange={(e) => handleFile(e.target.files?.[0] ?? null)} />

        {error && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg">{error}</div>}

        {result && (
          <div className="space-y-3">
            <div className="grid grid-cols-3 gap-2 text-center">
              <div className="bg-slate-50 border border-slate-100 rounded-lg py-2">
                <div className="text-sm font-bold text-slate-800">{result.totalRows ?? "—"}</div>
                <div className="text-[10px] text-slate-400">{t("bookCatalogPage.importModal.stats.totalRows")}</div>
              </div>
              <div className="bg-emerald-50 border border-emerald-100 rounded-lg py-2">
                <div className="text-sm font-bold text-emerald-600">{result.successRows}</div>
                <div className="text-[10px] text-emerald-500">{t("bookCatalogPage.importModal.stats.successRows")}</div>
              </div>
              <div className="bg-rose-50 border border-rose-100 rounded-lg py-2">
                <div className="text-sm font-bold text-rose-600">{result.failedRows}</div>
                <div className="text-[10px] text-rose-500">{t("bookCatalogPage.importModal.stats.failedRows")}</div>
              </div>
            </div>

            {result.errorSummary.length > 0 && (
              <div className="border border-rose-100 rounded-lg overflow-hidden">
                <div className="bg-rose-50 px-3 py-1.5 text-[10px] font-bold text-rose-600 uppercase">
                  {t("bookCatalogPage.importModal.errorDetailTitle")}
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
