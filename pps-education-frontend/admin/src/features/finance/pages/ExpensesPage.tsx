import React, { useEffect, useState } from "react";
import { Check, Clock, Plus, Save, Wallet, X } from "lucide-react";
import { useTranslation } from "react-i18next";
import { ApiError } from "@/lib/apiClient";
import { useApp } from "@/context/AppContext";
import { useDialog } from "@/components/ui/DialogProvider";
import { listSites, SiteResponse } from "@/features/facility/api";
import {
  EXPENSE_PAYMENT_METHODS,
  ExpenseCategoryResponse,
  ExpensePaymentMethod,
  OperatingExpenseResponse,
  createOperatingExpense,
  decideOperatingExpense,
  listExpenseCategories,
  listOperatingExpenses
} from "../api";
import { expenseStatusVariants, formatDate, formatVnd, inputClass, labelClass, monthRange, todayIso } from "../format";
import Badge from "@/components/ui/Badge";
import Button from "@/components/ui/Button";
import Card from "@/components/ui/Card";
import DatePicker from "@/components/ui/DatePicker";
import FloatingError from "@/components/ui/FloatingError";
import Modal from "@/components/ui/Modal";
import Pagination from "@/components/ui/Pagination";
import Select from "@/components/ui/Select";
import StatCard from "@/components/ui/StatCard";
import TableContainer, { Td, Th } from "@/components/ui/TableContainer";
import Toast from "@/components/ui/Toast";
import { useToast } from "@/lib/useToast";

const SHARED_SITE = "SHARED";

