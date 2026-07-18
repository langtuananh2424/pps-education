import React, { useState } from "react";
import { QrCode } from "lucide-react";
import { Invoice } from "@/types";
import { mockInvoices } from "@/data/mockData";
import Card from "@/components/ui/Card";
import Badge from "@/components/ui/Badge";
import TableContainer, { Td, Th } from "@/components/ui/TableContainer";
import InvoiceQrPanel from "../components/InvoiceQrPanel";

export default function BillingPage() {
  const [invoices, setInvoices] = useState<Invoice[]>(mockInvoices);
  const [selectedInvoiceId, setSelectedInvoiceId] = useState<string | null>(null);
  const selectedInvoice = invoices.find((i) => i.id === selectedInvoiceId) || null;

  const handleSimulatePayment = (invoiceId: string) => {
    setInvoices((prev) => prev.map((inv) => (inv.id === invoiceId ? { ...inv, status: "PAID", paidAmount: inv.finalAmount } : inv)));
    alert(`Webhook ngân hàng xác nhận thành công!\n- Hóa đơn '${invoiceId}' tự động gạch nợ trực tuyến.\n- Trạng thái cập nhật sang ĐÃ THANH TOÁN.`);
  };

  return (
    <div className="space-y-6">
      <div className="border-b border-slate-200 pb-4">
        <h1 className="text-xl font-bold font-display tracking-tight text-slate-900">Quản Lý Tài Chính & Sổ Cái Kế Toán</h1>
        <p className="text-xs text-slate-500 mt-1">Xuất hóa đơn học phí, quét mã QR gạch nợ tự động trực tuyến (UC-30).</p>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        <Card padded={false} className="lg:col-span-2 overflow-hidden">
          <div className="px-5 py-4 border-b border-slate-100 bg-slate-50 flex items-center justify-between">
            <span className="text-xs font-bold text-slate-700 font-display">Hóa đơn học phí kỳ này</span>
            <span className="text-[10px] font-bold text-slate-400 font-mono">Simulated VietQR Integration</span>
          </div>
          <TableContainer className="rounded-none border-0">
            <thead>
              <tr>
                <Th>Hóa đơn</Th>
                <Th>Học sinh</Th>
                <Th>Phụ huynh</Th>
                <Th>Số tiền đóng</Th>
                <Th className="text-center">Trạng thái</Th>
                <Th className="text-center">Mã QR</Th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {invoices.map((inv) => (
                <tr key={inv.id} className="hover:bg-slate-50/40 transition-colors">
                  <Td className="font-mono font-bold text-slate-500">{inv.id}</Td>
                  <Td>
                    <span className="font-bold text-slate-900 block">{inv.studentName}</span>
                    {inv.scholarshipApplied && (
                      <span className="text-[9px] bg-amber-50 text-brand-orange border border-brand-yellow/30 font-bold px-1.5 py-0.5 rounded mt-0.5 inline-block">Học bổng: {inv.scholarshipApplied}</span>
                    )}
                  </Td>
                  <Td className="font-medium">{inv.parentName}</Td>
                  <Td className="font-mono font-bold text-slate-900">{inv.finalAmount.toLocaleString("vi-VN")} đ</Td>
                  <Td className="text-center">
                    <Badge variant={inv.status === "PAID" ? "success" : inv.status === "OVERDUE" ? "danger" : "warning"}>
                      {inv.status === "PAID" ? "Đã đóng" : inv.status === "OVERDUE" ? "Quá hạn" : "Chờ thu"}
                    </Badge>
                  </Td>
                  <Td className="text-center">
                    <button onClick={() => setSelectedInvoiceId(inv.id)} className="p-1 rounded bg-slate-100 hover:bg-slate-200 text-slate-600 border border-slate-200" title="Hiển thị QR đóng học">
                      <QrCode className="w-4 h-4" />
                    </button>
                  </Td>
                </tr>
              ))}
            </tbody>
          </TableContainer>
        </Card>

        <Card>
          <InvoiceQrPanel invoice={selectedInvoice} onSimulatePayment={handleSimulatePayment} />
        </Card>
      </div>
    </div>
  );
}
