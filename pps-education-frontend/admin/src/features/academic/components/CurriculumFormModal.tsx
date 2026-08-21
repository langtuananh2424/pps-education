import React, { useState } from "react";
import { Plus } from "lucide-react";
import { useTranslation } from "react-i18next";
import { ApiError } from "@/lib/apiClient";
import { CreateCurriculumRequest, CurriculumResponse, createCurriculum } from "../api";
import Modal from "@/components/ui/Modal";
import Button from "@/components/ui/Button";
import Select from "@/components/ui/Select";

const inputClass = "w-full bg-slate-50 border border-slate-200 text-xs p-2.5 rounded-lg focus:outline-none";
const inputErrorClass = "w-full bg-rose-50/40 border border-rose-400 text-xs p-2.5 rounded-lg focus:outline-none focus:ring-1 focus:ring-rose-300";
const labelClass = "text-[10px] uppercase font-bold text-slate-500 block mb-1";

interface CurriculumFormModalProps {
  onClose: () => void;
  onCreated: (created: CurriculumResponse) => void;
}

/** UC-16 Main Flow bước 1-3: khởi tạo khung chương trình chuẩn mới. */
export default function CurriculumFormModal({ onClose, onCreated }: CurriculumFormModalProps) {
  const { t } = useTranslation("academic-curriculum");
  const [form, setForm] = useState({ code: "", name: "", classCategory: "MAIN", level: "", totalPeriods: "", defaultGradePassThreshold: "" });
  const [touched, setTouched] = useState({ code: false, name: false });
  const [submitAttempted, setSubmitAttempted] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const invalid = {
    code: (touched.code || submitAttempted) && !form.code.trim(),
    name: (touched.name || submitAttempted) && !form.name.trim()
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setSubmitAttempted(true);
    if (!form.code.trim() || !form.name.trim()) {
      setError(t("form.validationError"));
      return;
    }
    setSubmitting(true);
    setError(null);
    try {
      const request: CreateCurriculumRequest = {
        code: form.code.trim(),
        name: form.name.trim(),
        classCategory: form.classCategory,
        level: form.level.trim() || undefined,
        totalPeriods: form.totalPeriods ? Number(form.totalPeriods) : undefined,
        defaultGradePassThreshold: form.defaultGradePassThreshold ? Number(form.defaultGradePassThreshold) : undefined
      };
      const created = await createCurriculum(request);
      onCreated(created);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t("form.createFailedFallback"));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Modal open onClose={onClose} title={t("form.modalTitle")} size="md">
      <form onSubmit={handleSubmit} className="space-y-4">
        {error && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg">{error}</div>}

        <div className="grid grid-cols-2 gap-3">
          <div>
            <label className={labelClass}>{t("form.codeLabel")}</label>
            <input
              value={form.code}
              onChange={(e) => setForm({ ...form, code: e.target.value })}
              onBlur={() => setTouched((t2) => ({ ...t2, code: true }))}
              className={`${invalid.code ? inputErrorClass : inputClass} font-mono`}
            />
            {invalid.code && <p className="text-[10px] text-rose-600 mt-1">{t("form.codeRequiredError")}</p>}
          </div>
          <div>
            <label className={labelClass}>{t("form.classCategoryLabel")}</label>
            <Select value={form.classCategory} onChange={(e) => setForm({ ...form, classCategory: e.target.value })} className={inputClass}>
              <option value="MAIN">{t("form.classCategoryOptions.MAIN")}</option>
              <option value="SUPPLEMENTARY">{t("form.classCategoryOptions.SUPPLEMENTARY")}</option>
              <option value="EXAM_PREP">{t("form.classCategoryOptions.EXAM_PREP")}</option>
              <option value="OTHER">{t("form.classCategoryOptions.OTHER")}</option>
            </Select>
          </div>
          <div className="col-span-2">
            <label className={labelClass}>{t("form.nameLabel")}</label>
            <input
              value={form.name}
              onChange={(e) => setForm({ ...form, name: e.target.value })}
              onBlur={() => setTouched((t2) => ({ ...t2, name: true }))}
              className={invalid.name ? inputErrorClass : inputClass}
            />
            {invalid.name && <p className="text-[10px] text-rose-600 mt-1">{t("form.nameRequiredError")}</p>}
          </div>
          <div>
            <label className={labelClass}>{t("form.levelLabel")}</label>
            <input value={form.level} onChange={(e) => setForm({ ...form, level: e.target.value })} className={inputClass} />
          </div>
          <div>
            <label className={labelClass}>{t("form.totalPeriodsLabel")}</label>
            <input type="number" min={0} value={form.totalPeriods} onChange={(e) => setForm({ ...form, totalPeriods: e.target.value })} className={inputClass} />
          </div>
          <div className="col-span-2">
            <label className={labelClass}>{t("form.passThresholdLabel")}</label>
            <input
              type="number"
              step="0.1"
              min={0}
              max={10}
              value={form.defaultGradePassThreshold}
              onChange={(e) => setForm({ ...form, defaultGradePassThreshold: e.target.value })}
              className={inputClass}
            />
          </div>
        </div>

        <div className="flex justify-end gap-2 pt-2">
          <Button type="button" variant="secondary" onClick={onClose}>
            {t("form.cancelButton")}
          </Button>
          <Button type="submit" variant="primary" disabled={submitting}>
            <Plus className="w-3.5 h-3.5" />
            {submitting ? t("form.creating") : t("form.createButton")}
          </Button>
        </div>
      </form>
    </Modal>
  );
}
