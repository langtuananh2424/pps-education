import React, { useState } from "react";
import { Download, FileSpreadsheet, Upload } from "lucide-react";
import Modal from "./Modal";
import Button from "./Button";
import { ApiError } from "@/lib/apiClient";
import { buildXlsxTemplateBlob, downloadBlob } from "@/lib/xlsxTemplate";

export interface ImportErrorRow {
  row: number;
  reason: string;
}

export interface ImportCredentialRow {
  row: number;
  username: string;
  temporaryPassword: string;
}

export interface ImportResultLike {
  totalRows: number | null;
  successRows: number;
  failedRows: number;
  status: string;
  errorSummary: ImportErrorRow[];
  generatedCredentials?: ImportCredentialRow[];
}

interface ImportExcelButtonProps {
  title: string;
  templateFileName: string;
  templateHeaders: string[];
  templateSampleRow?: string[];
  uploadFn: (file: File) => Promise<ImportResultLike>;
  onImported?: () => void;
}

/**
 * Nút Import Excel dùng chung — tải mẫu (.xlsx tự dựng, không dùng thư viện
 * đọc/ghi Excel ngoài vì bản trên npm đều có lỗ hổng chưa vá), chọn file,
 * gọi thẳng API import thật (BE tự parse), hiển thị kết quả từng dòng.
 * Không có bước "preview nội dung file trước khi gửi" — đã xác nhận với
 * người dùng đánh đổi lấy việc không phải thêm dependency rủi ro.
 */
export default function ImportExcelButton({ title, templateFileName, templateHeaders, templateSampleRow, uploadFn, onImported }: ImportExcelButtonProps) {
  const [open, setOpen] = useState(false);
  const [file, setFile] = useState<File | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [result, setResult] = useState<ImportResultLike | null>(null);

  const reset = () => {
    setFile(null);
    setError(null);
    setResult(null);
  };

  const handleClose = () => {
    setOpen(false);
    reset();
  };

  const handleDownloadTemplate = () => {
    const blob = buildXlsxTemplateBlob(templateHeaders, templateSampleRow ? [templateSampleRow] : []);
    downloadBlob(blob, templateFileName);
  };

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const selected = e.target.files?.[0] ?? null;
    setError(null);
    setResult(null);
    if (selected && !selected.name.toLowerCase().endsWith(".xlsx")) {
      setError("Chỉ chấp nhận file .xlsx (đúng định dạng file mẫu tải về).");
      setFile(null);
      return;
    }
    setFile(selected);
  };

  const handleSubmit = async () => {
    if (!file) return;
    setSubmitting(true);
    setError(null);
    try {
      const res = await uploadFn(file);
      setResult(res);
      if (res.successRows > 0) onImported?.();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Import thất bại.");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <>
      <Button variant="secondary" size="sm" onClick={() => setOpen(true)} className="whitespace-nowrap shrink-0">
        <FileSpreadsheet className="w-3.5 h-3.5" />
        Import Excel
      </Button>

      {open && (
        <Modal open onClose={handleClose} title={title} description="Tải file mẫu, điền dữ liệu rồi tải lên để nhập hàng loạt." size="lg">
          <div className="space-y-4">
            <button
              type="button"
              onClick={handleDownloadTemplate}
              className="w-full flex items-center justify-center gap-2 border border-dashed border-slate-300 rounded-lg py-3 text-xs font-semibold text-slate-600 hover:bg-slate-50"
            >
              <Download className="w-4 h-4" />
              Tải file mẫu (.xlsx)
            </button>

            <div>
              <label className="text-[10px] uppercase font-bold tracking-wider text-slate-500 block mb-1">Tải lên file đã điền</label>
              <input
                type="file"
                accept=".xlsx"
                onChange={handleFileChange}
                className="w-full bg-slate-50 border border-slate-200 text-xs p-2.5 rounded-lg focus:outline-none file:mr-3 file:px-2.5 file:py-1 file:rounded-md file:border-0 file:bg-slate-200 file:text-slate-700 file:text-[11px] file:font-semibold"
              />
            </div>

            {error && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg">{error}</div>}

            {result && (
              <div className="space-y-3">
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

                {result.generatedCredentials && result.generatedCredentials.length > 0 && (
                  <div className="border border-amber-200 rounded-lg overflow-hidden">
                    <div className="bg-amber-50 px-3 py-1.5 text-[10px] font-bold text-amber-700 uppercase">
                      Mật khẩu tạm — chỉ hiển thị 1 lần, lưu lại ngay
                    </div>
                    <div className="max-h-48 overflow-y-auto divide-y divide-slate-100">
                      {result.generatedCredentials.map((c, i) => (
                        <div key={i} className="px-3 py-1.5 text-xs flex gap-3 font-mono">
                          <span className="text-slate-400 shrink-0">Dòng {c.row}</span>
                          <span className="font-bold text-slate-700">{c.username}</span>
                          <span className="text-amber-700">{c.temporaryPassword}</span>
                        </div>
                      ))}
                    </div>
                  </div>
                )}
              </div>
            )}

            <div className="flex justify-end gap-2 border-t border-slate-100 pt-3">
              <Button type="button" variant="secondary" size="sm" onClick={handleClose}>
                {result ? "Đóng" : "Hủy"}
              </Button>
              {!result && (
                <Button type="button" variant="primary" size="sm" disabled={!file || submitting} onClick={handleSubmit}>
                  <Upload className="w-3.5 h-3.5" />
                  {submitting ? "Đang nhập..." : "Xác nhận nhập"}
                </Button>
              )}
            </div>
          </div>
        </Modal>
      )}
    </>
  );
}
