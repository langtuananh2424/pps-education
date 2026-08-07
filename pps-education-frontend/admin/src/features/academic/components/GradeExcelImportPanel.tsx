import React, { useRef, useState } from "react";
import { Download, UploadCloud } from "lucide-react";
import { ApiError } from "@/lib/apiClient";
import { downloadBlob } from "@/lib/xlsxTemplate";
import { GradeEvaluationComponentResponse, GradeImportResponse, downloadGradeImportTemplate, importGrades } from "../api";

interface GradeExcelImportPanelProps {
  classId: number;
  setupId: number;
  components: GradeEvaluationComponentResponse[];
  onImported: () => void;
}

/**
 * UC-53: cột header phải khớp CHÍNH XÁC tên thành phần điểm đã cấu hình cho kỳ này
 * (hoặc Overall/Level) — sai 1 ký tự là bị từ chối cả file (xem
 * GradeImportService.mapHeader, không tạo import_job nếu có cột không khớp). "Tải file
 * mẫu" gọi thẳng endpoint backend (GradeImportService#buildTemplate) thay vì tự dựng
 * phía FE — tránh lệch header giữa 2 nơi sinh file (đã từng thiếu tiêu đề "Họ và tên"
 * do FE tự dựng riêng một bản khác bản backend).
 */
export default function GradeExcelImportPanel({ classId, setupId, components, onImported }: GradeExcelImportPanelProps) {
  const inputRef = useRef<HTMLInputElement>(null);
  const [submitting, setSubmitting] = useState(false);
  const [downloadingTemplate, setDownloadingTemplate] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [result, setResult] = useState<GradeImportResponse | null>(null);

  const handleDownloadTemplate = async () => {
    setDownloadingTemplate(true);
    setError(null);
    try {
      const blob = await downloadGradeImportTemplate(classId, setupId);
      downloadBlob(blob, `mau-nhap-diem-lop-${classId}-setup-${setupId}.xlsx`);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Tải file mẫu thất bại.");
    } finally {
      setDownloadingTemplate(false);
    }
  };

  const handleFile = async (file: File | null) => {
    if (!file) return;
    if (!file.name.toLowerCase().endsWith(".xlsx")) {
      setError("Chỉ hỗ trợ file .xlsx.");
      return;
    }
    setSubmitting(true);
    setError(null);
    setResult(null);
    try {
      const res = await importGrades(classId, setupId, file);
      setResult(res);
      if (res.successRows > 0) onImported();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Import thất bại.");
    } finally {
      setSubmitting(false);
      if (inputRef.current) inputRef.current.value = "";
    }
  };

  return (
    <div className="bg-white border border-slate-200 rounded-2xl overflow-hidden">
      <div className="px-5 py-4 border-b border-slate-100">
        <h3 className="text-sm font-bold text-slate-800 font-display">Nhập điểm thi qua Excel</h3>
        <p className="text-sm text-slate-500 mt-1">
          Tải lên bảng điểm đã hoàn thiện (cột A = mã học viên, các cột sau = tên kỹ năng/thành phần điểm khớp đúng tên đã cấu hình cho kỳ
          này, cộng 2 cột tùy chọn "Overall"/"Tổng điểm" và "Level"/"Cấp độ"). Hệ thống chỉ lưu lại giá trị Overall/Level bạn đã tự tính —
          không tự tính lại theo công thức nào cả.
        </p>
      </div>

      <div className="p-5">
        <button
          type="button"
          onClick={handleDownloadTemplate}
          disabled={components.length === 0 || downloadingTemplate}
          className="w-full flex items-center justify-center gap-2 border border-dashed border-slate-300 rounded-lg py-3 text-sm font-semibold text-slate-600 hover:bg-slate-50 disabled:opacity-50 disabled:cursor-not-allowed"
        >
          <Download className="w-4 h-4" />
          {downloadingTemplate ? "Đang tải file mẫu..." : "Tải file mẫu (.xlsx) — đã điền sẵn mã học viên của lớp này"}
        </button>

        <button
          type="button"
          onClick={() => inputRef.current?.click()}
          disabled={submitting}
          className="w-full flex flex-col items-center justify-center gap-2 border-2 border-dashed border-slate-200 rounded-xl py-10 text-slate-500 hover:border-brand-orange hover:bg-orange-50/30 transition-colors disabled:opacity-50 mt-3"
        >
          <UploadCloud className="w-7 h-7 text-brand-orange" />
          <span className="text-sm font-bold text-slate-700">{submitting ? "Đang nhập..." : "Click để chọn file bảng điểm (.xlsx)"}</span>
          <span className="text-sm text-slate-400">Chỉ hỗ trợ .xlsx</span>
        </button>
        <input ref={inputRef} type="file" accept=".xlsx" className="hidden" onChange={(e) => handleFile(e.target.files?.[0] ?? null)} />

        {error && <div className="text-sm text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg mt-4">{error}</div>}

        {result && (
          <div className="mt-4 space-y-3">
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
                <div className="bg-rose-50 px-3 py-1.5 text-sm font-bold text-rose-600 uppercase">Chi tiết lỗi từng dòng</div>
                <div className="max-h-48 overflow-y-auto divide-y divide-slate-100">
                  {result.errorSummary.map((e, i) => (
                    <div key={i} className="px-3 py-1.5 text-sm flex gap-2">
                      <span className="font-mono font-bold text-slate-400 shrink-0">Dòng {e.row}</span>
                      <span className="text-slate-600">{e.reason}</span>
                    </div>
                  ))}
                </div>
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  );
}
