import React, { useEffect, useState } from "react";
import { Plus } from "lucide-react";
import { useTranslation } from "react-i18next";
import { ApiError } from "@/lib/apiClient";
import { listSites, SiteResponse } from "@/features/facility/api";
import { AcademicYearResponse, ClassResponse, CreateClassRequest, createClass, CurriculumResponse, listAcademicYears, listCurriculums } from "../api";
import Modal from "@/components/ui/Modal";
import Button from "@/components/ui/Button";
import DatePicker from "@/components/ui/DatePicker";
import Select from "@/components/ui/Select";

const inputClass = "w-full bg-slate-50 border border-slate-200 text-xs p-2.5 rounded-lg focus:outline-none";
const inputErrorClass = "w-full bg-rose-50/40 border border-rose-400 text-xs p-2.5 rounded-lg focus:outline-none focus:ring-1 focus:ring-rose-300";
const labelClass = "text-[10px] uppercase font-bold text-slate-500 block mb-1";

interface ClassFormModalProps {
  onClose: () => void;
  onCreated: (created: ClassResponse) => void;
}

/** UC-18 Main Flow bước 3-4: khởi tạo record lớp học thực tế. */
export default function ClassFormModal({ onClose, onCreated }: ClassFormModalProps) {
  const { t } = useTranslation("academic-classes");
  const [sites, setSites] = useState<SiteResponse[]>([]);
  const [curriculums, setCurriculums] = useState<CurriculumResponse[]>([]);
  const [academicYears, setAcademicYears] = useState<AcademicYearResponse[]>([]);
  const [form, setForm] = useState({
    classCode: "",
    name: "",
    siteId: "",
    curriculumId: "",
    classType: "OPEN" as CreateClassRequest["classType"],
    maxStudents: "",
    minStudents: "",
    startDate: "",
    endDate: "",
    academicYearId: ""
  });
  const [touched, setTouched] = useState({ classCode: false, name: false, siteId: false, curriculumId: false, maxStudents: false, startDate: false });
  const [submitAttempted, setSubmitAttempted] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    listSites().then(setSites).catch(() => undefined);
    listCurriculums().then(setCurriculums).catch(() => undefined);
    listAcademicYears().then(setAcademicYears).catch(() => undefined);
  }, []);

  const markTouched = (field: keyof typeof touched) => setTouched((t) => ({ ...t, [field]: true }));
  const invalid = {
    classCode: (touched.classCode || submitAttempted) && !form.classCode.trim(),
    name: (touched.name || submitAttempted) && !form.name.trim(),
    siteId: (touched.siteId || submitAttempted) && !form.siteId,
    curriculumId: (touched.curriculumId || submitAttempted) && !form.curriculumId,
    maxStudents: (touched.maxStudents || submitAttempted) && !form.maxStudents,
    startDate: (touched.startDate || submitAttempted) && !form.startDate
  };

  const selectedSite = sites.find((s) => String(s.id) === form.siteId) ?? null;
  const eligibleSites = form.classType === "LINKED" ? sites.filter((s) => s.siteType === "PARTNER") : sites;
  const eligibleCurriculums = curriculums.filter((c) => c.siteId == null || (selectedSite && c.siteId === selectedSite.id));

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setSubmitAttempted(true);
    if (!form.classCode.trim() || !form.name.trim() || !form.siteId || !form.curriculumId || !form.maxStudents || !form.startDate) {
      setError(t("classForm.formIncompleteError"));
      return;
    }
    setSubmitting(true);
    setError(null);
    try {
      const request: CreateClassRequest = {
        classCode: form.classCode.trim(),
        name: form.name.trim(),
        siteId: Number(form.siteId),
        curriculumId: Number(form.curriculumId),
        classType: form.classType,
        maxStudents: Number(form.maxStudents),
        minStudents: form.minStudents ? Number(form.minStudents) : undefined,
        startDate: form.startDate,
        endDate: form.endDate || undefined,
        academicYearId: form.academicYearId ? Number(form.academicYearId) : undefined
      };
      const created = await createClass(request);
      onCreated(created);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t("classForm.submitError"));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Modal open onClose={onClose} title={t("classForm.modalTitle")} size="lg">
      <form onSubmit={handleSubmit} className="space-y-5">
        {error && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg">{error}</div>}

        <div className="grid grid-cols-2 gap-3">
          <div>
            <label className={labelClass}>{t("classForm.classCodeLabel")}</label>
            <input
              value={form.classCode}
              onChange={(e) => setForm({ ...form, classCode: e.target.value })}
              onBlur={() => markTouched("classCode")}
              className={`${invalid.classCode ? inputErrorClass : inputClass} font-mono`}
            />
            {invalid.classCode && <p className="text-[10px] text-rose-600 mt-1">{t("classForm.classCodeRequired")}</p>}
          </div>
          <div>
            <label className={labelClass}>{t("classForm.nameLabel")}</label>
            <input
              value={form.name}
              onChange={(e) => setForm({ ...form, name: e.target.value })}
              onBlur={() => markTouched("name")}
              className={invalid.name ? inputErrorClass : inputClass}
            />
            {invalid.name && <p className="text-[10px] text-rose-600 mt-1">{t("classForm.nameRequired")}</p>}
          </div>

          <div>
            <label className={labelClass}>{t("classForm.classTypeLabel")}</label>
            <Select
              value={form.classType}
              onChange={(e) => setForm({ ...form, classType: e.target.value as CreateClassRequest["classType"], siteId: "" })}
              className={inputClass}
            >
              <option value="OPEN">{t("classForm.classTypeOpenOption")}</option>
              <option value="LINKED">{t("classForm.classTypeLinkedOption")}</option>
            </Select>
            {form.classType === "LINKED" && (
              <p className="text-[10px] text-slate-400 mt-1">{t("classForm.classTypeLinkedHint")}</p>
            )}
          </div>
          <div>
            <label className={labelClass}>
              {t("classForm.siteLabel")}
              {form.classType === "LINKED" ? ` ${t("classForm.siteLabelLinkedSuffix")}` : ""} *
            </label>
            <Select
              value={form.siteId}
              onChange={(e) => setForm({ ...form, siteId: e.target.value, curriculumId: "" })}
              onBlur={() => markTouched("siteId")}
              className={invalid.siteId ? inputErrorClass : inputClass}
            >
              <option value="">{t("classForm.sitePlaceholder")}</option>
              {eligibleSites.map((s) => (
                <option key={s.id} value={s.id}>
                  {s.name} ({s.siteType === "PARTNER" ? t("classForm.siteOptionPartner") : t("classForm.siteOptionOwned")})
                </option>
              ))}
            </Select>
            {invalid.siteId && <p className="text-[10px] text-rose-600 mt-1">{t("classForm.siteRequired")}</p>}
          </div>

          <div className="col-span-2">
            <label className={labelClass}>{t("classForm.curriculumLabel")}</label>
            <Select
              value={form.curriculumId}
              onChange={(e) => setForm({ ...form, curriculumId: e.target.value })}
              onBlur={() => markTouched("curriculumId")}
              className={invalid.curriculumId ? inputErrorClass : inputClass}
            >
              <option value="">{t("classForm.curriculumPlaceholder")}</option>
              {eligibleCurriculums.map((c) => (
                <option key={c.id} value={c.id}>
                  {c.code} — {c.name}
                </option>
              ))}
            </Select>
            {invalid.curriculumId && <p className="text-[10px] text-rose-600 mt-1">{t("classForm.curriculumRequired")}</p>}
          </div>

          <div>
            <label className={labelClass}>{t("classForm.maxStudentsLabel")}</label>
            <input
              type="number"
              min={1}
              value={form.maxStudents}
              onChange={(e) => setForm({ ...form, maxStudents: e.target.value })}
              onBlur={() => markTouched("maxStudents")}
              className={invalid.maxStudents ? inputErrorClass : inputClass}
            />
            {invalid.maxStudents && <p className="text-[10px] text-rose-600 mt-1">{t("classForm.maxStudentsRequired")}</p>}
          </div>
          <div>
            <label className={labelClass}>{t("classForm.minStudentsLabel")}</label>
            <input type="number" min={0} value={form.minStudents} onChange={(e) => setForm({ ...form, minStudents: e.target.value })} className={inputClass} />
          </div>

          <div>
            <label className={labelClass}>{t("classForm.startDateLabel")}</label>
            <DatePicker
              value={form.startDate}
              onChange={(v) => {
                setForm({ ...form, startDate: v });
                markTouched("startDate");
              }}
              max={form.endDate || undefined}
              hasError={invalid.startDate}
            />
            {invalid.startDate && <p className="text-[10px] text-rose-600 mt-1">{t("classForm.startDateRequired")}</p>}
          </div>
          <div>
            <label className={labelClass}>{t("classForm.endDateLabel")}</label>
            <DatePicker value={form.endDate} onChange={(v) => setForm({ ...form, endDate: v })} min={form.startDate || undefined} />
          </div>

          <div>
            <label className={labelClass}>{t("classForm.academicYearLabel")}</label>
            <Select value={form.academicYearId} onChange={(e) => setForm({ ...form, academicYearId: e.target.value })} className={inputClass}>
              <option value="">{t("classForm.academicYearPlaceholder")}</option>
              {academicYears.map((y) => (
                <option key={y.id} value={y.id}>
                  {y.code} — {y.name}
                </option>
              ))}
            </Select>
          </div>
        </div>

        <div className="flex justify-end gap-2 pt-2">
          <Button type="button" variant="secondary" onClick={onClose}>
            {t("common.cancelButton")}
          </Button>
          <Button type="submit" variant="primary" disabled={submitting}>
            <Plus className="w-3.5 h-3.5" />
            {submitting ? t("classForm.submitting") : t("classForm.submitButton")}
          </Button>
        </div>
      </form>
    </Modal>
  );
}
