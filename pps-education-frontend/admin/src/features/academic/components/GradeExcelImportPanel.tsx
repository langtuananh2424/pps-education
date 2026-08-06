import React, { useRef, useState } from "react";
import { Download, UploadCloud } from "lucide-react";
import { ApiError } from "@/lib/apiClient";
import { buildXlsxTemplateBlob, downloadBlob } from "@/lib/xlsxTemplate";
import { ClassEnrollmentResponse, GradeEvaluationComponentResponse, GradeImportResponse, importGrades } from "../api";

interface GradeExcelImportPanelProps {
  classId: number;
  setupId: number;
  components: GradeEvaluationComponentResponse[];
  enrollments: ClassEnrollmentResponse[];
  onImported: () => void;
}

/**
 * UC-53: cột header phải khớp CHÍNH XÁC tên thành phần điểm đã cấu hình cho kỳ này
 * (hoặc Overall/Level) — sai 1 ký tự là bị từ chối cả file (xem
 * GradeImportService.mapHeader, không tạo import_job nếu có cột không khớp). Vì vậy
 * "Tải file mẫu" ở đây KHÔNG dùng 1 mẫu cột cố định như các import khác — tự dựng theo
 * đúng components/enrollments đang truyền vào (đúng kỳ + đúng lớp đang chọn), điền sẵn cột
 * A = mã học viên của mọi học viên ACTIVE trong lớp, đảm bảo tải mẫu về rồi nộp lại chắc
 * chắn khớp header ngay từ đầu.
 */
export default function GradeExcelImportPanel({ classId, setupId, components, enrollments, onImported }: GradeExcelImportPanelProps) {
  const inputRef = useRef<HTMLInputElement>(null);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [result, setResult] = useState<GradeImportResponse | null>(null);

  const handleDownloadTemplate = () => {
    // Cột A PHẢI là mã học viên (GradeImportService.importRow đọc cố định vị trí này) — cột B "Họ và
    // tên" chỉ hiển thị, GradeImportService.mapHeader bỏ qua qua IGNORED_ALIASES (khớp đúng chuỗi
    // "ho va ten" backend đã khai — không tự đổi chữ khác đi).
    const headers = ["Mã học viên", "Họ và tên", ...components.map((c) => c.name), "Overall", "Level", "Nhận xét", "Ghi chú"];
    const sampleRows = enrollments
      .filter((en) => en.status === "ACTIVE")
      .map((en) => [en.studentCode, en.studentFullName, ...components.map(() => ""), "", "", "", ""]);
    const blob = buildXlsxTemplateBlob(headers, sampleRows);
    downloadBlob(blob, `mau-nhap-diem-lop-${classId}-setup-${setupId}.xlsx`);
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
        <p className="text-xs text-slate-500 mt-1">
          Tải lên bảng điểm đã hoàn thiện (cột A = mã học viên, các cột sau = tên kỹ năng/thành phần điểm khớp đúng tên đã cấu hình cho kỳ
          này, cộng 2 cột tùy chọn "Overall"/"Tổng điểm" và "Level"/"Cấp độ"). Hệ thống chỉ lưu lại giá trị Overall/Level bạn đã tự tính —
          không tự tính lại theo công thức nào cả.
        </p>
      </div>

      <div className="p-5">
        <button
          type="button"
          onClick={handleDownloadTemplate}
          disabled={components.length === 0}
          className="w-full flex items-center justify-center gap-2 border border-dashed border-slate-300 rounded-lg py-3 text-xs font-semibold text-slate-600 hover:bg-slate-50 disabled:opacity-50 disabled:cursor-not-allowed"
        >
          <Download className="w-4 h-4" />
          Tải file mẫu (.xlsx) — đã điền sẵn mã học viên của lớp này
        </button>

        <button
          type="button"
          onClick={() => inputRef.current?.click()}
          disabled={submitting}
          className="w-full flex flex-col items-center justify-center gap-2 border-2 border-dashed border-slate-200 rounded-xl py-10 text-slate-500 hover:border-brand-orange hover:bg-orange-50/30 transition-colors disabled:opacity-50 mt-3"
        >
          <UploadCloud className="w-7 h-7 text-brand-orange" />
          <span className="text-sm font-bold text-slate-700">{submitting ? "Đang nhập..." : "Click để chọn file bảng điểm (.xlsx)"}</span>
          <span className="text-[11px] text-slate-400">Chỉ hỗ trợ .xlsx</span>
        </button>
        <input ref={inputRef} type="file" accept=".xlsx" className="hidden" onChange={(e) => handleFile(e.target.files?.[0] ?? null)} />

        {error && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg mt-4">{error}</div>}

        {result && (
          <div className="mt-4 space-y-3">
            <div className="grid grid-cols-3 gap-2 text-center">
              <div className="bg-slate-50 border border-slate-100 rounded-lg py-2">
                <div className="text-sm font-bold text-slate-800">{result.totalRows ?? "—"}</div>
                <div className="text-[10px] text-slate-400">Tổng số dòng</div>
              </div>
              <div className="bg-emerald-50 border border-emerald-100 rounded-lg py-2">
                <div className="text-sm font-bold text-emerald-600">{result.successRows}</div>
                <div className="text-[10px] text-emerald-500">Thành công</div>
              </div>
              <div className="bg-rose-50 border border-rose-100 rounded-lg py-2">
                <div className="text-sm font-bold text-rose-600">{result.failedRows}</div>
                <div className="text-[10px] text-rose-500">Lỗi</div>
              </div>
            </div>
            {result.errorSummary.length > 0 && (
              <div className="border border-rose-100 rounded-lg overflow-hidden">
                <div className="bg-rose-50 px-3 py-1.5 text-[10px] font-bold text-rose-600 uppercase">Chi tiết lỗi từng dòng</div>
                <div className="max-h-48 overflow-y-auto divide-y divide-slate-100">
                  {result.errorSummary.map((e, i) => (
                    <div key={i} className="px-3 py-1.5 text-xs flex gap-2">
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
