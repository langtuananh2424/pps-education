import React, { useEffect, useState } from "react";
import { CalendarRange, Plus } from "lucide-react";
import { useTranslation } from "react-i18next";
import { ApiError } from "@/lib/apiClient";
import Modal from "@/components/ui/Modal";
import Button from "@/components/ui/Button";
import DatePicker from "@/components/ui/DatePicker";
import Select from "@/components/ui/Select";
import {
  AcademicTermResponse,
  AcademicYearResponse,
  createAcademicTerm,
  listAcademicTerms,
  listAcademicYears,
  updateAcademicTerm
} from "../api";

const inputClass = "w-full bg-slate-50 border border-slate-200 text-xs p-2.5 rounded-lg focus:outline-none";
const labelClass = "text-[10px] uppercase font-bold text-slate-500 block mb-1";

interface AcademicTermManagerModalProps {
  siteId: number;
  siteName: string;
  onClose: () => void;
}

/**
 * UC-18 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-07-31) —
 * quản lý "Giai đoạn/Học kỳ" của 1 điểm trường (VD: Giữa kỳ 1, Cuối kỳ 1).
 * Độc lập với lớp học — không gán kỳ trực tiếp vào 1 lớp ở đây. Hồ sơ
 * lớp/học sinh theo từng kỳ (báo cáo & thống kê) là 1 phân hệ riêng sẽ
 * triển khai sau, dùng lại [startDate, endDate] của kỳ để lọc dữ liệu đã
 * có ngày tháng sẵn (ghi danh, phân công giáo viên, điểm danh, nhận xét).
 *
 * V157 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-28):
 * mỗi kỳ học bắt buộc gắn 1 năm học (danh mục dùng chung toàn hệ thống).
 */
