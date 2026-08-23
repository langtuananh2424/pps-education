import React, { useEffect, useState } from "react";
import { BookOpen, ListTree, Plus, Save, Send } from "lucide-react";
import { useTranslation } from "react-i18next";
import { ApiError } from "@/lib/apiClient";
import { listSites, SiteResponse } from "@/features/facility/api";
import {
  CreateCurriculumSubjectRequest,
  CurriculumResponse,
  CurriculumSubjectResponse,
  addCurriculumSubject,
  createCustomCurriculum,
  listCurriculumSubjects,
  submitCurriculumForApproval,
  updateCurriculum,
  updateCustomCurriculum
} from "../api";
import Badge from "@/components/ui/Badge";
import Button from "@/components/ui/Button";
import Modal from "@/components/ui/Modal";
import { curriculumStatusLabel, curriculumStatusVariants } from "./CurriculumListPanel";
import { useToast } from "@/lib/useToast";
import Toast from "@/components/ui/Toast";
import Select from "@/components/ui/Select";

const inputClass = "w-full bg-slate-50 border border-slate-200 text-xs p-2.5 rounded-lg focus:outline-none";
const labelClass = "text-[10px] uppercase font-bold text-slate-500 block mb-1";

type Tab = "profile" | "subjects";

interface CurriculumDetailPanelProps {
  curriculum: CurriculumResponse;
  onChanged: () => void;
}

export default function CurriculumDetailPanel({ curriculum, onChanged }: CurriculumDetailPanelProps) {
  const { t } = useTranslation("academic-curriculum");
  const [tab, setTab] = useState<Tab>("profile");
  const isCustom = curriculum.siteId != null;
  const { message: toastMessage, showToast } = useToast();

  return (
    <div className="lg:col-span-3 bg-white rounded-xl border border-slate-200 shadow-soft overflow-hidden flex flex-col">
      <div className="p-5 border-b border-slate-200 space-y-3 bg-slate-50/20">
        <div className="flex items-center justify-between flex-wrap gap-2">
          <div>
            <span className="text-[10px] font-mono font-bold uppercase tracking-wider text-brand-red bg-orange-50 border border-orange-100 px-2 py-0.5 rounded-md">
              {curriculum.code}
            </span>
            <h2 className="text-sm font-bold text-slate-800 mt-1">{curriculum.name}</h2>
          </div>
          <Badge variant={curriculumStatusVariants[curriculum.status] ?? "neutral"}>{curriculumStatusLabel(t, curriculum.status)}</Badge>
        </div>

        <div className="flex border-b border-slate-200 pt-1 gap-5">
          {(
            [
              ["profile", t("detail.tabs.profile"), BookOpen],
              ["subjects", t("detail.tabs.subjects"), ListTree]
            ] as const
          ).map(([key, label, Icon]) => (
            <button
              key={key}
              onClick={() => setTab(key)}
              className={`pb-2.5 text-xs font-bold border-b-2 flex items-center gap-1.5 transition-all ${
                tab === key ? "border-brand-red text-brand-red" : "border-transparent text-slate-500 hover:text-slate-700"
              }`}
            >
              <Icon className="w-3.5 h-3.5" />
              {label}
            </button>
          ))}
        </div>
      </div>

      <div className="flex-1 p-5 overflow-y-auto max-h-[560px]">
        {tab === "profile" && <ProfileTab curriculum={curriculum} isCustom={isCustom} onChanged={onChanged} showToast={showToast} />}
        {tab === "subjects" && <SubjectsTab curriculumId={curriculum.id} showToast={showToast} />}
      </div>

      <Toast message={toastMessage} />
    </div>
  );
}

