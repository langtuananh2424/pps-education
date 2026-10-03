import React, { useEffect, useState } from "react";
import { AlertTriangle, CheckCircle2, Plus, Receipt, Search, Wallet } from "lucide-react";
import { useTranslation } from "react-i18next";
import { ApiError } from "@/lib/apiClient";
import { useApp } from "@/context/AppContext";
import { listClasses, ClassResponse } from "@/features/academic/api";
import { listSites, SiteResponse } from "@/features/facility/api";
import { INVOICE_STATUSES, InvoiceResponse, searchInvoices } from "../api";
import { formatDate, formatVnd, inputClass, invoiceStatusVariants, labelClass, monthRange } from "../format";
import Badge from "@/components/ui/Badge";
import Button from "@/components/ui/Button";
import Card from "@/components/ui/Card";
import DatePicker from "@/components/ui/DatePicker";
import FloatingError from "@/components/ui/FloatingError";
import Pagination from "@/components/ui/Pagination";
import Select from "@/components/ui/Select";
import StatCard from "@/components/ui/StatCard";
import TableContainer, { Td, Th } from "@/components/ui/TableContainer";
import Toast from "@/components/ui/Toast";
import { useToast } from "@/lib/useToast";
import InvoiceDetailModal from "./InvoiceDetailModal";
import GenerateInvoicesModal from "./GenerateInvoicesModal";

