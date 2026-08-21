import React, { useRef, useState } from "react";
import { Download, UploadCloud } from "lucide-react";
import { useTranslation } from "react-i18next";
import { ApiError } from "@/lib/apiClient";
import { buildXlsxTemplateBlob, downloadBlob } from "@/lib/xlsxTemplate";
import { ReviewVideoQuestionImportResponse, importReviewVideoConnectionQuestions, importReviewVideoQuestions } from "../api";

const REFLEX_SAMPLE_ROWS: string[][] = [
  ["30", "Describe your favorite hobby.", "60", "3", "1"],
  ["120", "What did you do last weekend?", "45", "", "2"]
];

const CONNECTION_SAMPLE_ROWS: string[][] = [
  ["What is the capital of France?", "London", "Paris", "Berlin", "Madrid", "", "B", "1"]
];

interface ReviewVideoQuestionImportPanelProps {
  videoId: number;
  videoType: "REFLEX" | "CONNECTION";
  onImported: (res: ReviewVideoQuestionImportResponse) => void;
}

/**
 * UC-23 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng) — soạn hàng
 * loạt câu hỏi Kho Video Ôn tập bằng file mẫu Excel (.xlsx), mirror
 * QuestionImportPanel.tsx (Kho đề, UC-40) nhưng bỏ toggle định dạng (chỉ
 * .xlsx, không có .docx — đã xác nhận với người dùng).
 */
export default function ReviewVideoQuestionImportPanel({ videoId, videoType, onImported }: ReviewVideoQuestionImportPanelProps) {
  const { t } = useTranslation("lms-review-video");
  const inputRef = useRef<HTMLInputElement>(null);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [result, setResult] = useState<ReviewVideoQuestionImportResponse | null>(null);

  const headers = videoType === "REFLEX"
    ? (t("importPanel.reflexHeaders", { returnObjects: true }) as string[])
    : (t("importPanel.connectionHeaders", { returnObjects: true }) as string[]);
  const sampleRows = videoType === "REFLEX" ? REFLEX_SAMPLE_ROWS : CONNECTION_SAMPLE_ROWS;

  const handleDownloadTemplate = () => {
    const blob = buildXlsxTemplateBlob(headers, sampleRows);
    downloadBlob(blob, videoType === "REFLEX" ? t("importPanel.reflexTemplateFileName") : t("importPanel.connectionTemplateFileName"));
  };

  const handleFile = async (file: File | null) => {
    if (!file) return;
    if (!file.name.toLowerCase().endsWith(".xlsx")) {
      setError(t("importPanel.onlyXlsx"));
      return;
    }
    setSubmitting(true);
    setError(null);
    setResult(null);
    try {
      const res = videoType === "REFLEX"
        ? await importReviewVideoQuestions(videoId, file)
        : await importReviewVideoConnectionQuestions(videoId, file);
      setResult(res);
      if (res.successRows > 0) onImported(res);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t("importPanel.importFailed"));
    } finally {
      setSubmitting(false);
      if (inputRef.current) inputRef.current.value = "";
    }
  };

  return (
    <div className="space-y-3">
      <button
        type="button"
        onClick={handleDownloadTemplate}
        className="w-full flex items-center justify-center gap-2 border border-dashed border-slate-300 rounded-lg py-3 text-xs font-semibold text-slate-600 hover:bg-slate-50"
      >
        <Download className="w-4 h-4" />
        {t("importPanel.downloadTemplate")}
      </button>

      <button
        type="button"
        onClick={() => inputRef.current?.click()}
        disabled={submitting}
        className="w-full flex flex-col items-center justify-center gap-2 border-2 border-dashed border-slate-200 rounded-xl py-8 text-slate-500 hover:border-brand-orange hover:bg-orange-50/30 transition-colors disabled:opacity-50"
      >
        <UploadCloud className="w-6 h-6 text-brand-orange" />
        <span className="text-xs font-bold text-slate-700">{submitting ? t("importPanel.importing") : t("importPanel.clickToChoose")}</span>
      </button>
      <input ref={inputRef} type="file" accept=".xlsx" className="hidden" onChange={(e) => handleFile(e.target.files?.[0] ?? null)} />

      {error && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg">{error}</div>}

      {result && (
        <div className="space-y-3">
          <div className="grid grid-cols-3 gap-2 text-center">
            <div className="bg-slate-50 border border-slate-100 rounded-lg py-2">
              <div className="text-sm font-bold text-slate-800">{result.totalRows ?? "—"}</div>
              <div className="text-[10px] text-slate-400">{t("importPanel.stats.totalRows")}</div>
            </div>
            <div className="bg-emerald-50 border border-emerald-100 rounded-lg py-2">
              <div className="text-sm font-bold text-emerald-600">{result.successRows}</div>
              <div className="text-[10px] text-emerald-500">{t("importPanel.stats.successRows")}</div>
            </div>
            <div className="bg-rose-50 border border-rose-100 rounded-lg py-2">
              <div className="text-sm font-bold text-rose-600">{result.failedRows}</div>
              <div className="text-[10px] text-rose-500">{t("importPanel.stats.failedRows")}</div>
            </div>
          </div>

          {result.errorSummary.length > 0 && (
            <div className="border border-rose-100 rounded-lg overflow-hidden">
              <div className="bg-rose-50 px-3 py-1.5 text-[10px] font-bold text-rose-600 uppercase">{t("importPanel.errorDetailTitle")}</div>
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
  );
}
