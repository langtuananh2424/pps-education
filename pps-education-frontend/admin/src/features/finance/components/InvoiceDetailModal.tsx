import React, { useEffect, useState } from "react";
import { Ban, Save } from "lucide-react";
import { useTranslation } from "react-i18next";
import { ApiError } from "@/lib/apiClient";
import { useApp } from "@/context/AppContext";
import { useDialog } from "@/components/ui/DialogProvider";
import {
  InvoiceHistoryResponse,
  InvoiceResponse,
  MANUAL_PAYMENT_METHODS,
  PaymentMethod,
  PaymentResponse,
  cancelInvoice,
  getInvoiceDetail,
  listInvoiceHistory,
  listInvoicePayments,
  recordManualPayment
} from "../api";
import { formatDate, formatDateTime, formatVnd, inputClass, invoiceStatusVariants, labelClass } from "../format";
import Badge from "@/components/ui/Badge";
import Button from "@/components/ui/Button";
import FloatingError from "@/components/ui/FloatingError";
import Modal from "@/components/ui/Modal";
import Select from "@/components/ui/Select";
import TableContainer, { Td, Th } from "@/components/ui/TableContainer";

interface InvoiceDetailModalProps {
  invoiceId: number;
  onClose: () => void;
  onUpdated: (invoice: InvoiceResponse, message: string) => void;
}