function ProfileTab({
  curriculum,
  isCustom,
  onChanged,
  showToast
}: {
  curriculum: CurriculumResponse;
  isCustom: boolean;
  onChanged: () => void;
  showToast: (msg: string) => void;
}) {
  const { t } = useTranslation("academic-curriculum");
  const [form, setForm] = useState({
    name: curriculum.name,
    level: curriculum.level ?? "",
    gradeLevel: curriculum.gradeLevel ?? "",
    track: curriculum.track ?? "",
    totalPeriods: curriculum.totalPeriods != null ? String(curriculum.totalPeriods) : "",
    defaultGradePassThreshold: curriculum.defaultGradePassThreshold != null ? String(curriculum.defaultGradePassThreshold) : "",
    status: curriculum.status
  });
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [showCustomForm, setShowCustomForm] = useState(false);

  useEffect(
    () =>
      setForm({
        name: curriculum.name,
        level: curriculum.level ?? "",
        gradeLevel: curriculum.gradeLevel ?? "",
        track: curriculum.track ?? "",
        totalPeriods: curriculum.totalPeriods != null ? String(curriculum.totalPeriods) : "",
        defaultGradePassThreshold: curriculum.defaultGradePassThreshold != null ? String(curriculum.defaultGradePassThreshold) : "",
        status: curriculum.status
      }),
    [curriculum]
  );

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!form.name.trim()) {
      setError(t("detail.profile.nameRequiredError"));
      return;
    }
    setSaving(true);
    setError(null);
    try {
      if (isCustom) {
        await updateCustomCurriculum(curriculum.id, {
          name: form.name.trim(),
          level: form.level.trim() || undefined,
          totalPeriods: form.totalPeriods ? Number(form.totalPeriods) : undefined,
          defaultGradePassThreshold: form.defaultGradePassThreshold ? Number(form.defaultGradePassThreshold) : undefined
        });
      } else {
        await updateCurriculum(curriculum.id, {
          name: form.name.trim(),
          level: form.level.trim() || undefined,
          gradeLevel: form.gradeLevel || undefined,
          track: form.track || undefined,
          totalPeriods: form.totalPeriods ? Number(form.totalPeriods) : undefined,
          defaultGradePassThreshold: form.defaultGradePassThreshold ? Number(form.defaultGradePassThreshold) : undefined,
          status: form.status,
          confirm: false
        });
      }
      onChanged();
      showToast(t("detail.profile.savedToast"));
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t("detail.profile.updateFailedFallback"));
    } finally {
      setSaving(false);
    }
  };

  const handleSubmitForApproval = async () => {
    setError(null);
    try {
      await submitCurriculumForApproval(curriculum.id);
      onChanged();
      showToast(t("detail.profile.submittedToast"));
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t("detail.profile.submitForApprovalFailedFallback"));
    }
  };

  return (
    <div className="space-y-5">
      <form onSubmit={handleSubmit} className="space-y-4">
        {error && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg">{error}</div>}
        <div className="grid grid-cols-2 gap-3">
          <div className="col-span-2">
            <label className={labelClass}>{t("detail.profile.nameLabel")}</label>
            <input value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} className={inputClass} required />
          </div>
          <div>
            <label className={labelClass}>{t("detail.profile.levelLabel")}</label>
            <input value={form.level} onChange={(e) => setForm({ ...form, level: e.target.value })} className={inputClass} />
          </div>
          {!isCustom && (
            <>
              <div>
                <label className={labelClass}>{t("detail.profile.gradeLevelLabel")}</label>
                <Select value={form.gradeLevel} onChange={(e) => setForm({ ...form, gradeLevel: e.target.value })} className={inputClass}>
                  <option value="">{t("detail.profile.gradeLevelPlaceholder")}</option>
                  <option value="GRADE_6">{t("detail.profile.gradeLevelOptions.GRADE_6")}</option>
                  <option value="GRADE_7">{t("detail.profile.gradeLevelOptions.GRADE_7")}</option>
                  <option value="GRADE_8">{t("detail.profile.gradeLevelOptions.GRADE_8")}</option>
                  <option value="GRADE_9">{t("detail.profile.gradeLevelOptions.GRADE_9")}</option>
                </Select>
              </div>
              <div>
                <label className={labelClass}>{t("detail.profile.trackLabel")}</label>
                <Select value={form.track} onChange={(e) => setForm({ ...form, track: e.target.value })} className={inputClass}>
                  <option value="">{t("detail.profile.trackPlaceholder")}</option>
                  <option value="IELTS">{t("detail.profile.trackOptions.IELTS")}</option>
                  <option value="CAMBRIDGE">{t("detail.profile.trackOptions.CAMBRIDGE")}</option>
                </Select>
              </div>
            </>
          )}
          <div>
            <label className={labelClass}>{t("detail.profile.totalPeriodsLabel")}</label>
            <input type="number" min={0} value={form.totalPeriods} onChange={(e) => setForm({ ...form, totalPeriods: e.target.value })} className={inputClass} />
          </div>
          <div>
            <label className={labelClass}>{t("detail.profile.passThresholdLabel")}</label>
            <input
              type="number"
              step="0.1"
              value={form.defaultGradePassThreshold}
              onChange={(e) => setForm({ ...form, defaultGradePassThreshold: e.target.value })}
              className={inputClass}
            />
          </div>
          {!isCustom && (
            <div>
              <label className={labelClass}>{t("detail.profile.statusLabel")}</label>
              <Select value={form.status} onChange={(e) => setForm({ ...form, status: e.target.value })} className={inputClass}>
                <option value="DRAFT">{t("status.DRAFT")}</option>
                <option value="ACTIVE">{t("status.ACTIVE")}</option>
                <option value="ARCHIVED">{t("status.ARCHIVED")}</option>
              </Select>
            </div>
          )}
        </div>
        <Button type="submit" variant="primary" size="sm" disabled={saving}>
          <Save className="w-3.5 h-3.5" />
          {saving ? t("detail.profile.saving") : t("detail.profile.saveButton")}
        </Button>
      </form>

      {isCustom && curriculum.status === "DRAFT" && (
        <div className="border-t border-slate-100 pt-4">
          <Button type="button" variant="dark" size="sm" onClick={handleSubmitForApproval}>
            <Send className="w-3.5 h-3.5" />
            {t("detail.profile.submitForApprovalButton")}
          </Button>
        </div>
      )}

      {!isCustom && (
        <div className="border-t border-slate-100 pt-4 space-y-3">
          <Button type="button" variant="secondary" size="sm" onClick={() => setShowCustomForm(true)}>
            <Plus className="w-3.5 h-3.5" />
            {t("detail.profile.createCustomButton")}
          </Button>
          <Modal open={showCustomForm} onClose={() => setShowCustomForm(false)} title={t("detail.profile.createCustomModalTitle")}>
            <CreateCustomForm
              parentCurriculumId={curriculum.id}
              onDone={() => {
                setShowCustomForm(false);
                onChanged();
                showToast(t("detail.profile.createdCustomToast"));
              }}
              onCancel={() => setShowCustomForm(false)}
            />
          </Modal>
        </div>
      )}
    </div>
  );
}

