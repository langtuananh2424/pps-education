import React, { useRef, useState } from "react";
import { Download, UploadCloud } from "lucide-react";
import { ApiError } from "@/lib/apiClient";
import { buildXlsxTemplateBlob, downloadBlob } from "@/lib/xlsxTemplate";
import { QuestionImportResponse, QuestionImportedRow, downloadExamQuestionImportWordTemplate, downloadQuestionImportWordTemplate, importExamQuestions, importQuestions } from "../api";

type FileFormat = "xlsx" | "docx";

/**
 * Cột cố định của mẫu Excel — khớp 1:1 vị trí đọc trong
 * ExcelQuestionRowParser.java (A→N). 5 dòng ví dụ demo đủ 5 loại UI hỗ trợ,
 * y hệt nội dung buildWordTemplate() bên backend để 2 định dạng nhất quán.
 */
const EXCEL_HEADERS = [
  "Loại câu hỏi", "Độ khó", "Nội dung", "Đáp án A", "Đáp án B", "Đáp án C", "Đáp án D",
  "Đáp án đúng", "URL Audio", "URL Hình ảnh", "Transcript/Từ khóa phát âm", "Điểm", "Giải thích", "Tags"
];

const EXCEL_SAMPLE_ROWS: string[][] = [
  ["TRAC_NGHIEM", "EASY", "What is the capital of France?", "London", "Paris", "Berlin", "Madrid", "B",
    "", "", "", "1", "Paris là thủ đô nước Pháp.", "geo,easy"],
  ["TRAC_NGHIEM_VOICE", "MEDIUM", "Listen and choose the word you hear.", "ship", "sheep", "chip", "cheap", "B",
    "https://example-r2.dev/lms/questions/audio/mau.mp3", "", "sheep", "1", "", ""],
  ["DIEN_TU", "", "She ___ (go) to school every day.", "", "", "", "", "goes",
    "", "", "", "1", "Hiện tại đơn, ngôi thứ 3 số ít.", ""],
  ["TU_LUAN", "HARD", "Write a 150-word essay about your favorite hobby.", "", "", "", "", "",
    "", "https://example-r2.dev/lms/questions/images/mau.png", "", "2", "Chấm theo thang điểm nội dung/ngữ pháp/từ vựng.", ""],
  ["SPEAKING", "", "Read the following sentence aloud.", "", "", "", "", "",
    "", "", "enthusiasm, literature, variety", "1", "", ""]
];

interface QuestionImportPanelProps {
  bankId?: number;
  examId?: number;
  onImported: (createdQuestions: QuestionImportedRow[]) => void;
}

/**
 * UC-40 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-07-30):
 * soạn đề nhanh — panel dùng chung cho cả trang "Ngân hàng câu hỏi" (import
 * độc lập vào 1 bank) lẫn bước "Soạn đề" trong "Soạn & giao đề" (import rồi
 * tự gắn luôn vào đề đang soạn — xem CreateAndAssignExerciseModal.tsx).
 * KHÔNG dùng chung components/ui/ImportExcelButton.tsx vì cái đó cứng 1
 * định dạng .xlsx và đang dùng ở 7 nơi khác — hỗ trợ thêm .docx ở đây sẽ
 * phải sửa hành vi chung không cần thiết cho các nơi kia.
 */
