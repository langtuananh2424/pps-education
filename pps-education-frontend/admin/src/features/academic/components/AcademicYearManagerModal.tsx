import React, { useEffect, useState } from "react";
import { GraduationCap, Plus } from "lucide-react";
import { useTranslation } from "react-i18next";
import { ApiError } from "@/lib/apiClient";
import Modal from "@/components/ui/Modal";
import Button from "@/components/ui/Button";
import DatePicker from "@/components/ui/DatePicker";
import Select from "@/components/ui/Select";
import Badge from "@/components/ui/Badge";
import { AcademicYearResponse, createAcademicYear, listAcademicYears, updateAcademicYear } from "../api";

const inputClass = "w-full bg-slate-50 border border-slate-200 text-xs p-2.5 rounded-lg focus:outline-none";
const labelClass = "text-[10px] uppercase font-bold text-slate-500 block mb-1";

const yearStatusCodes: AcademicYearResponse["status"][] = ["PLANNED", "ACTIVE", "CLOSED"];

/**
 * Nhãn trạng thái năm học dịch qua i18next namespace "academic-curriculum" (key
 * `yearManager.status.<status>`) — theo đúng pattern `roleLabel(t, role)` ở
 * src/constants/roles.ts (Phase 2, kế hoạch đồng bộ song ngữ).
 */
function yearStatusLabel(t: (key: string, options?: Record<string, unknown>) => string, status: AcademicYearResponse["status"]): string {
  return t(`yearManager.status.${status}`, { defaultValue: status });
}

interface AcademicYearManagerModalProps {
  onClose: () => void;
}

/**
 * V102 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-07) —
 * quản lý danh mục "Năm học" dùng chung toàn hệ thống (nguồn cho dropdown
 * chọn năm học khi tạo lớp/chuyển lớp hàng loạt), khác Kỳ học (site-scoped).
 */
