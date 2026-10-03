import React, { useEffect, useState } from "react";
import { Plus, Save, Search } from "lucide-react";
import { useTranslation } from "react-i18next";
import { ApiError } from "@/lib/apiClient";
import { useApp } from "@/context/AppContext";
import { useDialog } from "@/components/ui/DialogProvider";
import { listStudents, StudentResponse } from "@/features/student/api";
import { ScholarshipResponse, createScholarship, listScholarships, revokeScholarship } from "../api";
import { formatDate, formatVnd, inputClass, labelClass } from "../format";
import Badge, { BadgeVariant } from "@/components/ui/Badge";
import Button from "@/components/ui/Button";
import Card from "@/components/ui/Card";
import DatePicker from "@/components/ui/DatePicker";
import FloatingError from "@/components/ui/FloatingError";
import Modal from "@/components/ui/Modal";
import Select from "@/components/ui/Select";
import TableContainer, { Td, Th } from "@/components/ui/TableContainer";
import Toast from "@/components/ui/Toast";
import { useToast } from "@/lib/useToast";

const statusVariants: Record<ScholarshipResponse["status"], BadgeVariant> = {
  ACTIVE: "success",
  EXPIRED: "neutral",
  REVOKED: "danger"
};

/** Học bổng/Miễn giảm (UC-30 A3): học bổng ACTIVE còn hiệu lực được tự trừ vào hóa đơn sinh sau đó. */
export default function ScholarshipsTab() {
  const { t } = useTranslation("finance");
  const { hasPermission } = useApp();
  const { confirmDialog } = useDialog();
  const canCreate = hasPermission("finance.scholarship.create");
  const canRevoke = hasPermission("finance.scholarship.revoke");
  const { message: toastMessage, showToast } = useToast();

  const [status, setStatus] = useState("ACTIVE");
  const [items, setItems] = useState<ScholarshipResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [createOpen, setCreateOpen] = useState(false);

  const load = () => {
    setLoading(true);
    listScholarships({ status: status || undefined })
      .then(setItems)
      .catch((err) => setError(err instanceof ApiError ? err.message : t("scholarships.loadError")))
      .finally(() => setLoading(false));
  };

  useEffect(load, [status]);

  const handleRevoke = async (s: ScholarshipResponse) => {
    const ok = await confirmDialog(t("scholarships.revokeConfirm", { name: s.name, student: s.studentFullName }), { danger: true });
    if (!ok) return;
    try {
      const updated = await revokeScholarship(s.id);
      setItems((prev) => (status ? prev.filter((x) => x.id !== updated.id) : prev.map((x) => (x.id === updated.id ? updated : x))));
      showToast(t("scholarships.revokedToast"));
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t("scholarships.revokeError"));
    }
  };

  const discountLabel = (s: ScholarshipResponse) =>
    s.discountType === "PERCENTAGE" ? `${s.discountValue}%` : formatVnd(s.discountValue);

  return (
    <div className="space-y-5">
      <FloatingError message={error} onClose={() => setError(null)} />
      <Toast message={toastMessage} />

      <Card padded={false} className="overflow-hidden">
        <div className="px-5 py-4 border-b border-slate-100 bg-slate-50 flex flex-wrap items-center justify-between gap-3">
          <span className="text-sm font-bold text-slate-700 font-display">{t("scholarships.listTitle")}</span>
          <div className="flex items-center gap-2">
            <Select value={status} onChange={(e) => setStatus(e.target.value)} className={`${inputClass} w-44`}>
              <option value="">{t("common.all")}</option>
              <option value="ACTIVE">{t("scholarshipStatus.ACTIVE")}</option>
              <option value="EXPIRED">{t("scholarshipStatus.EXPIRED")}</option>
              <option value="REVOKED">{t("scholarshipStatus.REVOKED")}</option>
            </Select>
            {canCreate && (
              <Button size="sm" onClick={() => setCreateOpen(true)}>
                <Plus className="w-4 h-4" /> {t("scholarships.createButton")}
              </Button>
            )}
          </div>
        </div>
        {loading ? (
          <p className="text-sm text-slate-500 p-5">{t("common.loading")}</p>
        ) : items.length === 0 ? (
          <p className="text-sm text-slate-400 italic p-5">{t("scholarships.empty")}</p>
        ) : (
          <TableContainer className="rounded-none border-0">
            <thead>
              <tr>
                <Th>{t("invoices.colStudent")}</Th>
                <Th>{t("scholarships.colName")}</Th>
                <Th className="text-right">{t("scholarships.colDiscount")}</Th>
                <Th>{t("scholarships.colScope")}</Th>
                <Th>{t("scholarships.colValidity")}</Th>
                <Th>{t("common.status")}</Th>
                {canRevoke && <Th />}
              </tr>
            </thead>
            <tbody>
              {items.map((s) => (
                <tr key={s.id} className="border-t border-slate-100">
                  <Td>
                    <div className="font-semibold text-slate-800">{s.studentFullName}</div>
                    <div className="text-[12px] text-slate-400 font-mono">{s.studentCode}</div>
                  </Td>
                  <Td>
                    {s.name} <span className="font-mono text-[12px] text-slate-400">{s.code}</span>
                  </Td>
                  <Td className="text-right">
                    {discountLabel(s)}
                    {s.maxAmount != null && <div className="text-[12px] text-slate-400">{t("scholarships.maxAmountShort", { amount: formatVnd(s.maxAmount) })}</div>}
                  </Td>
                  <Td>{t(`scholarshipScope.${s.applicableScope}`)}</Td>
                  <Td>
                    {formatDate(s.validFrom)} – {s.validTo ? formatDate(s.validTo) : t("scholarships.noEnd")}
                  </Td>
                  <Td>
                    <Badge variant={statusVariants[s.status]}>{t(`scholarshipStatus.${s.status}`)}</Badge>
                  </Td>
                  {canRevoke && (
                    <Td className="text-right">
                      {s.status === "ACTIVE" && (
                        <Button size="sm" variant="ghost" onClick={() => handleRevoke(s)}>
                          {t("scholarships.revoke")}
                        </Button>
                      )}
                    </Td>
                  )}
                </tr>
              ))}
            </tbody>
          </TableContainer>
        )}
      </Card>

      {createOpen && (
        <CreateScholarshipModal
          onClose={() => setCreateOpen(false)}
          onCreated={() => {
            setCreateOpen(false);
            showToast(t("scholarships.createdToast"));
            load();
          }}
        />
      )}
    </div>
  );
}

