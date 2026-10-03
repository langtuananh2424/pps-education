import React, { useEffect, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { QRCodeSVG } from "qrcode.react";
import { Calendar, CheckCircle2, CreditCard, ExternalLink, Loader2, QrCode, ShieldAlert } from "lucide-react";
import { ApiError } from "@/lib/apiClient";
import { createInvoicePaymentLink, InvoiceResponse, listMyInvoices, PaymentLinkResponse } from "../api";

/** Chu kỳ hỏi lại trạng thái hóa đơn khi đang chờ payOS gọi webhook. */
const PAYMENT_POLL_MS = 5000;

const formatPrice = (value: number) => new Intl.NumberFormat("vi-VN", { style: "currency", currency: "VND" }).format(value);

export default function BillingTab() {
  const { t } = useTranslation("portal-account");
  const [invoices, setInvoices] = useState<InvoiceResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [selected, setSelected] = useState<InvoiceResponse | null>(null);
  const [link, setLink] = useState<PaymentLinkResponse | null>(null);
  const [linkLoading, setLinkLoading] = useState(false);
  const [linkError, setLinkError] = useState<string | null>(null);
  const [paidNotice, setPaidNotice] = useState<"full" | "partial" | null>(null);
  const linkRef = useRef<PaymentLinkResponse | null>(null);
  linkRef.current = link;

  useEffect(() => {
    listMyInvoices()
      .then(setInvoices)
      .catch((err) => setError(err instanceof ApiError ? err.message : t("billing.loadInvoicesFailed")))
      .finally(() => setLoading(false));
  }, []);

  const unpaid = invoices.filter((i) => i.status !== "PAID" && i.status !== "CANCELLED");
  const paid = invoices.filter((i) => i.status === "PAID");
  const totalOutstanding = unpaid.reduce((sum, i) => sum + i.outstandingAmount, 0);

  const handlePay = (inv: InvoiceResponse) => {
    setSelected(inv);
    setLink(null);
    setLinkError(null);
    setPaidNotice(null);
    setLinkLoading(true);
    createInvoicePaymentLink(inv.id)
      .then(setLink)
      .catch((err) => setLinkError(err instanceof ApiError ? err.message : t("billing.linkFailed")))
      .finally(() => setLinkLoading(false));
  };

  // Đang hiển thị QR: hỏi lại hóa đơn đều đặn; số còn nợ đổi nghĩa là webhook payOS đã gạch nợ.
  useEffect(() => {
    if (!link) return;
    const timer = window.setInterval(() => {
      listMyInvoices()
        .then((latest) => {
          const active = linkRef.current;
          if (!active) return;
          setInvoices(latest);
          const current = latest.find((i) => i.id === active.invoiceId);
          if (current && current.outstandingAmount !== active.amount) {
            setPaidNotice(current.status === "PAID" ? "full" : "partial");
            setLink(null);
            setSelected(null);
          }
        })
        .catch(() => undefined);
    }, PAYMENT_POLL_MS);
    return () => window.clearInterval(timer);
  }, [link?.orderCode]);

  if (loading) return <p className="text-sm text-muted font-bold">{t("billing.loading")}</p>;

  return (
    <div className="space-y-6">
      {error && <div className="text-xs font-bold text-rose-600 bg-rose-50 border border-rose-100 p-3 rounded-xl">{error}</div>}

      <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
        <div className="bg-white border border-line/80 p-6 rounded-[20px] shadow-[0_8px_30px_rgba(30,42,69,0.03)] flex items-center gap-4">
          <div className="w-12 h-12 rounded-full bg-coral/10 border border-coral/20 flex items-center justify-center text-coral shrink-0">
            <ShieldAlert size={24} />
          </div>
          <div>
            <p className="text-xs text-muted font-extrabold uppercase tracking-wider">{t("billing.outstandingAmount")}</p>
            <p className="text-xl font-extrabold text-ink">{formatPrice(totalOutstanding)}</p>
          </div>
        </div>
        <div className="bg-white border border-line/80 p-6 rounded-[20px] shadow-[0_8px_30px_rgba(30,42,69,0.03)] flex items-center gap-4">
          <div className="w-12 h-12 rounded-full bg-teal/10 border border-teal/20 flex items-center justify-center text-teal shrink-0">
            <CheckCircle2 size={24} />
          </div>
          <div>
            <p className="text-xs text-muted font-extrabold uppercase tracking-wider">{t("billing.completed")}</p>
            <p className="text-xl font-extrabold text-ink">{t("billing.invoiceCount", { count: paid.length })}</p>
          </div>
        </div>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        <div className="lg:col-span-2 space-y-6">
          <div className="bg-white border border-line/80 p-6 rounded-[20px] shadow-[0_8px_30px_rgba(30,42,69,0.03)] space-y-4">
            <h2 className="text-xl font-extrabold text-ink flex items-center gap-2">
              <CreditCard className="text-teal" /> {t("billing.unpaidInvoicesTitle")}
            </h2>
            {unpaid.length === 0 ? (
              <div className="py-8 text-center text-muted bg-sky-2 rounded-[20px] border border-dashed border-line">
                <CheckCircle2 className="mx-auto text-teal mb-2" size={36} />
                <p className="font-extrabold text-ink">{t("billing.noUnpaidInvoices")}</p>
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
                          {inv.status === "OVERDUE" ? t("billing.overdue") : t("billing.unpaid")}
                        </span>
                        <span className="text-xs text-muted font-mono font-bold">{inv.invoiceNumber}</span>
                      </div>
                      <h4 className="font-extrabold text-ink text-base">{inv.studentFullName}</h4>
                      <p className="text-xs text-muted font-bold flex items-center gap-1">
                        <Calendar size={12} /> {t("billing.dueDate")} <span className="font-bold text-ink">{inv.dueDate}</span>
                      </p>
                    </div>
                    <div className="flex items-center gap-4 w-full md:w-auto justify-between md:justify-end">
                      <span className="text-lg font-extrabold text-coral">{formatPrice(inv.outstandingAmount)}</span>
                      <button
                        onClick={() => handlePay(inv)}
                        disabled={linkLoading}
                        className="bg-teal hover:bg-teal-deep disabled:opacity-60 text-white border border-teal-deep shadow-md px-4 py-2 rounded-xl font-extrabold text-xs transition-all flex items-center gap-1.5"
                      >
                        <QrCode size={14} /> {t("billing.payByQr")}
                      </button>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>

          {paid.length > 0 && (
            <div className="bg-white border border-line/80 p-6 rounded-[20px] shadow-[0_8px_30px_rgba(30,42,69,0.03)] space-y-4">
              <h2 className="text-xl font-extrabold text-ink font-display">{t("billing.paidHistoryTitle")}</h2>
              <div className="space-y-3">
                {paid.map((inv) => (
                  <div key={inv.id} className="border border-line/60 p-4 rounded-[20px] flex justify-between items-center bg-sky-2">
                    <div className="space-y-1">
                      <h4 className="font-extrabold text-ink text-lg">{inv.invoiceNumber}</h4>
                      <p className="text-xs text-muted font-bold">{t("billing.due")} {inv.dueDate}</p>
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
              <QrCode className="text-teal" /> {t("billing.payTitle")}
            </h3>
            {paidNotice && (
              <div className="flex items-start gap-2 text-sm font-bold text-teal-deep bg-teal/10 border border-teal/20 p-3 rounded-xl">
                <CheckCircle2 size={18} className="shrink-0 mt-0.5" />
                {paidNotice === "full" ? t("billing.paidSuccess") : t("billing.paidSuccessPartial")}
              </div>
            )}
            {linkLoading && (
              <div className="flex items-center justify-center gap-2 py-10 text-muted font-bold text-sm">
                <Loader2 size={18} className="animate-spin" /> {t("billing.creatingLink")}
              </div>
            )}
            {linkError && !linkLoading && (
              <div className="text-xs font-bold text-rose-600 bg-rose-50 border border-rose-100 p-3 rounded-xl">{linkError}</div>
            )}
            {link && selected && !linkLoading && (
              <div className="space-y-3">
                <p className="text-xl font-extrabold text-coral">{formatPrice(link.amount)}</p>
                {link.qrCode && (
                  <div className="flex justify-center p-3 bg-white rounded-xl border border-line">
                    <QRCodeSVG value={link.qrCode} size={220} level="M" />
                  </div>
                )}
                <dl className="text-xs space-y-1 font-bold">
                  {link.accountName && (
                    <div className="flex justify-between gap-2"><dt className="text-muted">{t("billing.accountName")}</dt><dd className="text-ink text-right">{link.accountName}</dd></div>
                  )}
                  {link.accountNumber && (
                    <div className="flex justify-between gap-2"><dt className="text-muted">{t("billing.accountNumber")}</dt><dd className="text-ink font-mono">{link.accountNumber}</dd></div>
                  )}
                  {link.description && (
                    <div className="flex justify-between gap-2"><dt className="text-muted">{t("billing.transferNote")}</dt><dd className="text-ink font-mono">{link.description}</dd></div>
                  )}
                  <div className="flex justify-between gap-2">
                    <dt className="text-muted">{t("billing.expiresAt")}</dt>
                    <dd className="text-ink">{new Date(link.expiresAt).toLocaleTimeString("vi-VN", { hour: "2-digit", minute: "2-digit" })}</dd>
                  </div>
                </dl>
                {link.checkoutUrl && (
                  <a
                    href={link.checkoutUrl}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="w-full bg-slate-900 hover:bg-slate-800 text-white py-2.5 rounded-xl font-extrabold text-xs flex items-center justify-center gap-2"
                  >
                    <ExternalLink size={14} /> {t("billing.openCheckout")}
                  </a>
                )}
                <p className="text-[11px] text-muted font-bold flex items-center gap-1.5">
                  <Loader2 size={12} className="animate-spin shrink-0" /> {t("billing.waiting")}
                </p>
                <p className="text-[11px] text-muted font-bold">{t("billing.scanHint")}</p>
              </div>
            )}
            {!link && !linkLoading && !linkError && !paidNotice && (
              <div className="text-center py-10 text-muted bg-sky-2/40 rounded-[20px] border border-dashed border-line space-y-2">
                <QrCode className="mx-auto text-muted/40" size={40} />
                <p className="font-extrabold text-sm text-ink">{t("billing.selectInvoicePrompt")}</p>
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
