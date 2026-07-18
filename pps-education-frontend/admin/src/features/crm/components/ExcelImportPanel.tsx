import React, { useState } from "react";
import { AlertCircle, FileSpreadsheet, UploadCloud } from "lucide-react";

interface ImportReport {
  total: number;
  success: number;
  failed: number;
  duplicates: string[];
}

interface ExcelImportPanelProps {
  onImport: () => void;
}

export default function ExcelImportPanel({ onImport }: ExcelImportPanelProps) {
  const [fileName, setFileName] = useState("");
  const [importReport, setImportReport] = useState<ImportReport | null>(null);

  const handleSimulateUpload = () => {
    setFileName("Duyet_Danh_Sach_NghiaTan_Khoi3.xlsx");
    setTimeout(() => {
      setImportReport({
        total: 15,
        success: 12,
        failed: 3,
        duplicates: ["Nguyễn Minh Khang (SĐT trùng lặp STU-001)", "Trần Mai Chi (Trùng lặp họ tên + ngày sinh STU-002)"]
      });
      onImport();
    }, 1200);
  };

  return (
    <div className="space-y-6">
      <div className="bg-white p-6 rounded-xl border border-slate-200 shadow-soft space-y-4">
        <h3 className="text-sm font-bold text-slate-800 font-display">Nhập học theo lô từ Trường liên kết (UC-35)</h3>
        <p className="text-xs text-slate-500 leading-normal">
          Khi nhận danh sách học sinh theo lớp từ các trường liên kết (như Tiểu học Nghĩa Tân), hệ thống hỗ trợ import file Excel để tự
          động kiểm duyệt trùng lặp (trùng mã học sinh hoặc họ tên trùng ngày sinh) trước khi gán lớp hàng loạt.
        </p>

        <div
          onClick={handleSimulateUpload}
          className="border-2 border-dashed border-slate-300 rounded-xl p-8 flex flex-col items-center justify-center gap-3 cursor-pointer hover:bg-slate-50/60 hover:border-brand-orange/50 transition-all text-center"
        >
          <UploadCloud className="w-10 h-10 text-brand-orange shrink-0 animate-bounce" />
          <div>
            <span className="text-xs font-bold text-slate-700 block">Click để tải lên tệp tin danh sách mẫu (Excel)</span>
            <span className="text-[11px] text-slate-400 mt-1 block">Hỗ trợ định dạng: .xlsx, .xls, .csv</span>
          </div>
        </div>

        {fileName && (
          <div className="p-3 bg-slate-50 border rounded-lg flex items-center justify-between text-xs animate-in fade-in">
            <div className="flex items-center gap-2">
              <FileSpreadsheet className="w-5 h-5 text-emerald-600 shrink-0" />
              <span className="font-semibold text-slate-800 truncate max-w-sm">{fileName}</span>
            </div>
            <span className="text-[10px] bg-emerald-100 text-emerald-800 px-2 py-0.5 rounded font-bold font-mono">Phân tích thành công</span>
          </div>
        )}

        {importReport && (
          <div className="space-y-4 border-t border-slate-100 pt-4 animate-in slide-in-from-bottom-2 duration-150">
            <h4 className="text-xs font-bold text-slate-800 font-display">Biên bản đối chiếu & Nhập hồ sơ hàng loạt</h4>

            <div className="grid grid-cols-3 gap-4 text-center">
              <div className="bg-slate-50 p-3 rounded border">
                <span className="text-[10px] text-slate-400 font-bold block uppercase">Tổng dòng đọc được</span>
                <span className="text-xl font-bold text-slate-800 font-mono">{importReport.total} dòng</span>
              </div>
              <div className="bg-emerald-50 p-3 rounded border border-emerald-100">
                <span className="text-[10px] text-emerald-600 font-bold block uppercase">Hợp lệ & Gán lớp</span>
                <span className="text-xl font-bold text-emerald-600 font-mono">+{importReport.success} học sinh</span>
              </div>
              <div className="bg-rose-50 p-3 rounded border border-rose-100">
                <span className="text-[10px] text-rose-600 font-bold block uppercase">Cảnh báo trùng lặp</span>
                <span className="text-xl font-bold text-rose-600 font-mono">{importReport.failed} em bị loại</span>
              </div>
            </div>

            <div className="bg-rose-50/50 p-4 rounded-lg border border-rose-100 space-y-2">
              <div className="flex items-center gap-1.5 text-xs font-bold text-rose-600">
                <AlertCircle className="w-4 h-4 shrink-0" />
                <span>Lý do loại trừ trùng lặp (UNIQUE constraint check):</span>
              </div>
              <ul className="list-disc pl-5 text-[11px] text-slate-600 space-y-1">
                {importReport.duplicates.map((dup, dIdx) => (
                  <li key={dIdx} className="font-semibold">
                    {dup}
                  </li>
                ))}
              </ul>
              <p className="text-[10px] text-slate-400 mt-1">*Hệ thống tự động bỏ qua 2 dòng trên để bảo đảm tính thống nhất và không bị sai lệch cơ sở dữ liệu.</p>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