function CreateScholarshipModal({ onClose, onCreated }: { onClose: () => void; onCreated: () => void }) {
  const { t } = useTranslation("finance");
  const [query, setQuery] = useState("");
  const [students, setStudents] = useState<StudentResponse[]>([]);
  const [searching, setSearching] = useState(false);
  const [form, setForm] = useState({
    studentId: "",
    code: "",
    name: "",
    discountType: "PERCENTAGE" as "PERCENTAGE" | "FIXED_AMOUNT",
    discountValue: "",
    applicableScope: "PER_INVOICE" as "PER_INVOICE" | "ONE_TIME",
    validFrom: "",
    validTo: "",
    maxAmount: ""
  });
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const searchStudents = async () => {
    if (!query.trim()) return;
    setSearching(true);
    try {
      const res = await listStudents(query.trim());
      setStudents(res);
      setForm((f) => ({ ...f, studentId: res.length === 1 ? String(res[0].id) : "" }));
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t("scholarships.searchError"));
    } finally {
      setSearching(false);
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!form.studentId || !form.code.trim() || !form.name.trim() || !form.discountValue) {
      setError(t("common.missingFields"));
      return;
    }
    const value = Number(form.discountValue);
    if (value <= 0 || (form.discountType === "PERCENTAGE" && value > 100)) {
      setError(t("scholarships.invalidDiscount"));
      return;
    }
    setSubmitting(true);
    setError(null);
    try {
      await createScholarship({
        studentId: Number(form.studentId),
        code: form.code.trim(),
        name: form.name.trim(),
        discountType: form.discountType,
        discountValue: value,
        applicableScope: form.applicableScope,
        validFrom: form.validFrom || undefined,
        validTo: form.validTo || undefined,
        maxAmount: form.maxAmount ? Number(form.maxAmount) : undefined
      });
      onCreated();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t("scholarships.createError"));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Modal open onClose={onClose} title={t("scholarships.createTitle")} description={t("scholarships.createDescription")} size="lg">
      <form onSubmit={handleSubmit} className="space-y-4">
        <FloatingError message={error} onClose={() => setError(null)} />
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
          <div>
            <label className={labelClass}>{t("scholarships.findStudent")}</label>
            <div className="flex gap-2">
              <input
                value={query}
                onChange={(e) => setQuery(e.target.value)}
                onKeyDown={(e) => {
                  if (e.key === "Enter") {
                    e.preventDefault();
                    searchStudents();
                  }
                }}
                placeholder={t("scholarships.findStudentPlaceholder")}
                className={inputClass}
              />
              <Button type="button" variant="secondary" onClick={searchStudents} disabled={searching} aria-label={t("common.search")}>
                <Search className="w-4 h-4" />
              </Button>
            </div>
          </div>
          <div>
            <label className={labelClass}>{t("invoices.colStudent")}</label>
            <Select value={form.studentId} onChange={(e) => setForm({ ...form, studentId: e.target.value })} className={inputClass} disabled={students.length === 0}>
              <option value="">{students.length === 0 ? t("scholarships.searchFirst") : t("common.choose")}</option>
              {students.map((s) => (
                <option key={s.id} value={s.id}>
                  {s.fullName} ({s.studentCode})
                </option>
              ))}
            </Select>
          </div>
          <div>
            <label className={labelClass}>{t("scholarships.colCode")}</label>
            <input value={form.code} onChange={(e) => setForm({ ...form, code: e.target.value.toUpperCase() })} className={`${inputClass} font-mono`} />
          </div>
          <div>
            <label className={labelClass}>{t("scholarships.colName")}</label>
            <input value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} className={inputClass} />
          </div>
          <div>
            <label className={labelClass}>{t("scholarships.discountType")}</label>
            <Select
              value={form.discountType}
              onChange={(e) => setForm({ ...form, discountType: e.target.value as "PERCENTAGE" | "FIXED_AMOUNT" })}
              className={inputClass}
            >
              <option value="PERCENTAGE">{t("discountType.PERCENTAGE")}</option>
              <option value="FIXED_AMOUNT">{t("discountType.FIXED_AMOUNT")}</option>
            </Select>
          </div>
          <div>
            <label className={labelClass}>{form.discountType === "PERCENTAGE" ? t("scholarships.percentValue") : t("scholarships.amountValue")}</label>
            <input type="number" min={0} value={form.discountValue} onChange={(e) => setForm({ ...form, discountValue: e.target.value })} className={inputClass} />
          </div>
          <div>
            <label className={labelClass}>{t("scholarships.colScope")}</label>
            <Select
              value={form.applicableScope}
              onChange={(e) => setForm({ ...form, applicableScope: e.target.value as "PER_INVOICE" | "ONE_TIME" })}
              className={inputClass}
            >
              <option value="PER_INVOICE">{t("scholarshipScope.PER_INVOICE")}</option>
              <option value="ONE_TIME">{t("scholarshipScope.ONE_TIME")}</option>
            </Select>
          </div>
          {form.discountType === "PERCENTAGE" && (
            <div>
              <label className={labelClass}>{t("scholarships.maxAmount")}</label>
              <input type="number" min={0} value={form.maxAmount} onChange={(e) => setForm({ ...form, maxAmount: e.target.value })} className={inputClass} />
            </div>
          )}
          <div>
            <label className={labelClass}>{t("scholarships.validFrom")}</label>
            <DatePicker value={form.validFrom} onChange={(v) => setForm({ ...form, validFrom: v })} />
          </div>
          <div>
            <label className={labelClass}>{t("scholarships.validTo")}</label>
            <DatePicker value={form.validTo} onChange={(v) => setForm({ ...form, validTo: v })} min={form.validFrom || undefined} />
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