export default function AcademicYearManagerModal({ onClose }: AcademicYearManagerModalProps) {
  const { t } = useTranslation("academic-curriculum");
  const [years, setYears] = useState<AcademicYearResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [showCreate, setShowCreate] = useState(false);
  const [editingId, setEditingId] = useState<number | null>(null);

  const load = () => {
    setLoading(true);
    listAcademicYears()
      .then(setYears)
      .catch((err) => setError(err instanceof ApiError ? err.message : t("yearManager.loadFailedFallback")))
      .finally(() => setLoading(false));
  };
  useEffect(load, []);

  return (
    <Modal open onClose={onClose} title={t("yearManager.modalTitle")} size="lg">
      {error && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg mb-3">{error}</div>}

      <div className="flex items-center justify-between mb-3">
        <span className="text-[10px] font-bold uppercase text-slate-500">{t("yearManager.createdCountLabel", { count: years.length })}</span>
        {!showCreate && (
          <Button size="sm" variant="secondary" onClick={() => setShowCreate(true)}>
            <Plus className="w-3.5 h-3.5" />
            {t("yearManager.addButton")}
          </Button>
        )}
      </div>

      {loading ? (
        <p className="text-xs text-slate-500">{t("yearManager.loading")}</p>
      ) : years.length === 0 && !showCreate ? (
        <div className="text-xs text-slate-400 italic text-center py-6 flex flex-col items-center gap-2">
          <GraduationCap className="w-8 h-8 text-slate-300" />
          {t("yearManager.empty")}
        </div>
      ) : (
        <div className="space-y-2 mb-3">
          {years.map((y) =>
            editingId === y.id ? (
              <YearForm
                key={y.id}
                initial={y}
                onCancel={() => setEditingId(null)}
                onSubmit={async (values) => {
                  await updateAcademicYear(y.id, values);
                  setEditingId(null);
                  load();
                }}
              />
            ) : (
              <div key={y.id} className="border border-slate-200 rounded-lg p-3 text-xs flex items-center justify-between gap-2">
                <div>
                  <div className="font-bold text-slate-800 flex items-center gap-2">
                    {y.code} — {y.name}
                    <Badge variant={y.status === "ACTIVE" ? "success" : y.status === "CLOSED" ? "neutral" : "info"}>
                      {yearStatusLabel(t, y.status)}
                    </Badge>
                  </div>
                  {(y.startDate || y.endDate) && (
                    <div className="text-[10px] text-slate-500 mt-0.5">
                      {y.startDate ?? "?"} → {y.endDate ?? "?"}
                    </div>
                  )}
                </div>
                <button onClick={() => setEditingId(y.id)} className="text-brand-red font-bold text-[11px] hover:underline shrink-0">
                  {t("yearManager.editButton")}
                </button>
              </div>
            )
          )}
        </div>
      )}

      {showCreate && (
        <YearForm
          onCancel={() => setShowCreate(false)}
          onSubmit={async (values) => {
            await createAcademicYear(values);
            setShowCreate(false);
            load();
          }}
        />
      )}
    </Modal>
  );
}

interface YearFormValues {
  code: string;
  name: string;
  startDate?: string;
  endDate?: string;
  status: AcademicYearResponse["status"];
}

function YearForm({
  initial,
  onCancel,
  onSubmit
}: {
  initial?: AcademicYearResponse;
  onCancel: () => void;
  onSubmit: (values: YearFormValues) => Promise<void>;
}) {
  const { t } = useTranslation("academic-curriculum");
  const [code, setCode] = useState(initial?.code ?? "");
  const [name, setName] = useState(initial?.name ?? "");
  const [startDate, setStartDate] = useState(initial?.startDate ?? "");
  const [endDate, setEndDate] = useState(initial?.endDate ?? "");
  const [status, setStatus] = useState<AcademicYearResponse["status"]>(initial?.status ?? "PLANNED");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const isEdit = !!initial;

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if ((!isEdit && !code.trim()) || !name.trim()) {
      setError(t("yearManager.form.validationError"));
      return;
    }
    if (startDate && endDate && endDate < startDate) {
      setError(t("yearManager.form.dateOrderError"));
      return;
    }
    setSubmitting(true);
    setError(null);
    try {
      const values: YearFormValues = { code: code.trim(), name: name.trim(), startDate: startDate || undefined, endDate: endDate || undefined, status };
      await onSubmit(values);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t("yearManager.form.saveFailedFallback"));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <form onSubmit={handleSubmit} className="bg-slate-50 border border-slate-200 rounded-xl p-4 space-y-3">
      {error && <p className="text-xs text-rose-600">{error}</p>}
      <div className="grid grid-cols-2 gap-3">
        <div>
          <label className={labelClass}>{t("yearManager.form.codeLabel")} {isEdit && <span className="normal-case font-normal text-slate-400">{t("yearManager.form.codeNotEditable")}</span>}</label>
          <input value={code} onChange={(e) => setCode(e.target.value)} disabled={isEdit} placeholder={t("yearManager.form.codePlaceholder")} className={`${inputClass} font-mono`} />
        </div>
        <div>
          <label className={labelClass}>{t("yearManager.form.nameLabel")}</label>
          <input value={name} onChange={(e) => setName(e.target.value)} placeholder={t("yearManager.form.namePlaceholder")} className={inputClass} />
        </div>
        <div>
          <label className={labelClass}>{t("yearManager.form.startDateLabel")}</label>
          <DatePicker value={startDate} onChange={setStartDate} max={endDate || undefined} />
        </div>
        <div>
          <label className={labelClass}>{t("yearManager.form.endDateLabel")}</label>
          <DatePicker value={endDate} onChange={setEndDate} min={startDate || undefined} />
        </div>
        {isEdit && (
          <div>
            <label className={labelClass}>{t("yearManager.form.statusLabel")}</label>
            <Select value={status} onChange={(e) => setStatus(e.target.value as AcademicYearResponse["status"])} className={inputClass}>
              {yearStatusCodes.map((s) => (
                <option key={s} value={s}>
                  {yearStatusLabel(t, s)}
                </option>
              ))}
            </Select>
          </div>
        )}
      </div>
      <div className="flex gap-2">
        <Button type="button" variant="secondary" size="sm" onClick={onCancel}>
          {t("yearManager.form.cancelButton")}
        </Button>
        <Button type="submit" variant="primary" size="sm" disabled={submitting}>
          {submitting ? t("yearManager.form.saving") : isEdit ? t("yearManager.form.saveChangesButton") : t("yearManager.form.createButton")}
        </Button>
      </div>
    </form>
  );
}