/** UC-30 phía Kế toán: tra cứu hóa đơn theo kỳ phát hành, xem chi tiết, ghi nhận thanh toán, hủy, sinh hóa đơn. */
export default function InvoicesTab() {
  const { t } = useTranslation("finance");
  const { hasPermission } = useApp();
  const canGenerate = hasPermission("finance.invoice.generate");
  const { message: toastMessage, showToast } = useToast();

  const initialRange = monthRange();
  const [from, setFrom] = useState(initialRange.from);
  const [to, setTo] = useState(initialRange.to);
  const [status, setStatus] = useState("");
  const [siteId, setSiteId] = useState("");
  const [classId, setClassId] = useState("");
  const [keyword, setKeyword] = useState("");
  const [sites, setSites] = useState<SiteResponse[]>([]);
  const [classes, setClasses] = useState<ClassResponse[]>([]);
  const [invoices, setInvoices] = useState<InvoiceResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [generateOpen, setGenerateOpen] = useState(false);
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(20);

  useEffect(() => {
    listSites().then(setSites).catch(() => setSites([]));
  }, []);

  useEffect(() => {
    listClasses(siteId ? { siteId: Number(siteId) } : undefined).then(setClasses).catch(() => setClasses([]));
  }, [siteId]);

  const load = () => {
    if (!from || !to) return;
    setLoading(true);
    setError(null);
    searchInvoices({
      from,
      to,
      status: status || undefined,
      siteId: siteId ? Number(siteId) : undefined,
      classId: classId ? Number(classId) : undefined,
      keyword
    })
      .then((res) => {
        setInvoices(res);
        setPage(0);
      })
      .catch((err) => setError(err instanceof ApiError ? err.message : t("invoices.loadError")))
      .finally(() => setLoading(false));
  };

  useEffect(load, [from, to, status, siteId, classId]);

  const handleSearch = (e: React.FormEvent) => {
    e.preventDefault();
    load();
  };

  const handleUpdated = (updated: InvoiceResponse) => {
    setInvoices((prev) => prev.map((inv) => (inv.id === updated.id ? updated : inv)));
  };

  const active = invoices.filter((i) => i.status !== "CANCELLED");
  const totalBilled = active.reduce((sum, i) => sum + i.totalAmount, 0);
  const totalPaid = active.reduce((sum, i) => sum + i.paidAmount, 0);
  const totalOutstanding = active.reduce((sum, i) => sum + i.outstandingAmount, 0);
  const overdueCount = invoices.filter((i) => i.status === "OVERDUE").length;
  const pageInvoices = invoices.slice(page * pageSize, (page + 1) * pageSize);

  return (
    <div className="space-y-5">
      <FloatingError message={error} onClose={() => setError(null)} />
      <Toast message={toastMessage} />

      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        <StatCard icon={Receipt} label={t("invoices.statBilled")} value={formatVnd(totalBilled)} hint={t("invoices.statCount", { count: active.length })} tone="slate" />
        <StatCard icon={CheckCircle2} label={t("invoices.statPaid")} value={formatVnd(totalPaid)} tone="brand" />
        <StatCard icon={Wallet} label={t("invoices.statOutstanding")} value={formatVnd(totalOutstanding)} tone="warning" />
        <StatCard icon={AlertTriangle} label={t("invoices.statOverdue")} value={String(overdueCount)} tone="danger" />
      </div>

      <Card>
        <form onSubmit={handleSearch} className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-6 gap-3 items-end">
          <div>
            <label className={labelClass}>{t("invoices.issueFrom")}</label>
            <DatePicker value={from} onChange={setFrom} max={to} />
          </div>
          <div>
            <label className={labelClass}>{t("invoices.issueTo")}</label>
            <DatePicker value={to} onChange={setTo} min={from} />
          </div>
          <div>
            <label className={labelClass}>{t("common.status")}</label>
            <Select value={status} onChange={(e) => setStatus(e.target.value)} className={inputClass}>
              <option value="">{t("common.all")}</option>
              {INVOICE_STATUSES.map((s) => (
                <option key={s} value={s}>
                  {t(`invoiceStatus.${s}`)}
                </option>
              ))}
            </Select>
          </div>
          <div>
            <label className={labelClass}>{t("common.site")}</label>
            <Select
              value={siteId}
              onChange={(e) => {
                setSiteId(e.target.value);
                setClassId("");
              }}
              className={inputClass}
            >
              <option value="">{t("common.allSites")}</option>
              {sites.map((s) => (
                <option key={s.id} value={s.id}>
                  {s.name}
                </option>
              ))}
            </Select>
          </div>
          <div>
            <label className={labelClass}>{t("common.class")}</label>
            <Select value={classId} onChange={(e) => setClassId(e.target.value)} className={inputClass}>
              <option value="">{t("common.allClasses")}</option>
              {classes.map((c) => (
                <option key={c.id} value={c.id}>
                  {c.name} ({c.classCode})
                </option>
              ))}
            </Select>
          </div>
          <div className="flex gap-2">
            <input
              value={keyword}
              onChange={(e) => setKeyword(e.target.value)}
              placeholder={t("invoices.keywordPlaceholder")}
              className={inputClass}
            />
            <Button type="submit" variant="secondary" aria-label={t("common.search")}>
              <Search className="w-4 h-4" />
            </Button>
          </div>
        </form>
      </Card>

      <Card padded={false} className="overflow-hidden">
        <div className="px-5 py-4 border-b border-slate-100 bg-slate-50 flex items-center justify-between gap-3">
          <span className="text-sm font-bold text-slate-700 font-display">{t("invoices.listTitle")}</span>
          {canGenerate && (
            <Button size="sm" onClick={() => setGenerateOpen(true)}>
              <Plus className="w-4 h-4" /> {t("invoices.generateButton")}
            </Button>
          )}
        </div>
        {loading ? (
          <p className="text-sm text-slate-500 font-medium p-5">{t("common.loading")}</p>
        ) : invoices.length === 0 ? (
          <p className="text-sm text-slate-400 italic p-5">{t("invoices.empty")}</p>
        ) : (
          <>
            <TableContainer className="rounded-none border-0">
              <thead>
                <tr>
                  <Th>{t("invoices.colNumber")}</Th>
                  <Th>{t("invoices.colStudent")}</Th>
                  <Th>{t("invoices.colClass")}</Th>
                  <Th>{t("invoices.colDueDate")}</Th>
                  <Th className="text-right">{t("invoices.colTotal")}</Th>
                  <Th className="text-right">{t("invoices.colOutstanding")}</Th>
                  <Th>{t("common.status")}</Th>
                </tr>
              </thead>
              <tbody>
                {pageInvoices.map((inv) => (
                  <tr key={inv.id} onClick={() => setSelectedId(inv.id)} className="cursor-pointer border-t border-slate-100 hover:bg-slate-50">
                    <Td className="font-mono text-[13px]">{inv.invoiceNumber}</Td>
                    <Td>
                      <div className="font-semibold text-slate-800">{inv.studentFullName}</div>
                      <div className="text-[12px] text-slate-400 font-mono">{inv.studentCode}</div>
                    </Td>
                    <Td>
                      <div>{inv.className ?? "—"}</div>
                      {inv.siteName && <div className="text-[12px] text-slate-400">{inv.siteName}</div>}
                    </Td>
                    <Td>{formatDate(inv.dueDate)}</Td>
                    <Td className="text-right font-semibold">{formatVnd(inv.totalAmount)}</Td>
                    <Td className="text-right">{formatVnd(inv.outstandingAmount)}</Td>
                    <Td>
                      <Badge variant={invoiceStatusVariants[inv.status]}>{t(`invoiceStatus.${inv.status}`)}</Badge>
                    </Td>
                  </tr>
                ))}
              </tbody>
            </TableContainer>
            <div className="px-5 py-3 border-t border-slate-100">
              <Pagination
                page={page}
                pageSize={pageSize}
                totalElements={invoices.length}
                itemLabel={t("invoices.itemLabel")}
                onPageChange={setPage}
                onPageSizeChange={(size) => {
                  setPageSize(size);
                  setPage(0);
                }}
              />
            </div>
          </>
        )}
      </Card>

      {selectedId != null && (
        <InvoiceDetailModal
          invoiceId={selectedId}
          onClose={() => setSelectedId(null)}
          onUpdated={(updated, message) => {
            handleUpdated(updated);
            showToast(message);
          }}
        />
      )}

      {generateOpen && (
        <GenerateInvoicesModal
          onClose={() => setGenerateOpen(false)}
          onGenerated={(count) => {
            setGenerateOpen(false);
            showToast(t("generate.successToast", { count }));
            load();
          }}
        />
      )}
    </div>
  );
}
