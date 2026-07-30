import React from "react";
import { Check, CheckCircle, Wallet } from "lucide-react";
import { Invoice } from "@/types";

interface InvoiceQrPanelProps {
  invoice: Invoice | null;
  onSimulatePayment: (invoiceId: string) => void;
}

export default function InvoiceQrPanel({ invoice, onSimulatePayment }: InvoiceQrPanelProps) {
  if (!invoice) {
    return (
      <div className="h-64 border border-dashed rounded-xl flex flex-col items-center justify-center text-slate-400 text-xs italic gap-1.5 text-center p-4">
        <Wallet className="w-6 h-6 text-slate-300 animate-bounce" />
        <span>Nhấp chọn mã QR trên danh sách hóa đơn học phí để xuất QR đóng học trực tuyến.</span>
      </div>
    );
  }

  return (
    <div className="space-y-4 text-center">
      <div className="border-b pb-2 text-left">
        <span className="text-[10px] font-mono font-bold text-slate-400">CHI TIẾT THANH TOÁN</span>
        <h3 className="text-xs font-bold text-slate-800">
          {invoice.id} - {invoice.studentName}
        </h3>
      </div>

      <div className="flex flex-col items-center p-4 bg-slate-50 rounded-lg border border-slate-100">
        <span className="text-[10px] font-bold text-slate-400 block uppercase tracking-widest font-mono mb-2">Mã QR ngân hàng động (VietQR API)</span>

        <img src={invoice.qrUrl} alt="VietQR Học phí" className="w-40 h-44 border bg-white p-2 rounded-lg object-contain shadow-xs" referrerPolicy="no-referrer" />

        <div className="text-xs font-semibold text-slate-700 mt-3 font-mono">Học phí: {invoice.finalAmount.toLocaleString("vi-VN")} đ</div>
        <p className="text-[9px] text-slate-400 mt-1 max-w-[200px]">Quét QR bằng ứng dụng ngân hàng của Phụ huynh để thực hiện giao dịch chuyển khoản tự động.</p>
      </div>

      {invoice.status !== "PAID" ? (
        <button
          onClick={() => onSimulatePayment(invoice.id)}
          className="w-full bg-slate-900 hover:bg-slate-800 text-brand-yellow font-bold text-xs py-2 rounded-lg shadow-soft flex items-center justify-center gap-1"
        >
          <Check className="w-4 h-4" />
          Giả lập webhook Ngân hàng (Xác nhận)
        </button>
      ) : (
        <div className="p-3 bg-emerald-50 border border-emerald-100 text-emerald-800 rounded-lg text-xs font-semibold flex items-center justify-center gap-1.5">
          <CheckCircle className="w-5 h-5 text-emerald-600 shrink-0" />
          <span>Hóa đơn đã được gạch nợ thành công!</span>
        </div>
      )}
    </div>
  );
}