/** UC-31: Kế toán ghi nhận chi vận hành; Ban giám đốc duyệt/từ chối (A2). Khoản bị từ chối không tính vào báo cáo. */
export default function ExpensesPage() {
  const { t } = useTranslation("finance");
  const { hasPermission } = useApp();
  const { promptDialog, confirmDialog } = useDialog();
  const canCreate = hasPermission("finance.expense.create");
  const canApprove = hasPermission("finance.expense.approve");
  const { message: toastMessage, showToast } = useToast();

  const initialRange = monthRange();
  const [from, setFrom] = useState(initialRange.from);
  const [to, setTo] = useState(initialRange.to);
  const [siteFilter, setSiteFilter] = useState("");
  const [statusFilter, setStatusFilter] = useState("");
  const [sites, setSites] = useState<SiteResponse[]>([]);
  const [expenses, setExpenses] = useState<OperatingExpenseResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [createOpen, setCreateOpen] = useState(false);
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(20);

  useEffect(() => {
    listSites().then(setSites).catch(() => setSites([]));
  }, []);

  const load = () => {
    if (!from || !to) return;
    setLoading(true);
    setError(null);
    const siteId = siteFilter && siteFilter !== SHARED_SITE ? Number(siteFilter) : undefined;
    listOperatingExpenses({ from, to, siteId })
      .then((res) => {
        setExpenses(res.sort((a, b) => b.expenseDate.localeCompare(a.expenseDate) || b.id - a.id));
        setPage(0);
      })
      .catch((err) => setError(err instanceof ApiError ? err.message : t("expenses.loadError")))
      .finally(() => setLoading(false));
  };

  useEffect(load, [from, to, siteFilter]);

  const visible = expenses.filter(
    (e) => (siteFilter !== SHARED_SITE || e.siteId == null) && (!statusFilter || e.status === statusFilter)
  );
  const counted = visible.filter((e) => e.status !== "REJECTED");
  const totalAmount = counted.reduce((sum, e) => sum + e.amount, 0);
  const pending = visible.filter((e) => e.status === "RECORDED");
  const pageItems = visible.slice(page * pageSize, (page + 1) * pageSize);

  const replace = (updated: OperatingExpenseResponse) =>
    setExpenses((prev) => prev.map((e) => (e.id === updated.id ? updated : e)));

  const handleApprove = async (expense: OperatingExpenseResponse) => {
    const ok = await confirmDialog(t("expenses.approveConfirm", { number: expense.expenseNumber, amount: formatVnd(expense.amount) }));
    if (!ok) return;
    try {
      replace(await decideOperatingExpense(expense.id, "APPROVED"));
      showToast(t("expenses.approvedToast"));
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t("expenses.decideError"));
    }
  };

  const handleReject = async (expense: OperatingExpenseResponse) => {
    const reason = await promptDialog(t("expenses.rejectPrompt", { number: expense.expenseNumber }), {
      title: t("expenses.rejectTitle"),
      confirmLabel: t("expenses.reject"),
      required: true,
      multiline: true
    });
    if (!reason?.trim()) return;
    try {
      replace(await decideOperatingExpense(expense.id, "REJECTED", reason.trim()));
      showToast(t("expenses.rejectedToast"));
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t("expenses.decideError"));
    }
  };

  return (
    <div className="space-y-6">
      <div className="border-b border-slate-200 pb-4">
        <h1 className="text-xl font-bold font-display tracking-tight text-slate-900">{t("expenses.title")}</h1>
        <p className="text-sm text-slate-500 mt-1">{t("expenses.description")}</p>
      </div>

      <FloatingError message={error} onClose={() => setError(null)} />
      <Toast message={toastMessage} />

      <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
        <StatCard icon={Wallet} label={t("expenses.statTotal")} value={formatVnd(totalAmount)} hint={t("expenses.statTotalHint")} tone="slate" />
        <StatCard icon={Clock} label={t("expenses.statPending")} value={String(pending.length)} hint={formatVnd(pending.reduce((s, e) => s + e.amount, 0))} tone="warning" />
      </div>

      <Card>
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-3 items-end">
          <div>
            <label className={labelClass}>{t("expenses.dateFrom")}</label>
            <DatePicker value={from} onChange={setFrom} max={to} />
          </div>
          <div>
            <label className={labelClass}>{t("expenses.dateTo")}</label>
            <DatePicker value={to} onChange={setTo} min={from} />
          </div>
          <div>
            <label className={labelClass}>{t("common.site")}</label>
            <Select value={siteFilter} onChange={(e) => setSiteFilter(e.target.value)} className={inputClass}>
              <option value="">{t("common.allSites")}</option>
              <option value={SHARED_SITE}>{t("expenses.sharedOnly")}</option>
              {sites.map((s) => (
                <option key={s.id} value={s.id}>
                  {s.name}
                </option>
              ))}
            </Select>
          </div>
          <div>
            <label className={labelClass}>{t("common.status")}</label>
            <Select value={statusFilter} onChange={(e) => setStatusFilter(e.target.value)} className={inputClass}>
              <option value="">{t("common.all")}</option>
              <option value="RECORDED">{t("expenseStatus.RECORDED")}</option>
              <option value="APPROVED">{t("expenseStatus.APPROVED")}</option>
              <option value="REJECTED">{t("expenseStatus.REJECTED")}</option>
            </Select>
          </div>
        </div>
      </Card>

      <Card padded={false} className="overflow-hidden">
        <div className="px-5 py-4 border-b border-slate-100 bg-slate-50 flex items-center justify-between gap-3">
          <span className="text-sm font-bold text-slate-700 font-display">{t("expenses.listTitle")}</span>
          {canCreate && (
            <Button size="sm" onClick={() => setCreateOpen(true)}>
              <Plus className="w-4 h-4" /> {t("expenses.createButton")}
            </Button>
          )}
        </div>
        {loading ? (
          <p className="text-sm text-slate-500 p-5">{t("common.loading")}</p>
        ) : visible.length === 0 ? (
          <p className="text-sm text-slate-400 italic p-5">{t("expenses.empty")}</p>
        ) : (
          <>
            <TableContainer className="rounded-none border-0">
              <thead>
                <tr>
                  <Th>{t("expenses.colDate")}</Th>
                  <Th>{t("expenses.colCategory")}</Th>
                  <Th>{t("expenses.colDescription")}</Th>
                  <Th>{t("common.site")}</Th>
                  <Th className="text-right">{t("expenses.colAmount")}</Th>
                  <Th>{t("common.status")}</Th>
                  {canApprove && <Th />}
                </tr>
              </thead>
              <tbody>
                {pageItems.map((e) => (
                  <tr key={e.id} className="border-t border-slate-100 align-top">
                    <Td>
                      <div>{formatDate(e.expenseDate)}</div>
                      <div className="text-[12px] font-mono text-slate-400">{e.expenseNumber}</div>
                    </Td>
                    <Td>{e.expenseCategoryName}</Td>
                    <Td>
                      <div className="text-slate-800">{e.description}</div>
                      <div className="text-[12px] text-slate-400">
                        {t(`expensePaymentMethod.${e.paymentMethod}`)}
                        {e.supplierName && ` · ${e.supplierName}`}
                        {e.receiptNumber && ` · ${t("detail.receipt")} ${e.receiptNumber}`}
                        {` · ${t("expenses.recordedBy", { name: e.recordedByName })}`}
                      </div>
                      {e.rejectionReason && <div className="text-[12px] text-rose-600 mt-0.5">{t("expenses.rejectionReason", { reason: e.rejectionReason })}</div>}
                    </Td>
                    <Td>{e.siteName ?? <span className="text-slate-400">{t("expenses.shared")}</span>}</Td>
                    <Td className="text-right font-semibold">{formatVnd(e.amount)}</Td>
                    <Td>
                      <Badge variant={expenseStatusVariants[e.status]}>{t(`expenseStatus.${e.status}`)}</Badge>
                      {e.approvedByName && <div className="text-[12px] text-slate-400 mt-0.5">{e.approvedByName}</div>}
                    </Td>
                    {canApprove && (
                      <Td className="text-right whitespace-nowrap">
                        {e.status === "RECORDED" && (
                          <div className="flex justify-end gap-1">
                            <Button size="sm" variant="secondary" onClick={() => handleApprove(e)} aria-label={t("expenses.approve")}>
                              <Check className="w-4 h-4" /> {t("expenses.approve")}
                            </Button>
                            <Button size="sm" variant="ghost" onClick={() => handleReject(e)} aria-label={t("expenses.reject")}>
                              <X className="w-4 h-4" /> {t("expenses.reject")}
                            </Button>
                          </div>
                        )}
                      </Td>
                    )}
                  </tr>
                ))}
              </tbody>
            </TableContainer>
            <div className="px-5 py-3 border-t border-slate-100">
              <Pagination
                page={page}
                pageSize={pageSize}
                totalElements={visible.length}
                itemLabel={t("expenses.itemLabel")}
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

      {createOpen && (
        <CreateExpenseModal
          sites={sites}
          onClose={() => setCreateOpen(false)}
          onCreated={() => {
            setCreateOpen(false);
            showToast(t("expenses.createdToast"));
            load();
          }}
        />
      )}
    </div>
  );
}

function CreateExpenseModal({ sites, onClose, onCreated }: { sites: SiteResponse[]; onClose: () => void; onCreated: () => void }) {
  const { t } = useTranslation("finance");
  const [categories, setCategories] = useState<ExpenseCategoryResponse[]>([]);
  const [form, setForm] = useState({
    expenseCategoryCode: "",
    siteId: "",
    expenseDate: todayIso(),
    amount: "",
    description: "",
    paymentMethod: "BANK_TRANSFER" as ExpensePaymentMethod,
    supplierName: "",
    receiptNumber: "",
    fileUrl: ""
  });
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    listExpenseCategories().then(setCategories).catch(() => setCategories([]));
  }, []);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    const amount = Number(form.amount);
    if (!form.expenseCategoryCode || !form.expenseDate || !form.description.trim() || !form.amount) {
      setError(t("common.missingFields"));
      return;
    }
    if (!Number.isFinite(amount) || amount <= 0) {
      setError(t("detail.invalidAmount"));
      return;
    }
    setSubmitting(true);
    setError(null);
    try {
      await createOperatingExpense({
        expenseCategoryCode: form.expenseCategoryCode,
        siteId: form.siteId ? Number(form.siteId) : undefined,
        expenseDate: form.expenseDate,
        amount,
        description: form.description.trim(),
        paymentMethod: form.paymentMethod,
        supplierName: form.supplierName.trim() || undefined,
        receiptNumber: form.receiptNumber.trim() || undefined,
        fileUrl: form.fileUrl.trim() || undefined
      });
      onCreated();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t("expenses.createError"));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Modal open onClose={onClose} title={t("expenses.createTitle")} size="lg">
      <form onSubmit={handleSubmit} className="space-y-4">
        <FloatingError message={error} onClose={() => setError(null)} />
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
          <div>
            <label className={labelClass}>{t("expenses.colCategory")}</label>
            <Select value={form.expenseCategoryCode} onChange={(e) => setForm({ ...form, expenseCategoryCode: e.target.value })} className={inputClass}>
              <option value="">{t("common.choose")}</option>
              {categories.map((c) => (
                <option key={c.id} value={c.code}>
                  {c.name}
                </option>
              ))}
            </Select>
          </div>
          <div>
            <label className={labelClass}>{t("common.site")}</label>
            <Select value={form.siteId} onChange={(e) => setForm({ ...form, siteId: e.target.value })} className={inputClass}>
              <option value="">{t("expenses.sharedOption")}</option>
              {sites.map((s) => (
                <option key={s.id} value={s.id}>
                  {s.name}
                </option>
              ))}
            </Select>
          </div>
          <div>
            <label className={labelClass}>{t("expenses.colDate")}</label>
            <DatePicker value={form.expenseDate} onChange={(v) => setForm({ ...form, expenseDate: v })} />
          </div>
          <div>
            <label className={labelClass}>{t("expenses.colAmount")}</label>
            <input type="number" min={1} value={form.amount} onChange={(e) => setForm({ ...form, amount: e.target.value })} className={inputClass} />
          </div>
          <div className="sm:col-span-2">
            <label className={labelClass}>{t("expenses.colDescription")}</label>
            <textarea
              rows={2}
              value={form.description}
              onChange={(e) => setForm({ ...form, description: e.target.value })}
              placeholder={form.siteId ? undefined : t("expenses.sharedDescriptionHint")}
              className={inputClass}
            />
          </div>
          <div>
            <label className={labelClass}>{t("expenses.paymentMethod")}</label>
            <Select value={form.paymentMethod} onChange={(e) => setForm({ ...form, paymentMethod: e.target.value as ExpensePaymentMethod })} className={inputClass}>
              {EXPENSE_PAYMENT_METHODS.map((m) => (
                <option key={m} value={m}>
                  {t(`expensePaymentMethod.${m}`)}
                </option>
              ))}
            </Select>
          </div>
          <div>
            <label className={labelClass}>{t("expenses.supplier")}</label>
            <input value={form.supplierName} onChange={(e) => setForm({ ...form, supplierName: e.target.value })} className={inputClass} />
          </div>
          <div>
            <label className={labelClass}>{t("detail.receiptNumber")}</label>
            <input value={form.receiptNumber} onChange={(e) => setForm({ ...form, receiptNumber: e.target.value })} className={inputClass} />
          </div>
          <div>
            <label className={labelClass}>{t("expenses.fileUrl")}</label>
            <input value={form.fileUrl} onChange={(e) => setForm({ ...form, fileUrl: e.target.value })} placeholder="https://" className={inputClass} />
          </div>
        </div>
        <div className="flex justify-end gap-2">
          <Button type="button" variant="secondary" onClick={onClose}>
            {t("common.cancel")}
          </Button>
          <Button type="submit" disabled={submitting}>
            <Save className="w-4 h-4" /> {t("common.save")}
          </Button>
        </div>
      </form>
    </Modal>
  );
}