function CreateCustomForm({ parentCurriculumId, onDone, onCancel }: { parentCurriculumId: number; onDone: () => void; onCancel: () => void }) {
  const { t } = useTranslation("academic-curriculum");
  const [sites, setSites] = useState<SiteResponse[]>([]);
  const [form, setForm] = useState({ code: "", siteId: "", name: "" });
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    listSites().then((all) => setSites(all.filter((s) => s.siteType === "PARTNER"))).catch(() => undefined);
  }, []);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!form.code.trim() || !form.siteId) {
      setError(t("detail.customForm.validationError"));
      return;
    }
    setSubmitting(true);
    setError(null);
    try {
      await createCustomCurriculum({ code: form.code.trim(), parentCurriculumId, siteId: Number(form.siteId), name: form.name.trim() || undefined });
      onDone();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t("detail.customForm.createFailedFallback"));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <form onSubmit={handleSubmit} className="bg-slate-50 border border-slate-200 rounded-xl p-4 space-y-3">
      {error && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg">{error}</div>}
      <div className="grid grid-cols-2 gap-2">
        <input value={form.code} onChange={(e) => setForm({ ...form, code: e.target.value })} placeholder={t("detail.customForm.codePlaceholder")} className={`${inputClass} font-mono`} />
        <Select value={form.siteId} onChange={(e) => setForm({ ...form, siteId: e.target.value })} className={inputClass}>
          <option value="">{t("detail.customForm.sitePlaceholder")}</option>
          {sites.map((s) => (
            <option key={s.id} value={s.id}>
              {s.name}
            </option>
          ))}
        </Select>
      </div>
      <input value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} placeholder={t("detail.customForm.namePlaceholder")} className={inputClass} />
      <div className="flex gap-2">
        <Button type="button" variant="secondary" size="sm" onClick={onCancel}>
          {t("detail.customForm.cancelButton")}
        </Button>
        <Button type="submit" variant="primary" size="sm" disabled={submitting}>
          {submitting ? t("detail.customForm.creating") : t("detail.customForm.createButton")}
        </Button>
      </div>
    </form>
  );
}