export default function AcademicTermManagerModal({ siteId, siteName, onClose }: AcademicTermManagerModalProps) {
  const { t } = useTranslation("academic-curriculum");
  const [terms, setTerms] = useState<AcademicTermResponse[]>([]);
  const [years, setYears] = useState<AcademicYearResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [showCreate, setShowCreate] = useState(false);
  const [editingId, setEditingId] = useState<number | null>(null);

  const load = () => {
    setLoading(true);
    Promise.all([listAcademicTerms(siteId), listAcademicYears()])
      .then(([termList, yearList]) => {
        setTerms(termList);
        setYears(yearList);
      })
      .catch((err) => setError(err instanceof ApiError ? err.message : t("termManager.loadFailedFallback")))
      .finally(() => setLoading(false));
  };
  useEffect(load, [siteId]);

  return (
    <Modal open onClose={onClose} title={t("termManager.modalTitle", { siteName })} size="lg">
      {error && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg mb-3">{error}</div>}

      <div className="flex items-center justify-between mb-3">
        <span className="text-[10px] font-bold uppercase text-slate-500">{t("termManager.createdCountLabel", { count: terms.length })}</span>
        {!showCreate && (
          <Button size="sm" variant="secondary" onClick={() => setShowCreate(true)}>
            <Plus className="w-3.5 h-3.5" />
            {t("termManager.addButton")}
          </Button>
        )}
      </div>

      {loading ? (
        <p className="text-xs text-slate-500">{t("termManager.loading")}</p>
      ) : terms.length === 0 && !showCreate ? (
        <div className="text-xs text-slate-400 italic text-center py-6 flex flex-col items-center gap-2">
          <CalendarRange className="w-8 h-8 text-slate-300" />
          {t("termManager.empty")}
        </div>
      ) : (
        <div className="space-y-2 mb-3">
          {terms.map((term) =>
            editingId === term.id ? (
              <TermForm
                key={term.id}
                initial={term}
                years={years}
                onCancel={() => setEditingId(null)}
                onSubmit={async (values) => {
                  await updateAcademicTerm(term.id, values);
                  setEditingId(null);
                  load();
                }}
              />
            ) : (
              <div key={term.id} className="border border-slate-200 rounded-lg p-3 text-xs flex items-center justify-between gap-2">
                <div>
                  <div className="font-bold text-slate-800">
                    {term.code} — {term.name}
                  </div>
                  <div className="text-[10px] text-slate-500 mt-0.5">
                    {term.startDate} → {term.endDate}
                    {term.academicYearCode ? ` · ${term.academicYearCode}` : ""}
                  </div>
                </div>
                <button onClick={() => setEditingId(term.id)} className="text-brand-red font-bold text-[11px] hover:underline shrink-0">
                  {t("termManager.editButton")}
                </button>
              </div>
            )
          )}
        </div>
      )}

      {showCreate && (
        <TermForm
          years={years}
          onCancel={() => setShowCreate(false)}
          onSubmit={async (values) => {
            await createAcademicTerm({ ...values, siteId });
            setShowCreate(false);
            load();
          }}
        />
      )}
    </Modal>
  );
}

interface TermFormValues {
  code: string;
  name: string;
  academicYearId: number;
  startDate: string;
  endDate: string;
}

function TermForm({
  initial,
  years,
  onCancel,
  onSubmit
}: {
  initial?: AcademicTermResponse;
  years: AcademicYearResponse[];
  onCancel: () => void;
  onSubmit: (values: TermFormValues) => Promise<void>;
}) {
  const { t } = useTranslation("academic-curriculum");
  const [code, setCode] = useState(initial?.code ?? "");
  const [name, setName] = useState(initial?.name ?? "");
  const [academicYearId, setAcademicYearId] = useState<string>(initial?.academicYearId ? String(initial.academicYearId) : "");
  const [startDate, setStartDate] = useState(initial?.startDate ?? "");
  const [endDate, setEndDate] = useState(initial?.endDate ?? "");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const isEdit = !!initial;

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if ((!isEdit && !code.trim()) || !name.trim() || !academicYearId || !startDate || !endDate) {
      setError(t("termManager.form.validationError"));
      return;
    }
    if (endDate < startDate) {
      setError(t("termManager.form.dateOrderError"));
      return;
    }
    setSubmitting(true);
    setError(null);
    try {
      const values: TermFormValues = {
        code: code.trim(),
        name: name.trim(),
        academicYearId: Number(academicYearId),
        startDate,
        endDate
      };
      await onSubmit(values);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t("termManager.form.saveFailedFallback"));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <form onSubmit={handleSubmit} className="bg-slate-50 border border-slate-200 rounded-xl p-4 space-y-3">
      {error && <p className="text-xs text-rose-600">{error}</p>}
      <div className="grid grid-cols-2 gap-3">
        <div>
          <label className={labelClass}>{t("termManager.form.codeLabel")} {isEdit && <span className="normal-case font-normal text-slate-400">{t("termManager.form.codeNotEditable")}</span>}</label>
          <input value={code} onChange={(e) => setCode(e.target.value)} disabled={isEdit} placeholder={t("termManager.form.codePlaceholder")} className={`${inputClass} font-mono`} />
        </div>
        <div>
          <label className={labelClass}>{t("termManager.form.nameLabel")}</label>
          <input value={name} onChange={(e) => setName(e.target.value)} placeholder={t("termManager.form.namePlaceholder")} className={inputClass} />
        </div>
        <div className="col-span-2">
          <label className={labelClass}>{t("termManager.form.academicYearLabel")}</label>
          <Select value={academicYearId} onChange={(e) => setAcademicYearId(e.target.value)} className={inputClass} disabled={years.length === 0}>
            <option value="">{t("termManager.form.academicYearPlaceholder")}</option>
            {years.map((y) => (
              <option key={y.id} value={y.id}>
                {y.code} — {y.name}
              </option>
            ))}
          </Select>
          {years.length === 0 && <p className="text-[10px] text-amber-600 mt-1">{t("termManager.form.academicYearEmpty")}</p>}
        </div>
        <div>
          <label className={labelClass}>{t("termManager.form.startDateLabel")}</label>
          <DatePicker value={startDate} onChange={setStartDate} max={endDate || undefined} />
        </div>
        <div>
          <label className={labelClass}>{t("termManager.form.endDateLabel")}</label>
          <DatePicker value={endDate} onChange={setEndDate} min={startDate || undefined} />
        </div>
      </div>
      <div className="flex gap-2">
        <Button type="button" variant="secondary" size="sm" onClick={onCancel}>
          {t("termManager.form.cancelButton")}
        </Button>
        <Button type="submit" variant="primary" size="sm" disabled={submitting}>
          {submitting ? t("termManager.form.saving") : isEdit ? t("termManager.form.saveChangesButton") : t("termManager.form.createButton")}
        </Button>
      </div>
    </form>
  );
}
