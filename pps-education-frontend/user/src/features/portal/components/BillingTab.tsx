import React, { useEffect, useState } from "react";
import { Calendar, CheckCircle2, Copy, CreditCard, QrCode, ShieldAlert } from "lucide-react";
import { ApiError } from "@/lib/apiClient";
import { InvoiceResponse, listMyInvoices } from "../api";

const formatPrice = (value: number) => new Intl.NumberFormat("vi-VN", { style: "currency", currency: "VND" }).format(value);

export default function BillingTab() {
  const [invoices, setInvoices] = useState<InvoiceResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [selected, setSelected] = useState<InvoiceResponse | null>(null);
  const [copied, setCopied] = useState(false);

  useEffect(() => {
    listMyInvoices()
      .then(setInvoices)
      .catch((err) => setError(err instanceof ApiError ? err.message : "Không tải được hóa đơn học phí."))
      .finally(() => setLoading(false));
  }, []);

  const unpaid = invoices.filter((i) => i.status !== "PAID" && i.status !== "CANCELLED");
  const paid = invoices.filter((i) => i.status === "PAID");
  const totalOutstanding = unpaid.reduce((sum, i) => sum + i.outstandingAmount, 0);

  const handleCopy = (data: string) => {
    navigator.clipboard.writeText(data).then(() => {
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    });
  };

  if (loading) return <p className="text-sm text-muted font-bold">Đang tải...</p>;

  return (
    <div className="space-y-6">
      {error && <div className="text-xs font-bold text-rose-600 bg-rose-50 border border-rose-100 p-3 rounded-xl">{error}</div>}

      <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
        <div className="bg-white border border-line/80 p-6 rounded-[20px] shadow-[0_8px_30px_rgba(30,42,69,0.03)] flex items-center gap-4">
          <div className="w-12 h-12 rounded-full bg-coral/10 border border-coral/20 flex items-center justify-center text-coral shrink-0">
            <ShieldAlert size={24} />
          </div>
          <div>
            <p className="text-xs text-muted font-extrabold uppercase tracking-wider">Học phí cần đóng</p>
            <p className="text-xl font-extrabold text-ink">{formatPrice(totalOutstanding)}</p>
          </div>
        </div>
        <div className="bg-white border border-line/80 p-6 rounded-[20px] shadow-[0_8px_30px_rgba(30,42,69,0.03)] flex items-center gap-4">
          <div className="w-12 h-12 rounded-full bg-teal/10 border border-teal/20 flex items-center justify-center text-teal shrink-0">
            <CheckCircle2 size={24} />
          </div>
          <div>
            <p className="text-xs text-muted font-extrabold uppercase tracking-wider">Đã hoàn thành</p>
            <p className="text-xl font-extrabold text-ink">{paid.length} hóa đơn</p>
          </div>
        </div>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        <div className="lg:col-span-2 space-y-6">
          <div className="bg-white border border-line/80 p-6 rounded-[20px] shadow-[0_8px_30px_rgba(30,42,69,0.03)] space-y-4">
            <h2 className="text-xl font-extrabold text-ink flex items-center gap-2">
              <CreditCard className="text-teal" /> Hóa đơn cần thanh toán
            </h2>
            {unpaid.length === 0 ? (
              <div className="py-8 text-center text-muted bg-sky-2 rounded-[20px] border border-dashed border-line">
                <CheckCircle2 className="mx-auto text-teal mb-2" size={36} />
                <p className="font-extrabold text-ink">Không còn hóa đơn nào cần đóng.</p>
              </div>
            ) : (
              <div className="space-y-4">
                {unpaid.map((inv) => (
                  <div
                    key={inv.id}
                    className="border border-line/80 hover:border-teal/50 transition-all p-5 rounded-[20px] flex flex-col md:flex-row justify-between items-start md:items-center gap-4 bg-sky-2"
                  >
                    <div className="space-y-1">
                      <div className="flex items-center gap-2">
                        <span className="px-2 py-0.5 bg-coral/10 text-coral text-xs font-bold rounded-full border border-coral/20">
                          {inv.status === "OVERDUE" ? "Quá hạn" : "Chưa thanh toán"}
                        </span>
                        <span className="text-xs text-muted font-mono font-bold">{inv.invoiceNumber}</span>
                      </div>
                      <h4 className="font-extrabold text-ink text-base">{inv.studentFullName}</h4>
                      <p className="text-xs text-muted font-bold flex items-center gap-1">
                        <Calendar size={12} /> Hạn thanh toán: <span className="font-bold text-ink">{inv.dueDate}</span>
                      </p>
                    </div>
                    <div className="flex items-center gap-4 w-full md:w-auto justify-between md:justify-end">
                      <span className="text-lg font-extrabold text-coral">{formatPrice(inv.outstandingAmount)}</span>
                      {inv.qrCodeData && (
                        <button
                          onClick={() => setSelected(inv)}
                          className="bg-teal hover:bg-teal-deep text-white border border-teal-deep shadow-md px-4 py-2 rounded-xl font-extrabold text-xs transition-all flex items-center gap-1.5"
                        >
                          <QrCode size={14} /> Xem chuyển khoản
                        </button>
                      )}
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>

          {paid.length > 0 && (
            <div className="bg-white border border-line/80 p-6 rounded-[20px] shadow-[0_8px_30px_rgba(30,42,69,0.03)] space-y-4">
              <h2 className="text-xl font-extrabold text-ink font-display">Lịch sử đã thanh toán</h2>
              <div className="space-y-3">
                {paid.map((inv) => (
                  <div key={inv.id} className="border border-line/60 p-4 rounded-[20px] flex justify-between items-center bg-sky-2">
                    <div className="space-y-1">
                      <h4 className="font-extrabold text-ink text-sm">{inv.invoiceNumber}</h4>
                      <p className="text-xs text-muted font-bold">Hạn: {inv.dueDate}</p>
                    </div>
                    <span className="text-sm font-extrabold text-teal-deep">{formatPrice(inv.totalAmount)}</span>
                  </div>
                ))}
              </div>
            </div>
          )}
        </div>

        <div className="lg:col-span-1">
          <div className="bg-white border border-line/80 p-6 rounded-[20px] shadow-[0_8px_30px_rgba(30,42,69,0.03)] sticky top-6 space-y-4">
            <h3 className="text-lg font-extrabold text-ink flex items-center gap-2">
              <QrCode className="text-teal" /> Nội dung chuyển khoản
            </h3>
            {selected ? (
              <div className="space-y-3">
                <p className="text-xs text-muted font-bold">Số tiền cần chuyển:</p>
                <p className="text-xl font-extrabold text-coral">{formatPrice(selected.outstandingAmount)}</p>
                <div className="p-3 bg-sky rounded-xl border border-line font-mono text-xs break-all">{selected.qrCodeData}</div>
                <button
                  onClick={() => handleCopy(selected.qrCodeData ?? "")}
                  className="w-full bg-slate-900 hover:bg-slate-800 text-white py-2.5 rounded-xl font-extrabold text-xs flex items-center justify-center gap-2"
                >
                  <Copy size={14} /> {copied ? "Đã sao chép!" : "Sao chép nội dung"}
                </button>
                <p className="text-[11px] text-muted font-bold">
                  Chuyển khoản đúng nội dung trên — hệ thống tự động ghi nhận đã thanh toán khi ngân hàng xác nhận, không cần báo lại.
                </p>
              </div>
            ) : (
              <div className="text-center py-10 text-muted bg-sky-2/40 rounded-[20px] border border-dashed border-line space-y-2">
                <QrCode className="mx-auto text-muted/40" size={40} />
                <p className="font-extrabold text-sm text-ink">Chọn 1 hóa đơn để xem nội dung chuyển khoản</p>
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