/** Chi tiết 1 hóa đơn: các dòng phí, khoản thu, lịch sử thay đổi; ghi nhận thanh toán thủ công (A2) và hủy hóa đơn. */
export default function InvoiceDetailModal({ invoiceId, onClose, onUpdated }: InvoiceDetailModalProps) {
  const { t } = useTranslation("finance");
  const { hasPermission } = useApp();
  const { promptDialog } = useDialog();
  const canRecordPayment = hasPermission("finance.invoice.payment.record");
  const canCancel = hasPermission("finance.invoice.cancel");

  const [invoice, setInvoice] = useState<InvoiceResponse | null>(null);
  const [payments, setPayments] = useState<PaymentResponse[]>([]);
  const [history, setHistory] = useState<InvoiceHistoryResponse[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [amount, setAmount] = useState("");
  const [method, setMethod] = useState<PaymentMethod>("CASH");
  const [receiptNumber, setReceiptNumber] = useState("");

  const load = () => {
    Promise.all([getInvoiceDetail(invoiceId), listInvoicePayments(invoiceId), listInvoiceHistory(invoiceId)])
      .then(([inv, pays, hist]) => {
        setInvoice(inv);
        setPayments(pays);
        setHistory(hist);
        setAmount(inv.outstandingAmount > 0 ? String(inv.outstandingAmount) : "");
      })
      .catch((err) => setError(err instanceof ApiError ? err.message : t("detail.loadError")));
  };

  useEffect(load, [invoiceId]);

  const isOpen = invoice != null && invoice.status !== "CANCELLED" && invoice.status !== "PAID";

  const handleRecordPayment = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!invoice) return;
    const value = Number(amount);
    if (!Number.isFinite(value) || value <= 0) {
      setError(t("detail.invalidAmount"));
      return;
    }
    setSubmitting(true);
    setError(null);
    try {
      await recordManualPayment(invoice.id, { amount: value, paymentMethod: method, receiptNumber: receiptNumber.trim() || undefined });
      const [updated, pays, hist] = await Promise.all([getInvoiceDetail(invoice.id), listInvoicePayments(invoice.id), listInvoiceHistory(invoice.id)]);
      setInvoice(updated);
      setPayments(pays);
      setHistory(hist);
      setReceiptNumber("");
      setAmount(updated.outstandingAmount > 0 ? String(updated.outstandingAmount) : "");
      onUpdated(updated, t("detail.paymentRecordedToast"));
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t("detail.paymentError"));
    } finally {
      setSubmitting(false);
    }
  };

  const handleCancel = async () => {
    if (!invoice) return;
    const reason = await promptDialog(t("detail.cancelPrompt", { number: invoice.invoiceNumber }), {
      title: t("detail.cancelTitle"),
      confirmLabel: t("detail.cancelConfirm"),
      required: true,
      multiline: true
    });
    if (!reason?.trim()) return;
    setSubmitting(true);
    setError(null);
    try {
      const updated = await cancelInvoice(invoice.id, reason.trim());
      setInvoice(updated);
      setHistory(await listInvoiceHistory(invoice.id));
      onUpdated(updated, t("detail.cancelledToast"));
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t("detail.cancelError"));
    } finally {
      setSubmitting(false);
    }
  };

  const historyLabel = (h: InvoiceHistoryResponse) => {
    const details = h.details ?? {};
    if (h.action === "CREATED") return t("history.created");
    if (details.reason === "OVERDUE") return t("history.overdue");
    if (details.status === "CANCELLED") return t("history.cancelled", { reason: String(details.reason ?? "") });
    return t("history.updated", { status: t(`invoiceStatus.${String(details.status ?? "")}`), paid: formatVnd(Number(details.paidAmount ?? 0)) });
  };

  return (
    <Modal open onClose={onClose} title={invoice ? t("detail.title", { number: invoice.invoiceNumber }) : t("common.loading")} size="lg">
      <div className="space-y-5">
        <FloatingError message={error} onClose={() => setError(null)} />
        {!invoice ? (
          <p className="text-sm text-slate-500">{t("common.loading")}</p>
        ) : (
          <>
            <div className="grid grid-cols-2 sm:grid-cols-3 gap-3 text-sm">
              <div>
                <span className={labelClass}>{t("invoices.colStudent")}</span>
                <span className="font-semibold text-slate-800">{invoice.studentFullName}</span>
                <span className="block text-[12px] text-slate-400 font-mono">{invoice.studentCode}</span>
              </div>
              <div>
                <span className={labelClass}>{t("invoices.colClass")}</span>
                <span>{invoice.className ?? "—"}</span>
                {invoice.siteName && <span className="block text-[12px] text-slate-400">{invoice.siteName}</span>}
              </div>
              <div>
                <span className={labelClass}>{t("common.status")}</span>
                <Badge variant={invoiceStatusVariants[invoice.status]}>{t(`invoiceStatus.${invoice.status}`)}</Badge>
              </div>
              <div>
                <span className={labelClass}>{t("detail.billingPeriod")}</span>
                <span>
                  {formatDate(invoice.billingPeriodFrom)} – {formatDate(invoice.billingPeriodTo)}
                </span>
              </div>
              <div>
                <span className={labelClass}>{t("detail.issueDate")}</span>
                <span>{formatDate(invoice.issueDate)}</span>
              </div>
              <div>
                <span className={labelClass}>{t("invoices.colDueDate")}</span>
                <span>{formatDate(invoice.dueDate)}</span>
              </div>
            </div>

            <TableContainer>
              <thead>
                <tr>
                  <Th>{t("detail.itemDescription")}</Th>
                  <Th className="text-right">{t("detail.itemQuantity")}</Th>
                  <Th className="text-right">{t("detail.itemUnitPrice")}</Th>
                  <Th className="text-right">{t("detail.itemAmount")}</Th>
                </tr>
              </thead>
              <tbody>
                {invoice.items.map((item) => (
                  <tr key={item.id} className="border-t border-slate-100">
                    <Td>{item.description}</Td>
                    <Td className="text-right">{item.quantity}</Td>
                    <Td className="text-right">{formatVnd(item.unitPrice)}</Td>
                    <Td className="text-right">{formatVnd(item.amount)}</Td>
                  </tr>
                ))}
                <tr className="border-t border-slate-100">
                  <Td colSpan={3} className="text-right text-slate-500">{t("detail.discount")}</Td>
                  <Td className="text-right text-emerald-600">−{formatVnd(invoice.discountTotal)}</Td>
                </tr>
                <tr className="border-t border-slate-100">
                  <Td colSpan={3} className="text-right font-bold">{t("invoices.colTotal")}</Td>
                  <Td className="text-right font-bold">{formatVnd(invoice.totalAmount)}</Td>
                </tr>
                <tr className="border-t border-slate-100">
                  <Td colSpan={3} className="text-right text-slate-500">{t("detail.paid")}</Td>
                  <Td className="text-right">{formatVnd(invoice.paidAmount)}</Td>
                </tr>
                <tr className="border-t border-slate-100">
                  <Td colSpan={3} className="text-right font-bold">{t("invoices.colOutstanding")}</Td>
                  <Td className="text-right font-bold text-brand-red">{formatVnd(invoice.outstandingAmount)}</Td>
                </tr>
              </tbody>
            </TableContainer>

            <div className="space-y-2">
              <span className="text-[12px] font-bold uppercase text-slate-500">{t("detail.paymentsTitle")}</span>
              {payments.length === 0 ? (
                <p className="text-sm text-slate-400 italic">{t("detail.noPayments")}</p>
              ) : (
                <ul className="divide-y divide-slate-100 border border-slate-200 rounded-xl">
                  {payments.map((p) => (
                    <li key={p.id} className="px-4 py-2.5 flex items-center justify-between gap-3 text-sm">
                      <div>
                        <div className="font-semibold text-slate-800">
                          {formatVnd(p.amount)} · {t(`paymentMethod.${p.paymentMethod}`)}
                        </div>
                        <div className="text-[12px] text-slate-400">
                          {formatDateTime(p.paidAt)} · {p.confirmedByName ?? t("common.system")}
                          {p.receiptNumber && ` · ${t("detail.receipt")} ${p.receiptNumber}`}
                          {p.bankTransactionId && ` · ${p.bankTransactionId}`}
                        </div>
                      </div>
                      <span className="font-mono text-[12px] text-slate-400">{p.paymentReference}</span>
                    </li>
                  ))}
                </ul>
              )}
            </div>

            {canRecordPayment && isOpen && (
              <form onSubmit={handleRecordPayment} className="border-t border-slate-100 pt-4 space-y-3">
                <span className="text-[12px] font-bold uppercase text-slate-500">{t("detail.recordPaymentTitle")}</span>
                <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
                  <div>
                    <label className={labelClass}>{t("detail.amount")}</label>
                    <input type="number" min={1} value={amount} onChange={(e) => setAmount(e.target.value)} className={inputClass} />
                  </div>
                  <div>
                    <label className={labelClass}>{t("detail.method")}</label>
                    <Select value={method} onChange={(e) => setMethod(e.target.value as PaymentMethod)} className={inputClass}>
                      {MANUAL_PAYMENT_METHODS.map((m) => (
                        <option key={m} value={m}>
                          {t(`paymentMethod.${m}`)}
                        </option>
                      ))}
                    </Select>
                  </div>
                  <div>
                    <label className={labelClass}>{t("detail.receiptNumber")}</label>
                    <input value={receiptNumber} onChange={(e) => setReceiptNumber(e.target.value)} className={inputClass} />
                  </div>
                </div>
                <div className="flex justify-end">
                  <Button type="submit" disabled={submitting}>
                    <Save className="w-4 h-4" /> {t("detail.recordPaymentButton")}
                  </Button>
                </div>
              </form>
            )}

            <div className="space-y-2 border-t border-slate-100 pt-4">
              <span className="text-[12px] font-bold uppercase text-slate-500">{t("detail.historyTitle")}</span>
              <ul className="space-y-1.5 text-sm">
                {history.map((h) => (
                  <li key={h.id} className="flex flex-wrap gap-x-2 text-slate-600">
                    <span className="text-slate-400 tabular-nums">{formatDateTime(h.createdAt)}</span>
                    <span className="font-semibold text-slate-700">{h.changedByName ?? t("common.system")}</span>
                    <span>{historyLabel(h)}</span>
                  </li>
                ))}
              </ul>
            </div>

            {canCancel && invoice.status !== "CANCELLED" && invoice.paidAmount === 0 && (
              <div className="flex justify-end border-t border-slate-100 pt-4">
                <Button variant="danger" size="sm" onClick={handleCancel} disabled={submitting}>
                  <Ban className="w-4 h-4" /> {t("detail.cancelButton")}
                </Button>
              </div>
            )}
          </>
        )}
      </div>
    </Modal>
  );
}
