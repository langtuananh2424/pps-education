import React, { useState } from "react";
import { AlertTriangle, Check, FileSpreadsheet, Upload } from "lucide-react";
import { ApiError } from "@/lib/apiClient";
import Button from "@/components/ui/Button";
import { ClassScheduleImportResponse, importClassSchedule } from "../api";

interface ImportScheduleFormProps {
  classId: number;
  onDone: () => void;
  onCancel: () => void;
}

/** UC-57: Nhập lịch học từ file Excel — upload thật (multipart) tới BE, BE tự đọc/kiểm tra trùng lịch, không giả lập parse ở FE. */
export default function ImportScheduleForm({ classId, onDone, onCancel }: ImportScheduleFormProps) {
  const [file, setFile] = useState<File | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [result, setResult] = useState<ClassScheduleImportResponse | null>(null);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    if (!file) {
      setError("Vui lòng chọn 1 tệp Excel (.xlsx).");
      return;
    }
    setSubmitting(true);
    try {
      const res = await importClassSchedule(classId, file);
      setResult(res);
      if (res.failedRows === 0) onDone();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Nhập lịch học từ Excel thất bại.");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <form onSubmit={handleSubmit} className="bg-emerald-50/40 border border-emerald-200 rounded-xl p-4 space-y-3">
      {error && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg">{error}</div>}

      <label className="border-2 border-dashed border-slate-300 hover:border-emerald-400 bg-white rounded-xl p-5 text-center cursor-pointer block transition-all">
        <input type="file" accept=".xlsx" className="hidden" onChange={(e) => setFile(e.target.files?.[0] ?? null)} />
        <Upload className="w-6 h-6 text-slate-500 mx-auto mb-1.5" />
        {file ? (
          <p className="text-xs font-bold text-slate-800">{file.name}</p>
        ) : (
          <p className="text-xs font-bold text-slate-600">Nhấn để chọn tệp Excel (.xlsx)</p>
        )}
      </label>

      {result && (
        <div className={`p-3 rounded-lg text-xs space-y-1.5 border ${result.failedRows === 0 ? "bg-emerald-50 border-emerald-200" : "bg-rose-50 border-rose-200"}`}>
          <div className="flex items-center gap-1.5 font-bold">
            {result.failedRows === 0 ? <Check className="w-3.5 h-3.5 text-emerald-600" /> : <AlertTriangle className="w-3.5 h-3.5 text-rose-600" />}
            <span>
              Thành công {result.successRows} / {result.totalRows ?? "?"} dòng
              {result.failedRows > 0 && <span className="text-rose-600"> — lỗi {result.failedRows} dòng</span>}
            </span>
          </div>
          {result.errorSummary && result.errorSummary.length > 0 && (
            <div className="space-y-0.5 max-h-28 overflow-y-auto">
              {result.errorSummary.map((err, i) => (
                <p key={i} className="text-[10px] text-rose-600">
                  {JSON.stringify(err)}
                </p>
              ))}
            </div>
          )}
        </div>
      )}

      <div className="flex gap-2">
        <Button type="button" variant="secondary" size="sm" onClick={onCancel}>
          Đóng
        </Button>
        <Button type="submit" variant="primary" size="sm" disabled={submitting || !file}>
          <FileSpreadsheet className="w-3.5 h-3.5" />
          {submitting ? "Đang nhập khẩu..." : "Nhập khẩu Excel"}
        </Button>
      </div>
    </form>
  );
}