export default function QuestionImportPanel({ bankId, examId, onImported }: QuestionImportPanelProps) {
  const [format, setFormat] = useState<FileFormat>("xlsx");
  const inputRef = useRef<HTMLInputElement>(null);
  const [submitting, setSubmitting] = useState(false);
  const [downloadingTemplate, setDownloadingTemplate] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [result, setResult] = useState<QuestionImportResponse | null>(null);

  const handleDownloadTemplate = async () => {
    setError(null);
    if (format === "xlsx") {
      const blob = buildXlsxTemplateBlob(EXCEL_HEADERS, EXCEL_SAMPLE_ROWS);
      downloadBlob(blob, "mau-soan-cau-hoi.xlsx");
      return;
    }
    setDownloadingTemplate(true);
    try {
      const blob = await (examId ? downloadExamQuestionImportWordTemplate() : downloadQuestionImportWordTemplate());
      downloadBlob(blob, "mau-soan-cau-hoi.docx");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Tải file mẫu thất bại.");
    } finally {
      setDownloadingTemplate(false);
    }
  };

  const handleFile = async (file: File | null) => {
    if (!file) return;
    const lower = file.name.toLowerCase();
    if (!lower.endsWith(".xlsx") && !lower.endsWith(".docx")) {
      setError("Chỉ hỗ trợ file .xlsx hoặc .docx.");
      return;
    }
    setSubmitting(true);
    setError(null);
    setResult(null);
    try {
      const res = examId
        ? await importExamQuestions(examId, file)
        : bankId
          ? await importQuestions(bankId, file)
          : (() => { throw new Error("Thiếu ngữ cảnh Đề/Ngân hàng câu hỏi."); })();
      setResult(res);
      if (res.successRows > 0) onImported(res.createdQuestions);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Import thất bại.");
    } finally {
      setSubmitting(false);
      if (inputRef.current) inputRef.current.value = "";
    }
  };

  return (
    <div className="space-y-3">
      <div className="flex items-center gap-1.5 bg-slate-100 p-1 rounded-lg w-fit">
        {(["xlsx", "docx"] as FileFormat[]).map((f) => (
          <button
            key={f}
            type="button"
            onClick={() => setFormat(f)}
            className={`text-sm font-bold px-3 py-1.5 rounded-md transition-all ${
              format === f ? "bg-white text-brand-red shadow-xs" : "text-slate-500 hover:text-slate-700"
            }`}
          >
            {f === "xlsx" ? "Excel (.xlsx)" : "Word (.docx)"}
          </button>
        ))}
      </div>

      <button
        type="button"
        onClick={handleDownloadTemplate}
        disabled={downloadingTemplate}
        className="w-full flex items-center justify-center gap-2 border border-dashed border-slate-300 rounded-lg py-3 text-sm font-semibold text-slate-600 hover:bg-slate-50 disabled:opacity-50"
      >
        <Download className="w-4 h-4" />
        {downloadingTemplate ? "Đang tải..." : `Tải file mẫu (.${format})`}
      </button>

      <button
        type="button"
        onClick={() => inputRef.current?.click()}
        disabled={submitting}
        className="w-full flex flex-col items-center justify-center gap-2 border-2 border-dashed border-slate-200 rounded-xl py-8 text-slate-500 hover:border-brand-orange hover:bg-orange-50/30 transition-colors disabled:opacity-50"
      >
        <UploadCloud className="w-6 h-6 text-brand-orange" />
        <span className="text-sm font-bold text-slate-700">{submitting ? "Đang nhập..." : `Click để chọn file (.${format})`}</span>
      </button>
      <input ref={inputRef} type="file" accept=".xlsx,.docx" className="hidden" onChange={(e) => handleFile(e.target.files?.[0] ?? null)} />

      {error && <div className="text-sm text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg">{error}</div>}

      {result && (
        <div className="space-y-3">
          <div className="grid grid-cols-3 gap-2 text-center">
            <div className="bg-slate-50 border border-slate-100 rounded-lg py-2">
              <div className="text-sm font-bold text-slate-800">{result.totalRows ?? "—"}</div>
              <div className="text-sm text-slate-400">Tổng số dòng</div>
            </div>
            <div className="bg-emerald-50 border border-emerald-100 rounded-lg py-2">
              <div className="text-sm font-bold text-emerald-600">{result.successRows}</div>
              <div className="text-sm text-emerald-500">Thành công</div>
            </div>
            <div className="bg-rose-50 border border-rose-100 rounded-lg py-2">
              <div className="text-sm font-bold text-rose-600">{result.failedRows}</div>
              <div className="text-sm text-rose-500">Lỗi</div>
            </div>
          </div>

          {result.errorSummary.length > 0 && (
            <div className="border border-rose-100 rounded-lg overflow-hidden">
              <div className="bg-rose-50 px-3 py-1.5 text-sm font-bold text-rose-600 uppercase">Chi tiết lỗi từng dòng/block</div>
              <div className="max-h-48 overflow-y-auto divide-y divide-slate-100">
                {result.errorSummary.map((e, i) => (
                  <div key={i} className="px-3 py-1.5 text-sm flex gap-2">
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
  );
}