function SubjectsTab({ curriculumId, showToast }: { curriculumId: number; showToast: (msg: string) => void }) {
  const { t } = useTranslation("academic-curriculum");
  const [subjects, setSubjects] = useState<CurriculumSubjectResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [adding, setAdding] = useState(false);

  const load = () => {
    setLoading(true);
    listCurriculumSubjects(curriculumId)
      .then(setSubjects)
      .catch((err) => setError(err instanceof ApiError ? err.message : t("detail.subjects.loadFailedFallback")))
      .finally(() => setLoading(false));
  };
  useEffect(load, [curriculumId]);

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <span className="text-[10px] font-bold uppercase text-slate-500">{t("detail.subjects.countLabel", { count: subjects.length })}</span>
        <Button size="sm" variant="secondary" onClick={() => setAdding(true)}>
          <Plus className="w-3.5 h-3.5" />
          {t("detail.subjects.addButton")}
        </Button>
      </div>

      {error && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg">{error}</div>}

      {loading ? (
        <p className="text-xs text-slate-500">{t("detail.subjects.loading")}</p>
      ) : subjects.length === 0 ? (
        <p className="text-xs text-slate-400 italic">{t("detail.subjects.empty")}</p>
      ) : (
        <div className="space-y-2">
          {subjects
            .slice()
            .sort((a, b) => a.displayOrder - b.displayOrder)
            .map((s) => (
              <div key={s.id} className="border border-slate-200 rounded-lg p-3 text-xs flex items-center justify-between">
                <div>
                  <span className="font-bold text-slate-800">{s.name}</span>
                  <span className="font-mono text-slate-400 ml-2">{s.subjectCode}</span>
                </div>
                {s.periodCount != null && <span className="text-slate-400">{t("detail.subjects.periodCount", { count: s.periodCount })}</span>}
              </div>
            ))}
        </div>
      )}

      <Modal open={adding} onClose={() => setAdding(false)} title={t("detail.subjects.modalTitle")}>
        <AddSubjectForm
          curriculumId={curriculumId}
          onDone={() => {
            setAdding(false);
            load();
            showToast(t("detail.subjects.addedToast"));
          }}
          onCancel={() => setAdding(false)}
        />
      </Modal>
    </div>
  );
}

function AddSubjectForm({ curriculumId, onDone, onCancel }: { curriculumId: number; onDone: () => void; onCancel: () => void }) {
  const { t } = useTranslation("academic-curriculum");
  const [form, setForm] = useState({ subjectCode: "", name: "", periodCount: "" });
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!form.subjectCode.trim() || !form.name.trim()) {
      setError(t("detail.addSubjectForm.validationError"));
      return;
    }
    setSubmitting(true);
    setError(null);
    try {
      const request: CreateCurriculumSubjectRequest = { subjectCode: form.subjectCode.trim(), name: form.name.trim(), periodCount: form.periodCount ? Number(form.periodCount) : undefined };
      await addCurriculumSubject(curriculumId, request);
      onDone();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t("detail.addSubjectForm.createFailedFallback"));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <form onSubmit={handleSubmit} className="bg-slate-50 border border-slate-200 rounded-xl p-4 space-y-3">
      {error && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg">{error}</div>}
      <div className="grid grid-cols-3 gap-2">
        <input value={form.subjectCode} onChange={(e) => setForm({ ...form, subjectCode: e.target.value })} placeholder={t("detail.addSubjectForm.codePlaceholder")} className={`${inputClass} font-mono`} />
        <input value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} placeholder={t("detail.addSubjectForm.namePlaceholder")} className={`${inputClass} col-span-2`} />
      </div>
      <input type="number" min={0} value={form.periodCount} onChange={(e) => setForm({ ...form, periodCount: e.target.value })} placeholder={t("detail.addSubjectForm.periodCountPlaceholder")} className={inputClass} />
      <div className="flex gap-2">
        <Button type="button" variant="secondary" size="sm" onClick={onCancel}>
          {t("detail.addSubjectForm.cancelButton")}
        </Button>
        <Button type="submit" variant="primary" size="sm" disabled={submitting}>
          {submitting ? t("detail.addSubjectForm.saving") : t("detail.addSubjectForm.submitButton")}
        </Button>
      </div>
    </form>
  );
}
