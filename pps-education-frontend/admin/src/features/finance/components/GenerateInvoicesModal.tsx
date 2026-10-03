import React, { useEffect, useState } from "react";
import { Receipt } from "lucide-react";
import { useTranslation } from "react-i18next";
import { ApiError } from "@/lib/apiClient";
import { listClasses, ClassResponse } from "@/features/academic/api";
import { generateInvoices } from "../api";
import { addDays, inputClass, labelClass, monthRange, todayIso } from "../format";
import Button from "@/components/ui/Button";
import DatePicker from "@/components/ui/DatePicker";
import FloatingError from "@/components/ui/FloatingError";
import Modal from "@/components/ui/Modal";
import Select from "@/components/ui/Select";

interface GenerateInvoicesModalProps {
  onClose: () => void;
  onGenerated: (count: number) => void;
}

/**
 * UC-30 Main Flow bước 1 — Kế toán sinh hóa đơn bổ sung ngoài cron định kỳ. Bỏ trống lớp = mọi lớp đang có
 * gói học phí; học sinh đã có hóa đơn cùng kỳ (cùng ngày bắt đầu kỳ) được bỏ qua, không sinh trùng.
 */
export default function GenerateInvoicesModal({ onClose, onGenerated }: GenerateInvoicesModalProps) {
  const { t } = useTranslation("finance");
  const range = monthRange();
  const [classes, setClasses] = useState<ClassResponse[]>([]);
  const [classId, setClassId] = useState("");
  const [periodFrom, setPeriodFrom] = useState(range.from);
  const [periodTo, setPeriodTo] = useState(range.to);
  const [issueDate, setIssueDate] = useState(todayIso());
  const [dueDate, setDueDate] = useState(addDays(todayIso(), 15));
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    listClasses().then(setClasses).catch(() => setClasses([]));
  }, []);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!periodFrom || !periodTo || !issueDate || !dueDate) {
      setError(t("common.missingFields"));
      return;
    }
    if (dueDate < issueDate) {
      setError(t("generate.dueBeforeIssue"));
      return;
    }
    setSubmitting(true);
    setError(null);
    try {
      const created = await generateInvoices({
        classId: classId ? Number(classId) : undefined,
        billingPeriodFrom: periodFrom,
        billingPeriodTo: periodTo,
        issueDate,
        dueDate
      });
      onGenerated(created.length);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t("generate.error"));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Modal open onClose={onClose} title={t("generate.title")} description={t("generate.description")}>
      <form onSubmit={handleSubmit} className="space-y-4">
        <FloatingError message={error} onClose={() => setError(null)} />
        <div>
          <label className={labelClass}>{t("common.class")}</label>
          <Select value={classId} onChange={(e) => setClassId(e.target.value)} className={inputClass}>
            <option value="">{t("generate.allClassesWithPlan")}</option>
            {classes
              .filter((c) => c.status !== "CANCELLED" && c.status !== "COMPLETED")
              .map((c) => (
                <option key={c.id} value={c.id}>
                  {c.name} ({c.classCode}) · {c.siteName}
                </option>
              ))}
          </Select>
        </div>
        <div className="grid grid-cols-2 gap-3">
          <div>
            <label className={labelClass}>{t("generate.periodFrom")}</label>
            <DatePicker value={periodFrom} onChange={setPeriodFrom} max={periodTo} />
          </div>
          <div>
            <label className={labelClass}>{t("generate.periodTo")}</label>
            <DatePicker value={periodTo} onChange={setPeriodTo} min={periodFrom} />
          </div>
          <div>
            <label className={labelClass}>{t("detail.issueDate")}</label>
            <DatePicker value={issueDate} onChange={setIssueDate} />
          </div>
          <div>
            <label className={labelClass}>{t("invoices.colDueDate")}</label>
            <DatePicker value={dueDate} onChange={setDueDate} min={issueDate} />
          </div>
        </div>
        <div className="flex justify-end gap-2">
          <Button type="button" variant="secondary" onClick={onClose}>
            {t("common.cancel")}
          </Button>
          <Button type="submit" disabled={submitting}>
            <Receipt className="w-4 h-4" /> {t("generate.submit")}
          </Button>
        </div>
      </form>
    </Modal>
  );
}
